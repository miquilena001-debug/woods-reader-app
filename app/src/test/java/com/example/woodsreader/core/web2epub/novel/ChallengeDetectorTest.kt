package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeDetectorTest {

    @Test
    fun `detects cloudflare challenge by server header and 403 status`() {
        val isChallenge = ChallengeDetector.looksLikeChallenge(
            status = 403,
            headers = mapOf("server" to "cloudflare", "content-type" to "text/html"),
            html = "<html><body>Access Denied</body></html>"
        )
        assertTrue(isChallenge)
    }

    @Test
    fun `detects cloudflare challenge by server header and 503 status`() {
        val isChallenge = ChallengeDetector.looksLikeChallenge(
            status = 503,
            headers = mapOf("server" to "cloudflare-nginx"),
            html = "<html><body>Error</body></html>"
        )
        assertTrue(isChallenge)
    }

    @Test
    fun `detects just a moment title in 200 html`() {
        val isChallenge = ChallengeDetector.looksLikeChallenge(
            status = 200,
            headers = emptyMap(),
            html = Fixtures.CLOUDFLARE_CHALLENGE_HTML
        )
        assertTrue(isChallenge)
    }

    @Test
    fun `detects turnstile wrapper in 200 html`() {
        val html = """<html><body><div class="cf-turnstile" data-sitekey="xyz"></div></body></html>"""
        val isChallenge = ChallengeDetector.looksLikeChallenge(
            status = 200,
            headers = emptyMap(),
            html = html
        )
        assertTrue(isChallenge)
    }

    @Test
    fun `normal page does not trigger challenge detection`() {
        val isChallenge = ChallengeDetector.looksLikeChallenge(
            status = 200,
            headers = mapOf("server" to "nginx"),
            html = Fixtures.CHAPTER_RICH_HTML
        )
        assertFalse(isChallenge)
    }
}
