package com.example.woodsreader.data.repository

import androidx.paging.Pager
import androidx.room.withTransaction
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.woodsreader.data.database.AppDatabase
import com.example.woodsreader.data.database.BookDao
import com.example.woodsreader.data.database.CollectionDao
import com.example.woodsreader.data.database.CollectionEntity
import com.example.woodsreader.data.database.BookDistribution
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkDao
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.TtsRuleDao
import com.example.woodsreader.data.database.TtsRuleEntity
import com.example.woodsreader.data.database.DailyWordCount
import com.example.woodsreader.data.database.HighlightDao
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.HourlyActivity
import com.example.woodsreader.data.database.ModeEndurance
import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsDao
import com.example.woodsreader.data.database.ReadingStatsEntity
import com.example.woodsreader.data.database.SessionSummary
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for book data.
 * ViewModels access all data exclusively through this class.
 *
 * All DB operations are delegated to DAOs that run on Room's internal
 * dispatcher (IO-backed), so no explicit Dispatchers.IO needed here.
 */
@Singleton
class BookRepository @Inject constructor(
    private val db: AppDatabase,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val sessionDao: ReadingSessionDao,
    private val readingStatsDao: ReadingStatsDao,
    private val collectionDao: CollectionDao,
    private val ttsRuleDao: TtsRuleDao
) {
    private companion object {
        const val DEFAULT_PAGE_SIZE = 30

        /** Returns a Pager Flow for paged queries. */
        fun <T : Any> paged(pageSize: Int = DEFAULT_PAGE_SIZE, source: () -> androidx.paging.PagingSource<Int, T>) =
            Pager(PagingConfig(pageSize = pageSize)) { source() }.flow
    }

    // ── Books ─────────────────────────────────────────────────────────────────

    fun getAllBooks(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<BookEntity>> =
        paged(pageSize) { bookDao.getAll() }

    private fun buildFtsMatchQuery(rawQuery: String, target: String = "ALL"): String {
        val reservedOperators = setOf("AND", "OR", "NOT")
        val tokens = rawQuery
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() && it.uppercase() !in reservedOperators }
        if (tokens.isEmpty()) return ""
        val prefix = when (target) {
            "TITLE" -> "title:"
            "AUTHOR" -> "author:"
            "DESCRIPTION" -> "description:"
            else -> ""
        }
        return tokens.joinToString(" ") { "$prefix$it*" }
    }

    fun searchBooks(query: String, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<BookEntity>> {
        val matchQuery = buildFtsMatchQuery(query)
        if (matchQuery.isEmpty()) return getAllBooks(pageSize)
        return paged(pageSize) { bookDao.searchBooks(matchQuery) }
    }

    fun searchAndFilterBooks(
        query: String,
        target: String,
        format: String?,
        status: String?,
        collectionId: String?,
        sortOrder: String,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Flow<PagingData<BookEntity>> {
        val formatParam = format.takeIf { it != null && it != "ALL" }
        val statusParam = status.takeIf { it != null && it != "ALL" }
        val collectionParam = collectionId.takeIf { it != null && it != "ALL" }
        val matchQuery = buildFtsMatchQuery(query, target)
        
        return if (matchQuery.isNotEmpty()) {
            paged(pageSize) {
                val args = mutableListOf<Any>()
                var sql = "SELECT books.* FROM books INNER JOIN books_fts ON books.rowid = books_fts.rowid WHERE books_fts MATCH ?"
                args.add(matchQuery)
                
                if (formatParam != null) { sql += " AND books.format = ?"; args.add(formatParam) }
                if (statusParam != null) { sql += " AND books.readingStatus = ?"; args.add(statusParam) }
                if (collectionParam != null) { sql += " AND books.collectionId = ?"; args.add(collectionParam) }
                
                sql += when (sortOrder) {
                    "TITLE_ASC" -> " ORDER BY books.title ASC, books.id ASC"
                    "TITLE_DESC" -> " ORDER BY books.title DESC, books.id ASC"
                    "AUTHOR_ASC" -> " ORDER BY books.author ASC, books.id ASC"
                    "LAST_OPENED" -> " ORDER BY books.lastOpenedAt DESC, books.id ASC"
                    "DATE_ADDED" -> " ORDER BY books.importedAt DESC, books.id ASC"
                    else -> " ORDER BY books.id ASC"
                }
                bookDao.advancedSearchBooksRaw(androidx.sqlite.db.SimpleSQLiteQuery(sql, args.toTypedArray()))
            }
        } else {
            paged(pageSize) {
                val args = mutableListOf<Any>()
                var sql = "SELECT books.* FROM books WHERE 1=1"
                
                if (formatParam != null) { sql += " AND books.format = ?"; args.add(formatParam) }
                if (statusParam != null) { sql += " AND books.readingStatus = ?"; args.add(statusParam) }
                if (collectionParam != null) { sql += " AND books.collectionId = ?"; args.add(collectionParam) }
                
                sql += when (sortOrder) {
                    "TITLE_ASC" -> " ORDER BY books.title ASC, books.id ASC"
                    "TITLE_DESC" -> " ORDER BY books.title DESC, books.id ASC"
                    "AUTHOR_ASC" -> " ORDER BY books.author ASC, books.id ASC"
                    "LAST_OPENED" -> " ORDER BY books.lastOpenedAt DESC, books.id ASC"
                    "DATE_ADDED" -> " ORDER BY books.importedAt DESC, books.id ASC"
                    else -> " ORDER BY books.id ASC"
                }
                bookDao.advancedFilterBooksRaw(androidx.sqlite.db.SimpleSQLiteQuery(sql, args.toTypedArray()))
            }
        }
    }

    fun getBooksByStatus(status: String, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<BookEntity>> =
        paged(pageSize) { bookDao.getByStatus(status) }

    fun getBooksByCollection(collectionId: String, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<BookEntity>> =
        paged(pageSize) { bookDao.getBooksByCollection(collectionId) }

    // ── Collections ───────────────────────────────────────────────────────────

    fun getAllCollections(): Flow<List<CollectionEntity>> = collectionDao.getAll()

    suspend fun saveCollection(collection: CollectionEntity) = collectionDao.upsert(collection)

    suspend fun deleteCollection(collection: CollectionEntity) = collectionDao.delete(collection)

    suspend fun getBook(id: String): BookEntity? = bookDao.getById(id)

    fun getBookFlow(id: String): Flow<BookEntity?> = bookDao.getByIdFlow(id)

    fun getLastOpenedBook(): Flow<BookEntity?> = bookDao.getLastOpened()

    /** Returns the N most recently opened books for the history sheet. */
    fun getRecentBooks(limit: Int = 10): Flow<List<BookEntity>> = bookDao.getRecentBooks(limit)

    fun getCurrentReadingBook(): Flow<BookEntity?> =
        bookDao.getFirstByStatus(ReadingStatus.IN_PROGRESS.name)

    suspend fun upsertBook(book: BookEntity)           = bookDao.upsert(book)
    suspend fun upsertBooks(books: List<BookEntity>)   = bookDao.upsertAll(books)
    suspend fun deleteBook(book: BookEntity)           = bookDao.delete(book)
    suspend fun deleteBookById(id: String)             = bookDao.deleteById(id)
    suspend fun bookExistsByHash(hash: String)         = bookDao.existsByHash(hash)
    suspend fun getBookByHash(hash: String)            = bookDao.getByHash(hash)
    suspend fun getBookByPath(path: String): BookEntity? = bookDao.getByPath(path)
    suspend fun existsByPath(path: String): Boolean = bookDao.existsByPath(path)

    // ── Escrituras parciales por campo ────────────────────────────────────────
    // Preferir estas sobre upsertBook(book.copy(...)): un snapshot atrasado no debe
    // pisar progreso, locator ni tiempo de lectura modificados entretanto.
    suspend fun updateReadingStatus(bookId: String, status: ReadingStatus) =
        bookDao.updateReadingStatus(bookId, status)

    suspend fun updateUserRating(bookId: String, rating: Int?) =
        bookDao.updateUserRating(bookId, rating)

    suspend fun updateUserNotes(bookId: String, notes: String) =
        bookDao.updateUserNotes(bookId, notes)

    suspend fun updateDescription(bookId: String, description: String) =
        bookDao.updateDescription(bookId, description)

    suspend fun updateBookCollection(bookId: String, collectionId: String?) =
        bookDao.updateCollection(bookId, collectionId)

    suspend fun updateEnrichedMetadata(
        bookId: String,
        title: String,
        author: String,
        description: String,
        language: String,
        coverCachePath: String?,
        fileHash: String
    ) = bookDao.updateEnrichedMetadata(bookId, title, author, description, language, coverCachePath, fileHash)

    /**
     * Fusión atómica de un libro duplicado en el canónico. Dentro de una sola transacción:
     * relee ambas filas (no usa snapshots previos), calcula la fusión sobre los valores
     * actuales, traslada subrayados, marcadores, sesiones y reglas TTS del duplicado, guarda
     * el canónico y borra el duplicado. Sin la reasignación previa, el ON DELETE CASCADE de
     * las FKs borraría los hijos del duplicado.
     *
     * Si el canónico ya no existe no se hace nada (el duplicado se conserva).
     */
    suspend fun mergeDuplicateBook(canonicalId: String, duplicateId: String, fallbackCoverPath: String?) {
        if (canonicalId == duplicateId) return
        db.withTransaction {
            val canonical = bookDao.getById(canonicalId) ?: return@withTransaction
            val duplicate = bookDao.getById(duplicateId)

            if (duplicate != null) {
                val duplicateIsAhead = duplicate.readingProgressPercent > canonical.readingProgressPercent
                val mergedStatus = when {
                    canonical.readingStatus == ReadingStatus.ABANDONED -> ReadingStatus.ABANDONED
                    canonical.readingStatus == ReadingStatus.COMPLETED -> ReadingStatus.COMPLETED
                    duplicateIsAhead -> duplicate.readingStatus
                    else -> canonical.readingStatus
                }
                bookDao.upsert(
                    canonical.copy(
                        readingProgressPercent = maxOf(canonical.readingProgressPercent, duplicate.readingProgressPercent),
                        readingStatus = mergedStatus,
                        currentLocatorJson = if (duplicateIsAhead) duplicate.currentLocatorJson else canonical.currentLocatorJson,
                        fallbackLocatorHref = if (duplicateIsAhead) duplicate.fallbackLocatorHref else canonical.fallbackLocatorHref,
                        fallbackLocatorPosition = if (duplicateIsAhead) duplicate.fallbackLocatorPosition else canonical.fallbackLocatorPosition,
                        totalReadingTimeMs = canonical.totalReadingTimeMs + duplicate.totalReadingTimeMs,
                        lastOpenedAt = maxOf(canonical.lastOpenedAt ?: 0L, duplicate.lastOpenedAt ?: 0L).takeIf { it > 0L },
                        userRating = canonical.userRating ?: duplicate.userRating,
                        userNotes = canonical.userNotes.ifBlank { duplicate.userNotes },
                        collectionId = canonical.collectionId ?: duplicate.collectionId,
                        perBookSettingsJson = canonical.perBookSettingsJson ?: duplicate.perBookSettingsJson,
                        coverCachePath = canonical.coverCachePath ?: fallbackCoverPath
                    )
                )
            }

            highlightDao.reassignBook(duplicateId, canonicalId)
            bookmarkDao.reassignBook(duplicateId, canonicalId)
            sessionDao.reassignBook(duplicateId, canonicalId)
            ttsRuleDao.reassignBook(duplicateId, canonicalId)
            bookDao.deleteById(duplicateId)
        }
    }

    // ── Incremental scan support ──────────────────────────────────────────────
    suspend fun getImportedAtByPath(path: String): Long? = bookDao.getImportedAtByPath(path)

    // ── Deep Enrich support ───────────────────────────────────────────────────
    suspend fun getUnenrichedBooks(limit: Int = 50): List<BookEntity> = bookDao.getUnenrichedBooks(limit)

    // ── Orphan cleanup support ────────────────────────────────────────────────
    suspend fun getAllKnownPaths(): Set<String> = bookDao.getAllKnownPaths().toHashSet()
    suspend fun getAllBookPaths(): List<com.example.woodsreader.data.database.BookPathProjection> = bookDao.getAllBookPaths()

    suspend fun updateReadingProgress(bookId: String, progress: Float, locatorJson: String) = bookDao.updateProgress(
                id      = bookId,
                progress = progress,
                locator = locatorJson,
                time    = System.currentTimeMillis()
            )

    /**
     * Saves progress with a fallback position so the reader can recover
     * the approximate location even if the CFI locator becomes invalid.
     */
    suspend fun updateReadingProgressWithFallback(
        bookId: String,
        progress: Float,
        locatorJson: String,
        fallbackHref: String,
        fallbackPosition: Int
    ) = bookDao.updateProgressWithFallback(
            id               = bookId,
            progress         = progress,
            locator          = locatorJson,
            fallbackHref     = fallbackHref,
            fallbackPosition = fallbackPosition,
            time             = System.currentTimeMillis()
        )

    suspend fun addReadingTime(bookId: String, durationMs: Long) = bookDao.addReadingTime(bookId, durationMs)

    suspend fun markOpened(bookId: String) = bookDao.updateLastOpened(bookId, System.currentTimeMillis())

    suspend fun updateCoverPath(bookId: String, coverPath: String) = bookDao.updateCover(bookId, coverPath)
    suspend fun getBooksMissingCover(limit: Int = 50): List<BookEntity> = bookDao.getBooksMissingCover(limit)
    suspend fun clearCover(bookId: String) = bookDao.clearCover(bookId)
    suspend fun clearAllCovers() = bookDao.clearAllCovers()

    // ── Highlights ────────────────────────────────────────────────────────────

    fun getHighlights(bookId: String): Flow<List<HighlightEntity>> = highlightDao.getByBook(bookId)
    suspend fun saveHighlight(highlight: HighlightEntity)  = highlightDao.upsert(highlight)
    suspend fun deleteHighlight(highlight: HighlightEntity) = highlightDao.delete(highlight)

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getByBook(bookId)
    suspend fun saveBookmark(bookmark: BookmarkEntity)    = bookmarkDao.upsert(bookmark)
    suspend fun deleteBookmark(bookmark: BookmarkEntity)  = bookmarkDao.delete(bookmark)
    suspend fun clearAutoBookmarks(bookId: String)        = bookmarkDao.deleteAutomaticBookmarks(bookId)

    // ── Reading sessions ──────────────────────────────────────────────────────

    suspend fun startSession(session: ReadingSessionEntity) = sessionDao.insert(session)

    suspend fun closeSession(id: String, endLocatorJson: String, wordsRead: Long) = sessionDao.closeSession(
                id        = id,
                endAt     = System.currentTimeMillis(),
                endLocator = endLocatorJson,
                wordsRead = wordsRead
            )

    suspend fun deleteSession(id: String) = sessionDao.deleteSessionById(id)

    fun getSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>> =
        sessionDao.getByBook(bookId)

    fun todaySessions(): Flow<List<ReadingSessionEntity>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return sessionDao.todaySessions(startOfDay)
    }

    // ── Analytics / Stats ─────────────────────────────────────────────────────

    fun getCompletedBooksCount(): Flow<Int>           = bookDao.getCompletedBooksCount()
    fun getInProgressBooksCount(): Flow<Int>          = bookDao.getInProgressBooksCount()
    fun getTotalReadingTimeMs(): Flow<Long?>          = bookDao.getTotalReadingTimeMs()
    fun getUniqueReadingDays(): Flow<List<String>>    = sessionDao.getUniqueReadingDays(0L)
    fun getWeeklyWords(sevenDaysAgo: Long): Flow<List<DailyWordCount>> =
        sessionDao.getPagesPerDayLastWeek(sevenDaysAgo)
    fun getReadingDaysLast28Days(since: Long): Flow<List<String>> =
        sessionDao.getReadingDaysLast28Days(since)
    fun getAverageWpmByMode(mode: ReadingMode): Flow<Double?>        = sessionDao.getAverageWpmByMode(mode)
    fun getAverageWordsPerDay(sinceMs: Long): Flow<Double?>          = sessionDao.getAverageWordsPerDay(sinceMs)
    fun getSessionSummaries(sinceMs: Long): Flow<List<SessionSummary>> = sessionDao.getSessionSummaries(sinceMs)
    fun getHourlyActivity(sinceMs: Long): Flow<List<HourlyActivity>> = sessionDao.getHourlyActivity(sinceMs)
    fun getBookDistribution(sinceMs: Long): Flow<List<BookDistribution>> = sessionDao.getBookDistribution(sinceMs)
    fun getModeEndurance(): Flow<List<ModeEndurance>>                 = sessionDao.getModeEndurance()

    /** Cleanup: removes sessions too short to be meaningful. */
    suspend fun cleanupInvalidSessions() {
        val irrelevant = sessionDao.getIrrelevantSessions()
        if (irrelevant.isNotEmpty()) sessionDao.deleteSessions(irrelevant)
    }

    /**
     * Self-healing: repairs zero-word sessions by estimating words from duration.
     * Conservative: 100 WPM for manual reading, 150 WPM for TTS.
     */
    suspend fun repairZeroWordSessions() {
        val broken = sessionDao.getBrokenSessions()
        if (broken.isEmpty()) return

        broken.forEach { session ->
            val durationMin = ((session.endAt ?: return@forEach) - session.startAt) / 60_000.0
            if (durationMin > 0.5) {
                val estimatedWords = (durationMin * if (session.mode == ReadingMode.MANUAL) 100 else 150).toLong()
                sessionDao.updateWordsRead(session.id, estimatedWords)
            }
        }
    }

    // ── Daily reading stats ───────────────────────────────────────────────────

    suspend fun getReadingStats(date: String): ReadingStatsEntity? = readingStatsDao.getStatsForDate(date)

    suspend fun saveReadingStats(stats: ReadingStatsEntity) = readingStatsDao.insertOrUpdate(stats)

    fun getAllReadingStats(): Flow<List<ReadingStatsEntity>> =
        readingStatsDao.getAllStats()

    // ── TtsRules ──────────────────────────────────────────────────────────────
    fun getTtsRules(): Flow<List<TtsRuleEntity>> = ttsRuleDao.getAllRulesFlow()
    suspend fun saveTtsRule(rule: TtsRuleEntity) = ttsRuleDao.insert(rule)
    suspend fun deleteTtsRule(rule: TtsRuleEntity) = ttsRuleDao.delete(rule)

    suspend fun addWordExclusionRule(
        pattern: String,
        isExactMatch: Boolean = true,
        applyToAllBooks: Boolean = true,
        bookId: String? = null
    ): TtsRuleEntity {
        val rule = TtsRuleEntity(
            pattern = pattern.trim(),
            replacement = "",
            isRegex = !isExactMatch,
            isEnabled = true,
            bookId = if (applyToAllBooks) null else bookId
        )
        ttsRuleDao.insert(rule)
        return rule
    }
}
