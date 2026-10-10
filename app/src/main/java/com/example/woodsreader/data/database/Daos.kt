package com.example.woodsreader.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.woodsreader.data.model.ReadingMode
import kotlinx.coroutines.flow.Flow

// ── BookDao ───────────────────────────────────────────────────────────────────
@Dao
interface BookDao {

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    @Delete
    suspend fun delete(book: BookEntity)

    @Transaction
    @Query("""
        SELECT * FROM books
        ORDER BY
            CASE WHEN lastOpenedAt IS NULL THEN 1 ELSE 0 END ASC,
            lastOpenedAt DESC,
            id ASC
    """)
    fun getAll(): androidx.paging.PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getByIdFlow(id: String): Flow<BookEntity?>

    @Transaction
    @Query("SELECT * FROM books WHERE readingStatus = :status ORDER BY lastOpenedAt DESC NULLS LAST")
    fun getByStatus(status: String): androidx.paging.PagingSource<Int, BookEntity>

    @Transaction
    @Query("SELECT * FROM books WHERE collectionId = :collectionId ORDER BY lastOpenedAt DESC NULLS LAST")
    fun getBooksByCollection(collectionId: String): androidx.paging.PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT 1")
    fun getLastOpened(): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentBooks(limit: Int = 10): Flow<List<BookEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE filePath = :path)")
    suspend fun existsByPath(path: String): Boolean

    @Query("SELECT * FROM books WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): BookEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE fileHash = :hash)")
    suspend fun existsByHash(hash: String): Boolean

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): BookEntity?

    @Query("""
        UPDATE books SET
            readingProgressPercent = :progress,
            currentLocatorJson = :locator,
            lastOpenedAt = :time
        WHERE id = :id
    """)
    suspend fun updateProgress(id: String, progress: Float, locator: String, time: Long)

    @Query("""
        UPDATE books SET
            readingProgressPercent = :progress,
            currentLocatorJson = :locator,
            fallbackLocatorHref = :fallbackHref,
            fallbackLocatorPosition = :fallbackPosition,
            lastOpenedAt = :time
        WHERE id = :id
    """)
    suspend fun updateProgressWithFallback(
        id: String,
        progress: Float,
        locator: String,
        fallbackHref: String,
        fallbackPosition: Int,
        time: Long
    )

    @Query("UPDATE books SET lastOpenedAt = :time WHERE id = :id")
    suspend fun updateLastOpened(id: String, time: Long)

    @Query("UPDATE books SET coverCachePath = :path WHERE id = :id")
    suspend fun updateCover(id: String, path: String)

    @Query("SELECT * FROM books WHERE coverCachePath IS NULL OR coverCachePath = '' LIMIT :limit")
    suspend fun getBooksMissingCover(limit: Int = 50): List<BookEntity>

    @Query("UPDATE books SET coverCachePath = NULL WHERE id = :id")
    suspend fun clearCover(id: String)

    @Query("UPDATE books SET coverCachePath = NULL")
    suspend fun clearAllCovers()

    // Búsqueda FTS4 - mucho más rápido que LIKE para 1000+ libros
    @Transaction
    @Query("""
        SELECT books.* FROM books
        INNER JOIN books_fts ON books.rowid = books_fts.rowid
        WHERE books_fts MATCH :query
        ORDER BY lastOpenedAt DESC NULLS LAST
    """)
    fun searchBooks(query: String): androidx.paging.PagingSource<Int, BookEntity>

    @RawQuery(observedEntities = [BookEntity::class])
    fun advancedSearchBooksRaw(query: androidx.sqlite.db.SupportSQLiteQuery): androidx.paging.PagingSource<Int, BookEntity>

    @RawQuery(observedEntities = [BookEntity::class])
    fun advancedFilterBooksRaw(query: androidx.sqlite.db.SupportSQLiteQuery): androidx.paging.PagingSource<Int, BookEntity>

    

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    // Estadísticas
    @Query("SELECT COUNT(*) FROM books WHERE readingStatus = 'COMPLETED'")
    fun getCompletedBooksCount(): Flow<Int>

    @Query("SELECT SUM(totalReadingTimeMs) FROM books")
    fun getTotalReadingTimeMs(): Flow<Long?>

    @Query("SELECT * FROM books WHERE readingStatus = :status ORDER BY lastOpenedAt DESC NULLS LAST LIMIT 1")
    fun getFirstByStatus(status: String): Flow<BookEntity?>

    @Query("UPDATE books SET totalReadingTimeMs = totalReadingTimeMs + :addMs WHERE id = :id")
    suspend fun addReadingTime(id: String, addMs: Long)

    // ── Incremental scan support ──────────────────────────────────────────────
    @Query("SELECT importedAt FROM books WHERE filePath = :path LIMIT 1")
    suspend fun getImportedAtByPath(path: String): Long?

    // ── Deep Enrich support ───────────────────────────────────────────────────
    @Query("""
        SELECT * FROM books 
        WHERE fileHash = '' OR fileHash LIKE 'pending-%' OR author = 'Unknown Author' OR author = '' OR coverCachePath IS NULL 
        LIMIT :limit
    """)
    suspend fun getUnenrichedBooks(limit: Int = 50): List<BookEntity>

    // ── Orphan cleanup support ────────────────────────────────────────────────
    @Query("SELECT filePath FROM books")
    suspend fun getAllKnownPaths(): List<String>

    @Query("SELECT id, filePath FROM books")
    suspend fun getAllBookPaths(): List<BookPathProjection>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM books")
    suspend fun getAllBooks(): List<BookEntity>
}

