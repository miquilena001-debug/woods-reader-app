package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.domain.reader.ChapterProgressResult
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Locator

class ReaderViewModelProgressCharacterizationTest {

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

    private fun getProgressTracker(vm: ReaderViewModel): ReaderProgressTracker {
        val field = ReaderViewModel::class.java.getDeclaredField("progressTracker")
        field.isAccessible = true
        return field.get(vm) as ReaderProgressTracker
    }

    private fun getAnnotationsController(vm: ReaderViewModel): ReaderAnnotationsController {
        val field = ReaderViewModel::class.java.getDeclaredField("annotations")
        field.isAccessible = true
        return field.get(vm) as ReaderAnnotationsController
    }

    private fun getCurrentTtsLocatorFlow(vm: ReaderViewModel): MutableStateFlow<Locator?> {
        val field = ReaderViewModel::class.java.getDeclaredField("_currentTtsLocator")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(vm) as MutableStateFlow<Locator?>
    }

    private fun getLastTtsLocatorFlow(vm: ReaderViewModel): MutableStateFlow<Locator?> {
        val field = ReaderViewModel::class.java.getDeclaredField("_lastTtsLocator")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(vm) as MutableStateFlow<Locator?>
    }

    private fun getGlobalProgressFlow(vm: ReaderViewModel): MutableStateFlow<Float> {
        val field = ReaderViewModel::class.java.getDeclaredField("_globalProgress")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(vm) as MutableStateFlow<Float>
    }

    private fun getUiStateFlow(vm: ReaderViewModel): MutableStateFlow<ReaderUiState> {
        val field = ReaderViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(vm) as MutableStateFlow<ReaderUiState>
    }

    private fun setResourceIndexCache(vm: ReaderViewModel, cache: Map<String, Int>?) {
        val field = ReaderViewModel::class.java.getDeclaredField("resourceIndexCache")
        field.isAccessible = true
        field.set(vm, cache)
    }

    private fun invokeOnTtsLocatorChanged(vm: ReaderViewModel, locator: Locator) {
        val method = ReaderViewModel::class.java.getDeclaredMethod("onTtsLocatorChanged", Locator::class.java)
        method.isAccessible = true
        method.invoke(vm, locator)
    }

    private fun invokeGetFallbackLocator(vm: ReaderViewModel): Locator? {
        val method = ReaderViewModel::class.java.getDeclaredMethod("getFallbackLocator")
        method.isAccessible = true
        return method.invoke(vm) as Locator?
    }

    // --- P1: onLocatorChanged en PDF Original calcula _globalProgress con pageFraction ---
    @Test
    fun testP1_onLocatorChanged_pdfOriginal_calculatesGlobalProgressWithPageFraction() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        val loc = fixture.createMockLocator(position = 5)

        vm.onLocatorChanged(loc)

