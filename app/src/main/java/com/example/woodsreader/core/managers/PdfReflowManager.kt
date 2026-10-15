package com.example.woodsreader.core.managers

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.core.managers.pdf.analysis.TextAnalyzer
import com.example.woodsreader.core.pipeline.ReadingPipeline
import com.example.woodsreader.core.pipeline.TtsNarrationUnit
import com.example.woodsreader.core.pipeline.ReadingBlock
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.data.model.ReflowState
import com.example.woodsreader.ui.reader.TtsState
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.ExperimentalReadiumApi
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import com.example.woodsreader.core.pipeline.NoiseClassifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReflowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager,
    private val readingPipeline: ReadingPipeline,
    private val noiseClassifier: NoiseClassifier,
    private val ttsRuleDao: com.example.woodsreader.data.database.TtsRuleDao
) {
    companion object {
        private const val TAG = "PdfReflowManager"
        private const val WINDOW_SIZE = 10
        private const val BASE_FONT_SIZE = 12f
        private const val HEADER_THRESHOLD = 1.3f
        private const val LOAD_TIMEOUT = 10_000L // 10s para abrir el archivo
        private const val PAGE_TIMEOUT = 5_000L  // 5s por página
    }

    private val pdfMutex = Mutex()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var preloadJob: Job? = null
    private val pageBoundariesMap = java.util.concurrent.ConcurrentHashMap<Int, Pair<String?, String?>>()
    @Volatile
    private var knownRepetitiveHeadersFooters: Set<String> = emptySet()

    private var lastLoadedPage: Int = -1
    private var firstLoadedPage: Int = -1
    private val textAnalyzer = TextAnalyzer(
        baseFontSize = BASE_FONT_SIZE,
        headerThreshold = HEADER_THRESHOLD
    )

    private var currentDocument: PDDocument? = null
    @Volatile
    private var isCurrentlyLoading = false

    private val _isReflowMode = MutableStateFlow(false)
    val isReflowMode = _isReflowMode.asStateFlow()

    private val _reflowElements = MutableStateFlow<List<ReflowItem>>(emptyList())
    val reflowElements = _reflowElements.asStateFlow()

    private val _isLoadingMoreReflow = MutableStateFlow(false)
    val isLoadingMoreReflow = _isLoadingMoreReflow.asStateFlow()

    private val _reflowState = MutableStateFlow(ReflowState())
    val reflowState = _reflowState.asStateFlow()

    private val _ttsActiveBlockIndex = MutableStateFlow(-1)
    val ttsActiveBlockIndex = _ttsActiveBlockIndex.asStateFlow()

    private val _currentTtsPage = MutableStateFlow(1)
    val currentTtsPage = _currentTtsPage.asStateFlow()

    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    val ttsState = _ttsState.asStateFlow()

    private val _currentTtsSentence = MutableStateFlow("")
    val currentTtsSentence = _currentTtsSentence.asStateFlow()

    // Unidades de narración producidas por ReadingPipeline (para TTS limpio)
    private val _ttsNarrationUnits = MutableStateFlow<List<TtsNarrationUnit>>(emptyList())
    val ttsNarrationUnits = _ttsNarrationUnits.asStateFlow()

    private var isReflowTtsRunning = false
    private var reflowTtsJob: Job? = null
    var ttsUserStopped = false

    private var currentBookFilePath: String? = null
    private var currentBookId: String? = null
    private var onWordsProcessed: ((Long) -> Unit)? = null
    private var lastPitch: Float? = null
    private var lastLanguageCode: String? = null
    private var lastVoiceName: String? = null

    fun initialize(
        filePath: String,
        bookId: String? = null,
        onWordsProcessedCallback: (Long) -> Unit
    ) {
        currentBookFilePath = filePath
        currentBookId = bookId
        onWordsProcessed = onWordsProcessedCallback
    }

    fun setReflowMode(enabled: Boolean, scope: CoroutineScope, initialPosition: Int = 1) {
        preloadJob?.cancel()
        preloadJob = null
        _isReflowMode.value = enabled
        if (enabled) {
            lastLoadedPage = -1
            firstLoadedPage = -1
            _reflowState.update { it.copy(items = emptyList(), isLoading = true, currentLoadingPage = -1) } // -1 = Abriendo archivo
            _reflowElements.value = emptyList()
            _ttsNarrationUnits.value = emptyList()
            scope.launch {
                loadReflowPages(fromPage = initialPosition, isInitial = true)
            }
        } else {
            pauseReflowTts()
            closeDocument()
            _reflowState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleReflowMode(scope: CoroutineScope, initialPosition: Int = 1) {
        setReflowMode(!_isReflowMode.value, scope, initialPosition)
    }

    fun setTtsActiveBlockIndex(index: Int) { _ttsActiveBlockIndex.value = index }

    fun loadNextPage(scope: CoroutineScope) {
        if (isCurrentlyLoading) return
        val state = _reflowState.value
        if (state.totalPages > 0 && lastLoadedPage >= state.totalPages) return
        
        scope.launch {
            loadReflowPages(fromPage = lastLoadedPage + 1, isInitial = false, prepend = false)
        }
    }

    fun loadPreviousPage(scope: CoroutineScope) {
        if (isCurrentlyLoading) return
        if (firstLoadedPage <= 1) return
        
        scope.launch {
            loadReflowPages(fromPage = firstLoadedPage - 1, isInitial = false, prepend = true)
        }
    }

    fun jumpToPage(scope: CoroutineScope, page: Int) {
        preloadJob?.cancel()
        preloadJob = null
        pauseReflowTts()
        lastLoadedPage = -1
        firstLoadedPage = -1
        _reflowState.update { it.copy(items = emptyList(), isLoading = true, currentLoadingPage = page) }
        _reflowElements.value = emptyList()
        _ttsNarrationUnits.value = emptyList()
        scope.launch {
            loadReflowPages(fromPage = page, isInitial = true)
        }
    }
    private fun evictFarPages(items: List<ReflowItem>, targetPage: Int): List<ReflowItem> {
        val filtered = items.filter { item ->
            val pageDiff = kotlin.math.abs(item.pageNumber - targetPage)
            if (item is ReflowItem.Image) {
                // Las imágenes consumen bastante memoria en el heap; solo retenerlas a +/- 2 páginas
                pageDiff <= 2
            } else {
                pageDiff <= 5
            }
        }
        if (filtered.isNotEmpty()) {
            firstLoadedPage = filtered.minOf { it.pageNumber }
            lastLoadedPage = filtered.maxOf { it.pageNumber }
        } else {
            firstLoadedPage = targetPage
            lastLoadedPage = targetPage
        }
        return filtered
    }

    private fun sampleDocumentHeadersFooters(document: PDDocument, total: Int) {
        if (total < 3) return
        val sampleMax = minOf(total, 7)
        for (p in 1..sampleMax) {
            if (pageBoundariesMap.containsKey(p)) continue
            try {
                val sampleLines = mutableListOf<TextAnalyzer.LineInfo>()
                val sampleStripper = object : PDFTextStripper() {
                    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                        super.writeString(text, textPositions)
                        if (!text.isNullOrBlank() && !textPositions.isNullOrEmpty()) {
                            val fontSize = textPositions.firstOrNull()?.fontSize ?: 0f
                            val y = textPositions.firstOrNull()?.yDirAdj ?: 0f
                            val xStart = textPositions.minOfOrNull { it.xDirAdj } ?: 0f
                            val xEnd = textPositions.maxOfOrNull { it.xDirAdj + it.widthDirAdj } ?: 0f
                            sampleLines.add(TextAnalyzer.LineInfo(text, fontSize, y, xStart, xEnd))
                        }
                    }
                }
                sampleStripper.sortByPosition = true
                sampleStripper.startPage = p
                sampleStripper.endPage = p
                sampleStripper.writeText(document, java.io.StringWriter())
                val first = sampleLines.firstOrNull()?.text
                val last = sampleLines.lastOrNull()?.text
                pageBoundariesMap[p] = Pair(first, last)
            } catch (_: Exception) {}
        }
        updateRepetitiveHeadersFooters()
    }

    private fun updateRepetitiveHeadersFooters() {
        if (pageBoundariesMap.size >= 3) {
            knownRepetitiveHeadersFooters = noiseClassifier.detectRepetitiveHeadersFooters(pageBoundariesMap.values.toList())
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    suspend fun loadReflowPages(fromPage: Int = 1, isInitial: Boolean = false, prepend: Boolean = false) {
        if (isCurrentlyLoading && !isInitial) return
        
        pdfMutex.withLock {
            val filePath = currentBookFilePath ?: return@withLock
            isCurrentlyLoading = true
            
            _reflowState.update { it.copy(isLoading = isInitial, currentLoadingPage = if (currentDocument == null) -1 else fromPage) } 
            _isLoadingMoreReflow.value = true
            
            withContext(Dispatchers.IO) {
                try {
                    if (currentDocument == null) {
                        withTimeout(LOAD_TIMEOUT) {
                            val uri = Uri.parse(filePath)
                            val inputStream = if (uri.scheme == "content") {
                                context.contentResolver.openInputStream(uri)
                            } else {
                                File(filePath).inputStream()
                            } ?: throw Exception("Stream null")
                            inputStream.use { stream ->
                                currentDocument = PDDocument.load(stream)
                            }
                        }
                    }
                    val document = currentDocument ?: return@withContext
                    val total = document.numberOfPages
                    val targetPage = fromPage.coerceIn(1, total)

                    if (knownRepetitiveHeadersFooters.isEmpty() && total >= 3) {
                        sampleDocumentHeadersFooters(document, total)
                    }

                    // 1. CARGA INSTANTÁNEA: Página actual completa (con timeout)
                    val initialItems = mutableListOf<ReflowItem>()
                    withTimeout(PAGE_TIMEOUT) {
                        _reflowState.update { it.copy(currentLoadingPage = targetPage) }
                        processSinglePage(document, targetPage, initialItems)
                    }
                    
                    _reflowState.update { current ->
                        val filteredItems = current.items.filterNot { it.pageNumber == targetPage }
                        val combined = if (isInitial) initialItems 
                                       else if (prepend) initialItems + filteredItems
                                       else filteredItems + initialItems
                        val evicted = evictFarPages(combined, targetPage)
                        current.copy(items = evicted, totalPages = total, isLoading = false, currentLoadingPage = targetPage)
                    }
                    _reflowElements.value = _reflowState.value.items
                    _isLoadingMoreReflow.value = false

                    // 2. PRECARGA DE FONDO: solo hacia adelante para no desplazar la posición inicial
                    if (isInitial) {
                        preloadJob?.cancel()
                        preloadJob = managerScope.launch {
                            loadRangeInBackground(document, targetPage + 1, minOf(total, targetPage + 2), append = true)
                        }
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error loadReflowPages: ${e.message}")
                    _reflowState.update { it.copy(isLoading = false, error = e.message) }
                } finally {
                    isCurrentlyLoading = false
                    _isLoadingMoreReflow.value = false
                }
            }
        }
    }

    private suspend fun loadRangeInBackground(document: PDDocument, start: Int, end: Int, append: Boolean) {
        if (start > end) return
        if (!_isReflowMode.value || currentDocument == null || document !== currentDocument) return
        val range = if (append) start..end else (end downTo start)
        for (pIdx in range) {
            if (!_isReflowMode.value || currentDocument == null || document !== currentDocument) return
            // Evitar duplicar página si ya fue agregada por carga directa
            if (_reflowState.value.items.any { it.pageNumber == pIdx }) continue

            val pageItems = mutableListOf<ReflowItem>()
            try {
                pdfMutex.withLock {
                    if (!_isReflowMode.value || currentDocument == null || document !== currentDocument) return
                    withTimeout(PAGE_TIMEOUT) {
                        _reflowState.update { it.copy(currentLoadingPage = pIdx) }
                        processSinglePage(document, pIdx, pageItems)
                    }
                }
                if (pageItems.isNotEmpty() && _isReflowMode.value && currentDocument != null && document === currentDocument) {
                    _reflowState.update { current ->
                        if (!_isReflowMode.value || currentDocument == null || document !== currentDocument) return@update current
                        if (current.items.any { it.pageNumber == pIdx }) return@update current
                        val newList = if (append) current.items + pageItems else pageItems + current.items
                        val evicted = evictFarPages(newList, pIdx)
                        current.copy(items = evicted)
                    }
                    if (_isReflowMode.value && currentDocument != null && document === currentDocument) {
                        _reflowElements.value = _reflowState.value.items
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error cargando página de fondo $pIdx")
            }
        }
    }

    private suspend fun processSinglePage(document: PDDocument, pageIndex: Int, targetList: MutableList<ReflowItem>) {
        try {
            val page = document.getPage(pageIndex - 1)
            
            // 1. Texto
            val lineInfos = mutableListOf<TextAnalyzer.LineInfo>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    super.writeString(text, textPositions)
                    if (!text.isNullOrBlank() && textPositions != null && textPositions.isNotEmpty()) {
                        val fontSize = textPositions.firstOrNull()?.fontSize ?: 0f
                        val y = textPositions.firstOrNull()?.yDirAdj ?: 0f
                        val xStart = textPositions.minOfOrNull { it.xDirAdj } ?: 0f
                        val xEnd = textPositions.maxOfOrNull { it.xDirAdj + it.widthDirAdj } ?: 0f
                        lineInfos.add(TextAnalyzer.LineInfo(text, fontSize, y, xStart, xEnd))
                    }
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = pageIndex
            stripper.endPage = pageIndex
            
            // Dummy writer to avoid NPE in PDFTextStripper.getText()
            val dummyWriter = java.io.StringWriter()
            stripper.writeText(document, dummyWriter)

            // 2. Convertir lineas a RawDocumentBlock (tuberia unificada)
            val rawBlocks = textAnalyzer.convertPageToRawBlocks(lineInfos, pageIndex)

            // Registrar límites de página para detección de encabezados y pies repetidos entre páginas
            pageBoundariesMap[pageIndex] = Pair(lineInfos.firstOrNull()?.text, lineInfos.lastOrNull()?.text)
            updateRepetitiveHeadersFooters()

            // 3. Pasar por ReadingPipeline: filtro de ruido, reglas activas del libro, formato tipografico, TTS units
            val activeRules = try {
                ttsRuleDao.getActiveRulesForBook(currentBookId).map {
                    com.example.woodsreader.core.pipeline.WordExclusionRule(
                        id = it.id.toString(),
                        target = it.pattern,
                        replacement = it.replacement,
                        isRegex = it.isRegex,
                        bookId = it.bookId
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }

            val processed = readingPipeline.process(
                rawBlocks = rawBlocks,
                rules = activeRules,
                stripNoiseFromDisplay = true,
                knownRepetitiveHeadersFooters = knownRepetitiveHeadersFooters
            )

            // 4. Acumular unidades de narracion limpias para TTS (deduplicando por página)
            _ttsNarrationUnits.update { current ->
                val filtered = current.filterNot { it.pageIndex == pageIndex }
                filtered + processed.ttsNarrationUnits
            }

            // 5. Mapear ReadingBlock a ReflowItem para la UI con IDs estables
            var blockIdx = 0
            for (block in processed.displayBlocks) {
                val blockId = "p${pageIndex}_blk_${blockIdx++}"
                when (block) {
                    is ReadingBlock.Heading -> targetList.add(
                        ReflowItem.Header(block.text, block.level, block.pageIndex, blockId)
                    )
                    is ReadingBlock.Paragraph -> targetList.add(
                        ReflowItem.Paragraph(block.text, block.pageIndex, blockId)
                    )
                    is ReadingBlock.Image, is ReadingBlock.Noise -> {
                        // Imagenes se extraen por separado; ruido no llega aqui
                    }
                }
            }

            // 6. Imagenes extraidas directamente del documento PDF
            extractImagesFromPage(document, pageIndex - 1, targetList)

            // 7. Contabilizar palabras procesadas
            val wordCount = processed.metrics.wordsProcessed
            if (wordCount > 0) onWordsProcessed?.invoke(wordCount)

        } catch (e: Exception) {
            Timber.e(e, "Error procesando página $pageIndex")
        }
    }

    private fun extractImagesFromPage(document: PDDocument, pageIndex: Int, targetList: MutableList<ReflowItem>) {
        try {
            val page = document.getPage(pageIndex)
            val resources = page.resources ?: return
            extractImagesFromResources(resources, pageIndex, targetList, intArrayOf(0))
        } catch (_: Exception) {}
    }

    private fun extractImagesFromResources(
        resources: com.tom_roush.pdfbox.pdmodel.PDResources,
        pageIndex: Int,
        targetList: MutableList<ReflowItem>,
        imgCounter: IntArray
    ) {
        try {
            resources.xObjectNames?.forEach { name ->
                if (imgCounter[0] >= 4) return@forEach // Límite de 4 imágenes por página para proteger RAM
                val xObject = resources.getXObject(name)
                if (xObject is PDImageXObject) {
                    extractAndCompressImage(xObject, 600)?.let { bytes ->
                        val currentIdx = imgCounter[0]++
                        val imageId = "p${pageIndex + 1}_img_${currentIdx}_${bytes.size}"
                        targetList.add(ReflowItem.Image(imageBytes = bytes, pageNumber = pageIndex + 1, id = imageId))
                    }
                } else if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) {
                    val formResources = xObject.resources
                    if (formResources != null) {
                        extractImagesFromResources(formResources, pageIndex, targetList, imgCounter)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractAndCompressImage(xObject: PDImageXObject, reqWidth: Int): ByteArray? {
        val sourceBitmap = try {
            xObject.image
        } catch (t: Throwable) {
            null
        } ?: return null

        return try {
            val width = sourceBitmap.width
            val height = sourceBitmap.height

            // Descartar iconos, separadores o imágenes diminutas (< 48x48) para no saturar memoria
            if (width < 48 || height < 48) {
                sourceBitmap.recycle()
                return null
            }

            val targetWidth = if (width > reqWidth) reqWidth else width
            val targetHeight = (height * (targetWidth.toFloat() / width)).toInt().coerceAtLeast(1)

            val resized = if (width > reqWidth) {
                Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
            } else {
                sourceBitmap
            }

            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.WEBP, 50, stream) // Calidad 50 para máxima velocidad
            val result = stream.toByteArray()

            // Liberación inmediata de memoria nativa GPU/Bitmap
            if (resized != sourceBitmap) {
                resized.recycle()
            }
            sourceBitmap.recycle()

            result
        } catch (e: Exception) {
            try { sourceBitmap.recycle() } catch (_: Throwable) {}
            null
        }
    }

    private var isReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseReflowTts()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isReceiverRegistered) {
            try {
                context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                isReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }
    }

    suspend fun playReflowTts(
        scope: CoroutineScope,
        speed: Float,
        pitch: Float? = null,
        languageCode: String? = null,
        voiceName: String? = null,
        startIndex: Int = _ttsActiveBlockIndex.value.coerceAtLeast(0)
    ) {
        lastPitch = pitch
        lastLanguageCode = languageCode
        lastVoiceName = voiceName
        val locale = languageCode?.let { java.util.Locale.forLanguageTag(it) } ?: java.util.Locale.getDefault()
        if (!ttsManager.initialize(locale, voiceName)) return
        registerNoisyReceiver()

        // Iterar directamente sobre los elementos legibles de reflowState.items
        // para que ttsActiveBlockIndex coincida exactamente 1:1 con readableItems de la UI
        val currentItems = _reflowState.value.items
        val readableItems = currentItems.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }

        if (readableItems.isEmpty()) {
            _ttsState.value = TtsState.STOPPED
            return
        }
        val safeStartIndex = startIndex.coerceIn(0, readableItems.size - 1)

        ttsManager.setRate(speed)
        if (pitch != null) {
            ttsManager.setPitch(pitch)
        }
        if (voiceName != null) {
            ttsManager.setVoice(voiceName)
        }
        _ttsActiveBlockIndex.value = safeStartIndex
        _ttsState.value = TtsState.PLAYING
        isReflowTtsRunning = true
        ttsUserStopped = false

        reflowTtsJob?.cancel()
        reflowTtsJob = scope.launch {
            var i = safeStartIndex
            while (isActive && isReflowTtsRunning && !ttsUserStopped) {
                val currentReadable = _reflowState.value.items.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
                if (i >= currentReadable.size) {
                    val state = _reflowState.value
                    if (state.totalPages > 0 && lastLoadedPage < state.totalPages && !isCurrentlyLoading) {
                        loadNextPage(scope)
                        delay(600)
                        val updated = _reflowState.value.items.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
                        if (i < updated.size) continue
                    }
                    break
                }

                _ttsActiveBlockIndex.value = i

                val item = currentReadable[i]
                val text = when (item) {
                    is ReflowItem.Header -> item.content
                    is ReflowItem.Paragraph -> item.content
                    is ReflowItem.Text -> item.element.locator.text.highlight ?: ""
                    else -> ""
                }
                val pageNum = item.pageNumber

                // Si nos acercamos al final de los items cargados, precargar siguiente página con antelación
                if (i >= currentReadable.size - 2) {
                    val state = _reflowState.value
                    if (state.totalPages > 0 && lastLoadedPage < state.totalPages && !isCurrentlyLoading) {
                        loadNextPage(scope)
                    }
                }

                if (text.isNotBlank()) {
                    _currentTtsPage.value = pageNum
                    val sentences = splitSentences(text)
                    for (sentence in sentences) {
                        if (!isActive || !isReflowTtsRunning || ttsUserStopped) break
                        if (sentence.isNotBlank()) {
                            _currentTtsSentence.value = sentence
                            try {
                                ttsManager.speak(sentence)
                            } catch (_: Exception) { break }
                        }
                    }
                }

                i++
            }
            if (isActive && !ttsUserStopped) {
                _currentTtsSentence.value = ""
                _ttsState.value = TtsState.STOPPED
                isReflowTtsRunning = false
                unregisterNoisyReceiver()
            }
        }
    }

    private fun splitSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length < 50) return listOf(trimmed)
        val pattern = Regex("""(?<=[.!?…])\s+(?=[«"—\p{Lu}\p{Lt}])""")
        val parts = trimmed.split(pattern).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.isNotEmpty()) parts else listOf(trimmed)
    }

    fun pauseReflowTts() {
        ttsUserStopped = true
        isReflowTtsRunning = false
        reflowTtsJob?.cancel()
        reflowTtsJob = null
        ttsManager.stop()
        _ttsState.value = TtsState.PAUSED
        unregisterNoisyReceiver()
    }

    fun stopReflowTts() {
        ttsUserStopped = true
        isReflowTtsRunning = false
        reflowTtsJob?.cancel()
        reflowTtsJob = null
        ttsManager.stop()
        _currentTtsSentence.value = ""
        _ttsActiveBlockIndex.value = -1
        _ttsState.value = TtsState.STOPPED
        unregisterNoisyReceiver()
    }

    fun onSpeedChange(speed: Float, scope: CoroutineScope) {
        ttsManager.setRate(speed)
        if (isReflowTtsRunning) {
            val idx = _ttsActiveBlockIndex.value
            pauseReflowTts()
            scope.launch {
                playReflowTts(
                    scope = scope,
                    speed = speed,
                    pitch = lastPitch,
                    languageCode = lastLanguageCode,
                    voiceName = lastVoiceName,
                    startIndex = idx
                )
            }
        }
    }

    fun getIsReflowTtsRunning() = isReflowTtsRunning

    fun getAvailableVoices(): List<String> = ttsManager.getAvailableVoices()
    fun setVoice(voiceName: String) { ttsManager.setVoice(voiceName) }

    suspend fun warmUpTtsEngine(locale: java.util.Locale, voiceName: String?): Boolean {
        return ttsManager.initialize(locale, voiceName)
    }

    fun closeDocument() {
        val docToClose = currentDocument
        currentDocument = null
        // Usamos un nuevo scope independiente del lifecycle para asegurar que el recurso
        // nativo SIEMPRE se libere, incluso si managerScope es cancelado un milisegundo despues
        CoroutineScope(Dispatchers.IO).launch {
            pdfMutex.withLock {
                try {
                    docToClose?.close()
                } catch (e: Exception) {
                    Timber.w(e, "Error closing PDF document")
                }
            }
        }
    }

    fun onCleared() {
        preloadJob?.cancel()
        preloadJob = null
        reflowTtsJob?.cancel()
        reflowTtsJob = null
        closeDocument()
        unregisterNoisyReceiver()
        _isReflowMode.value = false
        isCurrentlyLoading = false
        _reflowElements.value = emptyList()
        _reflowState.value = ReflowState()
        _ttsNarrationUnits.value = emptyList()
        currentBookFilePath = null
        onWordsProcessed = null
        pageBoundariesMap.clear()
        knownRepetitiveHeadersFooters = emptySet()
        currentBookId = null
        lastPitch = null
        lastLanguageCode = null
        lastVoiceName = null
    }
}




