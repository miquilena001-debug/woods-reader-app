package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExtractorTest {

    @Test
    fun `extract rich chapter with profile removes noise and extracts images`() {
        val profile = SiteProfile(
            id = "test",
            domains = listOf("example.com"),
            titleSelector = "h2.chapter-title",
            contentSelectors = listOf("#chr-content"),
            removeSelectors = listOf(".ads"),
            removeTextContains = listOf("Report chapter if you find errors.")
        )

        val extractor = ContentExtractor(profile)
        val result = extractor.extract(
            html = Fixtures.CHAPTER_RICH_HTML,
            baseUri = "https://example.com/novel/ch1"
        )

        assertEquals("Capítulo 1: El Despertar", result.title)
        assertEquals(ChapterStatus.OK, result.status)
        assertTrue(result.wordCount > 30)
        assertEquals(1, result.imageSources.size)
        assertEquals("https://example.com/images/forest_dawn.jpg", result.imageSources[0])
        assertTrue(!result.bodyXhtml.contains("Publicidad no deseada"))
        assertTrue(!result.bodyXhtml.contains("Report chapter if you find errors"))
        assertTrue(result.bodyXhtml.contains("El sol brillaba con una"))
    }

    @Test
    fun `extract br-only chapter normalizes to p tags`() {
        val extractor = ContentExtractor()
        val result = extractor.extract(
            html = Fixtures.CHAPTER_ONLY_BR_HTML,
            baseUri = "https://example.com/novel/ch2"
        )

        assertEquals("Capítulo 2: Primeros Pasos", result.title)
        assertTrue(result.bodyXhtml.contains("<p>Avanzaron lentamente por el sendero pedregoso.</p>"))
        assertTrue(result.bodyXhtml.contains("<p>La noche caía y las sombras se alargaban sin tregua."))
    }

    @Test
    fun `extract locked chapter detects locked status`() {
        val extractor = ContentExtractor()
        val result = extractor.extract(
            html = Fixtures.CHAPTER_LOCKED_HTML,
            baseUri = "https://example.com/novel/ch3"
        )

        assertEquals(ChapterStatus.LOCKED, result.status)
        assertTrue(result.bodyXhtml.contains("Capítulo bloqueado"))
    }

    @Test
    fun `extract short chapter marks as SHORT`() {
        val extractor = ContentExtractor()
        val shortHtml = """
            <html>
                <body>
                    <h1>Short notice</h1>
                    <div id="content">
                        <p>Author is taking a break today. See you tomorrow!</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val result = extractor.extract(shortHtml, "https://example.com/novel/short")
        assertEquals(ChapterStatus.SHORT, result.status)
        assertTrue(result.wordCount < 100)
    }

    @Test
    fun `extract empty chapter marks as EMPTY`() {
        val extractor = ContentExtractor()
        val emptyHtml = "<html><body></body></html>"

        val result = extractor.extract(emptyHtml, "https://example.com/novel/empty", fallbackTitle = "Capítulo Vacío")
        assertEquals(ChapterStatus.EMPTY, result.status)
        assertEquals(0, result.wordCount)
        assertEquals("Capítulo Vacío", result.title)
    }

    @Test
    fun `extract removes duplicate title heading inside content`() {
        val extractor = ContentExtractor()
        val html = """
            <html>
                <head><title>Chapter 1: The Awakening</title></head>
                <body>
                    <div id="content">
                        <h1>Chapter 1: The Awakening</h1>
                        <p>This is the actual narrative that starts the adventure with plenty of details and events.</p>
                        <p>More words to make it exceed the short chapter threshold so we can test duplicate heading removal cleanly without triggering other statuses.</p>
                        <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val result = extractor.extract(html, "https://example.com/ch1")
        assertEquals("Chapter 1: The Awakening", result.title)
        // El h1 dentro del bodyXhtml no debe existir porque duplicaba el título
        assertTrue(!result.bodyXhtml.contains("<h1>Chapter 1: The Awakening</h1>"))
        assertTrue(result.bodyXhtml.contains("This is the actual narrative"))
    }
}
