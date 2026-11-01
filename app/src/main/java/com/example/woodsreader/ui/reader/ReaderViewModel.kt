package com.example.woodsreader.ui.reader
import android.app.Application
import com.example.woodsreader.core.pipeline.PdfBookViewSynchronizer
import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.domain.reader.SaveReadingProgressUseCase
import com.example.woodsreader.domain.reader.CalculateReadingStatsUseCase
import com.example.woodsreader.domain.reader.LoadBookFastUseCase
import com.example.woodsreader.domain.reader.LoadBookResult
import com.example.woodsreader.domain.reader.ManageReaderAnnotationsUseCase
import com.example.woodsreader.domain.reader.ReaderIntelligenceAndExportCoordinator
import com.example.woodsreader.domain.reader.ReaderChapterNavigationCoordinator
import com.example.woodsreader.domain.reader.ParsedChapterData
import android.content.Context

import android.util.Log
import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay

import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkEntity

import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.TtsRuleEntity
import com.example.woodsreader.data.datastore.UserPreferences

import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.model.HighlightColor

import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.repository.BookRepository

import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.core.tts.TtsPresetPack
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.EdgeVoiceOption
import com.example.woodsreader.domain.reader.ReaderVoiceAndAmbienceController
import com.example.woodsreader.core.search.FastInBookSearchHit
import com.example.woodsreader.core.search.FastSearchProgress
import com.example.woodsreader.core.managers.PdfReflowManager

import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.model.PageTurnStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import org.readium.r2.navigator.Navigator

import org.readium.r2.navigator.VisualNavigator



import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.json.JSONObject

import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.*

import android.util.LruCache
import com.example.woodsreader.core.xray.ChapterXRayReport
import com.example.woodsreader.core.license.ProLicenseManager

import com.example.woodsreader.core.system.WoodsKeepAliveManager
import com.example.woodsreader.service.CoverExtractor
import org.readium.r2.shared.publication.Link
import java.io.File

