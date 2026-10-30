package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReflowItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelCharacterizationTest {

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

    // =========================================================================
    // flushReadingProgressImmediately
    // =========================================================================

    @Test
    fun `F1 reflow flush reading progress with loc position and totalPages`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
                val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 4, progression = 0.4, totalProgression = 0.4)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        fixture.reflowStateFlow.value = fixture.reflowStateFlow.value.copy(totalPages = 10)

        val slotBookId = slot<String>()
        val slotProgress = slot<Float>()
        val slotFallbackJson = mutableListOf<String?>()
        val slotFallbackPos = mutableListOf<Int?>()

        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = capture(slotBookId),
                chapterIndex = any(),
                progress = capture(slotProgress),
                currentLocator = any(),
                fallbackLocatorJson = captureNullable(slotFallbackJson),
                fallbackHref = any(),
                fallbackPosition = captureNullable(slotFallbackPos)
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertEquals("test_book_id", slotBookId.captured)
        assertEquals(0.4f, slotProgress.captured, 0.001f)
        assertEquals(4, slotFallbackPos.lastOrNull())
        assertEquals(loc.toJSON().toString(), slotFallbackJson.lastOrNull().takeIf { it?.isNotEmpty() == true })
    }

    @Test
    fun `F1 reflow flush reading progress fallback to first reflow element page when loc position is null`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = null, progression = null, totalProgression = null)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val reflowItem = ReflowItem.Paragraph(content = "Hello", pageNumber = 7, id = "p1")
        fixture.reflowElementsFlow.value = listOf(reflowItem)
        fixture.reflowStateFlow.value = fixture.reflowStateFlow.value.copy(totalPages = 14)

        val slotFallbackPos = mutableListOf<Int?>()
        val slotProgress = slot<Float>()

        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = capture(slotProgress),
                currentLocator = any(),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = captureNullable(slotFallbackPos)
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertEquals(7, slotFallbackPos.lastOrNull())
        assertEquals(7f / 14f, slotProgress.captured, 0.001f)
    }

    @Test
    fun `F2 PDF original flush reading progress updates lastKnownReflowPage and calculates progress`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 5, progression = null, totalProgression = null)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val slotProgress = slot<Float>()
        val slotFallbackPos = mutableListOf<Int?>()

        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = capture(slotProgress),
                currentLocator = any(),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = captureNullable(slotFallbackPos)
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertEquals(5, slotFallbackPos.lastOrNull())
        assertEquals(5f / 20f, slotProgress.captured, 0.001f)
    }

    @Test
    fun `F3 EPUB flush reading progress triggers saveProgress and then flushImmediately`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 3, progression = 0.5, totalProgression = 0.25)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val slotFallbackPos = mutableListOf<Int?>()
        val slotProgress = slot<Float>()

        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = capture(slotProgress),
                currentLocator = any(),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = captureNullable(slotFallbackPos)
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        // Verify saveProgressDebounced called (from internal saveProgress)
        verify {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = "test_book_id",
                progress = any(),
                locator = loc,
                fallbackPosition = any(),
                debounceMs = any(),
                force = any()
            )
        }

        assertEquals(3, slotFallbackPos.lastOrNull())
        assertEquals(0.25f, slotProgress.captured, 0.001f)
    }

    // =========================================================================
    // resumeTts
    // =========================================================================

    @Test
    fun testR1PdfOriginalPausedPlaysReflowTts() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 3)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        fixture.isReflowModeFlow.value = false
        fixture.reflowTtsStateFlow.value = TtsState.PAUSED
        fixture.reflowCurrentTtsPageFlow.value = 3

        coEvery {
            fixture.pdfReflowManager.playReflowTts(
                scope = any(),
                speed = any(),
                pitch = isNull(),
                languageCode = isNull(),
                voiceName = isNull(),
                startIndex = any()
            )
        } returns Unit

        vm.resumeTts()

        coVerify {
            fixture.pdfReflowManager.playReflowTts(
                scope = any(),
                speed = any(),
                pitch = isNull(),
                languageCode = isNull(),
                voiceName = isNull(),
                startIndex = any()
            )
        }
    }

    @Test
    fun `R2 reflow or PDF pauseTts then onReflowScroll then resumeTts honors user scroll`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val item0 = ReflowItem.Paragraph(content = "Line 0", pageNumber = 1, id = "0")
        val item1 = ReflowItem.Paragraph(content = "Line 1", pageNumber = 1, id = "1")
        val item2 = ReflowItem.Paragraph(content = "Line 2", pageNumber = 1, id = "2")
        fixture.reflowElementsFlow.value = listOf(item0, item1, item2)

        fixture.reflowTtsActiveBlockIndexFlow.value = 0
        vm.pauseTts()

        // User scrolled to index 2
        vm.onReflowScroll(firstVisibleItemIndex = 2, isScrolling = false)

        val slotStartIndex = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(
                scope = any(),
                speed = any(),
                pitch = any(),
                languageCode = any(),
                voiceName = any(),
                startIndex = capture(slotStartIndex)
            )
        } returns Unit

        vm.resumeTts()

        assertEquals(2, slotStartIndex.captured)
    }

    @Test
    fun `R3 EPUB resumeTts when PAUSED with same href and prog diff within tolerance calls epubFormatManager resumeTts`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val sharedUrl = mockk<org.readium.r2.shared.util.Url>(relaxed = true)
        every { sharedUrl.toString() } returns "chap1.xhtml"
        val loc = fixture.createMockLocator(hrefStr = "chap1.xhtml", progression = 0.102)
        every { loc.href } returns sharedUrl
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val ttsLoc = fixture.createMockLocator(hrefStr = "chap1.xhtml", progression = 0.100)
        every { ttsLoc.href } returns sharedUrl
        fixture.epubCurrentTtsLocatorFlow.value = ttsLoc
        fixture.epubTtsStateFlow.value = TtsState.PAUSED

        vm.resumeTts()

        verify { fixture.epubFormatManager.resumeTts() }
    }

    @Test
    fun `R3 EPUB resumeTts when PAUSED with different href calls playTts with visualLoc`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(hrefStr = "chap2.xhtml", progression = 0.0)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val ttsLoc = fixture.createMockLocator(hrefStr = "chap1.xhtml", progression = 0.5)
        fixture.epubCurrentTtsLocatorFlow.value = ttsLoc
        fixture.epubTtsStateFlow.value = TtsState.PAUSED

        vm.resumeTts()

        verify { fixture.epubFormatManager.playTts(any(), loc, any(), any(), any()) }
    }

    // =========================================================================
    // onReflowScroll (hot path)
    // =========================================================================

    @Test
    fun `S1 isScrolling true does not trigger TTS active index update nor saveProgressDebounced`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val item = ReflowItem.Paragraph(content = "Para", pageNumber = 2, id = "p1")
        fixture.reflowElementsFlow.value = listOf(item)
        fixture.reflowStateFlow.value = fixture.reflowStateFlow.value.copy(totalPages = 5)

        vm.onReflowScroll(firstVisibleItemIndex = 0, isScrolling = true)

        verify(exactly = 0) { fixture.pdfReflowManager.setTtsActiveBlockIndex(any()) }
        verify(exactly = 0) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                any(), any(), any(), any(), any(), any(), any()
            )
        }
        verify(exactly = 0) { fixture.readingSessionManager.addPageRead(any()) }
        assertEquals(2f / 5f, vm.globalProgress.value, 0.001f)
    }

    @Test
    fun `S2 idx changed with ttsPausedAtIndex sets userScrolledSinceTtsPause`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val item0 = ReflowItem.Paragraph(content = "0", pageNumber = 1, id = "0")
        val item1 = ReflowItem.Paragraph(content = "1", pageNumber = 1, id = "1")
        fixture.reflowElementsFlow.value = listOf(item0, item1)

        fixture.reflowTtsActiveBlockIndexFlow.value = 0
        vm.pauseTts()

        // Unchanged index: scroll to 0 again
        vm.onReflowScroll(firstVisibleItemIndex = 0, isScrolling = false)

        val slotStartIndex1 = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(
                scope = any(), speed = any(), pitch = any(), languageCode = any(), voiceName = any(),
                startIndex = capture(slotStartIndex1)
            )
        } returns Unit

        vm.resumeTts()
        // Should resume at paused index 0
        assertEquals(0, slotStartIndex1.captured)

        // Now pause again, and scroll to index 1 (changed)
        fixture.reflowTtsActiveBlockIndexFlow.value = 0
        vm.pauseTts()
        vm.onReflowScroll(firstVisibleItemIndex = 1, isScrolling = false)

        val slotStartIndex2 = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(
                scope = any(), speed = any(), pitch = any(), languageCode = any(), voiceName = any(),
                startIndex = capture(slotStartIndex2)
            )
        } returns Unit

        vm.resumeTts()
        // Should resume at scrolled index 1
        assertEquals(1, slotStartIndex2.captured)
    }

    @Test
    fun `S3 isScrolling false TTS not running sets active block index`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val item = ReflowItem.Paragraph(content = "Para", pageNumber = 1, id = "p1")
        fixture.reflowElementsFlow.value = listOf(item)
        every { fixture.pdfReflowManager.getIsReflowTtsRunning() } returns false

        vm.onReflowScroll(firstVisibleItemIndex = 0, isScrolling = false)

        verify { fixture.pdfReflowManager.setTtsActiveBlockIndex(0) }
    }

    @Test
    fun `S4 isScrolling false page greater than 0 records page read once and saves progress`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val item = ReflowItem.Paragraph(content = "Para", pageNumber = 3, id = "p3")
        fixture.reflowElementsFlow.value = listOf(item)
        fixture.reflowStateFlow.value = fixture.reflowStateFlow.value.copy(totalPages = 10)

        vm.onReflowScroll(firstVisibleItemIndex = 0, isScrolling = false)

        verify(exactly = 1) { fixture.readingSessionManager.addPageRead("pdf_reflow#3") }
        coVerify(exactly = 1) { fixture.calculateReadingStatsUseCase.recordPageRead("pdf_reflow#3", isAlreadyRead = false) }
        verify(exactly = 1) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = "test_book_id",
                progress = 0.3f,
                locator = null,
                fallbackPosition = 3,
                debounceMs = any(),
                force = true
            )
        }

        // Call again on same page - addPageRead should not be called again
        vm.onReflowScroll(firstVisibleItemIndex = 0, isScrolling = false)
        verify(exactly = 1) { fixture.readingSessionManager.addPageRead("pdf_reflow#3") }
    }

    @Test
    fun `S5 idx out of elements indices causes no progress or page side effects`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        fixture.reflowElementsFlow.value = emptyList()

        vm.onReflowScroll(firstVisibleItemIndex = 99, isScrolling = false)

        verify(exactly = 0) { fixture.readingSessionManager.addPageRead(any()) }
        verify(exactly = 0) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `S6 totalPages is 0 leaves globalProgress unchanged`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val item = ReflowItem.Paragraph(content = "Para", pageNumber = 2, id = "p2")
        fixture.reflowElementsFlow.value = listOf(item)
        fixture.reflowStateFlow.value = fixture.reflowStateFlow.value.copy(totalPages = 0)

        val initialProg = vm.globalProgress.value
        vm.onReflowScroll(firstVisibleItemIndex = 0, isScrolling = false)

        assertEquals(initialProg, vm.globalProgress.value, 0.0001f)
    }

    // =========================================================================
    // onLocatorChanged
    // =========================================================================

    @Test
    fun `L1 PDF original sets lastKnownReflowPage and saves progress debounced without EPUB serialize`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        val loc = fixture.createMockLocator(position = 5)

        vm.onLocatorChanged(loc)

        verify {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = "test_book_id",
                progress = 5f / 20f,
                locator = loc,
                fallbackPosition = 5,
                debounceMs = any(),
                force = any()
            )
        }
        verify(exactly = 0) { fixture.saveReadingProgressUseCase.serializeLocator(any()) }
    }

    @Test
    fun `L2 PDF reflow mode does not save progress on onLocatorChanged`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 0)
        // Set lastReflowScrollIndex so lastKnownReflowPage is 0
        vm.onReflowScroll(firstVisibleItemIndex = 999, isScrolling = false)
        val loc = fixture.createMockLocator(position = 3)

        vm.onLocatorChanged(loc)

        verify(exactly = 0) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `L3 EPUB duplicate locator only executes side effects once`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val loc = fixture.createMockLocator(hrefStr = "chap1.xhtml", position = 2, totalProgression = 0.2)

        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"href\":\"chap1.xhtml\"}"

        vm.onLocatorChanged(loc)
        vm.onLocatorChanged(loc) // duplicate invocation

        verify(exactly = 1) { fixture.saveReadingProgressUseCase.setLastSavedLocatorJson("{\"href\":\"chap1.xhtml\"}") }
        verify(exactly = 1) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = "test_book_id",
                progress = any(),
                locator = loc,
                fallbackPosition = any(),
                debounceMs = any(),
                force = any()
            )
        }
    }

    @Test
    fun `L4 saveProgress effects in MANUAL mode increments words and records page read once`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        every { fixture.readingSessionManager.currentSessionMode } returns ReadingMode.MANUAL
        every { fixture.calculateReadingStatsUseCase.calculateWordsDelta(any(), any(), any()) } returns 50L

        val loc = fixture.createMockLocator(hrefStr = "chap1.xhtml", position = 2, totalProgression = 0.3)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"json\":\"1\"}"

        vm.onLocatorChanged(loc)

        verify { fixture.readingSessionManager.incrementWords(50L) }
        verify(exactly = 1) { fixture.readingSessionManager.addPageRead("chap1.xhtml#2") }
        coVerify(exactly = 1) { fixture.calculateReadingStatsUseCase.recordPageRead("chap1.xhtml#2", isAlreadyRead = false) }
        verify { fixture.readingSessionManager.recordActivity(scope = any(), bookId = any(), currentLocator = any()) }
    }

    // =========================================================================
    // Extra pins (X1, X2)
    // =========================================================================

    @Test
    fun `X1 goToNextChapter passes visual page and persists to lastKnownReflowPage`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 8)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val slotPage = slot<Int>()
        every {
            fixture.chapterNavigationCoordinator.goToNextChapter(
                scope = any(),
                bookId = any(),
                publication = any(),
                navigator = any(),
                isPdfBook = any(),
                isReflowMode = any(),
                currentPdfPage = capture(slotPage),
                ttsIsPlaying = any(),
                resourceIndexCache = any(),
                onJumpToReflowPage = any(),
                onPlayPdfPageTts = any(),
                onPlayTts = any(),
                onSetLastTtsLocator = any()
            )
        } returns Unit

        vm.goToNextChapter(nav)

        assertEquals(8, slotPage.captured)

        // Verify side effect: now flush with a null navigator, lastKnownReflowPage should be 8
        vm.navigator = null
        val slotFlushPos = mutableListOf<Int?>()
        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = any(),
                currentLocator = any(),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = captureNullable(slotFlushPos)
            )
        } returns Unit

        vm.flushReadingProgressImmediately()
        assertEquals(8, slotFlushPos.lastOrNull())
    }

    @Test
    fun `X2 onCleared executes tear down in verified order`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)

        val onClearedMethod = ReaderViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)

        verifyOrder {
            fixture.saveReadingProgressUseCase.flushImmediately(any(), any(), any(), any(), any(), any(), any(), any())
            fixture.epubFormatManager.stopTts()
            fixture.readingSessionManager.stopActiveSession(scope = any(), bookId = any(), currentLocator = any())
            fixture.pdfReflowManager.onCleared()
            fixture.epubFormatManager.onCleared(any())
            fixture.chapterNavigationCoordinator.clear()
        }
    }
}
