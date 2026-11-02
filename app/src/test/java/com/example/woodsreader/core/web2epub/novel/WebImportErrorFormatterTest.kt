package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WebImportErrorFormatterTest {

    @Test
    fun `format translates FetchError types to clear Spanish user messages`() {
        val challengeMsg = WebImportErrorFormatter.format(FetchError.ChallengeRequired("Cloudflare", "challenge"))
        assertTrue(challengeMsg.contains("verificación anti-bot"))

        val loginMsg = WebImportErrorFormatter.format(FetchError.LoginRequired("https://site.com/login"))
        assertTrue(loginMsg.contains("requiere iniciar sesión"))

        val rateLimitedWithSec = WebImportErrorFormatter.format(FetchError.RateLimited(60_000L))
        assertTrue(rateLimitedWithSec.contains("60 segundos"))

        val rateLimitedNull = WebImportErrorFormatter.format(FetchError.RateLimited(null))
        assertTrue(rateLimitedNull.contains("HTTP 429"))

        val jsMsg = WebImportErrorFormatter.format(FetchError.NeedsJavaScript("SPA"))
        assertTrue(jsMsg.contains("requiere JavaScript"))

        val notFoundMsg = WebImportErrorFormatter.format(FetchError.NotFound("https://site.com/ch404"))
        assertTrue(notFoundMsg.contains("HTTP 404"))

        val httpMsg = WebImportErrorFormatter.format(FetchError.HttpError(500, "Internal Server Error"))
        assertTrue(httpMsg.contains("HTTP 500"))

        val netMsg = WebImportErrorFormatter.format(FetchError.NetworkError("Timeout"))
        assertTrue(netMsg.contains("Error de conexión"))

        val largeMsg = WebImportErrorFormatter.format(FetchError.TooLarge("https://site.com/big"))
        assertTrue(largeMsg.contains("5 MB"))

        val unsupportedMsg = WebImportErrorFormatter.format(
            FetchError.UnsupportedSite("Wattpad", "Wattpad no permite la importación automatizada de contenido según sus Términos de Servicio.")
        )
        assertEquals("Wattpad no permite la importación automatizada de contenido según sus Términos de Servicio.", unsupportedMsg)
    }

    @Test
    fun `format translates FetchFailure types to clear Spanish user messages`() {
        val challenge = WebImportErrorFormatter.format(FetchFailure.Challenge("https://site.com"))
        assertTrue(challenge.contains("verificación anti-bot"))

        val login = WebImportErrorFormatter.format(FetchFailure.LoginRequired("https://site.com/login"))
        assertTrue(login.contains("requiere iniciar sesión"))

        val blocked429 = WebImportErrorFormatter.format(FetchFailure.Blocked(429, 30_000L))
        assertTrue(blocked429.contains("30 segundos"))

        val blocked503 = WebImportErrorFormatter.format(FetchFailure.Blocked(503, null))
        assertTrue(blocked503.contains("HTTP 503"))

        val unsupported = WebImportErrorFormatter.format(
            FetchFailure.UnsupportedSite("Wattpad", "Wattpad no permite la importación automatizada.")
        )
        assertEquals("Wattpad no permite la importación automatizada.", unsupported)
    }

    @Test
    fun `format handles generic Throwable fallback`() {
        val generic = WebImportErrorFormatter.format(IllegalStateException("Fallo interno personalizado"))
        assertEquals("Fallo interno personalizado", generic)

        val nullMsg = WebImportErrorFormatter.format(RuntimeException())
        assertEquals("Ocurrió un error inesperado al procesar la novela.", nullMsg)
    }
}
