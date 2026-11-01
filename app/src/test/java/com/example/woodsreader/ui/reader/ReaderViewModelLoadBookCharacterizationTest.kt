package com.example.woodsreader.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.domain.reader.LoadBookResult
import com.example.woodsreader.domain.reader.LoadedBookSessionData
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import java.io.File

class ReaderViewModelLoadBookCharacterizationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fixture: ReaderViewModelFixture

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture = ReaderViewModelFixture()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun invokeLoadBook(vm: ReaderViewModel) {
        val method = ReaderViewModel::class.java.getDeclaredMethod("loadBook")
        method.isAccessible = true
        method.invoke(vm)
    }

    private fun getProgressTracker(vm: ReaderViewModel): ReaderProgressTracker {
        val field = ReaderViewModel::class.java.getDeclaredField("progressTracker")
        field.isAccessible = true
        return field.get(vm) as ReaderProgressTracker
    }

    private fun setupBook(
        format: BookFormat = BookFormat.EPUB,
        filePath: String = "/books/sample.epub",
        initialPage: Int? = 1,
        fallbackPosition: Int? = null,
        progressPercent: Float = 0f,
        initialLocator: Locator? = null,
        uriPath: String? = filePath,
        coverCachePath: String? = null
    ): Pair<BookEntity, Publication> {
        val book = BookEntity(
            id = "test_book_id",
            filePath = filePath,
            title = "Test Book",
            format = format,
            fallbackLocatorPosition = fallbackPosition,
            readingProgressPercent = progressPercent,
            coverCachePath = coverCachePath,
            fileHash = "test_hash",
            author = "Test Author"
        )
        val link = mockk<Link>(relaxed = true)
        val linkUrl = mockk<Url>(relaxed = true)
        every { linkUrl.toString() } returns "chapter1.html"
        val hrefObj = mockk<org.readium.r2.shared.publication.Href>(relaxed = true)
        every { hrefObj.toString() } returns "chapter1.html"
        every { link.href } returns hrefObj
        every { link.title } returns "chapter1.html"

        val pub = mockk<Publication>(relaxed = true) {
            every { readingOrder } returns listOf(link)
        }
        val uri = mockk<Uri>(relaxed = true) {
            every { path } returns uriPath
        }

        val sessionData = LoadedBookSessionData(
            book = book,
            publication = pub,
            uri = uri,
            resourceIndexCache = mapOf("chapter1.html" to 0),
            positionsByResource = emptyList(),
            totalPositions = 100,
            initialLocator = initialLocator,
            initialPdfPage = initialPage,
            flatTableOfContents = emptyList()
        )

        coEvery { fixture.loadBookFastUseCase.getBookEntity("test_book_id") } returns book
        coEvery { fixture.loadBookFastUseCase(book) } returns LoadBookResult.Success(sessionData)

        return Pair(book, pub)
    }

    // ── B1..B5: Edge Cases & Transitions ────────────────────────────────────

    @Test
    fun testB1_loadBook_blankBookId_emitsErrorAndNeverLoadsEntity() = runTest {
        val handle = SavedStateHandle(mapOf("bookId" to "   "))
        val vm = fixture.createViewModel(customSavedStateHandle = handle)

        val state = vm.uiState.value
        assertTrue("uiState debe ser Error cuando bookId está en blanco", state is ReaderUiState.Error)
        assertEquals("ID de libro no válido o no proporcionado", (state as ReaderUiState.Error).message)
        coVerify(exactly = 0) { fixture.loadBookFastUseCase.getBookEntity(any()) }
    }

    @Test
    fun testB2_loadBook_bookNotFound_emitsErrorAndNeverLoadsFast() = runTest {
        coEvery { fixture.loadBookFastUseCase.getBookEntity("test_book_id") } returns null
        val vm = fixture.createViewModel()

        val state = vm.uiState.value
        assertTrue("uiState debe ser Error cuando no se encuentra el libro", state is ReaderUiState.Error)
        assertEquals("Libro no encontrado en la base de datos", (state as ReaderUiState.Error).message)
        coVerify(exactly = 0) { fixture.loadBookFastUseCase.invoke(any<BookEntity>()) }
    }

    @Test
    fun testB3_loadBook_reloadWithPreviousPublication_closesPreviousAndStopsTtsBeforeGetBookEntity() = runTest {
        val (_, newPub) = setupBook(format = BookFormat.EPUB)
        val vm = fixture.createViewModel()

        val prevPub = mockk<Publication>(relaxed = true)
        vm.publication = prevPub

        invokeLoadBook(vm)

        coVerifyOrder {
            fixture.pdfReflowManager.stopReflowTts()
            fixture.epubFormatManager.stopTts()
            prevPub.close()
            fixture.loadBookFastUseCase.getBookEntity("test_book_id")
        }
        assertSame(newPub, vm.publication)
    }

    @Test
    fun testB4_loadBook_resultError_emitsLoadingInitialLoadingErrorSequence() = runTest(testDispatcher) {
        val (book, _) = setupBook(format = BookFormat.EPUB)
        coEvery { fixture.loadBookFastUseCase.getBookEntity("test_book_id") } coAnswers {
            kotlinx.coroutines.delay(10)
            book
        }
        coEvery { fixture.loadBookFastUseCase(book) } coAnswers {
            kotlinx.coroutines.delay(10)
            LoadBookResult.Error("boom", RuntimeException("cause"))
        }

        val vm = fixture.createViewModel()
        val states = mutableListOf<ReaderUiState>()
        val job = backgroundScope.launch(testDispatcher) {
            vm.uiState.collect { states.add(it) }
        }

        invokeLoadBook(vm)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val relevant = states.takeLast(3)
        assertEquals(3, relevant.size)
        assertTrue("Primera emisión debe ser Loading", relevant[0] is ReaderUiState.Loading)
        assertTrue("Segunda emisión debe ser InitialLoading", relevant[1] is ReaderUiState.InitialLoading)
        assertTrue("Tercera emisión debe ser Error", relevant[2] is ReaderUiState.Error)
        assertEquals("boom", (relevant[2] as ReaderUiState.Error).message)
        assertNull("La publicación debe quedar nula", vm.publication)
    }

    @Test
    fun testB5_loadBook_useCaseThrows_emitsErrorWithExceptionMessage() = runTest {
        val (book, _) = setupBook(format = BookFormat.EPUB)
        coEvery { fixture.loadBookFastUseCase(book) } throws RuntimeException("x")

        val vm = fixture.createViewModel()
        val state = vm.uiState.value
        assertTrue("uiState debe ser Error", state is ReaderUiState.Error)
        assertEquals("Error al cargar libro: x", (state as ReaderUiState.Error).message)
    }

    // ── B6..B8: EPUB Success & Progress ─────────────────────────────────────

    @Test
    fun testB6_loadBook_epubSuccess_emitsLoadingInitialLoadingSuccess_andInitsNavigatorAndTts() = runTest(testDispatcher) {
        val loc = fixture.createMockLocator(hrefStr = "chapter1.html", position = 1, progression = 0.0, totalProgression = 0.0)
        val (book, pub) = setupBook(format = BookFormat.EPUB, initialLocator = loc)
        coEvery { fixture.loadBookFastUseCase.getBookEntity("test_book_id") } coAnswers {
            kotlinx.coroutines.delay(10)
            book
        }
        val uri = mockk<Uri>(relaxed = true) {
            every { path } returns "/books/sample.epub"
        }
        val sessionData = LoadedBookSessionData(
            book = book,
            publication = pub,
            uri = uri,
            resourceIndexCache = mapOf("chapter1.html" to 0),
            positionsByResource = emptyList(),
            totalPositions = 100,
            initialLocator = loc,
            initialPdfPage = null,
            flatTableOfContents = emptyList()
        )
        coEvery { fixture.loadBookFastUseCase(book) } coAnswers {
            kotlinx.coroutines.delay(10)
            LoadBookResult.Success(sessionData)
        }

        val vm = fixture.createViewModel()
        val states = mutableListOf<ReaderUiState>()
        val job = backgroundScope.launch(testDispatcher) {
            vm.uiState.collect { states.add(it) }
        }

        invokeLoadBook(vm)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val relevant = states.takeLast(3)
        assertEquals(3, relevant.size)
        assertTrue("Primera emisión debe ser Loading", relevant[0] is ReaderUiState.Loading)
        assertTrue("Segunda emisión debe ser InitialLoading", relevant[1] is ReaderUiState.InitialLoading)
        assertTrue("Tercera emisión debe ser Success", relevant[2] is ReaderUiState.Success)

        val success = relevant[2] as ReaderUiState.Success
        assertEquals(loc, success.initialLocator)
        assertEquals(loc.toJSON().toString(), success.initialLocatorJson)
        assertSame(pub, vm.publication)

        verify { fixture.pdfReflowManager.setReflowMode(enabled = false, scope = any()) }
        coVerify { fixture.epubFormatManager.initNavigator(pub, loc, any(), null, "test_book_id") }
        verify { fixture.ttsManager.setCurrentBookId("test_book_id") }
    }

    @Test
    fun testB7_loadBook_initialProgress_clampsProgressAndStartsSession() = runTest {
        val loc = fixture.createMockLocator(hrefStr = "chapter1.html", position = 1)
        
        // Test -0.5f clamped to 0.0f
        setupBook(format = BookFormat.EPUB, progressPercent = -0.5f, initialLocator = loc)
        val vmNegative = fixture.createViewModel()
        assertEquals(0f, vmNegative.globalProgress.value)

        // Test 0.4f preserved
        setupBook(format = BookFormat.EPUB, progressPercent = 0.4f, initialLocator = loc)
        val vmNormal = fixture.createViewModel()
        assertEquals(0.4f, vmNormal.globalProgress.value)

        // Test 1.7f clamped to 1.0f
        setupBook(format = BookFormat.EPUB, progressPercent = 1.7f, initialLocator = loc)
        val vmExcess = fixture.createViewModel()
        assertEquals(1.0f, vmExcess.globalProgress.value)

        verify { fixture.readingSessionManager.startNewSession(any(), "test_book_id", ReadingMode.MANUAL, loc) }
    }

    @Test
    fun testB8a_loadBook_lastRecordedProgressionSeed_fromInitialLocator() = runTest {
        val loc = fixture.createMockLocator(hrefStr = "chapter1.html", totalProgression = 0.2)
        setupBook(format = BookFormat.EPUB, progressPercent = 0.8f, initialLocator = loc)
        every { fixture.readingSessionManager.currentSessionMode } returns ReadingMode.MANUAL

        val vm = fixture.createViewModel()

        val nextLoc = fixture.createMockLocator(hrefStr = "chapter1.html", totalProgression = 0.35)
        vm.onLocatorChanged(nextLoc)

        verify {
            fixture.calculateReadingStatsUseCase.calculateWordsDelta(
                currentProgression = 0.35,
                lastRecordedProgression = 0.2,
                estimatedWordCount = any()
            )
        }
    }

    @Test
    fun testB8b_loadBook_lastRecordedProgressionSeed_fromReadingProgressPercentFallback() = runTest {
        setupBook(format = BookFormat.EPUB, progressPercent = 0.15f, initialLocator = null)
        every { fixture.readingSessionManager.currentSessionMode } returns ReadingMode.MANUAL

        val vm = fixture.createViewModel()

        val nextLoc = fixture.createMockLocator(hrefStr = "chapter1.html", totalProgression = 0.35)
        vm.onLocatorChanged(nextLoc)

        verify {
            fixture.calculateReadingStatsUseCase.calculateWordsDelta(
                currentProgression = 0.35,
                lastRecordedProgression = 0.15f.toDouble(),
                estimatedWordCount = any()
            )
        }
    }

    // ── B9..B11: PDF Path & Initialization ──────────────────────────────────

    @Test
    fun testB9a_loadBook_pdfPath_absoluteFilePathWins() = runTest {
        setupBook(
            format = BookFormat.PDF,
            filePath = "/storage/emulated/0/book.pdf",
            uriPath = "/resolved/path.pdf"
        )
        fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.initialize("/storage/emulated/0/book.pdf", "test_book_id", any())
        }
    }

    @Test
    fun testB9b_loadBook_pdfPath_relativeFilePathUsesUriPath() = runTest {
        setupBook(
            format = BookFormat.PDF,
            filePath = "content://media/book.pdf",
            uriPath = "/resolved/path.pdf"
        )
        fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.initialize("/resolved/path.pdf", "test_book_id", any())
        }
    }

    @Test
    fun testB9c_loadBook_pdfPath_nullUriPathFallsBackToFilePath() = runTest {
        setupBook(
            format = BookFormat.PDF,
            filePath = "relative.pdf",
            uriPath = null
        )
        fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.initialize("relative.pdf", "test_book_id", any())
        }
    }

    @Test
    fun testB10a_loadBook_pdfInitialPage_sessionDataWins() = runTest {
        setupBook(
            format = BookFormat.PDF,
            initialPage = 5,
            fallbackPosition = 10
        )
        val vm = fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.setReflowMode(enabled = true, scope = any(), initialPosition = 5)
        }
        assertEquals(5, getProgressTracker(vm).lastKnownReflowPage)
    }

    @Test
    fun testB10b_loadBook_pdfInitialPage_fallbackLocatorPositionWins() = runTest {
        setupBook(
            format = BookFormat.PDF,
            initialPage = null,
            fallbackPosition = 7
        )
        val vm = fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.setReflowMode(enabled = true, scope = any(), initialPosition = 7)
        }
        assertEquals(7, getProgressTracker(vm).lastKnownReflowPage)
    }

    @Test
    fun testB10c_loadBook_pdfInitialPage_zeroFallbackPositionDefaultsToOne() = runTest {
        setupBook(
            format = BookFormat.PDF,
            initialPage = null,
            fallbackPosition = 0
        )
        val vm = fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.setReflowMode(enabled = true, scope = any(), initialPosition = 1)
        }
        assertEquals(1, getProgressTracker(vm).lastKnownReflowPage)
    }

    @Test
    fun testB10d_loadBook_pdfInitialPage_negativeFallbackPositionDefaultsToOne() = runTest {
        setupBook(
            format = BookFormat.PDF,
            initialPage = null,
            fallbackPosition = -3
        )
        val vm = fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.setReflowMode(enabled = true, scope = any(), initialPosition = 1)
        }
        assertEquals(1, getProgressTracker(vm).lastKnownReflowPage)
    }

    @Test
    fun testB11_loadBook_pdfInitializeCallback_recordsActivity() = runTest {
        setupBook(format = BookFormat.PDF)
        val callbackSlot = slot<(Long) -> Unit>()

        fixture.createViewModel()

        verify {
            fixture.pdfReflowManager.initialize(any(), any(), capture(callbackSlot))
        }
        callbackSlot.captured.invoke(42L)

        verify {
            fixture.readingSessionManager.recordActivity(any(), eq("test_book_id"), any())
        }
    }

    // ── B12..B15: Phase 2 Hydration & Cover ──────────────────────────────────

    @Test
    fun testB12_loadBook_phase2_callsMarkOpenedAndUpdateLastReadBookId() = runTest {
        setupBook(format = BookFormat.EPUB)
        val vm = fixture.createViewModel()

        assertTrue(vm.uiState.value is ReaderUiState.Success)
        coVerify(timeout = 3000) { fixture.bookRepo.markOpened("test_book_id") }
        coVerify(timeout = 3000) { fixture.userPrefsRepo.updateLastReadBookId("test_book_id") }
    }

    @Test
    fun testB13a_loadBook_coverMissing_extractsAndUpdatesCoverPath() = runTest {
        setupBook(format = BookFormat.EPUB, coverCachePath = null)
        coEvery {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        } returns "/covers/new_cover.jpg"

        fixture.createViewModel()

        coVerify(timeout = 3000) {
            fixture.coverExtractor.extractCover(
                source = any(),
                format = BookFormat.EPUB,
                fileHash = "test_hash",
                title = "Test Book",
                author = "Test Author",
                publication = any()
            )
        }
        coVerify(timeout = 3000) {
            fixture.bookRepo.updateCoverPath("test_book_id", "/covers/new_cover.jpg")
        }
    }

    @Test
    fun testB13b_loadBook_coverPresent_doesNotExtractCover() = runTest {
        val tempFile = File.createTempFile("test_cover", ".jpg")
        tempFile.deleteOnExit()

        setupBook(format = BookFormat.EPUB, coverCachePath = tempFile.absolutePath)

        fixture.createViewModel()

        coVerify(timeout = 3000) { fixture.bookRepo.markOpened("test_book_id") }
        coVerify(exactly = 0) {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            fixture.bookRepo.updateCoverPath(any(), any())
        }
    }

    @Test
    fun testB13c_loadBook_coverExtractNull_doesNotUpdateCoverPath() = runTest {
        setupBook(format = BookFormat.EPUB, coverCachePath = null)
        coEvery {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        } returns null

        fixture.createViewModel()

        coVerify(timeout = 3000) {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            fixture.bookRepo.updateCoverPath(any(), any())
        }
    }

    @Test
    fun testB14_loadBook_phase2Failure_markOpenedThrows_staysSuccess() = runTest {
        setupBook(format = BookFormat.EPUB)
        coEvery { fixture.bookRepo.markOpened(any()) } throws RuntimeException("db error")

        val vm = fixture.createViewModel()

        coVerify(timeout = 3000) { fixture.bookRepo.markOpened("test_book_id") }
        assertTrue("uiState debe mantenerse en Success pese a fallo de fase 2", vm.uiState.value is ReaderUiState.Success)
        coVerify(exactly = 0) {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testB15_loadBook_phase2Failure_extractCoverThrows_staysSuccess() = runTest {
        setupBook(format = BookFormat.EPUB, coverCachePath = null)
        coEvery {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("extractor crash")

        val vm = fixture.createViewModel()

        coVerify(timeout = 3000) {
            fixture.coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            fixture.bookRepo.updateCoverPath(any(), any())
        }
        assertTrue("uiState debe mantenerse en Success", vm.uiState.value is ReaderUiState.Success)
    }
}
