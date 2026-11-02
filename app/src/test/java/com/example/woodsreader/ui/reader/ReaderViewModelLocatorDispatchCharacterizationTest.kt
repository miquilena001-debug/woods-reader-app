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
    // Caso a: Asignar la misma instancia dos veces (con guarda de identidad)
    // =========================================================================
    @Test
    fun `case a - assigning same visual navigator instance twice produces single subscription with identity guard`() = runTest(testDispatcher) {
        val vm = fixture.createViewModel()
        val loc = fixture.createMockLocator()
        val (nav, trackingFlow) = createTrackingVisualNavigator(loc)

        vm.navigator = nav
        assertEquals(1, trackingFlow.totalSubscriptions)
        assertEquals(1, trackingFlow.activeSubscriptions)

        // Con la guarda de identidad, reasignar la misma instancia no re-suscribe
        vm.navigator = nav
        assertEquals(1, trackingFlow.totalSubscriptions)
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
    // Caso e / Test 1: Mismo locator dos veces seguidas en PDF original procesa UNA SOLA VEZ
    // =========================================================================
    @Test
    fun `commit3 test 1 - same locator twice consecutively in original PDF processes only once`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, initialPage = 1)
        val loc = fixture.createMockLocator(position = 3, progression = 0.3, totalProgression = 0.3)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)
        vm.onLocatorChanged(loc)

        // Con deduplicación en PDF original: ambos corren 1 sola vez
        verify(exactly = 1) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any(), any())
        }
    }

    // =========================================================================
    // Test 2: Mismo locator tras entrar y salir de reflow procesa de nuevo
    // =========================================================================
    @Test
    fun `commit3 test 2 - same locator after entering and exiting reflow processes again`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, initialPage = 1)
        val loc = fixture.createMockLocator(position = 3, progression = 0.3, totalProgression = 0.3)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)
        // Entrar y salir de reflow
        vm.toggleReflowMode()
        vm.toggleReflowMode()
        vm.onLocatorChanged(loc)

        verify(exactly = 2) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(any(), any(), any(), any())
        }
        verify(exactly = 2) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any(), any())
        }
    }

    // =========================================================================
    // Test 3: Mismo locator tras cambiar lastKnownReflowPage por otro escritor procesa de nuevo
    // =========================================================================
    @Test
    fun `commit3 test 3 - same locator after lastKnownReflowPage changed by another writer processes again`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, initialPage = 1)
        val loc = fixture.createMockLocator(position = 3, progression = 0.3, totalProgression = 0.3)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)
        // Otro escritor (ej. salto a página externa o jumpToReflowPage) altera la página
        vm.jumpToReflowPage(15)
        vm.onLocatorChanged(loc)

        verify(exactly = 2) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(any(), any(), any(), any())
        }
        verify(exactly = 2) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any(), any())
        }
    }

    // =========================================================================
    // Test 4: Mismo locator tras reasignar navigator procesa de nuevo
    // =========================================================================
    @Test
    fun `commit3 test 4 - same locator after reassigning navigator processes again`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, initialPage = 1)
        val loc = fixture.createMockLocator(position = 3, progression = 0.3, totalProgression = 0.3)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc)
        // Reasignar navigator invalida la clave de deduplicación
        val (newNav, _) = createTrackingVisualNavigator(loc)
        vm.navigator = newNav
        vm.onLocatorChanged(loc)

        verify(exactly = 2) {
            fixture.manageReaderAnnotationsUseCase.isPageBookmarked(any(), any(), any(), any())
        }
        verify(exactly = 2) {
            fixture.saveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), any(), any())
        }
    }

    // =========================================================================
    // Test 5: Otro locator procesa siempre
    // =========================================================================
    @Test
    fun `commit3 test 5 - different locator always processes`() = runTest(testDispatcher) {
        val vm = fixture.loadedVm(format = BookFormat.PDF, reflow = false, initialPage = 1)
        val loc1 = fixture.createMockLocator(position = 3, progression = 0.3, totalProgression = 0.3)
        val loc2 = fixture.createMockLocator(position = 4, progression = 0.4, totalProgression = 0.4)

        clearMocks(fixture.manageReaderAnnotationsUseCase, fixture.saveReadingProgressUseCase, answers = false)

        vm.onLocatorChanged(loc1)
        vm.onLocatorChanged(loc2)

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
