package com.example.woodsreader.core.managers

import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsEntity
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionManagerTest {

    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val timeProvider: TimeProvider = mockk(relaxed = true)
    private lateinit var sessionManager: ReadingSessionManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        sessionManager = ReadingSessionManager(bookRepository, timeProvider)
        // Default time
        every { timeProvider.currentTimeMillis() } returns 10000L
        every { timeProvider.currentDateString() } returns "2026-09-12"
    }

    @Test
    fun testStartNewSession() = runTest(testDispatcher) {
        val bookId = "book123"
        val mockLocator = mockk<Locator>(relaxed = true)
        val mockJsonObject = mockk<org.json.JSONObject>(relaxed = true)
        every { mockJsonObject.toString() } returns """{"href":"http://example.com/chapter1"}"""
        every { mockLocator.toJSON() } returns mockJsonObject

        sessionManager.startNewSession(testScope, bookId, ReadingMode.MANUAL, mockLocator)

        assertNotNull(sessionManager.activeSessionId)
        assertEquals(ReadingMode.MANUAL, sessionManager.currentSessionMode)
        assertEquals(mockLocator, sessionManager.sessionStartLocator)
        assertEquals(0L, sessionManager.wordsProcessed.value)
        assertTrue(sessionManager.pagesRead.value.isEmpty())

        coVerify(exactly = 1) {
            bookRepository.startSession(any())
        }
    }

    @Test
    fun testIncrementWordsAndPages() {
        sessionManager.incrementWords(150)
        sessionManager.addPageRead("page_1")
        sessionManager.addPageRead("page_2")

        assertEquals(150L, sessionManager.wordsProcessed.value)
        assertEquals(setOf("page_1", "page_2"), sessionManager.pagesRead.value)
    }

    @Test
    fun testDiscardGhostSessionDueToDuration() = runTest(testDispatcher) {
        val bookId = "book123"
        
        // Start at T=10000
        every { timeProvider.currentTimeMillis() } returns 10000L
        sessionManager.startNewSession(testScope, bookId, ReadingMode.MANUAL, null)
        val sessionId = sessionManager.activeSessionId!!

        // Stop at T=11000 (duration = 1000ms < 2000ms threshold)
        every { timeProvider.currentTimeMillis() } returns 11000L
        sessionManager.stopActiveSession(testScope, bookId, null)

        assertNull(sessionManager.activeSessionId)
        coVerify(exactly = 1) {
            bookRepository.deleteSession(sessionId)
        }
        coVerify(exactly = 0) {
            bookRepository.closeSession(any(), any(), any())
        }
    }

    @Test
    fun testDiscardGhostSessionDueToLowWordsAndNoPages() = runTest(testDispatcher) {
        val bookId = "book123"
        every { timeProvider.currentTimeMillis() } returns 10000L
        sessionManager.startNewSession(testScope, bookId, ReadingMode.MANUAL, null)
        val sessionId = sessionManager.activeSessionId!!

        // Process only 5 words, no pages
        sessionManager.incrementWords(5)

        // Stop at T=15000 (duration = 5000ms >= 2000ms threshold, but low words)
        every { timeProvider.currentTimeMillis() } returns 15000L
        sessionManager.stopActiveSession(testScope, bookId, null)

        assertNull(sessionManager.activeSessionId)
        coVerify(exactly = 1) {
            bookRepository.deleteSession(sessionId)
        }
        coVerify(exactly = 0) {
            bookRepository.closeSession(any(), any(), any())
        }
    }

    @Test
    fun testSaveValidSessionWithWords() = runTest(testDispatcher) {
        val bookId = "book123"
        coEvery { bookRepository.getReadingStats(any()) } returns null

        every { timeProvider.currentTimeMillis() } returns 10000L
        sessionManager.startNewSession(testScope, bookId, ReadingMode.MANUAL, null)
        val sessionId = sessionManager.activeSessionId!!

        // Process 20 words (>= 10 words, valid)
        sessionManager.incrementWords(20)

        // Stop at T=15000
        every { timeProvider.currentTimeMillis() } returns 15000L
        sessionManager.stopActiveSession(testScope, bookId, null)

        assertNull(sessionManager.activeSessionId)
        coVerify(exactly = 1) {
            bookRepository.closeSession(sessionId, any(), 20L)
        }
        coVerify(exactly = 0) {
            bookRepository.deleteSession(any())
        }
    }

    @Test
    fun testSaveValidSessionWithPagesOnly() = runTest(testDispatcher) {
        val bookId = "book123"
        coEvery { bookRepository.getReadingStats(any()) } returns null

        every { timeProvider.currentTimeMillis() } returns 10000L
        sessionManager.startNewSession(testScope, bookId, ReadingMode.MANUAL, null)
        val sessionId = sessionManager.activeSessionId!!

        sessionManager.addPageRead("page_1")

        // Stop at T=15000
        every { timeProvider.currentTimeMillis() } returns 15000L
        sessionManager.stopActiveSession(testScope, bookId, null)

        assertNull(sessionManager.activeSessionId)
        coVerify(exactly = 1) {
            bookRepository.closeSession(sessionId, any(), 0L)
        }
        coVerify(exactly = 0) {
            bookRepository.deleteSession(any())
        }
    }

    @Test
    fun testSwitchSessionMode() = runTest(testDispatcher) {
        val bookId = "book123"
        every { timeProvider.currentTimeMillis() } returns 10000L
        sessionManager.startNewSession(testScope, bookId, ReadingMode.MANUAL, null)
        
        // Switch to TTS
        every { timeProvider.currentTimeMillis() } returns 15000L
        sessionManager.switchSessionMode(testScope, bookId, ReadingMode.TTS, null)
        
        assertEquals(ReadingMode.TTS, sessionManager.currentSessionMode)
        
        // Since words = 0, first session gets deleted, second starts
        coVerify(exactly = 1) {
            bookRepository.deleteSession(any()) // 1 discard for manual
        }
        coVerify(exactly = 2) {
            bookRepository.startSession(any()) // 1 for manual, 1 for TTS
        }
    }

    @Test
    fun testRecordActivityAutoStartsSession() = runTest(testDispatcher) {
        val bookId = "book123"
        assertNull(sessionManager.activeSessionId)
        
        every { timeProvider.currentTimeMillis() } returns 10000L
        sessionManager.recordActivity(testScope, bookId, null)
        
        assertNotNull(sessionManager.activeSessionId)
    }
}
