package com.example.woodsreader.ui.reader
import android.app.Application
import com.example.woodsreader.core.pipeline.PdfBookViewSynchronizer
import com.example.woodsreader.core.managers.SleepTimer
import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.core.managers.ReadingProgressManager
import android.content.Context
import java.util.Locale

import android.util.Log
import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay

import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkEntity

import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.TtsRuleEntity
import com.example.woodsreader.data.database.ReadingStatsEntity
import com.example.woodsreader.data.datastore.UserPreferences

import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.model.HighlightColor

import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.repository.BookRepository

import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.core.tts.AmbiencePreset
import com.example.woodsreader.core.tts.ForestAmbienceEngine
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.TtsPresetCatalog
import com.example.woodsreader.core.tts.TtsPresetPack
import com.example.woodsreader.core.tts.ChapterAudioExporter
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.PronunciationLexiconManager
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import com.example.woodsreader.core.tts.SleepTimerPreset
import com.example.woodsreader.core.tts.EdgeNeuralTtsClient
import com.example.woodsreader.core.tts.EdgeVoiceOption
import com.example.woodsreader.core.tts.AmbientSoundscapePlayer
import com.example.woodsreader.core.managers.PdfReflowManager

import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.model.PageTurnStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext
import org.readium.r2.navigator.Navigator

import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.preferences.Color

import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

import org.readium.r2.shared.util.AbsoluteUrl
import com.example.woodsreader.core.pipeline.EpubPackageSanitizer
import org.readium.r2.shared.util.mediatype.MediaType

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.json.JSONObject

import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.*

import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.asset.AssetRetriever
import android.util.LruCache
import com.example.woodsreader.core.pipeline.ProcessedDocument
import com.example.woodsreader.core.pipeline.ReadingBlock
import com.example.woodsreader.core.xray.BookXRayEngine
import com.example.woodsreader.core.xray.ChapterXRayReport
import com.example.woodsreader.core.license.ProLicenseManager

import com.example.woodsreader.service.CoverExtractor
import org.readium.r2.shared.publication.Link
import org.readium.r2.streamer.PublicationOpener
import java.io.File

import javax.inject.Inject
import kotlin.math.abs

data class ParsedChapterData(
    val index: Int,
    val href: String,
    val title: String,
    val document: ProcessedDocument
)

@OptIn(FlowPreview::class, ExperimentalReadiumApi::class)

@HiltViewModel

