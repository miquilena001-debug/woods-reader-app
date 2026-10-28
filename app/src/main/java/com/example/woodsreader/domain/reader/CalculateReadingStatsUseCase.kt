package com.example.woodsreader.domain.reader

import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsDao
import com.example.woodsreader.data.database.ReadingStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.readium.r2.shared.publication.Locator
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

/**
 * Resultado del cálculo del progreso de capítulo y progreso global del libro.
 */
data class ChapterProgressResult(
    val chapterTimeRemaining: String,
    val globalProgress: Float
)

/**
 * Caso de uso responsable de los cálculos matemáticos de métricas de lectura y analítica.
 *
 * Encapsula:
 * - Estimación de Palabras Por Minuto (WPM) activa basada en sesión.
 * - Estimación de tiempo restante de lectura para el libro completo ("Xh Ym restantes").
 * - Estimación de tiempo restante para el capítulo actual y cálculo de progreso global con Readium.
 * - Incremento delta de palabras leídas en modo manual.
 * - Registro de páginas únicas y estadísticas diarias (`ReadingStatsDao`).
 * - Gestión de sesiones y cálculo de racha de lectura (`ReadingSessionDao`).
 */
class CalculateReadingStatsUseCase @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
    private val readingStatsDao: ReadingStatsDao
) {

    /**
     * Calcula las palabras por minuto (WPM) activas según la duración de la sesión y las palabras procesadas.
     * Si la sesión es muy corta (< 30s) o con pocas palabras (< 50), retorna el valor base estándar de 200 WPM.
     */
    fun calculateActiveWpm(sessionStartTimeMs: Long, wordsProcessed: Long): Int {
        val sessionDurationMs = System.currentTimeMillis() - sessionStartTimeMs
        val durationMin = sessionDurationMs.toDouble() / 60_000.0
        return if (durationMin > 0.5 && wordsProcessed > 50) {
            (wordsProcessed.toDouble() / durationMin).toInt().coerceIn(80, 500)
        } else {
            200
        }
    }

    /**
     * Calcula el incremento de palabras leídas en modo manual a partir del cambio en el porcentaje total de progresión.
     * Ignora deltas superiores al 10% para evitar lecturas fantasmas por saltos de página/capítulo.
     */
    fun calculateWordsDelta(
        currentProgression: Double,
        lastRecordedProgression: Double,
        estimatedWordCount: Long
    ): Long {
        val delta = abs(currentProgression - lastRecordedProgression)
        return if (delta > 0 && delta < 0.1) {
            (delta * estimatedWordCount).toLong()
        } else {
            0L
        }
    }

    /**
     * Calcula el tiempo restante estimado para finalizar el libro completo en formato amigable.
     */
    fun calculateBookTimeRemaining(
        totalWords: Long,
        totalProgression: Double,
        activeWpm: Int
    ): String {
        val wpm = activeWpm.coerceAtLeast(50)
        val bookWordsRemaining = (totalWords * (1.0 - totalProgression)).coerceAtLeast(0.0)
        val bookMinLeft = (bookWordsRemaining / wpm).toInt()
        return if (bookMinLeft > 0) {
            if (bookMinLeft >= 60) {
                "${bookMinLeft / 60}h ${bookMinLeft % 60}m restantes"
            } else {
                "$bookMinLeft min restantes en el libro"
            }
        } else {
            "Terminando libro"
        }
    }

    /**
     * Calcula el tiempo restante estimado para terminar el capítulo actual y el progreso global del libro.
     */
    fun calculateChapterProgress(
        locatorProgression: Double,
        resourceIndex: Int,
        positionsByResource: List<List<Locator>>,
        totalPositions: Int,
        activeWpm: Int
    ): ChapterProgressResult? {
        if (totalPositions <= 0 || resourceIndex < 0) return null
        val currentResourcePositions = positionsByResource.getOrNull(resourceIndex) ?: return null
        if (currentResourcePositions.isEmpty()) return null

        val positionInResource = currentResourcePositions
            .indexOfFirst { (it.locations.progression ?: 0.0) >= locatorProgression }
            .coerceAtLeast(0)

        val remainingPages = (currentResourcePositions.size - positionInResource).coerceAtLeast(0)
        val chapterWordsRemaining = remainingPages * 250 // Estimación de 250 palabras por página estándar
        val wpm = activeWpm.coerceAtLeast(50)
        val chapterMinLeft = (chapterWordsRemaining / wpm).toInt()

        val chapterTimeRemaining = if (chapterMinLeft > 0) {
            "$chapterMinLeft min para el capítulo"
        } else {
            "Terminando capítulo"
        }

        var positionsBefore = 0
        val limit = minOf(resourceIndex, positionsByResource.size)
        for (i in 0 until limit) {
            positionsBefore += positionsByResource[i].size
        }

        val globalProgress = ((positionsBefore + positionInResource).toFloat() / totalPositions).coerceIn(0f, 1f)
        return ChapterProgressResult(chapterTimeRemaining, globalProgress)
    }

    /**
     * Registra una página leída en las estadísticas diarias de Room (`reading_stats`), si aún no ha sido registrada hoy.
     */
    suspend fun recordPageRead(
        pageKey: String,
        isAlreadyRead: Boolean,
        dateString: String = LocalDate.now().toString()
    ): Boolean {
        if (isAlreadyRead || pageKey.isBlank()) return false
        val stats = readingStatsDao.getStatsForDate(dateString) ?: ReadingStatsEntity(dateString)
        readingStatsDao.insertOrUpdate(
            stats.copy(
                pagesRead = stats.pagesRead + 1,
                booksActive = if (stats.booksActive == 0) 1 else stats.booksActive
            )
        )
        return true
    }

    /**
     * Inserta una nueva sesión de lectura en la base de datos.
     */
    suspend fun recordSessionStart(session: ReadingSessionEntity) {
        readingSessionDao.insert(session)
    }

    /**
     * Cierra una sesión de lectura abierta actualizando fecha de fin, locator y palabras leídas.
     */
    suspend fun recordSessionClose(
        sessionId: String,
        endAt: Long,
        endLocatorJson: String,
        wordsRead: Long
    ) {
        readingSessionDao.closeSession(
            id = sessionId,
            endAt = endAt,
            endLocator = endLocatorJson,
            wordsRead = wordsRead
        )
    }

    /**
     * Consulta las sesiones de lectura registradas hoy.
     */
    fun getTodaySessions(startOfDayMs: Long): Flow<List<ReadingSessionEntity>> {
        return readingSessionDao.todaySessions(startOfDayMs)
    }

    /**
     * Consulta las sesiones de un libro específico.
     */
    fun getSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>> {
        return readingSessionDao.getByBook(bookId)
    }

    /**
     * Calcula la racha consecutiva de lectura (streak) en días.
     */
    suspend fun calculateReadingStreak(lookbackDays: Long = 60L): Int {
        val sinceMs = System.currentTimeMillis() - (lookbackDays * 24 * 60 * 60 * 1000L)
        val daysList = readingSessionDao.getUniqueReadingDays(sinceMs).firstOrNull() ?: emptyList()
        if (daysList.isEmpty()) return 0

        val dateSet = daysList.mapNotNull {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }.toSet()

        var streak = 0
        var checkDate = LocalDate.now()

        // Si hoy aún no ha leído, comprobamos ayer para no penalizar el día en curso
        if (!dateSet.contains(checkDate)) {
            checkDate = checkDate.minusDays(1)
        }

        while (dateSet.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return streak
    }
}
