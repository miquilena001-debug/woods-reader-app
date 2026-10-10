package com.example.woodsreader.core.managers

import com.example.woodsreader.data.repository.BookRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressManager @Inject constructor(
    private val bookRepository: BookRepository
) {
    fun calculateEstimatedTimeToFinish(
        progress: Float,
        estimatedWordCount: Long,
        manualWpm: Double?,
        ttsWpm: Double?,
        isTtsActive: Boolean
    ): String {
        val totalWords = if (estimatedWordCount > 0) estimatedWordCount else 80_000L
        val remainingWords = (1.0 - progress) * totalWords
        val effectiveWpm = when {
            isTtsActive -> ttsWpm ?: manualWpm ?: 250.0
            else -> manualWpm ?: ttsWpm ?: 250.0
        }.coerceAtLeast(100.0)

        val minutesLeft = (remainingWords / effectiveWpm).toInt()
        val hours = minutesLeft / 60
        val mins = minutesLeft % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    fun calculateEstimatedFinishDate(
        progress: Float,
        estimatedWordCount: Long,
        wpd: Double?
    ): String {
        val totalWords = if (estimatedWordCount > 0) estimatedWordCount else 80_000L
        val remainingWords = (1.0 - progress) * totalWords
        val effectiveWpd = if (wpd != null && wpd > 500) wpd else 3000.0

        val daysLeft = (remainingWords / effectiveWpd).toLong().coerceAtLeast(0)
        val finishDate = LocalDate.now().plusDays(daysLeft)
        return finishDate.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}
