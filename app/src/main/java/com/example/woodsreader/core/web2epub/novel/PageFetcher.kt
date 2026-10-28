package com.example.woodsreader.core.web2epub.novel

import java.io.IOException

class FetchedPage(
    val requestedUrl: String,
    val finalUrl: String,
    val status: Int,
    val bytes: ByteArray,
    val contentType: String?,
    val headers: Map<String, String> // claves en minúscula
)

sealed class FetchFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Network(message: String, cause: Throwable? = null) : FetchFailure(message, cause)
    class NotFound(val url: String) : FetchFailure("404: $url")
    class Blocked(val status: Int, val retryAfterMs: Long?) : FetchFailure("Bloqueado ($status)")
    class Challenge(val url: String) : FetchFailure("Verificación anti-bot requerida")
    class TooLarge(val url: String) : FetchFailure("Respuesta demasiado grande")
}

interface PageFetcher {
    @Throws(FetchFailure::class)
    suspend fun get(url: String, referer: String? = null): FetchedPage // lanza FetchFailure
}