        assertEquals("globalProgress debe calcularse como 5/20 = 0.25", 0.25f, vm.globalProgress.value, 0.001f)
    }

    // --- P2: onLocatorChanged en PDF Original persiste lastKnownReflowPage ---
    @Test
    fun testP2_onLocatorChanged_pdfOriginal_persistsLastKnownReflowPage() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        getProgressTracker(vm).lastKnownReflowPage = 1
        val loc = fixture.createMockLocator(position = 7)

        vm.onLocatorChanged(loc)

        assertEquals("lastKnownReflowPage debe ser 7", 7, getProgressTracker(vm).lastKnownReflowPage)
    }

    // --- P3: onLocatorChanged en PDF Original con position == null usa 1 como fallback ---
    @Test
    fun testP3_onLocatorChanged_pdfOriginal_nullPosition_fallbackToOne() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        getProgressTracker(vm).lastKnownReflowPage = 0
        val loc = fixture.createMockLocator(position = null)

        vm.onLocatorChanged(loc)

        assertEquals("Fallback position debe ser 1", 1, getProgressTracker(vm).lastKnownReflowPage)
        assertEquals("globalProgress con fallback 1 debe ser 1/20", 0.05f, vm.globalProgress.value, 0.001f)
    }

    // --- P4: onLocatorChanged en PDF Reflow no ejecuta guardado ni actualiza _globalProgress ---
    @Test
    fun testP4_onLocatorChanged_pdfReflow_doesNotSaveOrUpdateProgress() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, totalPositions = 20)
        val loc = fixture.createMockLocator(position = 5)

        vm.onLocatorChanged(loc)

        verify(exactly = 0) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = any(),
                progress = any(),
                locator = any(),
                fallbackPosition = any(),
                debounceMs = any(),
                force = any()
            )
        }
        assertEquals(0f, vm.globalProgress.value, 0.001f)
    }

    // --- P5: onLocatorChanged en EPUB descarta invocación si locJson == lastSavedLocatorJson ---
    @Test
    fun testP5_onLocatorChanged_epub_duplicateJson_ignored() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val loc = fixture.createMockLocator(hrefStr = "c1.html", position = 2)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"href\":\"c1.html\"}"

        vm.onLocatorChanged(loc)
        vm.onLocatorChanged(loc) // duplicate call

        verify(exactly = 1) {
            fixture.saveReadingProgressUseCase.setLastSavedLocatorJson("{\"href\":\"c1.html\"}")
        }
    }

    // --- P6: saveProgress en MANUAL mode incrementa palabras leídas ---
    @Test
    fun testP6_saveProgress_manualMode_incrementsWords() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        every { fixture.readingSessionManager.currentSessionMode } returns ReadingMode.MANUAL
        every { fixture.calculateReadingStatsUseCase.calculateWordsDelta(any(), any(), any()) } returns 42L
        val loc = fixture.createMockLocator(hrefStr = "c1.html", position = 2, totalProgression = 0.3)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"loc\":\"p6\"}"

        vm.onLocatorChanged(loc)

        verify(exactly = 1) { fixture.readingSessionManager.incrementWords(42L) }
    }

    // --- P7: saveProgress en TTS mode NO incrementa palabras ni altera lastRecordedProgression ---
    @Test
    fun testP7_saveProgress_ttsMode_doesNotIncrementWordsNorAdvanceProgression() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        every { fixture.readingSessionManager.currentSessionMode } returns ReadingMode.TTS
        every { fixture.calculateReadingStatsUseCase.calculateWordsDelta(any(), any(), any()) } returns 50L
        val loc = fixture.createMockLocator(hrefStr = "c1.html", position = 2, totalProgression = 0.5)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"loc\":\"p7\"}"
        getProgressTracker(vm).lastRecordedProgression = 0.0

        vm.onLocatorChanged(loc)

        verify(exactly = 0) { fixture.readingSessionManager.incrementWords(any()) }
        assertEquals("Progression no debe cambiar en TTS mode", 0.0, getProgressTracker(vm).lastRecordedProgression, 0.0001)
    }

    // --- P8: saveProgress deduplica pageKey (no llama addPageRead si ya existe en pagesRead) ---
    @Test
    fun testP8_saveProgress_duplicatePageKey_doesNotAddPageRead() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val pageKey = "c1.html#2"
        fixture.sessionPagesReadFlow.value = setOf(pageKey)
        val loc = fixture.createMockLocator(hrefStr = "c1.html", position = 2)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"loc\":\"p8\"}"

        vm.onLocatorChanged(loc)

        verify(exactly = 0) { fixture.readingSessionManager.addPageRead(pageKey) }
        coVerify(exactly = 0) { fixture.calculateReadingStatsUseCase.recordPageRead(pageKey, any()) }
    }

    // --- P9: saveProgress en EPUB actualiza caché de capítulo y llama prefetch al cambiar href ---
    @Test
    fun testP9_saveProgress_epub_chapterChange_triggersPrefetchAndCache() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, readingOrderHrefs = listOf("c1.html", "c2.html"))
        val loc = fixture.createMockLocator(hrefStr = "c2.html", position = 1)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"loc\":\"p9\"}"
        coEvery { fixture.epubFormatManager.processResource("c2.html") } returns mockk(relaxed = true)

        vm.onLocatorChanged(loc)

        coVerify { fixture.epubFormatManager.processResource("c2.html") }
    }

    // --- P10: saveProgress prefiere _globalProgress si es > 0f frente a locator.totalProgression ---
    @Test
    fun testP10_saveProgress_prefersGlobalProgressOverLocatorProgression() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        every {
            fixture.calculateReadingStatsUseCase.calculateChapterProgress(any(), any(), any(), any(), any())
        } returns ChapterProgressResult("10m", 0.85f)

        val loc = fixture.createMockLocator(hrefStr = "c1.html", position = 1, totalProgression = 0.3)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"loc\":\"p10\"}"

        vm.onLocatorChanged(loc)

        verify {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = any(),
                progress = 0.85f,
                locator = loc,
                fallbackPosition = any(),
                debounceMs = any(),
                force = any()
            )
        }
    }

    // --- P12: onTtsLocatorChanged actualiza lastSavedLocatorJson y despacha saveTtsProgressDebounced ---
    @Test
    fun testP12_onTtsLocatorChanged_updatesLastSavedLocatorAndSavesTtsProgress() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val loc = fixture.createMockLocator(hrefStr = "c1.html", position = 3)
        every { fixture.saveReadingProgressUseCase.serializeLocator(loc) } returns "{\"tts\":\"loc12\"}"

        invokeOnTtsLocatorChanged(vm, loc)

        assertEquals("{\"tts\":\"loc12\"}", getProgressTracker(vm).lastSavedLocatorJson)
        verify { fixture.saveReadingProgressUseCase.setLastSavedLocatorJson("{\"tts\":\"loc12\"}") }
        verify {
            fixture.saveReadingProgressUseCase.saveTtsProgressDebounced(
                scope = any(),
                bookId = "test_book_id",
                locator = loc
            )
        }
    }

    // --- P13: flushReadingProgress prioridad 1: toma _currentTtsLocator si TTS está PLAYING o PAUSED ---
    @Test
    fun testP13_flushReadingProgress_activeTts_takesTtsLocator() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        fixture.epubTtsStateFlow.value = TtsState.PLAYING
        val ttsLoc = fixture.createMockLocator(hrefStr = "tts_active.html", position = 10)
        getCurrentTtsLocatorFlow(vm).value = ttsLoc

        val nav = mockk<VisualNavigator>(relaxed = true)
        val navLoc = fixture.createMockLocator(hrefStr = "nav_loc.html", position = 1)
        every { nav.currentLocator } returns MutableStateFlow(navLoc)
        vm.navigator = nav

        val slotLoc = slot<Locator?>()
        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = any(),
                currentLocator = captureNullable(slotLoc),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertEquals(ttsLoc, slotLoc.captured)
    }

    // --- P14: flushReadingProgress prioridad 2: cae a annotations.currentLocator si nav es nulo ---
    @Test
    fun testP14_flushReadingProgress_nullNav_fallsBackToAnnotationsLocator() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        vm.navigator = null
        getCurrentTtsLocatorFlow(vm).value = null
        val annotLoc = fixture.createMockLocator(hrefStr = "annot.html", position = 4)
        getAnnotationsController(vm).currentLocator = annotLoc
        getProgressTracker(vm).lastSavedLocatorJson = null

        val slotLoc = slot<Locator?>()
        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = any(),
                currentLocator = captureNullable(slotLoc),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertEquals(annotLoc, slotLoc.captured)
    }

    // --- P15: flushReadingProgress prioridad 3: deserializa lastSavedLocatorJson si no hay locator en memoria ---
    @Test
    fun testP15_flushReadingProgress_deserializesLastSavedLocatorJson() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        vm.navigator = null
        getCurrentTtsLocatorFlow(vm).value = null
        getAnnotationsController(vm).currentLocator = null
        val savedJson = "{\"href\":\"saved_page.html\",\"locations\":{\"position\":42}}"
        getProgressTracker(vm).lastSavedLocatorJson = savedJson

        val expectedLocator = fixture.createMockLocator(hrefStr = "saved_page.html", position = 42)
        mockkObject(Locator.Companion)
        every { Locator.fromJSON(any()) } returns expectedLocator

        val slotLoc = slot<Locator?>()
        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = any(),
                currentLocator = captureNullable(slotLoc),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertNotNull("currentLoc no debe ser nulo cuando lastSavedLocatorJson es deserializable", slotLoc.captured)
        assertEquals(expectedLocator, slotLoc.captured)
    }

    // --- P16: flushReadingProgress en PDF Original persiste lastKnownReflowPage = it antes de flush ---
    @Test
    fun testP16_flushReadingProgress_pdfOriginal_remembersPageInTracker() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 9)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav
        getProgressTracker(vm).lastKnownReflowPage = 1

        vm.flushReadingProgressImmediately()

        assertEquals("lastKnownReflowPage debe actualizarse a 9 en PDF original flush", 9, getProgressTracker(vm).lastKnownReflowPage)
    }

    // --- P17: flushReadingProgress en EPUB ejecuta saveProgress(currentLoc) antes de flushImmediately ---
    @Test
    fun testP17_flushReadingProgress_epub_executesSaveProgressBeforeFlush() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(hrefStr = "chap1.xhtml", position = 3, totalProgression = 0.25)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        // Limpiar llamadas previas disparadas por observeNavigator al asignar navigator
        clearMocks(fixture.saveReadingProgressUseCase, answers = false)

        vm.flushReadingProgressImmediately()

        verifyOrder {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = any(),
                progress = any(),
                locator = loc,
                fallbackPosition = any(),
                debounceMs = any(),
                force = any()
            )
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = any(),
                currentLocator = loc,
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        }
    }

    // --- P18: getCurrentChapterIndex resuelve índice por readingOrder cuando no está en caché y retorna 0 si falla ---
    @Test
    fun testP18_getCurrentChapterIndex_resolvesFromReadingOrderWhenCacheMiss() = runTest(testDispatcher) {
        val hrefs = listOf("c0.html", "c1.html", "c2.html")
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, readingOrderHrefs = hrefs)
        setResourceIndexCache(vm, null) // forzar cache miss
        every { fixture.chapterNavigationCoordinator.findMatchingResourceIndex("c2.html", any(), any()) } returns 2

        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(hrefStr = "c2.html")
        every { nav.currentLocator } returns MutableStateFlow(loc)
        vm.navigator = nav

        val chapterIdx = vm.getCurrentChapterIndex()

        assertEquals("Debe resolver índice 2 por readingOrder", 2, chapterIdx)
    }

    // --- P19: getFallbackLocator cadena de fallback: prefiere _lastTtsLocator a _currentTtsLocator ---
    @Test
    fun testP19_getFallbackLocator_prefersLastTtsLocatorOverCurrent() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        vm.navigator = null
        val lastLoc = fixture.createMockLocator(hrefStr = "last.html", position = 5)
        val curLoc = fixture.createMockLocator(hrefStr = "curr.html", position = 6)
        getLastTtsLocatorFlow(vm).value = lastLoc
        getCurrentTtsLocatorFlow(vm).value = curLoc

        val fallback = invokeGetFallbackLocator(vm)

        assertEquals("Debe preferir _lastTtsLocator a _currentTtsLocator", lastLoc, fallback)
    }

    // --- P20: onLocatorChanged PDF Original invocado dos veces con el mismo locator: saveProgressDebounced x2 e isPageBookmarked x2 ---
    @Test
    fun testP20_onLocatorChanged_pdfOriginal_duplicateInvocation_triggersTwice() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        val loc = fixture.createMockLocator(position = 5)

        vm.onLocatorChanged(loc)
        vm.onLocatorChanged(loc)

        verify(exactly = 1) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(
                scope = any(),
                bookId = "test_book_id",
                progress = 0.25f,
                locator = loc,
                fallbackPosition = 5,
                debounceMs = any(),
                force = any()
            )
        }
        verify(exactly = 1) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(
                bookmarks = any(),
                currentLocator = loc,
                isPdfOrReflow = any(),
                reflowPage = any()
            )
        }
    }

    // --- P21: getFallbackLocator con initialLocatorJson inválido o en blanco ---
    @Test
    fun testP21_getFallbackLocator_invalidJsonFallsBackWithoutThrowing_blankJsonSkipsParse() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, readingOrderHrefs = listOf("first.html"))
        vm.navigator = null
        getLastTtsLocatorFlow(vm).value = null
        getCurrentTtsLocatorFlow(vm).value = null

        val pub = vm.publication!!
        val firstLink = pub.readingOrder.first()
        val firstLocator = fixture.createMockLocator(hrefStr = "first.html", position = 1)
        every { pub.locatorFromLink(firstLink) } returns firstLocator

        mockkObject(Locator.Companion)
        every { Locator.fromJSON(any()) } throws org.json.JSONException("broken json")

        val currentSuccess = getUiStateFlow(vm).value as ReaderUiState.Success

        // Parte A: initialLocatorJson con JSON roto no lanza excepción y cae a firstLink
        getUiStateFlow(vm).value = currentSuccess.copy(initialLocatorJson = "BROKEN_JSON_STRING{{{")
        val fallbackA = invokeGetFallbackLocator(vm)
        assertEquals("Con JSON inválido debe hacer fallback a firstLink sin lanzar", firstLocator, fallbackA)

        // Parte B: initialLocatorJson en blanco no intenta parsear y cae a firstLink
        getUiStateFlow(vm).value = currentSuccess.copy(initialLocatorJson = "   ")
        val fallbackB = invokeGetFallbackLocator(vm)
        assertEquals("Con JSON en blanco debe hacer fallback a firstLink", firstLocator, fallbackB)
    }

    // --- P22: flushReadingProgressImmediately con ttsState == STOPPED y _currentTtsLocator presente cae a nav ---
    @Test
    fun testP22_flushReadingProgress_inactiveTts_ignoresTtsLocatorAndFallsBackToNav() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        fixture.epubTtsStateFlow.value = TtsState.STOPPED
        val ttsLoc = fixture.createMockLocator(hrefStr = "tts_stopped.html", position = 8)
        getCurrentTtsLocatorFlow(vm).value = ttsLoc

        val nav = mockk<VisualNavigator>(relaxed = true)
        val navLoc = fixture.createMockLocator(hrefStr = "nav_active.html", position = 2)
        every { nav.currentLocator } returns MutableStateFlow(navLoc)
        vm.navigator = nav

        val slotLoc = slot<Locator?>()
        every {
            fixture.saveReadingProgressUseCase.flushImmediately(
                scope = any(),
                bookId = any(),
                chapterIndex = any(),
                progress = any(),
                currentLocator = captureNullable(slotLoc),
                fallbackLocatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        } returns Unit

        vm.flushReadingProgressImmediately()

        assertEquals("Con TTS STOPPED no debe usar locator TTS, debe caer a nav", navLoc, slotLoc.captured)
    }
}
