package com.example.woodsreader.ui.reader

import android.app.Application
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.core.managers.PdfReflowManager
import com.example.woodsreader.core.managers.ReadingProgressManager
import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.core.pipeline.PdfBookViewSynchronizer
import com.example.woodsreader.core.system.WoodsKeepAliveManager
import com.example.woodsreader.core.tts.SleepTimerPreset
import com.example.woodsreader.core.tts.SmartSleepTimerManager
import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.TtsRuleEntity
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.data.model.ReflowState
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.domain.reader.CalculateReadingStatsUseCase
import com.example.woodsreader.domain.reader.LoadBookFastUseCase
import com.example.woodsreader.domain.reader.LoadBookResult
import com.example.woodsreader.domain.reader.LoadedBookSessionData
import com.example.woodsreader.domain.reader.ManageReaderAnnotationsUseCase
import com.example.woodsreader.domain.reader.ParsedChapterData
import com.example.woodsreader.domain.reader.ReaderChapterNavigationCoordinator
import com.example.woodsreader.domain.reader.ReaderIntelligenceAndExportCoordinator
import com.example.woodsreader.domain.reader.ReaderVoiceAndAmbienceController
import com.example.woodsreader.domain.reader.SaveReadingProgressUseCase
import com.example.woodsreader.service.CoverExtractor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

class ReaderViewModelFixture {

