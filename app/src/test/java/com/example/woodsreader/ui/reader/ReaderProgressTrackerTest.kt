package com.example.woodsreader.ui.reader

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

class ReaderProgressTrackerTest {

    private fun createMockLocator(
        position: Int? = null,
        progression: Double? = null,
        totalProgression: Double? = null
    ): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val url = mockk<Url>(relaxed = true)
        every { url.toString() } returns "resource.xhtml"
        every { locator.href } returns url

        val locations = mockk<Locator.Locations>(relaxed = true)
        every { locations.position } returns position
        every { locations.progression } returns progression
        every { locations.totalProgression } returns totalProgression
        every { locator.locations } returns locations

        return locator
    }

    @Test
    fun testDefaults() {
        val tracker = ReaderProgressTracker()
        assertEquals(1, tracker.lastKnownReflowPage)
        assertEquals(0.0, tracker.lastRecordedProgression, 0.0001)
        assertNull(tracker.lastSavedLocatorJson)
    }

    @Test
    fun testTotalPdfPagesChain() {
        val tracker = ReaderProgressTracker()

        // 1. totalPositions wins when > 0
        assertEquals(50, tracker.totalPdfPages(totalPositions = 50, reflowTotalPages = 40, readingOrderSize = 30))

        // 2. then reflowTotalPages when totalPositions <= 0
        assertEquals(40, tracker.totalPdfPages(totalPositions = 0, reflowTotalPages = 40, readingOrderSize = 30))
        assertEquals(40, tracker.totalPdfPages(totalPositions = -1, reflowTotalPages = 40, readingOrderSize = 30))

        // 3. then readingOrderSize when both previous <= 0
        assertEquals(30, tracker.totalPdfPages(totalPositions = 0, reflowTotalPages = 0, readingOrderSize = 30))

        // 4. fallback to 1 when all <= 0 or null
        assertEquals(1, tracker.totalPdfPages(totalPositions = 0, reflowTotalPages = 0, readingOrderSize = null))
        assertEquals(1, tracker.totalPdfPages(totalPositions = -5, reflowTotalPages = 0, readingOrderSize = 0))
    }

    @Test
    fun testPageFractionDividesAndClamps() {
        val tracker = ReaderProgressTracker()

        assertEquals(0.5f, tracker.pageFraction(5, 10), 0.001f)
        assertEquals(0.0f, tracker.pageFraction(0, 10), 0.001f)
        assertEquals(1.0f, tracker.pageFraction(10, 10), 0.001f)
        // Clamping to 0..1
        assertEquals(0.0f, tracker.pageFraction(-5, 10), 0.001f)
        assertEquals(1.0f, tracker.pageFraction(15, 10), 0.001f)
    }

    @Test
    fun testPlanFlushReflowBranch() {
        val tracker = ReaderProgressTracker()

        // Page resolution: position wins
        val locWithPos = createMockLocator(position = 8, totalProgression = 0.8)
        val plan1 = tracker.planFlush(
            currentLoc = locWithPos, prog = 0.5f, isReflowMode = true, isPdfBook = false,
            reflowFirstElementPage = 3, reflowTotalPages = 20, totalPositions = 0,
            readingOrderSize = null, globalProgress = 0.1f
        )
        assertEquals(8, plan1.fallbackPosition)
        assertEquals(0.8f, plan1.progress, 0.001f)
        assertNull(plan1.pageToRemember)
        assertFalse(plan1.saveProgressFirst)

        // Page resolution: loc position null -> lastKnownReflowPage (>0) wins
        tracker.lastKnownReflowPage = 6
        val locNoPos = createMockLocator(position = null, totalProgression = null, progression = 0.3)
        val plan2 = tracker.planFlush(
            currentLoc = locNoPos, prog = 0.5f, isReflowMode = true, isPdfBook = false,
            reflowFirstElementPage = 3, reflowTotalPages = 20, totalPositions = 0,
            readingOrderSize = null, globalProgress = 0.1f
        )
        assertEquals(6, plan2.fallbackPosition)
        assertEquals(0.3f, plan2.progress, 0.001f)

        // Page resolution: lastKnownReflowPage <= 0 -> reflowFirstElementPage wins
        tracker.lastKnownReflowPage = 0
        val plan3 = tracker.planFlush(
            currentLoc = null, prog = 0.5f, isReflowMode = true, isPdfBook = false,
            reflowFirstElementPage = 4, reflowTotalPages = 20, totalPositions = 0,
            readingOrderSize = null, globalProgress = 0.1f
        )
        assertEquals(4, plan3.fallbackPosition)
        // Progress: reflowTotalPages > 0 -> pageFraction(4, 20) = 0.2f
        assertEquals(0.2f, plan3.progress, 0.001f)

        // Page resolution: reflowFirstElementPage null -> 1 fallback; reflowTotalPages <= 0 -> globalProgress fallback
        val plan4 = tracker.planFlush(
            currentLoc = null, prog = 0.5f, isReflowMode = true, isPdfBook = false,
            reflowFirstElementPage = null, reflowTotalPages = 0, totalPositions = 0,
            readingOrderSize = null, globalProgress = 0.77f
        )
        assertEquals(1, plan4.fallbackPosition)
        assertEquals(0.77f, plan4.progress, 0.001f)
    }

    @Test
    fun testPlanFlushPdfBranch() {
        val tracker = ReaderProgressTracker()

        // Page resolution: position wins; pageToRemember equals page
        val locWithPos = createMockLocator(position = 5, totalProgression = 0.5)
        val plan1 = tracker.planFlush(
            currentLoc = locWithPos, prog = 0.2f, isReflowMode = false, isPdfBook = true,
            reflowFirstElementPage = null, reflowTotalPages = 0, totalPositions = 10,
            readingOrderSize = null, globalProgress = 0.1f
        )
        assertEquals(5, plan1.fallbackPosition)
        assertEquals(5, plan1.pageToRemember)
        assertEquals(0.5f, plan1.progress, 0.001f)
        assertFalse(plan1.saveProgressFirst)

        // When loc progressions are null -> pageFraction(page, totalPdfPages)
        val locNoProg = createMockLocator(position = 3, progression = null, totalProgression = null)
        val plan2 = tracker.planFlush(
            currentLoc = locNoProg, prog = 0.2f, isReflowMode = false, isPdfBook = true,
            reflowFirstElementPage = null, reflowTotalPages = 0, totalPositions = 10,
            readingOrderSize = null, globalProgress = 0.1f
        )
        assertEquals(3, plan2.fallbackPosition)
        assertEquals(3, plan2.pageToRemember)
        assertEquals(0.3f, plan2.progress, 0.001f)

        // When currentLoc is null -> fallback to lastKnownReflowPage
        tracker.lastKnownReflowPage = 7
        val plan3 = tracker.planFlush(
            currentLoc = null, prog = 0.2f, isReflowMode = false, isPdfBook = true,
            reflowFirstElementPage = null, reflowTotalPages = 0, totalPositions = 10,
            readingOrderSize = null, globalProgress = 0.1f
        )
        assertEquals(7, plan3.fallbackPosition)
        assertEquals(7, plan3.pageToRemember)
        assertEquals(0.7f, plan3.progress, 0.001f)
    }

    @Test
    fun testPlanFlushEpubBranch() {
        val tracker = ReaderProgressTracker()

        // EPUB with currentLoc != null -> progress == prog, fallbackPosition == position ?: 0, saveProgressFirst == true
        val loc = createMockLocator(position = 12)
        val plan1 = tracker.planFlush(
            currentLoc = loc, prog = 0.65f, isReflowMode = false, isPdfBook = false,
            reflowFirstElementPage = null, reflowTotalPages = 0, totalPositions = 0,
            readingOrderSize = null, globalProgress = 0f
        )
        assertEquals(0.65f, plan1.progress, 0.001f)
        assertEquals(12, plan1.fallbackPosition)
        assertNull(plan1.pageToRemember)
        assertTrue(plan1.saveProgressFirst)

        // EPUB with currentLoc == null -> saveProgressFirst == false, fallbackPosition == 0
        val plan2 = tracker.planFlush(
            currentLoc = null, prog = 0.4f, isReflowMode = false, isPdfBook = false,
            reflowFirstElementPage = null, reflowTotalPages = 0, totalPositions = 0,
            readingOrderSize = null, globalProgress = 0f
        )
        assertEquals(0.4f, plan2.progress, 0.001f)
        assertEquals(0, plan2.fallbackPosition)
        assertNull(plan2.pageToRemember)
        assertFalse(plan2.saveProgressFirst)
    }
}
