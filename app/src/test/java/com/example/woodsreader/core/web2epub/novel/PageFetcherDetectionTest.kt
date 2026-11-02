package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class PageFetcherDetectionTest {

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Resource not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    // --- Retry-After tests ---

    @Test
    fun `parseRetryAfter parses integer seconds into milliseconds`() {
        val ms = PageFetcherDetector.parseRetryAfter("120")
        assertEquals(120_000L, ms)
    }

    @Test
    fun `parseRetryAfter parses RFC 1123 HTTP-date into millisecond delta`() {
        val now = System.currentTimeMillis()
        val targetTime = ZonedDateTime.now().plusSeconds(60)
        val httpDateStr = targetTime.format(DateTimeFormatter.RFC_1123_DATE_TIME)

        val ms = PageFetcherDetector.parseRetryAfter(httpDateStr, nowMs = now)
        assertNotNull(ms)
        assertTrue("Delta en ms debe ser positivo y cercano a 60.000 ms", ms!! in 55_000L..65_000L)
    }

    @Test
    fun `parseRetryAfter caps delay at 1 hour maximum`() {
        // 5 horas en segundos (18.000 s)
        val ms = PageFetcherDetector.parseRetryAfter("18000")
        assertEquals(PageFetcherDetector.MAX_RETRY_AFTER_MS, ms)
    }

    @Test
    fun `parseRetryAfter returns null for blank, invalid or past date`() {
        assertNull(PageFetcherDetector.parseRetryAfter(null))
        assertNull(PageFetcherDetector.parseRetryAfter("   "))
        assertNull(PageFetcherDetector.parseRetryAfter("invalid-date-string"))

        // Fecha en el pasado
        val pastDate = ZonedDateTime.now().minusMinutes(5).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val ms = PageFetcherDetector.parseRetryAfter(pastDate)
        assertEquals(0L, ms)
    }

    // --- Login redirect tests ---

    @Test
    fun `isLoginRedirect detects redirect to login path`() {
        val requested = "https://example.com/novel/ch1"
        val redirected = "https://example.com/login?nexturl=%2Fnovel%2Fch1"
        assertTrue(PageFetcherDetector.isLoginRedirect(requested, redirected))

        val redirectedSignin = "https://example.com/signin"
        assertTrue(PageFetcherDetector.isLoginRedirect(requested, redirectedSignin))
    }

    @Test
    fun `isLoginRedirect non-regression preserves requested url with login in slug without redirect`() {
        val url = "https://example.com/novel/the-login-system-master/chapter-1"
        // Misma URL o redirección estética a trailing slash
        assertFalse(PageFetcherDetector.isLoginRedirect(url, url))
        assertFalse(PageFetcherDetector.isLoginRedirect(url, "$url/"))
    }

    // --- SPA shell detection tests ---

    @Test
    fun `detectSpaShell detects empty root with noscript and small payload against NanoReads fixture`() {
        val nanoreadsHtml = loadResource("sites/nanoreads/detection_spa_shell.html")
        assertTrue(PageFetcherDetector.detectSpaShell(nanoreadsHtml))
    }

    @Test
    fun `detectSpaShell non-regression preserves short legitimate chapter without empty root`() {
        val legitimateHtml = loadResource("sites/synthetic/short_legitimate_chapter.html")
        assertFalse(PageFetcherDetector.detectSpaShell(legitimateHtml))
    }

    @Test
    fun `detectSpaShell returns false if root container has substantial children`() {
        val ssrHtml = """
            <!doctype html>
            <html>
            <head><title>SSR Novel</title></head>
            <body>
                <div id="root">
                    <h1>Capítulo SSR</h1>
                    <p>Texto ya renderizado en el servidor para lectura directa.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        assertFalse(PageFetcherDetector.detectSpaShell(ssrHtml))
    }

    // --- Cloudflare Challenge detection test ---

    @Test
    fun `detectChallenge identifies Royal Road Cloudflare 403 fixture`() {
        val royalRoadHtml = loadResource("sites/royalroad/detection_cloudflare_403.html")
        val headers = mapOf("cf-mitigated" to "challenge", "server" to "cloudflare")
        assertTrue(PageFetcherDetector.detectChallenge(403, headers, royalRoadHtml))
    }

    // --- FetchResult and FetchFailure conversion tests ---

    @Test
    fun `toFetchedPageOrThrow converts FetchResult Success to FetchedPage`() {
        val page = FetchedPage("https://a.com", "https://a.com", 200, "ok".toByteArray(), "text/html", emptyMap())
        val result = FetchResult.Success(page)
        assertEquals(page, result.toFetchedPageOrThrow())
        assertEquals("ok", page.html)
    }

    @Test
    fun `toFetchedPageOrThrow converts FetchResult Failure to typed FetchFailure`() {
        val failureChallenge = FetchResult.Failure(FetchError.ChallengeRequired("Cloudflare", "Bot check"))
        try {
            failureChallenge.toFetchedPageOrThrow()
            fail("Expected FetchFailure.Challenge")
        } catch (e: FetchFailure.Challenge) {
            assertEquals("Bot check", e.url)
        }

        val failureLogin = FetchResult.Failure(FetchError.LoginRequired("https://a.com/login"))
        try {
            failureLogin.toFetchedPageOrThrow()
            fail("Expected FetchFailure.LoginRequired")
        } catch (e: FetchFailure.LoginRequired) {
            assertEquals("https://a.com/login", e.loginUrl)
        }

        val failureSpa = FetchResult.Failure(FetchError.NeedsJavaScript("Empty SPA shell"))
        try {
            failureSpa.toFetchedPageOrThrow()
            fail("Expected FetchFailure.NeedsJavaScript")
        } catch (e: FetchFailure.NeedsJavaScript) {
            assertEquals("Empty SPA shell", e.reason)
        }
    }
    @Test
    fun `parseRetryAfter caps HTTP-date delay at 1 hour maximum`() {
        val now = System.currentTimeMillis()
        val twoHoursLater = ZonedDateTime.now().plusHours(2).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val ms = PageFetcherDetector.parseRetryAfter(twoHoursLater, nowMs = now)
        assertEquals(3_600_000L, ms)
    }

    @Test
    fun `isLoginRedirect non-regression preserves requested url with login in slug when query parameters are appended without login path`() {
        val requested = "https://example.com/novel/the-login-system-master/chapter-1"
        val redirectedWithTracking = "https://example.com/novel/the-login-system-master/chapter-1?source=share&utm=test"
        assertFalse(PageFetcherDetector.isLoginRedirect(requested, redirectedWithTracking))
    }

    @Test
    fun `detectSpaShell returns false if payload exceeds 25KB even with empty root and noscript`() {
        val largePadding = "<!-- " + "x".repeat(26_000) + " -->"
        val largeHtml = """
            <!doctype html><html><head><script defer src="/main.js"></script></head>
            <body><noscript>You need to enable JavaScript to run this app.</noscript><div id="root"></div>$largePadding</body></html>
        """.trimIndent()
        assertFalse(PageFetcherDetector.detectSpaShell(largeHtml))
    }

    @Test
    fun `detectSpaShell returns false if noscript is present but neither root nor app container exists`() {
        val htmlWithoutRoot = """
            <!doctype html><html><head></head>
            <body>
                <noscript>You need to enable JavaScript to run this app.</noscript>
                <div id="main-content"><p>Contenido completo del capítulo estático.</p></div>
            </body></html>
        """.trimIndent()
        assertFalse(PageFetcherDetector.detectSpaShell(htmlWithoutRoot))
    }

    @Test
    fun `detectSpaShell returns false if empty root exists but no noscript or spa script bundle present`() {
        val htmlWithoutSpaSignals = """
            <!doctype html><html><head><title>Static Page</title></head>
            <body>
                <div id="root"></div>
                <div class="content"><p>Texto legítimo cargado estáticamente sin JS.</p></div>
            </body></html>
        """.trimIndent()
        assertFalse(PageFetcherDetector.detectSpaShell(htmlWithoutSpaSignals))
    }

}
