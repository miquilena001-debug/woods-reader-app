package com.example.woodsreader.core.managers

import android.content.Context
import android.net.Uri
import com.example.woodsreader.data.database.BookDao
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkDao
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightDao
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.TtsRuleDao
import com.example.woodsreader.data.database.TtsRuleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WoodsFullBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val books: List<BookEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
    val ttsRules: List<BackupTtsRule> = emptyList()
)

@Serializable
data class BackupTtsRule(
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean,
    val isEnabled: Boolean,
    val bookId: String? = null
)

data class RestoreSummary(
    val booksRestored: Int,
    val highlightsRestored: Int,
    val bookmarksRestored: Int,
    val rulesRestored: Int
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val sessionDao: ReadingSessionDao,
    private val ttsRuleDao: TtsRuleDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val books = bookDao.getAllBooks()
        val highlights = highlightDao.getAllHighlights()
        val bookmarks = bookmarkDao.getAllBookmarks()
        val sessions = sessionDao.getAllSessions()
        val rules = ttsRuleDao.getAllRulesFlow().first().map {
            BackupTtsRule(
                pattern = it.pattern,
                replacement = it.replacement,
                isRegex = it.isRegex,
                isEnabled = it.isEnabled,
                bookId = it.bookId
            )
        }

        val payload = WoodsFullBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            books = books,
            highlights = highlights,
            bookmarks = bookmarks,
            sessions = sessions,
            ttsRules = rules
        )
        json.encodeToString(payload)
    }

    suspend fun exportBackupToUri(targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val content = createBackupJson()
            val out = context.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("No se pudo abrir el destino para guardar el respaldo")
            out.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        }
    }

    suspend fun restoreFromUri(sourceUri: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val rawJson = context.contentResolver.openInputStream(sourceUri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("No se pudo leer el archivo de respaldo")

            restoreFromJson(rawJson)
        }
    }

    suspend fun restoreFromJson(rawJson: String): RestoreSummary = withContext(Dispatchers.IO) {
        val backup = json.decodeFromString<WoodsFullBackup>(rawJson)
        val localBooks = bookDao.getAllBooks()
        val localByHash = localBooks.filter { it.fileHash.isNotBlank() && !it.fileHash.startsWith("pending-") }
            .associateBy { it.fileHash }
        val localById = localBooks.associateBy { it.id }

        // Mapa de ID antiguo (en el backup) -> ID real en el dispositivo actual
        val bookIdRemap = mutableMapOf<String, String>()
        var booksUpdatedCount = 0

        for (backupBook in backup.books) {
            val matchedLocal = localByHash[backupBook.fileHash] ?: localById[backupBook.id]
            if (matchedLocal != null) {
                bookIdRemap[backupBook.id] = matchedLocal.id
                val backupIsAhead = backupBook.readingProgressPercent >= matchedLocal.readingProgressPercent
                val merged = matchedLocal.copy(
                    readingProgressPercent = maxOf(matchedLocal.readingProgressPercent, backupBook.readingProgressPercent),
                    readingStatus = if (backupIsAhead) backupBook.readingStatus else matchedLocal.readingStatus,
                    currentLocatorJson = if (backupIsAhead) (backupBook.currentLocatorJson ?: matchedLocal.currentLocatorJson) else matchedLocal.currentLocatorJson,
                    fallbackLocatorHref = if (backupIsAhead) (backupBook.fallbackLocatorHref ?: matchedLocal.fallbackLocatorHref) else matchedLocal.fallbackLocatorHref,
                    fallbackLocatorPosition = if (backupIsAhead) (backupBook.fallbackLocatorPosition ?: matchedLocal.fallbackLocatorPosition) else matchedLocal.fallbackLocatorPosition,
                    totalReadingTimeMs = maxOf(matchedLocal.totalReadingTimeMs, backupBook.totalReadingTimeMs),
                    lastOpenedAt = maxOf(matchedLocal.lastOpenedAt ?: 0L, backupBook.lastOpenedAt ?: 0L).takeIf { it > 0L },
                    userRating = backupBook.userRating ?: matchedLocal.userRating,
                    userNotes = matchedLocal.userNotes.ifBlank { backupBook.userNotes },
                    collectionId = matchedLocal.collectionId ?: backupBook.collectionId
                )
                bookDao.upsert(merged)
                booksUpdatedCount++
            }
        }

        val validHighlightList = backup.highlights.mapNotNull { h ->
            val targetBookId = bookIdRemap[h.bookId] ?: return@mapNotNull null
            h.copy(bookId = targetBookId)
        }
        if (validHighlightList.isNotEmpty()) {
            highlightDao.upsertAll(validHighlightList)
        }

        val validBookmarkList = backup.bookmarks.mapNotNull { b ->
            val targetBookId = bookIdRemap[b.bookId] ?: return@mapNotNull null
            b.copy(bookId = targetBookId)
        }
        if (validBookmarkList.isNotEmpty()) {
            bookmarkDao.upsertAll(validBookmarkList)
        }

        val validSessions = backup.sessions.mapNotNull { s ->
            val targetBookId = bookIdRemap[s.bookId] ?: return@mapNotNull null
            s.copy(bookId = targetBookId)
        }
        if (validSessions.isNotEmpty()) {
            sessionDao.upsertAll(validSessions)
        }

        val existingRules = ttsRuleDao.getAllRulesFlow().first().map { it.pattern to it.bookId }.toSet()
        var rulesAdded = 0
        for (rule in backup.ttsRules) {
            val mappedBookId = rule.bookId?.let { bookIdRemap[it] }
            if ((rule.pattern to mappedBookId) !in existingRules) {
                ttsRuleDao.insert(
                    TtsRuleEntity(
                        pattern = rule.pattern,
                        replacement = rule.replacement,
                        isRegex = rule.isRegex,
                        isEnabled = rule.isEnabled,
                        bookId = mappedBookId
                    )
                )
                rulesAdded++
            }
        }

        RestoreSummary(
            booksRestored = booksUpdatedCount,
            highlightsRestored = validHighlightList.size,
            bookmarksRestored = validBookmarkList.size,
            rulesRestored = rulesAdded
        )
    }
}