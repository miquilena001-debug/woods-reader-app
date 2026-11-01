package com.example.woodsreader.ui.reader

import org.readium.r2.shared.publication.Locator
import java.io.File

object ReaderLoadResolvers {
    fun pdfPhysicalPath(filePath: String, uriPath: String?): String =
        if (filePath.startsWith("/")) filePath else uriPath ?: filePath

    fun initialPdfPage(initialPdfPage: Int?, fallbackLocatorPosition: Int?): Int =
        initialPdfPage ?: fallbackLocatorPosition?.takeIf { it > 0 } ?: 1

    fun initialRecordedProgression(initialLocator: Locator?, readingProgressPercent: Float): Double =
        initialLocator?.locations?.totalProgression ?: readingProgressPercent.toDouble()

    fun initialGlobalProgress(readingProgressPercent: Float): Float =
        readingProgressPercent.coerceIn(0f, 1f)

    fun isCoverMissing(coverPath: String?): Boolean =
        coverPath.isNullOrBlank() || !File(coverPath).exists()
}
