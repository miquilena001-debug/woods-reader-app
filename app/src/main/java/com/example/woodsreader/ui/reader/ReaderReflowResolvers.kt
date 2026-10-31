package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.model.ReflowItem
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

object ReaderReflowResolvers {

    fun readableItems(elements: List<ReflowItem>): List<ReflowItem> {
        return elements.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
    }

    fun readableIndex(
        elements: List<ReflowItem>,
        readableItems: List<ReflowItem>,
        elementIndex: Int
    ): Int {
        val boundedIdx = elementIndex.coerceIn(0, (elements.size - 1).coerceAtLeast(0))
        val visibleItem = elements.getOrNull(boundedIdx)
        return if (visibleItem != null && visibleItem in readableItems) {
            readableItems.indexOf(visibleItem)
        } else {
            elements.drop(boundedIdx).firstNotNullOfOrNull { item ->
                readableItems.indexOf(item).takeIf { it >= 0 }
            } ?: 0
        }
    }

    fun locatorForPdfPage(
        page: Int,
        publication: Publication,
        reflowTotalPages: Int,
        totalPositions: Int,
        positionsByResource: List<List<Locator>>
    ): Locator? {
        val totalPages = reflowTotalPages.takeIf { it > 1 }
            ?: totalPositions.takeIf { it > 1 }
            ?: publication.readingOrder.size.takeIf { it > 1 }
            ?: 1
        val prog = ((page - 1).toDouble() / totalPages.toDouble()).coerceIn(0.0, 1.0)

        if (totalPositions > 1 && positionsByResource.isNotEmpty()) {
            var count = 0
            for (sublist in positionsByResource) {
                if (page - 1 < count + sublist.size) {
                    val loc = sublist.getOrNull(page - 1 - count)
                    if (loc != null) {
                        return loc.copy(
                            locations = loc.locations.copy(
                                fragments = listOf("page=$page"),
                                position = page,
                                progression = loc.locations.progression ?: prog,
                                totalProgression = loc.locations.totalProgression ?: prog
                            )
                        )
                    }
                }
                count += sublist.size
            }
        }
        val targetIdx = (page - 1).coerceAtLeast(0)
        val link = publication.readingOrder.getOrNull(targetIdx) ?: publication.readingOrder.firstOrNull() ?: return null
        return publication.locatorFromLink(link)?.copy(
            locations = Locator.Locations(
                fragments = listOf("page=$page"),
                position = page,
                progression = prog,
                totalProgression = prog
            )
        )
    }
}
