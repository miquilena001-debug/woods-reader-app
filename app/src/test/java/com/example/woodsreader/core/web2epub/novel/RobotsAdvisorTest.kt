package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotsAdvisorTest {

    @Test
    fun `robots advisor parses disallow rules correctly`() = runTest {
        val robotsTxt = """
        User-agent: Googlebot
        Disallow: /google-only/

        User-agent: *
        Disallow: /admin/
        Disallow: /private
        """.trimIndent()

        val fakeFetcher = FakePageFetcher()
        fakeFetcher.setResponse(
            "https://example.com/robots.txt",
            FetchedPage(
                requestedUrl = "https://example.com/robots.txt",
                finalUrl = "https://example.com/robots.txt",
                status = 200,
                bytes = robotsTxt.toByteArray(Charsets.UTF_8),
                contentType = "text/plain",
                headers = mapOf("content-type" to "text/plain")
            )
        )

        val advisor = RobotsAdvisor(fakeFetcher)

        assertTrue(advisor.isDisallowed("https://example.com/admin/settings"))
        assertTrue(advisor.isDisallowed("https://example.com/private/book"))
        assertFalse(advisor.isDisallowed("https://example.com/novel/chapter-1"))
        assertFalse(advisor.isDisallowed("https://example.com/public"))
    }
}
