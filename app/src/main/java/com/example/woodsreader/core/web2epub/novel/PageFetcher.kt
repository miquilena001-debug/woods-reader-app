package com.example.woodsreader.core.web2epub.novel

import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FetchedPage(
    val requestedUrl: String,
    val finalUrl: String,
    val status: Int,
    val bytes: ByteArray,
    val contentType: String?,
    val headers: Map<String, String> // claves en minúscula
) {
    val html: String by lazy {
        if (bytes.isEmpty()) "" else CharsetDetector.decode(bytes, contentType)
    }
}

sealed interface FetchError {
    data class ChallengeRequired(val provider: String, val message: String) : FetchError
    data class LoginRequired(val loginUrl: String) : FetchError
    data class RateLimited(val retryAfterMs: Long?) : FetchError
    data class NeedsJavaScript(val reason: String) : FetchError
    data class NotFound(val url: String) : FetchError
    data class HttpError(val statusCode: Int, val message: String) : FetchError
    data class NetworkError(val message: String, val cause: Throwable? = null) : FetchError
    data class TooLarge(val url: String) : FetchError
}

sealed interface FetchResult {
    data class Success(val page: FetchedPage) : FetchResult
    data class Failure(val error: FetchError) : FetchResult
}

sealed class FetchFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Network(message: String, cause: Throwable? = null) : FetchFailure(message, cause)
    class NotFound(val url: String) : FetchFailure("404: $url")
    class Blocked(val status: Int, val retryAfterMs: Long?) : FetchFailure("Bloqueado ($status)")
    class Challenge(val url: String) : FetchFailure("Verificación anti-bot requerida")
    class TooLarge(val url: String) : FetchFailure("Respuesta demasiado grande")
    class LoginRequired(val loginUrl: String) : FetchFailure("Inicio de sesión requerido: $loginUrl")
    class NeedsJavaScript(val reason: String) : FetchFailure("Requiere JavaScript: $reason")
}

fun FetchResult.toFetchedPageOrThrow(): FetchedPage = when (this) {
    is FetchResult.Success -> page
    is FetchResult.Failure -> when (val err = error) {
        is FetchError.ChallengeRequired -> throw FetchFailure.Challenge(err.message)
        is FetchError.LoginRequired -> throw FetchFailure.LoginRequired(err.loginUrl)
        is FetchError.RateLimited -> throw FetchFailure.Blocked(429, err.retryAfterMs)
        is FetchError.NeedsJavaScript -> throw FetchFailure.NeedsJavaScript(err.reason)
        is FetchError.NotFound -> throw FetchFailure.NotFound(err.url)
        is FetchError.HttpError -> throw FetchFailure.Blocked(err.statusCode, null)
        is FetchError.NetworkError -> throw FetchFailure.Network(err.message, err.cause)
        is FetchError.TooLarge -> throw FetchFailure.TooLarge(err.url)
    }
}

object PageFetcherDetector {
    const val MAX_RETRY_AFTER_MS = 3_600_000L // 1 hora máximo
    const val MAX_SPA_SHELL_BYTES = 25_000

    fun parseRetryAfter(headerValue: String?, nowMs: Long = System.currentTimeMillis()): Long? {
        if (headerValue.isNullOrBlank()) return null
        val trimmed = headerValue.trim()

        // 1. Número entero de segundos
        trimmed.toLongOrNull()?.let { seconds ->
            if (seconds <= 0) return 0L
            val ms = seconds * 1000L
            return ms.coerceAtMost(MAX_RETRY_AFTER_MS)
        }

        // 2. Fecha HTTP (RFC 1123)
        return try {
            val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
            val targetZoned = ZonedDateTime.parse(trimmed, formatter)
            val targetMs = targetZoned.toInstant().toEpochMilli()
            val diffMs = targetMs - nowMs
            diffMs.coerceIn(0L, MAX_RETRY_AFTER_MS)
        } catch (_: Exception) {
            null
        }
    }

