package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Descargador puro de imágenes para WebToEpub.
 * Respeta ImageBudgetTracker y utiliza ImageSniffer para la detección de tipos.
 */
class ImageDownloader(
    private val client: OkHttpClient,
    private val budgetTracker: ImageBudgetTracker = ImageBudgetTracker()
) {
    suspend fun downloadImage(url: String, referer: String? = null): DownloadedImage? = withContext(Dispatchers.IO) {
        if (!budgetTracker.canDownload(null)) {
            return@withContext null
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_UA)
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

        if (!referer.isNullOrBlank()) {
            requestBuilder.header("Referer", referer)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()
                if (contentLength > budgetTracker.maxBytesPerImage) {
                    return@withContext null
                }

                val buffer = ByteArrayOutputStream()
                val inputStream: InputStream = body.byteStream()
                val temp = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (inputStream.read(temp).also { bytesRead = it } != -1) {
                    totalRead += bytesRead
                    if (totalRead > budgetTracker.maxBytesPerImage) {
                        return@withContext null
                    }
                    buffer.write(temp, 0, bytesRead)
                }

                val bytes = buffer.toByteArray()
                if (bytes.isEmpty()) return@withContext null

                val sniffed = ImageSniffer.sniff(bytes) ?: return@withContext null

                if (!budgetTracker.recordDownload(bytes.size.toLong())) {
                    return@withContext null
                }

                val fileName = ImageSniffer.sha1Name(url, sniffed.extension)
                val zipPath = "OEBPS/images/$fileName"

                DownloadedImage(
                    originalUrl = url,
                    zipPath = zipPath,
                    mimeType = sniffed.mimeType,
                    bytes = bytes
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
