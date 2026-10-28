package com.example.woodsreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.woodsreader.data.graph.EntityType
import com.example.woodsreader.data.graph.GraphEntity
import com.example.woodsreader.data.graph.GraphMention
import com.example.woodsreader.data.graph.GraphRelation
import com.example.woodsreader.data.graph.GraphRepository
import com.example.woodsreader.ui.graph.ForceLayout
import com.example.woodsreader.ui.graph.GraphCanvas
import com.example.woodsreader.ui.graph.entityColor
import com.example.woodsreader.ui.theme.HiddenForest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import javax.inject.Inject

data class EgoGraphUiState(
    val bookId: String = "",
    val currentChapter: Int = 0,
    val centerEntity: GraphEntity? = null,
    val allEntities: List<GraphEntity> = emptyList(),
    val nodes: List<GraphEntity> = emptyList(),
    val edges: List<Pair<Int, Int>> = emptyList(),
    val directRelations: List<GraphRelation> = emptyList(),
    val selectedNodeIdx: Int? = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class ReaderEgoGraphViewModel @Inject constructor(
    private val graphRepository: GraphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EgoGraphUiState())
    val uiState: StateFlow<EgoGraphUiState> = _uiState.asStateFlow()

    fun loadEgoGraph(
        bookId: String,
        currentChapter: Int,
        initialEntityName: String? = null,
        initialEntityId: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, bookId = bookId, currentChapter = currentChapter) }

            // 1. Obtener entidades confirmadas hasta el capítulo actual
            val entities = graphRepository.entities(bookId, currentChapter, limit = 150).firstOrNull() ?: emptyList()

            if (entities.isEmpty()) {
                _uiState.update {
                    it.copy(
                        allEntities = emptyList(),
                        nodes = emptyList(),
                        edges = emptyList(),
                        directRelations = emptyList(),
                        centerEntity = null,
                        isLoading = false
                    )
                }
                return@launch
            }

            // 2. Determinar la entidad centro (ego)
            var center: GraphEntity? = null
            if (!initialEntityName.isNullOrBlank()) {
                center = entities.firstOrNull { it.name.equals(initialEntityName.trim(), ignoreCase = true) }
                if (center == null) {
                    val matching = graphRepository.getEntityByName(bookId, initialEntityName.trim())
                    if (matching != null && matching.confirmed && !matching.ignored && matching.firstChapterIndex <= currentChapter) {
                        center = matching
                    }
                }
            }
            if (center == null && initialEntityId != null) {
                center = entities.firstOrNull { it.id == initialEntityId }
            }
            if (center == null) {
                center = entities.firstOrNull()
            }

            if (center == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            buildEgoGraph(bookId, currentChapter, center, entities)
        }
    }

    fun setCenterEntity(entity: GraphEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value
            buildEgoGraph(currentState.bookId, currentState.currentChapter, entity, currentState.allEntities)
        }
    }

    fun selectNode(idx: Int?) {
        _uiState.update { it.copy(selectedNodeIdx = idx) }
    }

    private suspend fun buildEgoGraph(
        bookId: String,
        currentChapter: Int,
        center: GraphEntity,
        allEntities: List<GraphEntity>
    ) {
        val relations = graphRepository.getRelationsForEntity(bookId, center.id).firstOrNull() ?: emptyList()
        val filteredRelations = relations.filter { it.firstChapterIndex <= currentChapter }

        val neighborIds = filteredRelations.map {
            if (it.sourceId == center.id) it.targetId else it.sourceId
        }.distinct().toSet()

        val neighborEntities = neighborIds.mapNotNull { id ->
            allEntities.firstOrNull { it.id == id } ?: graphRepository.getEntityById(id)
        }.filter { it.confirmed && !it.ignored && it.firstChapterIndex <= currentChapter }

        val nodes = listOf(center) + neighborEntities.filter { it.id != center.id }

        val idToIndex = nodes.mapIndexed { idx, e -> e.id to idx }.toMap()
        val edges = filteredRelations.mapNotNull { r ->
            val s = idToIndex[r.sourceId]
            val t = idToIndex[r.targetId]
            if (s != null && t != null && s != t) Pair(s, t) else null
        }.distinct()

        _uiState.update {
            it.copy(
                allEntities = allEntities,
                centerEntity = center,
                nodes = nodes,
                edges = edges,
                directRelations = filteredRelations,
                selectedNodeIdx = 0,
                isLoading = false
            )
        }
    }

    fun addEntityFromSelection(
        bookId: String,
        name: String,
        type: EntityType,
        description: String,
        currentChapter: Int,
        locator: Locator?,
        onDone: ((GraphEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = name.trim()
            val existing = graphRepository.getEntityByName(bookId, trimmedName)
            val finalEntity: GraphEntity
            if (existing != null) {
                val updated = existing.copy(
                    confirmed = true,
                    ignored = false,
                    mentionCount = existing.mentionCount + 1,
                    description = if (description.isNotBlank()) description.trim() else existing.description
                )
                graphRepository.updateEntity(updated)
                finalEntity = updated
            } else {
                val newEntity = GraphEntity(
                    bookId = bookId,
                    name = trimmedName,
                    type = type,
                    description = description.trim(),
                    firstChapterIndex = currentChapter,
                    mentionCount = 1,
                    confirmed = true,
                    ignored = false
                )
                val id = graphRepository.insertEntity(newEntity)
                finalEntity = if (id > 0) newEntity.copy(id = id) else (graphRepository.getEntityByName(bookId, trimmedName) ?: newEntity)
            }

            if (locator != null && finalEntity.id > 0) {
                val mention = GraphMention(
                    entityId = finalEntity.id,
                    chapterIndex = currentChapter,
                    href = locator.href.toString(),
                    progression = locator.locations.progression ?: 0.0,
                    snippet = locator.text.highlight ?: ""
                )
                graphRepository.insertMentions(listOf(mention))
            }

            loadEgoGraph(bookId, currentChapter, initialEntityId = finalEntity.id)
            onDone?.invoke(finalEntity)
        }
    }

    suspend fun getMentionLocator(entityId: Long, currentChapter: Int): Locator? {
        val mention = graphRepository.nextMention(entityId, currentChapter)
            ?: graphRepository.prevMention(entityId, currentChapter)
            ?: return null

        val hrefUrl = Url(mention.href) ?: return null
        return Locator(
            href = hrefUrl,
            mediaType = MediaType("application/xhtml+xml") ?: MediaType.XHTML,
            locations = Locator.Locations(progression = mention.progression),
            text = Locator.Text(highlight = mention.snippet.ifEmpty { null })
        )
    }
}

