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
class OkHttpPageFetcher @JvmOverloads constructor(
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
        return fetch(url, referer).toFetchedPageOrThrow()
    }

    override suspend fun fetch(url: String, referer: String?): FetchResult {
        return fetchInternal(url = url, referer = referer, redirectHops = 0)
    }

    private suspend fun fetchInternal(url: String, referer: String?, redirectHops: Int): FetchResult {
        if (redirectHops > 5) {
            return FetchResult.Failure(FetchError.NetworkError("Demasiadas redirecciones para la URL: $url"))
        }

        pacer?.await()

        val trimmed = url.trim()
        val scheme = try {
            java.net.URI(trimmed).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }

        if (scheme != "http" && scheme != "https") {
            return FetchResult.Failure(FetchError.NetworkError("Esquema no soportado: $scheme para la URL: $url"))
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
            if (e is FetchFailure) {
                return when (e) {
                    is FetchFailure.Challenge -> FetchResult.Failure(FetchError.ChallengeRequired("Cloudflare", e.message ?: "Challenge required"))
                    is FetchFailure.LoginRequired -> FetchResult.Failure(FetchError.LoginRequired(e.loginUrl))
                    is FetchFailure.Blocked -> FetchResult.Failure(FetchError.RateLimited(e.retryAfterMs))
                    is FetchFailure.NeedsJavaScript -> FetchResult.Failure(FetchError.NeedsJavaScript(e.reason))
                    is FetchFailure.NotFound -> FetchResult.Failure(FetchError.NotFound(e.url))
                    is FetchFailure.TooLarge -> FetchResult.Failure(FetchError.TooLarge(e.url))
                    is FetchFailure.Network -> FetchResult.Failure(FetchError.NetworkError(e.message ?: "Network error", e.cause))
                    is FetchFailure.UnsupportedSite -> FetchResult.Failure(FetchError.UnsupportedSite(e.siteName, e.reason))
                }
            }
            return FetchResult.Failure(FetchError.NetworkError(e.message ?: "Error de conexión de red", e))
        }

        return response.use { resp ->
            val status = resp.code
            val finalUrl = resp.request.url.toString()
            val contentType = resp.header("Content-Type")

            val headersMap = mutableMapOf<String, String>()
            for (i in 0 until resp.headers.size) {
                headersMap[resp.headers.name(i).lowercase()] = resp.headers.value(i)
            }

            val body = resp.body ?: return@use FetchResult.Failure(FetchError.NetworkError("Cuerpo de respuesta vacío para: $url"))
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
                        return@use FetchResult.Failure(FetchError.TooLarge(url))
                    }
                }
            } catch (e: IOException) {
                return@use FetchResult.Failure(FetchError.NetworkError("Error al leer el cuerpo de respuesta: ${e.message}", e))
            }

            val bytes = buffer.readByteArray()
            val fullHtml = if (bytes.isNotEmpty()) {
                CharsetDetector.decode(bytes, contentType)
            } else ""

            val looksChallenge = PageFetcherDetector.detectChallenge(status, headersMap, fullHtml)

            if (looksChallenge) {
                return@use FetchResult.Failure(
                    FetchError.ChallengeRequired("Cloudflare", "Verificación anti-bot requerida en $finalUrl")
                )
            }

            if (PageFetcherDetector.isLoginRedirect(url, finalUrl)) {
                return@use FetchResult.Failure(FetchError.LoginRequired(finalUrl))
            }

            if (status == 404) {
                return@use FetchResult.Failure(FetchError.NotFound(url))
            }

            if (status == 429) {
                val retryAfterMs = PageFetcherDetector.parseRetryAfter(resp.header("Retry-After"))
                return@use FetchResult.Failure(FetchError.RateLimited(retryAfterMs))
            }

            if (status in 400..599) {
                val retryAfterMs = PageFetcherDetector.parseRetryAfter(resp.header("Retry-After"))
                if (retryAfterMs != null) {
                    return@use FetchResult.Failure(FetchError.RateLimited(retryAfterMs))
                }
                val msg = resp.message.ifBlank { "HTTP $status" }
                return@use FetchResult.Failure(FetchError.HttpError(status, msg))
            }

            if (!resp.isSuccessful) {
                val msg = resp.message.ifBlank { "HTTP $status" }
                return@use FetchResult.Failure(FetchError.HttpError(status, msg))
            }

            if (PageFetcherDetector.detectSpaShell(fullHtml)) {
                return@use FetchResult.Failure(
                    FetchError.NeedsJavaScript("El sitio requiere JavaScript para renderizar el contenido (SPA shell)")
                )
            }

            // Detección de páginas intermedias con redirección JavaScript o Meta-Refresh
            // (Ej: páginas señuelo 'Loading...' de readnovel.site -> novelphoenix.com)
            val jsRedirectTarget = extractRedirectTarget(fullHtml, finalUrl)
            if (jsRedirectTarget != null && jsRedirectTarget != finalUrl && jsRedirectTarget != url) {
                return@use fetchInternal(url = jsRedirectTarget, referer = finalUrl, redirectHops = redirectHops + 1)
            }

            FetchResult.Success(
                FetchedPage(
                    requestedUrl = url,
                    finalUrl = finalUrl,
                    status = status,
                    bytes = bytes,
                    contentType = contentType,
                    headers = headersMap
                )
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
