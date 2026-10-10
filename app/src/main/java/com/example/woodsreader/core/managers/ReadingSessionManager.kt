package com.example.woodsreader.core.managers

import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsEntity
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Locator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingSessionManager @Inject constructor(
    private val bookRepository: BookRepository,
    private val timeProvider: TimeProvider
) {
    private companion object {
        const val INACTIVITY_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        const val MIN_DURATION_MS = 2000L
        const val MIN_WORDS_FOR_GHOST = 10L
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    @Volatile
    var activeSessionId: String? = null
        private set

    @Volatile
    var activeBookId: String? = null
        private set

    @Volatile
    var sessionStartTime = 0L
        private set

    @Volatile
    var lastActivityTime = 0L
        private set

    @Volatile
    var currentSessionMode = ReadingMode.MANUAL
        private set

    @Volatile
    var sessionStartLocator: Locator? = null
        private set

    @Volatile
    var sessionStartLocatorJson = ""
        private set

    private val _wordsProcessed = MutableStateFlow(0L)
    val wordsProcessed = _wordsProcessed.asStateFlow()

    private val _pagesRead = MutableStateFlow<Set<String>>(emptySet())
    val pagesRead = _pagesRead.asStateFlow()

    fun incrementWords(words: Long) {
        if (words > 0) {
            _wordsProcessed.value += words
        }
    }

    fun addPageRead(pageKey: String) {
        if (pageKey.isNotBlank()) {
            _pagesRead.value = _pagesRead.value + pageKey
        }
    }

    fun startNewSession(scope: CoroutineScope = appScope, bookId: String, mode: ReadingMode, initialLocator: Locator?) {
        val targetScope = if (scope.isActive) scope else appScope
        targetScope.launch {
            sessionMutex.withLock {
                internalStartSession(bookId, mode, initialLocator)
            }
        }
    }

    fun stopActiveSession(scope: CoroutineScope = appScope, bookId: String, currentLocator: Locator?) {
        val targetScope = if (scope.isActive) scope else appScope
        targetScope.launch {
            sessionMutex.withLock {
                internalStopSession(bookId, currentLocator)
            }
        }
    }

    fun switchSessionMode(scope: CoroutineScope = appScope, bookId: String, newMode: ReadingMode, currentLocator: Locator?) {
        val targetScope = if (scope.isActive) scope else appScope
        targetScope.launch {
            sessionMutex.withLock {
                if (currentSessionMode == newMode && activeSessionId != null && activeBookId == bookId) {
                    lastActivityTime = timeProvider.currentTimeMillis()
                    return@withLock
                }
                internalStopSession(bookId, currentLocator)
                internalStartSession(bookId, newMode, currentLocator)
            }
        }
    }

    fun recordActivity(scope: CoroutineScope = appScope, bookId: String, currentLocator: Locator?) {
        val targetScope = if (scope.isActive) scope else appScope
        targetScope.launch {
            sessionMutex.withLock {
                lastActivityTime = timeProvider.currentTimeMillis()
                if (activeSessionId == null) {
                    internalStartSession(bookId, currentSessionMode, currentLocator)
                }
            }
        }
    }

    private suspend fun internalStartSession(bookId: String, mode: ReadingMode, initialLocator: Locator?) {
        if (activeSessionId != null) {
            if (activeBookId == bookId) return // Already active for this book
            // Different book: close previous session cleanly first
            val prevBook = activeBookId
            if (prevBook != null) {
                internalStopSession(prevBook, null)
            } else {
                activeSessionId = null
                activeBookId = null
            }
        }

        val startTime = timeProvider.currentTimeMillis()
        sessionStartTime = startTime
        lastActivityTime = startTime
        val sessionId = UUID.randomUUID().toString()
        
        activeSessionId = sessionId
        activeBookId = bookId
        currentSessionMode = mode
        sessionStartLocator = initialLocator
        sessionStartLocatorJson = initialLocator?.toJSON()?.toString() ?: ""
        _wordsProcessed.value = 0L
        _pagesRead.value = emptySet()

        bookRepository.startSession(
            ReadingSessionEntity(
                id = sessionId,
                bookId = bookId,
                startAt = startTime,
                startLocatorJson = sessionStartLocatorJson,
                mode = mode
            )
        )
    }

    private suspend fun internalStopSession(bookId: String, currentLocator: Locator?) {
        val sessionId = activeSessionId ?: return
        val targetBookId = activeBookId ?: bookId
        activeSessionId = null // Detach immediately
        activeBookId = null

        val startTime = sessionStartTime
        val currentTime = timeProvider.currentTimeMillis()
        val endTime = if (currentTime - lastActivityTime > INACTIVITY_THRESHOLD_MS) {
            lastActivityTime
        } else {
            currentTime
        }
        
        val durationMs = (endTime - startTime).coerceAtLeast(0L)
        val words = _wordsProcessed.value
        val pages = _pagesRead.value

        val shouldDiscard = durationMs < MIN_DURATION_MS || (words < MIN_WORDS_FOR_GHOST && pages.isEmpty())

        // Save session unconditionally using NonCancellable to avoid data loss on scope exit
        kotlinx.coroutines.withContext(NonCancellable) {
            if (shouldDiscard) {
                bookRepository.deleteSession(sessionId)
            } else {
                saveValidSession(sessionId, targetBookId, durationMs, words, currentLocator)
            }
        }
    }

    private suspend fun saveValidSession(
        sessionId: String, 
        bookId: String, 
        durationMs: Long, 
        words: Long, 
        endLocator: Locator?
    ) {
        val locatorJson = endLocator?.toJSON()?.toString()
        val progression = endLocator?.locations?.totalProgression?.toFloat() ?: 0f

        bookRepository.closeSession(
            id = sessionId,
            endLocatorJson = locatorJson ?: "",
            wordsRead = words
        )

        if (endLocator != null && locatorJson != null) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = bookId,
                progress = progression,
                locatorJson = locatorJson,
                fallbackHref = endLocator.href.toString(),
                fallbackPosition = endLocator.locations.position ?: 0
            )
        }

        if (durationMs > 0) {
            bookRepository.addReadingTime(bookId, durationMs)
            val today = timeProvider.currentDateString()
            val stats = bookRepository.getReadingStats(today) ?: ReadingStatsEntity(today)
            bookRepository.saveReadingStats(
                stats.copy(secondsRead = stats.secondsRead + (durationMs / 1000))
            )
        }
    }
}