    val app: Application = mockk(relaxed = true)
    val savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("bookId" to "test_book_id"))
    val bookRepo: BookRepository = mockk(relaxed = true)
    val userPrefsRepo: UserPreferencesRepository = mockk(relaxed = true)
    val loadBookFastUseCase: LoadBookFastUseCase = mockk(relaxed = true)
    val smartSleep: SmartSleepTimerManager = mockk(relaxed = true)
    val ttsManager: TtsManager = mockk(relaxed = true)
    val pdfReflowManager: PdfReflowManager = mockk(relaxed = true) {
        io.mockk.coEvery { playReflowTts(any(), any(), any(), any(), any(), any()) } returns Unit
    }
    val epubFormatManager: EpubFormatManager = mockk(relaxed = true)
    val readingSessionManager: ReadingSessionManager = mockk(relaxed = true)
    val pdfBookViewSynchronizer: PdfBookViewSynchronizer = mockk(relaxed = true)
    val coverExtractor: CoverExtractor = mockk(relaxed = true)
    val coordinator: ReaderIntelligenceAndExportCoordinator = mockk(relaxed = true)
    val voiceAndAmbienceController: ReaderVoiceAndAmbienceController = mockk(relaxed = true)
    val proLicenseManager: ProLicenseManager = mockk(relaxed = true)
    val woodsKeepAliveManager: WoodsKeepAliveManager = mockk(relaxed = true)
    val saveReadingProgressUseCase: SaveReadingProgressUseCase = mockk(relaxed = true)
    val calculateReadingStatsUseCase: CalculateReadingStatsUseCase = mockk(relaxed = true)
    val manageReaderAnnotationsUseCase: ManageReaderAnnotationsUseCase = mockk(relaxed = true)
    val realLruCache = LruCache<Int, ParsedChapterData>(5)
    val chapterNavigationCoordinator: ReaderChapterNavigationCoordinator = mockk(relaxed = true) {
        every { chapterLruCache } returns realLruCache
    }

    // Real MutableStateFlows for managers
    val userPreferencesFlow = MutableStateFlow(UserPreferences())
    val ttsRulesFlow = MutableStateFlow<List<TtsRuleEntity>>(emptyList())
    val bookmarksFlow = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val highlightsFlow = MutableStateFlow<List<HighlightEntity>>(emptyList())

    val isReflowModeFlow = MutableStateFlow(false)
    val reflowStateFlow = MutableStateFlow(ReflowState())
    val reflowElementsFlow = MutableStateFlow<List<ReflowItem>>(emptyList())
    val reflowTtsStateFlow = MutableStateFlow(TtsState.IDLE)
    val reflowTtsActiveBlockIndexFlow = MutableStateFlow(0)
    val reflowCurrentTtsPageFlow = MutableStateFlow(0)
    val reflowCurrentTtsSentenceFlow = MutableStateFlow("")
    val reflowIsLoadingMoreFlow = MutableStateFlow(false)

    val epubTtsStateFlow = MutableStateFlow(TtsState.IDLE)
    val epubCurrentTtsLocatorFlow = MutableStateFlow<Locator?>(null)

    val ttsCurrentNarrationUnitIndexFlow = MutableStateFlow(0)
    val ttsIsLoadingModelFlow = MutableStateFlow(false)
    val smartSleepActivePresetFlow = MutableStateFlow(SleepTimerPreset.OFF)

    val sessionPagesReadFlow = MutableStateFlow<Set<String>>(emptySet())
    val sessionWordsProcessedFlow = MutableStateFlow(0L)

    init {
        every { userPrefsRepo.userPreferencesFlow } returns userPreferencesFlow
        every { bookRepo.getTtsRules() } returns ttsRulesFlow
        every { manageReaderAnnotationsUseCase.getBookmarks(any()) } returns bookmarksFlow
        every { manageReaderAnnotationsUseCase.getHighlights(any()) } returns highlightsFlow

        every { smartSleep.activePreset } returns smartSleepActivePresetFlow
        every { ttsManager.smartSleepTimerManager } returns smartSleep
        every { ttsManager.currentNarrationUnitIndex } returns ttsCurrentNarrationUnitIndexFlow
        every { ttsManager.isLoadingModel } returns ttsIsLoadingModelFlow

        // playReflowTts's default `startIndex` reads the private field _ttsActiveBlockIndex, which is
        // null on a mockk instance (NPE before the call is recorded). Initialize it by reflection.
        PdfReflowManager::class.java.getDeclaredField("_ttsActiveBlockIndex").apply {
            isAccessible = true
            set(pdfReflowManager, MutableStateFlow(-1))
        }

        every { pdfReflowManager.isReflowMode } returns isReflowModeFlow
        every { pdfReflowManager.reflowState } returns reflowStateFlow
        every { pdfReflowManager.reflowElements } returns reflowElementsFlow
        every { pdfReflowManager.ttsState } returns reflowTtsStateFlow
        every { pdfReflowManager.ttsActiveBlockIndex } returns reflowTtsActiveBlockIndexFlow
        every { pdfReflowManager.currentTtsPage } returns reflowCurrentTtsPageFlow
        every { pdfReflowManager.currentTtsSentence } returns reflowCurrentTtsSentenceFlow
        every { pdfReflowManager.isLoadingMoreReflow } returns reflowIsLoadingMoreFlow

        every { epubFormatManager.ttsState } returns epubTtsStateFlow
        every { epubFormatManager.currentTtsLocator } returns epubCurrentTtsLocatorFlow

        every { readingSessionManager.pagesRead } returns sessionPagesReadFlow
        every { readingSessionManager.wordsProcessed } returns sessionWordsProcessedFlow
        every { readingSessionManager.addPageRead(any()) } answers {
            val page = firstArg<String>()
            sessionPagesReadFlow.value = sessionPagesReadFlow.value + page
        }

        every { saveReadingProgressUseCase.serializeLocator(any()) } answers {
            firstArg<Locator?>()?.toJSON()?.toString() ?: ""
        }
    }

    fun createMockLocator(
        hrefStr: String = "chapter1.html",
        position: Int? = 1,
        progression: Double? = 0.25,
        totalProgression: Double? = 0.1
    ): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val url = mockk<Url>(relaxed = true)
        every { url.toString() } returns hrefStr
        every { locator.href } returns url

        val locations = mockk<Locator.Locations>(relaxed = true)
        every { locations.position } returns position
        every { locations.progression } returns progression
        every { locations.totalProgression } returns totalProgression
        every { locator.locations } returns locations

        val json = org.json.JSONObject().apply {
            put("href", hrefStr)
            position?.let { put("position", it) }
            progression?.let { put("progression", it) }
            totalProgression?.let { put("totalProgression", it) }
        }
        every { locator.toJSON() } returns json
        return locator
    }

    fun createViewModel(
        customSavedStateHandle: SavedStateHandle? = null,
        customApp: Application? = null,
        customBookRepo: BookRepository? = null,
        customUserPrefsRepo: UserPreferencesRepository? = null,
        customLoadBookFastUseCase: LoadBookFastUseCase? = null,
        customTtsManager: TtsManager? = null,
        customPdfReflowManager: PdfReflowManager? = null,
        customEpubFormatManager: EpubFormatManager? = null,
        customReadingSessionManager: ReadingSessionManager? = null,
        customPdfBookViewSynchronizer: PdfBookViewSynchronizer? = null,
        customCoverExtractor: CoverExtractor? = null,
        customCoordinator: ReaderIntelligenceAndExportCoordinator? = null,
        customVoiceAndAmbienceController: ReaderVoiceAndAmbienceController? = null,
        customProLicenseManager: ProLicenseManager? = null,
        customWoodsKeepAliveManager: WoodsKeepAliveManager? = null,
        customSaveReadingProgressUseCase: SaveReadingProgressUseCase? = null,
        customCalculateReadingStatsUseCase: CalculateReadingStatsUseCase? = null,
        customManageReaderAnnotationsUseCase: ManageReaderAnnotationsUseCase? = null,
        customChapterNavigationCoordinator: ReaderChapterNavigationCoordinator? = null
    ): ReaderViewModel {
        return ReaderViewModel(
            context = customApp ?: app,
            savedStateHandle = customSavedStateHandle ?: savedStateHandle,
            bookRepository = customBookRepo ?: bookRepo,
            userPreferencesRepository = customUserPrefsRepo ?: userPrefsRepo,
            loadBookFastUseCase = customLoadBookFastUseCase ?: loadBookFastUseCase,
            ttsManager = customTtsManager ?: ttsManager,
            pdfReflowManager = customPdfReflowManager ?: pdfReflowManager,
            epubFormatManager = customEpubFormatManager ?: epubFormatManager,
            readingSessionManager = customReadingSessionManager ?: readingSessionManager,
            pdfBookViewSynchronizer = customPdfBookViewSynchronizer ?: pdfBookViewSynchronizer,
            coverExtractor = customCoverExtractor ?: coverExtractor,
            readerIntelligenceAndExportCoordinator = customCoordinator ?: coordinator,
            voiceAndAmbienceController = customVoiceAndAmbienceController ?: voiceAndAmbienceController,
            proLicenseManager = customProLicenseManager ?: proLicenseManager,
            woodsKeepAliveManager = customWoodsKeepAliveManager ?: woodsKeepAliveManager,
            saveReadingProgressUseCase = customSaveReadingProgressUseCase ?: saveReadingProgressUseCase,
            calculateReadingStatsUseCase = customCalculateReadingStatsUseCase ?: calculateReadingStatsUseCase,
            manageReaderAnnotationsUseCase = customManageReaderAnnotationsUseCase ?: manageReaderAnnotationsUseCase,
            chapterNavigationCoordinator = customChapterNavigationCoordinator ?: chapterNavigationCoordinator
        )
    }

    fun loadedVm(
        format: BookFormat = BookFormat.EPUB,
        reflow: Boolean = false,
        totalPositions: Int = 100,
        initialPage: Int = 1,
        readingOrderHrefs: List<String> = listOf("chapter1.html", "chapter2.html"),
        bookId: String = "test_book_id"
    ): ReaderViewModel {
        val book = BookEntity(
            id = bookId,
            filePath = if (format == BookFormat.PDF) "/books/sample.pdf" else "/books/sample.epub",
            title = "Test Book",
            format = format,
            fileHash = "test_hash"
        )
        val readingOrderLinks = readingOrderHrefs.map { hrefStr ->
            val link = mockk<Link>(relaxed = true)
            val linkUrl = mockk<Url>(relaxed = true)
            every { linkUrl.toString() } returns hrefStr
            val hrefObj = mockk<org.readium.r2.shared.publication.Href>(relaxed = true)
            every { hrefObj.toString() } returns hrefStr
            every { link.href } returns hrefObj
            every { link.title } returns hrefStr
            link
        }
        val publication = mockk<Publication>(relaxed = true) {
            every { readingOrder } returns readingOrderLinks
        }
        val uri = mockk<Uri>(relaxed = true) {
            every { path } returns book.filePath
        }
        val sessionData = LoadedBookSessionData(
            book = book,
            publication = publication,
            uri = uri,
            resourceIndexCache = readingOrderHrefs.mapIndexed { idx, h -> h to idx }.toMap(),
            positionsByResource = emptyList(),
            totalPositions = totalPositions,
            initialLocator = if (format == BookFormat.EPUB) {
                createMockLocator(hrefStr = "chapter1.html", position = 1, progression = 0.0, totalProgression = 0.0)
            } else null,
            initialPdfPage = initialPage,
            flatTableOfContents = emptyList()
        )

        coEvery { loadBookFastUseCase.getBookEntity(bookId) } returns book
        coEvery { loadBookFastUseCase(book) } returns LoadBookResult.Success(sessionData)

        isReflowModeFlow.value = reflow

        val handle = SavedStateHandle(mapOf("bookId" to bookId))
        val vm = createViewModel(customSavedStateHandle = handle)
        return vm
    }
}
