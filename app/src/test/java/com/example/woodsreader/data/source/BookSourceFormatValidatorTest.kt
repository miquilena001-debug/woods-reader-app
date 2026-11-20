package com.example.woodsreader.data.source

import com.example.woodsreader.data.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookSourceFormatValidatorTest {
    @Test
    fun `detects PDF by its content and closes the stream`() {
        val input = TrackedInputStream("%PDF-1.7\n".toByteArray())

        assertEquals(BookFormat.PDF, BookSourceFormatValidator.detectFormat(input))
        assertTrue(input.closed)
    }

    @Test
    fun `accepts a structurally valid EPUB without relying on a filename`() {
        assertEquals(BookFormat.EPUB, detect(epubBytes()))
    }

    @Test
    fun `rejects ZIP archives without EPUB container metadata`() {
        assertEquals(BookFormat.UNKNOWN, detect(zipBytes("not-epub.txt" to "payload".toByteArray())))
    }

    @Test
    fun `rejects EPUB-like ZIP with a malformed container and closes the stream`() {
        val input = TrackedInputStream(
            zipBytes(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to "<container/>".toByteArray()
            )
        )

        assertEquals(null, BookSourceFormatValidator.detectFormat(input))
        assertTrue(input.closed)
    }

    @Test
    fun `rejects corrupted required EPUB package entries`() {
        val corrupt = epubBytes()
        val marker = "<rootfile".toByteArray()
        val markerOffset = corrupt.indexOfSubArray(marker)
        assertTrue(markerOffset >= 0)
        corrupt[markerOffset] = '>'.code.toByte()

        assertEquals(null, BookSourceFormatValidator.detectFormat(ByteArrayInputStream(corrupt)))
    }

    @Test
    fun `URI fallback markers are not accepted as content hashes`() {
        assertEquals(true, BookContentHash.isContentAddress("a".repeat(64)))
        assertEquals(false, BookContentHash.isContentAddress("uri_123"))
        assertEquals(false, BookContentHash.isContentAddress(""))
    }

    private fun detect(bytes: ByteArray): BookFormat =
        BookSourceFormatValidator.detectFormat(ByteArrayInputStream(bytes)) ?: BookFormat.UNKNOWN

    private fun epubBytes(): ByteArray = zipBytes(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to
            """<?xml version="1.0"?><container><rootfile full-path="OEBPS/content.opf"/></container>"""
                .toByteArray()
    )

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEachIndexed { index, (name, bytes) ->
                val entry = ZipEntry(name)
                if (name == "mimetype" || name == "META-INF/container.xml") {
                    val crc = CRC32().apply { update(bytes) }
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = crc.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private class TrackedInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
        for (start in 0..size - needle.size) {
            if (needle.indices.all { this[start + it] == needle[it] }) return start
        }
        return -1
    }
}
