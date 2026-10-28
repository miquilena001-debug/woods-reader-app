package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NovelIndexLoaderTest {

    private val sampleProfile = SiteProfile(
        id = "test-site",
        domains = listOf("example.com"),
        chapterListSelector = "#chapter-list",
        chapterLinkSelector = "a[href]",
        reverseToc = false,
        novelTitleSelector = "h1.novel-title",
        coverSelector = null
    )

    @Test
    fun `loads metadata and chapters correctly with looksReversed true for descending toc`() = runTest {
        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/novel/index",
            FetchedPage(
                requestedUrl = "https://example.com/novel/index",
                finalUrl = "https://example.com/novel/index",
                status = 200,
                bytes = Fixtures.TOC_DESCENDING_HTML.toByteArray(Charsets.UTF_8),
                contentType = "text/html; charset=utf-8",
                headers = mapOf("content-type" to "text/html; charset=utf-8")
            )
        )

        val registry = SiteProfileRegistry(listOf(sampleProfile))
        val loader = NovelIndexLoader(fakeFetcher, registry)

        val novelIndex = loader.load("https://example.com/novel/index")

        assertEquals("Novela de Prueba", novelIndex.title)
        assertEquals("Autor Fantástico", novelIndex.author)
        assertEquals("Una novela épica de prueba en el bosque.", novelIndex.description)
        assertEquals("https://example.com/cover.jpg", novelIndex.coverUrl)
        assertEquals("es", novelIndex.language)
        assertEquals(5, novelIndex.chapters.size)
        // Fixture tiene capítulo 5 primero y capítulo 1 último -> looksReversed = true
        assertTrue(novelIndex.looksReversed)
        assertEquals("Capítulo 5: El Fin del Viaje", novelIndex.chapters[0].title)
        assertEquals("https://example.com/novel/capitulo-5", novelIndex.chapters[0].url)
        assertEquals(1, novelIndex.chapters[0].index)
    }

    @Test
    fun `deduplicates chapter urls and filters javascript and anchors`() = runTest {
        val html = """
        <!DOCTYPE html>
        <html>
        <head><title>Novela Duplicada</title></head>
        <body>
            <div id="chapter-list">
                <a href="https://example.com/ch1">Capítulo 1</a>
                <a href="https://example.com/ch1">Capítulo 1 Repetido</a>
                <a href="javascript:void(0)">No es capítulo</a>
                <a href="#top">Ir arriba</a>
                <a href="https://example.com/novel/toc">Mismo que índice</a>
                <a href="https://example.com/ch2">Capítulo 2</a>
            </div>
        </body>
        </html>
        """.trimIndent()

        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/novel/toc",
            FetchedPage(
                requestedUrl = "https://example.com/novel/toc",
                finalUrl = "https://example.com/novel/toc",
                status = 200,
                bytes = html.toByteArray(Charsets.UTF_8),
                contentType = "text/html",
                headers = emptyMap()
            )
        )

        val registry = SiteProfileRegistry(listOf(sampleProfile))
        val loader = NovelIndexLoader(fakeFetcher, registry)

        val novelIndex = loader.load("https://example.com/novel/toc")

        assertEquals(2, novelIndex.chapters.size)
        assertEquals("https://example.com/ch1", novelIndex.chapters[0].url)
        assertEquals("https://example.com/ch2", novelIndex.chapters[1].url)
    }

    @Test
    fun `resolves relative and parent links correctly with baseUri`() = runTest {
        val html = """
        <!DOCTYPE html>
        <html>
        <head><title>Enlaces Relativos</title></head>
        <body>
            <div id="chapter-list">
                <a href="ch1.html">Capítulo 1</a>
                <a href="../other/ch2.html">Capítulo 2</a>
            </div>
        </body>
        </html>
        """.trimIndent()

        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/book/sub/index.html",
            FetchedPage(
                requestedUrl = "https://example.com/book/sub/index.html",
                finalUrl = "https://example.com/book/sub/index.html",
                status = 200,
                bytes = html.toByteArray(Charsets.UTF_8),
                contentType = "text/html",
                headers = emptyMap()
            )
        )

        val registry = SiteProfileRegistry(listOf(sampleProfile))
        val loader = NovelIndexLoader(fakeFetcher, registry)

        val novelIndex = loader.load("https://example.com/book/sub/index.html")

        assertEquals(2, novelIndex.chapters.size)
        assertEquals("https://example.com/book/sub/ch1.html", novelIndex.chapters[0].url)
        assertEquals("https://example.com/book/other/ch2.html", novelIndex.chapters[1].url)
    }

    @Test
    fun `extracts cover from coverSelector with data-src`() = runTest {
        val customProfile = sampleProfile.copy(
            coverSelector = "div.novel-cover img"
        )
        val html = """
        <!DOCTYPE html>
        <html>
        <head><title>Novela con Portada</title></head>
        <body>
            <div class="novel-cover">
                <img data-src="https://example.com/images/lazy_cover.png" alt="Cover" />
            </div>
            <div id="chapter-list">
                <a href="https://example.com/ch1">Capítulo 1</a>
            </div>
        </body>
        </html>
        """.trimIndent()

        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/novel",
            FetchedPage(
                requestedUrl = "https://example.com/novel",
                finalUrl = "https://example.com/novel",
                status = 200,
                bytes = html.toByteArray(Charsets.UTF_8),
                contentType = "text/html",
                headers = emptyMap()
            )
        )

        val registry = SiteProfileRegistry(listOf(customProfile))
        val loader = NovelIndexLoader(fakeFetcher, registry)

        val novelIndex = loader.load("https://example.com/novel")

        assertEquals("https://example.com/images/lazy_cover.png", novelIndex.coverUrl)
    }

    @Test
    fun `novelphoenix style metadata extraction picks summary content and author tag correctly`() = runTest {
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>MAGUS INFINITE - Read Novel Free</title>
            <meta name="author" content="Novel Phoenix" />
            <meta property="og:description" content="Read MAGUS INFINITE novel online free at NovelPhoenix in Mobile, Tablet, iOS, Android" />
        </head>
        <body>
            <h1 class="novel-title">MAGUS INFINITE</h1>
            <a class="property-item" href="/author/bricktrader"><span itemprop="author">BRICKTRADER</span></a>
            <div class="summary">
                <h4 class="lined">Summary</h4>
                <div class="content expand-wrapper"><p>Elric Voss is sixteen years old...</p></div>
            </div>
            <a class="chapter-latest-container" href="https://example.com/novel/magus-infinite/chapters">Novel Chapters</a>
        </body>
        </html>
        """.trimIndent()

        val chaptersHtml = """
        <!DOCTYPE html>
        <html>
        <body>
            <div id="chpagedlist">
                <ul class="chapter-list">
                    <li><a href="/novel/magus-infinite/chapter-1">Chapter 1</a></li>
                    <li><a href="/novel/magus-infinite/chapter-2">Chapter 2</a></li>
                </ul>
            </div>
        </body>
        </html>
        """.trimIndent()

        val profile = SiteProfile(
            id = "novelfire",
            domains = listOf("example.com"),
            authorSelector = "a[href*='/author/'], [itemprop='author'], .author a",
            descriptionSelector = ".summary .content, .summary, .description, .novel-desc, #info .desc",
            chapterListSelector = "ul.chapter-list, #chpagedlist, .chapter-list",
            chapterLinkSelector = "a[href*='/chapter-']",
            novelTitleSelector = ".novel-title"
        )

        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/novel/magus-infinite",
            FetchedPage(
                requestedUrl = "https://example.com/novel/magus-infinite",
                finalUrl = "https://example.com/novel/magus-infinite",
                status = 200,
                bytes = html.toByteArray(Charsets.UTF_8),
                contentType = "text/html",
                headers = emptyMap()
            )
        )
        fakeFetcher.setResponse(
            "https://example.com/novel/magus-infinite/chapters",
            FetchedPage(
                requestedUrl = "https://example.com/novel/magus-infinite/chapters",
                finalUrl = "https://example.com/novel/magus-infinite/chapters",
                status = 200,
                bytes = chaptersHtml.toByteArray(Charsets.UTF_8),
                contentType = "text/html",
                headers = emptyMap()
            )
        )

        val registry = SiteProfileRegistry(listOf(profile))
        val loader = NovelIndexLoader(fakeFetcher, registry)
        val novelIndex = loader.load("https://example.com/novel/magus-infinite")

        assertEquals("MAGUS INFINITE", novelIndex.title)
        assertEquals("BRICKTRADER", novelIndex.author)
        assertEquals("Elric Voss is sixteen years old...", novelIndex.description)
        assertEquals(2, novelIndex.chapters.size)
    }

    @Test
    fun `missing chapter container adds warning and returns empty chapters`() = runTest {
        val html = """
        <!DOCTYPE html>
        <html>
        <head><title>Sin Contenedor</title></head>
        <body>
            <div id="wrong-id">
                <a href="https://example.com/ch1">Capítulo 1</a>
            </div>
        </body>
        </html>
        """.trimIndent()

        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/novel",
            FetchedPage(
                requestedUrl = "https://example.com/novel",
                finalUrl = "https://example.com/novel",
                status = 200,
                bytes = html.toByteArray(Charsets.UTF_8),
                contentType = "text/html",
                headers = emptyMap()
            )
        )

        val registry = SiteProfileRegistry(listOf(sampleProfile))
        val loader = NovelIndexLoader(fakeFetcher, registry)

        val novelIndex = loader.load("https://example.com/novel")

        assertTrue(novelIndex.chapters.isEmpty())
        assertTrue(novelIndex.warnings.any { it.contains("No se encontró el contenedor de capítulos") })
    }
}
