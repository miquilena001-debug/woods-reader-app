package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.data.model.ReflowState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.mediatype.MediaType

class ReaderViewModelReflowCharacterizationTest {

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

    // --- Q1: toggleReflowMode con reflow activo y ttsActiveBlockIndex dentro de rango ---
    @Test
    fun testQ1_toggleReflowMode_whenActive_andActiveBlockInBounds_navigatesAndToggles() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val currentLoc = fixture.createMockLocator(position = 1)
        every { nav.currentLocator } returns MutableStateFlow(currentLoc)
        vm.navigator = nav

        val item = ReflowItem.Paragraph("Page 3 text", pageNumber = 3, id = "1")
        fixture.reflowElementsFlow.value = listOf(item)
        fixture.reflowTtsActiveBlockIndexFlow.value = 0 // apunta a elemento en página 3
        fixture.isReflowModeFlow.value = true

        val pubField = ReaderViewModel::class.java.getDeclaredField("publication").apply { isAccessible = true }
        val pub = pubField.get(vm) as Publication
        val realLoc = Locator(href = mockk(relaxed = true), mediaType = MediaType.PDF)
        every { pub.locatorFromLink(any()) } returns realLoc

        vm.toggleReflowMode()

        testScheduler.advanceTimeBy(150)

        // Verifica que navigator.go navegue al locator de página 3
        val locSlot = slot<Locator>()
        verify(exactly = 1) { nav.go(capture(locSlot), any()) }
        assertEquals(3, locSlot.captured.locations.position)

        // Verifica que pdfReflowManager.toggleReflowMode se invoque con initialPosition = 3
        verify(exactly = 1) { fixture.pdfReflowManager.toggleReflowMode(any(), initialPosition = 3) }
    }

    // --- Q2: toggleReflowMode con reflow activo pero activeIndex fuera de rango ---
    @Test
    fun testQ2_toggleReflowMode_whenActive_andActiveBlockOutOfBounds_fallsBackToCurrentLocatorPosition() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val currentLoc = fixture.createMockLocator(position = 5)
        every { nav.currentLocator } returns MutableStateFlow(currentLoc)
        vm.navigator = nav

        fixture.reflowElementsFlow.value = emptyList() // activeIndex fuera de rango
        fixture.reflowTtsActiveBlockIndexFlow.value = 99
        fixture.isReflowModeFlow.value = true

        vm.toggleReflowMode()

        testScheduler.advanceTimeBy(150)

        // No navega con go() porque pageFromReflow es null
        verify(exactly = 0) { nav.go(any<Locator>(), any()) }
        // Llama toggleReflowMode con posición de currentLocator (5)
        verify(exactly = 1) { fixture.pdfReflowManager.toggleReflowMode(any(), initialPosition = 5) }

        // Subcaso: navigator es null -> currentLocator es null -> fallback a 1
        vm.navigator = null
        vm.toggleReflowMode()
        verify(exactly = 1) { fixture.pdfReflowManager.toggleReflowMode(any(), initialPosition = 1) }
    }

    // --- Q3: toggleReflowMode con reflow inactivo ---
    @Test
    fun testQ3_toggleReflowMode_whenInactive_doesNotNavigate_andTogglesWithPosition() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false)
        val nav = mockk<VisualNavigator>(relaxed = true)
        val currentLoc = fixture.createMockLocator(position = 1)
        every { nav.currentLocator } returns MutableStateFlow(currentLoc)
        vm.navigator = nav

        val item = ReflowItem.Paragraph("Page 4 text", pageNumber = 4, id = "1")
        fixture.reflowElementsFlow.value = listOf(item)
        fixture.reflowTtsActiveBlockIndexFlow.value = 0
        fixture.isReflowModeFlow.value = false // Inactivo

        vm.toggleReflowMode()

        testScheduler.advanceTimeBy(150)

        // No debe llamar a navigator.go
        verify(exactly = 0) { nav.go(any<Locator>(), any()) }
        // Debe llamar toggleReflowMode con initialPosition = 4
        verify(exactly = 1) { fixture.pdfReflowManager.toggleReflowMode(any(), initialPosition = 4) }
    }

    // --- Q4: loadNextPage y loadPreviousPage delegan a pdfReflowManager ---
    @Test
    fun testQ4_loadNextPage_and_loadPreviousPage_delegateToPdfReflowManager() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)

        vm.loadNextPage()
        verify(exactly = 1) { fixture.pdfReflowManager.loadNextPage(any()) }

        vm.loadPreviousPage()
        verify(exactly = 1) { fixture.pdfReflowManager.loadPreviousPage(any()) }
    }

    // --- Q5: jumpToReflowPage actualiza progressTracker y delega a pdfReflowManager ---
    @Test
    fun testQ5_jumpToReflowPage_updatesProgressTracker_and_callsPdfReflowManager() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)

        val trackerField = ReaderViewModel::class.java.getDeclaredField("progressTracker").apply { isAccessible = true }
        val tracker = trackerField.get(vm) as ReaderProgressTracker

        vm.jumpToReflowPage(7)

        assertEquals(7, tracker.lastKnownReflowPage)
        verify(exactly = 1) { fixture.pdfReflowManager.jumpToPage(any(), 7) }
    }

    // --- Q6: getLocatorForPdfPage delega con publicación y reflowState ---
    @Test
    fun testQ6_getLocatorForPdfPage_delegatesWithPublicationAndReflowState() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true)
        fixture.reflowStateFlow.value = ReflowState(items = emptyList(), totalPages = 10)

        val pubField = ReaderViewModel::class.java.getDeclaredField("publication").apply { isAccessible = true }
        val pub = pubField.get(vm) as Publication
        val realLoc = Locator(href = mockk(relaxed = true), mediaType = MediaType.PDF)
        every { pub.locatorFromLink(any()) } returns realLoc

        val loc = vm.getLocatorForPdfPage(3)
        assertNotNull(loc)
        assertEquals(3, loc?.locations?.position)

        // Caso donde publication es null -> retorna null
        pubField.set(vm, null)
        assertNull(vm.getLocatorForPdfPage(3))
    }
}
