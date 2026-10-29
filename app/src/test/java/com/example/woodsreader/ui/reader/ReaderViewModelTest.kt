
package com.example.woodsreader.ui.reader

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.core.managers.PdfReflowManager
import com.example.woodsreader.core.managers.ReadingProgressManager
import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.service.CoverExtractor
import com.example.woodsreader.core.pipeline.PdfBookViewSynchronizer
import com.example.woodsreader.core.search.FastInBookSearchHit
import com.example.woodsreader.core.search.FastSearchProgress
import com.example.woodsreader.core.share.QuoteVideoClipExporter
import com.example.woodsreader.core.system.WoodsKeepAliveManager
import com.example.woodsreader.core.tts.AudioExportState
import com.example.woodsreader.core.tts.SleepTimerPreset
import com.example.woodsreader.core.tts.SmartSleepTimerManager
import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.domain.reader.CalculateReadingStatsUseCase
import com.example.woodsreader.domain.reader.LoadBookFastUseCase
import com.example.woodsreader.domain.reader.ManageReaderAnnotationsUseCase
import com.example.woodsreader.domain.reader.ReaderChapterNavigationCoordinator
import com.example.woodsreader.domain.reader.ReaderIntelligenceAndExportCoordinator
import com.example.woodsreader.domain.reader.ReaderVoiceAndAmbienceController
import com.example.woodsreader.domain.reader.SaveReadingProgressUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Link

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        coordinator: ReaderIntelligenceAndExportCoordinator = mockk(relaxed = true)
    ): ReaderViewModel {
        val app: Application = mockk(relaxed = true)
        val bookRepo: BookRepository = mockk(relaxed = true) {
            every { getTtsRules() } returns MutableStateFlow(emptyList())
        }
        val userPrefsRepo: UserPreferencesRepository = mockk(relaxed = true) {
            every { userPreferencesFlow } returns MutableStateFlow(UserPreferences())
        }
        val loadBookFastUseCase: LoadBookFastUseCase = mockk(relaxed = true)
        val smartSleep: SmartSleepTimerManager = mockk(relaxed = true) {
            every { activePreset } returns MutableStateFlow(SleepTimerPreset.OFF)
        }
        val ttsManager: TtsManager = mockk(relaxed = true) {
            every { smartSleepTimerManager } returns smartSleep
        }
        val pdfReflowManager: PdfReflowManager = mockk(relaxed = true) {
            every { isReflowMode } returns MutableStateFlow(false)
            every { currentTtsSentence } returns MutableStateFlow("")
            every { currentTtsPage } returns MutableStateFlow(0)
            every { ttsState } returns MutableStateFlow(TtsState.IDLE)
            every { reflowState } returns MutableStateFlow(com.example.woodsreader.data.model.ReflowState())
            every { isLoadingMoreReflow } returns MutableStateFlow(false)
        }
        val epubFormatManager: EpubFormatManager = mockk(relaxed = true) {
            every { ttsState } returns MutableStateFlow(TtsState.IDLE)
        }
        val readingSessionManager: ReadingSessionManager = mockk(relaxed = true)
        val pdfBookViewSynchronizer: PdfBookViewSynchronizer = mockk(relaxed = true)
        val coverExtractor: CoverExtractor = mockk(relaxed = true)
        val voiceAndAmbienceController: ReaderVoiceAndAmbienceController = mockk(relaxed = true)
        val proLicenseManager: ProLicenseManager = mockk(relaxed = true)
        val woodsKeepAliveManager: WoodsKeepAliveManager = mockk(relaxed = true)
        val quoteVideoClipExporter: QuoteVideoClipExporter = mockk(relaxed = true)
        val saveReadingProgressUseCase: SaveReadingProgressUseCase = mockk(relaxed = true)
        val calculateReadingStatsUseCase: CalculateReadingStatsUseCase = mockk(relaxed = true)
        val manageReaderAnnotationsUseCase: ManageReaderAnnotationsUseCase = mockk(relaxed = true) {
            every { getBookmarks(any()) } returns MutableStateFlow(emptyList())
        }
        val chapterNavigationCoordinator: ReaderChapterNavigationCoordinator = mockk(relaxed = true)

        return ReaderViewModel(
            context = app,
            savedStateHandle = savedStateHandle,
            bookRepository = bookRepo,
            userPreferencesRepository = userPrefsRepo,
            loadBookFastUseCase = loadBookFastUseCase,
            ttsManager = ttsManager,
            pdfReflowManager = pdfReflowManager,
            epubFormatManager = epubFormatManager,
            readingSessionManager = readingSessionManager,
            pdfBookViewSynchronizer = pdfBookViewSynchronizer,
            coverExtractor = coverExtractor,
            readerIntelligenceAndExportCoordinator = coordinator,
            voiceAndAmbienceController = voiceAndAmbienceController,
            proLicenseManager = proLicenseManager,
            woodsKeepAliveManager = woodsKeepAliveManager,
            quoteVideoClipExporter = quoteVideoClipExporter,
            saveReadingProgressUseCase = saveReadingProgressUseCase,
            calculateReadingStatsUseCase = calculateReadingStatsUseCase,
            manageReaderAnnotationsUseCase = manageReaderAnnotationsUseCase,
            chapterNavigationCoordinator = chapterNavigationCoordinator
        )
    }

    @Test
    fun `test empty bookId emits ReaderUiState Error`() = runTest(testDispatcher) {
        val viewModel = createViewModel(savedStateHandle = SavedStateHandle())
        assertEquals(
            ReaderUiState.Error("ID de libro no válido o no proporcionado"),
            viewModel.uiState.value
        )
    }

    @Test
    fun `test public StateFlows and methods reflect ReaderIntelligenceAndExportCoordinator`() = runTest(testDispatcher) {
        val audioStateFlow = MutableStateFlow<AudioExportState>(AudioExportState.Idle)
        val searchHitsFlow = MutableStateFlow<List<FastInBookSearchHit>>(emptyList())
        val searchProgressFlow = MutableStateFlow<FastSearchProgress?>(null)

        val coordinator = mockk<ReaderIntelligenceAndExportCoordinator>(relaxed = true) {
            every { audioExportState } returns audioStateFlow
            every { inBookSearchResults } returns searchHitsFlow
            every { inBookSearchProgress } returns searchProgressFlow
        }

        val viewModel = createViewModel(coordinator = coordinator)

        // 1. Verificar audioExportState
        assertEquals(AudioExportState.Idle, viewModel.audioExportState.value)
        audioStateFlow.value = AudioExportState.Completed("cap1.m4a", 2048L, hasLyrics = true)
        assertEquals(AudioExportState.Completed("cap1.m4a", 2048L, hasLyrics = true), viewModel.audioExportState.value)

        // 2. Delegaciones de audio export
        viewModel.resetAudioExportState()
        verify(exactly = 1) { coordinator.resetAudioExportState() }

        viewModel.cancelAudioExport()
        verify(exactly = 1) { coordinator.cancelAudioExport() }

        // 3. Verificar inBookSearchResults e inBookSearchProgress
        assertEquals(emptyList<FastInBookSearchHit>(), viewModel.inBookSearchResults.value)
        assertNull(viewModel.inBookSearchProgress.value)

        val mockHit = mockk<FastInBookSearchHit>()
        searchHitsFlow.value = listOf(mockHit)
        assertEquals(listOf(mockHit), viewModel.inBookSearchResults.value)

        val mockProgress = mockk<FastSearchProgress>()
        searchProgressFlow.value = mockProgress
        assertEquals(mockProgress, viewModel.inBookSearchProgress.value)

        // 4. Delegación de cancelación de búsqueda
        viewModel.cancelInBookSearch()
        verify(exactly = 1) { coordinator.cancelInBookSearch() }
    }

    @Test
    fun `test findNextWordRange with standard sentence`() {
        val manager = EpubFormatManager()
        val text = "El veloz murciélago hindú"
        
        // From start
        val firstWord = manager.findNextWordRange(text, null)
        assertNotNull(firstWord)
        assertEquals("El", text.substring(firstWord!!))

        // Next word
        val secondWord = manager.findNextWordRange(text, firstWord)
        assertNotNull(secondWord)
        assertEquals("veloz", text.substring(secondWord!!))

        // Third word
        val thirdWord = manager.findNextWordRange(text, secondWord)
        assertNotNull(thirdWord)
        assertEquals("murciélago", text.substring(thirdWord!!))
    }

    @Test
    fun `test findPrevWordRange with standard sentence`() {
        val manager = EpubFormatManager()
        val text = "El veloz murciélago hindú"

        val lastWordRange = 20..24 // "hindú"
        val prevWord = manager.findPrevWordRange(text, lastWordRange)
        assertNotNull(prevWord)
        assertEquals("murciélago", text.substring(prevWord!!))

        val prevWord2 = manager.findPrevWordRange(text, prevWord)
        assertNotNull(prevWord2)
        assertEquals("veloz", text.substring(prevWord2!!))
    }

    @Test
    fun `test findNextWordRange with punctuation and trailing spaces`() {
        val manager = EpubFormatManager()
        val text = "¡Hola, mundo! ¿Cómo estás?"

        val firstWord = manager.findNextWordRange(text, null)
        assertNotNull(firstWord)
        assertEquals("Hola", text.substring(firstWord!!))

        val secondWord = manager.findNextWordRange(text, firstWord)
        assertNotNull(secondWord)
        assertEquals("mundo", text.substring(secondWord!!))

        val thirdWord = manager.findNextWordRange(text, secondWord)
        assertNotNull(thirdWord)
        assertEquals("Cómo", text.substring(thirdWord!!))

        val fourthWord = manager.findNextWordRange(text, thirdWord)
        assertNotNull(fourthWord)
        assertEquals("estás", text.substring(fourthWord!!))

        // At end of text, returns null
        val noMore = manager.findNextWordRange(text, fourthWord)
        assertNull(noMore)
    }

    @Test
    fun `test word range with empty and blank strings`() {
        val manager = EpubFormatManager()
        assertNull(manager.findNextWordRange("", null))
        assertNull(manager.findNextWordRange("   ... !!! ", null))
        assertNull(manager.findPrevWordRange("", null))
        assertNull(manager.findPrevWordRange("   ... !!! ", null))
    }

    @Test
    fun `test ttsState enum values integrity`() {
        val states = TtsState.values()
        assertTrue(states.contains(TtsState.IDLE))
        assertTrue(states.contains(TtsState.PLAYING))
        assertTrue(states.contains(TtsState.PAUSED))
        assertTrue(states.contains(TtsState.STOPPED))
    }

    @Test
    fun `test scrollMode enum values and defaults`() {
        val modes = com.example.woodsreader.model.ScrollMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(com.example.woodsreader.model.ScrollMode.PAGINATED))
        assertTrue(modes.contains(com.example.woodsreader.model.ScrollMode.CONTINUOUS_VERTICAL))
        assertTrue(modes.contains(com.example.woodsreader.model.ScrollMode.CONTINUOUS_HORIZONTAL))

        val defaultPrefs = com.example.woodsreader.data.datastore.UserPreferences()
        assertEquals(com.example.woodsreader.model.ScrollMode.PAGINATED, defaultPrefs.scrollMode)
        assertTrue(defaultPrefs.pageTurnAnimation)
        org.junit.Assert.assertFalse(defaultPrefs.invertedTapZones)
    }

    @Test
    fun `test toEpubPreferences maps scrollMode correctly`() {
        val paginatedPrefs = com.example.woodsreader.data.datastore.UserPreferences(
            scrollMode = com.example.woodsreader.model.ScrollMode.PAGINATED
        )
        val epubPaginated = paginatedPrefs.toEpubPreferences()
        assertEquals(true, epubPaginated.scroll)

        val verticalPrefs = com.example.woodsreader.data.datastore.UserPreferences(
            scrollMode = com.example.woodsreader.model.ScrollMode.CONTINUOUS_VERTICAL
        )
        val epubVertical = verticalPrefs.toEpubPreferences()
        assertEquals(true, epubVertical.scroll)

        val horizontalPrefs = com.example.woodsreader.data.datastore.UserPreferences(
            scrollMode = com.example.woodsreader.model.ScrollMode.CONTINUOUS_HORIZONTAL
        )
        val epubHorizontal = horizontalPrefs.toEpubPreferences()
        assertEquals(true, epubHorizontal.scroll)
    }

    @Test
    fun `test MasterMenuOptionType contains SCROLL_MODE`() {
        val types = MasterMenuOptionType.values()
        assertTrue(types.contains(MasterMenuOptionType.SCROLL_MODE))
        assertTrue(types.contains(MasterMenuOptionType.AUTO_SCROLL))
    }

    @Test
    fun `test AutoScrollTeleprompterState default values`() {
        val state = AutoScrollTeleprompterState()
        assertEquals(false, state.isActive)
        assertEquals(220, state.wpm)
        assertEquals(false, state.isManuallyPaused)
    }

    @Test
    fun `test pageTurnStyle enum values and defaults`() {
        val styles = com.example.woodsreader.model.PageTurnStyle.values()
        assertEquals(3, styles.size)
        assertTrue(styles.contains(com.example.woodsreader.model.PageTurnStyle.BOOK_FLIP))
        assertTrue(styles.contains(com.example.woodsreader.model.PageTurnStyle.SLIDE))
        assertTrue(styles.contains(com.example.woodsreader.model.PageTurnStyle.NONE))

        val defaultPrefs = com.example.woodsreader.data.datastore.UserPreferences()
        assertEquals(com.example.woodsreader.model.PageTurnStyle.BOOK_FLIP, defaultPrefs.pageTurnStyle)
    }

    @Test
    fun `test ReaderViewState and ReaderState pdfBookView properties`() {
        val viewState = ReaderViewState()
        org.junit.Assert.assertFalse(viewState.isPdfBookViewActive)
        assertEquals(0, viewState.pdfBookViewBlockIndex)

        val updatedState = viewState.copy(isPdfBookViewActive = true, pdfBookViewBlockIndex = 42)
        assertTrue(updatedState.isPdfBookViewActive)
        assertEquals(42, updatedState.pdfBookViewBlockIndex)

        val readerState = ReaderState()
        org.junit.Assert.assertFalse(readerState.isPdfBookViewActive)
        assertEquals(0, readerState.pdfBookViewBlockIndex)
    }

    @Test
    fun `test TtsRuleEntity creation for exact and regex patterns`() {
        val exactRule = com.example.woodsreader.data.database.TtsRuleEntity(
            pattern = "patrocinador",
            replacement = "",
            isRegex = false,
            isEnabled = true
        )
        assertEquals("patrocinador", exactRule.pattern)
        assertEquals("", exactRule.replacement)
        org.junit.Assert.assertFalse(exactRule.isRegex)
        assertTrue(exactRule.isEnabled)

        val regexRule = com.example.woodsreader.data.database.TtsRuleEntity(
            pattern = """\[\d+\]""",
            replacement = "",
            isRegex = true,
            isEnabled = true
        )
        assertEquals("""\[\d+\]""", regexRule.pattern)
        assertTrue(regexRule.isRegex)
    }

    private fun mockTocLink(hrefStr: String, childrenList: List<Link> = emptyList()): Link {
        val link = mockk<Link>(relaxed = true)
        val href = mockk<org.readium.r2.shared.publication.Href>(relaxed = true)
        every { href.toString() } returns hrefStr
        every { link.href } returns href
        every { link.children } returns childrenList
        return link
    }

    @Test
    fun `test resolvePdfTargetPageFromToc navigation for first, intermediate and last pages`() {
        val toc = listOf(
            mockTocLink("#page=1"),
            mockTocLink("#page=10"),
            mockTocLink("#page=25"),
            mockTocLink(
                "#page=50",
                childrenList = listOf(
                    mockTocLink("#page=75")
                )
            )
        )

        // 1. Caso Primera Página (página 1 o dentro del capítulo 1)
        // Desde la primera página (1), el siguiente capítulo es la página 10
        assertEquals(10, ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 1))
        // En la primera página (1), no hay capítulo anterior
        assertNull(ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 1))
        // Desde una página intermedia dentro del capítulo 1 (ej: pág 5)
        assertEquals(10, ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 5))
        assertEquals(1, ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 5))

        // 2. Caso Página Intermedia (capítulo 2 / 3)
        // En el inicio de capítulo 2 (página 10)
        assertEquals(25, ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 10))
        assertEquals(1, ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 10))
        // En medio de capítulo 2 (ej: página 18)
        assertEquals(25, ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 18))
        assertEquals(10, ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 18))
        // En el inicio de capítulo 3 (página 25)
        assertEquals(50, ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 25))
        assertEquals(10, ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 25))

        // 3. Caso Última Página / Último Capítulo
        // En el subcapítulo final (página 75)
        assertNull(ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 75))
        assertEquals(50, ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 75))
        // Más allá de la última página registrada en el TOC (ej: página 100)
        assertNull(ReaderViewModel.resolveNextPdfPageFromToc(toc, currentPage = 100))
        assertEquals(75, ReaderViewModel.resolvePreviousPdfPageFromToc(toc, currentPage = 100))

        // 4. Caso TOC vacío o formatos alternativos de página
        assertNull(ReaderViewModel.resolveNextPdfPageFromToc(emptyList(), currentPage = 1))
        assertNull(ReaderViewModel.resolvePreviousPdfPageFromToc(emptyList(), currentPage = 1))

        val altToc = listOf(
            mockTocLink("doc.pdf#page=5"),
            mockTocLink("/page-15"),
            mockTocLink("/25")
        )
        assertEquals(15, ReaderViewModel.resolveNextPdfPageFromToc(altToc, currentPage = 5))
        assertEquals(25, ReaderViewModel.resolveNextPdfPageFromToc(altToc, currentPage = 15))
        assertEquals(15, ReaderViewModel.resolvePreviousPdfPageFromToc(altToc, currentPage = 25))
    }
}

