package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.data.model.ReflowState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

class ReaderViewModelTtsCharacterizationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ReaderViewModelFixture()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- T1: onPlay en reflow: llama playReflowTts con startIndex de lastReflowScrollIndex y prefs ---
    @Test
    fun testT1_onPlay_reflowMode_playsTtsWithReadableIndexAndPreferences() = runTest(testDispatcher) {
        val prefs = fixture.userPreferencesFlow.value.copy(
            ttsSpeed = 1.25f,
            ttsPitch = 0.9f,
            ttsLanguage = "es",
            ttsVoiceName = "es-ES-Standard-A"
        )
        fixture.userPreferencesFlow.value = prefs
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        val job = backgroundScope.launch { vm.userPreferences.collect {} }

        val p0 = ReflowItem.PageNumber("1", pageNumber = 1)
        val p1 = ReflowItem.Paragraph("First paragraph", pageNumber = 1, id = "p1")
        val p2 = ReflowItem.Paragraph("Second paragraph", pageNumber = 1, id = "p2")
        val items = listOf(p0, p1, p2)
        fixture.reflowStateFlow.value = ReflowState(items = items, totalPages = 1)

        // Scroll to item 1 (p1, which is readable index 0)
        vm.onReflowScroll(firstVisibleItemIndex = 1, isScrolling = false)

        val slotStartIndex = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(
                scope = any(),
                speed = 1.25f,
                pitch = 0.9f,
                languageCode = "es",
                voiceName = "es-ES-Standard-A",
                startIndex = capture(slotStartIndex)
            )
        } returns Unit

        vm.onPlay()

        assertTrue("playReflowTts should be called", slotStartIndex.isCaptured)
        assertEquals(0, slotStartIndex.captured)
        job.cancel()
    }

    // --- T2: onPlay PDF original, no pausado o distinta página: va a playTts(locator) ---
    @Test
    fun testT2_onPlay_pdfOriginal_notPausedOrDifferentPage_delegatesToPlayTts() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 5)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        fixture.isReflowModeFlow.value = false
        // Case A: State is STOPPED -> must call playTts which stops reflow/epub and calls playReflowTts
        fixture.reflowTtsStateFlow.value = TtsState.STOPPED
        fixture.reflowCurrentTtsPageFlow.value = 5

        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any())
        } returns Unit

        vm.onPlay()

        coVerify { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any()) }

        // Case B: State is PAUSED but visualPage (5) != ttsPage (2)
        fixture.reflowTtsStateFlow.value = TtsState.PAUSED
        fixture.reflowCurrentTtsPageFlow.value = 2

        vm.onPlay()
        // Must also call playTts(loc)
        coVerify(atLeast = 2) { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any()) }
    }

    // --- T3: onPlay EPUB: va a playTtsFromVisualPosition (verifica epubFormatManager.playTts) ---
    @Test
    fun testT3_onPlay_epub_delegatesToPlayTtsFromVisualPosition() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(hrefStr = "chapter1.html", position = 1)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        vm.onPlay()

        verify { fixture.epubFormatManager.playTts(any(), loc, nav, any(), any()) }
    }

    // --- T4: playTtsFromVisualPosition: EpubNavigatorFragment (normal y exception) y VisualNavigator ---
    @Test
    fun testT4_playTtsFromVisualPosition_epubNavigatorFragment_and_fallback() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)

        // Subcase A: EpubNavigatorFragment with valid firstVisibleElementLocator
        val epubNav = mockk<EpubNavigatorFragment>(relaxed = true)
        val firstLoc = fixture.createMockLocator(hrefStr = "first.html", position = 1)
        val currentLoc = fixture.createMockLocator(hrefStr = "current.html", position = 2)
        coEvery { epubNav.firstVisibleElementLocator() } returns firstLoc
        every { epubNav.currentLocator } returns MutableStateFlow(currentLoc)
        vm.navigator = epubNav

        vm.onPlay()
        verify { fixture.epubFormatManager.playTts(any(), firstLoc, epubNav, any(), any()) }

        // Subcase B: EpubNavigatorFragment throwing exception -> falls back to currentLocator
        val epubNavThrowing = mockk<EpubNavigatorFragment>(relaxed = true)
        coEvery { epubNavThrowing.firstVisibleElementLocator() } throws RuntimeException("DOM not ready")
        every { epubNavThrowing.currentLocator } returns MutableStateFlow(currentLoc)
        vm.navigator = epubNavThrowing

        vm.onPlay()
        verify { fixture.epubFormatManager.playTts(any(), currentLoc, epubNavThrowing, any(), any()) }
    }

    // --- T5: playTtsFromLast: con _lastTtsLocator presente y sin él ---
    @Test
    fun testT5_playTtsFromLast_withAndWithoutLastLocator() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val visualLoc = fixture.createMockLocator(hrefStr = "visual.html", position = 1)
        every { nav.currentLocator } returns MutableStateFlow(visualLoc)
        vm.navigator = nav

        // Case A: _lastTtsLocator is present via pause
        val lastLoc = fixture.createMockLocator(hrefStr = "saved_last.html", position = 10)
        fixture.epubCurrentTtsLocatorFlow.value = lastLoc
        val lastTtsField = ReaderViewModel::class.java.getDeclaredField("_lastTtsLocator").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (lastTtsField.get(vm) as MutableStateFlow<Locator?>).value = lastLoc

        assertEquals(lastLoc, vm.lastTtsLocator.value)

        vm.playTtsFromLast()

        // Verify lastTtsLocator is cleared to null
        assertNull(vm.lastTtsLocator.value)
        verify { fixture.epubFormatManager.playTts(any(), lastLoc, nav, any(), any()) }

        // Case B: _lastTtsLocator is null -> falls back to onPlay() -> visualLoc
        assertNull(vm.lastTtsLocator.value)
        vm.playTtsFromLast()
        verify { fixture.epubFormatManager.playTts(any(), visualLoc, nav, any(), any()) }
    }

    // --- T6: playTts reflow/PDF: targetIndex in cache, -1 with loadReflowPages, snippet refinement ---
    @Test
    fun testT6_playTts_reflowPdf_caching_pageLoad_and_snippetRefinement() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)

        // Case A: targetIndex already in cache
        val p1 = ReflowItem.Paragraph("Page 1 text", pageNumber = 1, id = "1")
        val p2 = ReflowItem.Paragraph("Page 2 text with special keyword", pageNumber = 2, id = "2")
        fixture.reflowStateFlow.value = ReflowState(items = listOf(p1, p2), totalPages = 2)

        val locPage1 = fixture.createMockLocator(position = 1)
        val slotIndexA = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndexA))
        } returns Unit

        vm.playTts(locPage1)
        assertEquals(0, slotIndexA.captured)

        // Case B: targetIndex == -1 triggers loadReflowPages
        val locPage3 = fixture.createMockLocator(position = 3)
        coEvery {
            fixture.pdfReflowManager.loadReflowPages(fromPage = 3, isInitial = true)
        } answers {
            val p3 = ReflowItem.Paragraph("Page 3 text", pageNumber = 3, id = "3")
            fixture.reflowStateFlow.value = ReflowState(items = listOf(p1, p2, p3), totalPages = 3)
        }

        val slotIndexB = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndexB))
        } returns Unit

        vm.playTts(locPage3)
        coVerify { fixture.pdfReflowManager.loadReflowPages(fromPage = 3, isInitial = true) }
        assertEquals(2, slotIndexB.captured)

        // Case C: snippet refinement matches text
        val locSnippet = fixture.createMockLocator(position = 1)
        every { locSnippet.text } returns Locator.Text(highlight = "special keyword")

        val slotIndexC = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndexC))
        } returns Unit

        vm.playTts(locSnippet)
        // Matched p2 at index 1
        assertEquals(1, slotIndexC.captured)
    }

    // --- T7: playTts EPUB: detiene reflow solo si estado PLAYING o PAUSED ---
    @Test
    fun testT7_playTts_epub_stopsReflowOnlyWhenPlayingOrPaused() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val loc = fixture.createMockLocator(hrefStr = "chap1.html", position = 1)

        // Subcase A: reflow is PLAYING -> stops reflow
        io.mockk.clearMocks(fixture.pdfReflowManager, answers = false)
        fixture.reflowTtsStateFlow.value = TtsState.PLAYING
        vm.playTts(loc)
        verify(exactly = 1) { fixture.pdfReflowManager.stopReflowTts() }

        // Subcase B: reflow is STOPPED -> does not call stopReflowTts in EPUB branch
        io.mockk.clearMocks(fixture.pdfReflowManager, answers = false)
        fixture.reflowTtsStateFlow.value = TtsState.STOPPED
        vm.playTts(loc)
        verify(exactly = 0) { fixture.pdfReflowManager.stopReflowTts() }
    }

    // --- T8: pauseTts EPUB: llama epubFormatManager.pauseTts y pasa a MANUAL sin tocar reflow ---
    @Test
    fun testT8_pauseTts_epub_pausesEpubAndSwitchesToManual() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)

        vm.pauseTts()

        verify { fixture.epubFormatManager.pauseTts() }
        verify(exactly = 0) { fixture.pdfReflowManager.pauseReflowTts() }
        verify { fixture.readingSessionManager.switchSessionMode(any(), any(), ReadingMode.MANUAL, any()) }
    }

    // --- T9: resumeTts PDF original no pausado: llama playTts(locator) ---
    @Test
    fun testT9_resumeTts_pdfOriginal_notPaused_callsPlayTts() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 4)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        fixture.isReflowModeFlow.value = false
        fixture.reflowTtsStateFlow.value = TtsState.STOPPED

        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any())
        } returns Unit

        vm.resumeTts()

        coVerify { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any()) }
    }

    // --- T10: resumeTts reflow SIN scroll previo con pausedIdx válido y fuera de rango ---
    @Test
    fun testT10_resumeTts_reflow_noScroll_validPausedIdx_and_outOfRange() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        val p0 = ReflowItem.Paragraph("Para 0", pageNumber = 1, id = "0")
        val p1 = ReflowItem.Paragraph("Para 1", pageNumber = 1, id = "1")
        fixture.reflowElementsFlow.value = listOf(p0, p1)

        // Subcase A: pausedIdx is 1 (valid)
        fixture.reflowTtsActiveBlockIndexFlow.value = 1
        vm.pauseTts()

        val slotIndexValid = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndexValid))
        } returns Unit

        vm.resumeTts()
        assertEquals(1, slotIndexValid.captured)

        // Subcase B: pausedIdx is 99 (out of range) -> falls back to visibleReadableIdx (0)
        fixture.reflowTtsActiveBlockIndexFlow.value = 99
        vm.pauseTts()

        val slotIndexFallback = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndexFallback))
        } returns Unit

        vm.resumeTts()
        assertEquals(0, slotIndexFallback.captured)
    }

    // --- T11: resumeTts EPUB STOPPED/IDLE va a playTtsFromVisualPosition; PLAYING no hace nada ---
    @Test
    fun testT11_resumeTts_epub_stoppedOrPlaying_behavior() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(hrefStr = "chap1.html", position = 1)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        // Subcase A: STOPPED/IDLE
        fixture.epubTtsStateFlow.value = TtsState.IDLE
        vm.resumeTts()
        verify { fixture.epubFormatManager.playTts(any(), loc, nav, any(), any()) }

        // Subcase B: PLAYING -> does nothing
        io.mockk.clearMocks(fixture.epubFormatManager, answers = false)
        fixture.epubTtsStateFlow.value = TtsState.PLAYING
        vm.resumeTts()
        verify(exactly = 0) { fixture.epubFormatManager.resumeTts() }
        verify(exactly = 0) { fixture.epubFormatManager.playTts(any(), any(), any(), any(), any()) }
    }

    // --- T12: skipReflowTtsForward (+1) y skipReflowTtsBackward (-1 con coerceAtLeast(0)) ---
    @Test
    fun testT12_skipReflowTts_forwardAndBackward() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        fixture.reflowTtsActiveBlockIndexFlow.value = 2

        val slotForward = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotForward))
        } returns Unit

        vm.skipReflowTtsForward()
        assertEquals(3, slotForward.captured)

        val slotBackward = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotBackward))
        } returns Unit

        vm.skipReflowTtsBackward()
        assertEquals(1, slotBackward.captured)

        // Boundary test: backward from 0 coerced to 0
        fixture.reflowTtsActiveBlockIndexFlow.value = 0
        val slotCoerced = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotCoerced))
        } returns Unit

        vm.skipReflowTtsBackward()
        assertEquals(0, slotCoerced.captured)
    }

    // --- T13: skipTtsSentence/Word/Chapter delegating correctly ---
    @Test
    fun testT13_skipTts_delegation_reflow_vs_epub() = runTest(testDispatcher) {
        // Reflow mode
        val vmReflow = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        fixture.reflowTtsActiveBlockIndexFlow.value = 1
        coEvery { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any()) } returns Unit

        vmReflow.skipTtsSentenceForward()
        coVerify { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = 2) }

        vmReflow.skipTtsWordBackward()
        coVerify { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = 0) }

        // EPUB mode
        val vmEpub = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        vmEpub.skipTtsSentenceForward()
        verify { fixture.epubFormatManager.skipTtsSentenceForward() }

        vmEpub.skipTtsSentenceBackward()
        verify { fixture.epubFormatManager.skipTtsSentenceBackward() }

        vmEpub.skipTtsWordForward()
        verify { fixture.epubFormatManager.skipTtsWordForward() }

        vmEpub.skipTtsWordBackward()
        verify { fixture.epubFormatManager.skipTtsWordBackward() }

        vmEpub.skipTtsChapterForward()
        verify { fixture.epubFormatManager.skipTtsChapterForward(any(), any(), any()) }

        vmEpub.skipTtsChapterBackward()
        verify { fixture.epubFormatManager.skipTtsChapterBackward(any(), any(), any()) }
    }

    // --- T14: stopTts borra el estado de pausa ---
    @Test
    fun testT14_stopTts_clearsPauseState_so_subsequentResumeUsesVisibleIndex() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        val p0 = ReflowItem.Paragraph("Item 0", pageNumber = 1, id = "0")
        val p1 = ReflowItem.Paragraph("Item 1", pageNumber = 1, id = "1")
        val p2 = ReflowItem.Paragraph("Item 2", pageNumber = 1, id = "2")
        fixture.reflowElementsFlow.value = listOf(p0, p1, p2)

        fixture.reflowTtsActiveBlockIndexFlow.value = 2
        vm.pauseTts()

        // Stop TTS
        vm.stopTts()

        // After stop, resumeTts should ignore paused index (2) and resume from visible index (0)
        val slotIndex = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndex))
        } returns Unit

        vm.resumeTts()
        assertEquals(0, slotIndex.captured)
    }

    // --- T15: onReflowScroll marca scroll desde pausa solo si cambia índice y hay pausa guardada ---
    @Test
    fun testT15_onReflowScroll_marksScrollOnlyWhenIndexChangesAndPaused() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        val p0 = ReflowItem.Paragraph("Item 0", pageNumber = 1, id = "0")
        val p1 = ReflowItem.Paragraph("Item 1", pageNumber = 1, id = "1")
        val p2 = ReflowItem.Paragraph("Item 2", pageNumber = 1, id = "2")
        fixture.reflowElementsFlow.value = listOf(p0, p1, p2)

        // 1. Scroll without pause: should NOT set userScrolledSinceTtsPause
        vm.onReflowScroll(firstVisibleItemIndex = 1, isScrolling = false)

        // 2. Pause at block 0
        fixture.reflowTtsActiveBlockIndexFlow.value = 0
        vm.pauseTts()

        // 3. Scroll to SAME index (1) -> should NOT mark scroll
        vm.onReflowScroll(firstVisibleItemIndex = 1, isScrolling = false)

        // 4. Scroll to DIFFERENT index (2) -> marks userScrolledSinceTtsPause = true
        vm.onReflowScroll(firstVisibleItemIndex = 2, isScrolling = false)

        val slotIndex = slot<Int>()
        coEvery {
            fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), startIndex = capture(slotIndex))
        } returns Unit

        vm.resumeTts()
        // Resumes at visible index 2 because user scrolled during pause
        assertEquals(2, slotIndex.captured)
    }

    // --- T16: Construcción: crear VM con UnconfinedTestDispatcher ejecuta loadBook -> stopTts síncronamente ---
    @Test
    fun testT16_construction_invokesStopTtsSynchronously_withoutException() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB)
        assertNotNull(vm)
        // Verify stopTts side effects executed during constructor:
        assertNull(vm.lastTtsLocator.value)
        assertEquals("", vm.currentTtsSentence.value)
        assertNull(vm.currentTtsRange.value)
        assertNull(vm.activeNarrationBlockId.value)
        assertNull(vm.activeNarrationWordRange.value)
        verify(atLeast = 1) { fixture.epubFormatManager.stopTts() }
        verify(atLeast = 1) { fixture.pdfReflowManager.stopReflowTts() }
    }
}