// ── HighlightDao ──────────────────────────────────────────────────────────────
@Dao
interface HighlightDao {

    @Upsert
    suspend fun upsert(highlight: HighlightEntity)

    @Delete
    suspend fun delete(highlight: HighlightEntity)

    @Transaction
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getByBook(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: String): HighlightEntity?

    @Query("SELECT * FROM highlights")
    suspend fun getAllHighlights(): List<HighlightEntity>

    @Upsert
    suspend fun upsertAll(highlights: List<HighlightEntity>)
}

// ── BookmarkDao ───────────────────────────────────────────────────────────────
@Dao
interface BookmarkDao {

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Transaction
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getByBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND isAutomatic = 1")
    suspend fun deleteAutomaticBookmarks(bookId: String)

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Upsert
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)
}

// ── ReadingSessionDao ─────────────────────────────────────────────────────────
@Dao
interface ReadingSessionDao {

    @Upsert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("UPDATE reading_sessions SET endAt = :endAt, endLocatorJson = :endLocator, wordsRead = :wordsRead WHERE id = :id")
    suspend fun closeSession(id: String, endAt: Long, endLocator: String, wordsRead: Long)

    @Transaction
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startAt DESC")
    fun getByBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT SUM(wordsRead) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun totalWordsRead(bookId: String): Long?

    // << CHANGE: Añadido parámetro startOfDayMs para permitir búsqueda por índice
    @Transaction
    @Query("SELECT * FROM reading_sessions WHERE startAt >= :startOfDayMs ORDER BY startAt DESC")
    fun todaySessions(startOfDayMs: Long): Flow<List<ReadingSessionEntity>>

    // << CHANGE: Añadido filtro por timestamp para optimizar la obtención de días únicos
    @Query("SELECT DISTINCT DATE(startAt/1000, 'unixepoch') FROM reading_sessions WHERE startAt >= :sinceMs ORDER BY startAt DESC")
    fun getUniqueReadingDays(sinceMs: Long): Flow<List<String>>

    // Devuelve la suma de progreso por día para los últimos 7 días
    @Query("""
        SELECT CAST(strftime('%w', startAt / 1000, 'unixepoch') AS INTEGER) as dayOfWeek,
               SUM(wordsRead) as totalWords
        FROM reading_sessions
        WHERE startAt >= :sevenDaysAgo
        GROUP BY dayOfWeek
        ORDER BY dayOfWeek ASC
    """)
    fun getPagesPerDayLastWeek(sevenDaysAgo: Long): Flow<List<DailyWordCount>>

    @Query("""
        SELECT DISTINCT date(startAt / 1000, 'unixepoch') as sessionDate
        FROM reading_sessions
        WHERE startAt >= :twentyEightDaysAgo
        ORDER BY sessionDate ASC
    """)
    fun getReadingDaysLast28Days(twentyEightDaysAgo: Long): Flow<List<String>>

    // ── Consultas para Analítica Predictiva (Parte 3) ──────────────────────────
    
    @Query("""
        SELECT AVG(wordsRead / ((endAt - startAt) / 60000.0)) 
        FROM reading_sessions 
        WHERE mode = :mode AND (endAt - startAt) > 10000 AND wordsRead > 0
    """)
    fun getAverageWpmByMode(mode: ReadingMode): Flow<Double?>

    @Query("""
        SELECT AVG(dailyTotal) FROM (
            SELECT SUM(wordsRead) as dailyTotal 
            FROM reading_sessions 
            WHERE startAt >= :sinceMs 
            GROUP BY date(startAt / 1000, 'unixepoch')
        )
    """)
    fun getAverageWordsPerDay(sinceMs: Long): Flow<Double?>

    @Query("""
        SELECT AVG(wordsRead) as avgWords, 
               AVG((endAt - startAt) / 60000.0) as avgMinutes,
               mode
        FROM reading_sessions 
        WHERE startAt >= :sinceMs
        GROUP BY mode
    """)
    fun getSessionSummaries(sinceMs: Long): Flow<List<SessionSummary>>

    // ── Motor de Compactación (Grooming Parte 4) ──────────────────────────────
    
