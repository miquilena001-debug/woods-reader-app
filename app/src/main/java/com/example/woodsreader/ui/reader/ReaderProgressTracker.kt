package com.example.woodsreader.ui.reader

import org.readium.r2.shared.publication.Locator

class FlushPlan(
    val progress: Float,
    val fallbackPosition: Int,
    /** Non-null => caller sets lastKnownReflowPage to it BEFORE flushing (PDF original branch). */
    val pageToRemember: Int?,
    /** true => caller calls saveProgress(currentLoc) BEFORE flushing (EPUB branch, currentLoc != null). */
    val saveProgressFirst: Boolean
)

class ReaderProgressTracker {
    var lastKnownReflowPage: Int = 1
    var lastRecordedProgression: Double = 0.0
    var lastSavedLocatorJson: String? = null

    fun totalPdfPages(totalPositions: Int, reflowTotalPages: Int, readingOrderSize: Int?): Int =
        totalPositions.takeIf { it > 0 }
            ?: reflowTotalPages.takeIf { it > 0 }
            ?: readingOrderSize?.takeIf { it > 0 }
            ?: 1

    fun pageFraction(page: Int, totalPages: Int): Float =
        (page.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

    fun planFlush(
        currentLoc: Locator?,
        prog: Float,
        isReflowMode: Boolean,
        isPdfBook: Boolean,
        reflowFirstElementPage: Int?,
        reflowTotalPages: Int,
        totalPositions: Int,
        readingOrderSize: Int?,
        globalProgress: Float
    ): FlushPlan {
        if (isReflowMode) {
            val page = currentLoc?.locations?.position ?: lastKnownReflowPage.takeIf { it > 0 }
                ?: reflowFirstElementPage ?: 1
            val progress = currentLoc?.locations?.totalProgression?.toFloat()
                ?: currentLoc?.locations?.progression?.toFloat()
                ?: if (reflowTotalPages > 0) pageFraction(page, reflowTotalPages) else globalProgress
            return FlushPlan(progress, page, null, false)
        } else if (isPdfBook) {
            val page = currentLoc?.locations?.position ?: lastKnownReflowPage.takeIf { it > 0 } ?: 1
            val totalPages = totalPdfPages(totalPositions, reflowTotalPages, readingOrderSize)
            val progress = currentLoc?.locations?.totalProgression?.toFloat()
                ?: currentLoc?.locations?.progression?.toFloat()
                ?: pageFraction(page, totalPages)
            return FlushPlan(progress, page, page, false)
        }
        return FlushPlan(prog, currentLoc?.locations?.position ?: 0, null, currentLoc != null)
    }
}
