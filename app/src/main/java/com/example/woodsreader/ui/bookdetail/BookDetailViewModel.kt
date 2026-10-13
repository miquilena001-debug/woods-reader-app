package com.example.woodsreader.ui.bookdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.service.BookMetadataExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class BookDetailUiState(
    val book: BookEntity? = null,
    val highlights: List<HighlightEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
    val stats: BookStats = BookStats(),
    val isLoading: Boolean = true,
    val isGeneratingSynopsis: Boolean = false,
    val error: String? = null
)

data class BookStats(
    val totalTimeMs: Long = 0L,
    val averageWpm: Double = 0.0,
    val sessionCount: Int = 0,
    val averageSessionTimeMs: Long = 0L,
    val totalWordsRead: Long = 0L,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val last7DaysMinutes: List<Pair<String, Float>> = emptyList(),
    val activeDaysThisWeek: Set<Int> = emptySet(),
    val ttsRatio: Float = 0f,
    val totalTtsTimeMs: Long = 0L,
    val totalManualTimeMs: Long = 0L
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val metadataExtractor: BookMetadataExtractor
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle["bookId"])

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        loadBookData()
    }

    private fun loadBookData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                combine(
                    bookRepository.getBookFlow(bookId),
                    bookRepository.getHighlights(bookId),
                    bookRepository.getBookmarks(bookId),
                    bookRepository.getSessionsForBook(bookId)
                ) { book, highlights, bookmarks, sessions ->
                    if (book == null) {
                        BookDetailUiState(isLoading = false, error = "Book not found")
                    } else {
                        val stats = calculateStats(book, sessions)
                        BookDetailUiState(
                            book = book,
                            highlights = highlights,
                            bookmarks = bookmarks,
                            sessions = sessions,
                            stats = stats,
                            isLoading = false
                        )
                    }
                }.collect { newState ->
                    _uiState.value = newState
                }
            } catch (e: Exception) {
                _uiState.value = BookDetailUiState(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    private fun calculateStats(book: BookEntity, sessions: List<ReadingSessionEntity>): BookStats {
        val completedSessions = sessions.filter { it.endAt != null }
        val sessionCount = completedSessions.size
        
        val totalTimeMs = book.totalReadingTimeMs
        
        val totalWordsRead = completedSessions.sumOf { it.wordsRead }
        val totalSessionDurationMs = completedSessions.sumOf { it.endAt!! - it.startAt }
        
        val averageWpm = if (totalSessionDurationMs > 0) {
            (totalWordsRead.toDouble() / (totalSessionDurationMs.toDouble() / 60000.0))
        } else {
            0.0
        }
        
        val averageSessionTimeMs = if (sessionCount > 0) {
            totalSessionDurationMs / sessionCount
        } else {
            0L
        }

        // Zone ID for date calculations
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        // Extract sorted unique reading dates
        val readingDates = completedSessions.map { session ->
            Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate()
        }.toSortedSet()

        // Calculate current streak
        var currentStreak = 0
        if (readingDates.isNotEmpty()) {
            if (readingDates.contains(today)) {
                var checkDate = today
                while (readingDates.contains(checkDate)) {
                    currentStreak++
                    checkDate = checkDate.minusDays(1)
                }
            } else {
                val yesterday = today.minusDays(1)
                if (readingDates.contains(yesterday)) {
                    var checkDate = yesterday
                    while (readingDates.contains(checkDate)) {
                        currentStreak++
                        checkDate = checkDate.minusDays(1)
                    }
                }
            }
        }

        // Calculate max streak
        var maxStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null
        for (date in readingDates) {
            if (prevDate == null) {
                tempStreak = 1
            } else {
                val daysBetween = ChronoUnit.DAYS.between(prevDate, date)
                if (daysBetween == 1L) {
                    tempStreak++
                } else if (daysBetween > 1L) {
                    maxStreak = maxOf(maxStreak, tempStreak)
                    tempStreak = 1
                }
            }
            prevDate = date
        }
        maxStreak = maxOf(maxStreak, tempStreak)

        // Calculate reading time for last 7 calendar days (today and 6 days before)
        val last7DaysMinutes = (6 downTo 0).map { offset ->
            val targetDate = today.minusDays(offset.toLong())
            val daySessions = completedSessions.filter { session ->
                Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate() == targetDate
            }
            val totalMs = daySessions.sumOf { (it.endAt ?: it.startAt) - it.startAt }
            val minutes = totalMs / 60000f

            val dayName = when (targetDate.dayOfWeek) {
                DayOfWeek.MONDAY -> "L"
                DayOfWeek.TUESDAY -> "M"
                DayOfWeek.WEDNESDAY -> "M"
                DayOfWeek.THURSDAY -> "J"
                DayOfWeek.FRIDAY -> "V"
                DayOfWeek.SATURDAY -> "S"
                DayOfWeek.SUNDAY -> "D"
                else -> ""
            }
            dayName to minutes
        }

        // Calculate active days for the current calendar week (Monday to Sunday)
        val currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val activeDaysThisWeek = (0..6).mapNotNull { offset ->
            val weekDate = currentWeekMonday.plusDays(offset.toLong())
            val hasReading = completedSessions.any { session ->
                Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate() == weekDate
            }
            if (hasReading) weekDate.dayOfWeek.value else null
        }.toSet()

        // Calculate TTS vs Manual ratio
        val totalTtsTimeMs = completedSessions
            .filter { it.mode == ReadingMode.TTS }
            .sumOf { (it.endAt ?: it.startAt) - it.startAt }

        val totalManualTimeMs = completedSessions
            .filter { it.mode == ReadingMode.MANUAL }
            .sumOf { (it.endAt ?: it.startAt) - it.startAt }

        val totalDurationMs = totalTtsTimeMs + totalManualTimeMs
        val ttsRatio = if (totalDurationMs > 0) {
            totalTtsTimeMs.toFloat() / totalDurationMs.toFloat()
        } else {
            0f
        }

        return BookStats(
            totalTimeMs = totalTimeMs,
            averageWpm = averageWpm,
            sessionCount = sessionCount,
            averageSessionTimeMs = averageSessionTimeMs,
            totalWordsRead = totalWordsRead,
            currentStreak = currentStreak,
            maxStreak = maxStreak,
            last7DaysMinutes = last7DaysMinutes,
            activeDaysThisWeek = activeDaysThisWeek,
            ttsRatio = ttsRatio,
            totalTtsTimeMs = totalTtsTimeMs,
            totalManualTimeMs = totalManualTimeMs
        )
    }

    fun updateReadingStatus(status: ReadingStatus) {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            val updatedBook = currentBook.copy(readingStatus = status)
            bookRepository.upsertBook(updatedBook)
        }
    }

    fun updateRating(rating: Int?) {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            val updatedBook = currentBook.copy(userRating = rating)
            bookRepository.upsertBook(updatedBook)
        }
    }

    fun updateUserNotes(notes: String) {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            val updatedBook = currentBook.copy(userNotes = notes)
            bookRepository.upsertBook(updatedBook)
        }
    }

    fun updateDescription(newDescription: String) {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            val updatedBook = currentBook.copy(description = newDescription.trim())
            bookRepository.upsertBook(updatedBook)
        }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            bookRepository.deleteHighlight(highlight)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookRepository.deleteBookmark(bookmark)
        }
    }

    /**
     * Generates a synopsis from the book's own content using TextRank.
     * Shows a loading indicator while processing.
     */
    fun generateSynopsis() {
        val book = _uiState.value.book ?: return
        if (_uiState.value.isGeneratingSynopsis) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSynopsis = true) }
            try {
                val fileHash = book.fileHash
                val uri = if (book.filePath.startsWith("/")) {
                    android.net.Uri.fromFile(File(book.filePath))
                } else {
                    android.net.Uri.parse(book.filePath)
                }
                val fileName = File(book.filePath).name.ifBlank { book.title }
                val extracted = metadataExtractor.extractMetadata(uri, fileName, book.fileSizeBytes, fileHash)
                val synopsis = extracted?.description?.takeIf { it.isNotBlank() } ?: ""

                if (synopsis.isNotBlank()) {
                    bookRepository.upsertBook(book.copy(description = synopsis))
                }
            } catch (e: Exception) {
                // Silently ignore — synopsis stays empty
            } finally {
                _uiState.update { it.copy(isGeneratingSynopsis = false) }
            }
        }
    }
}
