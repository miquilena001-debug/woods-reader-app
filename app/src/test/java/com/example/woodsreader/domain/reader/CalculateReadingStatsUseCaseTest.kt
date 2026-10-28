package com.example.woodsreader.domain.reader

import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsDao
import com.example.woodsreader.data.database.ReadingStatsEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import java.time.LocalDate

class CalculateReadingStatsUseCaseTest {

    private val readingSessionDao: ReadingSessionDao = mockk(relaxed = true)
    private val readingStatsDao: ReadingStatsDao = mockk(relaxed = true)

    private lateinit var useCase: CalculateReadingStatsUseCase

    @Before
    fun setUp() {
        useCase = CalculateReadingStatsUseCase(readingSessionDao, readingStatsDao)
    }

    private fun createMockLocatorWithProgression(progression: Double): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val locations = mockk<Locator.Locations>(relaxed = true)
        every { locations.progression } returns progression
        every { locator.locations } returns locations
        return locator
    }

    // ── 1. Palabras Por Minuto (WPM) Activas ──────────────────────────────────

    @Test
    fun testCalculateActiveWpm_under30sOrUnder50WordsReturnsDefault200() {
        val now = System.currentTimeMillis()

        // Caso 1: Duración menor a 30s (< 0.5 min)
        val wpmUnderTime = useCase.calculateActiveWpm(sessionStartTimeMs = now - 20_000L, wordsProcessed = 100L)
        assertEquals(200, wpmUnderTime)

        // Caso 2: Menos de 50 palabras procesadas
        val wpmUnderWords = useCase.calculateActiveWpm(sessionStartTimeMs = now - 60_000L, wordsProcessed = 40L)
        assertEquals(200, wpmUnderWords)
    }

    @Test
    fun testCalculateActiveWpm_normalReadingCalculatesCorrectWpmClamped() {
        val now = System.currentTimeMillis()
        // 60 segundos (1.0 min) y 260 palabras -> 260 WPM
        val wpm = useCase.calculateActiveWpm(sessionStartTimeMs = now - 60_000L, wordsProcessed = 260L)
        assertEquals(260, wpm)
    }

    @Test
    fun testCalculateActiveWpm_extremeWpmClampedToLimits() {
        val now = System.currentTimeMillis()

        // Extremo superior: 1000 palabras en 1 min -> limitado a 500
        val fastWpm = useCase.calculateActiveWpm(sessionStartTimeMs = now - 60_000L, wordsProcessed = 1000L)
        assertEquals(500, fastWpm)

        // Extremo inferior: 60 palabras en 2 min = 30 WPM -> limitado a 80
        val slowWpm = useCase.calculateActiveWpm(sessionStartTimeMs = now - 120_000L, wordsProcessed = 60L)
        assertEquals(80, slowWpm)
    }

    // ── 2. Delta de Palabras Leídas (Filtro de Lecturas Fantasmas) ───────────

    @Test
    fun testCalculateWordsDelta_normalDeltaCalculatesWords() {
        // Delta 5% sobre 100.000 palabras -> 5.000 palabras
        val deltaWords = useCase.calculateWordsDelta(
            currentProgression = 0.05,
            lastRecordedProgression = 0.0,
            estimatedWordCount = 100_000L
        )
        assertEquals(5_000L, deltaWords)
    }

    @Test
    fun testCalculateWordsDelta_jumpOver10PercentFilteredAsGhostRead() {
        // Salto de página/capítulo del 20% (0.40 - 0.20 = 0.20 >= 0.1) -> Ignorado (0 palabras)
        val ghostJump20 = useCase.calculateWordsDelta(
            currentProgression = 0.40,
            lastRecordedProgression = 0.20,
            estimatedWordCount = 100_000L
        )
        assertEquals(0L, ghostJump20)

        // Salto de 15% (0.35 - 0.20 = 0.15 >= 0.1) -> Ignorado (0 palabras)
        val ghostJump15 = useCase.calculateWordsDelta(
            currentProgression = 0.35,
            lastRecordedProgression = 0.20,
            estimatedWordCount = 100_000L
        )
        assertEquals(0L, ghostJump15)
    }

    @Test
    fun testCalculateWordsDelta_zeroOrNegativeDeltaReturnsZero() {
        val sameProgress = useCase.calculateWordsDelta(
            currentProgression = 0.20,
            lastRecordedProgression = 0.20,
            estimatedWordCount = 100_000L
        )
        assertEquals(0L, sameProgress)
    }

    // ── 3. Estimación de Tiempo Restante del Libro ───────────────────────────

    @Test
    fun testCalculateBookTimeRemaining_moreThan60MinutesFormatsHoursAndMinutes() {
        // 100.000 palabras, 50% leído -> 50.000 restantes a 200 WPM = 250 min = 4h 10m
        val timeStr = useCase.calculateBookTimeRemaining(
            totalWords = 100_000L,
            totalProgression = 0.5,
            activeWpm = 200
        )
        assertEquals("4h 10m restantes", timeStr)
    }

    @Test
    fun testCalculateBookTimeRemaining_lessThan60MinutesFormatsMinutes() {
        // 10.000 palabras, 50% leído -> 5.000 restantes a 200 WPM = 25 min
        val timeStr = useCase.calculateBookTimeRemaining(
            totalWords = 10_000L,
            totalProgression = 0.5,
            activeWpm = 200
        )
        assertEquals("25 min restantes en el libro", timeStr)
    }

    @Test
    fun testCalculateBookTimeRemaining_zeroOrFinishedFormatsTerminandoLibro() {
        val timeStr = useCase.calculateBookTimeRemaining(
            totalWords = 50_000L,
            totalProgression = 1.0,
            activeWpm = 200
        )
        assertEquals("Terminando libro", timeStr)
    }

    // ── 4. Progreso de Capítulo y Global ─────────────────────────────────────

    @Test
    fun testCalculateChapterProgress_invalidTotalPositionsOrResourceReturnsNull() {
        val nullForTotalZero = useCase.calculateChapterProgress(
            locatorProgression = 0.0,
            resourceIndex = 0,
            positionsByResource = emptyList(),
            totalPositions = 0,
            activeWpm = 200
        )
        assertNull(nullForTotalZero)

        val nullForInvalidResource = useCase.calculateChapterProgress(
            locatorProgression = 0.0,
            resourceIndex = -1,
            positionsByResource = emptyList(),
            totalPositions = 10,
            activeWpm = 200
        )
        assertNull(nullForInvalidResource)
    }

    @Test
    fun testCalculateChapterProgress_normalProgressCalculatesTimeRemainingAndGlobalProgress() {
        val res0 = listOf(
            createMockLocatorWithProgression(0.0),
            createMockLocatorWithProgression(0.2),
            createMockLocatorWithProgression(0.4),
            createMockLocatorWithProgression(0.6),
            createMockLocatorWithProgression(0.8)
        )
        val res1 = listOf(
            createMockLocatorWithProgression(0.0),
            createMockLocatorWithProgression(0.5)
        )
        val positions = listOf(res0, res1)
        val totalPositions = 7 // 5 + 2

        val result = useCase.calculateChapterProgress(
            locatorProgression = 0.4,
            resourceIndex = 0,
            positionsByResource = positions,
            totalPositions = totalPositions,
            activeWpm = 200
        )

        assertNotNull(result)
        // positionInResource = 2. remainingPages = 5 - 2 = 3.
        // chapterWordsRemaining = 3 * 250 = 750 palabras.
        // chapterMinLeft = 750 / 200 = 3 min.
        assertEquals("3 min para el capítulo", result!!.chapterTimeRemaining)
        // globalProgress = (0 + 2) / 7 = 0.2857f
        assertEquals(2f / 7f, result.globalProgress, 0.01f)
    }

    // ── 5. Racha de Lectura (Streak) ─────────────────────────────────────────

    @Test
    fun testCalculateReadingStreak_emptyDaysReturnsZero() = runTest {
        coEvery { readingSessionDao.getUniqueReadingDays(any()) } returns flowOf(emptyList())
        val streak = useCase.calculateReadingStreak()
        assertEquals(0, streak)
    }

    @Test
    fun testCalculateReadingStreak_streakIncludesToday() = runTest {
        val today = LocalDate.now()
        val days = listOf(
            today.toString(),
            today.minusDays(1).toString(),
            today.minusDays(2).toString()
        )
        coEvery { readingSessionDao.getUniqueReadingDays(any()) } returns flowOf(days)
        val streak = useCase.calculateReadingStreak()
        assertEquals(3, streak)
    }

    @Test
    fun testCalculateReadingStreak_streakIncludesYesterdayWhenTodayNotRead() = runTest {
        val today = LocalDate.now()
        // Hoy aún no ha leído, pero leyó ayer y antes de ayer
        val days = listOf(
            today.minusDays(1).toString(),
            today.minusDays(2).toString()
        )
        coEvery { readingSessionDao.getUniqueReadingDays(any()) } returns flowOf(days)
        val streak = useCase.calculateReadingStreak()
        assertEquals(2, streak)
    }

    @Test
    fun testCalculateReadingStreak_brokenStreakStopsAtGap() = runTest {
        val today = LocalDate.now()
        // Leyó hoy y ayer, pero faltó hace 2 días (gap) y leyó hace 3 días
        val days = listOf(
            today.toString(),
            today.minusDays(1).toString(),
            today.minusDays(3).toString()
        )
        coEvery { readingSessionDao.getUniqueReadingDays(any()) } returns flowOf(days)
        val streak = useCase.calculateReadingStreak()
        assertEquals(2, streak)
    }

    // ── 6. Registro de Páginas y Sesiones ────────────────────────────────────

    @Test
    fun testRecordPageRead_newValidPageInsertsOrUpdatesDao() = runTest {
        val todayStr = LocalDate.now().toString()
        coEvery { readingStatsDao.getStatsForDate(todayStr) } returns null

        val recorded = useCase.recordPageRead("page_ch1_p1", isAlreadyRead = false)
        assertTrue(recorded)

        coVerify(exactly = 1) {
            readingStatsDao.insertOrUpdate(
                match { it.date == todayStr && it.pagesRead == 1 && it.booksActive == 1 }
            )
        }
    }

    @Test
    fun testRecordPageRead_alreadyReadOrBlankKeyReturnsFalse() = runTest {
        val alreadyRead = useCase.recordPageRead("page_ch1_p1", isAlreadyRead = true)
        val blankKey = useCase.recordPageRead("   ", isAlreadyRead = false)
        assertFalse(alreadyRead)
        assertFalse(blankKey)

        coVerify(exactly = 0) { readingStatsDao.insertOrUpdate(any()) }
    }

    @Test
    fun testRecordSessionStartAndClose() = runTest {
        val session = ReadingSessionEntity(
            id = "sess_1",
            bookId = "book_1",
            startAt = 1000L,
            endAt = null,
            startLocatorJson = "loc_start",
            endLocatorJson = null,
            wordsRead = 0L
        )

        useCase.recordSessionStart(session)
        coVerify(exactly = 1) { readingSessionDao.insert(session) }

        useCase.recordSessionClose(
            sessionId = "sess_1",
            endAt = 2000L,
            endLocatorJson = "loc_end",
            wordsRead = 150L
        )
        coVerify(exactly = 1) {
            readingSessionDao.closeSession(
                id = "sess_1",
                endAt = 2000L,
                endLocator = "loc_end",
                wordsRead = 150L
            )
        }
    }
}