class ReaderViewModel @Inject constructor(
    private val context: Application,

    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,

    private val userPreferencesRepository: UserPreferencesRepository,
    private val assetRetriever: AssetRetriever,

    private val publicationOpener: PublicationOpener,
    private val ttsManager: TtsManager,

    private val pdfReflowManager: PdfReflowManager,
    private val epubFormatManager: EpubFormatManager,

    private val readingSessionManager: ReadingSessionManager,
    private val readingProgressManager: ReadingProgressManager,
    private val sleepTimer: SleepTimer,
    private val pdfBookViewSynchronizer: PdfBookViewSynchronizer,
    private val sherpaModelManager: SherpaModelManager,
    private val ambienceEngine: ForestAmbienceEngine,
    private val coverExtractor: CoverExtractor,
    private val ttsPresetCatalog: TtsPresetCatalog,
    private val chapterAudioExporter: ChapterAudioExporter,
    private val edgeNeuralTtsClient: EdgeNeuralTtsClient,
    private val pronunciationLexiconManager: PronunciationLexiconManager,
    val proLicenseManager: ProLicenseManager,
    val ambientSoundscapePlayer: AmbientSoundscapePlayer
) : ViewModel() {

    companion object {
        private const val TAG = "ReaderViewModel"

        private const val INACTIVITY_THRESHOLD = 5 * 60 * 1000L
        private const val MAX_CACHED_REFLOW_PAGES = 50
        private const val MAX_CACHED_CHAPTERS = 5
    }

    val chapterLruCache = LruCache<Int, ParsedChapterData>(MAX_CACHED_CHAPTERS)
    private var prefetchJob: Job? = null

    val bookId: String = checkNotNull(savedStateHandle.get<String>("bookId"))
    var publication: Publication? = null

    private var navigatorLocatorJob: Job? = null
    private var lastSavedLocatorJson: String? = null
    private var lastSaveTimeMs: Long = 0L
    private var lastProcessedChapterHref: String? = null

    var navigator: Navigator? = null
        set(value) {
            field = value
            epubFormatManager.updateNavigator(value)
            observeNavigator(value)
        }

    private fun observeNavigator(nav: Navigator?) {
        navigatorLocatorJob?.cancel()
        if (nav is VisualNavigator) {
            navigatorLocatorJob = viewModelScope.launch {
                nav.currentLocator.collect { loc ->
                    onLocatorChanged(loc)
                }
            }
        }
    }

    private val _viewState = MutableStateFlow(ReaderViewState())
    val viewState: StateFlow<ReaderViewState> = _viewState.asStateFlow()
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)

    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private var positionsByResource = emptyList<List<Locator>>()

    private var totalPositions = 0
    private var resourceIndexCache: Map<String, Int>? = null
    private var positionsJob: Job? = null

    private val _bookTimeRemaining = MutableStateFlow<String?>(null)
    val bookTimeRemaining = _bookTimeRemaining.asStateFlow()
    private val _chapterTimeRemaining = MutableStateFlow<String?>(null)
    val chapterTimeRemaining = _chapterTimeRemaining.asStateFlow()
    private var lastTtsSentence: String? = null

    private var lastRecordedProgression = 0.0
    val userPreferences: StateFlow<com.example.woodsreader.data.datastore.UserPreferences> = userPreferencesRepository.userPreferencesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.woodsreader.data.datastore.UserPreferences())

    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed = _currentSpeed.asStateFlow()

    private val _currentPitch = MutableStateFlow(1.0f)
    val currentPitch = _currentPitch.asStateFlow()

    val isReflowMode = pdfReflowManager.isReflowMode
    private val _showControls = MutableStateFlow(false)

    val showControls = _showControls.asStateFlow()
    private val _globalProgress = MutableStateFlow(0f)

    val globalProgress = _globalProgress.asStateFlow()
    private val _showEpubCoverPage = MutableStateFlow(false)
    val showEpubCoverPage = _showEpubCoverPage.asStateFlow()
    val ttsActiveBlockIndex = pdfReflowManager.ttsActiveBlockIndex

    // ── Teleprompter / Auto-Scroll Controller ────────────────────────────────
    private val autoScrollPrefs by lazy {
        context.getSharedPreferences("woods_teleprompter_prefs", Context.MODE_PRIVATE)
    }
    private val _isAutoScrollActive = MutableStateFlow(false)
    val isAutoScrollActive: StateFlow<Boolean> = _isAutoScrollActive.asStateFlow()

    private val _autoScrollWpm = MutableStateFlow(
        autoScrollPrefs.getInt("auto_scroll_wpm", 220).coerceIn(100, 450)
    )
    val autoScrollWpm: StateFlow<Int> = _autoScrollWpm.asStateFlow()

    fun toggleAutoScroll() {
        _isAutoScrollActive.update { !it }
    }

    fun setAutoScrollActive(active: Boolean) {
        _isAutoScrollActive.value = active
    }

    fun setAutoScrollWpm(wpm: Int) {
        val clamped = wpm.coerceIn(100, 450)
        _autoScrollWpm.value = clamped
        autoScrollPrefs.edit().putInt("auto_scroll_wpm", clamped).apply()
    }

    val currentTtsPage = pdfReflowManager.currentTtsPage
    val sleepTimerRemaining = combine(
        sleepTimer.sleepTimerRemaining,
        ttsManager.smartSleepTimerManager.remainingSeconds,
        ttsManager.smartSleepTimerManager.activePreset
    ) { legacyRemaining, smartRemainingSecs, smartPreset ->
        when {
            smartPreset == SleepTimerPreset.END_OF_CHAPTER -> -1L
            smartRemainingSecs > 0 -> smartRemainingSecs * 1000L
            else -> legacyRemaining
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val activeAmbiencePreset = ambienceEngine.activePreset
    val ambienceVolume = ambienceEngine.volume

    fun toggleNextAmbience() {
        ambienceEngine.cycleNextPreset()
    }

    fun setAmbiencePreset(preset: AmbiencePreset) {
        ambienceEngine.setPreset(preset)
    }

    fun setAmbienceVolume(volume: Float) {
        ambienceEngine.setVolume(volume)
    }

    fun installTtsPresetPack(pack: TtsPresetPack) = viewModelScope.launch {
        installTtsPresetPackWithResult(pack)
    }

    suspend fun installTtsPresetPackWithResult(pack: TtsPresetPack): Int {
        val addedRules = ttsPresetCatalog.installPresetPack(pack)
        epubFormatManager.invalidateCache()
        return addedRules
    }

    private var progressListenerJob: Job? = null
    private var reflowListenerJob: Job? = null

    private val _chapterInfo = MutableStateFlow(ChapterInfo())
    val chapterInfo = _chapterInfo.asStateFlow()
    val currentChapterTitle: StateFlow<String> = _chapterInfo
        .map { it.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _isRtl = MutableStateFlow(false)
    val isRtl = _isRtl.asStateFlow()

    private val _currentTtsLocator = MutableStateFlow<Locator?>(null)
    val currentTtsLocator = _currentTtsLocator.asStateFlow()

    // Ãšltimo locator donde el TTS fue detenido â€” permite "Continuar desde donde pausÃ©"
    private val _lastTtsLocator = MutableStateFlow<Locator?>(null)
    val lastTtsLocator = _lastTtsLocator.asStateFlow()

    val reflowState = pdfReflowManager.reflowState
    val isLoadingMoreReflow = pdfReflowManager.isLoadingMoreReflow
    private var lastKnownReflowPage: Int = 1
    private var currentBookmarkLocator: Locator? = null
    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked = _isCurrentPageBookmarked.asStateFlow()

        val highlights = bookRepository.getHighlights(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarks = bookRepository.getBookmarks(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ttsRules = bookRepository.getTtsRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices = _availableVoices.asStateFlow()
    val ttsState = combine(epubFormatManager.ttsState, pdfReflowManager.ttsState, isReflowMode) { epub, pdf, reflow ->
        if (isPdfBook || reflow) pdf else epub
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.woodsreader.ui.reader.TtsState.STOPPED)
    private val _currentTtsSentence = MutableStateFlow<String>("")

    val currentTtsSentence = _currentTtsSentence.asStateFlow()
    private val _currentTtsRange = MutableStateFlow<IntRange?>(null)
    val currentTtsRange = _currentTtsRange.asStateFlow()

    val audioExportState = chapterAudioExporter.exportState
    val selectedSherpaVoice = sherpaModelManager.selectedVoice

    fun isSherpaVoiceDownloaded(option: com.example.woodsreader.core.tts.SherpaVoiceOption): Boolean =
        sherpaModelManager.isVoiceDownloaded(option)

    fun selectOrDownloadSherpaVoice(option: com.example.woodsreader.core.tts.SherpaVoiceOption) {
        viewModelScope.launch {
            if (sherpaModelManager.isVoiceDownloaded(option)) {
                ttsManager.switchSherpaVoice(option)
            } else {
                sherpaModelManager.selectVoiceOption(option)
                val res = sherpaModelManager.downloadAndExtractModel()
                if (res.isSuccess) {
                    ttsManager.switchSherpaVoice(option)
                }
            }
        }
    }

    fun deleteSherpaVoiceFromReader(option: com.example.woodsreader.core.tts.SherpaVoiceOption) {
        viewModelScope.launch {
            ttsManager.stop()
            sherpaModelManager.selectVoiceOption(option)
            sherpaModelManager.deleteModel()
            ttsManager.resetSherpa()
        }
    }

    fun resetAudioExportState() {
        chapterAudioExporter.resetState()
    }

    fun speakSnippet(text: String) {
        viewModelScope.launch {
            ttsManager.speak(text)
        }
    }

    val useEdgeNeuralTts = edgeNeuralTtsClient.isEnabled
    val selectedEdgeVoice = edgeNeuralTtsClient.selectedVoice

    fun setUseEdgeNeuralTts(enabled: Boolean) {
        edgeNeuralTtsClient.setEnabled(enabled)
    }

    fun selectEdgeVoice(option: EdgeVoiceOption) {
        edgeNeuralTtsClient.setVoice(option)
    }

    fun switchSherpaVoice(option: com.example.woodsreader.core.tts.SherpaVoiceOption) {
        viewModelScope.launch {
            ttsManager.switchSherpaVoice(option)
        }
    }

    fun cancelAudioExport() {
        chapterAudioExporter.cancelExport()
    }

    fun exportAudioChapters(maxChaptersToExport: Int = 1) {
        val book = (_uiState.value as? ReaderUiState.Success)?.book ?: return
        val pub = publication ?: return
        val startChapTitle = currentChapterTitle.value.ifBlank { "Capitulo" }

        val job = viewModelScope.launch {
            if (isReflowMode.value || isPdfBook) {
                val items = pdfReflowManager.reflowState.value.items.mapNotNull {
                    when (it) {
                        is ReflowItem.Paragraph -> it.content
                        is ReflowItem.Header -> it.content
                        else -> null
                    }
                }
                chapterAudioExporter.exportChaptersToM4a(
                    bookTitle = book.title,
                    exportLabel = startChapTitle,
                    totalChaptersCount = 1,
                    chapterProvider = { com.example.woodsreader.core.tts.ChapterTextChunk(startChapTitle, items) },
                    speed = currentSpeed.value
                )
            } else {
                val currentHref = (navigator as? org.readium.r2.navigator.VisualNavigator)?.currentLocator?.value?.href?.toString()
                    ?.substringBefore('#').orEmpty()
                val startIdx = pub.readingOrder.indexOfFirst {
                    it.href.toString().substringBefore('#') == currentHref ||
                        it.href.toString().substringAfterLast('/') == currentHref.substringAfterLast('/')
                }.coerceAtLeast(0)

                val availableCount = (pub.readingOrder.size - startIdx).coerceAtLeast(1)
                val countToExport = minOf(maxChaptersToExport, availableCount)
                val label = when {
                    countToExport == 1 -> startChapTitle
                    countToExport >= pub.readingOrder.size -> "Libro_Completo"
                    else -> "Caps_${startIdx + 1}_a_${startIdx + countToExport}"
                }

                chapterAudioExporter.exportChaptersToM4a(
                    bookTitle = book.title,
                    exportLabel = label,
                    totalChaptersCount = countToExport,
                    chapterProvider = { offset ->
                        val targetIdx = startIdx + offset
                        val link = pub.readingOrder.getOrNull(targetIdx) ?: return@exportChaptersToM4a null
                        val cached = chapterLruCache.get(targetIdx)
                        val doc = cached?.document ?: epubFormatManager.processResource(link.href.toString(), book.id)
                        if (doc != null && cached == null) {
                            val title = link.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${targetIdx + 1}"
                            chapterLruCache.put(targetIdx, ParsedChapterData(targetIdx, link.href.toString(), title, doc))
                        }
                        val texts = doc?.ttsNarrationUnits?.map { it.textToSpeak } ?: emptyList()
                        val title = link.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${targetIdx + 1}"
                        com.example.woodsreader.core.tts.ChapterTextChunk(title, texts)
                    },
                    speed = currentSpeed.value
                )
            }
        }
        chapterAudioExporter.setActiveJob(job)
    }

    fun exportCurrentChapterToAudio() = exportAudioChapters(1)

    val currentProcessedDocument: StateFlow<com.example.woodsreader.core.pipeline.ProcessedDocument?> =
        epubFormatManager.currentProcessedDocument

    private val _activeNarrationBlockId = MutableStateFlow<String?>(null)
    val activeNarrationBlockId: StateFlow<String?> = _activeNarrationBlockId.asStateFlow()

    private val _activeNarrationWordRange = MutableStateFlow<IntRange?>(null)
    val activeNarrationWordRange: StateFlow<IntRange?> = _activeNarrationWordRange.asStateFlow()

    fun updateNarrationHighlight(blockId: String?, wordRange: IntRange?) {
        _activeNarrationBlockId.value = blockId
        _activeNarrationWordRange.value = wordRange
        _viewState.update {
            it.copy(
                activeNarrationBlockId = blockId,
                activeNarrationWordRange = wordRange
            )
        }
    }
    // Ã¢â€â‚¬Ã¢â€â‚¬ Init Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    init {
        ambientSoundscapePlayer.onReaderVisibilityChanged(true)
        viewModelScope.launch {
            bookmarks.collect {
                refreshCurrentBookmarkState()
            }
        }

        // Sincronizar preferencias de TTS almacenadas
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                if (_currentSpeed.value != prefs.ttsSpeed) {
                    _currentSpeed.value = prefs.ttsSpeed
                }
                if (_currentPitch.value != prefs.ttsPitch) {
                    _currentPitch.value = prefs.ttsPitch
                }
                epubFormatManager.syncPreferences(prefs.ttsSpeed, prefs.ttsLanguage, prefs.ttsPitch)
            }
        }

        // Inicializar el servicio TTS inmediatamente
        epubFormatManager.initialize(
            context = context,
            stateChanged = { mode, sentence ->
                switchSessionMode(mode)
                _currentTtsSentence.value = sentence
            },
            locatorChanged = { locator ->
                _currentTtsLocator.value = locator
                onTtsLocatorChanged(locator)
            },
            chapterFinished = {
                advanceTtsToNextChapter()
            }
        )

        ttsManager.onQueueCompletedNaturally = {
            viewModelScope.launch {
                advanceTtsToNextChapter()
            }
        }

        // Propagar flujos del PdfReflowManager al ViewModel para modo PDF (reflow y original)
        viewModelScope.launch {
            combine(pdfReflowManager.currentTtsSentence, pdfReflowManager.currentTtsPage) { sentence, page ->
                sentence to page
            }.collect { (sentence, page) ->
                if (isPdfBook || isReflowMode.value) {
                    _currentTtsSentence.value = sentence
                    val pageLoc = getLocatorForPdfPage(page)
                    val ttsLoc = pageLoc?.copy(text = Locator.Text(highlight = sentence))
                    _currentTtsLocator.value = ttsLoc
                    if (ttsLoc != null && sentence.isNotBlank()) {
                        onTtsLocatorChanged(ttsLoc)
                    }
                }
            }
        }

        viewModelScope.launch {
            pdfReflowManager.ttsState.collect { state ->
                if (isPdfBook || isReflowMode.value) {
                    when (state) {
                        TtsState.PLAYING -> {
                            switchSessionMode(ReadingMode.TTS)
                        }
                        TtsState.PAUSED -> {
                            _lastTtsLocator.value = _currentTtsLocator.value
                        }
                        TtsState.STOPPED, TtsState.IDLE -> {
                            _currentTtsSentence.value = ""
                            _currentTtsRange.value = null
                            _currentTtsLocator.value = null
                        }
                        else -> Unit
                    }
                }
            }
        }

        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            ambientSoundscapePlayer.onReaderVisibilityChanged(true)
            _uiState.value = ReaderUiState.Loading
            try {
                stopTts()
                // Liberar inmediatamente cualquier publicación previa y cancelar cálculo de posiciones
                positionsJob?.cancel()
                positionsJob = null
                publication?.close()
                publication = null

                val loadedBook = withContext(Dispatchers.IO) {
                    bookRepository.getBook(bookId)
                }
                if (loadedBook == null) {
                    Log.e(TAG, "loadBook: Book not found for id $bookId")
                    _uiState.value = ReaderUiState.Error("Libro no encontrado en la base de datos")
                    return@launch
                }
                var book: BookEntity = loadedBook

                _showEpubCoverPage.value = false
                lastProcessedChapterHref = null
                _uiState.value = ReaderUiState.InitialLoading(book)

                val uri = resolveBookUri(book)
                if (uri == null) {
                    Log.e(TAG, "loadBook: Could not resolve URI for book: ${book.filePath}")
                    _uiState.value = ReaderUiState.Error("Archivo no accesible: ${book.title}")
                    return@launch
                }

                val pub = withContext(Dispatchers.IO) {
                    val assetResult = when (uri.scheme) {
                        "file" -> {
                            val filePath = uri.path ?: book.filePath
                            assetRetriever.retrieve(File(filePath))
                        }
                        else -> {
                            val url = AbsoluteUrl(uri.toString())
                            if (url != null) assetRetriever.retrieve(url) else null
                        }
                    }
                    val asset = assetResult?.getOrNull()
                    if (asset == null) {
                        Log.e(TAG, "loadBook: assetRetriever returned null for $uri")
                        return@withContext null
                    }
                    publicationOpener.open(
                        asset = EpubPackageSanitizer.transformAsset(asset),
                        allowUserInteraction = false
                    ).getOrNull()
                }

                if (pub == null) {
                    Log.e(TAG, "loadBook: publicationOpener failed to open publication for $uri")
                    _uiState.value = ReaderUiState.Error("No se pudo abrir el formato de ${book.title}")
                    return@launch
                }

                publication = pub

                // 1. Construcción O(1) de resourceIndexCache con pre-asignación en Dispatchers.Default
                val fastIndexCache = withContext(Dispatchers.Default) {
                    val order = pub.readingOrder
                    val map = HashMap<String, Int>(order.size * 4)
                    for (i in order.indices) {
                        val rawHref = order[i].href.toString()
                        val cleanHref = rawHref.substringBefore('#').removePrefix("/")
                        val filename = rawHref.substringAfterLast('/')
                        val cleanFilename = filename.substringBefore('#')
                        map[rawHref] = i
                        map[cleanHref] = i
                        map[filename] = i
                        map[cleanFilename] = i
                    }
                    map
                }
                resourceIndexCache = fastIndexCache

                // 2. Posiciones sintéticas inmediatas O(N) sin escanear ZIP en libros extensos (> 80 capítulos)
                val totalChapters = pub.readingOrder.size.coerceAtLeast(1)
                val computedPositions: List<List<Locator>> = if (totalChapters > 80) {
                    withContext(Dispatchers.Default) {
                        val invTotal = 1.0 / totalChapters.toDouble()
                        pub.readingOrder.mapIndexed { index, link ->
                            listOf(
                                Locator(
                                    href = link.url(),
                                    mediaType = link.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.XHTML,
                                    title = link.title,
                                    locations = Locator.Locations(
                                        progression = 0.0,
                                        position = index + 1,
                                        totalProgression = index.toDouble() * invTotal
                                    )
                                )
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        val rawPos = runCatching { pub.positions() }.getOrDefault(emptyList())
                        if (rawPos.isNotEmpty()) {
                            rawPos.groupBy { it.href.toString().substringBefore('#') }.values.toList()
                        } else {
                            val invTotal = 1.0 / totalChapters.toDouble()
                            pub.readingOrder.mapIndexed { index, link ->
                                listOf(
                                    Locator(
                                        href = link.url(),
                                        mediaType = link.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.XHTML,
                                        title = link.title,
                                        locations = Locator.Locations(
                                            progression = 0.0,
                                            position = index + 1,
                                            totalProgression = index.toDouble() * invTotal
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
                positionsByResource = computedPositions
                totalPositions = computedPositions.sumOf { it.size }

                ttsManager.setCurrentBookId(book.id)

                val initialLocator = if (book.format.name == "PDF") {
                    val physicalPath = if (book.filePath.startsWith("/")) {
                        book.filePath
                    } else {
                        uri.path ?: book.filePath
                    }
                    pdfReflowManager.initialize(physicalPath, book.id) { words ->
                        recordActivity()
                    }
                    val initialPage = book.fallbackLocatorPosition?.takeIf { it > 0 } ?: 1
                    lastKnownReflowPage = initialPage
                    pdfReflowManager.setReflowMode(enabled = true, scope = viewModelScope, initialPosition = initialPage)
                    null
                } else {
                    pdfReflowManager.setReflowMode(enabled = false, scope = viewModelScope)
                    val loc = validateAndRestoreLocator(
                        publication = pub,
                        cfiJson = book.currentLocatorJson,
                        fallbackHref = book.fallbackLocatorHref,
                        fallbackPosition = book.fallbackLocatorPosition
                    )
                    viewModelScope.launch {
                        epubFormatManager.initNavigator(pub, loc, viewModelScope, navigator, book.id)
                    }
                    loc
                }

                lastRecordedProgression = initialLocator?.locations?.totalProgression
                    ?: book.readingProgressPercent.toDouble()
                _globalProgress.value = book.readingProgressPercent.coerceIn(0f, 1f)
                startNewSession(ReadingMode.MANUAL, initialLocator)

                // FASE 1 (Crítica — < 100 ms): Emitir inmediatamente el estado de lectura listo
                _uiState.value = ReaderUiState.Success(
                    book = book,
                    publication = pub,
                    initialLocatorJson = initialLocator?.toJSON()?.toString(),
                    initialLocator = initialLocator
                )
                Log.d(TAG, "loadBook: Successfully loaded ${book.title} (Phase 1 Ready)")

                // FASE 2 (Diferida en background): Tareas secundarias que no bloquean la lectura
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        bookRepository.markOpened(book.id)
                        userPreferencesRepository.updateLastReadBookId(book.id)

                        // Asegurar portada si falta sin bloquear la apertura del lector
                        val coverPath = book.coverCachePath
                        if (coverPath.isNullOrBlank() || !File(coverPath).exists()) {
                            val newCover = coverExtractor.extractCover(
                                source = uri,
                                format = book.format,
                                fileHash = book.fileHash,
                                title = book.title,
                                author = book.author,
                                publication = pub
                            )
                            if (newCover != null) {
                                book = book.copy(coverCachePath = newCover)
                                bookRepository.updateCoverPath(book.id, newCover)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error en hidratación diferida (Fase 2) de ${book.title}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadBook: Exception loading book $bookId", e)
                _uiState.value = ReaderUiState.Error("Error al cargar libro: ${e.message}")
            }
        }
    }

    /**
     * Resuelve y valida la ruta o URI del libro.
     * Soporta rutas de archivo clásicas (/storage/...), URIs de MediaStore (content://media/...)
     * y URIs de SAF (content://com.android.externalstorage.documents/...).
     *
     * Para URIs de MediaStore, extrae la ruta física real usando la columna DATA y devuelve
     * una URI file:// que Readium puede abrir directamente.
     *
     * Retorna la URI válida o null si el archivo ya no existe o no es accesible.
     */
    private fun resolveBookUri(book: BookEntity): android.net.Uri? {
        val path = book.filePath

        if (path.isBlank()) return null
        try {
            // Caso 1: Ruta de archivo pura (/storage/emulated/0/...)
            if (path.startsWith("/")) {
                val file = File(path)
                return if (file.exists()) android.net.Uri.fromFile(file) else null
            }

            // Caso 2: URI parseable (content:// o file://)
            val uri = android.net.Uri.parse(path)

            when (uri.scheme) {
                "content" -> {
                    // Sub-caso A: URI de MediaStore (content://media/...)
                    // Readium NO puede leer directamente URIs de MediaStore.
                    // Necesitamos extraer la ruta física real usando la columna DATA.
                    if (uri.authority?.startsWith("media") == true) {
                        val physicalPath = try {
                            context.contentResolver.query(
                                uri,
                                arrayOf(android.provider.MediaStore.Files.FileColumns.DATA),
                                null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val colIdx = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
                                    if (colIdx >= 0) cursor.getString(colIdx) else null
                                } else null
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not query DATA column for MediaStore URI: $uri", e)
                            null
                        }

                        if (physicalPath != null) {
                            val file = File(physicalPath)
                            if (file.exists()) {
                                Log.d(TAG, "Resolved MediaStore URI to file path: $physicalPath")
                                return android.net.Uri.fromFile(file)
                            }
                        }

                        // Fallback: intentar abrir el descriptor directamente (Android 10+ puede funcionar)
                        return try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                                Log.d(TAG, "MediaStore URI accessible via FileDescriptor (no DATA path): $uri")
                                uri
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaStore URI no longer accessible: $path")
                            null
                        }
                    }

                    // Sub-caso B: URI de SAF (content://com.android.externalstorage.documents/...)
                    // Readium puede leer estas URIs directamente si tiene permisos persistentes.
                    return try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use {
                            uri
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SAF URI no longer accessible: $path")
                        null
                    }
                }

                "file" -> {
                    val file = File(uri.path ?: return null)
                    return if (file.exists()) uri else null
                }

                else -> {
                    // Otros esquemas (ej. http si se soporta en el futuro)

                    return uri
                }

            }

        } catch (e: Exception) {
            Log.e(TAG, "Error resolving book URI: $path", e)

        }

        return null
    }

    // â”€â”€ Session management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val isLoadingTtsModel: StateFlow<Boolean> = ttsManager.isLoadingModel

    fun startNewSession(mode: ReadingMode, initialLocator: Locator? = null) {
        val locator = (navigator as? VisualNavigator)?.currentLocator?.value ?: initialLocator
        readingSessionManager.startNewSession(viewModelScope, bookId, mode, locator)
        locator?.locations?.totalProgression?.let {
            lastRecordedProgression = it
        }
        lastTtsSentence = null
    }

    fun stopActiveSession() {
        val currentLocator = if (readingSessionManager.currentSessionMode == ReadingMode.TTS || ttsState.value == com.example.woodsreader.ui.reader.TtsState.PLAYING) {
            _currentTtsLocator.value ?: (navigator as? VisualNavigator)?.currentLocator?.value
        } else {
            (navigator as? VisualNavigator)?.currentLocator?.value
        }
        readingSessionManager.stopActiveSession(bookId = bookId, currentLocator = currentLocator)
    }

    fun switchSessionMode(newMode: ReadingMode) {
        val currentLocator = if (readingSessionManager.currentSessionMode == ReadingMode.TTS || ttsState.value == com.example.woodsreader.ui.reader.TtsState.PLAYING) {
            _currentTtsLocator.value ?: (navigator as? VisualNavigator)?.currentLocator?.value
        } else {
            (navigator as? VisualNavigator)?.currentLocator?.value
        }
        readingSessionManager.switchSessionMode(viewModelScope, bookId, newMode, currentLocator)
    }

    fun recordActivity() {
        val currentLocator = (navigator as? VisualNavigator)?.currentLocator?.value
        readingSessionManager.recordActivity(viewModelScope, bookId, currentLocator)
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ UI controls ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    fun toggleControls() { _showControls.value = !_showControls.value }

    fun hideControls()   { _showControls.value = false }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Preferences ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬
    // Funciones on* son el contrato pÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âºblico con la UI.

    // Las update* duplicadas han sido eliminadas en favor de estas.

            fun onThemeChange(theme: ReaderTheme)        = viewModelScope.launch { userPreferencesRepository.updateTheme(theme) }

    fun onFontSizeChange(size: Float)            = viewModelScope.launch { userPreferencesRepository.updateFontSize(size) }

    fun onLineSpacingChange(spacing: Float)      = viewModelScope.launch { userPreferencesRepository.updateLineSpacing(spacing) }

    fun onMarginChange(margin: Float)            = viewModelScope.launch { userPreferencesRepository.updateMargin(margin) }

    fun onTextAlignChange(align: String)         = viewModelScope.launch { userPreferencesRepository.updateTextAlign(align) }

    fun onFontFamilyChange(font: String)         = viewModelScope.launch { userPreferencesRepository.updateFontFamily(font) }

    fun updateVolumeKeysNavigation(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.updateVolumeKeysNavigation(enabled) }

    fun onScrollModeChange(mode: ScrollMode) = viewModelScope.launch { userPreferencesRepository.updateScrollMode(mode) }

    fun onPageTurnAnimationChange(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.updatePageTurnAnimation(enabled) }
    fun onPageTurnStyleChange(style: PageTurnStyle) = viewModelScope.launch { userPreferencesRepository.updatePageTurnStyle(style) }

    fun onInvertedTapZonesChange(inverted: Boolean) = viewModelScope.launch { userPreferencesRepository.updateInvertedTapZones(inverted) }

    fun toggleScrollMode() {
        val current = userPreferences.value.scrollMode
        val next = if (current == ScrollMode.PAGINATED) ScrollMode.CONTINUOUS_VERTICAL else ScrollMode.PAGINATED
        onScrollModeChange(next)
    }

    fun updateHyphenation(hyphenation: Boolean)  = viewModelScope.launch { userPreferencesRepository.updateHyphenation(hyphenation) }

    private var speedUpdateJob: Job? = null

    fun onSpeedChange(speed: Float, immediate: Boolean = false) {
        val coercedSpeed = (Math.round(speed * 100.0f) / 100.0f).coerceIn(0.25f, 4.0f)
        _currentSpeed.value = coercedSpeed

        speedUpdateJob?.cancel()
        if (immediate) {
            applySpeedChange(coercedSpeed)
        } else {
            speedUpdateJob = viewModelScope.launch {
                delay(150L)
                applySpeedChange(coercedSpeed)
            }
        }
    }

    private fun applySpeedChange(speed: Float) {
        pdfReflowManager.onSpeedChange(speed, viewModelScope)
        epubFormatManager.syncPreferences(speed, userPreferences.value.ttsLanguage, _currentPitch.value)
        viewModelScope.launch { userPreferencesRepository.updateTtsSpeed(speed) }
    }

    fun ttsSpeedUp() {
        val rounded = (Math.round(currentSpeed.value * 4f) / 4f)
        val newSpeed = if (rounded >= 3.0f) 0.75f else rounded + 0.25f
        onSpeedChange(newSpeed, immediate = true)
    }

    fun ttsSpeedDown() {
        val rounded = (Math.round(currentSpeed.value * 4f) / 4f)
        val newSpeed = if (rounded <= 0.75f) 3.0f else rounded - 0.25f
        onSpeedChange(newSpeed, immediate = true)
    }

    fun onPitchChange(pitch: Float) {
        _currentPitch.value = pitch
        epubFormatManager.syncPreferences(_currentSpeed.value, userPreferences.value.ttsLanguage, pitch)
        viewModelScope.launch { userPreferencesRepository.updateTtsPitch(pitch) }
    }

    fun onLanguageChange(language: String?) {
        epubFormatManager.syncPreferences(_currentSpeed.value, language, _currentPitch.value)
        viewModelScope.launch { userPreferencesRepository.updateTtsLanguage(language) }
    }

    fun onTtsAutoScrollChange(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.updateTtsAutoScroll(enabled) }
    val sherpaDownloadState = sherpaModelManager.downloadState
    private val _showSherpaDownloadDialog = MutableStateFlow(false)
    val showSherpaDownloadDialog = _showSherpaDownloadDialog.asStateFlow()

    fun dismissSherpaDownloadDialog() {
        _showSherpaDownloadDialog.value = false
    }

    fun downloadSherpaModel() {
        _showSherpaDownloadDialog.value = false
        viewModelScope.launch {
            val result = sherpaModelManager.downloadAndExtractModel()
            if (result.isSuccess) {
                userPreferencesRepository.updateUseSherpaTts(true)
                ttsManager.initSherpaIfNeeded()
            }
        }
    }

    fun updateUseSherpaTts(enabled: Boolean) = viewModelScope.launch {
        if (!enabled) {
            userPreferencesRepository.updateUseSherpaTts(false)
        } else {
            if (sherpaModelManager.isModelReady()) {
                userPreferencesRepository.updateUseSherpaTts(true)
                ttsManager.initSherpaIfNeeded()
            } else {
                _showSherpaDownloadDialog.value = true
            }
        }
    }

    fun updateTtsUiStyle(style: com.example.woodsreader.model.TtsUiStyle) = viewModelScope.launch { userPreferencesRepository.updateTtsUiStyle(style) }

    fun updateTtsHighlightStyle(style: com.example.woodsreader.model.TtsHighlightStyle) = viewModelScope.launch { userPreferencesRepository.updateTtsHighlightStyle(style) }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Highlights & Bookmarks Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    fun deleteHighlight(highlight: HighlightEntity) = viewModelScope.launch { bookRepository.deleteHighlight(highlight) }

    fun addHighlight(locator: Locator, color: HighlightColor, text: String, note: String = "") {
        viewModelScope.launch {
            val chapter = locator.title ?: _chapterInfo.value.title ?: ""
            val entity = HighlightEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                highlightedText = text,
                color = color,
                note = note,
                chapterTitle = chapter,
                createdAt = System.currentTimeMillis()
            )
            bookRepository.saveHighlight(entity)
        }
    }

    fun addBookmark(locator: Locator?, label: String? = null) = viewModelScope.launch {
        val currentLocator = (navigator as? VisualNavigator)?.currentLocator?.value ?: locator
        val page = if (isReflowMode.value || isPdfBook) {
            lastKnownReflowPage
        } else {
            currentLocator?.locations?.position ?: 1
        }
        val bookmarkLocator = currentLocator?.let {
            if (isReflowMode.value || isPdfBook) {
                it.copy(locations = it.locations.copy(position = page))
            } else {
                it
            }
        } ?: return@launch
        currentBookmarkLocator = bookmarkLocator
        refreshCurrentBookmarkState()
        if (_isCurrentPageBookmarked.value) return@launch

        val chapter = _chapterInfo.value.title?.takeIf { it.isNotBlank() }
            ?: bookmarkLocator.title?.takeIf { it.isNotBlank() }
            ?: ""
        val progressPct = String.format(Locale.US, "%.1f%%", (_globalProgress.value * 100f).coerceIn(0f, 100f))
        val snippet = bookmarkLocator.text.highlight?.trim()?.takeIf { it.isNotBlank() }
            ?: bookmarkLocator.text.after?.trim()?.take(120)?.takeIf { it.isNotBlank() }
            ?: if (isReflowMode.value || isPdfBook) {
                pdfReflowManager.reflowState.value.items.firstOrNull { item ->
                    item.pageNumber == page && (item is ReflowItem.Paragraph || item is ReflowItem.Header)
                }?.let { item ->
                    when (item) {
                        is ReflowItem.Paragraph -> item.content
                        is ReflowItem.Header -> item.content
                        else -> null
                    }?.trim()?.take(120)
                }
            } else {
                null
            }

        val autoLabel = buildString {
            if (chapter.isNotBlank()) append(chapter)
            if (page > 0) {
                if (isNotEmpty()) append(" · ")
                append("Página $page")
            }
            if (progressPct.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append("($progressPct)")
            }
            if (!snippet.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append("\"${snippet.take(110).trim()}\"")
            }
        }.ifBlank { "Página ${page.coerceAtLeast(1)} ($progressPct)" }

        bookRepository.saveBookmark(
            BookmarkEntity(
                bookId = bookId,
                locatorJson = bookmarkLocator.toJSON().toString(),
                label = label?.takeIf { it.isNotBlank() } ?: autoLabel
            )
        )
    }

    fun deleteBookmark(bookmark: BookmarkEntity) = viewModelScope.launch { bookRepository.deleteBookmark(bookmark) }

    fun saveTtsRule(rule: TtsRuleEntity) = viewModelScope.launch {
        bookRepository.saveTtsRule(rule)
        epubFormatManager.invalidateCache()
    }

    fun addTtsRule(pattern: String, replacement: String, isRegex: Boolean) = viewModelScope.launch {
        if (pattern.isNotBlank()) {
            bookRepository.saveTtsRule(TtsRuleEntity(pattern = pattern.trim(), replacement = replacement.trim(), isRegex = isRegex))
            epubFormatManager.invalidateCache()
        }
    }

    fun deleteTtsRule(rule: TtsRuleEntity) = viewModelScope.launch {
        bookRepository.deleteTtsRule(rule)
        epubFormatManager.invalidateCache()
    }

    fun addWordExclusionRule(
        pattern: String,
        isExactMatch: Boolean = true,
        applyToAllBooks: Boolean = true
    ) = viewModelScope.launch {
        bookRepository.addWordExclusionRule(
            pattern = pattern,
            isExactMatch = isExactMatch,
            applyToAllBooks = applyToAllBooks,
            bookId = if (applyToAllBooks) null else bookId
        )
        epubFormatManager.invalidateCache()
    }

    fun updateTtsVoiceName(voiceName: String?) = viewModelScope.launch {
        userPreferencesRepository.updateTtsVoiceName(voiceName)
        if (isReflowMode.value || isPdfBook) {
            pdfReflowManager.setVoice(voiceName ?: "")
        }
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Progress ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    private var lastTtsDbSaveTime = 0L

    private fun onTtsLocatorChanged(locator: Locator) {
        if (_showEpubCoverPage.value) return
        updateChapterMetadata(locator)
        val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L
        val totalProgression = locator.locations.totalProgression ?: 0.0
        val activeWpm = 200
        val bookWordsRemaining = (totalWords * (1.0 - totalProgression)).coerceAtLeast(0.0)
        val bookMinLeft = (bookWordsRemaining / activeWpm).toInt()
        _bookTimeRemaining.value = if (bookMinLeft > 0) {
            if (bookMinLeft >= 60) "${bookMinLeft / 60}h ${bookMinLeft % 60}m restantes"
            else "$bookMinLeft min restantes en el libro"
        } else "Terminando libro"

        if (totalPositions > 0) {
            val pub = publication
            if (pub != null) {
                val resourceIndex = resourceIndexCache?.get(locator.href.toString()) 
                    ?: pub.readingOrder.indexOfFirst { it.href.toString() == locator.href.toString() }
                if (resourceIndex != -1) {
                    val currentResourcePositions = positionsByResource.getOrNull(resourceIndex) ?: emptyList()
                    if (currentResourcePositions.isNotEmpty()) {
                        val positionInResource = currentResourcePositions
                            .indexOfFirst { (it.locations.progression ?: 0.0) >= (locator.locations.progression ?: 0.0) }
                            .coerceAtLeast(0)
                        val remainingPages = (currentResourcePositions.size - positionInResource).coerceAtLeast(0)
                        val chapterWordsRemaining = remainingPages * 250
                        val chapterMinLeft = (chapterWordsRemaining / activeWpm).toInt()
                        _chapterTimeRemaining.value = if (chapterMinLeft > 0) {
                            "$chapterMinLeft min para el capítulo"
                        } else "Terminando capítulo"

                        var positionsBefore = 0
                        val limit = minOf(resourceIndex, positionsByResource.size)
                        for (i in 0 until limit) {
                            positionsBefore += positionsByResource[i].size
                        }
                        _globalProgress.value = ((positionsBefore + positionInResource).toFloat() / totalPositions).coerceIn(0f, 1f)
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastTtsDbSaveTime > 10_000L) {
            lastTtsDbSaveTime = now
            val totalProg = (locator.locations.totalProgression ?: 0.0).toFloat()
            val locJson = locator.toJSON().toString()
            val fHref = locator.href.toString()
            val fPos = locator.locations.position ?: 0
            viewModelScope.launch(Dispatchers.IO) {
                bookRepository.updateReadingProgressWithFallback(
                    bookId = bookId,
                    progress = totalProg,
                    locatorJson = locJson,
                    fallbackHref = fHref,
                    fallbackPosition = fPos
                )
            }
        }
    }

    fun onLocatorChanged(locator: Locator) {
        currentBookmarkLocator = locator
        if (isPdfBook && !isReflowMode.value) {
            lastKnownReflowPage = locator.locations.position ?: 1
        }
        refreshCurrentBookmarkState()
        if (isPdfBook && !isReflowMode.value) {
            val page = locator.locations.position ?: 1
            lastKnownReflowPage = page
            val totalPages = totalPositions.takeIf { it > 0 }
                ?: pdfReflowManager.reflowState.value.totalPages.takeIf { it > 0 }
                ?: publication?.readingOrder?.size?.takeIf { it > 0 }
                ?: 1
            _globalProgress.value = (page.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

            val now = System.currentTimeMillis()
            if (now - lastSaveTimeMs > 800L) {
                lastSaveTimeMs = now
                viewModelScope.launch(Dispatchers.IO) {
                    bookRepository.updateReadingProgressWithFallback(
                        bookId = bookId,
                        progress = _globalProgress.value,
                        locatorJson = locator.toJSON().toString(),
                        fallbackHref = locator.href.toString(),
                        fallbackPosition = page
                    )
                }
            }
            return
        }

        if (!isPdfBook) {
            val locJson = locator.toJSON().toString()
            if (locJson != lastSavedLocatorJson) {
                lastSavedLocatorJson = locJson
                saveProgress(locator)
            }
        }
    }

    fun saveProgress(locator: Locator) {
        if (readingSessionManager.currentSessionMode == ReadingMode.MANUAL) {

            val currentProg = locator.locations.totalProgression ?: 0.0
            val delta = abs(currentProg - lastRecordedProgression)

            if (delta > 0 && delta < 0.1) {
                val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L

                readingSessionManager.incrementWords((delta * totalWords).toLong())
            }

            lastRecordedProgression = currentProg
        }

        // Track unique pages read in this session
        val pageKey = "${locator.href}#${locator.locations.position ?: 0}"
        val isNewPage = !readingSessionManager.pagesRead.value.contains(pageKey)
        if (isNewPage) {
            readingSessionManager.addPageRead(pageKey)
            viewModelScope.launch {
                val today = java.time.LocalDate.now().toString()
                val stats = bookRepository.getReadingStats(today) ?: ReadingStatsEntity(today)
                bookRepository.saveReadingStats(
                    stats.copy(
                        pagesRead = stats.pagesRead + 1,
                        booksActive = if (stats.booksActive == 0) 1 else stats.booksActive
                    )
                )
            }
        }

        if (!isPdfBook) {
            val href = locator.href.toString()
            if (lastProcessedChapterHref != href) {
                lastProcessedChapterHref = href
                val pub = publication
                val currentIdx = if (pub != null) {
                    resourceIndexCache?.get(href) ?: findMatchingResourceIndex(href, pub.readingOrder)
                } else -1

                viewModelScope.launch {
                    if (currentIdx != -1) {
                        val cached = chapterLruCache.get(currentIdx)
                        if (cached != null) {
                            epubFormatManager.putCachedDocument(href, cached.document)
                        }
                    }
                    val doc = epubFormatManager.processResource(href)
                    if (doc != null && currentIdx != -1 && pub != null) {
                        val link = pub.readingOrder.getOrNull(currentIdx)
                        val title = link?.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${currentIdx + 1}"
                        chapterLruCache.put(currentIdx, ParsedChapterData(currentIdx, href, title, doc))
                        prefetchSurroundingChapters(currentIdx)
                    }
                }
            }
        }

        // Calculate WPM & Time remaining estimates
        val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L
        val sessionDurationMs = System.currentTimeMillis() - readingSessionManager.sessionStartTime
        val durationMin = sessionDurationMs.toDouble() / 60_000.0
        val sessionWords = readingSessionManager.wordsProcessed.value
        val activeWpm = if (durationMin > 0.5 && sessionWords > 50) {
            (sessionWords.toDouble() / durationMin).toInt().coerceIn(80, 500)
        } else {
            200
        }

        val totalProgression = locator.locations.totalProgression ?: 0.0
        val bookWordsRemaining = (totalWords * (1.0 - totalProgression)).coerceAtLeast(0.0)
        val bookMinLeft = (bookWordsRemaining / activeWpm).toInt()
        _bookTimeRemaining.value = if (bookMinLeft > 0) {
            if (bookMinLeft >= 60) "${bookMinLeft / 60}h ${bookMinLeft % 60}m restantes"
            else "$bookMinLeft min restantes en el libro"
        } else "Terminando libro"
        if (totalPositions > 0) {
            val pub = publication
            if (pub != null) {
                val resourceIndex = resourceIndexCache?.get(locator.href.toString()) ?: pub.readingOrder.indexOfFirst { it.href.toString() == locator.href.toString() }
                if (resourceIndex != -1) {
                    val currentResourcePositions = positionsByResource.getOrNull(resourceIndex) ?: emptyList()
                    if (currentResourcePositions.isNotEmpty()) {
                        val positionInResource = currentResourcePositions
                            .indexOfFirst { (it.locations.progression ?: 0.0) >= (locator.locations.progression ?: 0.0) }
                            .coerceAtLeast(0)
                        val remainingPages = (currentResourcePositions.size - positionInResource).coerceAtLeast(0)
                        val chapterWordsRemaining = remainingPages * 250
                        val chapterMinLeft = (chapterWordsRemaining / activeWpm).toInt()
                        _chapterTimeRemaining.value = if (chapterMinLeft > 0) {
                            "$chapterMinLeft min para el capítulo"
                        } else "Terminando capítulo"

                        var positionsBefore = 0
                        val limit = minOf(resourceIndex, positionsByResource.size)
                        for (i in 0 until limit) {
                            positionsBefore += positionsByResource[i].size
                        }
                        _globalProgress.value = ((positionsBefore + positionInResource).toFloat() / totalPositions).coerceIn(0f, 1f)
                    }
                }
            }
        }

        updateChapterMetadata(locator)

        val calculatedProg = _globalProgress.value
        val locProg = (locator.locations.totalProgression ?: 0.0).toFloat()
        val totalProg = if (calculatedProg > 0f) calculatedProg else locProg
        val locJson = locator.toJSON().toString()
        val fHref = locator.href.toString()
        val fPos = locator.locations.position ?: 0

        val now = System.currentTimeMillis()
        if (now - lastSaveTimeMs > 800L) {
            lastSaveTimeMs = now
            viewModelScope.launch(Dispatchers.IO) {
                bookRepository.updateReadingProgressWithFallback(
                    bookId = bookId,
                    progress = totalProg,
                    locatorJson = locJson,
                    fallbackHref = fHref,
                    fallbackPosition = fPos
                )
            }
        }

        recordActivity()
    }

    /**
     * Valida el CFI guardado con una cascada de 3 niveles:

     * 1. CFI exacto (Locator.fromJSON) ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â el mÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡s preciso
     * 2. Fallback por href/capÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­tulo ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â recupera al menos el capÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­tulo correcto

     * 3. Primera posiciÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â³n del libro ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âºltimo recurso seguro
     */

    private suspend fun validateAndRestoreLocator(
        publication: Publication,
        cfiJson: String?,
        fallbackHref: String?,
        fallbackPosition: Int?
    ): Locator? {
        val cache = resourceIndexCache

        // Nivel 1: CFI exacto con verificación O(1)
        if (cfiJson != null) {
            try {
                val locator = Locator.fromJSON(org.json.JSONObject(cfiJson))
                if (locator != null) {
                    val hrefStr = locator.href.toString()
                    val fastIndex = cache?.get(hrefStr)
                        ?: cache?.get(hrefStr.substringBefore('#'))
                        ?: cache?.get(hrefStr.substringAfterLast('/'))
                        ?: cache?.get(hrefStr.substringAfterLast('/').substringBefore('#'))

                    if (fastIndex != null && fastIndex in publication.readingOrder.indices) {
                        Log.d(TAG, "Locator restored via CFI (Level 1)")
                        return locator
                    }
                    Log.w(TAG, "CFI href not found in publication, falling back to Level 2")
                }
            } catch (e: Exception) {
                Log.w(TAG, "CFI parse failed, falling back to Level 2: ${e.message}")
            }
        }

        // Nivel 2: Fallback por href de capítulo con resolución O(1) sin linear scan
        if (fallbackHref != null) {
            try {
                val fastIndex = cache?.get(fallbackHref)
                    ?: cache?.get(fallbackHref.substringBefore('#'))
                    ?: cache?.get(fallbackHref.substringAfterLast('/'))
                    ?: cache?.get(fallbackHref.substringAfterLast('/').substringBefore('#'))

                if (fastIndex != null && fastIndex in publication.readingOrder.indices) {
                    val link = publication.readingOrder[fastIndex]
                    val total = publication.readingOrder.size.coerceAtLeast(1)
                    val locator = Locator(
                        href = link.url(),
                        mediaType = link.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.XHTML,
                        title = link.title,
                        locations = Locator.Locations(
                            progression = 0.0,
                            position = fastIndex + 1,
                            totalProgression = fastIndex.toDouble() / total.toDouble()
                        )
                    )
                    Log.d(TAG, "Locator restored via fallback href (Level 2): $fallbackHref (index $fastIndex)")
                    return locator
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback href restoration failed: ${e.message}")
            }
        }

        // Nivel 3: Primera posición del libro (nunca falla)
        Log.w(TAG, "All locator restoration failed. Starting from beginning (Level 3).")
        return null // null = Readium usa firstLocator automáticamente
    }

    fun goToLocator(nav: Navigator?, locator: Locator) {
        (nav as? VisualNavigator)?.go(locator, animated = true)

        recordActivity()
    }

    fun seekToGlobalProgress(percent: Float, navigator: Navigator?) {
        if (isReflowMode.value) {
            val totalPages = pdfReflowManager.reflowState.value.totalPages
            if (totalPages > 0) {
                val targetPage = (percent * totalPages).toInt().coerceIn(1, totalPages)
                lastKnownReflowPage = targetPage
                jumpToReflowPage(targetPage)
                _globalProgress.value = percent
                return
            }
        }

        if (isPdfBook && !isReflowMode.value) {
            val nav = navigator as? VisualNavigator
            val pub = publication
            val totalPages = totalPositions.takeIf { it > 0 }
                ?: pdfReflowManager.reflowState.value.totalPages.takeIf { it > 0 }
                ?: pub?.readingOrder?.size?.takeIf { it > 0 }
                ?: 1
            val targetPage = (percent * totalPages).toInt().coerceIn(1, totalPages)
            lastKnownReflowPage = targetPage
            if (nav != null && pub != null) {
                val readingOrderLink = pub.readingOrder.firstOrNull()
                if (readingOrderLink != null) {
                    pub.locatorFromLink(readingOrderLink)?.let { base ->
                        nav.go(base.copy(locations = Locator.Locations(position = targetPage)), animated = false)
                    }
                }
            }
            _globalProgress.value = percent
            return
        }

        val nav = navigator as? VisualNavigator ?: return
        if (totalPositions <= 0 || positionsByResource.isEmpty()) {
            val pub = publication
            val links = pub?.readingOrder ?: emptyList()
            if (pub != null && links.isNotEmpty()) {
                val targetIdx = (percent * links.size).toInt().coerceIn(0, links.size - 1)
                pub.locatorFromLink(links[targetIdx])?.let { loc ->
                    nav.go(loc, animated = false)
                }
                _globalProgress.value = percent
            }
            return
        }

        val targetIdx = (percent * totalPositions).toInt().coerceIn(0, totalPositions - 1)
        var accumulated = 0

        var targetResourceIdx = 0
        var targetPosInResource = 0

        for ((idx, resourcePos) in positionsByResource.withIndex()) {
            if (targetIdx < accumulated + resourcePos.size) {
                targetResourceIdx = idx
                targetPosInResource = targetIdx - accumulated
                break
            }
            accumulated += resourcePos.size
        }

        if (targetResourceIdx in positionsByResource.indices && targetPosInResource in positionsByResource[targetResourceIdx].indices) {
            nav.go(positionsByResource[targetResourceIdx][targetPosInResource], animated = false)
            _globalProgress.value = percent
        }
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Chapter / Page navigation ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    fun pageForward(navigator: Navigator?) {
        val nav = navigator ?: return
        val prefs = userPreferences.value
        val animated = prefs.pageTurnAnimation && prefs.pageTurnStyle == com.example.woodsreader.model.PageTurnStyle.NONE
        nav.goForwardCompat(animated = animated)
    }

    fun pageBackward(navigator: Navigator?) {
        val nav = navigator ?: return
        val prefs = userPreferences.value
        val animated = prefs.pageTurnAnimation && prefs.pageTurnStyle == com.example.woodsreader.model.PageTurnStyle.NONE
        nav.goBackwardCompat(animated = animated)
    }

    /**
     * Determina si el locator actual del TTS ha salido de la página visible en EPUB.
     * Si la oración actual sigue estando dentro de los límites de la página mostrada,
     * retorna null para evitar llamadas repetidas a nav.go() que provocan destellos y parpadeos.
     */
    fun getTtsPageTargetIfNeeded(currentLocator: Locator?, ttsLocator: Locator?): Locator? {
        if (currentLocator == null || ttsLocator == null) return null
        if (ttsLocator.href.toString() != currentLocator.href.toString()) {
            return ttsLocator
        }
        return null
    }

    fun goToNextChapter(navigator: Navigator?) {
        val pub = publication ?: return
        val nav = navigator as? VisualNavigator ?: return

        if (isPdfBook) {
            val toc = pub.tableOfContents
            if (toc.isNotEmpty()) {
                val currentPage = if (isReflowMode.value) {
                    lastKnownReflowPage.takeIf { it > 0 } ?: 1
                } else {
                    val visualPage = (nav.currentLocator.value.locations.position ?: lastKnownReflowPage)
                    lastKnownReflowPage = visualPage
                    visualPage.takeIf { it > 0 } ?: 1
                }
                val nextLink = toc.mapNotNull { link ->
                    val page = link.href.toString().let { href ->
                        Regex("""(?i)[#?&]page=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""^/?(\d+)$""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                    }
                    if (page != null) link to page else null
                }.firstOrNull { it.second > currentPage }

                if (nextLink != null) {
                    val targetPage = nextLink.second
                    if (isReflowMode.value) {
                        jumpToReflowPage(targetPage)
                    } else {
                        val readingOrderLink = pub.readingOrder.firstOrNull()
                        if (readingOrderLink != null) {
                            pub.locatorFromLink(readingOrderLink)?.let { base ->
                                nav.go(base.copy(locations = Locator.Locations(position = targetPage)), animated = false)
                            }
                        }
                    }
                    return
                }
            }
        }

        val href = nav.currentLocator.value.href.toString()
        val order = pub.readingOrder
        val currentIdx = findMatchingResourceIndex(href, order).takeIf { it != -1 } ?: resourceIndexCache?.get(href) ?: -1

        if (currentIdx != -1 && currentIdx < order.size - 1) {
            val nextIdx = currentIdx + 1
            val nextLink = order[nextIdx]
            val nextLocator = pub.locatorFromLink(nextLink)?.copy(
                locations = Locator.Locations(progression = 0.0)
            )
            // Si el siguiente capítulo ya está en la caché LRU, transferirlo instantáneamente (< 50 ms)
            chapterLruCache.get(nextIdx)?.let { cached ->
                epubFormatManager.putCachedDocument(nextLink.href.toString(), cached.document)
            }
            if (nextLocator != null) {
                nav.go(nextLocator, animated = false)
            } else {
                nav.go(nextLink, animated = false)
            }
            prefetchSurroundingChapters(nextIdx)
        } else {
            // Fallback a navegación nativa de Readium si no se resolvió el índice
            nav.goForwardCompat(animated = true)
        }
    }

    private fun findMatchingResourceIndex(rawHref: String, order: List<org.readium.r2.shared.publication.Link>): Int {
        if (rawHref.isBlank() || order.isEmpty()) return -1

        // Estrategia 0: Búsqueda O(1) ultrarrápida en resourceIndexCache
        resourceIndexCache?.get(rawHref)?.let { return it }
        val beforeHash = rawHref.substringBefore('#')
        resourceIndexCache?.get(beforeHash)?.let { return it }
        val filename = rawHref.substringAfterLast('/')
        resourceIndexCache?.get(filename)?.let { return it }
        resourceIndexCache?.get(filename.substringBefore('#'))?.let { return it }

        fun normalize(str: String): String {
            val decoded = try {
                java.net.URLDecoder.decode(str, "UTF-8")
            } catch (_: Exception) {
                str
            }
            return decoded
                .substringBefore('#')
                .substringBefore('?')
                .replace('\\', '/')
                .trimStart('/')
                .lowercase(Locale.ROOT)
        }

        val target = normalize(rawHref)
        resourceIndexCache?.get(target)?.let { return it }

        // Estrategia fallback sólo si no coincidió en la caché
        for (i in order.indices) {
            val item = normalize(order[i].href.toString())
            if (item == target || item.endsWith("/$target") || target.endsWith("/$item") || item.substringAfterLast('/') == target.substringAfterLast('/')) {
                return i
            }
        }
        return -1
    }

    fun goToPreviousChapter(navigator: Navigator?) {
        val pub = publication ?: return
        val nav = navigator as? VisualNavigator ?: return

        if (isPdfBook) {
            val toc = pub.tableOfContents
            if (toc.isNotEmpty()) {
                val currentPage = if (isReflowMode.value) {
                    lastKnownReflowPage.takeIf { it > 0 } ?: 1
                } else {
                    val visualPage = (nav.currentLocator.value.locations.position ?: lastKnownReflowPage)
                    lastKnownReflowPage = visualPage
                    visualPage.takeIf { it > 0 } ?: 1
                }
                val prevLink = toc.mapNotNull { link ->
                    val page = link.href.toString().let { href ->
                        Regex("""(?i)[#?&]page=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""^/?(\d+)$""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                    }
                    if (page != null) link to page else null
                }.lastOrNull { it.second < currentPage }

                if (prevLink != null) {
                    val targetPage = prevLink.second
                    if (isReflowMode.value) {
                        jumpToReflowPage(targetPage)
                    } else {
                        val readingOrderLink = pub.readingOrder.firstOrNull()
                        if (readingOrderLink != null) {
                            pub.locatorFromLink(readingOrderLink)?.let { base ->
                                nav.go(base.copy(locations = Locator.Locations(position = targetPage)), animated = false)
                            }
                        }
                    }
                    return
                }
            }
        }

        val href = nav.currentLocator.value.href.toString()
        val order = pub.readingOrder
        val currentIdx = findMatchingResourceIndex(href, order).takeIf { it != -1 } ?: resourceIndexCache?.get(href) ?: -1

        if (currentIdx > 0) {
            val prevIdx = currentIdx - 1
            val prevLink = order[prevIdx]
            val prevLocator = pub.locatorFromLink(prevLink)?.copy(
                locations = Locator.Locations(progression = 1.0)
            )
            // Si el capítulo anterior ya está en la caché LRU, transferirlo instantáneamente (< 50 ms)
            chapterLruCache.get(prevIdx)?.let { cached ->
                epubFormatManager.putCachedDocument(prevLink.href.toString(), cached.document)
            }
            if (prevLocator != null) {
                nav.go(prevLocator, animated = false)
            } else {
                nav.go(prevLink, animated = false)
            }
            prefetchSurroundingChapters(prevIdx)

            viewModelScope.launch {
                for (delayMs in listOf(80L, 200L, 450L, 800L, 1200L)) {
                    kotlinx.coroutines.delay(delayMs)
                    val pubView = nav.safePublicationView ?: return@launch
                    applyToAllWebViews(pubView) { wv ->
                        wv.scrollToBottom()
                    }
                }
            }
        } else {
            // Fallback a navegación nativa de Readium cuando estamos al inicio del libro o índice 0
            val moved = nav.goBackwardCompat(animated = true)
            if (!moved) {
                viewModelScope.launch {
                    val pubView = nav.safePublicationView ?: return@launch
                    applyToAllWebViews(pubView) { wv ->
                        wv.scrollTo(0, 0)
                    }
                }
            }
        }
    }

    /**
     * Pre-carga asíncrona en baja prioridad (Dispatchers.Default) de los capítulos
     * adyacentes en la caché LRU (actual, los 2 siguientes y el anterior) para que
     * el pase de página o el avance automático de TTS sea instantáneo (< 50 ms).
     */
    private fun prefetchSurroundingChapters(currentIndex: Int) {
        val pub = publication ?: return
        if (isPdfBook) return
        val order = pub.readingOrder
        if (order.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.Default) {
            // Posponer la pre-carga 5 segundos para asegurar que el usuario esté leyendo establemente
            kotlinx.coroutines.delay(5000L)

            // 1. Siguiente capítulo (prioridad máxima)
            val nextIdx = currentIndex + 1
            if (nextIdx < order.size && chapterLruCache.get(nextIdx) == null) {
                val nextLink = order[nextIdx]
                val nextHref = nextLink.href.toString()
                val doc = withContext(Dispatchers.IO) {
                    epubFormatManager.processResource(nextHref, bookId)
                }
                if (doc != null) {
                    val title = nextLink.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${nextIdx + 1}"
                    chapterLruCache.put(nextIdx, ParsedChapterData(nextIdx, nextHref, title, doc))
                    Log.d(TAG, "Prefetched next chapter ($nextIdx/${order.size}): $nextHref")
                }
            }

            // 2. Capítulo anterior
            val prevIdx = currentIndex - 1
            if (prevIdx >= 0 && chapterLruCache.get(prevIdx) == null) {
                val prevLink = order[prevIdx]
                val prevHref = prevLink.href.toString()
                val doc = withContext(Dispatchers.IO) {
                    epubFormatManager.processResource(prevHref, bookId)
                }
                if (doc != null) {
                    val title = prevLink.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${prevIdx + 1}"
                    chapterLruCache.put(prevIdx, ParsedChapterData(prevIdx, prevHref, title, doc))
                    Log.d(TAG, "Prefetched prev chapter ($prevIdx/${order.size}): $prevHref")
                }
            }

            // 3. Siguiente del siguiente (para saltos rápidos continuos)
            val nextNextIdx = currentIndex + 2
            if (nextNextIdx < order.size && chapterLruCache.get(nextNextIdx) == null) {
                val nextNextLink = order[nextNextIdx]
                val nextNextHref = nextNextLink.href.toString()
                val doc = withContext(Dispatchers.IO) {
                    epubFormatManager.processResource(nextNextHref, bookId)
                }
                if (doc != null) {
                    val title = nextNextLink.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${nextNextIdx + 1}"
                    chapterLruCache.put(nextNextIdx, ParsedChapterData(nextNextIdx, nextNextHref, title, doc))
                }
            }
        }
    }

    private var lastChapterAdvanceTime = 0L

    /**
     * Paso automático de capítulo al terminar la narración TTS.
     * Avanza al siguiente capítulo sin que el usuario tenga que interactuar con la pantalla.
     */
    fun advanceTtsToNextChapter() {
        val now = System.currentTimeMillis()
        if (now - lastChapterAdvanceTime < 1200L) {
            Log.d(TAG, "advanceTtsToNextChapter: Ignorado disparo duplicado dentro de 1.2s")
            return
        }
        lastChapterAdvanceTime = now

        val pub = publication ?: return
        if (isPdfBook || isReflowMode.value) {
            goToNextChapter(navigator)
            if (ttsState.value == TtsState.PLAYING) {
                viewModelScope.launch {
                    delay(300)
                    onPlay()
                }
            }
            return
        }

        val order = pub.readingOrder
        val totalChapters = order.size
        val currentLoc = epubFormatManager.currentTtsLocator.value
            ?: (navigator as? VisualNavigator)?.currentLocator?.value
            ?: _lastTtsLocator.value
            ?: _currentTtsLocator.value
        val currentHref = currentLoc?.href?.toString() ?: ""
        val currentIdx = if (currentHref.isNotBlank()) {
            val idx = resourceIndexCache?.get(currentHref) ?: findMatchingResourceIndex(currentHref, order)
            if (idx != -1) idx else {
                val targetRes = currentHref.substringAfterLast('/').substringBefore('#')
                order.indexOfFirst {
                    it.href.toString() == currentHref ||
                    it.href.toString().substringAfterLast('/') == targetRes
                }
            }
        } else -1

        if (currentIdx != -1 && currentIdx + 1 < totalChapters) {
            val nextIdx = currentIdx + 1
            val nextLink = order[nextIdx]
            val nextLocator = pub.locatorFromLink(nextLink)?.copy(
                locations = Locator.Locations(progression = 0.0)
            )

            // Transferir el documento ya procesado desde chapterLruCache a epubFormatManager para cambio instantáneo (< 50 ms)
            chapterLruCache.get(nextIdx)?.let { cached ->
                epubFormatManager.putCachedDocument(nextLink.href.toString(), cached.document)
            }

            if (nextLocator != null) {
                (navigator as? VisualNavigator)?.go(nextLocator, animated = false)
                epubFormatManager.playTts(context, nextLocator, navigator, viewModelScope, pub)
            } else {
                epubFormatManager.skipTtsChapterForward(context, publication, navigator)
            }
            // Pre-cargar en segundo plano el siguiente capítulo
            prefetchSurroundingChapters(nextIdx)
        } else {
            // Último capítulo alcanzado o fallback
            epubFormatManager.skipTtsChapterForward(context, publication, navigator)
        }
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ TTS ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬
    private val isPdfBook: Boolean
        get() = (uiState.value as? ReaderUiState.Success)?.book?.format?.name == "PDF"

    fun getLocatorForPdfPage(page: Int): Locator? {
        val pub = publication ?: return null
        val totalPages = pdfReflowManager.reflowState.value.totalPages.takeIf { it > 1 }
            ?: totalPositions.takeIf { it > 1 }
            ?: pub.readingOrder.size.takeIf { it > 1 }
            ?: 1
        val prog = ((page - 1).toDouble() / totalPages.toDouble()).coerceIn(0.0, 1.0)

        if (totalPositions > 1 && positionsByResource.isNotEmpty()) {
            var count = 0
            for (sublist in positionsByResource) {
                if (page - 1 < count + sublist.size) {
                    val loc = sublist.getOrNull(page - 1 - count)
                    if (loc != null) {
                        return loc.copy(
                            locations = loc.locations.copy(
                                fragments = listOf("page=$page"),
                                position = page,
                                progression = loc.locations.progression ?: prog,
                                totalProgression = loc.locations.totalProgression ?: prog
                            )
                        )
                    }
                }
                count += sublist.size
            }
        }
        val targetIdx = (page - 1).coerceAtLeast(0)
        val link = pub.readingOrder.getOrNull(targetIdx) ?: pub.readingOrder.firstOrNull() ?: return null
        return pub.locatorFromLink(link)?.copy(
            locations = Locator.Locations(
                fragments = listOf("page=$page"),
                position = page,
                progression = prog,
                totalProgression = prog
            )
        )
    }

    fun getFallbackLocator(): Locator? {
        val navLocator = (navigator as? VisualNavigator)?.currentLocator?.value
        if (navLocator != null) return navLocator

        val lastLoc = _lastTtsLocator.value ?: _currentTtsLocator.value
        if (lastLoc != null) return lastLoc

        val successState = _uiState.value as? ReaderUiState.Success
        val initialJson = successState?.initialLocatorJson
        if (!initialJson.isNullOrBlank()) {
            try {
                val parsed = Locator.fromJSON(org.json.JSONObject(initialJson))
                if (parsed != null) return parsed
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing initialLocatorJson", e)
            }
        }

        val pub = publication ?: successState?.publication
        val firstLink = pub?.readingOrder?.firstOrNull()
        if (firstLink != null) {
            return pub.locatorFromLink(firstLink)
        }
        return null
    }

    fun onPlay() {
        if (isReflowMode.value) {
            val elements = pdfReflowManager.reflowState.value.items
            val readableItems = elements.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
            val currentIdx = lastReflowScrollIndex
            val visibleItem = elements.getOrNull(currentIdx)
            val startIndex = if (visibleItem != null && visibleItem in readableItems) {
                readableItems.indexOf(visibleItem)
            } else {
                elements.drop(currentIdx).firstNotNullOfOrNull { item ->
                    readableItems.indexOf(item).takeIf { it >= 0 }
                } ?: 0
            }

            viewModelScope.launch {
                pdfReflowManager.playReflowTts(
                    scope = viewModelScope,
                    speed = currentSpeed.value,
                    pitch = userPreferences.value.ttsPitch,
                    languageCode = userPreferences.value.ttsLanguage,
                    voiceName = userPreferences.value.ttsVoiceName,
                    startIndex = startIndex
                )
            }
            return
        }

        val locator = (navigator as? VisualNavigator)?.currentLocator?.value ?: getFallbackLocator() ?: return

        if (isPdfBook && !isReflowMode.value) {
            val visualPage = locator.locations.position ?: 1
            val ttsPage = currentTtsPage.value
            if (ttsState.value == TtsState.PAUSED && visualPage == ttsPage) {
                viewModelScope.launch { pdfReflowManager.playReflowTts(viewModelScope, currentSpeed.value) }
            } else {
                playTts(locator)
            }
            return
        }

        playTtsFromVisualPosition()
    }

    fun playTtsFromVisualPosition() {
        viewModelScope.launch {
            val nav = navigator
            val locator = if (nav is org.readium.r2.navigator.epub.EpubNavigatorFragment) {
                try {
                    nav.firstVisibleElementLocator() ?: nav.currentLocator.value
                } catch (e: Exception) {
                    Log.w(TAG, "firstVisibleElementLocator failed: ${e.message}")
                    nav.currentLocator.value
                }
            } else {
                (nav as? VisualNavigator)?.currentLocator?.value
            } ?: getFallbackLocator() ?: return@launch
            playTts(locator)
        }
    }

    fun playTtsFromLast() {
        val locator = _lastTtsLocator.value ?: getFallbackLocator() ?: run {
            // No hay locator previo, usar posición visible o fallback
            onPlay()
            return
        }
        _lastTtsLocator.value = null
        playTts(locator)
    }

    fun playTts(locator: Locator) {
        _lastTtsLocator.value = null
        switchSessionMode(ReadingMode.TTS)
        if (isReflowMode.value || isPdfBook) {
            viewModelScope.launch {
                val targetPage = locator.locations.position ?: lastKnownReflowPage.coerceAtLeast(1)
                var readable = pdfReflowManager.reflowState.value.items.filter {
                    it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text
                }
                var targetIndex = readable.indexOfFirst { it.pageNumber == targetPage }

                if (targetIndex == -1) {
                    pdfReflowManager.loadReflowPages(fromPage = targetPage, isInitial = true)
                    readable = pdfReflowManager.reflowState.value.items.filter {
                        it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text
                    }
                    targetIndex = readable.indexOfFirst { it.pageNumber == targetPage }.coerceAtLeast(0)
                }

                val snippet = locator.text.highlight?.trim()
                if (!snippet.isNullOrBlank()) {
                    val refinedIndex = readable.indexOfFirst { item ->
                        val content = when (item) {
                            is ReflowItem.Text -> item.element.locator.text.highlight ?: ""
                            is ReflowItem.Header -> item.content
                            is ReflowItem.Paragraph -> item.content
                            else -> ""
                        }
                        content.contains(snippet, ignoreCase = true)
                    }
                    if (refinedIndex != -1) {
                        targetIndex = refinedIndex
                    }
                }

                pdfReflowManager.playReflowTts(
                    scope = viewModelScope,
                    speed = currentSpeed.value,
                    pitch = userPreferences.value.ttsPitch,
                    languageCode = userPreferences.value.ttsLanguage,
                    voiceName = userPreferences.value.ttsVoiceName,
                    startIndex = targetIndex.coerceAtLeast(0)
                )
            }
        } else {
            epubFormatManager.playTts(context, locator, navigator, viewModelScope, publication)
        }
    }

    private var ttsPausedAtIndex: Int? = null
    private var userScrolledSinceTtsPause: Boolean = false

    fun pauseTts() {
        if (isReflowMode.value || isPdfBook) {
            ttsPausedAtIndex = pdfReflowManager.ttsActiveBlockIndex.value
            userScrolledSinceTtsPause = false
            pdfReflowManager.pauseReflowTts()
        } else {
            epubFormatManager.pauseTts()
        }

        switchSessionMode(ReadingMode.MANUAL)
    }

    fun stopTts() {
        android.util.Log.d("TTS_STOP", "ReaderViewModel.stopTts() invoked")
        _lastTtsLocator.value = null
        ttsPausedAtIndex = null
        userScrolledSinceTtsPause = false

        pdfReflowManager.stopReflowTts()
        epubFormatManager.stopTts()

        _currentTtsSentence.value = ""
        _currentTtsRange.value = null
        _currentTtsLocator.value = null
        _activeNarrationBlockId.value = null
        _activeNarrationWordRange.value = null

        switchSessionMode(ReadingMode.MANUAL)
    }

    fun resumeTts() {
        val locator = (navigator as? VisualNavigator)?.currentLocator?.value ?: getFallbackLocator()

        if (isPdfBook && !isReflowMode.value && locator != null) {
            val visualPage = locator.locations.position ?: 1
            val ttsPage = currentTtsPage.value
            if (ttsState.value == TtsState.PAUSED && visualPage == ttsPage) {
                viewModelScope.launch { pdfReflowManager.playReflowTts(viewModelScope, currentSpeed.value) }
            } else {
                playTts(locator)
            }
            return
        }

        if (isReflowMode.value || isPdfBook) {
            val elements = pdfReflowManager.reflowElements.value
            val readableItems = elements.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
            val currentIdx = lastReflowScrollIndex.coerceIn(0, (elements.size - 1).coerceAtLeast(0))
            val visibleItem = elements.getOrNull(currentIdx)
            val visibleReadableIdx = if (visibleItem != null && visibleItem in readableItems) {
                readableItems.indexOf(visibleItem)
            } else {
                elements.drop(currentIdx).firstNotNullOfOrNull { item ->
                    readableItems.indexOf(item).takeIf { it >= 0 }
                } ?: 0
            }

            // Si el usuario se desplazó por la pantalla mientras estaba en pausa, reanudar desde lo que está viendo ahora
            val targetStartIndex = if (userScrolledSinceTtsPause || ttsPausedAtIndex == null) {
                visibleReadableIdx
            } else {
                val pausedIdx = ttsPausedAtIndex ?: pdfReflowManager.ttsActiveBlockIndex.value
                if (pausedIdx in readableItems.indices) pausedIdx else visibleReadableIdx
            }

            userScrolledSinceTtsPause = false
            ttsPausedAtIndex = null

            viewModelScope.launch {
                pdfReflowManager.playReflowTts(
                    scope = viewModelScope,
                    speed = currentSpeed.value,
                    pitch = userPreferences.value.ttsPitch,
                    languageCode = userPreferences.value.ttsLanguage,
                    voiceName = userPreferences.value.ttsVoiceName,
                    startIndex = targetStartIndex.coerceAtLeast(0)
                )
            }
            return
        }

        when (ttsState.value) {
            TtsState.PAUSED -> {
                viewModelScope.launch {
                    val nav = navigator
                    val visualLoc = if (nav is org.readium.r2.navigator.epub.EpubNavigatorFragment) {
                        try {
                            nav.firstVisibleElementLocator() ?: nav.currentLocator.value
                        } catch (_: Exception) {
                            nav.currentLocator.value
                        }
                    } else {
                        (nav as? VisualNavigator)?.currentLocator?.value
                    }
                    val ttsLocator = epubFormatManager.currentTtsLocator.value
                    val isSameHref = visualLoc?.href == ttsLocator?.href
                    val progDiff = kotlin.math.abs((visualLoc?.locations?.progression ?: 0.0) - (ttsLocator?.locations?.progression ?: 0.0))

                    // Si el usuario se ha desplazado visualmente a otra página o cambió de capítulo durante la pausa
                    if (visualLoc != null && (!isSameHref || progDiff > 0.005)) {
                        playTts(visualLoc)
                    } else {
                        epubFormatManager.resumeTts()
                        switchSessionMode(ReadingMode.TTS)
                    }
                }
            }
            TtsState.STOPPED, TtsState.IDLE -> {
                playTtsFromVisualPosition()
            }
            else -> Unit
        }
    }

    fun skipTtsSentenceForward() {
        if (isReflowMode.value || isPdfBook) skipReflowTtsForward() else epubFormatManager.skipTtsSentenceForward()

    }

    fun skipTtsSentenceBackward() {
        if (isReflowMode.value || isPdfBook) skipReflowTtsBackward() else epubFormatManager.skipTtsSentenceBackward()

    }

    fun skipTtsWordForward() {
        if (isReflowMode.value || isPdfBook) skipReflowTtsForward() else epubFormatManager.skipTtsWordForward()
    }

    fun skipTtsWordBackward() {
        if (isReflowMode.value || isPdfBook) skipReflowTtsBackward() else epubFormatManager.skipTtsWordBackward()
    }

    fun skipTtsChapterForward() {
        if (isReflowMode.value || isPdfBook) {
            goToNextChapter(navigator)
            if (ttsState.value == TtsState.PLAYING) {
                viewModelScope.launch {
                    delay(400)
                    onPlay()
                }
            }
        } else {
            epubFormatManager.skipTtsChapterForward(context, publication, navigator)
        }
    }

    fun skipTtsChapterBackward() {
        if (isReflowMode.value || isPdfBook) {
            goToPreviousChapter(navigator)
            if (ttsState.value == TtsState.PLAYING) {
                viewModelScope.launch {
                    delay(400)
                    onPlay()
                }
            }
        } else {
            epubFormatManager.skipTtsChapterBackward(context, publication, navigator)
        }
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Reflow (PDF) ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    fun toggleReflowMode() {
        val currentLocator = (navigator as? VisualNavigator)?.currentLocator?.value
        val elements = pdfReflowManager.reflowElements.value
        val activeIndex = pdfReflowManager.ttsActiveBlockIndex.value
        val pageFromReflow = if (activeIndex in elements.indices) {
            elements[activeIndex].pageNumber
        } else null

        val initialPosition = pageFromReflow ?: currentLocator?.locations?.position ?: 1
        if (pdfReflowManager.isReflowMode.value) {
            // Saliendo de Vista Libro a Vista Original: posicionar el navegador en la página actual
            if (pageFromReflow != null) {
                val nav = navigator as? VisualNavigator
                if (nav != null) {
                    getLocatorForPdfPage(pageFromReflow)?.let { loc ->
                        nav.go(loc, animated = false)
                    }
                }
            }
        }
        pdfReflowManager.toggleReflowMode(viewModelScope, initialPosition)
    }

    private var lastReflowScrollIndex = 0

    fun onReflowScroll(firstVisibleItemIndex: Int, isScrolling: Boolean) {
        if (lastReflowScrollIndex != firstVisibleItemIndex && ttsPausedAtIndex != null) {
            userScrolledSinceTtsPause = true
        }
        lastReflowScrollIndex = firstVisibleItemIndex

        val elements = pdfReflowManager.reflowElements.value
        val totalPages = pdfReflowManager.reflowState.value.totalPages

        // Solo actualizar el índice TTS pasivo cuando el scroll se detiene para evitar jank/congelamientos a 60-90fps
        if (!isScrolling && !pdfReflowManager.getIsReflowTtsRunning() && elements.isNotEmpty()) {
            val readableItems = elements.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
            val visibleItem = elements.getOrNull(firstVisibleItemIndex)
            val readableIdx = if (visibleItem != null && visibleItem in readableItems) {
                readableItems.indexOf(visibleItem)
            } else {
                elements.drop(firstVisibleItemIndex).firstNotNullOfOrNull { item ->
                    readableItems.indexOf(item).takeIf { it >= 0 }
                } ?: 0
            }
            if (readableIdx >= 0) {
                pdfReflowManager.setTtsActiveBlockIndex(readableIdx)
            }
        }

        // Sincronizar con el navegador visual (PDF) y actualizar progreso
        if (firstVisibleItemIndex in elements.indices) {
            val item = elements[firstVisibleItemIndex]
            val pageNumber = item.pageNumber
            val progress = if (totalPages > 0) (pageNumber.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) else 0f
            if (totalPages > 0) {
                _globalProgress.value = progress
            }
            if (pageNumber > 0) {
                lastKnownReflowPage = pageNumber
                val locator = (navigator as? VisualNavigator)?.currentLocator?.value
                if (locator != null) {
                    currentBookmarkLocator = locator.copy(
                        locations = locator.locations.copy(position = pageNumber)
                    )
                    refreshCurrentBookmarkState()
                }
            }
            if (!isScrolling && pageNumber > 0) {
                val pageKey = "pdf_reflow#$pageNumber"
                if (!readingSessionManager.pagesRead.value.contains(pageKey)) {
                    readingSessionManager.addPageRead(pageKey)
                }

                recordActivity()
                viewModelScope.launch(Dispatchers.IO) {
                    bookRepository.updateReadingProgressWithFallback(
                        bookId = bookId,
                        progress = progress,
                        locatorJson = "",
                        fallbackHref = "",
                        fallbackPosition = pageNumber
                    )
                }
            }
        }
    }

    private fun refreshCurrentBookmarkState() {
        val locator = currentBookmarkLocator ?: return
        val position = if (isReflowMode.value || isPdfBook) lastKnownReflowPage else locator.locations.position ?: 0
        _isCurrentPageBookmarked.value = bookmarks.value.any { bookmark ->
            val savedLocator = runCatching {
                Locator.fromJSON(JSONObject(bookmark.locatorJson))
            }.getOrNull() ?: return@any false
            if (savedLocator.href.toString() != locator.href.toString()) {
                return@any false
            }
            val savedPosition = savedLocator.locations.position ?: 0
            if (position > 0 && savedPosition > 0) {
                savedPosition == position
            } else {
                val currentProgress = locator.locations.totalProgression
                val savedProgress = savedLocator.locations.totalProgression
                currentProgress != null && savedProgress != null && abs(currentProgress - savedProgress) < 0.002
            }
        }
    }

    fun loadNextPage() {
        pdfReflowManager.loadNextPage(viewModelScope)
    }

    fun loadPreviousPage() {
        pdfReflowManager.loadPreviousPage(viewModelScope)
    }

    fun jumpToReflowPage(page: Int) {
        lastKnownReflowPage = page
        pdfReflowManager.jumpToPage(viewModelScope, page)
    }

    fun pauseReflowTts() {
        pdfReflowManager.pauseReflowTts()

        switchSessionMode(ReadingMode.MANUAL)
    }

    fun skipReflowTtsForward()  {
        viewModelScope.launch { pdfReflowManager.playReflowTts(viewModelScope, currentSpeed.value, startIndex = ttsActiveBlockIndex.value + 1) }

    }

    fun skipReflowTtsBackward() {
        viewModelScope.launch { pdfReflowManager.playReflowTts(viewModelScope, currentSpeed.value, startIndex = (ttsActiveBlockIndex.value - 1).coerceAtLeast(0)) }

    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Sleep timer ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    fun startSleepTimer(minutes: Int) {
        val preset = when (minutes) {
            -1 -> SleepTimerPreset.END_OF_CHAPTER
            15 -> SleepTimerPreset.MIN_15
            30 -> SleepTimerPreset.MIN_30
            45 -> SleepTimerPreset.MIN_45
            60 -> SleepTimerPreset.MIN_60
            else -> if (minutes > 0) SleepTimerPreset.MIN_15 else SleepTimerPreset.OFF
        }
        ttsManager.smartSleepTimerManager.setSleepTimer(preset)
        if (minutes > 0) {
            sleepTimer.startSleepTimer(minutes, currentSpeed.value, viewModelScope, { speed -> updateActiveTtsSpeedOnly(speed) }, { stopTts(); ambienceEngine.stop() })
        } else {
            sleepTimer.cancelSleepTimer { speed -> updateActiveTtsSpeedOnly(speed) }
        }
    }

    fun cancelSleepTimer() {
        ttsManager.smartSleepTimerManager.setSleepTimer(SleepTimerPreset.OFF)
        sleepTimer.cancelSleepTimer { speed -> updateActiveTtsSpeedOnly(speed) }
    }

    val selectedLanguageFilter = sherpaModelManager.selectedLanguageFilter
    val autoDetectBookLanguage = sherpaModelManager.autoDetectBookLanguage
    val downloadedModelIds = sherpaModelManager.downloadedModelIds

    fun setSelectedLanguageFilter(language: SupportedTtsLanguage) {
        sherpaModelManager.setSelectedLanguageFilter(language)
    }

    fun setAutoDetectBookLanguage(enabled: Boolean) {
        sherpaModelManager.setAutoDetectBookLanguage(enabled)
    }

    fun deleteDownloadedModel(modelId: String) {
        viewModelScope.launch {
            ttsManager.stop()
            sherpaModelManager.deleteDownloadedModel(modelId)
            ttsManager.resetSherpa()
        }
    }

    val builtInLexiconEnabled = pronunciationLexiconManager.builtInEnabled
    val customPhoneticRules = pronunciationLexiconManager.customRules

    fun setBuiltInLexiconEnabled(enabled: Boolean) {
        pronunciationLexiconManager.setBuiltInEnabled(enabled)
    }

    fun addCustomPhoneticRule(original: String, phonetic: String, caseSensitive: Boolean = false) {
        pronunciationLexiconManager.addCustomRule(original, phonetic, caseSensitive)
    }

    fun removeCustomPhoneticRule(id: String) {
        pronunciationLexiconManager.removeCustomRule(id)
    }
    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Optimized sleep timer with minimal wake-ups ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    fun updateActiveTtsSpeedOnly(speed: Float) {
        _currentSpeed.value = speed
        pdfReflowManager.onSpeedChange(speed, viewModelScope)
        epubFormatManager.syncPreferences(speed, userPreferences.value.ttsLanguage, _currentPitch.value)
    }

    private fun updateChapterMetadata(locator: Locator) {
        val resourceHref = locator.href.toString()

        val resourceIndex = publication?.readingOrder
            ?.indexOfFirst { it.href.toString() == resourceHref } ?: -1

        var timeRemaining: Int? = null
        val prog = (locator.locations.progression ?: 0.0).toFloat()

        if (resourceIndex != -1 && resourceIndex < positionsByResource.size) {
            val resourcePositions = positionsByResource[resourceIndex]

            val currentPosInResource = (locator.locations.progression ?: 0.0) * resourcePositions.size
            val remainingInResource = resourcePositions.size - currentPosInResource

            timeRemaining = (remainingInResource * 0.8).toInt().coerceAtLeast(1)
        }

        val resolvedTitle = locator.title?.takeIf { it.isNotBlank() }
            ?: publication?.let { pub -> resolveChapterTitle(pub, resourceHref) }

        _chapterInfo.value = ChapterInfo(
            title = resolvedTitle,
            progress = prog,
            timeRemainingMinutes = timeRemaining
        )
    }

    private fun resolveChapterTitle(pub: Publication, resourceHref: String): String? {
        val normalized = EpubPackageSanitizer.normalizeResourcePath(resourceHref)
        val fileName = normalized.substringAfterLast('/')

        fun findInLinks(links: List<Link>): String? {
            for (link in links) {
                val linkNorm = EpubPackageSanitizer.normalizeResourcePath(link.href.toString())
                if (linkNorm == normalized ||
                    linkNorm.endsWith("/$normalized") ||
                    normalized.endsWith("/$linkNorm") ||
                    fileName.equals(linkNorm.substringAfterLast('/'), ignoreCase = true)
                ) {
                    val title = link.title?.trim()?.takeIf { it.isNotEmpty() }
                    if (title != null) return title
                }
                val child = findInLinks(link.children)
                if (child != null) return child
            }
            return null
        }

        return findInLinks(pub.tableOfContents)
            ?: findInLinks(pub.readingOrder)
            ?: pub.readingOrder.indexOfFirst { it.href.toString() == resourceHref }
                .takeIf { it >= 0 }
                ?.let { idx -> pub.tableOfContents.getOrNull(idx)?.title?.trim()?.takeIf { it.isNotEmpty() } }
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Misc ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬
    private var lastUserInteractionTime = 0L

    fun onUserInteraction()      { lastUserInteractionTime = System.currentTimeMillis() }

    fun isInteractionLockActive() = (System.currentTimeMillis() - lastUserInteractionTime) < 3000L

    fun togglePdfBookView() {
        val currentState = _viewState.value
        val isActivating = !currentState.isPdfBookViewActive
        val processedDoc = epubFormatManager.currentProcessedDocument.value
        val blocks = processedDoc?.displayBlocks ?: emptyList()

        if (isActivating) {
            val currentPage = currentTtsPage.value
            val targetBlock = pdfBookViewSynchronizer.findBlockIndexForPage(blocks, currentPage)
            _viewState.update {
                it.copy(
                    isPdfBookViewActive = true,
                    pdfBookViewBlockIndex = targetBlock
                )
            }
        } else {
            val currentBlock = currentState.pdfBookViewBlockIndex
            val totalPages = pdfReflowManager.reflowState.value.totalPages
            val targetPage = pdfBookViewSynchronizer.findPageForBlockIndex(blocks, currentBlock, totalPages)
            pdfReflowManager.jumpToPage(viewModelScope, targetPage)
            _viewState.update {
                it.copy(
                    isPdfBookViewActive = false
                )
            }
        }
    }

    fun saveCurrentProgressBeforeExit() {
        _isAutoScrollActive.value = false
        if (_showEpubCoverPage.value) return
        if (isReflowMode.value) {
            val page = lastKnownReflowPage.takeIf { it > 0 }
                ?: pdfReflowManager.reflowElements.value.firstOrNull()?.pageNumber ?: 1
            val totalPages = pdfReflowManager.reflowState.value.totalPages
            val progress = if (totalPages > 0) (page.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) else _globalProgress.value

            CoroutineScope(Dispatchers.IO).launch {
                bookRepository.updateReadingProgressWithFallback(
                    bookId = bookId,
                    progress = progress,
                    locatorJson = "",
                    fallbackHref = "",
                    fallbackPosition = page
                )
            }
        } else if (isPdfBook) {
            val nav = navigator as? VisualNavigator
            val navLoc = nav?.currentLocator?.value
            val page = navLoc?.locations?.position ?: lastKnownReflowPage.takeIf { it > 0 } ?: 1
            lastKnownReflowPage = page
            val totalPages = totalPositions.takeIf { it > 0 }
                ?: pdfReflowManager.reflowState.value.totalPages.takeIf { it > 0 }
                ?: publication?.readingOrder?.size?.takeIf { it > 0 }
                ?: 1
            val progress = (page.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

            CoroutineScope(Dispatchers.IO).launch {
                bookRepository.updateReadingProgressWithFallback(
                    bookId = bookId,
                    progress = progress,
                    locatorJson = navLoc?.toJSON()?.toString() ?: "",
                    fallbackHref = navLoc?.href?.toString() ?: "",
                    fallbackPosition = page
                )
            }
        } else {
            val currentLocator = (navigator as? VisualNavigator)?.currentLocator?.value
            if (currentLocator != null) {
                saveProgress(currentLocator)
            }
        }
    }

    fun dismissEpubCoverPage() {
        _showEpubCoverPage.value = false
    }

    override fun onCleared() {
        ambientSoundscapePlayer.onReaderVisibilityChanged(false)
        stopTts()
        ambienceEngine.stop()
        saveCurrentProgressBeforeExit()
        stopActiveSession()
        cancelSleepTimer()

        pdfReflowManager.onCleared()
        epubFormatManager.onCleared(context)

        super.onCleared()
        ttsManager.onQueueCompletedNaturally = null
        ttsManager.stop()
        ttsManager.setCurrentBookId(null)

        positionsJob?.cancel()
        positionsJob = null
        navigatorLocatorJob?.cancel()
        navigatorLocatorJob = null
        publication?.close()
        publication = null
        navigator = null
        positionsByResource = emptyList()
        resourceIndexCache = null
        prefetchJob?.cancel()
        prefetchJob = null
        chapterLruCache.evictAll()
    }

    fun generateXRayReport(currentParagraphIndex: Int? = null): ChapterXRayReport {
        val title = _chapterInfo.value.title?.takeIf { it.isNotBlank() } ?: "Capítulo actual"

        val epubBlocks = epubFormatManager.currentProcessedDocument.value?.displayBlocks
        val cachedDoc = if (epubBlocks.isNullOrEmpty()) {
            val href = (navigator as? VisualNavigator)?.currentLocator?.value?.href?.toString()
            if (href != null) {
                val pub = publication
                val idx = if (pub != null) resourceIndexCache?.get(href) ?: findMatchingResourceIndex(href, pub.readingOrder) else -1
                if (idx != -1) chapterLruCache.get(idx)?.document else null
            } else null
        } else null

        val effectiveBlocks = epubBlocks ?: cachedDoc?.displayBlocks

        val paragraphs = if (!effectiveBlocks.isNullOrEmpty()) {
            effectiveBlocks.mapNotNull { block ->
                when (block) {
                    is ReadingBlock.Paragraph -> block.text
                    is ReadingBlock.Heading -> block.text
                    else -> null
                }
            }.filter { it.isNotBlank() }
        } else {
            pdfReflowManager.reflowState.value.items.mapNotNull { item ->
                when (item) {
                    is ReflowItem.Paragraph -> item.content
                    is ReflowItem.Header -> item.content
                    is ReflowItem.Text -> item.element.locator.text.highlight
                    else -> null
                }
            }.filter { it.isNotBlank() }
        }

        val currentIdx = currentParagraphIndex ?: run {
            val ttsIdx = if (isReflowMode.value) {
                pdfReflowManager.ttsActiveBlockIndex.value
            } else {
                -1
            }
            if (ttsIdx in paragraphs.indices) {
                ttsIdx
            } else {
                val prog = (navigator as? VisualNavigator)?.currentLocator?.value?.locations?.progression ?: 0.0
                if (paragraphs.isNotEmpty()) {
                    ((prog * paragraphs.size).toInt()).coerceIn(0, paragraphs.size - 1)
                } else 0
            }
        }

        return BookXRayEngine.analyzeChapter(
            chapterTitle = title,
            paragraphs = paragraphs,
            currentParagraphIndex = currentIdx
        )
    }

}






data class ReaderViewState(
    val isTtsPlaying: Boolean = false,
    val currentSpeed: Float = 1.0f,
    val ttsHighlightStyle: com.example.woodsreader.model.TtsHighlightStyle = com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_YELLOW,
    val sleepTimerMinutes: Int? = null,
    val readingMode: com.example.woodsreader.data.model.ReadingMode = com.example.woodsreader.data.model.ReadingMode.MANUAL,
    val fontSize: Float = 16f,
    val lineSpacing: Float = 1.5f,
    val isBookmarked: Boolean = false,
    val globalProgress: Float = 0f,
    val chapterProgress: Float = 0f,
    val activeNarrationBlockId: String? = null,
    val activeNarrationWordRange: IntRange? = null,
    val isPdfBookViewActive: Boolean = false,
    val pdfBookViewBlockIndex: Int = 0
)
