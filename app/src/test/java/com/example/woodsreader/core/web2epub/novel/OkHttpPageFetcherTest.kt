package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class OkHttpPageFetcherTest {

    private fun createFetcherWithHandler(
        handler: (Request) -> Response
    ): OkHttpPageFetcher {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                handler(chain.request())
            })
            .build()
        return OkHttpPageFetcher(client)
    }

    private fun createFetcherWithResponse(
        statusCode: Int = 200,
        headers: Map<String, String> = emptyMap(),
        bodyText: String = "",
        finalUrl: String? = null
    ): OkHttpPageFetcher {
        return createFetcherWithHandler { request ->
            val targetUrl = finalUrl ?: request.url.toString()
            val reqBuilder = request.newBuilder().url(targetUrl)
            val builder = Response.Builder()
                .request(reqBuilder.build())
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Error $statusCode")

            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            val mediaType = (headers["Content-Type"] ?: "text/html; charset=utf-8").toMediaType()
            builder.body(bodyText.toResponseBody(mediaType))
            builder.build()
        }
    }

    @Test
    fun `fetch returns Success with FetchedPage on 200 OK HTML`() = runTest {
        val html = "<html><head><title>Title</title></head><body><p>Hello world</p></body></html>"
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            headers = mapOf("Content-Type" to "text/html; charset=utf-8", "X-Custom" to "test"),
            bodyText = html
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Success)
        val success = result as FetchResult.Success
        assertEquals(200, success.page.status)
        assertEquals("https://example.com/novel/ch1", success.page.finalUrl)
        assertEquals(html, success.page.html)
        assertEquals("test", success.page.headers["x-custom"])
    }

    @Test
    fun `fetch returns Failure ChallengeRequired on Cloudflare 403 with challenge header`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 403,
            headers = mapOf(
                "server" to "cloudflare",
                "cf-mitigated" to "challenge"
            ),
            bodyText = "Cloudflare turnstile challenge"
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.ChallengeRequired)
        val err = failure.error as FetchError.ChallengeRequired
        assertEquals("Cloudflare", err.provider)
    }

    @Test
    fun `fetch returns Failure ChallengeRequired on 200 OK with challenge body`() = runTest {
        val challengeHtml = "<html><head><title>Just a moment...</title></head><body><div id='challenge-running'></div></body></html>"
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            bodyText = challengeHtml
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.ChallengeRequired)
    }

    @Test
    fun `fetch returns Failure LoginRequired when redirected to login URL`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            finalUrl = "https://example.com/login?nexturl=/novel/ch1",
            bodyText = "<html><body>Please log in</body></html>"
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.LoginRequired)
        val err = failure.error as FetchError.LoginRequired
        assertEquals("https://example.com/login?nexturl=/novel/ch1", err.loginUrl)
    }

    @Test
    fun `fetch non-regression does not treat novel slug with login in path as LoginRequired`() = runTest {
        val chapterHtml = "<html><body><p>El protagonista dominó el sistema de login y subió de nivel.</p></body></html>"
        val url = "https://example.com/novel/the-login-system-master/chapter-1"
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            finalUrl = url,
            bodyText = chapterHtml
        )

        val result = fetcher.fetch(url)
        assertTrue(result is FetchResult.Success)
    }

    @Test
    fun `fetch returns Failure RateLimited with parsed seconds on 429 Retry-After`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 429,
            headers = mapOf("Retry-After" to "120"),
            bodyText = "Too Many Requests"
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.RateLimited)
        val err = failure.error as FetchError.RateLimited
        assertEquals(120_000L, err.retryAfterMs)
    }

    @Test
    fun `fetch returns Failure RateLimited with null retryAfterMs when 429 lacks header`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 429,
            bodyText = "Too Many Requests"
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.RateLimited)
        val err = failure.error as FetchError.RateLimited
        assertNull(err.retryAfterMs)
    }

    @Test
    fun `fetch returns Failure RateLimited when 503 has Retry-After`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 503,
            headers = mapOf("Retry-After" to "60"),
            bodyText = "Service Temporarily Unavailable"
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.RateLimited)
        val err = failure.error as FetchError.RateLimited
        assertEquals(60_000L, err.retryAfterMs)
    }

    @Test
    fun `fetch returns Failure HttpError when 500 has no Retry-After`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 500,
            bodyText = "Internal Server Error"
        )

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.HttpError)
        val err = failure.error as FetchError.HttpError
        assertEquals(500, err.statusCode)
    }

    @Test
    fun `fetch returns Failure NotFound on 404`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 404,
            bodyText = "Page Not Found"
        )

        val result = fetcher.fetch("https://example.com/novel/ch404")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.NotFound)
        val err = failure.error as FetchError.NotFound
        assertEquals("https://example.com/novel/ch404", err.url)
    }

    @Test
    fun `fetch returns Failure NeedsJavaScript when response is SPA shell`() = runTest {
        val spaHtml = """
            <!doctype html>
            <html>
            <head><script defer src="/main.js"></script></head>
            <body>
                <noscript>You need to enable JavaScript to run this app.</noscript>
                <div id="root"></div>
            </body>
            </html>
        """.trimIndent()
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            bodyText = spaHtml
        )

        val result = fetcher.fetch("https://nanoreads.com/hub/boss-ceo")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.NeedsJavaScript)
    }

    @Test
    fun `fetch returns Failure NetworkError when network exception occurs`() = runTest {
        val fetcher = createFetcherWithHandler {
            throw IOException("Connection timed out")
        }

        val result = fetcher.fetch("https://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.NetworkError)
    }

    @Test
    fun `fetch returns Failure NetworkError for invalid scheme`() = runTest {
        val fetcher = createFetcherWithResponse(statusCode = 200)
        val result = fetcher.fetch("ftp://example.com/novel/ch1")
        assertTrue(result is FetchResult.Failure)
        val failure = result as FetchResult.Failure
        assertTrue(failure.error is FetchError.NetworkError)
    }

    @Test
    fun `get backward compatibility delegates to fetch and returns FetchedPage`() = runTest {
        val html = "<html><body><p>Normal chapter content</p></body></html>"
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            bodyText = html
        )

        val page = fetcher.get("https://example.com/novel/ch1")
        assertEquals(200, page.status)
        assertEquals(html, page.html)
    }

    @Test
    fun `get backward compatibility throws FetchFailure Blocked on 429`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 429,
            headers = mapOf("Retry-After" to "45"),
            bodyText = "Rate limited"
        )

        try {
            fetcher.get("https://example.com/novel/ch1")
            fail("Expected FetchFailure.Blocked")
        } catch (e: FetchFailure.Blocked) {
            assertEquals(429, e.status)
            assertEquals(45_000L, e.retryAfterMs)
        }
    }

    @Test
    fun `get backward compatibility throws FetchFailure LoginRequired on login redirect`() = runTest {
        val fetcher = createFetcherWithResponse(
            statusCode = 200,
            finalUrl = "https://example.com/login?nexturl=/ch1",
            bodyText = "login page"
        )

        try {
            fetcher.get("https://example.com/novel/ch1")
            fail("Expected FetchFailure.LoginRequired")
        } catch (e: FetchFailure.LoginRequired) {
            assertEquals("https://example.com/login?nexturl=/ch1", e.loginUrl)
        }
    }
}
