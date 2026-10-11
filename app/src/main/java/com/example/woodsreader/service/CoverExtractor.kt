package com.example.woodsreader.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.woodsreader.data.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CoverExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CoverExtractor"
        private const val COVER_MAX_WIDTH = 480
        private const val COVER_MAX_HEIGHT = 720
        private const val JPEG_QUALITY = 85
        private const val COVERS_DIR_NAME = "covers"

        // Palettes for procedural cover fallback
        private val PALETTES = listOf(
            // Deep Forest (brand signature)
            Pair(Color.rgb(27, 59, 43), Color.rgb(11, 25, 18)),
            // Midnight Navy
            Pair(Color.rgb(26, 42, 58), Color.rgb(10, 16, 24)),
            // Vintage Wine / Burgundy
            Pair(Color.rgb(59, 27, 37), Color.rgb(24, 10, 14)),
            // Warm Amber / Leather
            Pair(Color.rgb(59, 44, 27), Color.rgb(24, 18, 10)),
            // Charcoal Slate
            Pair(Color.rgb(44, 48, 46), Color.rgb(18, 20, 19)),
            // Royal Plum
            Pair(Color.rgb(45, 27, 59), Color.rgb(19, 10, 24))
        )
    }

    private val permanentCoversDir: File by lazy {
        File(context.filesDir, COVERS_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    private val legacyCoversDir: File by lazy {
        File(context.cacheDir, COVERS_DIR_NAME)
    }

    /**
     * Checks if a cover is already cached on disk (either in filesDir or cacheDir).
     * Automatically migrates from cacheDir to filesDir if found.
     */
    fun getExistingCoverPath(fileHash: String): String? {
        if (fileHash.isBlank()) return null
        val targetFile = File(permanentCoversDir, "cover_$fileHash.jpg")
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile.absolutePath
        }

        // Check legacy cacheDir
        val legacyFile = File(legacyCoversDir, "cover_$fileHash.jpg")
        if (legacyFile.exists() && legacyFile.length() > 0) {
            try {
                legacyFile.copyTo(targetFile, overwrite = true)
                return targetFile.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "Failed to migrate legacy cover to filesDir", e)
                return legacyFile.absolutePath
            }
        }
        return null
    }

    /**
     * Extracts and persists a cover image for the given source and format.
     * Guaranteed to return a valid local file path or null only if file is completely unreadable.
     */
    suspend fun extractCover(
        source: Any,
        format: BookFormat,
        fileHash: String,
        title: String = "",
        author: String = "",
        publication: Publication? = null
    ): String? = withContext(Dispatchers.IO) {
        val effectiveHash = when {
            fileHash.isNotBlank() && !fileHash.startsWith("pending-") -> fileHash
            source is File -> "file_${source.name.hashCode()}_${source.length()}"
            source is Uri -> "uri_${kotlin.math.abs(source.toString().hashCode())}"
            else -> "cover_${kotlin.math.abs(title.hashCode())}_${kotlin.math.abs(author.hashCode())}"
        }

        // 1. Check existing cover on disk
        getExistingCoverPath(effectiveHash)?.let { return@withContext it }

        val targetFile = File(permanentCoversDir, "cover_$effectiveHash.jpg")

        // 2. Extract bitmap based on format
        var bitmap: Bitmap? = null

        val resolvedFormat = if (format != BookFormat.UNKNOWN) format else {
            val srcStr = source.toString().lowercase()
            when {
                srcStr.endsWith(".epub") -> BookFormat.EPUB
                srcStr.endsWith(".pdf")  -> BookFormat.PDF
                else -> BookFormat.UNKNOWN
            }
        }

        try {
            when (resolvedFormat) {
                BookFormat.PDF -> {
                    bitmap = extractPdfCover(source)
                }

                BookFormat.EPUB -> {
                    // Level A: Readium cover service if publication is provided
                    if (publication != null) {
                        try {
                            bitmap = publication.cover()
                        } catch (e: Exception) {
                            Log.w(TAG, "publication.cover() failed: ${e.message}")
                        }

                        // Level B: Heuristic manifest resource search
                        if (bitmap == null) {
                            bitmap = extractEpubCoverFromManifest(publication)
                        }
                    }

                    // Level C: Direct Zip inspection fallback (works even without Readium)
                    if (bitmap == null) {
                        bitmap = extractCoverFromZipArchive(source, isEpub = true)
                    }
                }

                BookFormat.UNKNOWN -> {
                    bitmap = null
                }
            }

            // 3. Fallback: If no image could be extracted, generate a procedural cover
            if (bitmap == null) {
                val coverTitle = title.ifBlank {
                    when (source) {
                        is File -> source.nameWithoutExtension
                        is Uri -> source.lastPathSegment?.substringBeforeLast('.') ?: "Libro"
                        else -> "Libro"
                    }
                }
                bitmap = generateProceduralCover(coverTitle, author)
            }

            if (bitmap == null) return@withContext null

            // 4. Scale and save to permanent storage
            val scaledBitmap = scaleBitmapToMaxDimensions(bitmap)
            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract/save cover for hash=$fileHash", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF Extraction (Native Android PdfRenderer)
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractPdfCover(source: Any): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = openParcelFileDescriptor(source) ?: return null
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null

            page = renderer.openPage(0)
            val srcWidth = page.width
            val srcHeight = page.height
            if (srcWidth <= 0 || srcHeight <= 0) return null

            val ratio = minOf(
                COVER_MAX_WIDTH.toFloat() / srcWidth,
                COVER_MAX_HEIGHT.toFloat() / srcHeight,
                2f // Allow crisp rendering
            )
            val dstWidth = (srcWidth * ratio).toInt().coerceIn(120, COVER_MAX_WIDTH)
            val dstHeight = (srcHeight * ratio).toInt().coerceIn(160, COVER_MAX_HEIGHT)

            val bitmap = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // PDF pages frequently have transparent background — default to white
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "extractPdfCover failed: ${e.message}")
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EPUB Manifest Heuristic Extraction (Readium Publication)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun extractEpubCoverFromManifest(publication: Publication): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check links in resources for cover keywords
            val coverKeywords = listOf("cover", "portada", "jacket", "front", "title")
            fun isImageLink(link: org.readium.r2.shared.publication.Link): Boolean {
                val media = link.mediaType
                val typeStr = media?.type?.lowercase().orEmpty()
                val hrefStr = link.href.toString().lowercase()
                return (media?.isBitmap == true) || typeStr.startsWith("image/") ||
                        hrefStr.endsWith(".jpg") || hrefStr.endsWith(".jpeg") ||
                        hrefStr.endsWith(".png") || hrefStr.endsWith(".webp")
            }

            val candidateLink = publication.manifest.resources.firstOrNull { link ->
                isImageLink(link) && coverKeywords.any { kw ->
                    link.href.toString().contains(kw, ignoreCase = true) ||
                    link.rels.any { rel -> rel.contains("cover", ignoreCase = true) }
                }
            } ?: publication.manifest.links.firstOrNull { link ->
                isImageLink(link) && coverKeywords.any { kw ->
                    link.href.toString().contains(kw, ignoreCase = true)
                }
            } ?: publication.manifest.resources.firstOrNull { isImageLink(it) }

            if (candidateLink != null) {
                val bytes = publication.get(candidateLink)?.read()?.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }

            // Check reading order first item (e.g. titlepage.xhtml)
            val firstChapterLink = publication.manifest.readingOrder.firstOrNull()
            if (firstChapterLink != null) {
                val html = publication.get(firstChapterLink)?.read()?.getOrNull()?.let { String(it, Charsets.UTF_8) }
                if (html != null) {
                    val imgPattern = Regex("""<(?:img|image)[^>]+(?:src|href|xlink:href)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val match = imgPattern.find(html)
                    if (match != null) {
                        val imgHref = match.groupValues[1].substringBefore('?')
                        val resolvedLink = publication.manifest.resources.firstOrNull {
                            it.href.toString().endsWith(imgHref.substringAfterLast('/'))
                        }
                        if (resolvedLink != null) {
                            val imgBytes = publication.get(resolvedLink)?.read()?.getOrNull()
                            if (imgBytes != null && imgBytes.isNotEmpty()) {
                                return@withContext BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                            }
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "extractEpubCoverFromManifest failed", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ZIP / CBZ Direct Archive Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractCoverFromZipArchive(source: Any, isEpub: Boolean): Bitmap? {
        val file = resolveAsFile(source)
        if (file != null && file.exists()) {
            return extractCoverFromZipFile(file, isEpub)
        }

        // Fallback using InputStream for content:// Uris
        return extractCoverFromZipStream(source, isEpub)
    }

    private fun extractCoverFromZipFile(file: File, isEpub: Boolean): Bitmap? {
        return try {
            ZipFile(file).use { zip ->
                val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")
                val entries = zip.entries().asSequence().filter { entry ->
                    !entry.isDirectory && imageExtensions.any { ext -> entry.name.lowercase().endsWith(ext) }
                }.toList()

                if (entries.isEmpty()) return null

                val targetEntry: ZipEntry? = if (isEpub) {
                    val coverKeywords = listOf("cover", "portada", "jacket", "front")
                    entries.firstOrNull { entry ->
                        val lower = entry.name.lowercase()
                        coverKeywords.any { kw -> lower.contains(kw) }
                    } ?: entries.firstOrNull()
                } else {
                    // CBZ: Sort by filename to get page 001
                    entries.minByOrNull { it.name.lowercase() }
                }

                if (targetEntry != null) {
                    zip.getInputStream(targetEntry).use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractCoverFromZipFile failed for ${file.name}", e)
            null
        }
    }

    private fun extractCoverFromZipStream(source: Any, isEpub: Boolean): Bitmap? {
        var input: InputStream? = null
        return try {
            input = openInputStream(source) ?: return null
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")
                val coverKeywords = listOf("cover", "portada", "jacket", "front")
                var firstImageBytes: ByteArray? = null

                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && imageExtensions.any { ext -> entry.name.lowercase().endsWith(ext) }) {
                        val lower = entry.name.lowercase()
                        val isCoverMatch = isEpub && coverKeywords.any { kw -> lower.contains(kw) }

                        val bytes = readStreamBytes(zis)
                        if (bytes.isNotEmpty()) {
                            if (isCoverMatch) {
                                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            if (firstImageBytes == null) {
                                firstImageBytes = bytes
                                if (!isEpub) {
                                    // For CBZ/comics, first valid image entry is good
                                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }

                firstImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractCoverFromZipStream failed: ${e.message}")
            null
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }



    // ─────────────────────────────────────────────────────────────────────────
    // Procedural Cover Generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a high-quality stylized book cover for books lacking any cover image
     * (e.g. TXT, MD, or text-only EPUBs).
     */
    fun generateProceduralCover(title: String, author: String): Bitmap {
        val width = 400
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Select palette based on title hash
        val paletteIndex = abs(title.hashCode()) % PALETTES.size
        val (topColor, bottomColor) = PALETTES[paletteIndex]

        // 1. Gradient Background
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            topColor, bottomColor, Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Decorative Border
        val borderPaint = Paint().apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val innerRect = RectF(24f, 24f, width - 24f, height - 24f)
        canvas.drawRoundRect(innerRect, 12f, 12f, borderPaint)

        // Decorative corner accents
        val cornerPaint = Paint().apply {
            color = Color.argb(140, 220, 180, 100) // Soft gold
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(32f, 32f, width - 32f, height - 32f), 8f, 8f, cornerPaint)

        // 3. Title Typography
        val titlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val textWidth = (width - 80).coerceAtLeast(100)
        val cleanTitle = title.trim().ifBlank { "Libro" }
        val titleLayout = StaticLayout.Builder.obtain(
            cleanTitle, 0, cleanTitle.length, titlePaint, textWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(5)
            .build()

        val titleY = (height * 0.32f) - (titleLayout.height / 2f)
        canvas.save()
        canvas.translate(width / 2f, titleY.coerceAtLeast(80f))
        titleLayout.draw(canvas)
        canvas.restore()

        // 4. Divider Line
        val dividerPaint = Paint().apply {
            color = Color.argb(180, 220, 180, 100) // Gold divider
            strokeWidth = 2f
            isAntiAlias = true
        }
        val dividerY = (titleY + titleLayout.height + 24f).coerceAtMost(height - 120f)
        canvas.drawLine((width / 2f) - 40f, dividerY, (width / 2f) + 40f, dividerY, dividerPaint)

        // 5. Author Typography
        val cleanAuthor = author.trim().ifBlank { "Woods Reader" }
        val authorPaint = TextPaint().apply {
            color = Color.argb(220, 230, 230, 230)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val authorLayout = StaticLayout.Builder.obtain(
            cleanAuthor, 0, cleanAuthor.length, authorPaint, textWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()

        canvas.save()
        canvas.translate(width / 2f, dividerY + 18f)
        authorLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun scaleBitmapToMaxDimensions(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val ratio = minOf(
            COVER_MAX_WIDTH.toFloat() / width,
            COVER_MAX_HEIGHT.toFloat() / height,
            1f // Do not upscale
        )

        return if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
    }

    private fun resolveAsFile(source: Any): File? {
        return when (source) {
            is File -> source
            is String -> if (source.startsWith("/")) File(source) else null
            is Uri -> {
                if (source.scheme == "file" && source.path != null) {
                    File(source.path!!)
                } else null
            }
            else -> null
        }
    }

    private fun openInputStream(source: Any): InputStream? {
        return try {
            when (source) {
                is File -> FileInputStream(source)
                is String -> {
                    if (source.startsWith("/")) FileInputStream(File(source))
                    else context.contentResolver.openInputStream(Uri.parse(source))
                }
                is Uri -> {
                    if (source.scheme == "file" && source.path != null) {
                        FileInputStream(File(source.path!!))
                    } else {
                        context.contentResolver.openInputStream(source)
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open InputStream for $source", e)
            null
        }
    }

    private fun openParcelFileDescriptor(source: Any): ParcelFileDescriptor? {
        return try {
            when (source) {
                is File -> ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
                is String -> {
                    if (source.startsWith("/")) {
                        ParcelFileDescriptor.open(File(source), ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        val uri = Uri.parse(source)
                        if (uri.scheme == "file" && uri.path != null) {
                            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                        } else {
                            context.contentResolver.openFileDescriptor(uri, "r")
                        }
                    }
                }
                is Uri -> {
                    if (source.scheme == "file" && source.path != null) {
                        ParcelFileDescriptor.open(File(source.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(source, "r")
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open ParcelFileDescriptor for $source", e)
            null
        }
    }

    private fun readStreamBytes(input: InputStream): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
