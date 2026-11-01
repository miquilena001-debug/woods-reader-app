package com.example.woodsreader.ui.reader

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

class ReaderNavigationResolversTest {

    private fun createLocator(
        hrefStr: String = "chapter1.xhtml",
        position: Int? = null,
        progression: Double? = null
    ): Locator {
        val mockUrl = mockk<Url>(relaxed = true)
        every { mockUrl.toString() } returns hrefStr
        return Locator(
            href = mockUrl,
            mediaType = MediaType.HTML,
            locations = Locator.Locations(
                position = position,
                progression = progression
            )
        )
    }

    private fun createPublication(links: List<Link> = emptyList()): Publication {
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns links
        return pub
    }

    // ==========================================
    // 1. resolveSeekTarget - Reflow variant
    // ==========================================

    @Test
    fun testResolveSeekTarget_reflow_positivePages_returnsCalculatedPage() {
        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = true,
            reflowTotalPages = 100,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 100 }
        )
        assertTrue("Expected SeekTarget.Reflow", result is ReaderNavigationResolvers.SeekTarget.Reflow)
        assertEquals(50, (result as ReaderNavigationResolvers.SeekTarget.Reflow).page)
    }

    @Test
    fun testResolveSeekTarget_reflow_clampsToLowerAndUpperBounds() {
        // Lower bound with 0f
        val result0 = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0f,
            isReflowMode = true,
            reflowTotalPages = 10,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        ) as ReaderNavigationResolvers.SeekTarget.Reflow
        assertEquals(1, result0.page)

        // Upper bound with 1f
        val result1 = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 1f,
            isReflowMode = true,
            reflowTotalPages = 10,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        ) as ReaderNavigationResolvers.SeekTarget.Reflow
        assertEquals(10, result1.page)

        // N15: Negative percent coerced to 1
        val resultNeg = ReaderNavigationResolvers.resolveSeekTarget(
            percent = -1f,
            isReflowMode = true,
            reflowTotalPages = 10,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        ) as ReaderNavigationResolvers.SeekTarget.Reflow
        assertEquals(1, resultNeg.page)

        // N15: Overflow percent coerced to max
        val resultOver = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 2f,
            isReflowMode = true,
            reflowTotalPages = 10,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        ) as ReaderNavigationResolvers.SeekTarget.Reflow
        assertEquals(10, resultOver.page)
    }

    @Test
    fun testResolveSeekTarget_reflow_totalPagesZeroOrNegative_fallsThroughToNextBranches() {
        // N14 fidelity check: reflow with totalPages <= 0 does NOT return Reflow; it falls through
        // If EPUB positions are available:
        val loc = createLocator("chap.xhtml")
        val resultPositions = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = true,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 1,
            publication = null,
            positionsByResource = listOf(listOf(loc)),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected fallthrough to EpubPosition", resultPositions is ReaderNavigationResolvers.SeekTarget.EpubPosition)

        // If totalPositions <= 0, falls through to EPUB link fallback:
        val link = mockk<Link>(relaxed = true)
        val pub = createPublication(listOf(link))
        val fallbackLoc = createLocator("fallback.xhtml")
        every { pub.locatorFromLink(link) } returns fallbackLoc

        val resultLinks = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = true,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected fallthrough to EpubLink", resultLinks is ReaderNavigationResolvers.SeekTarget.EpubLink)

        // If no publication links either: None
        val resultNone = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = true,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected fallthrough to None", resultNone is ReaderNavigationResolvers.SeekTarget.None)
    }

    // ==========================================
    // 2. resolveSeekTarget - PdfOriginal variant
    // ==========================================

    @Test
    fun testResolveSeekTarget_pdfOriginal_withPublicationAndReadingOrder_returnsPdfOriginalWithLocator() {
        val link = mockk<Link>(relaxed = true)
        val pub = createPublication(listOf(link))
        val baseLoc = createLocator("doc.pdf", position = 1)
        every { pub.locatorFromLink(link) } returns baseLoc

        var lambdaCalledWith: Int? = null
        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = true,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = {
                lambdaCalledWith = it
                20
            }
        )

        assertEquals(1, lambdaCalledWith)
        assertTrue("Expected SeekTarget.PdfOriginal", result is ReaderNavigationResolvers.SeekTarget.PdfOriginal)
        val pdfTarget = result as ReaderNavigationResolvers.SeekTarget.PdfOriginal
        assertEquals(10, pdfTarget.page)
        assertNotNull(pdfTarget.locator)
        assertEquals(10, pdfTarget.locator?.locations?.position)
    }

    @Test
    fun testResolveSeekTarget_pdfOriginal_nullPublication_returnsPdfOriginalWithNullLocator() {
        // N12 fidelity check: VM still sets page and _globalProgress even if publication == null
        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.3f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = true,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        )
        assertTrue("Expected SeekTarget.PdfOriginal", result is ReaderNavigationResolvers.SeekTarget.PdfOriginal)
        val pdfTarget = result as ReaderNavigationResolvers.SeekTarget.PdfOriginal
        assertEquals(3, pdfTarget.page)
        assertNull("Locator must be null when publication is null", pdfTarget.locator)
    }

    @Test
    fun testResolveSeekTarget_pdfOriginal_emptyReadingOrder_returnsPdfOriginalWithNullLocator() {
        // N12 fidelity check: publication has empty readingOrder
        val pub = createPublication(emptyList())
        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.7f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = true,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        )
        assertTrue("Expected SeekTarget.PdfOriginal", result is ReaderNavigationResolvers.SeekTarget.PdfOriginal)
        val pdfTarget = result as ReaderNavigationResolvers.SeekTarget.PdfOriginal
        assertEquals(7, pdfTarget.page)
        assertNull("Locator must be null when readingOrder is empty", pdfTarget.locator)
    }

    @Test
    fun testResolveSeekTarget_pdfOriginal_clampsToBounds() {
        // Negative percent clamped to 1
        val resultNeg = ReaderNavigationResolvers.resolveSeekTarget(
            percent = -0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = true,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        ) as ReaderNavigationResolvers.SeekTarget.PdfOriginal
        assertEquals(1, resultNeg.page)

        // Overflow percent clamped to totalPages
        val resultOver = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 1.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = true,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 10 }
        ) as ReaderNavigationResolvers.SeekTarget.PdfOriginal
        assertEquals(10, resultOver.page)
    }

    // ==========================================
    // 3. resolveSeekTarget - EpubLink fallback
    // ==========================================

    @Test
    fun testResolveSeekTarget_epubFallback_withPublicationLinks_returnsEpubLink() {
        val link0 = mockk<Link>(relaxed = true)
        val link1 = mockk<Link>(relaxed = true)
        val link2 = mockk<Link>(relaxed = true)
        val pub = createPublication(listOf(link0, link1, link2))

        val loc1 = createLocator("chapter2.xhtml")
        every { pub.locatorFromLink(link1) } returns loc1

        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected SeekTarget.EpubLink", result is ReaderNavigationResolvers.SeekTarget.EpubLink)
        val epubTarget = result as ReaderNavigationResolvers.SeekTarget.EpubLink
        assertEquals(loc1, epubTarget.locator)
    }

    @Test
    fun testResolveSeekTarget_epubFallback_locatorFromLinkNull_returnsEpubLinkWithNullLocator() {
        // N13 fidelity check: locatorFromLink is null, still returns EpubLink(null)
        val link0 = mockk<Link>(relaxed = true)
        val pub = createPublication(listOf(link0))
        every { pub.locatorFromLink(link0) } returns null

        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected SeekTarget.EpubLink", result is ReaderNavigationResolvers.SeekTarget.EpubLink)
        val epubTarget = result as ReaderNavigationResolvers.SeekTarget.EpubLink
        assertNull("Expected null locator when locatorFromLink returns null", epubTarget.locator)
    }

    @Test
    fun testResolveSeekTarget_epubFallback_nullPublicationOrEmptyReadingOrder_returnsNone() {
        // publication is null
        val resultNullPub = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = null,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected SeekTarget.None for null publication", resultNullPub is ReaderNavigationResolvers.SeekTarget.None)

        // readingOrder is empty
        val emptyPub = createPublication(emptyList())
        val resultEmptyPub = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = emptyPub,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        )
        assertTrue("Expected SeekTarget.None for empty readingOrder", resultEmptyPub is ReaderNavigationResolvers.SeekTarget.None)
    }

    @Test
    fun testResolveSeekTarget_epubFallback_clampsBounds() {
        val link0 = mockk<Link>(relaxed = true)
        val link1 = mockk<Link>(relaxed = true)
        val pub = createPublication(listOf(link0, link1))

        val loc0 = createLocator("ch0.xhtml")
        val loc1 = createLocator("ch1.xhtml")
        every { pub.locatorFromLink(link0) } returns loc0
        every { pub.locatorFromLink(link1) } returns loc1

        // Negative percent coerced to 0
        val resultNeg = ReaderNavigationResolvers.resolveSeekTarget(
            percent = -1f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        ) as ReaderNavigationResolvers.SeekTarget.EpubLink
        assertEquals(loc0, resultNeg.locator)

        // Overflow percent coerced to size - 1
        val resultOver = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 2f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 0,
            publication = pub,
            positionsByResource = emptyList(),
            totalPdfPages = { 0 }
        ) as ReaderNavigationResolvers.SeekTarget.EpubLink
        assertEquals(loc1, resultOver.locator)
    }

    // ==========================================
    // 4. resolveSeekTarget - EpubPosition variant
    // ==========================================

    @Test
    fun testResolveSeekTarget_epubPositions_validPositions_returnsEpubPosition() {
        val loc0 = createLocator("ch1.xhtml", position = 1)
        val loc1 = createLocator("ch1.xhtml", position = 2)
        val loc2 = createLocator("ch2.xhtml", position = 3)
        val loc3 = createLocator("ch2.xhtml", position = 4)
        val loc4 = createLocator("ch2.xhtml", position = 5)

        val positions = listOf(listOf(loc0, loc1), listOf(loc2, loc3, loc4))

        // targetIdx for 0.6f * 5 = 3 -> second resource (accumulated 2), index 1 -> loc3
        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.6f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 5,
            publication = null,
            positionsByResource = positions,
            totalPdfPages = { 0 }
        )
        assertTrue("Expected SeekTarget.EpubPosition", result is ReaderNavigationResolvers.SeekTarget.EpubPosition)
        val posTarget = result as ReaderNavigationResolvers.SeekTarget.EpubPosition
        assertEquals(loc3, posTarget.locator)
    }

    @Test
    fun testResolveSeekTarget_epubPositions_clampsBounds() {
        // N15 limits
        val loc0 = createLocator("ch1.xhtml", position = 1)
        val loc1 = createLocator("ch1.xhtml", position = 2)
        val positions = listOf(listOf(loc0, loc1))

        // Negative percent coerced to index 0
        val resultNeg = ReaderNavigationResolvers.resolveSeekTarget(
            percent = -0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 2,
            publication = null,
            positionsByResource = positions,
            totalPdfPages = { 0 }
        ) as ReaderNavigationResolvers.SeekTarget.EpubPosition
        assertEquals(loc0, resultNeg.locator)

        // Overflow percent coerced to last index
        val resultOver = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 1.8f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 2,
            publication = null,
            positionsByResource = positions,
            totalPdfPages = { 0 }
        ) as ReaderNavigationResolvers.SeekTarget.EpubPosition
        assertEquals(loc1, resultOver.locator)
    }

    @Test
    fun testResolveSeekTarget_epubPositions_invalidIndices_returnsNone() {
        // Total positions mismatch or inner empty lists leading to invalid targetPosInResource
        val positionsWithEmpty = listOf(emptyList<Locator>())
        val result = ReaderNavigationResolvers.resolveSeekTarget(
            percent = 0.5f,
            isReflowMode = false,
            reflowTotalPages = 0,
            isPdfBook = false,
            totalPositions = 1,
            publication = null,
            positionsByResource = positionsWithEmpty,
            totalPdfPages = { 0 }
        )
        assertTrue("Expected SeekTarget.None for invalid indices", result is ReaderNavigationResolvers.SeekTarget.None)
    }

    // ==========================================
    // 5. resolveCurrentPdfPage tests
    // ==========================================

    @Test
    fun testResolveCurrentPdfPage_reflowMode_prefersLastKnownReflowPage() {
        // N16: In reflow, lastKnownReflowPage > 0 takes priority, visualPosition is ignored, newLastKnown is null
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = true,
            lastKnownReflowPage = 5,
            currentTtsPage = 2,
            visualPosition = 9
        )
        assertEquals("pageToUse should match lastKnownReflowPage", 5, resolution.pageToUse)
        assertNull("newLastKnownReflowPage must be null in reflow", resolution.newLastKnownReflowPage)
    }

    @Test
    fun testResolveCurrentPdfPage_reflowMode_fallsBackToCurrentTtsPage() {
        // N16: lastKnownReflowPage <= 0 falls back to currentTtsPage > 0
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = true,
            lastKnownReflowPage = 0,
            currentTtsPage = 4,
            visualPosition = null
        )
        assertEquals("pageToUse should fall back to currentTtsPage", 4, resolution.pageToUse)
        assertNull("newLastKnownReflowPage must be null in reflow", resolution.newLastKnownReflowPage)
    }

    @Test
    fun testResolveCurrentPdfPage_reflowMode_fallsBackToOneWhenAllZeroOrNegative() {
        // N16: Both <= 0 -> falls back to 1
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = true,
            lastKnownReflowPage = 0,
            currentTtsPage = 0,
            visualPosition = null
        )
        assertEquals("pageToUse should fall back to 1", 1, resolution.pageToUse)
        assertNull("newLastKnownReflowPage must be null in reflow", resolution.newLastKnownReflowPage)
    }

    @Test
    fun testResolveCurrentPdfPage_pdfOriginal_usesPositiveVisualPosition() {
        // N17: When visualPosition is positive, pageToUse = visualPosition, newLastKnownReflowPage = visualPosition
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = false,
            lastKnownReflowPage = 2,
            currentTtsPage = 1,
            visualPosition = 7
        )
        assertEquals("pageToUse should be visualPosition", 7, resolution.pageToUse)
        assertEquals("newLastKnownReflowPage should be raw visualPosition", 7, resolution.newLastKnownReflowPage)
    }

    @Test
    fun testResolveCurrentPdfPage_pdfOriginal_clampsNonPositiveVisualPositionToOne_butPreservesRawInNewLastKnown() {
        // N17: When visualPosition <= 0, pageToUse clamped to 1, but newLastKnownReflowPage retains raw value (-3)
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = false,
            lastKnownReflowPage = 5,
            currentTtsPage = 1,
            visualPosition = -3
        )
        assertEquals("pageToUse should clamp to 1", 1, resolution.pageToUse)
        assertEquals("newLastKnownReflowPage should preserve raw value", -3, resolution.newLastKnownReflowPage)
    }

    @Test
    fun testResolveCurrentPdfPage_pdfOriginal_fallsBackToLastKnownReflowPageWhenVisualPositionNull() {
        // N17: When visualPosition is null, visualPage falls back to lastKnownReflowPage
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = false,
            lastKnownReflowPage = 8,
            currentTtsPage = 1,
            visualPosition = null
        )
        assertEquals("pageToUse should fall back to lastKnownReflowPage", 8, resolution.pageToUse)
        assertEquals("newLastKnownReflowPage should be lastKnownReflowPage", 8, resolution.newLastKnownReflowPage)
    }

    @Test
    fun testResolveCurrentPdfPage_pdfOriginal_clampsNonPositiveFallbackToOne() {
        // N17: When visualPosition is null and lastKnownReflowPage <= 0, clamped to 1, newLastKnown is raw fallback
        val resolution = ReaderNavigationResolvers.resolveCurrentPdfPage(
            isReflowMode = false,
            lastKnownReflowPage = 0,
            currentTtsPage = 1,
            visualPosition = null
        )
        assertEquals("pageToUse should clamp to 1", 1, resolution.pageToUse)
        assertEquals("newLastKnownReflowPage should be 0", 0, resolution.newLastKnownReflowPage)
    }
}
