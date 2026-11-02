package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.BookFormat
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Locator

class TrackingStateFlow<T>(
    private val delegate: StateFlow<T>
) : StateFlow<T> by delegate {
    var activeSubscriptions = 0
    var totalSubscriptions = 0

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        totalSubscriptions++
        activeSubscriptions++
        try {
            delegate.collect(collector)
        } finally {
            activeSubscriptions--
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelLocatorDispatchCharacterizationTest {

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

    private fun createTrackingVisualNavigator(initialLocator: Locator): Pair<VisualNavigator, TrackingStateFlow<Locator>> {
        val trackingFlow = TrackingStateFlow(MutableStateFlow(initialLocator))
        val nav = mockk<VisualNavigator>(relaxed = true)
        io.mockk.every { nav.currentLocator } returns trackingFlow
        return nav to trackingFlow
    }

    // =========================================================================
    // Caso a: Asignar la misma instancia dos veces (comportamiento actual en HEAD)
    // =========================================================================
    @Test
    fun `case a - assigning same visual navigator instance twice produces two subscriptions in HEAD`() = runTest(testDispatcher) {
        val vm = fixture.createViewModel()
        val loc = fixture.createMockLocator()
        val (nav, trackingFlow) = createTrackingVisualNavigator(loc)

        vm.navigator = nav
        assertEquals(1, trackingFlow.totalSubscriptions)
        assertEquals(1, trackingFlow.activeSubscriptions)

        // En HEAD, reasignar la misma instancia cancela y vuelve a suscribir
        vm.navigator = nav
        assertEquals(2, trackingFlow.totalSubscriptions)
        assertEquals(1, trackingFlow.activeSubscriptions)
    }

    // =========================================================================
    // Caso b: Asignar una instancia distinta cancela la anterior e inicia una nueva
    // =========================================================================
    @Test
    fun `case b - assigning different visual navigator instance cancels previous and starts new`() = runTest(testDispatcher) {
        val vm = fixture.createViewModel()
        val loc = fixture.createMockLocator()
        val (nav1, flow1) = createTrackingVisualNavigator(loc)
        val (nav2, flow2) = createTrackingVisualNavigator(loc)

        vm.navigator = nav1
        assertEquals(1, flow1.activeSubscriptions)
        assertEquals(0, flow2.activeSubscriptions)

        vm.navigator = nav2
        assertEquals(0, flow1.activeSubscriptions)
        assertEquals(1, flow2.activeSubscriptions)
        assertEquals(1, flow2.totalSubscriptions)
    }

    // =========================================================================
    // Caso c: Asignar null cancela la suscripción activa
    // =========================================================================
    @Test
    fun `case c - assigning null cancels active subscription`() = runTest(testDispatcher) {
        val vm = fixture.createViewModel()
        val loc = fixture.createMockLocator()
        val (nav, flow) = createTrackingVisualNavigator(loc)

        vm.navigator = nav
        assertEquals(1, flow.activeSubscriptions)

        vm.navigator = null
        assertEquals(0, flow.activeSubscriptions)
    }

    // =========================================================================
    // Caso d: Asignar un Navigator que no es VisualNavigator no suscribe nada
    // =========================================================================
    @Test
    fun `case d - assigning non-visual navigator does not subscribe`() = runTest(testDispatcher) {
        val vm = fixture.createViewModel()
        val nonVisualNav = mockk<Navigator>(relaxed = true)

        vm.navigator = nonVisualNav
        // No lanza job ni intenta castear
        assertEquals(nonVisualNav, vm.navigator)
    }

    // =========================================================================
    // Caso e: onLocatorChanged llamado dos veces con el mismo locator en PDF original
    // =========================================================================
    @Test
    fun `case e - onLocatorChanged called twice with same locator in original PDF executes work twice`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, initialPage = 1)
        val loc = fixture.createMockLocator(position = 3, progression = 0.3, totalProgression = 0.3)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)
        vm.onLocatorChanged(loc)

        // En HEAD: refreshCurrentBookmarkState (vía isPageBookmarked) y saveProgressDebounced corren 2 veces
        verify(exactly = 2) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(any(), any(), any(), any())
        }
        verify(exactly = 2) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any(), any())
        }
    }

    // =========================================================================
    // Caso f: onLocatorChanged llamado dos veces con el mismo locator en EPUB
    // =========================================================================
    @Test
    fun `case f - onLocatorChanged called twice with same locator in EPUB deduplicates saveProgress but runs bookmark twice`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.EPUB, reflow = false, initialPage = 1)
        val loc = fixture.createMockLocator(hrefStr = "chapter1.html", position = 1, progression = 0.2, totalProgression = 0.1)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)
        vm.onLocatorChanged(loc)

        // En EPUB: saveProgressDebounced corre 1 sola vez por la guarda lastSavedLocatorJson
        verify(exactly = 1) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any())
        }
        // Sin embargo, refreshCurrentBookmarkState corre 2 veces
        verify(exactly = 2) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(any(), any(), any(), any())
        }
    }

    // =========================================================================
    // Caso g: En PDF con reflow activo no se ejecuta la rama de guardado
    // =========================================================================
    @Test
    fun `case g - in PDF with reflow active saveProgress is not called`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = true, initialPage = 1)
        val loc = fixture.createMockLocator(position = 2, progression = 0.2, totalProgression = 0.2)

        clearMocks(fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)

        verify(exactly = 0) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any())
        }
    }
}
