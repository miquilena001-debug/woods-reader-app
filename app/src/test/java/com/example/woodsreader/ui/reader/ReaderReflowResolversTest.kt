package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.ReflowItem
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

class ReaderReflowResolversTest {

    private fun createLocator(
        hrefStr: String = "book.pdf",
        position: Int? = 1,
        progression: Double? = null,
        totalProgression: Double? = null,
        fragments: List<String> = emptyList()
    ): Locator {
        val mockUrl = mockk<Url>(relaxed = true)
        every { mockUrl.toString() } returns hrefStr
        return Locator(
            href = mockUrl,
            mediaType = MediaType.PDF,
            locations = Locator.Locations(
                fragments = fragments,
                progression = progression,
                position = position,
                totalProgression = totalProgression
            )
        )
    }

    // --- 1. readableItems tests ---

    @Test
    fun testReadableItems_keepsHeaderParagraphAndText_andDropsOtherSubtypes() {
        val header = ReflowItem.Header("Header 1", level = 1, pageNumber = 1)
        val paragraph = ReflowItem.Paragraph("Paragraph text", pageNumber = 1)
        val text = ReflowItem.Text(element = mockk(relaxed = true), pageNumber = 1)
        val pageNum = ReflowItem.PageNumber("1", pageNumber = 1)
        val image = ReflowItem.Image(byteArrayOf(), pageNumber = 1)
        val ignored = ReflowItem.Ignored("noise", reason = "header", pageNumber = 1)

        val elements = listOf(pageNum, header, image, paragraph, ignored, text)
        val result = ReaderReflowResolvers.readableItems(elements)

        assertEquals(3, result.size)
        assertEquals(header, result[0])
        assertEquals(paragraph, result[1])
        assertEquals(text, result[2])
    }

    @Test
    fun testReadableItems_emptyInput_returnsEmptyList() {
        val result = ReaderReflowResolvers.readableItems(emptyList())
        assertTrue(result.isEmpty())
    }

    // --- 2. readableIndex tests ---

