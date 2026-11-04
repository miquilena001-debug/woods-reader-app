package com.example.woodsreader.domain.reader

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.example.woodsreader.core.pipeline.ProcessedDocument
import com.example.woodsreader.ui.reader.EpubFormatManager
import com.example.woodsreader.ui.reader.goBackwardCompat
import com.example.woodsreader.ui.reader.goForwardCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedChapterData(
    val index: Int,
    val href: String,
    val title: String,
    val document: ProcessedDocument
)

enum class ChapterDirection { NEXT, PREVIOUS }

/**
 * Coordinador de navegación y precarga de capítulos del lector:
 * - Caché LRU de documentos XHTML procesados ([ParsedChapterData]) para cambio instantáneo (<50ms).
 * - Algoritmo de correspondencia tolerante de recursos/spines ([findMatchingResourceIndex]).
 * - Precarga asíncrona en baja prioridad de capítulos adyacentes ([prefetchSurroundingChapters]).
 * - Orquestación de salto manual ([goToNextChapter], [goToPreviousChapter]) y avance automático de TTS ([advanceTtsToNextChapter]).
 * - Resolución desacoplada de destinos por tabla de contenidos (TOC) para libros PDF.
 */
@Singleton
class ReaderChapterNavigationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epubFormatManager: EpubFormatManager
) {
    companion object {
        private const val TAG = "ReaderChapterNav"
        private const val MAX_CACHED_CHAPTERS = 5
        private const val CHAPTER_ADVANCE_DEBOUNCE_MS = 1200L
        private const val PREFETCH_DELAY_MS = 5000L

        private val PDF_PAGE_PARAM_REGEX = Regex("""(?i)[#?&]page=(\d+)""")
        private val PDF_PAGE_PATH_REGEX = Regex("""(?i)/page[_-]?(\d+)""")
        private val PDF_PAGE_HASH_REGEX = Regex("""#(\d+)$""")
        private val PDF_PAGE_NUMERIC_REGEX = Regex("""^/?(\d+)$""")

        fun extractPdfPagesFromToc(toc: List<Link>): List<Int> {
            val pages = mutableListOf<Int>()
            fun traverse(links: List<Link>) {
                for (link in links) {
                    val href = link.href.toString()
                    val page = PDF_PAGE_PARAM_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                        ?: PDF_PAGE_PATH_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                        ?: PDF_PAGE_HASH_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                        ?: PDF_PAGE_NUMERIC_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                    if (page != null && page > 0) {
                        pages.add(page)
                    }
                    if (link.children.isNotEmpty()) {
                        traverse(link.children)
                    }
                }
            }
            traverse(toc)
            return pages.distinct().sorted()
        }

        fun resolveNextPdfPageFromToc(toc: List<Link>, currentPage: Int): Int? {
            val pages = extractPdfPagesFromToc(toc)
            return pages.firstOrNull { it > currentPage }
        }

        fun resolvePreviousPdfPageFromToc(toc: List<Link>, currentPage: Int): Int? {
            val pages = extractPdfPagesFromToc(toc)
            return pages.lastOrNull { it < currentPage }
        }
    }

    val chapterLruCache = LruCache<Int, ParsedChapterData>(MAX_CACHED_CHAPTERS)
    private var prefetchJob: Job? = null
    private val lastChapterAdvanceTime = AtomicLong(0L)

    fun findMatchingResourceIndex(
        rawHref: String,
        order: List<Link>,
        resourceIndexCache: Map<String, Int>? = null
    ): Int {
        if (rawHref.isBlank() || order.isEmpty()) return -1

        // Estrategia 0: Búsqueda O(1) ultrarrápida en resourceIndexCache
        resourceIndexCache?.get(rawHref)?.let { return it }
        val beforeHash = rawHref.substringBefore('#')
        resourceIndexCache?.get(beforeHash)?.let { return it }
        val filename = rawHref.substringAfterLast('/')
        resourceIndexCache?.get(filename)?.let { return it }
        resourceIndexCache?.get(filename.substringBefore('#'))?.let { return it }

        fun normalize(str: String): String {
            val decoded = try {
                java.net.URLDecoder.decode(str, "UTF-8")
            } catch (_: Exception) {
                str
            }
            return decoded
                .substringBefore('#')
                .substringBefore('?')
                .replace('\\', '/')
                .trimStart('/')
                .lowercase(Locale.ROOT)
        }

        val target = normalize(rawHref)
        resourceIndexCache?.get(target)?.let { return it }

        // Estrategia fallback sólo si no coincidió en la caché
        for (i in order.indices) {
            val item = normalize(order[i].href.toString())
            if (item == target || item.endsWith("/$target") || target.endsWith("/$item") || item.substringAfterLast('/') == target.substringAfterLast('/')) {
                return i
            }
        }
        return -1
    }

    fun prefetchSurroundingChapters(
        scope: CoroutineScope,
        bookId: String,
        publication: Publication?,
        isPdfBook: Boolean,
        currentIndex: Int
    ) {
        val pub = publication ?: return
        if (isPdfBook) return
        val order = pub.readingOrder
        if (order.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.Default) {
            delay(PREFETCH_DELAY_MS)

            // 1. Siguiente capítulo (prioridad máxima)
            val nextIdx = currentIndex + 1
            if (nextIdx < order.size && chapterLruCache.get(nextIdx) == null) {
                val nextLink = order[nextIdx]
                val nextHref = nextLink.href.toString()
                val doc = withContext(Dispatchers.IO) {
                    epubFormatManager.processResource(nextHref, bookId)
                }
                if (doc != null) {
                    val title = nextLink.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${nextIdx + 1}"
                    chapterLruCache.put(nextIdx, ParsedChapterData(nextIdx, nextHref, title, doc))
                    Log.d(TAG, "Prefetched next chapter ($nextIdx/${order.size}): $nextHref")
                }
            }

            // 2. Capítulo anterior
            val prevIdx = currentIndex - 1
            if (prevIdx >= 0 && chapterLruCache.get(prevIdx) == null) {
                val prevLink = order[prevIdx]
                val prevHref = prevLink.href.toString()
                val doc = withContext(Dispatchers.IO) {
                    epubFormatManager.processResource(prevHref, bookId)
                }
                if (doc != null) {
                    val title = prevLink.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${prevIdx + 1}"
                    chapterLruCache.put(prevIdx, ParsedChapterData(prevIdx, prevHref, title, doc))
                    Log.d(TAG, "Prefetched prev chapter ($prevIdx/${order.size}): $prevHref")
                }
            }

            // 3. Siguiente del siguiente (para saltos rápidos continuos)
            val nextNextIdx = currentIndex + 2
            if (nextNextIdx < order.size && chapterLruCache.get(nextNextIdx) == null) {
                val nextNextLink = order[nextNextIdx]
                val nextNextHref = nextNextLink.href.toString()
                val doc = withContext(Dispatchers.IO) {
                    epubFormatManager.processResource(nextNextHref, bookId)
                }
                if (doc != null) {
                    val title = nextNextLink.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${nextNextIdx + 1}"
                    chapterLruCache.put(nextNextIdx, ParsedChapterData(nextNextIdx, nextNextHref, title, doc))
                }
            }
        }
    }

    fun navigatePdfChapter(
        publication: Publication?,
        navigator: Navigator?,
        isReflowMode: Boolean,
        direction: ChapterDirection,
        currentPdfPage: Int,
        ttsIsPlaying: Boolean,
        onJumpToReflowPage: (Int) -> Unit,
        onPlayPdfPageTts: (Int) -> Unit,
        onPlayTts: (Locator) -> Unit,
        onSetLastTtsLocator: (Locator) -> Unit
    ): Boolean {
        val pub = publication ?: return false
        val toc = pub.tableOfContents
        if (toc.isEmpty()) return false

        val targetPage = when (direction) {
            ChapterDirection.NEXT -> resolveNextPdfPageFromToc(toc, currentPdfPage)
            ChapterDirection.PREVIOUS -> resolvePreviousPdfPageFromToc(toc, currentPdfPage)
        }

        if (targetPage != null) {
            if (isReflowMode) {
                onJumpToReflowPage(targetPage)
                if (ttsIsPlaying) {
                    onPlayPdfPageTts(targetPage)
                }
            } else {
                val nav = navigator as? VisualNavigator
                val readingOrderLink = pub.readingOrder.firstOrNull()
                val targetLoc = readingOrderLink?.let { pub.locatorFromLink(it) }
                    ?.copy(locations = Locator.Locations(position = targetPage))
                if (targetLoc != null) {
                    nav?.go(targetLoc, animated = false)
                    if (ttsIsPlaying) {
                        onPlayTts(targetLoc)
                    } else {
                        onSetLastTtsLocator(targetLoc)
                    }
                }
            }
            return true
        }
        return false
    }

    fun goToNextChapter(
        scope: CoroutineScope,
        bookId: String,
        publication: Publication?,
        navigator: Navigator?,
        isPdfBook: Boolean,
        isReflowMode: Boolean,
        currentPdfPage: Int,
        ttsIsPlaying: Boolean,
        resourceIndexCache: Map<String, Int>?,
        onJumpToReflowPage: (Int) -> Unit,
        onPlayPdfPageTts: (Int) -> Unit,
        onPlayTts: (Locator) -> Unit,
        onSetLastTtsLocator: (Locator) -> Unit
    ) {
        val pub = publication ?: return

        if (isPdfBook || isReflowMode) {
            navigatePdfChapter(
                publication = pub,
                navigator = navigator,
                isReflowMode = isReflowMode,
                direction = ChapterDirection.NEXT,
                currentPdfPage = currentPdfPage,
                ttsIsPlaying = ttsIsPlaying,
                onJumpToReflowPage = onJumpToReflowPage,
                onPlayPdfPageTts = onPlayPdfPageTts,
                onPlayTts = onPlayTts,
                onSetLastTtsLocator = onSetLastTtsLocator
            )
            return
        }

        val nav = navigator as? VisualNavigator ?: return
        val href = nav.currentLocator.value.href.toString()
        val order = pub.readingOrder
        val currentIdx = findMatchingResourceIndex(href, order, resourceIndexCache).takeIf { it != -1 } ?: resourceIndexCache?.get(href) ?: -1

        if (currentIdx != -1 && currentIdx < order.size - 1) {
            val nextIdx = currentIdx + 1
            val nextLink = order[nextIdx]
            val nextLocator = pub.locatorFromLink(nextLink)?.copy(
                locations = Locator.Locations(progression = 0.0)
            )
            chapterLruCache.get(nextIdx)?.let { cached ->
                epubFormatManager.putCachedDocument(nextLink.href.toString(), cached.document)
            }
            if (nextLocator != null) {
                nav.go(nextLocator, animated = false)
                if (ttsIsPlaying) {
                    onPlayTts(nextLocator)
                } else {
                    onSetLastTtsLocator(nextLocator)
                }
            } else {
                nav.go(nextLink, animated = false)
            }
            prefetchSurroundingChapters(scope, bookId, pub, isPdfBook, nextIdx)
        } else {
            nav.goForwardCompat(animated = true)
        }
    }

    fun goToPreviousChapter(
        scope: CoroutineScope,
        bookId: String,
        publication: Publication?,
        navigator: Navigator?,
        isPdfBook: Boolean,
        isReflowMode: Boolean,
        currentPdfPage: Int,
        ttsIsPlaying: Boolean,
        resourceIndexCache: Map<String, Int>?,
        onJumpToReflowPage: (Int) -> Unit,
        onPlayPdfPageTts: (Int) -> Unit,
        onPlayTts: (Locator) -> Unit,
        onSetLastTtsLocator: (Locator) -> Unit,
        onDelayedScrollRefresh: (() -> Unit)? = null
    ) {
        val pub = publication ?: return

        if (isPdfBook || isReflowMode) {
            navigatePdfChapter(
                publication = pub,
                navigator = navigator,
                isReflowMode = isReflowMode,
                direction = ChapterDirection.PREVIOUS,
                currentPdfPage = currentPdfPage,
                ttsIsPlaying = ttsIsPlaying,
                onJumpToReflowPage = onJumpToReflowPage,
                onPlayPdfPageTts = onPlayPdfPageTts,
                onPlayTts = onPlayTts,
                onSetLastTtsLocator = onSetLastTtsLocator
            )
            return
        }

        val nav = navigator as? VisualNavigator ?: return
        val href = nav.currentLocator.value.href.toString()
        val order = pub.readingOrder
        val currentIdx = findMatchingResourceIndex(href, order, resourceIndexCache).takeIf { it != -1 } ?: resourceIndexCache?.get(href) ?: -1

        if (currentIdx > 0) {
            val prevIdx = currentIdx - 1
            val prevLink = order[prevIdx]
            val prevLocator = pub.locatorFromLink(prevLink)?.copy(
                locations = Locator.Locations(progression = 1.0)
            )
            chapterLruCache.get(prevIdx)?.let { cached ->
                epubFormatManager.putCachedDocument(prevLink.href.toString(), cached.document)
            }
            if (prevLocator != null) {
                nav.go(prevLocator, animated = false)
                if (ttsIsPlaying) {
                    onPlayTts(prevLocator)
                } else {
                    onSetLastTtsLocator(prevLocator)
                }
            } else {
                nav.go(prevLink, animated = false)
            }
            prefetchSurroundingChapters(scope, bookId, pub, isPdfBook, prevIdx)

            scope.launch {
                for (delayMs in listOf(80L, 200L, 450L, 800L, 1200L)) {
                    delay(delayMs)
                    onDelayedScrollRefresh?.invoke()
                }
            }
        } else {
            nav.goBackwardCompat(animated = true)
        }
    }

    fun advanceTtsToNextChapter(
        scope: CoroutineScope,
        bookId: String,
        publication: Publication?,
        navigator: Navigator?,
        isPdfBook: Boolean,
        isReflowMode: Boolean,
        currentTtsHref: String,
        resourceIndexCache: Map<String, Int>?,
        onNavigatePdfNext: () -> Unit
    ) {
        val now = SystemClock.elapsedRealtime()
        val last = lastChapterAdvanceTime.get()
        if (now - last < CHAPTER_ADVANCE_DEBOUNCE_MS || !lastChapterAdvanceTime.compareAndSet(last, now)) {
            Log.d(TAG, "advanceTtsToNextChapter: Ignorado disparo duplicado dentro de 1.2s")
            return
        }

        val pub = publication ?: return
        if (isPdfBook || isReflowMode) {
            onNavigatePdfNext()
            return
        }

        val order = pub.readingOrder
        val totalChapters = order.size
        val currentIdx = if (currentTtsHref.isNotBlank()) {
            val idx = resourceIndexCache?.get(currentTtsHref) ?: findMatchingResourceIndex(currentTtsHref, order, resourceIndexCache)
            if (idx != -1) idx else {
                val targetRes = currentTtsHref.substringAfterLast('/').substringBefore('#')
                order.indexOfFirst {
                    it.href.toString() == currentTtsHref ||
                    it.href.toString().substringAfterLast('/') == targetRes
                }
            }
        } else -1

        if (currentIdx != -1 && currentIdx + 1 < totalChapters) {
            val nextIdx = currentIdx + 1
            val nextLink = order[nextIdx]
            val nextLocator = pub.locatorFromLink(nextLink)?.copy(
                locations = Locator.Locations(progression = 0.0)
            )

            chapterLruCache.get(nextIdx)?.let { cached ->
                epubFormatManager.putCachedDocument(nextLink.href.toString(), cached.document)
            }

            if (nextLocator != null) {
                (navigator as? VisualNavigator)?.go(nextLocator, animated = false)
                epubFormatManager.playTts(context, nextLocator, navigator, scope, pub)
            } else {
                epubFormatManager.skipTtsChapterForward(context, pub, navigator)
            }
            prefetchSurroundingChapters(scope, bookId, pub, isPdfBook, nextIdx)
        } else {
            epubFormatManager.skipTtsChapterForward(context, pub, navigator)
        }
    }

    fun clear() {
        prefetchJob?.cancel()
        prefetchJob = null
        chapterLruCache.evictAll()
    }
}
