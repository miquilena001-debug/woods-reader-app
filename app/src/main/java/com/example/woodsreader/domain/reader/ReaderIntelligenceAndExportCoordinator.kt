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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        startIndex: Int
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
                FastEpubSearchEngine.searchPublicationStreaming(
                    bookId = bookId,
                    publication = pub,
                    rawQuery = query,
                    startFromChapterIndex = startIndex
                ).collect { progress ->
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
}