import javax.inject.Inject

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
    private val pdfBookViewSynchronizer: PdfBookViewSynchronizer,
    private val coverExtractor: CoverExtractor,
    private val readerIntelligenceAndExportCoordinator: ReaderIntelligenceAndExportCoordinator,
    private val voiceAndAmbienceController: ReaderVoiceAndAmbienceController,
    val proLicenseManager: ProLicenseManager,
    val woodsKeepAliveManager: WoodsKeepAliveManager,
    private val saveReadingProgressUseCase: SaveReadingProgressUseCase,
    private val calculateReadingStatsUseCase: CalculateReadingStatsUseCase,
    private val manageReaderAnnotationsUseCase: ManageReaderAnnotationsUseCase,
    private val chapterNavigationCoordinator: ReaderChapterNavigationCoordinator
) : ViewModel() {

    companion object {
        private const val TAG = "ReaderViewModel"

        private const val INACTIVITY_THRESHOLD = 5 * 60 * 1000L
        private const val MAX_CACHED_REFLOW_PAGES = 50
        private const val MAX_CACHED_CHAPTERS = 5

        /** Velocidad de lectura predeterminada en palabras por minuto (WPM) para estimaciones de progreso en TTS y fallback. */
        private const val DEFAULT_READING_WPM = 200

        fun extractPdfPagesFromToc(toc: List<Link>): List<Int> =
            ReaderChapterNavigationCoordinator.extractPdfPagesFromToc(toc)

        fun resolveNextPdfPageFromToc(toc: List<Link>, currentPage: Int): Int? =
            ReaderChapterNavigationCoordinator.resolveNextPdfPageFromToc(toc, currentPage)

        fun resolvePreviousPdfPageFromToc(toc: List<Link>, currentPage: Int): Int? =
            ReaderChapterNavigationCoordinator.resolvePreviousPdfPageFromToc(toc, currentPage)
    }

    private val chapterLruCache: LruCache<Int, ParsedChapterData>
        get() = chapterNavigationCoordinator.chapterLruCache

    val bookId: String = savedStateHandle.get<String>("bookId").orEmpty()
    var publication: Publication? = null

    private var navigatorLocatorJob: Job? = null
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

    private val _bookTimeRemaining = MutableStateFlow<String?>(null)
    val bookTimeRemaining = _bookTimeRemaining.asStateFlow()
    private val _chapterTimeRemaining = MutableStateFlow<String?>(null)
    val chapterTimeRemaining = _chapterTimeRemaining.asStateFlow()
    private var lastTtsSentence: String? = null

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
        val ttsActiveBlockIndex = pdfReflowManager.ttsActiveBlockIndex

    // ── Teleprompter / Auto-Scroll Controller ────────────────────────────────
    private val autoScroll = ReaderAutoScrollController {
        context.getSharedPreferences("woods_teleprompter_prefs", Context.MODE_PRIVATE)
    }
    val isAutoScrollActive: StateFlow<Boolean> = autoScroll.isActive
    val isAutoScrollManuallyPaused: StateFlow<Boolean> = autoScroll.isManuallyPaused
    val autoScrollWpm: StateFlow<Int> = autoScroll.wpm

    fun toggleAutoScroll() {
        if (autoScroll.toggle()) {
            hideControls()
            if (ttsState.value == TtsState.PLAYING) {
                pauseTts()
            }
        }
    }
    fun toggleAutoScrollPause() = autoScroll.togglePause()
    fun setAutoScrollManuallyPaused(paused: Boolean) = autoScroll.setManuallyPaused(paused)
    fun setAutoScrollWpm(wpm: Int) = autoScroll.setWpm(wpm)

    val currentTtsPage = pdfReflowManager.currentTtsPage
    val sleepTimerRemaining = voiceAndAmbienceController.sleepTimerRemaining
    val activeAmbiencePreset = voiceAndAmbienceController.activeAmbiencePreset
    fun toggleNextAmbience() {
        voiceAndAmbienceController.toggleNextAmbience()
    }

    fun installTtsPresetPack(pack: TtsPresetPack) = viewModelScope.launch {
        installTtsPresetPackWithResult(pack)
    }

    suspend fun installTtsPresetPackWithResult(pack: TtsPresetPack): Int {
        val addedRules = voiceAndAmbienceController.installTtsPresetPack(pack)
        epubFormatManager.invalidateCache()
        return addedRules
    }


    private val _chapterInfo = MutableStateFlow(ChapterInfo())
    val chapterInfo = _chapterInfo.asStateFlow()
    private val currentChapterTitle: StateFlow<String> = _chapterInfo
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
    private val annotations = ReaderAnnotationsController(manageReaderAnnotationsUseCase, bookId)
    val isCurrentPageBookmarked: StateFlow<Boolean> = annotations.isCurrentPageBookmarked

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
    val selectedSherpaVoice = voiceAndAmbienceController.selectedSherpaVoice

    fun isSherpaVoiceDownloaded(option: com.example.woodsreader.core.tts.SherpaVoiceOption): Boolean =
        voiceAndAmbienceController.isSherpaVoiceDownloaded(option)

    fun selectOrDownloadSherpaVoice(option: com.example.woodsreader.core.tts.SherpaVoiceOption) {
        voiceAndAmbienceController.selectOrDownloadSherpaVoice(viewModelScope, option)
    }

    fun deleteSherpaVoiceFromReader(option: com.example.woodsreader.core.tts.SherpaVoiceOption) {
        voiceAndAmbienceController.deleteSherpaVoiceFromReader(viewModelScope, option)
    }

    fun resetAudioExportState() {
        readerIntelligenceAndExportCoordinator.resetAudioExportState()
    }

    fun speakSnippet(text: String) {
        viewModelScope.launch {
            ttsManager.speak(text)
        }
    }

    val useEdgeNeuralTts = voiceAndAmbienceController.useEdgeNeuralTts
    val selectedEdgeVoice = voiceAndAmbienceController.selectedEdgeVoice

    fun setUseEdgeNeuralTts(enabled: Boolean) {
        voiceAndAmbienceController.setUseEdgeNeuralTts(enabled)
    }

    fun selectEdgeVoice(option: EdgeVoiceOption) {
        voiceAndAmbienceController.selectEdgeVoice(option)
    }

    fun cancelAudioExport() {
        readerIntelligenceAndExportCoordinator.cancelAudioExport()
    }

    fun exportAudioChapters(maxChaptersToExport: Int = 1) {
        val book = (_uiState.value as? ReaderUiState.Success)?.book ?: return
        val pub = publication ?: return
        readerIntelligenceAndExportCoordinator.exportAudio(
            scope = viewModelScope,
            book = book,
            publication = pub,
            isPdfBook = isPdfBook,
            isReflowMode = isReflowMode.value,
            startChapTitle = currentChapterTitle.value.ifBlank { "Capitulo" },
            maxChaptersToExport = maxChaptersToExport,
            speed = currentSpeed.value,
            coverFile = readerIntelligenceAndExportCoordinator.resolveCoverFile(book, context),
            currentTtsPage = pdfReflowManager.currentTtsPage.value,
            reflowItems = pdfReflowManager.reflowState.value.items,
            currentHref = (navigator as? org.readium.r2.navigator.VisualNavigator)?.currentLocator?.value?.href?.toString().orEmpty()
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

    fun exportCurrentChapterToAudio() = exportAudioChapters(1)

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
    private val sessionCoordinator = ReaderReadingSessionCoordinator(
        sessionManager = readingSessionManager, bookId = bookId, scope = viewModelScope,
        visibleLocator = { (navigator as? VisualNavigator)?.currentLocator?.value },
        ttsLocator = { _currentTtsLocator.value },
        isTtsPlaying = { ttsState.value == TtsState.PLAYING }
    )
    private val progressTracker = ReaderProgressTracker()

    private val statsCalculator = ReaderReadingStatsCalculator(
        statsUseCase = calculateReadingStatsUseCase,
        resolveChapterTitle = { pub, href, page -> loadBookFastUseCase.resolveChapterTitle(pub, href, page) }
    )
    private val ttsPlayback = ReaderTtsPlaybackCoordinator()

    // --- Init ------------------------------------------------------------------

    init {
        voiceAndAmbienceController.onReaderVisibilityChanged(true)
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
                    autoScroll.reset()
                }
            }
        }
        viewModelScope.launch {
            ttsManager.smartSleepTimerManager.activePreset.collect { preset ->
                voiceAndAmbienceController.handleSmartSleepTimerPreset(preset) { updateActiveTtsSpeedOnly(it) }
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
            voiceAndAmbienceController.onReaderVisibilityChanged(true)
            _uiState.value = ReaderUiState.Loading
            try {
                stopTts()
                // Liberar inmediatamente cualquier publicación previa
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
                    progressTracker.lastKnownReflowPage = initialPage
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

                progressTracker.lastRecordedProgression = initialLocator?.locations?.totalProgression
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

    private fun startNewSession(mode: ReadingMode, initialLocator: Locator? = null) {
        sessionCoordinator.start(mode, initialLocator)?.let { progressTracker.lastRecordedProgression = it }
        lastTtsSentence = null
    }
    private fun stopActiveSession() { sessionCoordinator.stop() }
    private fun switchSessionMode(newMode: ReadingMode) { sessionCoordinator.switchMode(newMode) }
    private fun recordActivity() { sessionCoordinator.recordActivity() }

    // --- UI controls -----------------------------------------------------------

    fun toggleControls() { _showControls.value = !_showControls.value }

    fun hideControls()   { _showControls.value = false }

    // --- Preferences -----------------------------------------------------------
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
    val sherpaDownloadState = voiceAndAmbienceController.sherpaDownloadState
    val showSherpaDownloadDialog = voiceAndAmbienceController.showSherpaDownloadDialog

    fun dismissSherpaDownloadDialog() {
        voiceAndAmbienceController.dismissSherpaDownloadDialog()
    }

    fun downloadSherpaModel() {
        voiceAndAmbienceController.downloadSherpaModel(viewModelScope)
    }

    fun updateUseSherpaTts(enabled: Boolean) = viewModelScope.launch {
        voiceAndAmbienceController.updateUseSherpaTts(viewModelScope, enabled)
    }

    fun updateTtsUiStyle(style: com.example.woodsreader.model.TtsUiStyle) = viewModelScope.launch { userPreferencesRepository.updateTtsUiStyle(style) }

    fun updateTtsHighlightStyle(style: com.example.woodsreader.model.TtsHighlightStyle) = viewModelScope.launch { userPreferencesRepository.updateTtsHighlightStyle(style) }

    // --- Bookmarks -------------------------------------------------------------

    fun deleteHighlight(highlight: HighlightEntity) = viewModelScope.launch {
        manageReaderAnnotationsUseCase.deleteHighlight(highlight)
    }

    fun addHighlight(locator: Locator, color: HighlightColor, text: String, note: String = "") {
        viewModelScope.launch {
            annotations.addHighlight(locator, color, text, note, _chapterInfo.value.title)
        }
    }

    fun addBookmark(locator: Locator?, label: String? = null) = viewModelScope.launch {
        annotations.addBookmark(
            navLocator = (navigator as? VisualNavigator)?.currentLocator?.value,
            locator = locator,
            label = label,
            bookmarks = bookmarks.value,
            isPdfOrReflow = isReflowMode.value || isPdfBook,
            lastKnownReflowPage = progressTracker.lastKnownReflowPage,
            chapterInfoTitle = _chapterInfo.value.title,
            reflowItems = pdfReflowManager.reflowState.value.items,
            globalProgress = _globalProgress.value
        )
    }

    fun deleteBookmark(bookmark: BookmarkEntity) = viewModelScope.launch {
        manageReaderAnnotationsUseCase.deleteBookmark(bookmark)
    }

    fun saveTtsRule(rule: TtsRuleEntity) = viewModelScope.launch {
        bookRepository.saveTtsRule(rule)
        epubFormatManager.invalidateCache()
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

    private fun updateReadingStats(locator: Locator, activeWpm: Int) {
        val update = statsCalculator.readingStats(
            locator = locator, activeWpm = activeWpm,
            totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L,
            totalPositions = totalPositions, positionsByResource = positionsByResource,
            resourceIndexCache = resourceIndexCache, publication = publication
        )
        _bookTimeRemaining.value = update.bookTimeRemaining
        update.chapterStats?.let {
            _chapterTimeRemaining.value = it.chapterTimeRemaining
            _globalProgress.value = it.globalProgress
        }
    }

    private fun onTtsLocatorChanged(locator: Locator) {
        if (_showEpubCoverPage.value) return
        annotations.currentLocator = locator
        val locJson = saveReadingProgressUseCase.serializeLocator(locator)
        progressTracker.lastSavedLocatorJson = locJson
        saveReadingProgressUseCase.setLastSavedLocatorJson(locJson)
        updateChapterMetadata(locator)
        updateReadingStats(locator, DEFAULT_READING_WPM)

        saveReadingProgressUseCase.saveTtsProgressDebounced(
            scope = viewModelScope,
            bookId = bookId,
            locator = locator
        )
    }

    fun onLocatorChanged(locator: Locator) {
        annotations.currentLocator = locator
        if (isPdfBook && !isReflowMode.value) {
            progressTracker.lastKnownReflowPage = locator.locations.position ?: 1
        }
        refreshCurrentBookmarkState()
        if (isPdfBook && !isReflowMode.value) {
            val page = locator.locations.position ?: 1
            progressTracker.lastKnownReflowPage = page
            val totalPages = progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, publication?.readingOrder?.size)
            _globalProgress.value = progressTracker.pageFraction(page, totalPages)

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
            if (locJson != progressTracker.lastSavedLocatorJson) {
                progressTracker.lastSavedLocatorJson = locJson
                saveReadingProgressUseCase.setLastSavedLocatorJson(locJson)
                saveProgress(locator)
            }
        }
    }

    private fun trackWordsRead(locator: Locator) {
        if (readingSessionManager.currentSessionMode == ReadingMode.MANUAL) {
            val currentProg = locator.locations.totalProgression ?: 0.0
            val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L
            val wordsRead = calculateReadingStatsUseCase.calculateWordsDelta(
                currentProgression = currentProg,
                lastRecordedProgression = progressTracker.lastRecordedProgression,
                estimatedWordCount = totalWords
            )
            if (wordsRead > 0) {
                readingSessionManager.incrementWords(wordsRead)
            }
            progressTracker.lastRecordedProgression = currentProg
        }
    }

    private fun trackPageRead(locator: Locator) {
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
    }

    private fun warmChapterCacheIfNeeded(locator: Locator) {
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
    }

    private fun saveProgress(locator: Locator) {
        trackWordsRead(locator)
        trackPageRead(locator)
        warmChapterCacheIfNeeded(locator)

        // Calculate WPM & Time remaining estimates
        val activeWpm = calculateReadingStatsUseCase.calculateActiveWpm(
            sessionStartTimeMs = readingSessionManager.sessionStartTime,
            wordsProcessed = readingSessionManager.wordsProcessed.value
        )
        updateReadingStats(locator, activeWpm)

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
        val target = ReaderNavigationResolvers.resolveSeekTarget(
            percent = percent,
            isReflowMode = isReflowMode.value,
            reflowTotalPages = pdfReflowManager.reflowState.value.totalPages,
            isPdfBook = isPdfBook,
            totalPositions = totalPositions,
            publication = publication,
            positionsByResource = positionsByResource,
            totalPdfPages = { progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, it) }
        )
        when (target) {
            is ReaderNavigationResolvers.SeekTarget.Reflow -> {
                progressTracker.lastKnownReflowPage = target.page
                jumpToReflowPage(target.page)
                _globalProgress.value = percent
            }
            is ReaderNavigationResolvers.SeekTarget.PdfOriginal -> {
                val nav = navigator as? VisualNavigator
                progressTracker.lastKnownReflowPage = target.page
                if (nav != null && target.locator != null) {
                    nav.go(target.locator, animated = false)
                }
                _globalProgress.value = percent
            }
            is ReaderNavigationResolvers.SeekTarget.EpubLink -> {
                val nav = navigator as? VisualNavigator ?: return
                if (target.locator != null) {
                    nav.go(target.locator, animated = false)
                }
                _globalProgress.value = percent
            }
            is ReaderNavigationResolvers.SeekTarget.EpubPosition -> {
                val nav = navigator as? VisualNavigator ?: return
                nav.go(target.locator, animated = false)
                _globalProgress.value = percent
            }
            ReaderNavigationResolvers.SeekTarget.None -> Unit
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

    fun goToNextChapter(navigator: Navigator? = null) {
        val pub = publication ?: return
        val effectiveNav = navigator ?: this.navigator
        val visualPosition = if (!isReflowMode.value) {
            (effectiveNav as? VisualNavigator)?.currentLocator?.value?.locations?.position
        } else null
        val pageResolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = isReflowMode.value,
            lastKnownReflowPage = progressTracker.lastKnownReflowPage,
            currentTtsPage = pdfReflowManager.currentTtsPage.value,
            visualPosition = visualPosition
        )
        pageResolution.newLastKnownReflowPage?.let { progressTracker.lastKnownReflowPage = it }
        chapterNavigationCoordinator.goToNextChapter(
            scope = viewModelScope,
            bookId = bookId,
            publication = pub,
            navigator = effectiveNav,
            isPdfBook = isPdfBook,
            isReflowMode = isReflowMode.value,
            currentPdfPage = pageResolution.pageToUse,
            ttsIsPlaying = ttsState.value == TtsState.PLAYING,
            resourceIndexCache = resourceIndexCache,
            onJumpToReflowPage = ::jumpToReflowPage,
            onPlayPdfPageTts = { targetPage -> getLocatorForPdfPage(targetPage)?.let(::playTts) },
            onPlayTts = ::playTts,
            onSetLastTtsLocator = { loc -> _lastTtsLocator.value = loc }
        )
    }

    private fun findMatchingResourceIndex(rawHref: String, order: List<Link>): Int {
        return chapterNavigationCoordinator.findMatchingResourceIndex(rawHref, order, resourceIndexCache)
    }

    fun goToPreviousChapter(navigator: Navigator? = null) {
        val pub = publication ?: return
        val nav = navigator ?: this.navigator
        val visualPosition = if (!isReflowMode.value) {
            (nav as? VisualNavigator)?.currentLocator?.value?.locations?.position
        } else null
        val pageResolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = isReflowMode.value,
            lastKnownReflowPage = progressTracker.lastKnownReflowPage,
            currentTtsPage = pdfReflowManager.currentTtsPage.value,
            visualPosition = visualPosition
        )
        pageResolution.newLastKnownReflowPage?.let { progressTracker.lastKnownReflowPage = it }
        chapterNavigationCoordinator.goToPreviousChapter(
            scope = viewModelScope,
            bookId = bookId,
            publication = pub,
            navigator = nav,
            isPdfBook = isPdfBook,
            isReflowMode = isReflowMode.value,
            currentPdfPage = pageResolution.pageToUse,
            ttsIsPlaying = ttsState.value == TtsState.PLAYING,
            resourceIndexCache = resourceIndexCache,
            onJumpToReflowPage = ::jumpToReflowPage,
            onPlayPdfPageTts = { targetPage -> getLocatorForPdfPage(targetPage)?.let(::playTts) },
            onPlayTts = ::playTts,
            onSetLastTtsLocator = { loc -> _lastTtsLocator.value = loc },
            onDelayedScrollRefresh = {
                (nav as? VisualNavigator)?.safePublicationView?.let { pubView ->
                    applyToAllWebViews(pubView) { wv ->
                        wv.scrollToBottom()
                    }
                }
            }
        )
    }

    private fun prefetchSurroundingChapters(currentIndex: Int) {
        chapterNavigationCoordinator.prefetchSurroundingChapters(
            scope = viewModelScope,
            bookId = bookId,
            publication = publication,
            isPdfBook = isPdfBook,
            currentIndex = currentIndex
        )
    }

    private fun advanceTtsToNextChapter() {
        val pub = publication ?: return
        val currentHref = epubFormatManager.currentTtsLocator.value?.href?.toString()
            ?: (navigator as? VisualNavigator)?.currentLocator?.value?.href?.toString()
            ?: _lastTtsLocator.value?.href?.toString()
            ?: _currentTtsLocator.value?.href?.toString()
            ?: ""
        chapterNavigationCoordinator.advanceTtsToNextChapter(
            scope = viewModelScope,
            bookId = bookId,
            publication = pub,
            navigator = navigator,
            isPdfBook = isPdfBook,
            isReflowMode = isReflowMode.value,
            currentTtsHref = currentHref,
            resourceIndexCache = resourceIndexCache,
            onNavigatePdfNext = { goToNextChapter(navigator) }
        )
    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ TTS ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬
    private val isPdfBook: Boolean
        get() = (uiState.value as? ReaderUiState.Success)?.book?.format?.name == "PDF"

    fun getLocatorForPdfPage(page: Int): Locator? {
        val pub = publication ?: return null
        return ReaderReflowResolvers.locatorForPdfPage(
            page = page,
            publication = pub,
            reflowTotalPages = pdfReflowManager.reflowState.value.totalPages,
            totalPositions = totalPositions,
            positionsByResource = positionsByResource
        )
    }

    private fun getFallbackLocator(): Locator? {
        val nav = navigator as? VisualNavigator
        val successState = _uiState.value as? ReaderUiState.Success
        return ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = nav?.currentLocator?.value,
            lastTtsLocator = _lastTtsLocator.value,
            currentTtsLocator = _currentTtsLocator.value,
            initialLocatorJson = successState?.initialLocatorJson,
            parseInitial = { initialJson ->
                try {
                    Locator.fromJSON(org.json.JSONObject(initialJson))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing initialLocatorJson", e)
                    null
                }
            },
            firstLinkLocator = {
                val pub = publication ?: successState?.publication
                val firstLink = pub?.readingOrder?.firstOrNull()
                if (firstLink != null) {
                    pub.locatorFromLink(firstLink)
                } else null
            }
        )
    }


    fun onPlay() {
        if (isReflowMode.value) {
            val elements = pdfReflowManager.reflowState.value.items
            val readableItems = ReaderReflowResolvers.readableItems(elements)
            val startIndex = ReaderReflowResolvers.readableIndex(elements, readableItems, lastReflowScrollIndex)

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

    private fun playTtsFromVisualPosition() {
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
        autoScroll.reset()
        _lastTtsLocator.value = null
        switchSessionMode(ReadingMode.TTS)
        if (isReflowMode.value || isPdfBook) {
            epubFormatManager.stopTts()
            pdfReflowManager.stopReflowTts()
            viewModelScope.launch {
                val targetIndex = ttsPlayback.resolveReflowPlayStartIndex(
                    locator = locator,
                    fallbackPage = progressTracker.lastKnownReflowPage,
                    items = { pdfReflowManager.reflowState.value.items },
                    loadPages = { pdfReflowManager.loadReflowPages(fromPage = it, isInitial = true) }
                )

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

    fun pauseTts() {
        if (isReflowMode.value || isPdfBook) {
            ttsPlayback.onPaused(pdfReflowManager.ttsActiveBlockIndex.value)
            pdfReflowManager.pauseReflowTts()
        } else {
            epubFormatManager.pauseTts()
        }

        switchSessionMode(ReadingMode.MANUAL)
    }

    fun stopTts() {
        android.util.Log.d("TTS_STOP", "ReaderViewModel.stopTts() invoked")
        _lastTtsLocator.value = null
        ttsPlayback.onStopped()

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
        autoScroll.reset()
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
            val readableItems = ReaderReflowResolvers.readableItems(elements)
            val visibleReadableIdx = ReaderReflowResolvers.readableIndex(elements, readableItems, lastReflowScrollIndex)

            val targetStartIndex = ttsPlayback.consumeReflowResumeIndex(
                visibleReadableIdx = visibleReadableIdx,
                readableCount = readableItems.size,
                activeBlockIndex = { pdfReflowManager.ttsActiveBlockIndex.value }
            )

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

                    // Si el usuario se ha desplazado visualmente a otra página o cambió de capítulo durante la pausa
                    if (ttsPlayback.shouldReplayFromVisual(visualLoc, ttsLocator)) {
                        playTts(visualLoc!!)
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
        ttsPlayback.onReflowScrolled(lastReflowScrollIndex, firstVisibleItemIndex)
        lastReflowScrollIndex = firstVisibleItemIndex

        val elements = pdfReflowManager.reflowElements.value
        val totalPages = pdfReflowManager.reflowState.value.totalPages

        // Solo actualizar el índice TTS pasivo cuando el scroll se detiene para evitar jank/congelamientos a 60-90fps
        if (!isScrolling && !pdfReflowManager.getIsReflowTtsRunning() && elements.isNotEmpty()) {
            val readableItems = ReaderReflowResolvers.readableItems(elements)
            val readableIdx = ReaderReflowResolvers.readableIndex(elements, readableItems, firstVisibleItemIndex)
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
                progressTracker.lastKnownReflowPage = pageNumber
                val locator = (navigator as? VisualNavigator)?.currentLocator?.value
                if (locator != null) {
                    annotations.currentLocator = locator.copy(
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
        if (annotations.currentLocator == null) return
        annotations.refresh(bookmarks.value, isReflowMode.value || isPdfBook, progressTracker.lastKnownReflowPage)
    }

    fun loadNextPage() {
        pdfReflowManager.loadNextPage(viewModelScope)
    }

    fun loadPreviousPage() {
        pdfReflowManager.loadPreviousPage(viewModelScope)
    }

    fun jumpToReflowPage(page: Int) {
        progressTracker.lastKnownReflowPage = page
        pdfReflowManager.jumpToPage(viewModelScope, page)
    }

    fun skipReflowTtsForward()  {
        viewModelScope.launch { pdfReflowManager.playReflowTts(viewModelScope, currentSpeed.value, startIndex = ttsActiveBlockIndex.value + 1) }

    }

    fun skipReflowTtsBackward() {
        viewModelScope.launch { pdfReflowManager.playReflowTts(viewModelScope, currentSpeed.value, startIndex = (ttsActiveBlockIndex.value - 1).coerceAtLeast(0)) }

    }

    // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ Sleep timer ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬

    fun startSleepTimer(minutes: Int) {
        voiceAndAmbienceController.startSleepTimer(
            minutes = minutes,
            currentSpeed = currentSpeed.value,
            scope = viewModelScope,
            onSpeedChange = { speed -> updateActiveTtsSpeedOnly(speed) },
            onTimerExpired = {
                stopTts()
                voiceAndAmbienceController.stopAmbience()
            }
        )
    }

    fun cancelSleepTimer() {
        voiceAndAmbienceController.cancelSleepTimer { speed -> updateActiveTtsSpeedOnly(speed) }
    }

    // --- Optimized sleep timer with minimal wake-ups ---------------------------

    private fun updateActiveTtsSpeedOnly(speed: Float) {
        _currentSpeed.value = speed
        pdfReflowManager.onSpeedChange(speed, viewModelScope)
        epubFormatManager.syncPreferences(speed, userPreferences.value.ttsLanguage, _currentPitch.value)
    }

    private fun updateChapterMetadata(locator: Locator) {
        _chapterInfo.value = statsCalculator.chapterInfo(
            locator = locator, publication = publication, positionsByResource = positionsByResource,
            pageFallback = if (isPdfBook) pdfReflowManager.currentTtsPage.value.takeIf { it > 0 } else null
        )
    }

    // --- Misc ------------------------------------------------------------------
    private var lastUserInteractionTime = 0L

    fun onUserInteraction()      { lastUserInteractionTime = System.currentTimeMillis() }

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
        val ttsIsActive = ttsState.value == TtsState.PLAYING || ttsState.value == TtsState.PAUSED
        val currentLoc = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = _currentTtsLocator.value,
            ttsIsActive = ttsIsActive,
            navLocator = nav?.currentLocator?.value,
            annotationsLocator = annotations.currentLocator,
            lastSavedLocatorJson = progressTracker.lastSavedLocatorJson,
            parseLocator = { saved ->
                runCatching { Locator.fromJSON(org.json.JSONObject(saved)) }.getOrNull()
            }
        )
        val chapterIdx = currentLoc?.let { locator ->
            val order = publication?.readingOrder.orEmpty()
            findMatchingResourceIndex(locator.href.toString(), order).takeIf { it >= 0 }
        } ?: getCurrentChapterIndex()
        val locJson = currentLoc?.toJSON()?.toString() ?: progressTracker.lastSavedLocatorJson ?: ""
        val href = currentLoc?.href?.toString() ?: ""
        val prog = currentLoc?.locations?.totalProgression?.toFloat()
            ?: currentLoc?.locations?.progression?.toFloat()
            ?: _globalProgress.value

        val plan = progressTracker.planFlush(
            currentLoc = currentLoc, prog = prog, isReflowMode = isReflowMode.value, isPdfBook = isPdfBook,
            reflowFirstElementPage = if (isReflowMode.value) pdfReflowManager.reflowElements.value.firstOrNull()?.pageNumber else null,
            reflowTotalPages = if (isReflowMode.value || isPdfBook) pdfReflowManager.reflowState.value.totalPages else 0,
            totalPositions = totalPositions,
            readingOrderSize = if (isPdfBook) publication?.readingOrder?.size else null,
            globalProgress = _globalProgress.value
        )
        plan.pageToRemember?.let { progressTracker.lastKnownReflowPage = it }
        if (plan.saveProgressFirst) saveProgress(currentLoc!!)
        saveReadingProgressUseCase.flushImmediately(
            bookId = bookId, chapterIndex = chapterIdx, progress = plan.progress, currentLocator = currentLoc,
            fallbackLocatorJson = locJson, fallbackHref = href, fallbackPosition = plan.fallbackPosition
        )
    }

    fun saveCurrentProgressBeforeExit() {
        autoScroll.deactivate()
        if (_showEpubCoverPage.value) return
        flushReadingProgressImmediately()
    }

    // ── Búsqueda En-Libro Streaming con Cero Bloqueo ─────────────────────────
    val inBookSearchResults: StateFlow<List<FastInBookSearchHit>> = readerIntelligenceAndExportCoordinator.inBookSearchResults
    val inBookSearchProgress: StateFlow<FastSearchProgress?> = readerIntelligenceAndExportCoordinator.inBookSearchProgress

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

    fun cancelInBookSearch() {
        readerIntelligenceAndExportCoordinator.cancelInBookSearch()
    }

    override fun onCleared() {
        readerIntelligenceAndExportCoordinator.clearSearchCache()
        voiceAndAmbienceController.onCleared { updateActiveTtsSpeedOnly(it) }
        flushReadingProgressImmediately()
        stopTts()
        stopActiveSession()

        pdfReflowManager.onCleared()
        epubFormatManager.onCleared(context)

        ttsManager.onQueueCompletedNaturally = null
        ttsManager.stop()
        ttsManager.setCurrentBookId(null)

        navigatorLocatorJob?.cancel()
        navigatorLocatorJob = null
        publication?.close()
        publication = null
        navigator = null
        positionsByResource = emptyList()
        resourceIndexCache = null
        chapterNavigationCoordinator.clear()
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
