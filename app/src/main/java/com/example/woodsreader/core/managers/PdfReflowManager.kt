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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReflowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager
) {
    companion object {
        private const val TAG = "PdfReflowManager"
        private const val WINDOW_SIZE = 10
        private const val BASE_FONT_SIZE = 12f
        private const val HEADER_THRESHOLD = 1.3f
        private const val LOAD_TIMEOUT = 10_000L // 10s para abrir el archivo
        private const val PAGE_TIMEOUT = 5_000L  // 5s por pÃ¡gina
    }

    private val pdfMutex = Mutex()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    private var isReflowTtsRunning = false
    private var reflowTtsJob: Job? = null
    var ttsUserStopped = false

    private var currentBookFilePath: String? = null
    private var onWordsProcessed: ((Long) -> Unit)? = null

    fun initialize(filePath: String, onWordsProcessedCallback: (Long) -> Unit) {
        currentBookFilePath = filePath
        onWordsProcessed = onWordsProcessedCallback
    }

    fun toggleReflowMode(scope: CoroutineScope, initialPosition: Int = 1) {
        val nextMode = !_isReflowMode.value
        _isReflowMode.value = nextMode
        if (nextMode) {
            lastLoadedPage = -1
            firstLoadedPage = -1
            _reflowState.update { it.copy(items = emptyList(), isLoading = true, currentLoadingPage = -1) } // -1 = Abriendo archivo
            _reflowElements.value = emptyList()
            scope.launch {
                loadReflowPages(fromPage = initialPosition, isInitial = true)
            }
        } else {
            pauseReflowTts()
            closeDocument()
            _reflowState.update { it.copy(isLoading = false) }
        }
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
        lastLoadedPage = -1
        firstLoadedPage = -1
        closeDocument() // Close previous document to prevent leak on re-open
        _reflowState.update { it.copy(items = emptyList(), isLoading = true, currentLoadingPage = -1) }
        _reflowElements.value = emptyList()
        scope.launch {
            loadReflowPages(fromPage = page, isInitial = true)
        }
    }
    private fun evictFarPages(items: List<ReflowItem>, targetPage: Int): List<ReflowItem> {
        val filtered = items.filter { kotlin.math.abs(it.pageNumber - targetPage) <= 12 }
        if (filtered.isNotEmpty()) {
            firstLoadedPage = filtered.minOf { it.pageNumber }
            lastLoadedPage = filtered.maxOf { it.pageNumber }
        } else {
            firstLoadedPage = targetPage
            lastLoadedPage = targetPage
        }
        return filtered
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

                    // 1. CARGA INSTANTÃNEA: PÃ¡gina actual completa (con timeout)
                    val initialItems = mutableListOf<ReflowItem>()
                    withTimeout(PAGE_TIMEOUT) {
                        _reflowState.update { it.copy(currentLoadingPage = targetPage) }
                        processSinglePage(document, targetPage, initialItems)
                    }
                    
                    _reflowState.update { current ->
                        val combined = if (isInitial) initialItems 
                                       else if (prepend) initialItems + current.items
                                       else current.items + initialItems
                        val evicted = evictFarPages(combined, targetPage)
                        current.copy(items = evicted, totalPages = total, isLoading = false, currentLoadingPage = targetPage)
                    }
                    _reflowElements.value = _reflowState.value.items
                    _isLoadingMoreReflow.value = false

                    // 2. CARGA DE FONDO: 2 adelante y 1 atrÃ¡s
                    if (isInitial) {
                        managerScope.launch {
                            loadRangeInBackground(document, targetPage + 1, minOf(total, targetPage + 2), append = true)
                            if (targetPage > 1) {
                                loadRangeInBackground(document, targetPage - 1, targetPage - 1, append = false)
                            }
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
        if (document !== currentDocument) return // Safety: don't use a closed document
        val range = if (append) start..end else (end downTo start)
        for (pIdx in range) {
            if (document !== currentDocument) return // Check again each iteration
            val pageItems = mutableListOf<ReflowItem>()
            try {
                pdfMutex.withLock {
                    withTimeout(PAGE_TIMEOUT) {
                        _reflowState.update { it.copy(currentLoadingPage = pIdx) }
                        processSinglePage(document, pIdx, pageItems)
                    }
                }
                if (pageItems.isNotEmpty()) {
                    _reflowState.update { current ->
                        val newList = if (append) current.items + pageItems else pageItems + current.items
                        val evicted = evictFarPages(newList, pIdx)
                        current.copy(items = evicted)
                    }
                    _reflowElements.value = _reflowState.value.items
                }
            } catch (e: Exception) {
                Timber.w(e, "Error cargando pÃ¡gina de fondo $pIdx")
            }
        }
    }

    private fun processSinglePage(document: PDDocument, pageIndex: Int, targetList: MutableList<ReflowItem>) {
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

            val semanticElements = textAnalyzer.analyzeLines(lineInfos, pageIndex)
            semanticElements.forEach { element ->
                when (element) {
                    is TextAnalyzer.SemanticElement.Header -> targetList.add(ReflowItem.Header(element.rawText, element.level, element.pageNumber))
                    is TextAnalyzer.SemanticElement.Paragraph -> targetList.add(ReflowItem.Paragraph(element.rawText, element.pageNumber))
                    else -> {}
                }
            }

            // 2. ImÃ¡genes (Solo si el texto es poco o siempre?)
            extractImagesFromPage(document, pageIndex - 1, targetList)
            
            // 3. Palabra count estimado
            val estimatedWords = targetList.filterIsInstance<ReflowItem.Paragraph>().sumOf { it.content.split("\\s+".toRegex()).size }
            if (estimatedWords > 0) onWordsProcessed?.invoke(estimatedWords.toLong())

        } catch (e: Exception) {
            Timber.e(e, "Error procesando pÃ¡gina $pageIndex")
        }
    }

    private fun extractImagesFromPage(document: PDDocument, pageIndex: Int, targetList: MutableList<ReflowItem>) {
        try {
            val resources = document.getPage(pageIndex).resources ?: return
            extractImagesFromResources(resources, pageIndex, targetList)
        } catch (_: Exception) {}
    }

    private fun extractImagesFromResources(resources: com.tom_roush.pdfbox.pdmodel.PDResources, pageIndex: Int, targetList: MutableList<ReflowItem>) {
        try {
            resources.xObjectNames?.forEach { name ->
                val xObject = resources.getXObject(name)
                if (xObject is PDImageXObject) {
                    extractAndCompressImage(xObject, 600)?.let {
                        targetList.add(ReflowItem.Image(it, pageIndex + 1))
                    }
                } else if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) {
                    val formResources = xObject.resources
                    if (formResources != null) {
                        extractImagesFromResources(formResources, pageIndex, targetList)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractAndCompressImage(xObject: PDImageXObject, reqWidth: Int): ByteArray? {
        return try {
            val sourceBitmap = xObject.image ?: return null
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            
            val targetWidth = if (width > reqWidth) reqWidth else width
            val targetHeight = (height * (targetWidth.toFloat() / width)).toInt()
            
            val resized = if (width > reqWidth) {
                Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
            } else {
                sourceBitmap
            }
            
            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.WEBP, 50, stream) // Calidad 50 para mÃ¡xima velocidad
            val result = stream.toByteArray()
            
            if (resized != sourceBitmap) resized.recycle()
            result
        } catch (e: Exception) {
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
        val locale = languageCode?.let { java.util.Locale.forLanguageTag(it) } ?: java.util.Locale.getDefault()
        if (!ttsManager.initialize(locale, voiceName)) return
        registerNoisyReceiver()
        val elements = _reflowElements.value
        if (elements.isEmpty() || startIndex >= elements.size) {
            _ttsState.value = TtsState.STOPPED
            return
        }

        ttsManager.setRate(speed)
        if (pitch != null) {
            ttsManager.setPitch(pitch)
        }
        if (voiceName != null) {
            ttsManager.setVoice(voiceName)
        }
        _ttsActiveBlockIndex.value = startIndex
        _ttsState.value = TtsState.PLAYING
        isReflowTtsRunning = true
        ttsUserStopped = false

        reflowTtsJob?.cancel()
        reflowTtsJob = scope.launch {
            for (i in startIndex until elements.size) {
                if (!isActive || !isReflowTtsRunning || ttsUserStopped) break
                
                _ttsActiveBlockIndex.value = i
                val text = when (val item = elements[i]) {
                    is ReflowItem.Header -> item.content
                    is ReflowItem.Paragraph -> item.content
                    else -> ""
                }
                
                if (text.isBlank()) continue
                _currentTtsPage.value = elements[i].pageNumber
                _currentTtsSentence.value = text
                
                try {
                    ttsManager.speak(text)
                } catch (_: Exception) { break }
            }
            if (isActive && !ttsUserStopped) {
                _ttsState.value = TtsState.STOPPED
                isReflowTtsRunning = false
                unregisterNoisyReceiver()
            }
        }
    }

    fun pauseReflowTts() {
        ttsUserStopped = true
        isReflowTtsRunning = false
        reflowTtsJob?.cancel()
        ttsManager.stop()
        _ttsState.value = TtsState.PAUSED
        unregisterNoisyReceiver()
    }

    fun stopReflowTts() {
        pauseReflowTts()
        _ttsState.value = TtsState.STOPPED
    }

    fun onSpeedChange(speed: Float, scope: CoroutineScope) {
        if (isReflowTtsRunning) {
            val idx = _ttsActiveBlockIndex.value
            pauseReflowTts()
            scope.launch { playReflowTts(scope, speed = speed, startIndex = idx) }
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
        reflowTtsJob?.cancel()
        closeDocument()
        unregisterNoisyReceiver()
        managerScope.cancel()
    }
}




