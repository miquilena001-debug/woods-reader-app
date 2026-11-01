package com.example.woodsreader.ui.reader

import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

object ReaderNavigationResolvers {

    sealed interface SeekTarget {
        data class Reflow(val page: Int) : SeekTarget
        // El VM SIEMPRE asigna lastKnownReflowPage = page y _globalProgress = percent; solo navega si nav != null && locator != null.
        data class PdfOriginal(val page: Int, val locator: Locator?) : SeekTarget
        // El VM exige nav != null (si no, return SIN tocar _globalProgress). Si locator != null navega; _globalProgress = percent SIEMPRE en esta variante.
        data class EpubLink(val locator: Locator?) : SeekTarget
        // El VM exige nav != null (si no, return). Navega y asigna _globalProgress.
        data class EpubPosition(val locator: Locator) : SeekTarget
        object None : SeekTarget   // el VM no hace nada
    }

    data class CurrentPdfPageResolution(
        val pageToUse: Int,
        val newLastKnownReflowPage: Int? = null
    )

    fun resolveSeekTarget(
        percent: Float,
        isReflowMode: Boolean,
        reflowTotalPages: Int,
        isPdfBook: Boolean,
        totalPositions: Int,
        publication: Publication?,
        positionsByResource: List<List<Locator>>,
        totalPdfPages: (readingOrderSize: Int?) -> Int
    ): SeekTarget {
        if (isReflowMode) {
            if (reflowTotalPages > 0) {
                val targetPage = (percent * reflowTotalPages).toInt().coerceIn(1, reflowTotalPages)
                return SeekTarget.Reflow(targetPage)
            }
        }

        if (isPdfBook && !isReflowMode) {
            val totalPages = totalPdfPages(publication?.readingOrder?.size)
            val targetPage = (percent * totalPages).toInt().coerceIn(1, totalPages)
            val readingOrderLink = publication?.readingOrder?.firstOrNull()
            val locatorOrNull = readingOrderLink?.let {
                publication?.locatorFromLink(it)?.copy(locations = Locator.Locations(position = targetPage))
            }
            return SeekTarget.PdfOriginal(page = targetPage, locator = locatorOrNull)
        }

        if (totalPositions <= 0 || positionsByResource.isEmpty()) {
            val pub = publication
            val links = pub?.readingOrder ?: emptyList()
            if (pub != null && links.isNotEmpty()) {
                val targetIdx = (percent * links.size).toInt().coerceIn(0, links.size - 1)
                val locator = pub.locatorFromLink(links[targetIdx])
                return SeekTarget.EpubLink(locator)
            }
            return SeekTarget.None
        }

        val targetIdx = (percent * totalPositions).toInt().coerceIn(0, totalPositions - 1)
        var accumulated = 0
        var targetResourceIdx = 0
        var targetPosInResource = 0

        for ((idx, resourcePos) in positionsByResource.withIndex()) {
            if (targetIdx < accumulated + resourcePos.size) {
                targetResourceIdx = idx
                targetPosInResource = targetIdx - accumulated
                break
            }
            accumulated += resourcePos.size
        }

        if (targetResourceIdx in positionsByResource.indices && targetPosInResource in positionsByResource[targetResourceIdx].indices) {
            return SeekTarget.EpubPosition(positionsByResource[targetResourceIdx][targetPosInResource])
        }

        return SeekTarget.None
    }

    fun resolveCurrentPdfPage(
        isReflowMode: Boolean,
        lastKnownReflowPage: Int,
        currentTtsPage: Int,
        visualPosition: Int?
    ): CurrentPdfPageResolution {
        return if (isReflowMode) {
            val page = lastKnownReflowPage.takeIf { it > 0 }
                ?: currentTtsPage.takeIf { it > 0 }
                ?: 1
            CurrentPdfPageResolution(pageToUse = page, newLastKnownReflowPage = null)
        } else {
            val visualPage = visualPosition ?: lastKnownReflowPage
            CurrentPdfPageResolution(
                pageToUse = visualPage.takeIf { it > 0 } ?: 1,
                newLastKnownReflowPage = visualPage
            )
        }
    }
}
