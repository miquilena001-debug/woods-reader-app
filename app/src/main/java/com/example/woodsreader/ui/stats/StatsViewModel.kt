package com.example.woodsreader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.woodsreader.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class StatsPeriod { Weekly, Monthly, Yearly }

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    init {
        // Protocolo de Integridad (Parte 8): limpieza y sanación al abrir estadísticas.
        viewModelScope.launch {
            bookRepository.cleanupInvalidSessions()
            bookRepository.repairZeroWordSessions()
        }
    }

    // 1. Libros Terminados
    val completedBooksCount: StateFlow<Int> = bookRepository.getCompletedBooksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 2. Tiempo Total Formateado (ej "12h 45m")
    val totalReadingTimeFormatted: StateFlow<String> = bookRepository.getTotalReadingTimeMs()
        .map { totalMs -> formatDuration(totalMs ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0h 0m")

    val totalReadingTimeMs: StateFlow<Long> = bookRepository.getTotalReadingTimeMs()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // 3. Racha actual (Días consecutivos)
    val currentStreak: StateFlow<Int> = bookRepository.getUniqueReadingDays()
        .map { dateStrings -> calculateStreak(dateStrings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 4. Progreso Semanal (M-B4)
    val weeklyWords = bookRepository.getWeeklyWords(
        System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Actividad últimos 28 días (Heatmap)
    val heatmapDays = bookRepository.getReadingDaysLast28Days(
        System.currentTimeMillis() - (28 * 24 * 60 * 60 * 1000L)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Minutos leídos hoy (Objetivo Diario)
    val todayReadingMinutes: StateFlow<Int> = bookRepository.todaySessions()
        .map { sessions ->
            sessions.sumOf { session ->
                val end = session.endAt ?: System.currentTimeMillis()
                ((end - session.startAt) / 60_000L).toInt()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayWordsProcessed: StateFlow<Int> = bookRepository.todaySessions()
        .map { sessions -> sessions.sumOf { it.wordsRead }.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Inteligencia Visual 2.0 (Parte 5) ────────────────────────────────────

    val hourlyActivity = bookRepository.getHourlyActivity(
        System.currentTimeMillis() - (28 * 24 * 60 * 60 * 1000L) // Últimos 28 días
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookDistribution = bookRepository.getBookDistribution(
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Inteligencia Predictiva (Parte 3) ──────────────────────────────────────
    val averageWpm: StateFlow<Int> = combine(
        bookRepository.getAverageWpmByMode(com.example.woodsreader.data.model.ReadingMode.MANUAL),
        bookRepository.getAverageWpmByMode(com.example.woodsreader.data.model.ReadingMode.TTS)
    ) { manual: Double?, tts: Double? ->
        (((manual ?: 250.0) + (tts ?: 250.0)) / 2.0).toInt().coerceAtLeast(100)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 250)

    val dailyCapacityWords: StateFlow<Int> = bookRepository.getAverageWordsPerDay(
        System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
    ).map { wpd: Double? -> wpd?.toInt() ?: 3000 }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3000)

    // ── Analítica de Dualidad (Parte 6) ──────────────────────────────────────

    val modeEndurance = bookRepository.getModeEndurance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTimeSaved: StateFlow<String> = bookRepository.getSessionSummaries(0L)
        .map { summaries -> calculateTimeSaved(summaries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0h")

    // ── Motor de Metas Dinámicas (Parte 7) ───────────────────────────────────

    private val lastBook: StateFlow<com.example.woodsreader.data.database.BookEntity?> =
        bookRepository.getCurrentReadingBook()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Motor de Metas Dinámicas (M-D4): 
     * Calcula la carga léxica necesaria por día basándose en el progreso remanente.
     */
    val smartDailyGoalWords: StateFlow<Int> = lastBook
        .map { book -> calculateSmartGoal(book) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    // 7. Etiqueta del Mes Actual
    val currentMonthLabel: String =
        java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
            .uppercase()

    // ── Helper Methods ────────────────────────────────────────────────────────

    private fun formatDuration(totalMs: Long): String {
        if (totalMs == 0L) return "0h 0m"
        val totalMinutes = totalMs / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun calculateTimeSaved(summaries: List<com.example.woodsreader.data.database.SessionSummary>): String {
        val ttsSummary    = summaries.find { it.mode == com.example.woodsreader.data.model.ReadingMode.TTS }
        val manualSummary = summaries.find { it.mode == com.example.woodsreader.data.model.ReadingMode.MANUAL }
        if (ttsSummary == null || ttsSummary.avgWords <= 0.0) return "0h 0m"
        
        val ttsWords = ttsSummary.avgWords
        val manualWpm = if (manualSummary != null && manualSummary.avgMinutes > 0) {
            (manualSummary.avgWords / manualSummary.avgMinutes).coerceIn(100.0, 600.0)
        } else {
            250.0 // Velocidad base de lectura manual
        }
        val ttsMinutes = ttsSummary.avgMinutes
        val manualEquivalentMinutes = if (manualWpm > 0) ttsWords / manualWpm else 0.0
        val savedMinutes = (manualEquivalentMinutes - ttsMinutes).coerceAtLeast(0.0)
        
        val savedHours = (savedMinutes / 60.0).toInt()
        val savedMins  = (savedMinutes % 60.0).toInt()
        return if (savedHours > 0) "${savedHours}h ${savedMins}m" else "${savedMins}m"
    }

    private fun calculateSmartGoal(book: com.example.woodsreader.data.database.BookEntity?): Int {
        if (book == null || book.estimatedWordCount == 0L) return 2000
        val wordsRemaining = book.estimatedWordCount.toDouble() * (1.0 - book.readingProgressPercent)
        val daysPerWeek = 5.0 // Meta base de 5 días de lectura
        if (book.readingProgressPercent > 0.95f) return 1000
        return (wordsRemaining / daysPerWeek).toInt().coerceIn(500, 10000)
    }

    private fun calculateStreak(dateStrings: List<String>): Int {
        if (dateStrings.isEmpty()) return 0
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dates = dateStrings.mapNotNull {
            try { LocalDate.parse(it, formatter) } catch (_: Exception) { null }
        }.toSortedSet(compareByDescending { it })

        val today = LocalDate.now()
        // Si no leyó ni hoy ni ayer, la racha es 0
        val startDate = when {
            dates.contains(today)              -> today
            dates.contains(today.minusDays(1)) -> today.minusDays(1)
            else                               -> return 0
        }
        var streak = 0
        var expected = startDate
        while (dates.contains(expected)) {
            streak++
            expected = expected.minusDays(1)
        }
        return streak
    }
}
