package com.example.woodsreader.ui.graph

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.woodsreader.data.graph.EntityType
import com.example.woodsreader.data.graph.GraphAlias
import com.example.woodsreader.data.graph.GraphEntity
import com.example.woodsreader.data.graph.GraphMention
import com.example.woodsreader.data.graph.GraphRelation
import com.example.woodsreader.data.graph.GraphRepository
import com.example.woodsreader.data.graph.GraphJsonHelper
import com.example.woodsreader.data.graph.GraphPositionsCache
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.work.GraphExtractionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GraphUiState(
    val nodes: List<GraphEntity> = emptyList(),
    val edges: List<Pair<Int, Int>> = emptyList(),
    val currentChapter: Int = 0,
    val isSpoilerFree: Boolean = true,
    val selectedTypes: Set<EntityType> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

data class GraphAnalysisProgress(
    val isRunning: Boolean = false,
    val progress: Int = 0,
    val status: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GraphViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val graphRepository: GraphRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    val bookId: String = savedStateHandle.get<String>("bookId").orEmpty()
    private val navChapterIndex: Int = savedStateHandle.get<Int>("chapterIndex") ?: -1

    private val workManager = WorkManager.getInstance(context)

    val analysisProgress: StateFlow<GraphAnalysisProgress> = workManager
        .getWorkInfosForUniqueWorkFlow("graph_extraction_$bookId")
        .map { workInfos ->
            val active = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            if (active != null) {
                val p = active.progress.getInt(GraphExtractionWorker.KEY_PROGRESS, 0)
                val s = active.progress.getString(GraphExtractionWorker.KEY_STATUS) ?: "Analizando..."
                GraphAnalysisProgress(isRunning = true, progress = p, status = s)
            } else {
                GraphAnalysisProgress(isRunning = false, progress = 0, status = "")
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            GraphAnalysisProgress()
        )

    fun startBookAnalysis() {
        val request = OneTimeWorkRequestBuilder<GraphExtractionWorker>()
            .setInputData(workDataOf(GraphExtractionWorker.KEY_BOOK_ID to bookId))
            .build()

        workManager.enqueueUniqueWork(
            "graph_extraction_$bookId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    val bookTitle: StateFlow<String> = bookRepository.getBookFlow(bookId)
        .map { it?.title ?: "Glosario" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Glosario")

    suspend fun getExportJson(): String {
        val book = bookRepository.getBook(bookId)
        val title = book?.title ?: "Libro"
        val exportData = graphRepository.exportAll(bookId)
        return GraphJsonHelper.exportToJson(title, exportData)
    }

    suspend fun importGlossary(jsonString: String): Int {
        return GraphJsonHelper.importGlossary(bookId, jsonString, graphRepository)
    }

    val spoilerFree = MutableStateFlow(true)
    val typeFilters = MutableStateFlow<Set<EntityType>>(emptySet())
    val searchQuery = MutableStateFlow("")
    val selectedIdx = MutableStateFlow<Int?>(null)

    // Tick incrementado en cada frame para forzar redibujo en Compose Canvas
    val tick = MutableStateFlow(0)

    var layout: ForceLayout? = null
        private set

    private var previousNodeIds: List<Long> = emptyList()
    private var physicsJob: Job? = null

    // Flujo del capítulo actual de lectura
    val currentChapterFlow: Flow<Int> = bookRepository.getBookFlow(bookId).map { book ->
        if (navChapterIndex >= 0) {
            navChapterIndex
        } else if (book != null && book.chapterCount > 0) {
            (book.readingProgressPercent * book.chapterCount).toInt().coerceAtLeast(0)
        } else {
            0
        }
    }.distinctUntilChanged()

    // REGLA 6: Combinar 4+ flows usando combines anidados de 2
    // Combine 1: spoilerFree (1) + currentChapterFlow (2) -> maxChFlow
    private val maxChFlow: Flow<Int> = combine(spoilerFree, currentChapterFlow) { isSpoilerFree, currentCh ->
        if (isSpoilerFree) currentCh else Int.MAX_VALUE
    }.distinctUntilChanged()

    // Combine 2: entities (1) + relations (2) desde Room según maxCh
    private val dbGraphFlow: Flow<Pair<List<GraphEntity>, List<GraphRelation>>> = maxChFlow.flatMapLatest { maxCh ->
        combine(
            graphRepository.entities(bookId, maxCh, limit = 150),
            graphRepository.relations(bookId, maxCh, minW = 3)
        ) { entities, relations ->
            Pair(entities, relations)
        }
    }

    // Combine 3: searchQuery (1) + typeFilters (2) -> filterParamsFlow
    private val filterParamsFlow: Flow<Pair<String, Set<EntityType>>> = combine(searchQuery, typeFilters) { query, types ->
        Pair(query, types)
    }

    // Combine 4: dbGraphFlow (1) + filterParamsFlow (2) -> graphState
    val uiState: StateFlow<GraphUiState> = combine(dbGraphFlow, filterParamsFlow) { (rawEntities, rawRelations), (query, types) ->
        val filteredEntities = rawEntities.filter { entity ->
            val matchesType = types.isEmpty() || entity.type in types
            val matchesQuery = query.isBlank() || entity.name.contains(query, ignoreCase = true)
            matchesType && matchesQuery
        }.take(150)

        val nodeIds = filteredEntities.map { it.id }.toSet()
        val idToIndex = filteredEntities.mapIndexed { idx, entity -> entity.id to idx }.toMap()

        val filteredEdges = rawRelations.mapNotNull { rel ->
            if (rel.sourceId in nodeIds && rel.targetId in nodeIds) {
                val u = idToIndex[rel.sourceId]
                val v = idToIndex[rel.targetId]
                if (u != null && v != null && u != v) Pair(u, v) else null
            } else null
        }

        GraphUiState(
            nodes = filteredEntities,
            edges = filteredEdges,
            isSpoilerFree = spoilerFree.value,
            selectedTypes = types,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GraphUiState())

    // Contador de candidatos pendientes
    val candidatesCount: StateFlow<Int> = graphRepository.candidates(bookId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Entidad seleccionada actualmente
    val selectedEntity: StateFlow<GraphEntity?> = combine(selectedIdx, uiState) { idx, state ->
        if (idx != null && idx in state.nodes.indices) state.nodes[idx] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Aliases de la entidad seleccionada
    val selectedEntityAliases: StateFlow<List<GraphAlias>> = selectedEntity.flatMapLatest { entity ->
        if (entity != null) graphRepository.getAliases(entity.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Relaciones de la entidad seleccionada emparejadas con el nodo correspondiente
    val selectedEntityRelations: StateFlow<List<Pair<GraphRelation, GraphEntity>>> = combine(selectedEntity, uiState) { entity, state ->
        Pair(entity, state.nodes)
    }.flatMapLatest { (entity, nodes) ->
        if (entity != null) {
            val nodeMap = nodes.associateBy { it.id }
            graphRepository.getRelationsForEntity(bookId, entity.id).map { rels ->
                rels.mapNotNull { rel ->
                    val otherId = if (rel.sourceId == entity.id) rel.targetId else rel.sourceId
                    val otherNode = nodeMap[otherId]
                    if (otherNode != null) Pair(rel, otherNode) else null
                }
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // FASE 7: Menciones de la entidad seleccionada ordenadas por capítulo
    val selectedEntityMentions: StateFlow<List<GraphMention>> = selectedEntity.flatMapLatest { entity ->
        if (entity != null) graphRepository.getMentionsForEntity(entity.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Índice de la mención actualmente enfocada
    val currentMentionIndex = MutableStateFlow(0)

    init {
        // Observar cambios en el conjunto de nodos para recrear ForceLayout e iniciar loop de física
        viewModelScope.launch {
            uiState.collect { state ->
                val newIds = state.nodes.map { it.id }
                if (newIds != previousNodeIds) {
                    previousNodeIds = newIds
                    // Recrear ForceLayout SOLO cuando cambia el conjunto de nodos
                    layout = if (state.nodes.isNotEmpty()) {
                        val cachedPositions = GraphPositionsCache.loadPositions(context, bookId)
                        val initialMap = mutableMapOf<Int, Pair<Float, Float>>()
                        state.nodes.forEachIndexed { idx, entity ->
                            val pos = cachedPositions[entity.id]
                            if (pos != null) {
                                initialMap[idx] = pos
                            }
                        }
                        val initialPositions = if (initialMap.isNotEmpty()) initialMap else null
                        ForceLayout(state.nodes.size, state.edges, initialPositions = initialPositions)
                    } else {
                        null
                    }
                    startPhysicsLoop(state.nodes)
                } else if (state.edges.isNotEmpty() && layout?.edges != state.edges) {
                    // Si solo cambiaron aristas con los mismos nodos, actualizar aristas preservando posiciones
                    val currentPos = mutableMapOf<Int, Pair<Float, Float>>()
                    layout?.let { l ->
                        for (i in 0 until minOf(state.nodes.size, l.n)) {
                            currentPos[i] = Pair(l.x[i], l.y[i])
                        }
                    }
                    layout = ForceLayout(state.nodes.size, state.edges, initialPositions = currentPos.ifEmpty { null })
                    startPhysicsLoop(state.nodes)
                }
            }
        }

        // FASE 7: Posicionar la mención inicial en nextMention(id, currentChapter) ?: prevMention
        viewModelScope.launch {
            combine(selectedEntityMentions, currentChapterFlow) { mentions, currentCh ->
                Pair(mentions, currentCh)
            }.collect { (mentions, currentCh) ->
                if (mentions.isNotEmpty()) {
                    val targetIdx = mentions.indexOfFirst { it.chapterIndex >= currentCh }
                        .takeIf { it != -1 } ?: (mentions.size - 1)
                    currentMentionIndex.value = targetIdx
                } else {
                    currentMentionIndex.value = 0
                }
            }
        }
    }

    private fun startPhysicsLoop(nodes: List<GraphEntity>) {
        physicsJob?.cancel()
        val currentLayout = layout ?: return
        physicsJob = viewModelScope.launch(Dispatchers.Default) {
            while (!currentLayout.settled && isActive) {
                currentLayout.step()
                tick.value += 1
                delay(16L) // ~60 fps
            }
            if (currentLayout.settled && isActive) {
                GraphPositionsCache.savePositions(context, bookId, nodes, currentLayout)
            }
        }
    }

    fun selectNode(index: Int?) {
        selectedIdx.value = index
    }

    fun toggleTypeFilter(type: EntityType) {
        typeFilters.update { current ->
            if (type in current) current - type else current + type
        }
    }

    fun setSpoilerFree(enabled: Boolean) {
        spoilerFree.value = enabled
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateEntityType(entityId: Long, newType: EntityType) {
        viewModelScope.launch {
            val entity = graphRepository.getEntityById(entityId) ?: return@launch
            graphRepository.updateEntity(entity.copy(type = newType))
        }
    }

    fun updateEntityDescription(entityId: Long, newDescription: String) {
        viewModelScope.launch {
            val entity = graphRepository.getEntityById(entityId) ?: return@launch
            graphRepository.updateEntity(entity.copy(description = newDescription))
        }
    }

    fun addAlias(entityId: Long, alias: String) {
        if (alias.isBlank()) return
        viewModelScope.launch {
            graphRepository.insertAlias(entityId, alias.trim())
        }
    }

    fun deleteAlias(entityId: Long, alias: String) {
        viewModelScope.launch {
            graphRepository.deleteAlias(entityId, alias)
        }
    }

    fun addRelation(sourceId: Long, targetId: Long, label: String) {
        viewModelScope.launch {
            graphRepository.upsertRelation(
                GraphRelation(
                    bookId = bookId,
                    sourceId = sourceId,
                    targetId = targetId,
                    label = label.trim(),
                    weight = 1,
                    manual = true,
                    firstChapterIndex = 0
                )
            )
        }
    }

    fun selectEntityById(entityId: Long) {
        val idx = uiState.value.nodes.indexOfFirst { it.id == entityId }
        if (idx != -1) {
            selectedIdx.value = idx
        }
    }

    fun selectNextMention() {
        val total = selectedEntityMentions.value.size
        if (total > 0) {
            currentMentionIndex.update { (it + 1).coerceAtMost(total - 1) }
        }
    }

    fun selectPrevMention() {
        val total = selectedEntityMentions.value.size
        if (total > 0) {
            currentMentionIndex.update { (it - 1).coerceAtLeast(0) }
        }
    }

    fun buildLocatorForCurrentMention(): Locator? {
        val mentions = selectedEntityMentions.value
        val idx = currentMentionIndex.value
        val mention = mentions.getOrNull(idx) ?: return null
        val url = Url(mention.href) ?: Url.fromDecodedPath(mention.href) ?: return null
        return Locator(
            href = url,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(
                progression = mention.progression
            ),
            text = Locator.Text(
                highlight = mention.snippet
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        val currentLayout = layout
        val currentNodes = uiState.value.nodes
        if (currentLayout != null && currentNodes.isNotEmpty()) {
            GraphPositionsCache.savePositions(context, bookId, currentNodes, currentLayout)
        }
    }
}
