package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReflowState
import com.example.woodsreader.model.PageTurnStyle
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.mediatype.MediaType

class ReaderViewModelNavigationCharacterizationTest {

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
    }

    private fun getProgressTracker(vm: ReaderViewModel): ReaderProgressTracker {
        val field = ReaderViewModel::class.java.getDeclaredField("progressTracker")
        field.isAccessible = true
        return field.get(vm) as ReaderProgressTracker
    }

    private fun setPositionsByResource(vm: ReaderViewModel, positions: List<List<Locator>>) {
        val field = ReaderViewModel::class.java.getDeclaredField("positionsByResource")
        field.isAccessible = true
        field.set(vm, positions)
    }

    private fun setTotalPositions(vm: ReaderViewModel, total: Int) {
        val field = ReaderViewModel::class.java.getDeclaredField("totalPositions")
        field.isAccessible = true
        field.set(vm, total)
    }

    // --- N1: seekToGlobalProgress en reflow activo con totalPages > 0 ---
    @Test
    fun testN1_seekToGlobalProgress_reflowMode_updatesTrackerAndJumps() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        fixture.reflowStateFlow.value = ReflowState(totalPages = 10)

        vm.seekToGlobalProgress(0.5f, null)

        assertEquals("lastKnownReflowPage debe ser 5", 5, getProgressTracker(vm).lastKnownReflowPage)
        verify { fixture.pdfReflowManager.jumpToPage(any(), 5) }
        assertEquals("globalProgress debe ser 0.5f", 0.5f, vm.globalProgress.value, 0.001f)
    }

    // --- N2: seekToGlobalProgress en PDF original ---
    @Test
    fun testN2_seekToGlobalProgress_pdfOriginal_updatesTrackerAndNavigates() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val link = mockk<Link>(relaxed = true)
        every { vm.publication?.readingOrder } returns listOf(link)
        every { vm.publication?.locatorFromLink(link) } returns Locator(
            href = mockk(relaxed = true),
            mediaType = MediaType.PDF
        )

        vm.seekToGlobalProgress(0.4f, nav) // 0.4 * 20 = 8

        assertEquals("lastKnownReflowPage debe ser 8", 8, getProgressTracker(vm).lastKnownReflowPage)
        assertEquals("globalProgress debe ser 0.4f", 0.4f, vm.globalProgress.value, 0.001f)
        verify { nav.go(match<Locator> { it.locations.position == 8 }, animated = false) }
    }

    // --- N3: seekToGlobalProgress en EPUB fallback sin posiciones ---
    @Test
    fun testN3_seekToGlobalProgress_epubFallback_navigatesToLinkByIndex() = runTest(testDispatcher) {
        val hrefs = listOf("c1.html", "c2.html", "c3.html", "c4.html")
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, totalPositions = 0, readingOrderHrefs = hrefs)
        val nav = mockk<VisualNavigator>(relaxed = true)

        val locators = hrefs.map { fixture.createMockLocator(it) }
        every { vm.publication?.locatorFromLink(any<Link>()) } answers {
            val link = firstArg<Link>()
            val hrefStr = link.href.toString()
            locators.find { it.href.toString() == hrefStr } ?: locators.first()
        }

        vm.seekToGlobalProgress(0.5f, nav) // 0.5 * 4 = 2 -> c3.html

        assertEquals("globalProgress debe ser 0.5f", 0.5f, vm.globalProgress.value, 0.001f)
        val slotLoc = slot<Locator>()
        verify { nav.go(capture(slotLoc), animated = false) }
        assertEquals("c3.html", slotLoc.captured.href.toString())
    }

    // --- N4: seekToGlobalProgress en EPUB por posiciones ---
    @Test
    fun testN4_seekToGlobalProgress_epubPositions_navigatesToResourceAndPosition() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, totalPositions = 10)
        val nav = mockk<VisualNavigator>(relaxed = true)

        val res0Locators = (0..4).map { fixture.createMockLocator("res0.html", position = it) }
        val targetLocator = fixture.createMockLocator("res1.html", position = 7)
        val res1Locators = listOf(
            fixture.createMockLocator("res1.html", position = 5),
            fixture.createMockLocator("res1.html", position = 6),
            targetLocator,
            fixture.createMockLocator("res1.html", position = 8),
            fixture.createMockLocator("res1.html", position = 9)
        )

        setPositionsByResource(vm, listOf(res0Locators, res1Locators))
        setTotalPositions(vm, 10)

        vm.seekToGlobalProgress(0.7f, nav) // 0.7 * 10 = 7 -> resource 1, position 2

        assertEquals("globalProgress debe ser 0.7f", 0.7f, vm.globalProgress.value, 0.001f)
        verify { nav.go(targetLocator, animated = false) }
    }

    // --- N5: seekToGlobalProgress con nav null en EPUB y límites de percent ---
    @Test
    fun testN5_seekToGlobalProgress_nullNavigatorOrClampedPercent() = runTest(testDispatcher) {
        // Parte A: nav null en EPUB
        val vmEpub = fixture.loadedVm(format = BookFormat.EPUB, reflow = false)
        vmEpub.seekToGlobalProgress(0.8f, null)
        assertEquals("Con nav null en EPUB no debe actualizar globalProgress", 0f, vmEpub.globalProgress.value, 0.001f)

        // Parte B: percent fuera de límites en reflow (clamped por coerceIn)
        val vmReflow = fixture.loadedVm(reflow = true)
        fixture.reflowStateFlow.value = ReflowState(totalPages = 10)

        vmReflow.seekToGlobalProgress(-1.0f, null)
        assertEquals("Percent negativo debe ser limitado a página 1", 1, getProgressTracker(vmReflow).lastKnownReflowPage)

        vmReflow.seekToGlobalProgress(2.0f, null)
        assertEquals("Percent > 1 debe ser limitado a página 10", 10, getProgressTracker(vmReflow).lastKnownReflowPage)
    }

    // --- N6: goToLocator en reflow con posición en locator ---
    @Test
    fun testN6_goToLocator_reflowWithPosition_jumpsAndRecordsActivity() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 4)

        vm.goToLocator(nav, loc)

        verify { nav.go(loc, animated = true) }
        verify { fixture.pdfReflowManager.jumpToPage(any(), 4) }
        assertEquals("lastKnownReflowPage debe ser 4", 4, getProgressTracker(vm).lastKnownReflowPage)
    }

    // --- N7: goToLocator cuando TTS está PLAYING ---
    @Test
    fun testN7_goToLocator_whenTtsPlaying_invokesPlayTts() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        fixture.reflowTtsStateFlow.value = TtsState.PLAYING
        val loc = fixture.createMockLocator(position = 4)

        vm.goToLocator(null, loc)

        coVerify { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any()) }
        assertNull("No debe guardar en _lastTtsLocator si TTS está reproduciendo", vm.lastTtsLocator.value)
    }

    // --- N8: goToLocator cuando TTS no está PLAYING ---
    @Test
    fun testN8_goToLocator_whenTtsNotPlaying_updatesLastTtsLocator() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        fixture.reflowTtsStateFlow.value = TtsState.PAUSED
        val loc = fixture.createMockLocator(position = 4)

        vm.goToLocator(null, loc)

        assertEquals("Debe asignar _lastTtsLocator cuando TTS está en pausa", loc, vm.lastTtsLocator.value)
        coVerify(exactly = 0) { fixture.pdfReflowManager.playReflowTts(any(), any(), any(), any(), any(), any()) }
    }

    // --- N9: goToNextChapter cuando publication == null ---
    @Test
    fun testN9_goToNextChapter_whenPublicationNull_returnsEarly() = runTest(testDispatcher) {
        val vm = fixture.loadedVm()
        vm.publication = null

        vm.goToNextChapter(null)

        verify(exactly = 0) {
            fixture.chapterNavigationCoordinator.goToNextChapter(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // --- N10: goToNextChapter en reflow no sobreescribe lastKnownReflowPage ---
    @Test
    fun testN10_goToNextChapter_inReflow_passesCurrentPdfPageWithoutMutatingTracker() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        getProgressTracker(vm).lastKnownReflowPage = 3
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

        vm.goToNextChapter(null)

        assertEquals("currentPdfPage debe ser 3", 3, slotPage.captured)
        assertEquals("lastKnownReflowPage no debe ser mutado en reflow", 3, getProgressTracker(vm).lastKnownReflowPage)
    }

    // --- N11: goToPreviousChapter en PDF original actualiza tracker ---
    @Test
    fun testN11_goToPreviousChapter_pdfOriginal_updatesTrackerAndDelegates() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 6)
        every { nav.currentLocator } returns MutableStateFlow(loc)
        val slotPage = slot<Int>()

        every {
            fixture.chapterNavigationCoordinator.goToPreviousChapter(
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
                onSetLastTtsLocator = any(),
                onDelayedScrollRefresh = any()
            )
        } returns Unit

        vm.goToPreviousChapter(nav)

        assertEquals("currentPdfPage debe ser 6", 6, slotPage.captured)
        assertEquals("lastKnownReflowPage debe actualizarse a 6", 6, getProgressTracker(vm).lastKnownReflowPage)
    }

    // --- N12: seekToGlobalProgress PDF original con nav == null actualiza estado sin navegar ---
    @Test
    fun testN12_seekToGlobalProgress_pdfOriginal_nullNavigator_updatesStateWithoutNavigating() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, totalPositions = 20)

        vm.seekToGlobalProgress(0.3f, null) // 0.3 * 20 = 6

        assertEquals("lastKnownReflowPage debe ser 6 aún con nav nulo", 6, getProgressTracker(vm).lastKnownReflowPage)
        assertEquals("globalProgress debe ser 0.3f", 0.3f, vm.globalProgress.value, 0.001f)
    }

    // --- N13: seekToGlobalProgress EPUB fallback con locatorFromLink null actualiza globalProgress ---
    @Test
    fun testN13_seekToGlobalProgress_epubFallback_nullLocator_updatesGlobalProgress() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, totalPositions = 0, readingOrderHrefs = listOf("c1.html"))
        val nav = mockk<VisualNavigator>(relaxed = true)
        every { vm.publication?.locatorFromLink(any<Link>()) } returns null

        vm.seekToGlobalProgress(0.6f, nav)

        assertEquals("globalProgress debe actualizarse a 0.6f aun con locator nulo", 0.6f, vm.globalProgress.value, 0.001f)
        verify(exactly = 0) { nav.go(any<Locator>(), any()) }
    }

    // --- N14: seekToGlobalProgress en reflow con totalPages == 0 cae a ramas siguientes ---
    @Test
    fun testN14_seekToGlobalProgress_reflowMode_zeroTotalPages_fallsThrough() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        fixture.reflowStateFlow.value = ReflowState(totalPages = 0)

        // Caso A: con nav null, cae a EPUB y retorna sin cambiar progreso
        vm.seekToGlobalProgress(0.5f, null)
        assertEquals("Con nav null debe retornar sin actualizar progreso", 0f, vm.globalProgress.value, 0.001f)

        // Caso B: con nav no nulo, cae a EPUB y ejecuta
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator("chapter1.html")
        every { vm.publication?.locatorFromLink(any<Link>()) } returns loc

        vm.seekToGlobalProgress(0.5f, nav)
        assertEquals("Con nav debe actualizar globalProgress en la rama EPUB", 0.5f, vm.globalProgress.value, 0.001f)
        verify { nav.go(loc, animated = false) }
    }

    // --- N15: seekToGlobalProgress con porcentajes extremos limitado por coerceIn ---
    @Test
    fun testN15_seekToGlobalProgress_extremePercentages_clampedCorrectly() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        fixture.reflowStateFlow.value = ReflowState(totalPages = 10)

        vm.seekToGlobalProgress(-0.5f, null)
        assertEquals("Página mínima debe ser 1", 1, getProgressTracker(vm).lastKnownReflowPage)

        vm.seekToGlobalProgress(2.0f, null)
        assertEquals("Página máxima debe ser 10", 10, getProgressTracker(vm).lastKnownReflowPage)

        // En EPUB con posiciones
        val vmEpub = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, totalPositions = 10)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val locators = (0..9).map { fixture.createMockLocator("res0.html", position = it) }
        setPositionsByResource(vmEpub, listOf(locators))
        setTotalPositions(vmEpub, 10)

        vmEpub.seekToGlobalProgress(-1.0f, nav)
        verify { nav.go(locators[0], animated = false) }

        vmEpub.seekToGlobalProgress(2.0f, nav)
        verify { nav.go(locators[9], animated = false) }
    }

    // --- N16: goToPreviousChapter en reflow: cadena de fallback ---
    @Test
    fun testN16_goToPreviousChapter_reflow_fallbackChain() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(reflow = true)
        val slotPage = slot<Int>()
        every {
            fixture.chapterNavigationCoordinator.goToPreviousChapter(
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
                onSetLastTtsLocator = any(),
                onDelayedScrollRefresh = any()
            )
        } returns Unit

        // Caso 1: lastKnownReflowPage > 0
        getProgressTracker(vm).lastKnownReflowPage = 5
        fixture.reflowCurrentTtsPageFlow.value = 2
        vm.goToPreviousChapter(null)
        assertEquals("Debe usar lastKnownReflowPage si es > 0", 5, slotPage.captured)
        assertEquals("No debe mutar lastKnownReflowPage", 5, getProgressTracker(vm).lastKnownReflowPage)

        // Caso 2: lastKnownReflowPage == 0, currentTtsPage > 0
        getProgressTracker(vm).lastKnownReflowPage = 0
        fixture.reflowCurrentTtsPageFlow.value = 2
        vm.goToPreviousChapter(null)
        assertEquals("Debe caer a currentTtsPage si lastKnownReflowPage <= 0", 2, slotPage.captured)

        // Caso 3: ambos <= 0 -> 1
        getProgressTracker(vm).lastKnownReflowPage = 0
        fixture.reflowCurrentTtsPageFlow.value = 0
        vm.goToPreviousChapter(null)
        assertEquals("Debe caer a 1 como fallback final", 1, slotPage.captured)
    }

    // --- N17: goToNextChapter y goToPreviousChapter en PDF original: orden de asignación y valores <= 0 ---
    @Test
    fun testN17_goToNextAndPreviousChapter_pdfOriginal_orderOfSideEffectAndNegativeVisualPosition() = runTest(testDispatcher) {
        // Orden del efecto secundario en goToNextChapter
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val loc = fixture.createMockLocator(position = 8)
        every { nav.currentLocator } returns MutableStateFlow(loc)

        var capturedTrackerValueAtCallTime: Int? = null
        every {
            fixture.chapterNavigationCoordinator.goToNextChapter(
                scope = any(),
                bookId = any(),
                publication = any(),
                navigator = any(),
                isPdfBook = any(),
                isReflowMode = any(),
                currentPdfPage = any(),
                ttsIsPlaying = any(),
                resourceIndexCache = any(),
                onJumpToReflowPage = any(),
                onPlayPdfPageTts = any(),
                onPlayTts = any(),
                onSetLastTtsLocator = any()
            )
        } answers {
            capturedTrackerValueAtCallTime = getProgressTracker(vm).lastKnownReflowPage
        }

        vm.goToNextChapter(nav)
        assertEquals("lastKnownReflowPage ya debe tener el valor visual 8 al invocarse el coordinador", 8, capturedTrackerValueAtCallTime)

        // Posición visual <= 0 en goToPreviousChapter
        val locNeg = fixture.createMockLocator(position = -2)
        every { nav.currentLocator } returns MutableStateFlow(locNeg)
        val slotPage = slot<Int>()

        every {
            fixture.chapterNavigationCoordinator.goToPreviousChapter(
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
                onSetLastTtsLocator = any(),
                onDelayedScrollRefresh = any()
            )
        } returns Unit

        vm.goToPreviousChapter(nav)
        assertEquals("currentPdfPage pasado al coordinador debe ser 1 si la posición visual es <= 0", 1, slotPage.captured)
        assertEquals("lastKnownReflowPage debe guardar la posición visual cruda (-2)", -2, getProgressTracker(vm).lastKnownReflowPage)
    }
}
