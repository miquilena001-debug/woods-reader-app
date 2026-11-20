package com.example.woodsreader.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.data.source.BookSourceFormatValidator
import com.example.woodsreader.data.source.ContentProviderIo
import com.example.woodsreader.di.DefaultDispatcher
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.example.woodsreader.core.pipeline.EpubPackageSanitizer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

@Singleton
class BookMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: Lazy<PublicationOpener>,
    private val summarizer: Summarizer,
    private val coverExtractor: CoverExtractor,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "BookMetadataExtractor"
        private const val EXTRACT_TIMEOUT_MS = 15_000L
        private const val MAX_PDF_SYNOPSIS_SOURCE_BYTES = 32L * 1_024L * 1_024L
    }

    suspend fun extractMetadata(file: File, fileHash: String): BookEntity? =
        extractFromSource(file, file.name, file.length(), fileHash)

    suspend fun extractMetadata(docFile: DocumentFile, fileHash: String): BookEntity? =
        extractFromSource(docFile.uri, docFile.name ?: "Unknown", docFile.length(), fileHash)

    suspend fun extractMetadata(uri: Uri, fileName: String, fileSize: Long, fileHash: String): BookEntity? =
        extractFromSource(uri, fileName, fileSize, fileHash)

    /**
     * Validates source bytes before opening them with Readium. Unreadable publications are rejected;
     * filename fallback is reserved for metadata failures after Readium opened the publication.
     */
    private suspend fun extractFromSource(
        source: Any,
        fileName: String,
        fileSize: Long,
        fileHash: String
    ): BookEntity? = withContext(Dispatchers.IO) {
        val filePath = extractPathFromSource(source)
        val format = detectSourceFormat(source) ?: return@withContext null

        try {
            withTimeout(EXTRACT_TIMEOUT_MS) {
                // Readium 3.0 asset retrieval:
                // MUST use assetRetriever.retrieve(File) for local files.
                // AbsoluteUrl with file:/// fails in Readium's ContentResolverAssetRetriever.
                val assetResult = try {
                    when (source) {
                        is File -> assetRetriever.retrieve(source)
                        is Uri -> {
                            if (source.scheme == "file") {
                                val path = source.path
                                if (path != null && File(path).exists()) {
                                    assetRetriever.retrieve(File(path))
                                } else {
                                    val url = AbsoluteUrl(source.toString())
                                    if (url != null) assetRetriever.retrieve(url) else null
                                }
                            } else {
                                val url = AbsoluteUrl(source.toString())
                                if (url != null) assetRetriever.retrieve(url) else null
                            }
                        }
                        is String -> {
                            if (source.startsWith("/")) {
                                assetRetriever.retrieve(File(source))
                            } else {
                                val uri = Uri.parse(source)
                                if (uri.scheme == "file" && uri.path != null) {
                                    assetRetriever.retrieve(File(uri.path!!))
                                } else {
                                    val url = AbsoluteUrl(source)
                                    if (url != null) assetRetriever.retrieve(url) else null
                                }
                            }
                        }
                        else -> return@withTimeout null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Asset retrieval failed (${e.javaClass.simpleName})")
                    return@withTimeout null
                }

                val asset = assetResult?.getOrNull()
                if (asset == null) {
                    Log.w(TAG, "Unable to retrieve validated book content")
                    return@withTimeout null
                }

                val publication = try {
                    val sanitizedAsset = EpubPackageSanitizer.transformAsset(asset)
                    publicationOpener.get().open(
                        asset = sanitizedAsset,
                        allowUserInteraction = false
                    ).getOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Publication validation failed (${e.javaClass.simpleName})")
                    return@withTimeout null
                }
                if (publication == null) {
                    Log.w(TAG, "Unable to open validated book publication")
                    return@withTimeout null
                }

                try {
                    val metadata = publication.manifest.metadata
                    val title = metadata.title ?: fileName.substringBeforeLast('.')
                    val author = metadata.authors.firstOrNull()?.name ?: "Unknown Author"
                    var description = metadata.description?.trim().orEmpty()

                    // Si no hay descripción en los metadatos o si es un PDF con metadatos no informativos/binarios
                    if (description.isBlank() || (format == BookFormat.PDF && (description.contains("%PDF") || description.contains("t.me/") || description.length < 40))) {
                        description = if (format == BookFormat.PDF) {
                            extractPdfSynopsis(source)
                        } else {
                            extractSynopsisFromContent(publication)
                        }
                    }

                    val language = metadata.languages.firstOrNull() ?: Locale.getDefault().language

                    // Extraer portada multiformato (con soporte nativo para PDF, EPUB, cómics y procedural)
                    val coverPath = coverExtractor.extractCover(
                        source = source,
                        format = format,
                        fileHash = fileHash,
                        title = title,
                        author = author,
                        publication = publication
                    )

                    BookEntity(
                        filePath = filePath,
                        title = title,
                        author = author,
                        language = language,
                        description = description,
                        format = format,
                        fileSizeBytes = fileSize,
                        fileHash = fileHash,
                        readingStatus = ReadingStatus.NOT_STARTED,
                        coverCachePath = coverPath,
                        importedAt = System.currentTimeMillis()
                    )
                } finally {
                    publication.close()
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Book metadata extraction timed out")
            throw java.io.IOException("Book metadata extraction timed out", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Book metadata extraction failed (${e.javaClass.simpleName})")
            extractFallbackMetadata(source, filePath, fileName, fileSize, fileHash, format)
        }
    }

    private suspend fun detectSourceFormat(source: Any): BookFormat? = when (source) {
        is File -> source.inputStream().use(BookSourceFormatValidator::detectFormat)
        is Uri -> if (source.scheme == "file" && source.path != null) {
            File(source.path!!).inputStream().use(BookSourceFormatValidator::detectFormat)
        } else {
            ContentProviderIo.run {
                context.contentResolver.openInputStream(source)
                    ?.let(BookSourceFormatValidator::detectFormat)
            }
        }
        is String -> {
            if (source.startsWith("/")) {
                File(source).inputStream().use(BookSourceFormatValidator::detectFormat)
            } else {
                val uri = Uri.parse(source)
                if (uri.scheme == "content") {
                    ContentProviderIo.run {
                        context.contentResolver.openInputStream(uri)
                            ?.let(BookSourceFormatValidator::detectFormat)
                    }
                } else {
                    val file = if (uri.scheme == "file" && uri.path != null) File(uri.path!!) else null
                    file?.inputStream()?.use(BookSourceFormatValidator::detectFormat)
                }
            }
        }
        else -> null
    }

    private fun extractPathFromSource(source: Any): String = when (source) {
        is File -> source.absolutePath
        is Uri -> source.toString()
        is String -> source
        else -> ""
    }

    private suspend fun extractFallbackMetadata(
        source: Any,
        filePath: String,
        fileName: String,
        fileSize: Long,
        fileHash: String,
        format: BookFormat
    ): BookEntity {
        val nameWithoutExt = fileName.substringBeforeLast('.')
        val (author, title) = if (" - " in nameWithoutExt) {
            val parts = nameWithoutExt.split(" - ", limit = 2)
            parts[0].trim() to parts[1].trim()
        } else {
            "Unknown Author" to nameWithoutExt.trim()
        }

        // Aun si Readium falla, CoverExtractor extrae la portada directamente
        // mediante PdfRenderer (PDF), ZIP (EPUB/CBZ), CBR (junrar) o portada procedural
        val coverPath = coverExtractor.extractCover(
            source = source,
            format = format,
            fileHash = fileHash,
            title = title,
            author = author,
            publication = null
        )

        val description = if (format == BookFormat.PDF) extractPdfSynopsis(source) else ""

        return BookEntity(
            filePath = filePath,
            title = title,
            author = author,
            language = "es",
            description = description,
            format = format,
            fileSizeBytes = fileSize,
            fileHash = fileHash,
            readingStatus = ReadingStatus.NOT_STARTED,
            coverCachePath = coverPath,
            importedAt = System.currentTimeMillis()
        )
    }

    /**
     * Extrae texto real de las primeras páginas del PDF usando PDFBox,
     * buscando encabezados explícitos de 'Sinopsis' o limpiando la narrativa inicial.
     */
    private suspend fun extractPdfSynopsis(source: Any): String = withContext(Dispatchers.IO) {
        try {
            val sourceFile = when (source) {
                is File -> source
                is Uri -> if (source.scheme == "file") source.path?.let(::File) else null
                is String -> if (source.startsWith("/")) {
                    File(source)
                } else {
                    Uri.parse(source).takeIf { it.scheme == "file" }?.path?.let(::File)
                }
                else -> null
            }
            if (sourceFile == null || sourceFile.length() > MAX_PDF_SYNOPSIS_SOURCE_BYTES) {
                return@withContext ""
            }

            val inputStream = when (source) {
                is File -> source.inputStream()
                is Uri -> sourceFile.inputStream()
                is String -> {
                    sourceFile.inputStream()
                }
                else -> null
            } ?: return@withContext ""

            var rawText = ""
            inputStream.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val totalPages = doc.numberOfPages
                    val pagesToScan = minOf(totalPages, 8)
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    stripper.startPage = 1
                    stripper.endPage = pagesToScan
                    rawText = stripper.getText(doc) ?: ""
                }
            }

            if (rawText.isBlank()) return@withContext ""

            // 1. Buscar si hay una sección explícita de "Sinopsis / Synopsis / Resumen / Argumento"
            val synopsisPatterns = listOf(
                Regex("""(?i)(?:^|\n)\s*(?:sinopsis|argumento|resumen|synopsis|summary)\s*[:\-\n]+\s*([^\n]+(?:\n(?!\s*(?:cap[íi]tulo|índice|index|tabla|volumen|prólogo|créditos|\d+[\.\s]))[^\n]+){1,10})"""),
                Regex("""(?i)sinopsis\s*[:\-\n]+\s*([^\n\r]+(?:\n[^\n\r]+){1,8})""")
            )

            for (pattern in synopsisPatterns) {
                val match = pattern.find(rawText)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    val cleaned = cleanSynopsisText(candidate)
                    if (cleaned.length >= 60) {
                        return@withContext cleaned
                    }
                }
            }

            // 2. Si no hay encabezado explícito, limpiar el texto descartando ruido técnico y resumir
            val cleanParagraphs = rawText.lines()
                .map { it.trim() }
                .filter { line ->
                    line.length >= 35 &&
                    !line.contains("http", ignoreCase = true) &&
                    !line.contains("t.me/", ignoreCase = true) &&
                    !line.contains("traducido", ignoreCase = true) &&
                    !line.contains("recopilado", ignoreCase = true) &&
                    !line.contains("copyright", ignoreCase = true) &&
                    !line.contains("todos los derechos", ignoreCase = true) &&
                    !line.matches(Regex("""^(?:pág(?:ina)?\s*\d+|\d+)$""", RegexOption.IGNORE_CASE))
                }

            if (cleanParagraphs.isEmpty()) return@withContext ""

            val combinedText = cleanParagraphs.take(15).joinToString("\n\n")
            if (combinedText.length >= 150) {
                val summary = withContext(defaultDispatcher) {
                    summarizer.summarize(combinedText, numSentences = 3)
                }
                if (summary.isNotBlank()) return@withContext summary
            }

            cleanParagraphs.firstOrNull { it.length in 80..400 } ?: ""
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Error extracting PDF synopsis (${e.javaClass.simpleName})")
            ""
        }
    }

    private fun cleanSynopsisText(text: String): String {
        return text
            .replace(Regex("""(?i)https?://\S+"""), "")
            .replace(Regex("""(?i)t\.me/\S+"""), "")
            .replace(Regex("""(?i)traducido\s+por\s+[^\n.]+"""), "")
            .replace(Regex("""(?i)recopilado\s+por\s+[^\n.]+"""), "")
            .replace("\r", "")
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                !line.contains("copyright", ignoreCase = true) &&
                !line.contains("todos los derechos", ignoreCase = true) &&
                !line.matches(Regex("""^(?:pág(?:ina)?\s*\d+|\d+)$""", RegexOption.IGNORE_CASE))
            }
            .joinToString("\n\n")
            .trim()
    }

    /**
     * Extracts a synopsis from the book's own content using TextRank.
     * Tries the first 3 chapters, picks the one with most text.
     * Returns empty string if content is insufficient.
     */
    private suspend fun extractSynopsisFromContent(publication: Publication): String =
        withContext(Dispatchers.IO) {
            try {
                val chapters = publication.readingOrder.take(3)
                var bestText = ""

                for (link in chapters) {
                    try {
                        val resource = publication.get(link) ?: continue
                        // Readium 3.x: read() returns Try<ByteArray, ReadError>
                        val html = resource.read().getOrNull()
                            ?.let { String(it, Charsets.UTF_8) } ?: continue
                        val plainText = summarizer.stripHtml(html)
                        if (plainText.length > bestText.length) bestText = plainText
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) { }
                }

                if (bestText.length < 200) return@withContext ""

                withContext(defaultDispatcher) {
                    summarizer.summarize(bestText, numSentences = 4)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Synopsis extraction failed (${e.javaClass.simpleName})")
                ""
            }
        }

}
