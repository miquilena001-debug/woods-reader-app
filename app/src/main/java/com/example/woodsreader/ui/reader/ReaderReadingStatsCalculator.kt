package com.example.woodsreader.ui.reader

import com.example.woodsreader.domain.reader.CalculateReadingStatsUseCase
import com.example.woodsreader.domain.reader.ChapterProgressResult
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

class ReadingStatsUpdate(
    val bookTimeRemaining: String?,
    val chapterStats: ChapterProgressResult?
)

class ReaderReadingStatsCalculator(
    private val statsUseCase: CalculateReadingStatsUseCase,
    private val resolveChapterTitle: (Publication, String, Int?) -> String?
) {
    companion object {
        /** Moved from ReaderViewModel (minutes per resource position). */
        private const val RESOURCE_POSITION_MINUTES_FACTOR = 0.8
    }

    fun readingStats(
        locator: Locator,
        activeWpm: Int,
        totalWords: Long,
        totalPositions: Int,
        positionsByResource: List<List<Locator>>,
        resourceIndexCache: Map<String, Int>?,
        publication: Publication?
    ): ReadingStatsUpdate {
        val totalProgression = locator.locations.totalProgression ?: 0.0
        val bookTimeRemaining = statsUseCase.calculateBookTimeRemaining(
            totalWords = totalWords,
            totalProgression = totalProgression,
            activeWpm = activeWpm
        )

        var chapterStats: ChapterProgressResult? = null
        if (totalPositions > 0 && publication != null) {
            val resourceIndex = resourceIndexCache?.get(locator.href.toString())
                ?: publication.readingOrder.indexOfFirst { it.href.toString() == locator.href.toString() }
            chapterStats = statsUseCase.calculateChapterProgress(
                locatorProgression = locator.locations.progression ?: 0.0,
                resourceIndex = resourceIndex,
                positionsByResource = positionsByResource,
                totalPositions = totalPositions,
                activeWpm = activeWpm
            )
        }
        return ReadingStatsUpdate(bookTimeRemaining, chapterStats)
    }

    fun chapterInfo(
        locator: Locator,
        publication: Publication?,
        positionsByResource: List<List<Locator>>,
        pageFallback: Int?
    ): ChapterInfo {
        val resourceHref = locator.href.toString()

        val resourceIndex = publication?.readingOrder
            ?.indexOfFirst { it.href.toString() == resourceHref } ?: -1

        var timeRemaining: Int? = null
        val prog = (locator.locations.progression ?: 0.0).toFloat()

        if (resourceIndex != -1 && resourceIndex < positionsByResource.size) {
            val resourcePositions = positionsByResource[resourceIndex]

            val currentPosInResource = (locator.locations.progression ?: 0.0) * resourcePositions.size
            val remainingInResource = resourcePositions.size - currentPosInResource

            timeRemaining = (remainingInResource * RESOURCE_POSITION_MINUTES_FACTOR).toInt().coerceAtLeast(1)
        }

        val pageNum = locator.locations.position ?: pageFallback

        val resolvedTitle = locator.title?.takeIf { it.isNotBlank() }
            ?: publication?.let { pub -> resolveChapterTitle(pub, resourceHref, pageNum) }

        return ChapterInfo(
            title = resolvedTitle,
            progress = prog,
            timeRemainingMinutes = timeRemaining
        )
    }
}
