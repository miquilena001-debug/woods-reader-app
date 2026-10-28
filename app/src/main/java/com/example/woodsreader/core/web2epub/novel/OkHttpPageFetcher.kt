package com.example.woodsreader.core.web2epub.novel

import android.webkit.CookieManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val WEB_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@Singleton
class OkHttpPageFetcher(
    private val client: OkHttpClient,
    private val pacer: RequestPacer? = null
) : PageFetcher {

    @Inject
    constructor() : this(
        client = OkHttpClient.Builder()
            .callTimeout(40, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
        pacer = null
    )

    override suspend fun get(url: String, referer: String?): FetchedPage {
        return getInternal(url = url, referer = referer, redirectHops = 0)
    }

    private suspend fun getInternal(url: String, referer: String?, redirectHops: Int): FetchedPage {
        if (redirectHops > 5) {
            throw FetchFailure.Network("Demasiadas redirecciones para la URL: $url")
        }

        pacer?.await()

        val trimmed = url.trim()
        val scheme = try {
            java.net.URI(trimmed).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }

        if (scheme != "http" && scheme != "https") {
            throw FetchFailure.Network("Esquema no soportado: $scheme para la URL: $url")
        }

        val requestBuilder = Request.Builder()
            .url(trimmed)
            .header("User-Agent", WEB_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")

        if (!referer.isNullOrBlank()) {
            requestBuilder.header("Referer", referer)
        }

        val cookies = try {
            CookieManager.getInstance().getCookie(trimmed)
        } catch (_: Throwable) {
            null
        }
        if (!cookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookies)
        }

        val request = requestBuilder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            if (e is FetchFailure) throw e
            throw FetchFailure.Network(e.message ?: "Error de conexión de red", e)
        }

        response.use { resp ->
            val status = resp.code
            val finalUrl = resp.request.url.toString()
            val contentType = resp.header("Content-Type")

            val headersMap = mutableMapOf<String, String>()
            for (i in 0 until resp.headers.size) {
                headersMap[resp.headers.name(i).lowercase()] = resp.headers.value(i)
            }

            val body = resp.body ?: throw FetchFailure.Network("Cuerpo de respuesta vacío para: $url")
            val maxBytes = 5L * 1024L * 1024L
            val buffer = Buffer()
            val source = body.source()
            var totalRead = 0L

            try {
                while (true) {
                    val read = source.read(buffer, 8192)
                    if (read == -1L) break
                    totalRead += read
                    if (totalRead > maxBytes) {
                        throw FetchFailure.TooLarge(url)
                    }
                }
            } catch (e: IOException) {
                if (e is FetchFailure) throw e
                throw FetchFailure.Network("Error al leer el cuerpo de respuesta: ${e.message}", e)
            }

            val bytes = buffer.readByteArray()
            val sampleHtml = if (bytes.isNotEmpty()) {
                val sampleLen = minOf(bytes.size, 20480)
                CharsetDetector.decode(bytes.copyOfRange(0, sampleLen), contentType)
            } else ""

            val looksChallenge = ChallengeDetector.looksLikeChallenge(status, headersMap, sampleHtml)

            if (status == 404) {
                throw FetchFailure.NotFound(url)
            }

            if (status == 429 || status == 503 || status == 403) {
                if (looksChallenge) {
                    throw FetchFailure.Challenge(url)
                }
                val retryAfterSec = resp.header("Retry-After")?.toLongOrNull()
                val retryAfterMs = if (retryAfterSec != null) retryAfterSec * 1000L else null
                throw FetchFailure.Blocked(status, retryAfterMs)
            }

            if (status == 200 && looksChallenge) {
                throw FetchFailure.Challenge(url)
            }

            if (!resp.isSuccessful) {
                throw FetchFailure.Blocked(status, null)
            }

            // Detección de páginas intermedias con redirección JavaScript o Meta-Refresh
            // (Ej: páginas señuelo 'Loading...' de readnovel.site -> novelphoenix.com)
            val fullHtml = if (bytes.isNotEmpty()) {
                CharsetDetector.decode(bytes, contentType)
            } else ""

            val jsRedirectTarget = extractRedirectTarget(fullHtml, finalUrl)
            if (jsRedirectTarget != null && jsRedirectTarget != finalUrl && jsRedirectTarget != url) {
                return getInternal(url = jsRedirectTarget, referer = finalUrl, redirectHops = redirectHops + 1)
            }

            return FetchedPage(
                requestedUrl = url,
                finalUrl = finalUrl,
                status = status,
                bytes = bytes,
                contentType = contentType,
                headers = headersMap
            )
        }
    }

    private fun extractRedirectTarget(html: String, baseUri: String): String? {
        if (html.isBlank()) return null

        // Solo procesar redirección JavaScript si la página es un señuelo intermedio (ej. título o cuerpo 'Loading...')
        val isIntermediateLoadingPage = html.contains("<title>Loading...</title>", ignoreCase = true) ||
                (html.contains("Loading...", ignoreCase = true) && html.length < 5000)

        // 1. Detección de JS window.location / location.href / location.replace
        if (isIntermediateLoadingPage) {
            val jsPattern = Regex("""(?:window\.)?location(?:\.href|\.replace)?\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val jsMatch = jsPattern.find(html)
            if (jsMatch != null) {
                val rawTarget = jsMatch.groupValues[1].replace("\\/", "/").trim()
                if (rawTarget.isNotBlank() && !rawTarget.endsWith("-")) {
                    return try {
                        java.net.URI(baseUri).resolve(rawTarget).toString()
                    } catch (_: Exception) {
                        rawTarget
                    }
                }
            }
        }

        // 2. Detección de meta http-equiv="refresh" content="0;url=..."
        val metaPattern = Regex("""<meta[^>]+http-equiv=["']?refresh["']?[^>]+content=["'][^"']*url=([^"']+)["']""", RegexOption.IGNORE_CASE)
        val metaMatch = metaPattern.find(html)
        if (metaMatch != null) {
            val rawTarget = metaMatch.groupValues[1].replace("'", "").replace("\"", "").trim()
            if (rawTarget.isNotBlank()) {
                return try {
                    java.net.URI(baseUri).resolve(rawTarget).toString()
                } catch (_: Exception) {
                    rawTarget
                }
            }
        }

        return null
    }
}
