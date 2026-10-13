package com.example.woodsreader.ui.reader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.service.ReaderTtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.woodsreader.core.pipeline.EpubContentExtractor
import com.example.woodsreader.core.pipeline.ProcessedDocument
import com.example.woodsreader.core.pipeline.ReadingPipeline
import com.example.woodsreader.core.pipeline.WordExclusionRule
import kotlinx.coroutines.withContext
import org.readium.navigator.media.common.MediaNavigator
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import javax.inject.Inject

class EpubFormatManager @Inject constructor(
    private val readingPipeline: ReadingPipeline,
    private val epubContentExtractor: EpubContentExtractor
) {

    // Constructor secundario sin anotación para tests unitarios
    constructor() : this(
        readingPipeline = ReadingPipeline(
            noiseClassifier = com.example.woodsreader.core.pipeline.NoiseClassifier(),
            imageAnnouncer = com.example.woodsreader.core.pipeline.ImageAnnouncer(),
            wordExclusionEngine = com.example.woodsreader.core.pipeline.WordExclusionEngine(),
            spanishTextFormatter = com.example.woodsreader.core.pipeline.SpanishTextFormatter()
        ),
        epubContentExtractor = EpubContentExtractor()
    )
    
    companion object {
        private const val TAG = "EpubFormatManager"
        private const val SERVICE_BIND_TIMEOUT_MS = 10_000L
    }

    private var ttsService: ReaderTtsService? = null
    private var currentPublication: Publication? = null
    private var currentScope: CoroutineScope? = null
    private var navigator: Navigator? = null
    
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound = _isServiceBound.asStateFlow()

    private var serviceFlowJob: Job? = null

    private val _currentTtsSentence = MutableStateFlow("")
    val currentTtsSentence = _currentTtsSentence.asStateFlow()

    private val _currentTtsLocator = MutableStateFlow<Locator?>(null)
    val currentTtsLocator = _currentTtsLocator.asStateFlow()

    private val _currentTtsRange = MutableStateFlow<IntRange?>(null)
    val currentTtsRange = _currentTtsRange.asStateFlow()

    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    val ttsState = _ttsState.asStateFlow()

    var ttsUserStopped = false
    private var hasEverStartedTts = false

    private var lastProcessedHref: String? = null
    private val _currentProcessedDocument = MutableStateFlow<ProcessedDocument?>(null)
    val currentProcessedDocument = _currentProcessedDocument.asStateFlow()
    
    private var onTtsStateChanged: ((ReadingMode, String) -> Unit)? = null
    private var onLocatorChanged: ((Locator) -> Unit)? = null

    fun updateNavigator(nav: Navigator?) {
        this.navigator = nav
    }

    private var pendingSpeed: Float? = null
    private var pendingPitch: Float? = null
    private var pendingLanguage: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ReaderTtsService.TtsBinder
            ttsService = binder?.getService()
            _isServiceBound.value = true
            ttsService?.preWarmEngine()
            pendingSpeed?.let { spd ->
                ttsService?.updateSettings(spd, pendingPitch ?: 1.0f, pendingLanguage)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            _isServiceBound.value = false
        }
    }

    fun initialize(context: Context, stateChanged: (ReadingMode, String) -> Unit, locatorChanged: (Locator) -> Unit) {
        onTtsStateChanged = stateChanged
        onLocatorChanged = locatorChanged
        val intent = Intent(context, ReaderTtsService::class.java)
        // Solo vinculamos el servicio al inicio. startForegroundService() se llamará 
        // únicamente cuando el usuario inicie la reproducción (playTts),
        // para cumplir con las políticas de Android 12+ de no arrancar servicios
        // en primer plano sin mostrar una notificación inmediata.
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Suspends until the TTS service is bound, with a configurable timeout.
     * @return true if the service was bound successfully, false on timeout.
     */
    suspend fun awaitServiceBinding(timeoutMs: Long = SERVICE_BIND_TIMEOUT_MS): Boolean {
        if (_isServiceBound.value) return true
        return withTimeoutOrNull(timeoutMs) {
            isServiceBound.first { it }
            true
        } ?: run {
            Log.w(TAG, "TTS service binding timed out after ${timeoutMs}ms — TTS will be unavailable")
            false
        }
    }

    suspend fun initNavigator(pub: Publication, initialLocator: Locator?, scope: CoroutineScope, navigator: Navigator?) {
        this.currentPublication = pub
        this.currentScope = scope
        this.navigator = navigator ?: this.navigator
        if (initialLocator != null) {
            processResource(initialLocator.href.toString())
        }
        if (ttsService == null) {
            Log.w(TAG, "initNavigator: service not bound yet, awaiting...")
            if (!awaitServiceBinding()) {
                Log.e(TAG, "initNavigator: service binding failed, TTS unavailable")
                return
            }
        }
        ttsService?.initNavigator(pub, initialLocator)
        observeServiceFlows(scope)
    }

    suspend fun processResource(
        href: String,
        rules: List<WordExclusionRule> = emptyList()
    ): ProcessedDocument? = withContext(Dispatchers.Default) {
        if (href.isBlank()) return@withContext null
        val targetRes = href.substringAfterLast('/').substringBefore('#')
        val lastRes = lastProcessedHref?.substringAfterLast('/')?.substringBefore('#')
        if (targetRes.isNotEmpty() && targetRes == lastRes && _currentProcessedDocument.value != null) {
            return@withContext _currentProcessedDocument.value
        }
        val pub = currentPublication ?: return@withContext null
        val baseHref = href.substringBefore('#')
        val link = pub.readingOrder.find { 
            it.href.toString() == href || it.href.toString() == baseHref || it.href.toString().substringAfterLast('/') == targetRes 
        } ?: pub.manifest.resources.find { 
            it.href.toString() == href || it.href.toString() == baseHref || it.href.toString().substringAfterLast('/') == targetRes 
        } ?: return@withContext null

        val html = try {
            pub.get(link)?.read()?.getOrNull()?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read resource $href: ${e.message}")
            null
        } ?: return@withContext null

        val rawBlocks = epubContentExtractor.extractBlocks(html, href)
        val processed = readingPipeline.process(rawBlocks, rules = rules, announceImages = true)
        lastProcessedHref = href
        _currentProcessedDocument.value = processed
        processed
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun observeServiceFlows(scope: CoroutineScope) {
        serviceFlowJob?.cancel()
        val service = ttsService ?: return
        
        serviceFlowJob = scope.launch {
            launch {
                service.playbackState.collectLatest { pbr ->
                    if (pbr == null) return@collectLatest
                    if (ttsUserStopped) {
                        _currentTtsSentence.value = ""
                        _currentTtsRange.value = null
                        _ttsState.value = TtsState.STOPPED
                        return@collectLatest
                    }
                    _currentTtsSentence.value = pbr.utterance
                    _currentTtsRange.value = pbr.range
                    _ttsState.value = when (pbr.state) {
                        is MediaNavigator.State.Ready -> {
                            if (pbr.playWhenReady) {
                                hasEverStartedTts = true
                                onTtsStateChanged?.invoke(ReadingMode.TTS, pbr.utterance)
                                TtsState.PLAYING
                            } else if (hasEverStartedTts) {
                                TtsState.PAUSED
                            } else {
                                TtsState.IDLE
                            }
                        }
                        is MediaNavigator.State.Ended -> {
                            hasEverStartedTts = false
                            TtsState.STOPPED
                        }
                        is MediaNavigator.State.Failure -> {
                            hasEverStartedTts = false
                            TtsState.STOPPED
                        }
                        else -> if (ttsUserStopped) TtsState.STOPPED else TtsState.IDLE
                    }
                }
            }
            launch {
                service.currentLocator.collectLatest { locator ->
                    if (locator == null) return@collectLatest
                    _currentTtsLocator.value = locator
                    onLocatorChanged?.invoke(locator)
                    val targetRes = locator.href.toString().substringAfterLast('/').substringBefore('#')
                    val lastRes = lastProcessedHref?.substringAfterLast('/')?.substringBefore('#')
                    if (targetRes.isNotEmpty() && targetRes != lastRes) {
                        processResource(locator.href.toString())
                    }
                }
            }
        }
    }

    fun syncPreferences(speed: Float, language: String?, pitch: Float = 1.0f) {
        pendingSpeed = speed
        pendingPitch = pitch
        pendingLanguage = language
        ttsService?.updateSettings(speed, pitch, language)
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun playTts(
        context: Context,
        locator: Locator,
        navigator: Navigator?,
        scope: CoroutineScope? = null,
        publication: Publication? = null
    ) {
        ttsUserStopped = false
        if (navigator != null) this.navigator = navigator
        if (publication != null) this.currentPublication = publication
        if (scope != null) this.currentScope = scope

        // Arrancar el servicio en primer plano JUSTO ANTES de empezar a reproducir.
        val intent = Intent(context, ReaderTtsService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
        }

        val service = ttsService
        if (service == null) {
            Log.w(TAG, "playTts: service not bound yet, awaiting binding before playback")
            val targetScope = this.currentScope ?: CoroutineScope(Dispatchers.Main)
            targetScope.launch {
                if (awaitServiceBinding()) {
                    executePlay(locator)
                } else {
                    Log.e(TAG, "playTts: service binding failed, cannot play")
                }
            }
            return
        }

        executePlay(locator)
    }

    private fun executePlay(locator: Locator) {
        ttsUserStopped = false
        hasEverStartedTts = true
        val service = ttsService ?: return
        val pub = currentPublication
        if (pub != null && !service.isNavigatorInitialized()) {
            service.initNavigator(pub, locator)
            currentScope?.let { observeServiceFlows(it) }
        }
        val visualNavigator = navigator as? VisualNavigator
        val currentRes = visualNavigator?.currentLocator?.value?.href?.toString()?.substringAfterLast('/')?.substringBefore('#')
        val targetRes = locator.href.toString().substringAfterLast('/').substringBefore('#')
        if (!currentRes.isNullOrEmpty() && !targetRes.isNullOrEmpty() && currentRes != targetRes) {
            visualNavigator?.go(locator, animated = false)
        }
        service.go(locator)
        service.play()
    }

    fun pauseTts() {
        ttsService?.pause()
    }

    fun stopTts() {
        ttsUserStopped = true
        hasEverStartedTts = false
        ttsService?.stop()
        _ttsState.value = TtsState.STOPPED
        _currentTtsSentence.value = ""
        _currentTtsRange.value = null
        _currentTtsLocator.value = null
    }

    fun resumeTts() {
        ttsUserStopped = false
        hasEverStartedTts = true
        val service = ttsService
        if (service != null && !service.isNavigatorInitialized()) {
            _currentTtsLocator.value?.let { executePlay(it) } ?: service.play()
        } else {
            service?.play()
        }
    }

    fun skipTtsSentenceForward() { ttsService?.goNext() }
    fun skipTtsSentenceBackward() { ttsService?.goPrevious() }

    fun skipTtsWordForward() {
        val sentence = _currentTtsSentence.value
        val range = _currentTtsRange.value
        val nextRange = findNextWordRange(sentence, range)
        if (nextRange != null) {
            _currentTtsRange.value = nextRange
        } else {
            skipTtsSentenceForward()
        }
    }

    fun skipTtsWordBackward() {
        val sentence = _currentTtsSentence.value
        val range = _currentTtsRange.value
        val prevRange = findPrevWordRange(sentence, range)
        if (prevRange != null) {
            _currentTtsRange.value = prevRange
        } else {
            skipTtsSentenceBackward()
        }
    }

    internal fun findNextWordRange(sentence: String, currentRange: IntRange?): IntRange? {
        if (sentence.isEmpty()) return null
        val start = currentRange?.last?.plus(1) ?: 0
        if (start >= sentence.length) return null
        
        var wordStart = start
        while (wordStart < sentence.length && !sentence[wordStart].isLetterOrDigit()) {
            wordStart++
        }
        if (wordStart >= sentence.length) return null
        
        var wordEnd = wordStart
        while (wordEnd < sentence.length && sentence[wordEnd].isLetterOrDigit()) {
            wordEnd++
        }
        return wordStart until wordEnd
    }

    internal fun findPrevWordRange(sentence: String, currentRange: IntRange?): IntRange? {
        if (sentence.isEmpty()) return null
        val end = currentRange?.first?.minus(1) ?: 0
        if (end <= 0) return null
        
        var wordEnd = end
        while (wordEnd > 0 && !sentence[wordEnd].isLetterOrDigit()) {
            wordEnd--
        }
        if (wordEnd < 0) return null
        
        var wordStart = wordEnd
        while (wordStart > 0 && sentence[wordStart - 1].isLetterOrDigit()) {
            wordStart--
        }
        return wordStart..wordEnd
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun skipTtsChapterForward(context: Context, publication: Publication?, navigator: Navigator?) {
        val pub = publication ?: return
        val locator = _currentTtsLocator.value ?: (navigator as? VisualNavigator)?.currentLocator?.value ?: return
        val order = pub.readingOrder
        val idx = order.indexOfFirst { it.href.toString() == locator.href.toString() }
        if (idx != -1 && idx < order.size - 1) {
            val nextLocator = pub.locatorFromLink(order[idx + 1]) ?: return
            playTts(context, nextLocator, navigator)
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun skipTtsChapterBackward(context: Context, publication: Publication?, navigator: Navigator?) {
        val pub = publication ?: return
        val locator = _currentTtsLocator.value ?: (navigator as? VisualNavigator)?.currentLocator?.value ?: return
        val order = pub.readingOrder
        val idx = order.indexOfFirst { it.href.toString() == locator.href.toString() }
        if (idx > 0) {
            val prevLocator = pub.locatorFromLink(order[idx - 1]) ?: return
            playTts(context, prevLocator, navigator)
        }
    }

    fun onCleared(context: Context) {
        serviceFlowJob?.cancel()
        serviceFlowJob = null
        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
        try { context.stopService(Intent(context, ReaderTtsService::class.java)) } catch (_: Exception) {}
        ttsService = null
        currentPublication = null
        currentScope = null
        navigator = null
        _currentProcessedDocument.value = null
        _currentTtsLocator.value = null
        _currentTtsSentence.value = ""
        onTtsStateChanged = null
        onLocatorChanged = null
    }
}

