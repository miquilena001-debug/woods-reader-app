package com.example.woodsreader.core.web2epub.novel

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

data class DownloadedImage(
    val originalUrl: String,
    val zipPath: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DownloadedImage
        return originalUrl == other.originalUrl && zipPath == other.zipPath
    }

    override fun hashCode(): Int {
        var result = originalUrl.hashCode()
        result = 31 * result + zipPath.hashCode()
        return result
    }
}

data class SniffedImageType(
    val mimeType: String,
    val extension: String
)

/**
 * Sniffer puro de tipos de imagen basado en magic bytes sin dependencias Android.
 */
object ImageSniffer {

    fun sniff(bytes: ByteArray): SniffedImageType? {
        if (bytes.size < 4) return null

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return SniffedImageType("image/jpeg", "jpg")
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        ) {
            return SniffedImageType("image/png", "png")
        }

        // GIF: GIF87a o GIF89a
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte() && (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()
        ) {
            return SniffedImageType("image/gif", "gif")
        }

        // WEBP: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return SniffedImageType("image/webp", "webp")
        }

        return null
    }

    fun sha1Name(url: String, extension: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(url.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02x".format(it) }
        return "$hex.$extension"
    }
}

/**
 * Control de presupuesto de almacenamiento y cantidad de imágenes.
 */
class ImageBudgetTracker(
    val maxBytesTotal: Long = 150L * 1024L * 1024L, // 150 MB
    val maxBytesPerImage: Long = 5L * 1024L * 1024L, // 5 MB
    val maxImagesPerChapter: Int = 40
) {
    private val currentTotalBytes = AtomicLong(0L)

    fun canDownload(contentLengthHeader: Long?): Boolean {
        if (contentLengthHeader != null && contentLengthHeader > maxBytesPerImage) {
            return false
        }
        if (contentLengthHeader != null && currentTotalBytes.get() + contentLengthHeader > maxBytesTotal) {
            return false
        }
        return currentTotalBytes.get() < maxBytesTotal
    }

    fun recordDownload(bytesCount: Long): Boolean {
        if (bytesCount > maxBytesPerImage) return false
        val newTotal = currentTotalBytes.addAndGet(bytesCount)
        return newTotal <= maxBytesTotal
    }

    fun getRemainingBytes(): Long {
        val remaining = maxBytesTotal - currentTotalBytes.get()
        return if (remaining > 0) remaining else 0
    }
}
