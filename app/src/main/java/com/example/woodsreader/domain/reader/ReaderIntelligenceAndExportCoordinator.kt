package com.example.woodsreader.domain.reader

import android.content.Context
import android.util.Log
import com.example.woodsreader.core.pipeline.ReadingBlock
import com.example.woodsreader.core.search.FastEpubSearchEngine
import com.example.woodsreader.core.search.FastInBookSearchHit
import com.example.woodsreader.core.search.FastSearchProgress
import com.example.woodsreader.core.tts.AudioExportState
import com.example.woodsreader.core.tts.ChapterAudioExporter
import com.example.woodsreader.core.tts.ChapterTextChunk
import com.example.woodsreader.core.xray.BookXRayEngine
import com.example.woodsreader.core.xray.ChapterXRayReport
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.ReflowItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinador de inteligencia y exportación del lector:
 * - Búsqueda en-libro streaming con FastEpubSearchEngine.
 * - Análisis y reporte contextual Rayos-X (BookXRayEngine).
 * - Orquestación de exportación de capítulos a audio .M4A (ChapterAudioExporter).
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class ReaderIntelligenceAndExportCoordinator @Inject constructor(
    private val chapterAudioExporter: ChapterAudioExporter
) {
    companion object {
        private const val TAG = "ReaderCoord"
    }

    // ── Búsqueda En-Libro Streaming ──────────────────────────────────────────

    private var inBookSearchJob: Job? = null
    private val _inBookSearchResults = MutableStateFlow<List<FastInBookSearchHit>>(emptyList())
    val inBookSearchResults: StateFlow<List<FastInBookSearchHit>> = _inBookSearchResults.asStateFlow()

    private val _inBookSearchProgress = MutableStateFlow<FastSearchProgress?>(null)
    val inBookSearchProgress: StateFlow<FastSearchProgress?> = _inBookSearchProgress.asStateFlow()

    private val _isInBookSearching = MutableStateFlow(false)
    val isInBookSearching: StateFlow<Boolean> = _isInBookSearching.asStateFlow()

    fun searchInBook(
        scope: CoroutineScope,
        bookId: String,
        publication: Publication?,
        query: String,
        startIndex: Int,
        isPdf: Boolean = false,
        pdfFile: File? = null
    ) {
        val pub = publication ?: return
        if (query.isBlank()) {
            cancelInBookSearch()
            return
        }

        inBookSearchJob?.cancel()
        inBookSearchJob = scope.launch {
            _isInBookSearching.value = true
            _inBookSearchResults.value = emptyList()
            _inBookSearchProgress.value = null

            try {
                val flow = if (isPdf && pdfFile != null && pdfFile.exists()) {
                    FastEpubSearchEngine.searchPdfStreaming(
                        bookId = bookId,
                        pdfFile = pdfFile,
                        publication = pub,
                        rawQuery = query,
                        startFromPage = startIndex.coerceAtLeast(1)
                    )
                } else {
                    FastEpubSearchEngine.searchPublicationStreaming(
                        bookId = bookId,
                        publication = pub,
                        rawQuery = query,
                        startFromChapterIndex = startIndex
                    )
                }
                flow.collect { progress ->
                    _inBookSearchResults.value = progress.hits
                    _inBookSearchProgress.value = progress
                    if (progress.isFinished) {
                        _isInBookSearching.value = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in searchInBook: ${e.message}", e)
                _isInBookSearching.value = false
            } finally {
                _isInBookSearching.value = false
            }
        }
    }

    fun cancelInBookSearch() {
        inBookSearchJob?.cancel()
        inBookSearchJob = null
        _isInBookSearching.value = false
    }

    fun clearSearchCache() {
        cancelInBookSearch()
        _inBookSearchResults.value = emptyList()
        _inBookSearchProgress.value = null
        FastEpubSearchEngine.clearCache()
    }

    // ── Motor Rayos-X (BookXRayEngine) ──────────────────────────────────────

    fun generateXRayReport(
        chapterTitle: String,
        epubBlocks: List<ReadingBlock>?,
        cachedDocumentBlocks: List<ReadingBlock>?,
        reflowItems: List<ReflowItem>?,
        currentParagraphIndex: Int?,
        ttsActiveBlockIndex: Int,
        isReflowMode: Boolean,
        visualProgression: Double
    ): ChapterXRayReport {
        val title = chapterTitle.ifBlank { "Capítulo actual" }
        val effectiveBlocks = epubBlocks ?: cachedDocumentBlocks

        val paragraphs = if (!effectiveBlocks.isNullOrEmpty()) {
            effectiveBlocks.mapNotNull { block ->
                when (block) {
                    is ReadingBlock.Paragraph -> block.text
                    is ReadingBlock.Heading -> block.text
                    else -> null
                }
            }.filter { it.isNotBlank() }
        } else {
            reflowItems?.mapNotNull { item ->
                when (item) {
                    is ReflowItem.Paragraph -> item.content
                    is ReflowItem.Header -> item.content
                    is ReflowItem.Text -> item.element.locator.text.highlight
                    else -> null
                }
            }?.filter { it.isNotBlank() } ?: emptyList()
        }

        val currentIdx = currentParagraphIndex ?: run {
            val ttsIdx = if (isReflowMode) ttsActiveBlockIndex else -1
            if (ttsIdx in paragraphs.indices) {
                ttsIdx
            } else {
                if (paragraphs.isNotEmpty()) {
                    ((visualProgression * paragraphs.size).toInt()).coerceIn(0, paragraphs.size - 1)
                } else 0
            }
        }

        return BookXRayEngine.analyzeChapter(
            chapterTitle = title,
            paragraphs = paragraphs,
            currentParagraphIndex = currentIdx
        )
    }

    // ── Orquestación de Exportación de Audio .M4A ───────────────────────────

    val audioExportState: StateFlow<AudioExportState> = chapterAudioExporter.exportState

    fun resetAudioExportState() {
        chapterAudioExporter.resetState()
    }

    fun cancelAudioExport() {
        chapterAudioExporter.cancelExport()
    }

    fun resolveCoverFile(book: BookEntity, context: Context): File? {
        return book.coverCachePath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
            ?: File(context.filesDir, "covers/cover_${book.fileHash}.jpg").takeIf { it.exists() && it.length() > 0 }
            ?: File(context.cacheDir, "covers/cover_${book.fileHash}.jpg").takeIf { it.exists() && it.length() > 0 }
    }

    fun exportReflowChapter(
        scope: CoroutineScope,
        book: BookEntity,
        chapterTitle: String,
        items: List<ReflowItem>,
        speed: Float,
        coverFile: File?
    ) {
        val textItems = items.mapNotNull {
            when (it) {
                is ReflowItem.Paragraph -> it.content
                is ReflowItem.Header -> it.content
                else -> null
            }
        }
        val job = scope.launch {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = book.title,
                exportLabel = chapterTitle,
                totalChaptersCount = 1,
                chapterProvider = { ChapterTextChunk(chapterTitle, textItems) },
                speed = speed,
                coverImageFile = coverFile,
                bookAuthor = book.author
            )
        }
        chapterAudioExporter.setActiveJob(job)
    }

    fun exportEpubChapters(
        scope: CoroutineScope,
        book: BookEntity,
        readingOrder: List<Link>,
        currentHref: String,
        startChapTitle: String,
        maxChaptersToExport: Int,
        speed: Float,
        coverFile: File?,
        resolveChapterChunk: suspend (targetIdx: Int, link: Link) -> ChapterTextChunk?
    ) {
        val cleanHref = currentHref.substringBefore('#')
        val startIdx = readingOrder.indexOfFirst {
            it.href.toString().substringBefore('#') == cleanHref ||
                it.href.toString().substringAfterLast('/') == cleanHref.substringAfterLast('/')
        }.coerceAtLeast(0)

        val availableCount = (readingOrder.size - startIdx).coerceAtLeast(1)
        val countToExport = minOf(maxChaptersToExport, availableCount)
        val label = when {
            countToExport == 1 -> startChapTitle
            countToExport >= readingOrder.size -> "Libro_Completo"
            else -> "Caps_${startIdx + 1}_a_${startIdx + countToExport}"
        }

        val job = scope.launch {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = book.title,
                exportLabel = label,
                totalChaptersCount = countToExport,
                chapterProvider = { offset ->
                    val targetIdx = startIdx + offset
                    val link = readingOrder.getOrNull(targetIdx) ?: return@exportChaptersToM4a null
                    resolveChapterChunk(targetIdx, link)
                },
                speed = speed,
                coverImageFile = coverFile,
                bookAuthor = book.author
            )
        }
        chapterAudioExporter.setActiveJob(job)
    }

    fun exportPdfChapters(
        scope: CoroutineScope,
        book: BookEntity,
        pdfFile: File,
        startPage: Int,
        maxChaptersOrPagesToExport: Int,
        speed: Float,
        coverFile: File?,
        publication: Publication? = null,
        pdfPageExtractor: ((doc: PDDocument, pageFrom: Int, pageTo: Int) -> String)? = null
    ) {
        val job = scope.launch {
            try {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdfFile).use { doc ->
                    val totalPages = doc.numberOfPages
                    if (totalPages == 0) return@use

                    val scannedChapters = com.example.woodsreader.core.pdf.PdfParagraphHealer.scanChapters(
                        document = doc,
                        pageTextExtractor = pdfPageExtractor?.let { extractor ->
                            { d, p -> extractor(d, p, p) }
                        }
                    )
                    val stripper = if (pdfPageExtractor == null) runCatching { com.tom_roush.pdfbox.text.PDFTextStripper() }.getOrNull() else null

                    if (scannedChapters.isNotEmpty()) {
                        val safeStartPage = startPage.coerceIn(1, totalPages)
                        val startChapIdx = scannedChapters.indexOfFirst { it.pageNumber >= safeStartPage }
                            .takeIf { it >= 0 } ?: 0
                        val countToExport = minOf(maxChaptersOrPagesToExport, scannedChapters.size - startChapIdx).coerceAtLeast(1)
                        val label = when {
                            countToExport == 1 -> scannedChapters[startChapIdx].title
                            countToExport >= scannedChapters.size -> "Libro_Completo"
                            else -> "Caps_${startChapIdx + 1}_a_${startChapIdx + countToExport}"
                        }

                        chapterAudioExporter.exportChaptersToM4a(
                            bookTitle = book.title,
                            exportLabel = label,
                            totalChaptersCount = countToExport,
                            chapterProvider = { offset ->
                                val chIdx = startChapIdx + offset
                                val currentChap = scannedChapters.getOrNull(chIdx) ?: return@exportChaptersToM4a null
                                val nextChap = scannedChapters.getOrNull(chIdx + 1)
                                val pageFrom = currentChap.pageNumber
                                val pageTo = nextChap?.let { (it.pageNumber - 1).coerceAtLeast(pageFrom) } ?: totalPages

                                val rawText = if (pdfPageExtractor != null) {
                                    pdfPageExtractor(doc, pageFrom, pageTo)
                                } else if (stripper != null) {
                                    stripper.startPage = pageFrom
                                    stripper.endPage = pageTo
                                    try { stripper.getText(doc) } catch (_: Throwable) { "" }
                                } else {
                                    ""
                                }
                                val healed = com.example.woodsreader.core.pdf.PdfParagraphHealer
                                    .healParagraphs(rawText.lines())
                                ChapterTextChunk(currentChap.title, healed)
                            },
                            speed = speed,
                            coverImageFile = coverFile,
                            bookAuthor = book.author
                        )
                    } else {
                        val safeStart = startPage.coerceIn(1, totalPages)
                        val countToExport = minOf(maxChaptersOrPagesToExport, totalPages - safeStart + 1).coerceAtLeast(1)
                        val label = when {
                            countToExport == 1 -> "Pagina_$safeStart"
                            countToExport >= totalPages -> "Libro_Completo"
                            else -> "Pags_${safeStart}_a_${safeStart + countToExport - 1}"
                        }

                        chapterAudioExporter.exportChaptersToM4a(
                            bookTitle = book.title,
                            exportLabel = label,
                            totalChaptersCount = countToExport,
                            chapterProvider = { offset ->
                                val targetPage = safeStart + offset
                                if (targetPage > totalPages) return@exportChaptersToM4a null
                                val rawText = if (pdfPageExtractor != null) {
                                    pdfPageExtractor(doc, targetPage, targetPage)
                                } else if (stripper != null) {
                                    stripper.startPage = targetPage
                                    stripper.endPage = targetPage
                                    try { stripper.getText(doc) } catch (_: Throwable) { "" }
                                } else {
                                    ""
                                }
                                val healed = com.example.woodsreader.core.pdf.PdfParagraphHealer
                                    .healParagraphs(rawText.lines())
                                ChapterTextChunk("Página $targetPage", healed)
                            },
                            speed = speed,
                            coverImageFile = coverFile,
                            bookAuthor = book.author
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error exportando audio PDF: ${e.message}", e)
            }
        }
        chapterAudioExporter.setActiveJob(job)
    }

    fun exportAudio(
        scope: CoroutineScope,
        book: BookEntity,
        publication: Publication,
        isPdfBook: Boolean,
        isReflowMode: Boolean,
        startChapTitle: String,
        maxChaptersToExport: Int,
        speed: Float,
        coverFile: File?,
        currentTtsPage: Int,
        reflowItems: List<ReflowItem>,
        currentHref: String,
        resolveChapterChunk: suspend (targetIdx: Int, link: Link) -> ChapterTextChunk?
    ) {
        val pdfFile = if (isPdfBook) File(book.filePath).takeIf { it.exists() } else null
        if (pdfFile != null) {
            val startPage = currentTtsPage.takeIf { it > 0 } ?: 1
            exportPdfChapters(
                scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO), book = book, pdfFile = pdfFile, startPage = startPage,
                maxChaptersOrPagesToExport = maxChaptersToExport, speed = speed,
                coverFile = coverFile, publication = publication
            )
        } else if (isReflowMode || isPdfBook) {
            exportReflowChapter(
                scope = scope, book = book, chapterTitle = startChapTitle,
                items = reflowItems, speed = speed, coverFile = coverFile
            )
        } else {
            exportEpubChapters(
                scope = scope, book = book, readingOrder = publication.readingOrder,
                currentHref = currentHref, startChapTitle = startChapTitle,
                maxChaptersToExport = maxChaptersToExport, speed = speed,
                coverFile = coverFile, resolveChapterChunk = resolveChapterChunk
            )
        }
    }
}
