package com.example.woodsreader.core.web2epub.novel

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object CharsetDetector {

    private val CONTENT_TYPE_CHARSET_REGEX = Regex("""(?i)\bcharset\s*=\s*["']?([^"';\s>]+)""")
    private val META_CHARSET_REGEX = Regex("""(?i)<meta\s+[^>]*\bcharset\s*=\s*["']?([^"';\s>/]+)""")
    private val META_CONTENT_CHARSET_REGEX = Regex("""(?i)<meta\s+[^>]*http-equiv\s*=\s*["']?content-type["']?[^>]*content\s*=\s*["'][^"']*charset\s*=\s*([^"';\s>]+)""")

    fun decode(bytes: ByteArray, contentType: String?): String {
        if (bytes.isEmpty()) return ""

        // (a) charset del Content-Type
        if (!contentType.isNullOrBlank()) {
            val match = CONTENT_TYPE_CHARSET_REGEX.find(contentType)
            if (match != null) {
                val charsetName = match.groupValues[1].trim()
                val cs = safeCharset(charsetName)
                if (cs != null) {
                    tryDecode(bytes, cs)?.let { return it }
                }
            }
        }

        // (b) BOM UTF-8 / UTF-16
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
        }

        // (c) Primeros 2048 bytes como ISO-8859-1 para buscar <meta>
        val scanLen = minOf(bytes.size, 2048)
        val headerString = String(bytes, 0, scanLen, Charsets.ISO_8859_1)
        val metaMatch = META_CHARSET_REGEX.find(headerString) ?: META_CONTENT_CHARSET_REGEX.find(headerString)
        if (metaMatch != null) {
            val metaCharset = metaMatch.groupValues[1].trim()
            val cs = safeCharset(metaCharset)
            if (cs != null) {
                tryDecode(bytes, cs)?.let { return it }
            }
        }

        // (d) Intentar UTF-8 estricto
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (_: Exception) {
            // Falla UTF-8 estricto
        }

        // (e) Fallback GB18030 -> windows-1252
        val gbCharset = safeCharset("GB18030")
        if (gbCharset != null) {
            tryDecode(bytes, gbCharset)?.let { return it }
        }
        val winCharset = safeCharset("windows-1252") ?: Charsets.ISO_8859_1
        return String(bytes, winCharset)
    }

    private fun safeCharset(name: String): Charset? {
        return try {
            if (Charset.isSupported(name)) Charset.forName(name) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryDecode(bytes: ByteArray, charset: Charset): String? {
        return try {
            String(bytes, charset)
        } catch (_: Exception) {
            null
        }
    }
}
