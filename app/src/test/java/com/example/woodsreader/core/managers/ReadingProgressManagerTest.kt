package com.example.woodsreader.core.managers

import com.example.woodsreader.data.repository.BookRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReadingProgressManagerTest {

    private val bookRepository: BookRepository = mockk(relaxed = true)
    private lateinit var progressManager: ReadingProgressManager

    @Before
    fun setup() {
        progressManager = ReadingProgressManager(bookRepository)
    }

    @Test
    fun testCalculateEstimatedTimeToFinish_manualReading() {
        // 50% progress, 100,000 total words -> 50,000 words remaining.
        // WPM manual = 200 -> 250 mins left -> 4h 10m
        val etf = progressManager.calculateEstimatedTimeToFinish(
            progress = 0.5f,
            estimatedWordCount = 100_000L,
            manualWpm = 200.0,
            ttsWpm = 150.0,
            isTtsActive = false
        )
        assertEquals("4h 10m", etf)
    }

    @Test
    fun testCalculateEstimatedTimeToFinish_ttsActive() {
        // 80% progress, 50,000 total words -> 10,000 words remaining.
        // WPM tts = 150 -> 66 mins left -> 1h 6m
        val etf = progressManager.calculateEstimatedTimeToFinish(
            progress = 0.8f,
            estimatedWordCount = 50_000L,
            manualWpm = 200.0,
            ttsWpm = 150.0,
            isTtsActive = true
        )
        assertEquals("1h 6m", etf)
    }

    @Test
    fun testCalculateEstimatedFinishDate() {
        // 50% progress, 60,000 total words -> 30,000 words remaining.
        // words per day (wpd) = 3000 -> 10 days left.
        val targetDate = LocalDate.now().plusDays(10)
        val expected = targetDate.format(DateTimeFormatter.ofPattern("d MMM"))

        val efd = progressManager.calculateEstimatedFinishDate(
            progress = 0.5f,
            estimatedWordCount = 60_000L,
            wpd = 3000.0
        )
        assertEquals(expected, efd)
    }
}
