package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSnifferTest {

    @Test
    fun `sniff detects JPEG magic bytes`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val sniffed = ImageSniffer.sniff(jpegHeader)

        assertNotNull(sniffed)
        assertEquals("image/jpeg", sniffed?.mimeType)
        assertEquals("jpg", sniffed?.extension)
    }

    @Test
    fun `sniff detects PNG magic bytes`() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            0x00, 0x00, 0x00, 0x0D
        )
        val sniffed = ImageSniffer.sniff(pngHeader)

        assertNotNull(sniffed)
        assertEquals("image/png", sniffed?.mimeType)
        assertEquals("png", sniffed?.extension)
    }

    @Test
    fun `sniff detects GIF magic bytes`() {
        val gifHeader = "GIF89aSampleImageData".toByteArray(Charsets.US_ASCII)
        val sniffed = ImageSniffer.sniff(gifHeader)

        assertNotNull(sniffed)
        assertEquals("image/gif", sniffed?.mimeType)
        assertEquals("gif", sniffed?.extension)
    }

    @Test
    fun `sniff detects WEBP magic bytes`() {
        val webpHeader = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x24, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
            0x00, 0x00
        )
        val sniffed = ImageSniffer.sniff(webpHeader)

        assertNotNull(sniffed)
        assertEquals("image/webp", sniffed?.mimeType)
        assertEquals("webp", sniffed?.extension)
    }

    @Test
    fun `sniff returns null on unknown bytes or html`() {
        val htmlBytes = "<!DOCTYPE html><html>".toByteArray(Charsets.UTF_8)
        val sniffed = ImageSniffer.sniff(htmlBytes)
        assertNull(sniffed)
    }

    @Test
    fun `sha1Name creates deterministic extension-aware path`() {
        val url = "https://example.com/cover.jpg"
        val name1 = ImageSniffer.sha1Name(url, "jpg")
        val name2 = ImageSniffer.sha1Name(url, "jpg")

        assertEquals(name1, name2)
        assertTrue(name1.endsWith(".jpg"))
        assertEquals(44, name1.length) // 40 sha1 hex chars + .jpg
    }

    @Test
    fun `budget tracker enforces per-image and total budget`() {
        val tracker = ImageBudgetTracker(maxBytesTotal = 1000L, maxBytesPerImage = 500L, maxImagesPerChapter = 2)

        assertTrue(tracker.canDownload(400L))
        assertFalse(tracker.canDownload(600L)) // Excede límite individual

        assertTrue(tracker.recordDownload(400L))
        assertEquals(600L, tracker.getRemainingBytes())

        assertTrue(tracker.recordDownload(400L))
        assertEquals(200L, tracker.getRemainingBytes())

        // Ya van 800L gastados, intentar gastar 300L superaría los 1000L
        assertFalse(tracker.recordDownload(300L))
    }
}