    fun isLoginRedirect(requestedUrl: String, finalUrl: String): Boolean {
        if (requestedUrl.isBlank() || finalUrl.isBlank()) return false
        val reqTrimmed = requestedUrl.trim().removeSuffix("/")
        val finalTrimmed = finalUrl.trim().removeSuffix("/")
        if (reqTrimmed.equals(finalTrimmed, ignoreCase = true)) {
            return false // Misma URL, sin redirección (protege slugs como "the-login-system")
        }

        val uri = try {
            URI(finalUrl)
        } catch (_: Exception) {
            return false
        }
        val path = uri.path?.lowercase() ?: ""
        val query = uri.query?.lowercase() ?: ""

        val canonicalLoginPaths = listOf("/login", "/signin", "/sign-in", "/auth/login", "/account/login")
        val isCanonicalPath = canonicalLoginPaths.any { path == it || path.startsWith("$it/") }

        val hasLoginQueryIndicator = query.contains("nexturl=") ||
                query.contains("returnurl=") ||
                query.contains("redirect=") ||
                query.contains("continue=")

        return isCanonicalPath || (path.contains("login") && hasLoginQueryIndicator)
    }

    fun detectSpaShell(html: String): Boolean {
        if (html.isBlank()) return false

        // Señal 1: Tamaño corto (< 25 KB)
        if (html.length > MAX_SPA_SHELL_BYTES) return false

        val doc = Jsoup.parse(html)

        // Señal 2: Contenedor de montaje SPA (#root o #app) existe y está vacío
        val rootEl = doc.selectFirst("#root, #app") ?: return false
        if (rootEl.children().isNotEmpty()) return false

        // Señal 3: Indicador explícito de JavaScript (<noscript> exigiendo JS o script bundle)
        val hasNoscript = html.contains("<noscript>", ignoreCase = true) &&
                (html.contains("enable javascript", ignoreCase = true) || html.contains("javascript to run this app", ignoreCase = true))
        val hasSpaScript = doc.select("script[src*='main'], script[src*='app'], script[src*='bundle']").isNotEmpty()
        if (!hasNoscript && !hasSpaScript) return false

        val paragraphs = doc.select("p")
        val paragraphCount = paragraphs.size
        val paragraphTextLength = paragraphs.text().length

        // Si contiene párrafos sustanciales, no es un cascarón vacío
        if (paragraphCount > 5 || paragraphTextLength > 1000) {
            return false
        }

        return true
    }

    fun detectChallenge(statusCode: Int, headers: Map<String, String>, html: String): Boolean {
        val cfMitigated = headers.entries.firstOrNull { it.key.equals("cf-mitigated", ignoreCase = true) }?.value ?: ""
        if (cfMitigated.contains("challenge", ignoreCase = true)) {
            return true
        }
        return ChallengeDetector.looksLikeChallenge(statusCode, headers, html)
    }
}

interface PageFetcher {
    suspend fun fetch(url: String, referer: String? = null): FetchResult = try {
        FetchResult.Success(get(url, referer))
    } catch (e: FetchFailure) {
        when (e) {
            is FetchFailure.Challenge -> FetchResult.Failure(FetchError.ChallengeRequired("Cloudflare", e.message ?: "Challenge required"))
            is FetchFailure.LoginRequired -> FetchResult.Failure(FetchError.LoginRequired(e.loginUrl))
            is FetchFailure.Blocked -> FetchResult.Failure(FetchError.RateLimited(e.retryAfterMs))
            is FetchFailure.NeedsJavaScript -> FetchResult.Failure(FetchError.NeedsJavaScript(e.reason))
            is FetchFailure.NotFound -> FetchResult.Failure(FetchError.NotFound(e.url))
            is FetchFailure.TooLarge -> FetchResult.Failure(FetchError.TooLarge(e.url))
            is FetchFailure.Network -> FetchResult.Failure(FetchError.NetworkError(e.message ?: "Network error", e.cause))
        }
    } catch (e: Exception) {
        FetchResult.Failure(FetchError.NetworkError(e.message ?: "Unknown error", e))
    }

    @Throws(FetchFailure::class)
    suspend fun get(url: String, referer: String? = null): FetchedPage
}
