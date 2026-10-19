package com.example.woodsreader.core.backup

import android.content.Context
import android.net.Uri
import com.example.woodsreader.core.tts.PhoneticRule
import com.example.woodsreader.core.tts.PronunciationLexiconManager
import com.example.woodsreader.core.tts.SoundscapeMode
import com.example.woodsreader.core.tts.StudioAcousticsProfile
import com.example.woodsreader.data.database.BookDao
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkDao
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightDao
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.database.ReadingStatsDao
import com.example.woodsreader.data.database.ReadingStatsEntity
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.ReadingStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BookProgressBackup(
    val id: String = "",
    val title: String,
    val fileHash: String,
    val filePath: String,
    val author: String = "",
    val readingProgressPercent: Float = 0f,
    val currentLocatorJson: String? = null,
    val fallbackLocatorHref: String? = null,
    val fallbackLocatorPosition: Int? = null,
    val lastOpenedAt: Long? = null,
    val totalReadingTimeMs: Long = 0L,
    val readingStatus: String = "NOT_STARTED",
    val userRating: Int? = null,
    val userNotes: String = "",
    val collectionId: String? = null,
    val perBookSettingsJson: String? = null
)

@Serializable
data class HighlightBackup(
    val id: String = "",
    val bookId: String,
    val bookTitle: String = "",
    val bookFileHash: String = "",
    val locatorJson: String,
    val highlightedText: String,
    val color: String = "YELLOW",
    val note: String = "",
    val chapterTitle: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class BookmarkBackup(
    val id: String = "",
    val bookId: String,
    val bookTitle: String = "",
    val bookFileHash: String = "",
    val locatorJson: String,
    val label: String = "",
    val isAutomatic: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class PhoneticRuleBackup(
    val id: String = "",
    val originalWord: String,
    val phoneticReplacement: String,
    val caseSensitive: Boolean = false
)

@Serializable
data class StudioPreferencesBackup(
    val acousticsProfile: String = StudioAcousticsProfile.STUDIO_WARM.id,
    val expressiveProsodyEnabled: Boolean = true,
    val dramatizedDialogueEnabled: Boolean = true,
    val secondaryDialogueVoiceId: String? = null,
    val soundscapeMode: String = SoundscapeMode.OFF.id,
    val soundscapeIntensity: Float = 0.35f
)

@Serializable
data class ReadingStatsBackup(
    val date: String,
    val secondsRead: Long = 0L,
    val pagesRead: Int = 0,
    val booksActive: Int = 0
)

@Serializable
data class ReadingSessionBackup(
    val id: String = "",
    val bookId: String,
    val startAt: Long,
    val endAt: Long? = null,
    val startLocatorJson: String,
    val endLocatorJson: String? = null,
    val mode: String = "MANUAL",
    val wordsRead: Long = 0L
)

@Serializable
data class WoodsReaderBackupVault(
    val formatVersion: Int = 2,
    val app: String = "Woods Reader",
    val exportedAt: Long = System.currentTimeMillis(),
    val exportDateFormatted: String = "",
    val books: List<BookProgressBackup> = emptyList(),
    val highlights: List<HighlightBackup> = emptyList(),
    val bookmarks: List<BookmarkBackup> = emptyList(),
    val phoneticRules: List<PhoneticRuleBackup> = emptyList(),
    val builtInLexiconEnabled: Boolean = true,
    val studioPreferences: StudioPreferencesBackup? = null,
    val readingStats: List<ReadingStatsBackup> = emptyList(),
    val readingSessions: List<ReadingSessionBackup> = emptyList(),
    val userPreferences: UserPreferences? = null
)

@Singleton
class WoodsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val readingSessionDao: ReadingSessionDao,
    private val readingStatsDao: ReadingStatsDao,
    private val pronunciationLexiconManager: PronunciationLexiconManager,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Empaqueta el progreso de lectura, subrayados, marcadores, reglas fonéticas,
     * preferencias acústicas de estudio y estadísticas en un archivo JSON en cacheDir.
     */
    suspend fun createBackupFile(): File = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = Date()
        val dateStamp = dateFormat.format(now)

        val localBooks = bookDao.getAllBooks()
        val booksMap = localBooks.associateBy { it.id }

        val booksBackup = localBooks.map { b ->
            BookProgressBackup(
                id = b.id,
                title = b.title,
                fileHash = b.fileHash,
                filePath = b.filePath,
                author = b.author,
                readingProgressPercent = b.readingProgressPercent,
                currentLocatorJson = b.currentLocatorJson,
                fallbackLocatorHref = b.fallbackLocatorHref,
                fallbackLocatorPosition = b.fallbackLocatorPosition,
                lastOpenedAt = b.lastOpenedAt,
                totalReadingTimeMs = b.totalReadingTimeMs,
                readingStatus = b.readingStatus.name,
                userRating = b.userRating,
                userNotes = b.userNotes,
                collectionId = b.collectionId,
                perBookSettingsJson = b.perBookSettingsJson
            )
        }

        val highlightsBackup = highlightDao.getAllHighlights().map { h ->
            val book = booksMap[h.bookId]
            HighlightBackup(
                id = h.id,
                bookId = h.bookId,
                bookTitle = book?.title ?: "",
                bookFileHash = book?.fileHash ?: "",
                locatorJson = h.locatorJson,
                highlightedText = h.highlightedText,
                color = h.color.name,
                note = h.note,
                chapterTitle = h.chapterTitle,
                createdAt = h.createdAt
            )
        }

        val bookmarksBackup = bookmarkDao.getAllBookmarks().map { b ->
            val book = booksMap[b.bookId]
            BookmarkBackup(
                id = b.id,
                bookId = b.bookId,
                bookTitle = book?.title ?: "",
                bookFileHash = book?.fileHash ?: "",
                locatorJson = b.locatorJson,
                label = b.label,
                isAutomatic = b.isAutomatic,
                createdAt = b.createdAt
            )
        }

        val customRulesBackup = pronunciationLexiconManager.getAllCustomRules().map { r ->
            PhoneticRuleBackup(
                id = r.id,
                originalWord = r.originalWord,
                phoneticReplacement = r.phoneticReplacement,
                caseSensitive = r.caseSensitive
            )
        }

        val studioPrefs = context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE)
        val studioBackup = StudioPreferencesBackup(
            acousticsProfile = studioPrefs.getString("acoustics_profile", StudioAcousticsProfile.STUDIO_WARM.id)
                ?: StudioAcousticsProfile.STUDIO_WARM.id,
            expressiveProsodyEnabled = studioPrefs.getBoolean("expressive_prosody_enabled", true),
            dramatizedDialogueEnabled = studioPrefs.getBoolean("dramatized_dialogue_enabled", true),
            secondaryDialogueVoiceId = studioPrefs.getString("secondary_dialogue_voice_id", null),
            soundscapeMode = studioPrefs.getString("soundscape_mode", SoundscapeMode.OFF.id)
                ?: SoundscapeMode.OFF.id,
            soundscapeIntensity = studioPrefs.getFloat("soundscape_intensity", 0.35f)
        )

        val statsBackup = readingStatsDao.getAllStatsList().map { s ->
            ReadingStatsBackup(
                date = s.date,
                secondsRead = s.secondsRead,
                pagesRead = s.pagesRead,
                booksActive = s.booksActive
            )
        }

        val sessionsBackup = readingSessionDao.getAllSessions().map { s ->
            ReadingSessionBackup(
                id = s.id,
                bookId = s.bookId,
                startAt = s.startAt,
                endAt = s.endAt,
                startLocatorJson = s.startLocatorJson,
                endLocatorJson = s.endLocatorJson,
                mode = s.mode.name,
                wordsRead = s.wordsRead
            )
        }

        val userPrefs = runCatching { preferencesRepository.userPreferencesFlow.first() }.getOrNull()

        val vault = WoodsReaderBackupVault(
            formatVersion = 2,
            app = "Woods Reader",
            exportedAt = now.time,
            exportDateFormatted = fullDateFormat.format(now),
            books = booksBackup,
            highlights = highlightsBackup,
            bookmarks = bookmarksBackup,
            phoneticRules = customRulesBackup,
            builtInLexiconEnabled = pronunciationLexiconManager.builtInEnabled.value,
            studioPreferences = studioBackup,
            readingStats = statsBackup,
            readingSessions = sessionsBackup,
            userPreferences = userPrefs
        )

        val jsonString = json.encodeToString(vault)
        val fileName = "woods_reader_backup_$dateStamp.json"
        val targetFile = File(context.cacheDir, fileName)
        targetFile.writeText(jsonString, Charsets.UTF_8)
        targetFile
    }

    /**
     * Exporta el contenido del respaldo directamente a un Uri SAF (ACTION_CREATE_DOCUMENT).
     */
    suspend fun exportBackupToUri(targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val backupFile = createBackupFile()
            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("No se pudo abrir el destino para escribir el respaldo")
            outputStream.use { out ->
                backupFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }
        }
    }

    /**
     * Restaura desde un Uri de respaldo, emparejando libros por title, fileHash y filePath.
     * Retorna el número total de registros restaurados.
     */
    suspend fun restoreFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No se pudo abrir el archivo de respaldo seleccionado")
            val rawJson = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

            if (rawJson.isBlank()) {
                throw IllegalStateException("El archivo de respaldo está vacío")
            }

            restoreFromJson(rawJson)
        }
    }

    /**
     * Deserializa y restaura el JSON asegurando compatibilidad hacia atrás.
     */
    suspend fun restoreFromJson(rawJson: String): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0

        val localBooks = bookDao.getAllBooks()
        val localByHash = localBooks.filter { it.fileHash.isNotBlank() && !it.fileHash.startsWith("pending-") }
            .associateBy { it.fileHash }
        val localByPath = localBooks.associateBy { it.filePath }
        val localByTitle = localBooks.associateBy { it.title.trim().lowercase(Locale.ROOT) }
        val localById = localBooks.associateBy { it.id }

        val bookIdRemap = mutableMapOf<String, String>()

        // Intentar parsear como WoodsReaderBackupVault (Formato actual)
        val vaultResult = runCatching { json.decodeFromString<WoodsReaderBackupVault>(rawJson) }

        if (vaultResult.isSuccess) {
            val vault = vaultResult.getOrThrow()

            // 1. Restaurar progreso de libros emparejados
            for (backupBook in vault.books) {
                val matched = localByHash[backupBook.fileHash]
                    ?: localByPath[backupBook.filePath]
                    ?: localByTitle[backupBook.title.trim().lowercase(Locale.ROOT)]
                    ?: localById[backupBook.id]

                if (matched != null) {
                    bookIdRemap[backupBook.id] = matched.id
                    val statusParsed = runCatching { ReadingStatus.valueOf(backupBook.readingStatus) }
                        .getOrDefault(matched.readingStatus)
                    val backupIsAhead = backupBook.readingProgressPercent >= matched.readingProgressPercent

                    val merged = matched.copy(
                        readingProgressPercent = maxOf(matched.readingProgressPercent, backupBook.readingProgressPercent),
                        readingStatus = if (backupIsAhead) statusParsed else matched.readingStatus,
                        currentLocatorJson = if (backupIsAhead) (backupBook.currentLocatorJson ?: matched.currentLocatorJson) else matched.currentLocatorJson,
                        fallbackLocatorHref = if (backupIsAhead) (backupBook.fallbackLocatorHref ?: matched.fallbackLocatorHref) else matched.fallbackLocatorHref,
                        fallbackLocatorPosition = if (backupIsAhead) (backupBook.fallbackLocatorPosition ?: matched.fallbackLocatorPosition) else matched.fallbackLocatorPosition,
                        totalReadingTimeMs = maxOf(matched.totalReadingTimeMs, backupBook.totalReadingTimeMs),
                        lastOpenedAt = maxOf(matched.lastOpenedAt ?: 0L, backupBook.lastOpenedAt ?: 0L).takeIf { it > 0L },
                        userRating = backupBook.userRating ?: matched.userRating,
                        userNotes = matched.userNotes.ifBlank { backupBook.userNotes },
                        collectionId = matched.collectionId ?: backupBook.collectionId
                    )
                    bookDao.upsert(merged)
                    restoredCount++
                }
            }

            // 2. Restaurar Subrayados
            val highlightsToRestore = vault.highlights.mapNotNull { h ->
                val targetBookId = bookIdRemap[h.bookId]
                    ?: localByHash[h.bookFileHash]?.id
                    ?: localByTitle[h.bookTitle.trim().lowercase(Locale.ROOT)]?.id
                    ?: return@mapNotNull null

                val highlightColor = runCatching { HighlightColor.valueOf(h.color) }
                    .getOrDefault(HighlightColor.YELLOW)

                HighlightEntity(
                    id = if (h.id.isNotBlank()) h.id else UUID.randomUUID().toString(),
                    bookId = targetBookId,
                    locatorJson = h.locatorJson,
                    highlightedText = h.highlightedText,
                    color = highlightColor,
                    note = h.note,
                    chapterTitle = h.chapterTitle,
                    createdAt = h.createdAt
                )
            }
            if (highlightsToRestore.isNotEmpty()) {
                highlightDao.upsertAll(highlightsToRestore)
                restoredCount += highlightsToRestore.size
            }

            // 3. Restaurar Marcadores
            val bookmarksToRestore = vault.bookmarks.mapNotNull { b ->
                val targetBookId = bookIdRemap[b.bookId]
                    ?: localByHash[b.bookFileHash]?.id
                    ?: localByTitle[b.bookTitle.trim().lowercase(Locale.ROOT)]?.id
                    ?: return@mapNotNull null

                BookmarkEntity(
                    id = if (b.id.isNotBlank()) b.id else UUID.randomUUID().toString(),
                    bookId = targetBookId,
                    locatorJson = b.locatorJson,
                    label = b.label,
                    isAutomatic = b.isAutomatic,
                    createdAt = b.createdAt
                )
            }
            if (bookmarksToRestore.isNotEmpty()) {
                bookmarkDao.upsertAll(bookmarksToRestore)
                restoredCount += bookmarksToRestore.size
            }

            // 4. Restaurar Reglas Fonéticas del Glosario
            if (vault.phoneticRules.isNotEmpty()) {
                val currentRulesMap = pronunciationLexiconManager.getAllCustomRules()
                    .associateBy { it.originalWord.lowercase(Locale.ROOT) }
                    .toMutableMap()

                var addedRules = 0
                for (rule in vault.phoneticRules) {
                    val key = rule.originalWord.lowercase(Locale.ROOT)
                    currentRulesMap[key] = PhoneticRule(
                        id = if (rule.id.isNotBlank()) rule.id else System.currentTimeMillis().toString(),
                        originalWord = rule.originalWord,
                        phoneticReplacement = rule.phoneticReplacement,
                        caseSensitive = rule.caseSensitive,
                        isBuiltIn = false
                    )
                    addedRules++
                }
                pronunciationLexiconManager.setAllCustomRules(currentRulesMap.values.toList())
                restoredCount += addedRules
            }
            pronunciationLexiconManager.setBuiltInEnabled(vault.builtInLexiconEnabled)

            // 5. Restaurar Preferencias de Estudio
            vault.studioPreferences?.let { sp ->
                val studioPrefs = context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE)
                val ambientPrefs = context.getSharedPreferences("woods_ambient_soundscape_prefs", Context.MODE_PRIVATE)

                studioPrefs.edit()
                    .putString("acoustics_profile", sp.acousticsProfile)
                    .putBoolean("expressive_prosody_enabled", sp.expressiveProsodyEnabled)
                    .putBoolean("dramatized_dialogue_enabled", sp.dramatizedDialogueEnabled)
                    .putString("secondary_dialogue_voice_id", sp.secondaryDialogueVoiceId)
                    .putString("soundscape_mode", sp.soundscapeMode)
                    .putFloat("soundscape_intensity", sp.soundscapeIntensity)
                    .apply()

                ambientPrefs.edit()
                    .putString("ambient_mode", sp.soundscapeMode)
                    .putFloat("ambient_volume", sp.soundscapeIntensity)
                    .apply()

                restoredCount++
            }

            // 6. Restaurar Estadísticas de Lectura
            if (vault.readingStats.isNotEmpty()) {
                val statsEntities = vault.readingStats.map { s ->
                    ReadingStatsEntity(
                        date = s.date,
                        secondsRead = s.secondsRead,
                        pagesRead = s.pagesRead,
                        booksActive = s.booksActive
                    )
                }
                readingStatsDao.upsertAll(statsEntities)
                restoredCount += statsEntities.size
            }

            // 7. Restaurar Sesiones de Lectura si están presentes
            if (vault.readingSessions.isNotEmpty()) {
                val sessionsToRestore = vault.readingSessions.mapNotNull { s ->
                    val targetBookId = bookIdRemap[s.bookId] ?: return@mapNotNull null
                    val modeParsed = runCatching { com.example.woodsreader.data.model.ReadingMode.valueOf(s.mode) }
                        .getOrDefault(com.example.woodsreader.data.model.ReadingMode.MANUAL)
                    ReadingSessionEntity(
                        id = if (s.id.isNotBlank()) s.id else UUID.randomUUID().toString(),
                        bookId = targetBookId,
                        startAt = s.startAt,
                        endAt = s.endAt,
                        startLocatorJson = s.startLocatorJson,
                        endLocatorJson = s.endLocatorJson,
                        mode = modeParsed,
                        wordsRead = s.wordsRead
                    )
                }
                if (sessionsToRestore.isNotEmpty()) {
                    readingSessionDao.upsertAll(sessionsToRestore)
                    restoredCount += sessionsToRestore.size
                }
            }

            // 8. Restaurar Preferencias Generales del Usuario
            vault.userPreferences?.let { up ->
                preferencesRepository.restorePreferences(up)
                restoredCount++
            }

            return@withContext restoredCount
        }

        // Fallback: Soporte para formato de respaldo de emergencia anterior (LibraryBackup o WoodsFullBackup)
        val legacyVaultResult = runCatching {
            val obj = org.json.JSONObject(rawJson)
            if (obj.has("books")) {
                val booksArray = obj.getJSONArray("books")
                for (i in 0 until booksArray.length()) {
                    val b = booksArray.getJSONObject(i)
                    val bHash = b.optString("fileHash", "")
                    val bPath = b.optString("filePath", "")
                    val bTitle = b.optString("title", "").trim().lowercase(Locale.ROOT)
                    val bId = b.optString("id", "")

                    val matched = localByHash[bHash]
                        ?: localByPath[bPath]
                        ?: localByTitle[bTitle]
                        ?: localById[bId]

                    if (matched != null) {
                        bookIdRemap[bId] = matched.id
                        val progress = b.optDouble("readingProgressPercent", matched.readingProgressPercent.toDouble()).toFloat()
                        val locator = if (b.has("currentLocatorJson") && !b.isNull("currentLocatorJson")) {
                            b.getString("currentLocatorJson")
                        } else {
                            matched.currentLocatorJson
                        }
                        val merged = matched.copy(
                            readingProgressPercent = maxOf(matched.readingProgressPercent, progress),
                            currentLocatorJson = if (progress >= matched.readingProgressPercent) locator else matched.currentLocatorJson
                        )
                        bookDao.upsert(merged)
                        restoredCount++
                    }
                }
            }
            restoredCount
        }

        legacyVaultResult.getOrDefault(restoredCount)
    }
}
