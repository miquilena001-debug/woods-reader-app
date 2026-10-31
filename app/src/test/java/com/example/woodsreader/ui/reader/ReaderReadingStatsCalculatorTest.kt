package com.example.woodsreader.ui.reader

import com.example.woodsreader.domain.reader.CalculateReadingStatsUseCase
import com.example.woodsreader.domain.reader.ChapterProgressResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

class ReaderReadingStatsCalculatorTest {

    private val statsUseCase = mockk<CalculateReadingStatsUseCase>(relaxed = true)
    private var resolverLambdaCalled = false
    private var resolvedTitleValue: String? = "Resolved Chapter Title"

    private val calculator = ReaderReadingStatsCalculator(
        statsUseCase = statsUseCase,
        resolveChapterTitle = { pub, href, page ->
            resolverLambdaCalled = true
            resolvedTitleValue
        }
    )

    private fun createMockLocator(
        hrefStr: String = "chapter1.xhtml",
        title: String? = null,
        position: Int? = null,
        progression: Double? = null,
        totalProgression: Double? = null
    ): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val url = mockk<Url>(relaxed = true)
        every { url.toString() } returns hrefStr
        every { locator.href } returns url
        every { locator.title } returns title

        val locations = mockk<Locator.Locations>(relaxed = true)
        every { locations.position } returns position
        every { locations.progression } returns progression
        every { locations.totalProgression } returns totalProgression
        every { locator.locations } returns locations

