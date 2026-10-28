package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CharsetDetectorTest {

    @Test
    fun `decodes standard UTF-8 text`() {
        val text = "Capítulo 1: El despertar en el bosque 🌲"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val decoded = CharsetDetector.decode(bytes, "text/html; charset=utf-8")
        assertEquals(text, decoded)
    }

    @Test
    fun `decodes UTF-8 with BOM`() {
        val text = "Texto con BOM"
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + rawBytes
        val decoded = CharsetDetector.decode(bomBytes, null)
        assertEquals(text, decoded)
    }

    @Test
    fun `decodes ISO-8859-1 using meta charset tag`() {
        val html = """<html><head><meta charset="iso-8859-1"></head><body>Café con leche</body></html>"""
        val bytes = html.toByteArray(Charsets.ISO_8859_1)
        val decoded = CharsetDetector.decode(bytes, "text/html")
        assertTrue(decoded.contains("Café con leche"))
    }

    @Test
    fun `decodes GBK using meta content-type http-equiv`() {
        val gbkCharset = Charset.forName("GBK")
        val html = """<html><head><meta http-equiv="Content-Type" content="text/html; charset=gbk"></head><body>你好世界</body></html>"""
        val bytes = html.toByteArray(gbkCharset)
        val decoded = CharsetDetector.decode(bytes, null)
        assertTrue(decoded.contains("你好世界"))
    }

    @Test
    fun `decodes using Content-Type header charset`() {
        val win1252 = Charset.forName("windows-1252")
        val text = "Español con acentos y eñes"
        val bytes = text.toByteArray(win1252)
        val decoded = CharsetDetector.decode(bytes, "text/html; charset=windows-1252")
        assertEquals(text, decoded)
    }

    @Test
    fun `falls back gracefully for invalid UTF-8 without meta`() {
        // Bytes que no son UTF-8 válido
        val invalidUtf8Bytes = byteArrayOf(0xC0.toByte(), 0xAF.toByte(), 0xFF.toByte())
        val decoded = CharsetDetector.decode(invalidUtf8Bytes, null)
        assertTrue(decoded.isNotEmpty())
    }
}
