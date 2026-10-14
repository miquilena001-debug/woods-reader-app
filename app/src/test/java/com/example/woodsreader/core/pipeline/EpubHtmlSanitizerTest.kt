package com.example.woodsreader.core.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHtmlSanitizerTest {

    @Test
    fun `sanitize adds xmlns to plain html tag`() {
        val input = "<html><head></head><body><p>Texto</p></body></html>"
        val output = EpubHtmlSanitizer.sanitize(input)
        assertTrue(output.contains("""<html xmlns="http://www.w3.org/1999/xhtml">"""))
        assertTrue(output.contains("<p>Texto</p>"))
    }

    @Test
    fun `sanitize preserves existing attributes while adding xmlns`() {
        val input = """<html class="calibre" lang="es"><head></head><body><p>Texto</p></body></html>"""
        val output = EpubHtmlSanitizer.sanitize(input)
        assertTrue(output.contains("""<html xmlns="http://www.w3.org/1999/xhtml" class="calibre" lang="es">"""))
    }

    @Test
    fun `sanitize leaves existing xmlns intact`() {
        val input = """<html xmlns="http://www.w3.org/1999/xhtml"><head></head><body><p>Texto</p></body></html>"""
        val output = EpubHtmlSanitizer.sanitize(input)
        assertEquals(input, output)
    }

    @Test
    fun `sanitize adds default xmlns when only a prefixed namespace exists`() {
        val input = """<html xmlns:epub="http://www.idpf.org/2007/ops"><head></head><body></body></html>"""
        val output = EpubHtmlSanitizer.sanitize(input)
        assertTrue(output.contains("""xmlns="http://www.w3.org/1999/xhtml""""))
        assertTrue(output.contains("""xmlns:epub="http://www.idpf.org/2007/ops""""))
    }

    @Test
    fun `sanitize inserts head tag when missing`() {
        val input = "<html><body><p>Capítulo 1</p></body></html>"
        val output = EpubHtmlSanitizer.sanitize(input)
        assertTrue(output.contains("</head>"))
        assertTrue(output.contains("""<html xmlns="http://www.w3.org/1999/xhtml">"""))
        assertTrue(output.contains("<p>Capítulo 1</p>"))
    }

    @Test
    fun `sanitize wraps orphan content in valid xhtml document`() {
        val input = "<p>Solo párrafos sueltos</p>"
        val output = EpubHtmlSanitizer.sanitize(input)
        assertTrue(output.contains("""<html xmlns="http://www.w3.org/1999/xhtml">"""))
        assertTrue(output.contains("<head>"))
        assertTrue(output.contains("<body>"))
        assertTrue(output.contains("<p>Solo párrafos sueltos</p>"))
    }

    @Test
    fun `sanitize removes an existing xml declaration before wrapping a fragment`() {
        val input = """<?xml version="1.0" encoding="ISO-8859-1"?><!DOCTYPE html><p>café</p>"""

        val output = EpubHtmlSanitizer.sanitize(input)

        assertEquals(1, Regex("""<\?xml""").findAll(output).count())
        assertTrue(output.contains("""<?xml version="1.0" encoding="utf-8"?>"""))
        assertTrue(output.contains("<p>café</p>"))
    }

    @Test
    fun `encodeSanitized uses utf8 for wrapped fragments`() {
        val original = "<p>café</p>"
        val sanitized = EpubHtmlSanitizer.sanitize(original)

        val encoded = EpubHtmlSanitizer.encodeSanitized(original, sanitized, Charsets.ISO_8859_1)

        assertEquals(sanitized, encoded.toString(Charsets.UTF_8))
    }

    @Test
    fun `sanitize inserts escaped chapter title at the start of the body`() {
        val input = "<html><head></head><body><p>Texto</p></body></html>"

        val output = EpubHtmlSanitizer.sanitize(input, "Capítulo <1> & más")

        assertTrue(output.contains("""<h1 class="woods-reader-chapter-title">Capítulo &lt;1&gt; &amp; más</h1>"""))
        assertTrue(output.indexOf("woods-reader-chapter-title") < output.indexOf("<p>Texto</p>"))
    }

    @Test
    fun `sanitize does not duplicate a title already at body start`() {
        val input = "<html><head></head><body><h1>Capítulo</h1><p>Texto</p></body></html>"

        val output = EpubHtmlSanitizer.sanitize(input, "Otro título")

        assertTrue(output.contains("<h1>Capítulo</h1>"))
        assertEquals(1, Regex("<h1").findAll(output).count())
    }

    @Test
    fun `detectCharset identifies xml declaration encoding`() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><html><head></head><body></body></html>"""
        val charset = EpubHtmlSanitizer.detectCharset(xml.toByteArray(Charsets.ISO_8859_1))
        assertEquals(Charsets.ISO_8859_1, charset)
    }

    @Test
    fun `detectCharset defaults to UTF-8`() {
        val html = "<html><head></head><body></body></html>"
        val charset = EpubHtmlSanitizer.detectCharset(html.toByteArray(Charsets.UTF_8))
        assertEquals(Charsets.UTF_8, charset)
    }

}