        return locator
    }

    private fun createMockLink(hrefStr: String): Link {
        val link = mockk<Link>(relaxed = true)
        val href = mockk<Href>(relaxed = true)
        every { href.toString() } returns hrefStr
        every { link.href } returns href
        return link
    }

    private fun createMockPublication(readingOrderHrefs: List<String>): Publication {
        val pub = mockk<Publication>(relaxed = true)
        val links = readingOrderHrefs.map { createMockLink(it) }
        every { pub.readingOrder } returns links
        return pub
    }

    @Test
    fun testReadingStatsReturnsBookTimeRemainingAndPassesCorrectArgs() {
        val locator = createMockLocator(totalProgression = 0.45)
        every {
            statsUseCase.calculateBookTimeRemaining(50_000L, 0.45, 250)
        } returns "2h 15m"

        val result = calculator.readingStats(
            locator = locator,
            activeWpm = 250,
            totalWords = 50_000L,
            totalPositions = 0,
            positionsByResource = emptyList(),
            resourceIndexCache = null,
            publication = null
        )

        assertEquals("2h 15m", result.bookTimeRemaining)
        verify { statsUseCase.calculateBookTimeRemaining(50_000L, 0.45, 250) }
    }

    @Test
    fun testReadingStatsChapterProgressNeverCalledWhenTotalPositionsZeroOrPublicationNull() {
        val locator = createMockLocator(hrefStr = "chap1.xhtml", totalProgression = 0.1)
        val pub = createMockPublication(listOf("chap1.xhtml"))

        // Case 1: totalPositions == 0, publication != null
        val res1 = calculator.readingStats(
            locator = locator,
            activeWpm = 200,
            totalWords = 100_000L,
            totalPositions = 0,
            positionsByResource = listOf(listOf(locator)),
            resourceIndexCache = null,
            publication = pub
        )
        assertNull(res1.chapterStats)
        verify(exactly = 0) { statsUseCase.calculateChapterProgress(any(), any(), any(), any(), any()) }

        // Case 2: totalPositions > 0, publication == null
        val res2 = calculator.readingStats(
            locator = locator,
            activeWpm = 200,
            totalWords = 100_000L,
            totalPositions = 5,
            positionsByResource = listOf(listOf(locator)),
            resourceIndexCache = null,
            publication = null
        )
        assertNull(res2.chapterStats)
        verify(exactly = 0) { statsUseCase.calculateChapterProgress(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testReadingStatsResourceIndexCacheHitFallbackAndNotFound() {
        val pub = createMockPublication(listOf("chap1.xhtml", "chap2.xhtml"))
        val chapterProgress = ChapterProgressResult(chapterTimeRemaining = "10m", globalProgress = 0.25f)
        every { statsUseCase.calculateChapterProgress(any(), any(), any(), any(), any()) } returns chapterProgress

        // 1. Cache hit is used as-is
        val loc1 = createMockLocator(hrefStr = "chap2.xhtml", progression = 0.3)
        calculator.readingStats(
            locator = loc1,
            activeWpm = 200,
            totalWords = 10_000L,
            totalPositions = 20,
            positionsByResource = emptyList(),
            resourceIndexCache = mapOf("chap2.xhtml" to 7),
            publication = pub
        )
        verify { statsUseCase.calculateChapterProgress(0.3, 7, any(), 20, 200) }

        // 2. Cache miss falls back to readingOrder.indexOfFirst
        val loc2 = createMockLocator(hrefStr = "chap2.xhtml", progression = 0.4)
        calculator.readingStats(
            locator = loc2,
            activeWpm = 200,
            totalWords = 10_000L,
            totalPositions = 20,
            positionsByResource = emptyList(),
            resourceIndexCache = emptyMap(),
            publication = pub
        )
        verify { statsUseCase.calculateChapterProgress(0.4, 1, any(), 20, 200) }

        // 3. Not found passes -1
        val loc3 = createMockLocator(hrefStr = "chap_unknown.xhtml", progression = 0.5)
        calculator.readingStats(
            locator = loc3,
            activeWpm = 200,
            totalWords = 10_000L,
            totalPositions = 20,
            positionsByResource = emptyList(),
            resourceIndexCache = null,
            publication = pub
        )
        verify { statsUseCase.calculateChapterProgress(0.5, -1, any(), 20, 200) }
    }

    @Test
    fun testChapterInfoTitleResolution() {
        val pub = createMockPublication(listOf("chap1.xhtml"))

        // 1. Non-blank locator.title wins, resolver lambda is NOT called
        resolverLambdaCalled = false
        val locWithTitle = createMockLocator(hrefStr = "chap1.xhtml", title = "Explicit Title")
        val info1 = calculator.chapterInfo(
            locator = locWithTitle,
            publication = pub,
            positionsByResource = emptyList(),
            pageFallback = null
        )
        assertEquals("Explicit Title", info1.title)
        assertEquals(false, resolverLambdaCalled)

        // 2. Blank or null title calls resolver lambda
        resolverLambdaCalled = false
        resolvedTitleValue = "Resolved Title"
        val locNullTitle = createMockLocator(hrefStr = "chap1.xhtml", title = null, position = 3)
        val info2 = calculator.chapterInfo(
            locator = locNullTitle,
            publication = pub,
            positionsByResource = emptyList(),
            pageFallback = null
        )
        assertEquals("Resolved Title", info2.title)
        assertEquals(true, resolverLambdaCalled)

        // 3. publication == null -> title is null
        val info3 = calculator.chapterInfo(
            locator = locNullTitle,
            publication = null,
            positionsByResource = emptyList(),
            pageFallback = null
        )
        assertNull(info3.title)
    }

    @Test
    fun testChapterInfoTimeRemainingAndProgress() {
        val pub = createMockPublication(listOf("chap1.xhtml", "chap2.xhtml"))
        val tenPositions = List(10) { createMockLocator("chap1.xhtml") }
        val positionsByResource = listOf(tenPositions)

        // 1. 10 positions, progression 0.5 -> remainingInResource = 5, (5 * 0.8).toInt() = 4
        val loc1 = createMockLocator("chap1.xhtml", progression = 0.5)
        val info1 = calculator.chapterInfo(loc1, pub, positionsByResource, pageFallback = null)
        assertEquals(4, info1.timeRemainingMinutes)
        assertEquals(0.5f, info1.progress, 0.001f)

        // 2. 10 positions, progression 1.0 -> remainingInResource = 0, coerceAtLeast(1) = 1
        val loc2 = createMockLocator("chap1.xhtml", progression = 1.0)
        val info2 = calculator.chapterInfo(loc2, pub, positionsByResource, pageFallback = null)
        assertEquals(1, info2.timeRemainingMinutes)
        assertEquals(1.0f, info2.progress, 0.001f)

        // 3. resource index -1 -> null
        val locUnknown = createMockLocator("chap_unknown.xhtml", progression = 0.5)
        val info3 = calculator.chapterInfo(locUnknown, pub, positionsByResource, pageFallback = null)
        assertNull(info3.timeRemainingMinutes)

        // 4. resource index >= positionsByResource.size -> null
        val locOutOfBounds = createMockLocator("chap2.xhtml", progression = 0.5)
        val info4 = calculator.chapterInfo(locOutOfBounds, pub, positionsByResource, pageFallback = null)
        assertNull(info4.timeRemainingMinutes)
    }

    @Test
    fun testChapterInfoPageNumPrecedence() {
        var capturedPage: Int? = null
        val customCalc = ReaderReadingStatsCalculator(
            statsUseCase = statsUseCase,
            resolveChapterTitle = { _, _, page ->
                capturedPage = page
                "Title"
            }
        )
        val pub = createMockPublication(listOf("chap1.xhtml"))

        // 1. locator.position wins over pageFallback
        val locWithPos = createMockLocator(hrefStr = "chap1.xhtml", position = 7)
        customCalc.chapterInfo(locWithPos, pub, emptyList(), pageFallback = 3)
        assertEquals(7, capturedPage)

        // 2. locator.position is null -> uses pageFallback
        val locNoPos = createMockLocator(hrefStr = "chap1.xhtml", position = null)
        customCalc.chapterInfo(locNoPos, pub, emptyList(), pageFallback = 3)
        assertEquals(3, capturedPage)
    }
}
