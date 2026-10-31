package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.ReflowItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

class ReaderTtsPlaybackCoordinatorTest {

    private val urlCache = mutableMapOf<String, Url>()

    private fun createLocator(
        hrefStr: String = "book.pdf",
        position: Int? = 1,
        progression: Double? = null,
        totalProgression: Double? = null,
        highlight: String? = null
    ): Locator {
        val mockUrl = urlCache.getOrPut(hrefStr) {
            val u = mockk<Url>(relaxed = true)
            every { u.toString() } returns hrefStr
            u
        }
        return Locator(
            href = mockUrl,
            mediaType = MediaType.PDF,
            locations = Locator.Locations(
                fragments = emptyList(),
                progression = progression,
                position = position,
                totalProgression = totalProgression
            ),
            text = Locator.Text(highlight = highlight)
        )
    }

    // --- 1. Initial state ---

    @Test
    fun testInitialState_hasNullPausedIndex_andFalseUserScrolled() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        assertNull(coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    // --- 2. onPaused and onStopped ---

    @Test
    fun testOnPaused_and_onStopped_transitions() {
        val coordinator = ReaderTtsPlaybackCoordinator()

        // Pause at block 5
        coordinator.onPaused(activeBlockIndex = 5)
        assertEquals(5, coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)

        // Stop resets both
        coordinator.onStopped()
        assertNull(coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    // --- 3. onReflowScrolled ---

    @Test
    fun testOnReflowScrolled_sameIndex_doesNotMarkScroll() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        coordinator.onPaused(2)

        coordinator.onReflowScrolled(previousIndex = 1, newIndex = 1)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    @Test
    fun testOnReflowScrolled_differentIndex_withoutPause_doesNotMarkScroll() {
        val coordinator = ReaderTtsPlaybackCoordinator()

        coordinator.onReflowScrolled(previousIndex = 1, newIndex = 2)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    @Test
    fun testOnReflowScrolled_differentIndex_withPause_marksScroll() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        coordinator.onPaused(2)

        coordinator.onReflowScrolled(previousIndex = 1, newIndex = 2)
        assertTrue(coordinator.userScrolledSinceTtsPause)
    }

    // --- 4. consumeReflowResumeIndex ---

    @Test
    fun testConsumeReflowResumeIndex_withUserScrolled_returnsVisibleIndex_andResets() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        coordinator.onPaused(3)
        coordinator.onReflowScrolled(1, 2) // userScrolledSinceTtsPause = true

        val result = coordinator.consumeReflowResumeIndex(
            visibleReadableIdx = 0,
            readableCount = 10,
            activeBlockIndex = { throw AssertionError("Should not be called") }
        )

        assertEquals(0, result)
        assertNull(coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    @Test
    fun testConsumeReflowResumeIndex_withoutPause_returnsVisibleIndex_andResets() {
        val coordinator = ReaderTtsPlaybackCoordinator()

        val result = coordinator.consumeReflowResumeIndex(
            visibleReadableIdx = 4,
            readableCount = 10,
            activeBlockIndex = { throw AssertionError("Should not be called") }
        )

        assertEquals(4, result)
        assertNull(coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    @Test
    fun testConsumeReflowResumeIndex_validPausedIndex_returnsPausedIndex_andDoesNotInvokeActiveLambda() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        coordinator.onPaused(3)

        var lambdaCalled = false
        val result = coordinator.consumeReflowResumeIndex(
            visibleReadableIdx = 0,
            readableCount = 10,
            activeBlockIndex = {
                lambdaCalled = true
                99
            }
        )

        assertEquals(3, result)
        assertFalse(lambdaCalled)
        assertNull(coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    @Test
    fun testConsumeReflowResumeIndex_outOfRangePausedIndex_returnsVisibleIndex() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        coordinator.onPaused(15) // > readableCount (10)

        val result = coordinator.consumeReflowResumeIndex(
            visibleReadableIdx = 2,
            readableCount = 10,
            activeBlockIndex = { 15 }
        )

        assertEquals(2, result)
        assertNull(coordinator.pausedAtIndex)
        assertFalse(coordinator.userScrolledSinceTtsPause)
    }

    // --- 5. resolveReflowPlayStartIndex ---

    @Test
    fun testResolveReflowPlayStartIndex_targetInCache_returnsTargetIndex() = runTest {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val loc = createLocator(position = 2)

        val p1 = ReflowItem.Paragraph("P1", pageNumber = 1)
        val p2 = ReflowItem.Paragraph("P2", pageNumber = 2)
        val items = listOf(p1, p2)

        var loadPagesCalled = false
        val index = coordinator.resolveReflowPlayStartIndex(
            locator = loc,
            fallbackPage = 1,
            items = { items },
            loadPages = { loadPagesCalled = true }
        )

        assertEquals(1, index)
        assertFalse(loadPagesCalled)
    }

    @Test
    fun testResolveReflowPlayStartIndex_targetNotInCache_callsLoadPages_andRecalculates() = runTest {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val loc = createLocator(position = 3)

        val p1 = ReflowItem.Paragraph("P1", pageNumber = 1)
        val p2 = ReflowItem.Paragraph("P2", pageNumber = 2)
        val p3 = ReflowItem.Paragraph("P3", pageNumber = 3)

        var currentItems = listOf(p1, p2)
        var loadedPage = -1

        val index = coordinator.resolveReflowPlayStartIndex(
            locator = loc,
            fallbackPage = 1,
            items = { currentItems },
            loadPages = { page ->
                loadedPage = page
                currentItems = listOf(p1, p2, p3)
            }
        )

        assertEquals(3, loadedPage)
        assertEquals(2, index)
    }

    @Test
    fun testResolveReflowPlayStartIndex_fallbackPage_coercedTo1_whenPositionNull() = runTest {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val loc = createLocator(position = null) // position null

        val p1 = ReflowItem.Paragraph("P1", pageNumber = 1)
        val items = listOf(p1)

        val index = coordinator.resolveReflowPlayStartIndex(
            locator = loc,
            fallbackPage = 0, // 0 should be coerced to 1
            items = { items },
            loadPages = {}
        )

        assertEquals(0, index)
    }

    @Test
    fun testResolveReflowPlayStartIndex_snippetRefinement_matchesTextHeaderAndParagraph() = runTest {
        val coordinator = ReaderTtsPlaybackCoordinator()

        // Match in Paragraph
        val p1 = ReflowItem.Paragraph("The quick brown fox", pageNumber = 1)
        val p2 = ReflowItem.Paragraph("jumps over the lazy dog", pageNumber = 1)
        val locPara = createLocator(position = 1, highlight = "lazy dog")
        val indexPara = coordinator.resolveReflowPlayStartIndex(
            locator = locPara,
            fallbackPage = 1,
            items = { listOf(p1, p2) },
            loadPages = {}
        )
        assertEquals(1, indexPara)

        // Match in Header
        val h1 = ReflowItem.Header("Chapter 1: The Beginning", level = 1, pageNumber = 1)
        val locHeader = createLocator(position = 1, highlight = "The Beginning")
        val indexHeader = coordinator.resolveReflowPlayStartIndex(
            locator = locHeader,
            fallbackPage = 1,
            items = { listOf(p1, h1) },
            loadPages = {}
        )
        assertEquals(1, indexHeader)

        // Match in Text element
        val mockElement = mockk<Content.Element>(relaxed = true)
        every { mockElement.locator.text.highlight } returns "Sub-element highlight snippet"
        val t1 = ReflowItem.Text(element = mockElement, pageNumber = 1)
        val locText = createLocator(position = 1, highlight = "highlight snippet")
        val indexText = coordinator.resolveReflowPlayStartIndex(
            locator = locText,
            fallbackPage = 1,
            items = { listOf(p1, t1) },
            loadPages = {}
        )
        assertEquals(1, indexText)

        // No match found -> keeps page targetIndex (0)
        val locNoMatch = createLocator(position = 1, highlight = "non-existent keyword")
        val indexNoMatch = coordinator.resolveReflowPlayStartIndex(
            locator = locNoMatch,
            fallbackPage = 1,
            items = { listOf(p1, p2) },
            loadPages = {}
        )
        assertEquals(0, indexNoMatch)

        // Blank / whitespace snippet -> ignored, keeps page targetIndex (0)
        val locBlank = createLocator(position = 1, highlight = "    ")
        val indexBlank = coordinator.resolveReflowPlayStartIndex(
            locator = locBlank,
            fallbackPage = 1,
            items = { listOf(p1, p2) },
            loadPages = {}
        )
        assertEquals(0, indexBlank)
    }

    // --- 6. shouldReplayFromVisual ---

    @Test
    fun testShouldReplayFromVisual_nullVisualLoc_returnsFalse() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val ttsLoc = createLocator(hrefStr = "c1.html", progression = 0.1)

        assertFalse(coordinator.shouldReplayFromVisual(null, ttsLoc))
    }

    @Test
    fun testShouldReplayFromVisual_sameHref_smallDiff_returnsFalse() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val visualLoc = createLocator(hrefStr = "c1.html", progression = 0.103)
        val ttsLoc = createLocator(hrefStr = "c1.html", progression = 0.100)

        // diff = 0.003 <= 0.005 -> false
        assertFalse(coordinator.shouldReplayFromVisual(visualLoc, ttsLoc))
    }

    @Test
    fun testShouldReplayFromVisual_sameHref_largeDiff_returnsTrue() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val visualLoc = createLocator(hrefStr = "c1.html", progression = 0.110)
        val ttsLoc = createLocator(hrefStr = "c1.html", progression = 0.100)

        // diff = 0.010 > 0.005 -> true
        assertTrue(coordinator.shouldReplayFromVisual(visualLoc, ttsLoc))
    }

    @Test
    fun testShouldReplayFromVisual_differentHref_returnsTrue() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val visualLoc = createLocator(hrefStr = "c2.html", progression = 0.100)
        val ttsLoc = createLocator(hrefStr = "c1.html", progression = 0.100)

        // different href -> true
        assertTrue(coordinator.shouldReplayFromVisual(visualLoc, ttsLoc))
    }

    @Test
    fun testShouldReplayFromVisual_nullProgressions_treatedAsZero() {
        val coordinator = ReaderTtsPlaybackCoordinator()
        val visualLoc = createLocator(hrefStr = "c1.html", progression = null)
        val ttsLoc = createLocator(hrefStr = "c1.html", progression = null)

        // same href, null progressions (diff 0.0 <= 0.005) -> false
        assertFalse(coordinator.shouldReplayFromVisual(visualLoc, ttsLoc))
    }
}
