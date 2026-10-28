package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class PageFetcherTest {

    @Test
    fun `fake fetcher returns configured page and counts calls`() = runTest {
        val fake = FakePageFetcher()
        val expected = FetchedPage(
            requestedUrl = "https://example.com/test",
            finalUrl = "https://example.com/test",
            status = 200,
            bytes = "Hello".toByteArray(Charsets.UTF_8),
            contentType = "text/html; charset=utf-8",
            headers = mapOf("content-type" to "text/html; charset=utf-8")
        )
        fake.setResponse("https://example.com/test", expected)

        val page = fake.get("https://example.com/test")
        assertEquals(200, page.status)
        assertEquals("Hello", String(page.bytes, Charsets.UTF_8))
        assertEquals(1, fake.callCount)
        assertEquals(listOf("https://example.com/test"), fake.requestedUrls)
    }

    @Test
    fun `fake fetcher throws configured failure`() = runTest {
        val fake = FakePageFetcher()
        fake.setFailure("https://example.com/blocked", FetchFailure.Blocked(403, 30_000L))

        try {
            fake.get("https://example.com/blocked")
            fail("Expected FetchFailure.Blocked")
        } catch (e: FetchFailure.Blocked) {
            assertEquals(403, e.status)
            assertEquals(30_000L, e.retryAfterMs)
        }
    }

    @Test
    fun `fixtures are populated and valid`() {
        assertNotNull(Fixtures.TOC_DESCENDING_HTML)
        assertNotNull(Fixtures.CHAPTER_RICH_HTML)
        assertNotNull(Fixtures.CHAPTER_ONLY_BR_HTML)
        assertNotNull(Fixtures.CHAPTER_LOCKED_HTML)
        assertNotNull(Fixtures.CLOUDFLARE_CHALLENGE_HTML)
    }
}
