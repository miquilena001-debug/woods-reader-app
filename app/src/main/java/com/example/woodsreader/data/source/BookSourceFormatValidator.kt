package com.example.woodsreader.data.source

import com.example.woodsreader.data.model.BookFormat
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipException

internal object BookSourceFormatValidator {
    private const val HEADER_PROBE_BYTES = 1_024
    private const val MAX_EPUB_ENTRIES_TO_PROBE = 16
    private const val MAX_EPUB_PROBE_BYTES = 256 * 1_024
    private const val MAX_CONTAINER_BYTES = 64 * 1_024
    private const val EPUB_MIMETYPE = "application/epub+zip"

    /**
     * Recognizes PDF headers and validates the bounded OCF structure required by EPUB.
     * The input stream is always closed. PDF validation is intentionally limited to its header;
     * Readium remains responsible for opening the complete publication.
     */
    fun detectFormat(input: InputStream): BookFormat? {
        return try {
            BufferedInputStream(input).use { stream ->
                stream.mark(HEADER_PROBE_BYTES + 1)
                val header = ByteArray(HEADER_PROBE_BYTES)
                var count = 0
                while (count < header.size) {
                    val read = stream.read(header, count, header.size - count)
                    if (read < 0) break
                    if (read == 0) {
                        val single = stream.read()
                        if (single < 0) break
                        header[count++] = single.toByte()
                    } else {
                        count += read
                    }
                }
                stream.reset()

                if (hasPdfHeader(header, count)) {
                    BookFormat.PDF
                } else if (count >= 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
                ) {
                    validateEpub(stream)
                } else {
                    null
                }
            }
        } catch (_: ZipException) {
            null
        }
    }

    private fun hasPdfHeader(bytes: ByteArray, count: Int): Boolean {
        val marker = "%PDF-".toByteArray(Charsets.US_ASCII)
        val lastStart = minOf(count - marker.size, HEADER_PROBE_BYTES - marker.size)
        for (start in 0..lastStart) {
            if ((start == 0 || bytes[start - 1] == '\n'.code.toByte() || bytes[start - 1] == '\r'.code.toByte()) &&
                marker.indices.all { offset -> bytes[start + offset] == marker[offset] } &&
                start + 7 < count &&
                bytes[start + 5].toInt().toChar().isDigit() &&
                bytes[start + 6] == '.'.code.toByte() &&
                bytes[start + 7].toInt().toChar().isDigit()
            ) {
                return true
            }
        }
        return false
    }

    private fun validateEpub(stream: InputStream): BookFormat? {
        ZipInputStream(stream).use { zip ->
            val mimetype = zip.nextEntry ?: return null
            if (mimetype.name != "mimetype" ||
                mimetype.isDirectory ||
                mimetype.method != java.util.zip.ZipEntry.STORED ||
                mimetype.size != EPUB_MIMETYPE.length.toLong()
            ) {
                return null
            }

            val mimeBytes = readEntry(zip, EPUB_MIMETYPE.length) ?: return null
            if (mimeBytes.toString(Charsets.US_ASCII) != EPUB_MIMETYPE) return null

            var entries = 1
            var totalBytes = mimeBytes.size
            while (entries < MAX_EPUB_ENTRIES_TO_PROBE) {
                val entry = zip.nextEntry ?: return null
                entries++
                if (entry.isDirectory) continue

                val entryLimit = minOf(
                    MAX_CONTAINER_BYTES,
                    MAX_EPUB_PROBE_BYTES - totalBytes
                )
                if (entryLimit <= 0) return null
                val bytes = readEntry(zip, entryLimit) ?: return null
                totalBytes += bytes.size
                if (entry.name == "META-INF/container.xml") {
                    if (bytes.size >= MAX_CONTAINER_BYTES) return null
                    val container = bytes.toString(Charsets.UTF_8)
                    return if (container.contains("<container") && container.contains("<rootfile")) {
                        BookFormat.EPUB
                    } else {
                        null
                    }
                }
            }
        }
        return null
    }

    private fun readEntry(input: InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(limit, 8_192))
        val buffer = ByteArray(4_096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                if (output.size() == limit) return null
                output.write(single)
                continue
            }
            if (read > limit - output.size()) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

internal object BookContentHash {
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun isContentAddress(value: String): Boolean = sha256Pattern.matches(value)
}
