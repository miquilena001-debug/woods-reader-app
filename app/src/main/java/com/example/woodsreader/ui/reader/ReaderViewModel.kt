package com.example.woodsreader.ui.reader
import android.app.Application
import com.example.woodsreader.core.pipeline.PdfBookViewSynchronizer
import com.example.woodsreader.core.managers.SleepTimer
import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.core.managers.ReadingProgressManager
import com.example.woodsreader.domain.reader.SaveReadingProgressUseCase
import com.example.woodsreader.domain.reader.CalculateReadingStatsUseCase
import com.example.woodsreader.domain.reader.LoadBookFastUseCase
import com.example.woodsreader.domain.reader.LoadBookResult
import com.example.woodsreader.domain.reader.ManageReaderAnnotationsUseCase
import com.example.woodsreader.domain.reader.ReaderIntelligenceAndExportCoordinator
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
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.PronunciationLexiconManager
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import com.example.woodsreader.core.tts.SleepTimerPreset
import com.example.woodsreader.core.tts.EdgeNeuralTtsClient
import com.example.woodsreader.core.tts.EdgeVoiceOption
import com.example.woodsreader.core.tts.AmbientSoundscapePlayer
import com.example.woodsreader.core.search.FastInBookSearchHit
import com.example.woodsreader.core.search.FastSearchProgress
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
import kotlinx.coroutines.CancellationException

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
import com.example.woodsreader.core.xray.ChapterXRayReport
import com.example.woodsreader.core.license.ProLicenseManager

import com.example.woodsreader.core.share.QuoteVideoClipExporter
import com.example.woodsreader.core.system.WoodsKeepAliveManager
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

data class AutoScrollTeleprompterState(
    val isActive: Boolean = false,
    val wpm: Int = 220,
    val isManuallyPaused: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalReadiumApi::class)

@HiltViewModel

