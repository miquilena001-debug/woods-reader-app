package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.ReflowItem
import org.readium.r2.shared.publication.Locator
import kotlin.math.abs

class ReaderTtsPlaybackCoordinator {
    var pausedAtIndex: Int? = null
        private set
    var userScrolledSinceTtsPause: Boolean = false
        private set

    /** Equivale a las dos primeras líneas de pauseTts() en rama reflow/PDF. */
    fun onPaused(activeBlockIndex: Int) {
        pausedAtIndex = activeBlockIndex
        userScrolledSinceTtsPause = false
    }

    /** Equivale a stopTts(): borra ambos. */
    fun onStopped() {
        pausedAtIndex = null
        userScrolledSinceTtsPause = false
    }

    /** Hot path por frame: sin lambdas, sin asignaciones nuevas. Llamar ANTES de asignar lastReflowScrollIndex. */
    fun onReflowScrolled(previousIndex: Int, newIndex: Int) {
        if (previousIndex != newIndex && pausedAtIndex != null) {
            userScrolledSinceTtsPause = true
        }
    }

    /** Lógica exacta de resumeTts() rama reflow/PDF: calcula el índice y luego RESETEA ambos campos. */
    fun consumeReflowResumeIndex(visibleReadableIdx: Int, readableCount: Int, activeBlockIndex: () -> Int): Int {
        val targetStartIndex = if (userScrolledSinceTtsPause || pausedAtIndex == null) {
            visibleReadableIdx
        } else {
            val pausedIdx = pausedAtIndex ?: activeBlockIndex()
            if (pausedIdx in 0 until readableCount) pausedIdx else visibleReadableIdx
        }
        userScrolledSinceTtsPause = false
        pausedAtIndex = null
        return targetStartIndex
    }

    /** Rama reflow/PDF de playTts(): copia 1:1 del cálculo de targetIndex. */
    suspend fun resolveReflowPlayStartIndex(
        locator: Locator,
        fallbackPage: Int,
        items: () -> List<ReflowItem>,
        loadPages: suspend (Int) -> Unit
    ): Int {
        val targetPage = locator.locations.position ?: fallbackPage.coerceAtLeast(1)
        var readable = ReaderReflowResolvers.readableItems(items())
        var targetIndex = readable.indexOfFirst { it.pageNumber == targetPage }

        if (targetIndex == -1) {
            loadPages(targetPage)
            readable = ReaderReflowResolvers.readableItems(items())
            targetIndex = readable.indexOfFirst { it.pageNumber == targetPage }.coerceAtLeast(0)
        }

        val snippet = locator.text.highlight?.trim()
        if (!snippet.isNullOrBlank()) {
            val refinedIndex = readable.indexOfFirst { item ->
                val content = when (item) {
                    is ReflowItem.Text -> item.element.locator.text.highlight ?: ""
                    is ReflowItem.Header -> item.content
                    is ReflowItem.Paragraph -> item.content
                    else -> ""
                }
                content.contains(snippet, ignoreCase = true)
            }
            if (refinedIndex != -1) {
                targetIndex = refinedIndex
            }
        }
        return targetIndex
    }

    /** Rama EPUB PAUSED de resumeTts(): true => playTts(visualLoc); false => epubFormatManager.resumeTts(). */
    fun shouldReplayFromVisual(visualLoc: Locator?, ttsLocator: Locator?): Boolean {
        return visualLoc != null && (
            visualLoc.href != ttsLocator?.href ||
            abs((visualLoc.locations.progression ?: 0.0) - (ttsLocator?.locations?.progression ?: 0.0)) > 0.005
        )
    }
}