/**
 * BottomSheet con el mini-grafo (ego graph: entidad + vecinos directos) del lector,
 * reutilizando GraphCanvas para renderizar la visualización de fuerzas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderEgoGraphBottomSheet(
    bookId: String,
    currentChapterIndex: Int,
    initialEntityName: String? = null,
    onDismiss: () -> Unit,
    onNavigateToFullGraph: () -> Unit,
    onJumpToLocator: (Locator) -> Unit,
    viewModel: ReaderEgoGraphViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId, currentChapterIndex, initialEntityName) {
        viewModel.loadEgoGraph(bookId, currentChapterIndex, initialEntityName)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    var layout by remember { mutableStateOf<ForceLayout?>(null) }

    LaunchedEffect(uiState.nodes, uiState.edges) {
        if (uiState.nodes.isNotEmpty()) {
            val fl = ForceLayout(uiState.nodes.size, uiState.edges)
            layout = fl
            withContext(Dispatchers.Default) {
                while (!fl.settled && isActive) {
                    fl.step()
                    tick++
                    delay(16L)
                }
            }
        } else {
            layout = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HiddenForest.Surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Fila de encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = HiddenForest.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Mini-Grafo de Relaciones",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToFullGraph) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Grafo completo",
                            tint = HiddenForest.Accent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = HiddenForest.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selector horizontal de entidad centro
            if (uiState.allEntities.isNotEmpty()) {
                Text(
                    text = "Entidad central (ego):",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.allEntities.forEach { entity ->
                        val isSelected = entity.id == uiState.centerEntity?.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCenterEntity(entity) },
                            label = {
                                Text(
                                    text = entity.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                selectedLabelColor = HiddenForest.Accent,
                                containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f),
                                labelColor = HiddenForest.TextSecondary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Canvas del grafo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A140F))
                    .border(0.5.dp, HiddenForest.Outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = HiddenForest.Accent,
                        strokeWidth = 2.5.dp
                    )
                } else if (uiState.nodes.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No hay entidades en este libro aún",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiddenForest.TextSecondary
                        )
                        Text(
                            text = "Selecciona texto en el lector para añadir personajes o lugares",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                    }
                } else {
                    val currentLayout = layout
                    if (currentLayout != null) {
                        GraphCanvas(
                            nodes = uiState.nodes,
                            edges = uiState.edges,
                            layout = currentLayout,
                            tick = tick,
                            selectedIdx = uiState.selectedNodeIdx,
                            onSelect = { viewModel.selectNode(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (uiState.edges.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Sin conexiones directas",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = HiddenForest.TextLow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Ficha de nodo seleccionado
            val selectedNode = uiState.nodes.getOrNull(uiState.selectedNodeIdx ?: 0) ?: uiState.centerEntity
            if (selectedNode != null) {
                val isCenter = selectedNode.id == uiState.centerEntity?.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HiddenForest.SurfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(entityColor(selectedNode.type))
                                )
                                Text(
                                    text = selectedNode.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = HiddenForest.Text
                                )
                                if (isCenter) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = HiddenForest.Accent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "CENTRO",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = HiddenForest.Accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${selectedNode.mentionCount} menciones",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.TextSecondary
                            )
                        }

                        if (selectedNode.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = selectedNode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextLow,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Relación directa si no es el centro
                        if (!isCenter && uiState.centerEntity != null) {
                            val rel = uiState.directRelations.firstOrNull {
                                (it.sourceId == selectedNode.id && it.targetId == uiState.centerEntity!!.id) ||
                                (it.sourceId == uiState.centerEntity!!.id && it.targetId == selectedNode.id)
                            }
                            if (rel != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Vínculo con ${uiState.centerEntity!!.name}: ${rel.label.ifBlank { "Relación directa" }} (peso: ${rel.weight})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = HiddenForest.Accent.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Botones de acción del nodo seleccionado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isCenter) {
                                Button(
                                    onClick = { viewModel.setCenterEntity(selectedNode) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HiddenForest.Accent.copy(alpha = 0.2f),
                                        contentColor = HiddenForest.Accent
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Centrar aquí", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val loc = viewModel.getMentionLocator(selectedNode.id, uiState.currentChapter)
                                        if (loc != null) {
                                            onJumpToLocator(loc)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ver en texto", fontSize = 12.sp, color = HiddenForest.Text)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para confirmar y guardar una nueva entidad seleccionada desde el texto.
 */
@Composable
fun AddEntityFromSelectionDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: EntityType, description: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf(EntityType.PERSONAJE) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HiddenForest.Surface,
        title = {
            Text(
                text = "Añadir al Grafo",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HiddenForest.Text
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HiddenForest.Accent,
                        focusedLabelColor = HiddenForest.Accent,
                        unfocusedTextColor = HiddenForest.Text,
                        focusedTextColor = HiddenForest.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tipo de entidad:",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EntityType.entries.forEach { type ->
                        val isSelected = type == selectedType
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = type },
                            label = { Text(type.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = entityColor(type).copy(alpha = 0.25f),
                                selectedLabelColor = entityColor(type),
                                containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f),
                                labelColor = HiddenForest.TextSecondary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HiddenForest.Accent,
                        focusedLabelColor = HiddenForest.Accent,
                        unfocusedTextColor = HiddenForest.Text,
                        focusedTextColor = HiddenForest.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), selectedType, description.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = "Guardar",
                    fontWeight = FontWeight.Bold,
                    color = if (name.isNotBlank()) HiddenForest.Accent else HiddenForest.TextLow
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = HiddenForest.TextSecondary)
            }
        }
    )
}
