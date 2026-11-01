package com.example.woodsreader.ui.reader

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import java.io.File

class ReaderLoadResolversTest {

    // 1. pdfPhysicalPath
    @Test
    fun testPdfPhysicalPath_absolutePathWins() {
        val result = ReaderLoadResolvers.pdfPhysicalPath(
            filePath = "/storage/emulated/0/book.pdf",
            uriPath = "/content/uri/path.pdf"
        )
        assertEquals("/storage/emulated/0/book.pdf", result)
    }

    @Test
    fun testPdfPhysicalPath_relativeUsesUriPathWhenAvailable() {
        val result = ReaderLoadResolvers.pdfPhysicalPath(
            filePath = "relative/book.pdf",
            uriPath = "/content/uri/path.pdf"
        )
        assertEquals("/content/uri/path.pdf", result)
    }

    @Test
    fun testPdfPhysicalPath_relativeFallsBackToFileUriWhenUriPathNull() {
        val result = ReaderLoadResolvers.pdfPhysicalPath(
            filePath = "relative/book.pdf",
            uriPath = null
        )
        assertEquals("relative/book.pdf", result)
    }

    // 2. initialPdfPage
    @Test
    fun testInitialPdfPage_initialPdfPageWins() {
        val result = ReaderLoadResolvers.initialPdfPage(
            initialPdfPage = 5,
            fallbackLocatorPosition = 10
        )
        assertEquals(5, result)
    }

    @Test
    fun testInitialPdfPage_fallbackPositionWinsWhenInitialNullAndPositive() {
        val result = ReaderLoadResolvers.initialPdfPage(
            initialPdfPage = null,
            fallbackLocatorPosition = 7
        )
        assertEquals(7, result)
    }

    @Test
    fun testInitialPdfPage_zeroFallbackPositionDefaultsToOne() {
        val result = ReaderLoadResolvers.initialPdfPage(
            initialPdfPage = null,
            fallbackLocatorPosition = 0
        )
        assertEquals(1, result)
    }

    @Test
    fun testInitialPdfPage_negativeFallbackPositionDefaultsToOne() {
        val result = ReaderLoadResolvers.initialPdfPage(
            initialPdfPage = null,
            fallbackLocatorPosition = -3
        )
        assertEquals(1, result)
    }

    @Test
    fun testInitialPdfPage_bothNullDefaultsToOne() {
        val result = ReaderLoadResolvers.initialPdfPage(
            initialPdfPage = null,
            fallbackLocatorPosition = null
        )
        assertEquals(1, result)
    }

    // 3. initialRecordedProgression
    @Test
    fun testInitialRecordedProgression_locatorTotalProgressionWins() {
        val locator = mockk<Locator>()
        val locations = mockk<Locator.Locations>()
        every { locations.totalProgression } returns 0.42
        every { locator.locations } returns locations

        val result = ReaderLoadResolvers.initialRecordedProgression(locator, 0.1f)
        assertEquals(0.42, result, 0.0001)
    }

    @Test
    fun testInitialRecordedProgression_locatorZeroTotalProgressionWins() {
        val locator = mockk<Locator>()
        val locations = mockk<Locator.Locations>()
        every { locations.totalProgression } returns 0.0
        every { locator.locations } returns locations

        val result = ReaderLoadResolvers.initialRecordedProgression(locator, 0.5f)
        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun testInitialRecordedProgression_nullTotalProgressionFallsBackToReadingProgressPercent() {
        val locator = mockk<Locator>()
        val locations = mockk<Locator.Locations>()
        every { locations.totalProgression } returns null
        every { locator.locations } returns locations

        val result = ReaderLoadResolvers.initialRecordedProgression(locator, 0.35f)
        assertEquals(0.35f.toDouble(), result, 0.000001)
    }

    @Test
    fun testInitialRecordedProgression_nullLocatorFallsBackToReadingProgressPercent() {
        val result = ReaderLoadResolvers.initialRecordedProgression(null, 0.65f)
        assertEquals(0.65f.toDouble(), result, 0.000001)
    }

    // 4. initialGlobalProgress
    @Test
    fun testInitialGlobalProgress_clampsBelowZero() {
        val result = ReaderLoadResolvers.initialGlobalProgress(-0.5f)
        assertEquals(0f, result, 0.0f)
    }

    @Test
    fun testInitialGlobalProgress_preservesValidRange() {
        val result = ReaderLoadResolvers.initialGlobalProgress(0.4f)
        assertEquals(0.4f, result, 0.0f)
    }

    @Test
    fun testInitialGlobalProgress_clampsAboveOne() {
        val result = ReaderLoadResolvers.initialGlobalProgress(1.7f)
        assertEquals(1f, result, 0.0f)
    }

    // 5. isCoverMissing
    @Test
    fun testIsCoverMissing_nullPathReturnsTrue() {
        assertTrue(ReaderLoadResolvers.isCoverMissing(null))
    }

    @Test
    fun testIsCoverMissing_emptyPathReturnsTrue() {
        assertTrue(ReaderLoadResolvers.isCoverMissing(""))
    }

    @Test
    fun testIsCoverMissing_blankPathReturnsTrue() {
        assertTrue(ReaderLoadResolvers.isCoverMissing("   "))
    }

    @Test
    fun testIsCoverMissing_nonExistentPathReturnsTrue() {
        assertTrue(ReaderLoadResolvers.isCoverMissing("non_existent_cover_file_xyz_123.jpg"))
    }

    @Test
    fun testIsCoverMissing_existingFileReturnsFalse() {
        val temp = File.createTempFile("test_cover", ".jpg")
        try {
            assertFalse(ReaderLoadResolvers.isCoverMissing(temp.absolutePath))
        } finally {
            temp.delete()
        }
    }
}