class ReaderViewModel @Inject constructor(
    private val context: Application,

    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,

    private val userPreferencesRepository: UserPreferencesRepository,
    private val loadBookFastUseCase: LoadBookFastUseCase,
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
    private val readerIntelligenceAndExportCoordinator: ReaderIntelligenceAndExportCoordinator,
    private val edgeNeuralTtsClient: EdgeNeuralTtsClient,
    private val pronunciationLexiconManager: PronunciationLexiconManager,
    val proLicenseManager: ProLicenseManager,
    val ambientSoundscapePlayer: AmbientSoundscapePlayer,
    val woodsKeepAliveManager: WoodsKeepAliveManager,
    val quoteVideoClipExporter: QuoteVideoClipExporter,
    private val saveReadingProgressUseCase: SaveReadingProgressUseCase,
    private val calculateReadingStatsUseCase: CalculateReadingStatsUseCase,
    private val manageReaderAnnotationsUseCase: ManageReaderAnnotationsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "ReaderViewModel"

        private const val INACTIVITY_THRESHOLD = 5 * 60 * 1000L
        private const val MAX_CACHED_REFLOW_PAGES = 50
        private const val MAX_CACHED_CHAPTERS = 5

        fun extractPdfPagesFromToc(toc: List<Link>): List<Int> {
            val pages = mutableListOf<Int>()
            fun traverse(links: List<Link>) {
                for (link in links) {
                    val href = link.href.toString()
                    val page = Regex("""(?i)[#?&]page=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""(?i)/page[_-]?(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""#(\d+)$""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""^/?(\d+)$""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                    if (page != null && page > 0) {
                        pages.add(page)
                    }
                    if (link.children.isNotEmpty()) {
                        traverse(link.children)
                    }
                }
            }
            traverse(toc)
            return pages.distinct().sorted()
        }

        fun resolveNextPdfPageFromToc(toc: List<Link>, currentPage: Int): Int? {
            return extractPdfPagesFromToc(toc).firstOrNull { it > currentPage }
        }

        fun resolvePreviousPdfPageFromToc(toc: List<Link>, currentPage: Int): Int? {
            return extractPdfPagesFromToc(toc).lastOrNull { it < currentPage }
        }
    }

    val chapterLruCache = LruCache<Int, ParsedChapterData>(MAX_CACHED_CHAPTERS)
    private var prefetchJob: Job? = null

    val bookId: String = savedStateHandle.get<String>("bookId").orEmpty()
    var publication: Publication? = null

    private var navigatorLocatorJob: Job? = null
    private var lastSavedLocatorJson: String? = null
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
    val currentNarrationUnitIndex: StateFlow<Int> = ttsManager.currentNarrationUnitIndex

    private val autoScrollPrefs by lazy {
        context.getSharedPreferences("woods_teleprompter_prefs", Context.MODE_PRIVATE)
    }
    private val _isAutoScrollActive = MutableStateFlow(false)
    val isAutoScrollActive: StateFlow<Boolean> = _isAutoScrollActive.asStateFlow()

    private val _isAutoScrollManuallyPaused = MutableStateFlow(false)
    val isAutoScrollManuallyPaused: StateFlow<Boolean> = _isAutoScrollManuallyPaused.asStateFlow()

    private val _autoScrollWpm = MutableStateFlow(
        autoScrollPrefs.getInt("auto_scroll_wpm", 220).coerceIn(100, 450)
    )
    val autoScrollWpm: StateFlow<Int> = _autoScrollWpm.asStateFlow()

    val teleprompterState: StateFlow<AutoScrollTeleprompterState> = combine(
        _isAutoScrollActive,
        _autoScrollWpm,
        _isAutoScrollManuallyPaused
    ) { active, wpm, paused ->
        AutoScrollTeleprompterState(
            isActive = active,
            wpm = wpm,
            isManuallyPaused = paused
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AutoScrollTeleprompterState()
    )

    fun toggleAutoScroll() {
        val willBeActive = !_isAutoScrollActive.value
        _isAutoScrollActive.value = willBeActive
        if (willBeActive) {
            _isAutoScrollManuallyPaused.value = false
            hideControls()
            if (ttsState.value == TtsState.PLAYING) {
                pauseTts()
            }
        }
    }

    fun toggleAutoScrollPause() {
        _isAutoScrollManuallyPaused.update { !it }
    }

    fun setAutoScrollManuallyPaused(paused: Boolean) {
        _isAutoScrollManuallyPaused.value = paused
    }

    fun setAutoScrollActive(active: Boolean) {
        _isAutoScrollActive.value = active
        if (active) {
            _isAutoScrollManuallyPaused.value = false
            hideControls()
            if (ttsState.value == TtsState.PLAYING) {
                pauseTts()
            }
        }
    }

    fun setAutoScrollWpm(wpm: Int) {
        val clamped = wpm.coerceIn(100, 450)
        _autoScrollWpm.value = clamped
        autoScrollPrefs.edit().putInt("auto_scroll_wpm", clamped).apply()
    }

    val currentTtsPage = pdfReflowManager.currentTtsPage
    val sleepTimerRemaining = sleepTimer.sleepTimerRemaining
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

    val highlights = manageReaderAnnotationsUseCase.getHighlights(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarks = manageReaderAnnotationsUseCase.getBookmarks(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val audioExportState = readerIntelligenceAndExportCoordinator.audioExportState
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
        readerIntelligenceAndExportCoordinator.resetAudioExportState()
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
        readerIntelligenceAndExportCoordinator.cancelAudioExport()
    }

    fun exportAudioChapters(maxChaptersToExport: Int = 1) {
        val book = (_uiState.value as? ReaderUiState.Success)?.book ?: return
        val pub = publication ?: return
        val startChapTitle = currentChapterTitle.value.ifBlank { "Capitulo" }
        val coverFile = readerIntelligenceAndExportCoordinator.resolveCoverFile(book, context)

        if (isReflowMode.value || isPdfBook) {
            readerIntelligenceAndExportCoordinator.exportReflowChapter(
                scope = viewModelScope,
                book = book,
                chapterTitle = startChapTitle,
                items = pdfReflowManager.reflowState.value.items,
                speed = currentSpeed.value,
                coverFile = coverFile
            )
        } else {
            val currentHref = (navigator as? org.readium.r2.navigator.VisualNavigator)?.currentLocator?.value?.href?.toString().orEmpty()
            readerIntelligenceAndExportCoordinator.exportEpubChapters(
                scope = viewModelScope,
                book = book,
                readingOrder = pub.readingOrder,
                currentHref = currentHref,
                startChapTitle = startChapTitle,
                maxChaptersToExport = maxChaptersToExport,
                speed = currentSpeed.value,
                coverFile = coverFile
            ) { targetIdx, link ->
                val cached = chapterLruCache.get(targetIdx)
                val doc = cached?.document ?: epubFormatManager.processResource(link.href.toString(), book.id)
                if (doc != null && cached == null) {
                    val title = link.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${targetIdx + 1}"
                    chapterLruCache.put(targetIdx, ParsedChapterData(targetIdx, link.href.toString(), title, doc))
                }
                val texts = doc?.ttsNarrationUnits?.map { it.textToSpeak } ?: emptyList()
                val title = link.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${targetIdx + 1}"
                com.example.woodsreader.core.tts.ChapterTextChunk(title, texts)
            }
        }
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
        viewModelScope.launch {
            ttsState.collect { state ->
                if (state == TtsState.PLAYING) {
                    _isAutoScrollActive.value = false
                    _isAutoScrollManuallyPaused.value = false
                }
            }
        }
        viewModelScope.launch {
            ttsManager.smartSleepTimerManager.activePreset.collect { preset ->
                if (preset == SleepTimerPreset.OFF && sleepTimer.sleepTimerRemaining.value == -1L) {
                    sleepTimer.cancelSleepTimer { updateActiveTtsSpeedOnly(it) }
                    ambienceEngine.stop()
                }
            }
        }

        loadBook()
    }

    private fun loadBook() {
        if (bookId.isBlank()) {
            _uiState.value = ReaderUiState.Error("ID de libro no válido o no proporcionado")
            return
        }
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

                val loadedBook = loadBookFastUseCase.getBookEntity(bookId)
                if (loadedBook == null) {
                    Log.e(TAG, "loadBook: Book not found for id $bookId")
                    _uiState.value = ReaderUiState.Error("Libro no encontrado en la base de datos")
                    return@launch
                }
                var book: BookEntity = loadedBook

                _showEpubCoverPage.value = false
                lastProcessedChapterHref = null
                _uiState.value = ReaderUiState.InitialLoading(book)

                val sessionData = when (val result = loadBookFastUseCase(book)) {
                    is LoadBookResult.Error -> {
                        Log.e(TAG, "loadBook: Failed to load book $bookId: ${result.message}", result.throwable)
                        _uiState.value = ReaderUiState.Error(result.message)
                        return@launch
                    }
                    is LoadBookResult.Success -> result.data
                }

                val pub = sessionData.publication
                publication = pub
                resourceIndexCache = sessionData.resourceIndexCache
                positionsByResource = sessionData.positionsByResource
                totalPositions = sessionData.totalPositions
                val uri = sessionData.uri

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
                    val initialPage = sessionData.initialPdfPage ?: book.fallbackLocatorPosition?.takeIf { it > 0 } ?: 1
                    lastKnownReflowPage = initialPage
                    pdfReflowManager.setReflowMode(enabled = true, scope = viewModelScope, initialPosition = initialPage)
                    null
                } else {
                    pdfReflowManager.setReflowMode(enabled = false, scope = viewModelScope)
                    val loc = sessionData.initialLocator
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
        ttsManager.setRate(speed)
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

    private var pitchUpdateJob: Job? = null

    fun onPitchChange(pitch: Float) {
        val coercedPitch = (Math.round(pitch * 100.0f) / 100.0f).coerceIn(0.5f, 2.0f)
        _currentPitch.value = coercedPitch
        ttsManager.setPitch(coercedPitch)
        epubFormatManager.syncPreferences(_currentSpeed.value, userPreferences.value.ttsLanguage, coercedPitch)

        pitchUpdateJob?.cancel()
        pitchUpdateJob = viewModelScope.launch {
            delay(150L)
            userPreferencesRepository.updateTtsPitch(coercedPitch)
        }
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

    // ── Highlights & Bookmarks ──────────────────────────────────────────────

    fun deleteHighlight(highlight: HighlightEntity) = viewModelScope.launch {
        manageReaderAnnotationsUseCase.deleteHighlight(highlight)
    }

    fun addHighlight(locator: Locator, color: HighlightColor, text: String, note: String = "") {
        viewModelScope.launch {
            val chapter = locator.title ?: _chapterInfo.value.title ?: ""
            manageReaderAnnotationsUseCase.saveHighlight(
                bookId = bookId,
                locator = locator,
                color = color,
                text = text,
                note = note,
                chapterTitle = chapter
            )
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

        manageReaderAnnotationsUseCase.createBookmark(
            bookId = bookId,
            locator = bookmarkLocator,
            label = label,
            chapterTitle = chapter,
            page = page,
            progress = _globalProgress.value,
            snippet = snippet
        )
    }

    fun deleteBookmark(bookmark: BookmarkEntity) = viewModelScope.launch {
        manageReaderAnnotationsUseCase.deleteBookmark(bookmark)
    }

    fun toggleBookmark(locator: Locator?, label: String? = null) = viewModelScope.launch {
        val currentLocator = (navigator as? VisualNavigator)?.currentLocator?.value ?: locator ?: return@launch
        val page = if (isReflowMode.value || isPdfBook) lastKnownReflowPage else currentLocator.locations.position ?: 1
        val bookmarkLocator = if (isReflowMode.value || isPdfBook) {
            currentLocator.copy(locations = currentLocator.locations.copy(position = page))
        } else {
            currentLocator
        }
        currentBookmarkLocator = bookmarkLocator
        val existing = manageReaderAnnotationsUseCase.findMatchingBookmark(
            bookmarks = bookmarks.value,
            currentLocator = bookmarkLocator,
            isPdfOrReflow = isReflowMode.value || isPdfBook,
            reflowPage = page
        )
        if (existing != null) {
            manageReaderAnnotationsUseCase.deleteBookmark(existing)
        } else {
            addBookmark(bookmarkLocator, label)
        }
        refreshCurrentBookmarkState()
    }

    fun exportNotesToMarkdown(bookTitle: String, bookAuthor: String): String {
        return manageReaderAnnotationsUseCase.exportToMarkdown(bookTitle, bookAuthor, highlights.value)
    }

    fun exportNotesToPlainText(bookTitle: String, bookAuthor: String): String {
        return manageReaderAnnotationsUseCase.exportToPlainText(bookTitle, bookAuthor, highlights.value)
    }

    fun formatCitation(text: String, bookTitle: String, author: String, chapterTitle: String? = null): String {
        return manageReaderAnnotationsUseCase.formatCitation(text, bookTitle, author, chapterTitle)
    }

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

    private fun onTtsLocatorChanged(locator: Locator) {
        if (_showEpubCoverPage.value) return
        currentBookmarkLocator = locator
        val locJson = saveReadingProgressUseCase.serializeLocator(locator)
        lastSavedLocatorJson = locJson
        saveReadingProgressUseCase.setLastSavedLocatorJson(locJson)
        updateChapterMetadata(locator)
        val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L
        val totalProgression = locator.locations.totalProgression ?: 0.0
        val activeWpm = 200
        _bookTimeRemaining.value = calculateReadingStatsUseCase.calculateBookTimeRemaining(
            totalWords = totalWords,
            totalProgression = totalProgression,
            activeWpm = activeWpm
        )

        if (totalPositions > 0) {
            val pub = publication
            if (pub != null) {
                val resourceIndex = resourceIndexCache?.get(locator.href.toString()) 
                    ?: pub.readingOrder.indexOfFirst { it.href.toString() == locator.href.toString() }
                val chapterStats = calculateReadingStatsUseCase.calculateChapterProgress(
                    locatorProgression = locator.locations.progression ?: 0.0,
                    resourceIndex = resourceIndex,
                    positionsByResource = positionsByResource,
                    totalPositions = totalPositions,
                    activeWpm = activeWpm
                )
                if (chapterStats != null) {
                    _chapterTimeRemaining.value = chapterStats.chapterTimeRemaining
                    _globalProgress.value = chapterStats.globalProgress
                }
            }
        }

        saveReadingProgressUseCase.saveTtsProgressDebounced(
            scope = viewModelScope,
            bookId = bookId,
            locator = locator
        )
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

            saveReadingProgressUseCase.saveProgressDebounced(
                scope = viewModelScope,
                bookId = bookId,
                progress = _globalProgress.value,
                locator = locator,
                fallbackPosition = page
            )
            return
        }

        if (!isPdfBook) {
            val locJson = saveReadingProgressUseCase.serializeLocator(locator)
            if (locJson != lastSavedLocatorJson) {
                lastSavedLocatorJson = locJson
                saveReadingProgressUseCase.setLastSavedLocatorJson(locJson)
                saveProgress(locator)
            }
        }
    }

    fun saveProgress(locator: Locator) {
        if (readingSessionManager.currentSessionMode == ReadingMode.MANUAL) {
            val currentProg = locator.locations.totalProgression ?: 0.0
            val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L
            val wordsRead = calculateReadingStatsUseCase.calculateWordsDelta(
                currentProgression = currentProg,
                lastRecordedProgression = lastRecordedProgression,
                estimatedWordCount = totalWords
            )
            if (wordsRead > 0) {
                readingSessionManager.incrementWords(wordsRead)
            }
            lastRecordedProgression = currentProg
        }

        // Track unique pages read in this session
        val pageKey = "${locator.href}#${locator.locations.position ?: 0}"
        val isNewPage = !readingSessionManager.pagesRead.value.contains(pageKey)
        if (isNewPage) {
            readingSessionManager.addPageRead(pageKey)
            viewModelScope.launch {
                calculateReadingStatsUseCase.recordPageRead(
                    pageKey = pageKey,
                    isAlreadyRead = false
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
        val activeWpm = calculateReadingStatsUseCase.calculateActiveWpm(
            sessionStartTimeMs = readingSessionManager.sessionStartTime,
            wordsProcessed = readingSessionManager.wordsProcessed.value
        )

        val totalProgression = locator.locations.totalProgression ?: 0.0
        _bookTimeRemaining.value = calculateReadingStatsUseCase.calculateBookTimeRemaining(
            totalWords = totalWords,
            totalProgression = totalProgression,
            activeWpm = activeWpm
        )

        if (totalPositions > 0) {
            val pub = publication
            if (pub != null) {
                val resourceIndex = resourceIndexCache?.get(locator.href.toString()) 
                    ?: pub.readingOrder.indexOfFirst { it.href.toString() == locator.href.toString() }
                val chapterStats = calculateReadingStatsUseCase.calculateChapterProgress(
                    locatorProgression = locator.locations.progression ?: 0.0,
                    resourceIndex = resourceIndex,
                    positionsByResource = positionsByResource,
                    totalPositions = totalPositions,
                    activeWpm = activeWpm
                )
                if (chapterStats != null) {
                    _chapterTimeRemaining.value = chapterStats.chapterTimeRemaining
                    _globalProgress.value = chapterStats.globalProgress
                }
            }
        }

        updateChapterMetadata(locator)

        val calculatedProg = _globalProgress.value
        val locProg = (locator.locations.totalProgression ?: 0.0).toFloat()
        val totalProg = if (calculatedProg > 0f) calculatedProg else locProg

        saveReadingProgressUseCase.saveProgressDebounced(
            scope = viewModelScope,
            bookId = bookId,
            progress = totalProg,
            locator = locator
        )

        recordActivity()
    }

    fun goToLocator(nav: Navigator?, locator: Locator) {
        val visualNav = (nav as? VisualNavigator) ?: (navigator as? VisualNavigator)
        visualNav?.go(locator, animated = true)
        if (isReflowMode.value) {
            locator.locations.position?.let { page ->
                jumpToReflowPage(page)
            }
        }
        if (ttsState.value == TtsState.PLAYING) {
            playTts(locator)
        } else {
            _lastTtsLocator.value = locator
        }
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

        if (isPdfBook || isReflowMode.value) {
            val toc = pub.tableOfContents
            if (toc.isNotEmpty()) {
                val currentPage = if (isReflowMode.value) {
                    lastKnownReflowPage.takeIf { it > 0 } 
                        ?: pdfReflowManager.currentTtsPage.value.takeIf { it > 0 } 
                        ?: 1
                } else {
                    val visualPage = ((navigator ?: this.navigator) as? VisualNavigator)?.currentLocator?.value?.locations?.position ?: lastKnownReflowPage
                    lastKnownReflowPage = visualPage
                    visualPage.takeIf { it > 0 } ?: 1
                }
                val targetPage = resolveNextPdfPageFromToc(toc, currentPage)

                if (targetPage != null) {
                    if (isReflowMode.value) {
                        jumpToReflowPage(targetPage)
                        if (ttsState.value == TtsState.PLAYING) {
                            getLocatorForPdfPage(targetPage)?.let(::playTts)
                        }
                    } else {
                        val nav = (navigator ?: this.navigator) as? VisualNavigator
                        val readingOrderLink = pub.readingOrder.firstOrNull()
                        val targetLoc = readingOrderLink?.let { pub.locatorFromLink(it) }
                            ?.copy(locations = Locator.Locations(position = targetPage))
                        if (targetLoc != null) {
                            nav?.go(targetLoc, animated = false)
                            if (ttsState.value == TtsState.PLAYING) {
                                playTts(targetLoc)
                            } else {
                                _lastTtsLocator.value = targetLoc
                            }
                        }
                    }
                    return
                }
            }
            return
        }

        val nav = (navigator ?: this.navigator) as? VisualNavigator ?: return

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
                if (ttsState.value == TtsState.PLAYING) {
                    playTts(nextLocator)
                } else {
                    _lastTtsLocator.value = nextLocator
                }
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

        if (isPdfBook || isReflowMode.value) {
            val toc = pub.tableOfContents
            if (toc.isNotEmpty()) {
                val currentPage = if (isReflowMode.value) {
                    lastKnownReflowPage.takeIf { it > 0 } 
                        ?: pdfReflowManager.currentTtsPage.value.takeIf { it > 0 } 
                        ?: 1
                } else {
                    val visualPage = ((navigator ?: this.navigator) as? VisualNavigator)?.currentLocator?.value?.locations?.position ?: lastKnownReflowPage
                    lastKnownReflowPage = visualPage
                    visualPage.takeIf { it > 0 } ?: 1
                }
                val targetPage = resolvePreviousPdfPageFromToc(toc, currentPage)

                if (targetPage != null) {
                    if (isReflowMode.value) {
                        jumpToReflowPage(targetPage)
                        if (ttsState.value == TtsState.PLAYING) {
                            getLocatorForPdfPage(targetPage)?.let(::playTts)
                        }
                    } else {
                        val nav = (navigator ?: this.navigator) as? VisualNavigator
                        val readingOrderLink = pub.readingOrder.firstOrNull()
                        val targetLoc = readingOrderLink?.let { pub.locatorFromLink(it) }
                            ?.copy(locations = Locator.Locations(position = targetPage))
                        if (targetLoc != null) {
                            nav?.go(targetLoc, animated = false)
                            if (ttsState.value == TtsState.PLAYING) {
                                playTts(targetLoc)
                            } else {
                                _lastTtsLocator.value = targetLoc
                            }
                        }
                    }
                    return
                }
            }
            return
        }

        val nav = (navigator ?: this.navigator) as? VisualNavigator ?: return

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
                if (ttsState.value == TtsState.PLAYING) {
                    playTts(prevLocator)
                } else {
                    _lastTtsLocator.value = prevLocator
                }
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
        _isAutoScrollActive.value = false
        _isAutoScrollManuallyPaused.value = false
        _lastTtsLocator.value = null
        switchSessionMode(ReadingMode.TTS)
        if (isReflowMode.value || isPdfBook) {
            epubFormatManager.stopTts()
            pdfReflowManager.stopReflowTts()
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
            if (pdfReflowManager.ttsState.value == TtsState.PLAYING ||
                pdfReflowManager.ttsState.value == TtsState.PAUSED
            ) {
                pdfReflowManager.stopReflowTts()
            }
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
        _isAutoScrollActive.value = false
        _isAutoScrollManuallyPaused.value = false
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
        } else {
            epubFormatManager.skipTtsChapterForward(context, publication, navigator)
        }
    }

    fun skipTtsChapterBackward() {
        if (isReflowMode.value || isPdfBook) {
            goToPreviousChapter(navigator)
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
                val isNewPage = !readingSessionManager.pagesRead.value.contains(pageKey)
                if (isNewPage) {
                    readingSessionManager.addPageRead(pageKey)
                    viewModelScope.launch {
                        calculateReadingStatsUseCase.recordPageRead(
                            pageKey = pageKey,
                            isAlreadyRead = false
                        )
                    }
                }

                recordActivity()
                saveReadingProgressUseCase.saveProgressDebounced(
                    scope = viewModelScope,
                    bookId = bookId,
                    progress = progress,
                    locator = null,
                    fallbackPosition = pageNumber,
                    force = true
                )
            }
        }
    }

    private fun refreshCurrentBookmarkState() {
        val locator = currentBookmarkLocator ?: return
        _isCurrentPageBookmarked.value = manageReaderAnnotationsUseCase.isPageBookmarked(
            bookmarks = bookmarks.value,
            currentLocator = locator,
            isPdfOrReflow = isReflowMode.value || isPdfBook,
            reflowPage = lastKnownReflowPage
        )
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
        if (minutes == -1) {
            ttsManager.smartSleepTimerManager.setSleepTimer(SleepTimerPreset.END_OF_CHAPTER)
            sleepTimer.startSleepTimer(
                minutes = -1,
                currentSpeed = currentSpeed.value,
                scope = viewModelScope,
                onSpeedChange = { speed -> updateActiveTtsSpeedOnly(speed) },
                onTimerExpired = {
                    stopTts()
                    ambienceEngine.stop()
                }
            )
        } else if (minutes > 0) {
            ttsManager.smartSleepTimerManager.setSleepTimer(SleepTimerPreset.OFF)
            sleepTimer.startSleepTimer(
                minutes = minutes,
                currentSpeed = currentSpeed.value,
                scope = viewModelScope,
                onSpeedChange = { speed -> updateActiveTtsSpeedOnly(speed) },
                onTimerExpired = {
                    stopTts()
                    ambienceEngine.stop()
                }
            )
        } else {
            cancelSleepTimer()
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
        return loadBookFastUseCase.resolveChapterTitle(pub, resourceHref)
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

    /**
     * Checkpoint instantáneo de posición al apagar pantalla o cambiar de app.
     * Guarda de inmediato en Room y en SharedPreferences sin esperar al debounce de 1500ms.
     */
    fun flushReadingProgressImmediately() {
        val nav = navigator as? VisualNavigator
        val ttsLocator = _currentTtsLocator.value.takeIf {
            ttsState.value == TtsState.PLAYING || ttsState.value == TtsState.PAUSED
        }
        val currentLoc = ttsLocator ?: nav?.currentLocator?.value ?: currentBookmarkLocator
            ?: lastSavedLocatorJson?.let { saved ->
                runCatching { Locator.fromJSON(org.json.JSONObject(saved)) }.getOrNull()
            }
        val chapterIdx = currentLoc?.let { locator ->
            val order = publication?.readingOrder.orEmpty()
            findMatchingResourceIndex(locator.href.toString(), order).takeIf { it >= 0 }
        } ?: getCurrentChapterIndex()
        val locJson = currentLoc?.toJSON()?.toString() ?: lastSavedLocatorJson ?: ""
        val href = currentLoc?.href?.toString() ?: ""
        val prog = currentLoc?.locations?.totalProgression?.toFloat()
            ?: currentLoc?.locations?.progression?.toFloat()
            ?: _globalProgress.value

        if (isReflowMode.value) {
            val page = currentLoc?.locations?.position ?: lastKnownReflowPage.takeIf { it > 0 }
                ?: pdfReflowManager.reflowElements.value.firstOrNull()?.pageNumber ?: 1
            val totalPages = pdfReflowManager.reflowState.value.totalPages
            val progress = currentLoc?.locations?.totalProgression?.toFloat()
                ?: currentLoc?.locations?.progression?.toFloat()
                ?: if (totalPages > 0) (page.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) else _globalProgress.value

            saveReadingProgressUseCase.flushImmediately(
                bookId = bookId,
                chapterIndex = chapterIdx,
                progress = progress,
                currentLocator = currentLoc,
                fallbackLocatorJson = locJson,
                fallbackHref = href,
                fallbackPosition = page
            )
        } else if (isPdfBook) {
            val page = currentLoc?.locations?.position ?: lastKnownReflowPage.takeIf { it > 0 } ?: 1
            lastKnownReflowPage = page
            val totalPages = totalPositions.takeIf { it > 0 }
                ?: pdfReflowManager.reflowState.value.totalPages.takeIf { it > 0 }
                ?: publication?.readingOrder?.size?.takeIf { it > 0 }
                ?: 1
            val progress = currentLoc?.locations?.totalProgression?.toFloat()
                ?: currentLoc?.locations?.progression?.toFloat()
                ?: (page.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

            saveReadingProgressUseCase.flushImmediately(
                bookId = bookId,
                chapterIndex = chapterIdx,
                progress = progress,
                currentLocator = currentLoc,
                fallbackLocatorJson = locJson,
                fallbackHref = href,
                fallbackPosition = page
            )
        } else {
            if (currentLoc != null) {
                saveProgress(currentLoc)
            }
            saveReadingProgressUseCase.flushImmediately(
                bookId = bookId,
                chapterIndex = chapterIdx,
                progress = prog,
                currentLocator = currentLoc,
                fallbackLocatorJson = locJson,
                fallbackHref = href,
                fallbackPosition = currentLoc?.locations?.position ?: 0
            )
        }
    }

    fun saveCurrentProgressBeforeExit() {
        _isAutoScrollActive.value = false
        if (_showEpubCoverPage.value) return
        flushReadingProgressImmediately()
    }

    fun dismissEpubCoverPage() {
        _showEpubCoverPage.value = false
    }

    // ── Búsqueda En-Libro Streaming con Cero Bloqueo ─────────────────────────
    val inBookSearchResults: StateFlow<List<FastInBookSearchHit>> = readerIntelligenceAndExportCoordinator.inBookSearchResults
    val inBookSearchProgress: StateFlow<FastSearchProgress?> = readerIntelligenceAndExportCoordinator.inBookSearchProgress
    val isInBookSearching: StateFlow<Boolean> = readerIntelligenceAndExportCoordinator.isInBookSearching

    fun getCurrentChapterIndex(): Int {
        val pub = publication ?: return 0
        val currentHref = (navigator as? VisualNavigator)?.currentLocator?.value?.href?.toString()
            ?: _currentTtsLocator.value?.href?.toString().orEmpty()
        return if (currentHref.isNotBlank()) {
            resourceIndexCache?.get(currentHref)
                ?: findMatchingResourceIndex(currentHref, pub.readingOrder).takeIf { it != -1 }
                ?: 0
        } else 0
    }

    fun searchInBook(query: String, startFromChapterIndex: Int? = null) {
        val startIndex = startFromChapterIndex ?: getCurrentChapterIndex()
        readerIntelligenceAndExportCoordinator.searchInBook(
            scope = viewModelScope,
            bookId = bookId,
            publication = publication,
            query = query,
            startIndex = startIndex
        )
    }

    fun cancelInBookSearch() {
        readerIntelligenceAndExportCoordinator.cancelInBookSearch()
    }

    override fun onCleared() {
        readerIntelligenceAndExportCoordinator.clearSearchCache()
        ambientSoundscapePlayer.onReaderVisibilityChanged(false)
        flushReadingProgressImmediately()
        stopTts()
        ambienceEngine.stop()
        stopActiveSession()
        cancelSleepTimer()

        pdfReflowManager.onCleared()
        epubFormatManager.onCleared(context)

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
        super.onCleared()
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

        val visualProg = (navigator as? VisualNavigator)?.currentLocator?.value?.locations?.progression ?: 0.0

        return readerIntelligenceAndExportCoordinator.generateXRayReport(
            chapterTitle = title,
            epubBlocks = epubBlocks,
            cachedDocumentBlocks = cachedDoc?.displayBlocks,
            reflowItems = pdfReflowManager.reflowState.value.items,
            currentParagraphIndex = currentParagraphIndex,
            ttsActiveBlockIndex = pdfReflowManager.ttsActiveBlockIndex.value,
            isReflowMode = isReflowMode.value,
            visualProgression = visualProg
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
