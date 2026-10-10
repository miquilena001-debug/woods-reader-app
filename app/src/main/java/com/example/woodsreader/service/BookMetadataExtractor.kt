package com.example.woodsreader.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingStatus
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

@Singleton
class BookMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: Lazy<PublicationOpener>,
    private val summarizer: Summarizer,
    private val coverExtractor: CoverExtractor
) {
    companion object {
        private const val TAG = "BookMetadataExtractor"
        private const val EXTRACT_TIMEOUT_MS = 15_000L
    }

    suspend fun extractMetadata(file: File, fileHash: String): BookEntity? =
        extractFromSource(file, file.name, file.length(), fileHash)

    suspend fun extractMetadata(docFile: DocumentFile, fileHash: String): BookEntity? =
        extractFromSource(docFile.uri, docFile.name ?: "Unknown", docFile.length(), fileHash)

    suspend fun extractMetadata(uri: Uri, fileName: String, fileSize: Long, fileHash: String): BookEntity? =
        extractFromSource(uri, fileName, fileSize, fileHash)

    /**
     * Extracts full metadata from the book source using Readium.
     * If Readium fails or times out, it gracefully degrades to a fallback strategy
     * that parses basic information from the filename while still ensuring cover extraction.
     */
    private suspend fun extractFromSource(
        source: Any,
        fileName: String,
        fileSize: Long,
        fileHash: String
    ): BookEntity? = withContext(Dispatchers.IO) {
        val filePath = extractPathFromSource(source)
        val format = determineFormat(fileName)

        try {
            withTimeout(EXTRACT_TIMEOUT_MS) {
                // Readium 3.0 asset retrieval:
                // MUST use assetRetriever.retrieve(File) for local files.
                // AbsoluteUrl with file:/// fails in Readium's ContentResolverAssetRetriever.
                val assetResult = when (source) {
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

                val asset = assetResult?.getOrNull()
                if (asset == null) {
                    Log.w(TAG, "Failed to retrieve asset for $fileName — using fallback extractor")
                    return@withTimeout extractFallbackMetadata(source, filePath, fileName, fileSize, fileHash, format)
                }

                val publication = publicationOpener.get().open(asset, allowUserInteraction = false).getOrNull()
                if (publication == null) {
                    Log.w(TAG, "Failed to open publication for $fileName — using fallback extractor")
                    return@withTimeout extractFallbackMetadata(source, filePath, fileName, fileSize, fileHash, format)
                }

                try {
                    val metadata = publication.manifest.metadata
                    val title = metadata.title ?: fileName.substringBeforeLast('.')
                    val author = metadata.authors.firstOrNull()?.name ?: "Unknown Author"
                    var description = metadata.description ?: ""

                    // Si no hay descripción en los metadatos, extraerla del contenido
                    // usando TextRank (extractivo, local, sin internet)
                    if (description.isBlank()) {
                        description = extractSynopsisFromContent(publication)
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
        } catch (e: Exception) {
            val reason = if (e is kotlinx.coroutines.TimeoutCancellationException) "Timeout" else e.message
            Log.e(TAG, "Error extracting metadata from $fileName ($reason)", e)
            extractFallbackMetadata(source, filePath, fileName, fileSize, fileHash, format)
        }
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

        return BookEntity(
            filePath = filePath,
            title = title,
            author = author,
            language = "es",
            description = "",
            format = format,
            fileSizeBytes = fileSize,
            fileHash = fileHash,
            readingStatus = ReadingStatus.NOT_STARTED,
            coverCachePath = coverPath,
            importedAt = System.currentTimeMillis()
        )
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
                    } catch (_: Exception) { }
                }

                if (bestText.length < 200) return@withContext ""

                summarizer.summarize(bestText, numSentences = 4)
            } catch (e: Exception) {
                Log.w(TAG, "extractSynopsisFromContent failed", e)
                ""
            }
        }

    private fun determineFormat(fileName: String): BookFormat =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "epub" -> BookFormat.EPUB
            "pdf" -> BookFormat.PDF
            "mobi" -> BookFormat.MOBI
            "azw" -> BookFormat.AZW
            "azw3" -> BookFormat.AZW3
            "cbz" -> BookFormat.CBZ
            "cbr" -> BookFormat.CBR
            "cbt" -> BookFormat.CBT
            "docx" -> BookFormat.DOCX
            "odt" -> BookFormat.ODT
            "rtf" -> BookFormat.RTF
            "md" -> BookFormat.MD
            "txt" -> BookFormat.TXT
            "fb2" -> BookFormat.FB2
            else -> BookFormat.UNKNOWN
        }
}
