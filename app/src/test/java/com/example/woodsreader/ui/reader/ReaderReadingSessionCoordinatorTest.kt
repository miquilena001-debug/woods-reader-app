package com.example.woodsreader.ui.reader

import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.data.model.ReadingMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderReadingSessionCoordinatorTest {

    private val sessionManager = mockk<ReadingSessionManager>(relaxed = true)
    private val scope: CoroutineScope = TestScope()
    private val bookId = "test_book_1"

    private val visibleLoc = mockk<Locator>("visibleLoc", relaxed = true)
    private val ttsLoc = mockk<Locator>("ttsLoc", relaxed = true)
    private val initialLoc = mockk<Locator>("initialLoc", relaxed = true)

    private var currentVisibleLoc: Locator? = visibleLoc
    private var currentTtsLoc: Locator? = ttsLoc
    private var ttsPlaying = false

    private val coordinator = ReaderReadingSessionCoordinator(
        sessionManager = sessionManager,
        bookId = bookId,
        scope = scope,
        visibleLocator = { currentVisibleLoc },
        ttsLocator = { currentTtsLoc },
        isTtsPlaying = { ttsPlaying }
    )

    @Test
    fun testManualModeNotPlayingUsesVisibleLocator() {
        every { sessionManager.currentSessionMode } returns ReadingMode.MANUAL
        ttsPlaying = false

        coordinator.stop()
        verify { sessionManager.stopActiveSession(bookId = bookId, currentLocator = visibleLoc) }

        coordinator.switchMode(ReadingMode.MANUAL)
        verify { sessionManager.switchSessionMode(scope, bookId, ReadingMode.MANUAL, visibleLoc) }
    }

    @Test
    fun testModeTtsUsesTtsLocatorAndFallsBackToVisibleLocatorWhenTtsNull() {
        every { sessionManager.currentSessionMode } returns ReadingMode.TTS
        ttsPlaying = false

        // With tts locator present
        currentTtsLoc = ttsLoc
        coordinator.stop()
        verify { sessionManager.stopActiveSession(bookId = bookId, currentLocator = ttsLoc) }

        // When tts locator is null, falls back to visible locator
        currentTtsLoc = null
        coordinator.stop()
        verify { sessionManager.stopActiveSession(bookId = bookId, currentLocator = visibleLoc) }
    }

    @Test
    fun testManualModeWithTtsPlayingUsesTtsLocator() {
        every { sessionManager.currentSessionMode } returns ReadingMode.MANUAL
        ttsPlaying = true
        currentTtsLoc = ttsLoc

        coordinator.stop()
        verify { sessionManager.stopActiveSession(bookId = bookId, currentLocator = ttsLoc) }
    }

    @Test
    fun testStartPrefersVisibleLocatorOverInitialLocatorAndReturnsTotalProgression() {
        val locationsWithProg = mockk<Locator.Locations>(relaxed = true)
        every { locationsWithProg.totalProgression } returns 0.75
        every { visibleLoc.locations } returns locationsWithProg

        val initialLocations = mockk<Locator.Locations>(relaxed = true)
        every { initialLocations.totalProgression } returns 0.25
        every { initialLoc.locations } returns initialLocations

        // 1. Visible locator present -> preferred over initialLocator
        currentVisibleLoc = visibleLoc
        val prog1 = coordinator.start(ReadingMode.MANUAL, initialLoc)
        verify { sessionManager.startNewSession(scope, bookId, ReadingMode.MANUAL, visibleLoc) }
        assertEquals(0.75, prog1)

        // 2. Visible locator null -> uses initialLocator
        currentVisibleLoc = null
        val prog2 = coordinator.start(ReadingMode.MANUAL, initialLoc)
        verify { sessionManager.startNewSession(scope, bookId, ReadingMode.MANUAL, initialLoc) }
        assertEquals(0.25, prog2)

        // 3. Both null -> returns null
        val prog3 = coordinator.start(ReadingMode.MANUAL, null)
        verify { sessionManager.startNewSession(scope, bookId, ReadingMode.MANUAL, null) }
        assertNull(prog3)
    }

    @Test
    fun testRecordActivityAlwaysUsesVisibleLocatorEvenInTtsMode() {
        every { sessionManager.currentSessionMode } returns ReadingMode.TTS
        ttsPlaying = true
        currentTtsLoc = ttsLoc
        currentVisibleLoc = visibleLoc

        coordinator.recordActivity()
        verify { sessionManager.recordActivity(scope, bookId, visibleLoc) }
    }
}