    @Test
    fun testReadableIndex_visibleItemIsReadable_returnsItsIndexAmongReadables() {
        val ignored = ReflowItem.Ignored("noise", reason = "header", pageNumber = 1)
        val p1 = ReflowItem.Paragraph("First paragraph", pageNumber = 1)
        val h1 = ReflowItem.Header("Chapter 1", level = 1, pageNumber = 1)

        val elements = listOf(ignored, p1, h1)
        val readables = ReaderReflowResolvers.readableItems(elements)

        // p1 is at elements index 1 -> readables index 0
        assertEquals(0, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 1))
        // h1 is at elements index 2 -> readables index 1
        assertEquals(1, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 2))
    }

    @Test
    fun testReadableIndex_visibleItemNotReadable_returnsNextReadableIndex() {
        val pageNum = ReflowItem.PageNumber("1", pageNumber = 1)
        val ignored = ReflowItem.Ignored("noise", reason = "header", pageNumber = 1)
        val p1 = ReflowItem.Paragraph("First paragraph", pageNumber = 1)
        val h1 = ReflowItem.Header("Chapter 1", level = 1, pageNumber = 1)

        val elements = listOf(pageNum, ignored, p1, h1)
        val readables = ReaderReflowResolvers.readableItems(elements)

        // Index 0 is PageNumber (not readable) -> next readable is p1 (index 0 in readables)
        assertEquals(0, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 0))
        // Index 1 is Ignored (not readable) -> next readable is p1 (index 0 in readables)
        assertEquals(0, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 1))
    }

    @Test
    fun testReadableIndex_noReadableAfterVisibleItem_returnsZero() {
        val p1 = ReflowItem.Paragraph("First paragraph", pageNumber = 1)
        val pageNum = ReflowItem.PageNumber("1", pageNumber = 1)
        val ignored = ReflowItem.Ignored("noise", reason = "footer", pageNumber = 1)

        val elements = listOf(p1, pageNum, ignored)
        val readables = ReaderReflowResolvers.readableItems(elements)

        // Index 1 has no readable items after it -> returns 0
        assertEquals(0, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 1))
        // Index 2 has no readable items after it -> returns 0
        assertEquals(0, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 2))
    }

    @Test
    fun testReadableIndex_elementIndexNegative_coercedToZero() {
        val p1 = ReflowItem.Paragraph("First paragraph", pageNumber = 1)
        val elements = listOf(p1)
        val readables = listOf(p1)

        assertEquals(0, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = -5))
    }

    @Test
    fun testReadableIndex_elementIndexBeyondSize_coercedToLastIndex() {
        val p1 = ReflowItem.Paragraph("First paragraph", pageNumber = 1)
        val p2 = ReflowItem.Paragraph("Second paragraph", pageNumber = 1)
        val elements = listOf(p1, p2)
        val readables = listOf(p1, p2)

        // Beyond size (99) coerced to index 1 -> readables index 1
        assertEquals(1, ReaderReflowResolvers.readableIndex(elements, readables, elementIndex = 99))
    }

    @Test
    fun testReadableIndex_emptyElements_returnsZero() {
        assertEquals(0, ReaderReflowResolvers.readableIndex(emptyList(), emptyList(), elementIndex = 0))
    }

    // --- 3. locatorForPdfPage: positions route ---

    @Test
    fun testLocatorForPdfPage_positionsRoute_insideFirstResource() {
        val loc1 = createLocator(position = 1, progression = 0.1, totalProgression = 0.1)
        val loc2 = createLocator(position = 2, progression = 0.2, totalProgression = 0.2)
        val positionsByResource = listOf(listOf(loc1, loc2))

        val pub = mockk<Publication>(relaxed = true)
        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 2,
            publication = pub,
            reflowTotalPages = 10,
            totalPositions = 2,
            positionsByResource = positionsByResource
        )

        assertNotNull(result)
        assertEquals(2, result?.locations?.position)
        assertEquals(listOf("page=2"), result?.locations?.fragments)
        assertEquals(0.2, result?.locations?.progression ?: 0.0, 0.001)
        assertEquals(0.2, result?.locations?.totalProgression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_positionsRoute_insideLaterResourceWithCountOffset() {
        val loc1 = createLocator(position = 1, progression = 0.1, totalProgression = 0.1)
        val loc2 = createLocator(position = 2, progression = 0.2, totalProgression = 0.2)
        val loc3 = createLocator(position = 3, progression = 0.3, totalProgression = 0.3)
        val loc4 = createLocator(position = 4, progression = 0.4, totalProgression = 0.4)
        val positionsByResource = listOf(listOf(loc1, loc2), listOf(loc3, loc4))

        val pub = mockk<Publication>(relaxed = true)
        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 3,
            publication = pub,
            reflowTotalPages = 10,
            totalPositions = 4,
            positionsByResource = positionsByResource
        )

        assertNotNull(result)
        assertEquals(3, result?.locations?.position)
        assertEquals(listOf("page=3"), result?.locations?.fragments)
        assertEquals(0.3, result?.locations?.progression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_positionsRoute_fallsBackProgressionWhenNull() {
        val loc1 = createLocator(position = 1, progression = null, totalProgression = null)
        val positionsByResource = listOf(listOf(loc1))

        val pub = mockk<Publication>(relaxed = true)
        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 1,
            publication = pub,
            reflowTotalPages = 10,
            totalPositions = 2,
            positionsByResource = positionsByResource
        )

        assertNotNull(result)
        // (1 - 1) / 10.0 = 0.0
        assertEquals(0.0, result?.locations?.progression ?: -1.0, 0.001)
        assertEquals(0.0, result?.locations?.totalProgression ?: -1.0, 0.001)
    }

    // --- 4. locatorForPdfPage: fallback route ---

    @Test
    fun testLocatorForPdfPage_fallbackRoute_usesReadingOrderPageMinusOne() {
        val link1 = mockk<Link>(relaxed = true)
        val link2 = mockk<Link>(relaxed = true)
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns listOf(link1, link2)

        val mockTargetLocator = createLocator(position = null, progression = null, totalProgression = null)
        every { pub.locatorFromLink(link2) } returns mockTargetLocator

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 2,
            publication = pub,
            reflowTotalPages = 4,
            totalPositions = 0,
            positionsByResource = emptyList()
        )

        assertNotNull(result)
        assertEquals(2, result?.locations?.position)
        assertEquals(listOf("page=2"), result?.locations?.fragments)
        // (2 - 1) / 4.0 = 0.25
        assertEquals(0.25, result?.locations?.progression ?: 0.0, 0.001)
        assertEquals(0.25, result?.locations?.totalProgression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_fallbackRoute_pageBeyondReadingOrder_fallsBackToFirstLink() {
        val link1 = mockk<Link>(relaxed = true)
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns listOf(link1)

        val mockLocator = createLocator(position = null, progression = null, totalProgression = null)
        every { pub.locatorFromLink(link1) } returns mockLocator

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 10,
            publication = pub,
            reflowTotalPages = 10,
            totalPositions = 0,
            positionsByResource = emptyList()
        )

        assertNotNull(result)
        assertEquals(10, result?.locations?.position)
        assertEquals(listOf("page=10"), result?.locations?.fragments)
        // (10 - 1) / 10.0 = 0.9
        assertEquals(0.9, result?.locations?.progression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_fallbackRoute_emptyReadingOrder_returnsNull() {
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns emptyList()

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 1,
            publication = pub,
            reflowTotalPages = 10,
            totalPositions = 0,
            positionsByResource = emptyList()
        )

        assertNull(result)
    }

    // --- 5. totalPages chain tests ---

    @Test
    fun testLocatorForPdfPage_totalPagesChain_prefersReflowTotalPages() {
        val link1 = mockk<Link>(relaxed = true)
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns listOf(link1)
        every { pub.locatorFromLink(link1) } returns createLocator()

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 11,
            publication = pub,
            reflowTotalPages = 20,
            totalPositions = 50,
            positionsByResource = emptyList()
        )

        assertNotNull(result)
        // (11 - 1) / 20.0 = 0.5
        assertEquals(0.5, result?.locations?.progression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_totalPagesChain_fallsBackToTotalPositions() {
        val link1 = mockk<Link>(relaxed = true)
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns listOf(link1)
        every { pub.locatorFromLink(link1) } returns createLocator()

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 26,
            publication = pub,
            reflowTotalPages = 1, // <= 1 ignored
            totalPositions = 50,
            positionsByResource = emptyList()
        )

        assertNotNull(result)
        // (26 - 1) / 50.0 = 0.5
        assertEquals(0.5, result?.locations?.progression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_totalPagesChain_fallsBackToReadingOrderSize() {
        val links = List(10) { mockk<Link>(relaxed = true) }
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns links
        every { pub.locatorFromLink(any()) } returns createLocator()

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 6,
            publication = pub,
            reflowTotalPages = 0,
            totalPositions = 0,
            positionsByResource = emptyList()
        )

        assertNotNull(result)
        // (6 - 1) / 10.0 = 0.5
        assertEquals(0.5, result?.locations?.progression ?: 0.0, 0.001)
    }

    @Test
    fun testLocatorForPdfPage_totalPagesChain_defaultsToOneAndClampsProgression() {
        val link1 = mockk<Link>(relaxed = true)
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns listOf(link1)
        every { pub.locatorFromLink(link1) } returns createLocator()

        val result = ReaderReflowResolvers.locatorForPdfPage(
            page = 5,
            publication = pub,
            reflowTotalPages = 0,
            totalPositions = 1, // <= 1 ignored
            positionsByResource = emptyList() // readingOrder size is 1, so <= 1 ignored -> defaults to 1
        )

        assertNotNull(result)
        // (5 - 1) / 1.0 = 4.0 coerced to 1.0
        assertEquals(1.0, result?.locations?.progression ?: 0.0, 0.001)
    }
}
