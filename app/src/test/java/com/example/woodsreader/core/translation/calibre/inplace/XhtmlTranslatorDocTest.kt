package com.example.woodsreader.core.translation.calibre.inplace

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class XhtmlTranslatorDocTest {

    @Test
    fun testTranslatePureTextAndPreserveLang() = runBlocking {
        val inputXhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>Chapter 1</title></head>
            <body>
                <h1>Chapter 1: Dawn</h1>
                <p>The dawn was silent.</p>
            </body>
            </html>
        """.trimIndent()

        val fakeTranslator = FakeTextTranslator(prefix = "[ES] ")
        val translatedXhtml = XhtmlTranslatorDoc.translate(
            xhtmlContent = inputXhtml,
            translator = fakeTranslator,
            sourceLang = "en",
            targetLang = "es"
        )
        println("Translated XHTML:\n$translatedXhtml")

        val doc = Jsoup.parse(translatedXhtml, "", Parser.xmlParser())
        assertEquals("es", doc.selectFirst("html")?.attr("xml:lang"))
        assertEquals("es", doc.selectFirst("html")?.attr("lang"))
        assertEquals("[ES] Chapter 1", doc.selectFirst("title")?.text())
        assertEquals("[ES] Chapter 1: Dawn", doc.selectFirst("h1")?.text())
        assertEquals("[ES] The dawn was silent.", doc.selectFirst("p")?.text())
    }

    @Test
    fun testPreserveInlineImagesAndTags() = runBlocking {
        val inputXhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body>
                <p>A solitary figure appeared: <img src="../images/dot.png" alt="Dot"/> on the horizon.</p>
                <p>This is <em>very</em> important and <span class="badge">highlighted</span>.</p>
            </body>
            </html>
        """.trimIndent()

        val fakeTranslator = FakeTextTranslator(prefix = "[ES] ")
        val translatedXhtml = XhtmlTranslatorDoc.translate(
            xhtmlContent = inputXhtml,
            translator = fakeTranslator,
            sourceLang = "en",
            targetLang = "es"
        )

        val doc = Jsoup.parse(translatedXhtml, "", Parser.xmlParser())
        val p1 = doc.select("p")[0]
        val img = p1.selectFirst("img")
        assertNotNull("La etiqueta img debe preservarse", img)
        assertEquals("../images/dot.png", img?.attr("src"))
        assertEquals("Dot", img?.attr("alt"))

        val p2 = doc.select("p")[1]
        val em = p2.selectFirst("em")
        assertNotNull("La etiqueta em debe preservarse", em)
        assertEquals("very", em?.text())

        val span = p2.selectFirst("span")
        assertNotNull("La etiqueta span debe preservarse", span)
        assertEquals("badge", span?.attr("class"))
        assertEquals("highlighted", span?.text())
    }

    @Test
    fun testFallbackWhenTranslatorDropsMarker() = runBlocking {
        val inputXhtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body>
                <p>Figure with image: <img src="../images/dot.png" alt="Dot"/> kept safe.</p>
            </body>
            </html>
        """.trimIndent()

        // Simular que el traductor pierde el marcador del img
        val badTranslator = FakeTextTranslator(prefix = "[ES] ", shouldDropMarker = true)
        val translatedXhtml = XhtmlTranslatorDoc.translate(
            xhtmlContent = inputXhtml,
            translator = badTranslator,
            sourceLang = "en",
            targetLang = "es"
        )

        val doc = Jsoup.parse(translatedXhtml, "", Parser.xmlParser())
        val img = doc.selectFirst("img")
        assertNotNull("Si el traductor borra un marcador, el fallback debe conservar el original con el img", img)
        assertEquals("../images/dot.png", img?.attr("src"))
    }
}