    @Delete
    suspend fun deleteSessions(sessions: List<ReadingSessionEntity>)

    // ── Inteligencia Visual 2.0 (Parte 5) ────────────────────────────────────

    @Query("""
        SELECT CAST(strftime('%H', startAt / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour, 
               SUM(wordsRead) as totalWords,
               AVG(wordsRead / ((endAt - startAt) / 60000.0)) as avgWpm
        FROM reading_sessions 
        WHERE startAt >= :sinceMs AND endAt IS NOT NULL
        GROUP BY hour
        ORDER BY hour ASC
    """)
    fun getHourlyActivity(sinceMs: Long): Flow<List<HourlyActivity>>

    @Transaction
    @Query("""
        SELECT b.title, b.id as bookId, SUM(s.wordsRead) as totalWords, b.coverCachePath as coverPath
        FROM reading_sessions s
        JOIN books b ON s.bookId = b.id
        WHERE s.startAt >= :sinceMs
        GROUP BY b.id
        ORDER BY totalWords DESC
    """)
    fun getBookDistribution(sinceMs: Long): Flow<List<BookDistribution>>

    @Query("""
        SELECT mode, AVG((endAt - startAt) / 60000.0) as avgDurationMinutes, COUNT(*) as totalSessions
        FROM reading_sessions
        WHERE endAt IS NOT NULL AND (endAt - startAt) > 10000
        GROUP BY mode
    """)
    fun getModeEndurance(): Flow<List<ModeEndurance>>

    // ── Motor de Sanación (Parte 8) ──────────────────────────────────────────

    @Query("SELECT * FROM reading_sessions WHERE wordsRead = 0 AND endAt IS NOT NULL")
    suspend fun getBrokenSessions(): List<ReadingSessionEntity>

    @Query("UPDATE reading_sessions SET wordsRead = :words WHERE id = :sessionId")
    suspend fun updateWordsRead(sessionId: String, words: Long)

    // Motor de Limpieza: sesiones con duración inválida y sin palabras leídas
    @Query("SELECT * FROM reading_sessions WHERE endAt IS NOT NULL AND (endAt - startAt) < 1000 AND wordsRead = 0")
    suspend fun getIrrelevantSessions(): List<ReadingSessionEntity>

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("SELECT * FROM reading_sessions")
    suspend fun getAllSessions(): List<ReadingSessionEntity>

    @Upsert
    suspend fun upsertAll(sessions: List<ReadingSessionEntity>)
}

/**
 * Proyección ligera para cálculos estadísticos sin cargar locators pesados (Parte 4).
 */
data class SessionSummary(
    val avgWords: Double,
    val avgMinutes: Double,
    val mode: com.example.woodsreader.data.model.ReadingMode
)

/**
 * Agregación de actividad por hora (0-23) para el mapa de calor (Parte 5).
 */
data class HourlyActivity(
    val hour: Int,
    val totalWords: Long,
    val avgWpm: Double
)

/**
 * Desglose de consumo por libro para el gráfico de distribución (Parte 5).
 */
data class BookDistribution(
    val title: String,
    val bookId: String,
    val totalWords: Long,
    val coverPath: String? = null
)

/**
 * Persistencia por modo (Parte 6).
 */
data class ModeEndurance(
    val mode: com.example.woodsreader.data.model.ReadingMode,
    val avgDurationMinutes: Double,
    val totalSessions: Int
)

/**
 * Proyección ligera con solo id y ruta para verificar existencia de archivos (Limpieza de huérfanos).
 */
data class BookPathProjection(
    val id: String,
    val filePath: String
)

// ── CollectionDao ─────────────────────────────────────────────────────────────
@Dao
interface CollectionDao {

    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Delete
    suspend fun delete(collection: CollectionEntity)

    @Transaction
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getAll(): Flow<List<CollectionEntity>>
}

// ── ReadingStatsDao ───────────────────────────────────────────────────────────
@Dao
interface ReadingStatsDao {
    @Query("SELECT * FROM reading_stats WHERE date = :date")
    suspend fun getStatsForDate(date: String): ReadingStatsEntity?

    @Upsert
    suspend fun insertOrUpdate(stats: ReadingStatsEntity)

    @Query("SELECT * FROM reading_stats ORDER BY date DESC LIMIT 30")
    fun getAllStats(): Flow<List<ReadingStatsEntity>>
}

// ── TtsRuleDao ────────────────────────────────────────────────────────────────
@Dao
interface TtsRuleDao {
    @Query("SELECT * FROM tts_rules ORDER BY id ASC")
    fun getAllRulesFlow(): Flow<List<TtsRuleEntity>>

    @Query("SELECT * FROM tts_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<TtsRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: TtsRuleEntity)

    @Delete
    suspend fun delete(rule: TtsRuleEntity)
}


