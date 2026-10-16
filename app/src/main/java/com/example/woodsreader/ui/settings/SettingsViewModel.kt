package com.example.woodsreader.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.woodsreader.data.database.BookDao
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkDao
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightDao
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionDao
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.service.BookScannerService
import com.example.woodsreader.core.managers.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.TtsManager
import java.io.File
import javax.inject.Inject

@Serializable
data class LibraryBackup(
    val books: List<BookEntity>,
    val highlights: List<HighlightEntity>,
    val bookmarks: List<BookmarkEntity>,
    val sessions: List<ReadingSessionEntity>
)

data class StorageSizes(
    val databaseBytes: Long = 0L,
    val coversBytes: Long = 0L,
    val espeakBytes: Long = 0L,
    val sherpaBytes: Long = 0L
) {
    val totalBytes: Long get() = databaseBytes + coversBytes + espeakBytes + sherpaBytes
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val scannerService: BookScannerService,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val readingSessionDao: ReadingSessionDao,
    private val sherpaModelManager: SherpaModelManager,
    private val ttsManager: TtsManager,
    private val backupManager: BackupManager
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val isScanning   = scannerService.isScanning
    val isEnriching  = scannerService.isEnriching
    val scanProgress = scannerService.scanProgress

    private val _storageSizes = MutableStateFlow(StorageSizes())
    val storageSizes: StateFlow<StorageSizes> = _storageSizes.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage = _backupStatusMessage.asStateFlow()

    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    fun exportFullBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _backupStatusMessage.value = "Exportando copia de seguridad..."
            backupManager.exportBackupToUri(uri)
                .onSuccess {
                    _backupStatusMessage.value = "Respaldo guardado correctamente (.woodsbackup)"
                }
                .onFailure { err ->
                    _backupStatusMessage.value =
                        "Error al exportar: ${err.localizedMessage ?: "desconocido"}"
                }
        }
    }

    fun restoreFullBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _backupStatusMessage.value = "Restaurando biblioteca..."
            backupManager.restoreFromUri(uri)
                .onSuccess { summary ->
                    _backupStatusMessage.value =
                        "Restaurados: ${summary.booksRestored} libros, ${summary.highlightsRestored} citas, " +
                            "${summary.bookmarksRestored} marcadores y ${summary.rulesRestored} reglas."
                }
                .onFailure { err ->
                    _backupStatusMessage.value =
                        "Error al restaurar: ${err.localizedMessage ?: "archivo inválido"}"
                }
        }
    }

    // ── Toggle and Slider handlers ───────────────────────────────────────────

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNotificationsEnabled(enabled) }
    }

    fun setTtsAutoStart(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateTtsAutoStart(enabled) }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAnalyticsEnabled(enabled) }
    }

    fun setTtsSpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.updateTtsSpeed(speed) }
    }

    fun setTtsPitch(pitch: Float) {
        viewModelScope.launch { preferencesRepository.updateTtsPitch(pitch) }
    }

    val sherpaDownloadState = sherpaModelManager.downloadState
    private val _showSherpaDownloadDialog = MutableStateFlow(false)
    val showSherpaDownloadDialog = _showSherpaDownloadDialog.asStateFlow()

    fun dismissSherpaDownloadDialog() {
        _showSherpaDownloadDialog.value = false
    }

    fun setUseSherpaTts(use: Boolean) {
        if (!use) {
            viewModelScope.launch { preferencesRepository.updateUseSherpaTts(false) }
        } else {
            if (sherpaModelManager.isModelReady()) {
                viewModelScope.launch {
                    preferencesRepository.updateUseSherpaTts(true)
                    ttsManager.initSherpaIfNeeded()
                }
            } else {
                _showSherpaDownloadDialog.value = true
            }
        }
    }

    fun downloadSherpaModel() {
        _showSherpaDownloadDialog.value = false
        viewModelScope.launch {
            val result = sherpaModelManager.downloadAndExtractModel()
            if (result.isSuccess) {
                preferencesRepository.updateUseSherpaTts(true)
                ttsManager.initSherpaIfNeeded()
            }
        }
    }

    fun deleteSherpaModel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ttsManager.resetSherpa()
            sherpaModelManager.deleteModel()
            preferencesRepository.updateUseSherpaTts(false)
            calculateStorageSizes(context)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            preferencesRepository.resetOnboarding()
        }
    }

    // ── Library Scan ─────────────────────────────────────────────────────────

    fun startScan() {
        viewModelScope.launch {
            val paths = preferences.value.customScanPaths
            scannerService.scan(paths)
        }
    }

    // ── Storage calculations ─────────────────────────────────────────────────

    fun calculateStorageSizes(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("lector_epub.db")
            val dbSize = if (dbFile.exists()) dbFile.length() else 0L

            val permanentCovers = getFolderSize(File(context.filesDir, "covers"))
            val legacyCovers = getFolderSize(File(context.cacheDir, "covers"))
            val coversSize = permanentCovers + legacyCovers

            val espeakDir = File(context.filesDir, "espeak-ng-data")
            val espeakSize = getFolderSize(espeakDir)

            val sherpaSize = sherpaModelManager.getModelSizeBytes()

            _storageSizes.value = StorageSizes(dbSize, coversSize, espeakSize, sherpaSize)
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach {
            size += getFolderSize(it)
        }
        return size
    }

    fun clearCoverCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val permanentCoversDir = File(context.filesDir, "covers")
            if (permanentCoversDir.exists()) {
                permanentCoversDir.listFiles()?.forEach { it.delete() }
            }
            val legacyCoversDir = File(context.cacheDir, "covers")
            if (legacyCoversDir.exists()) {
                legacyCoversDir.listFiles()?.forEach { it.delete() }
            }
            // Sincronizar Room DB para que los libros reflejen que necesitan re-enriquecerse
            bookDao.clearAllCovers()

            calculateStorageSizes(context)
        }
    }

    // ── Export / Import Library ──────────────────────────────────────────────

    fun exportLibrary(context: Context) {
        viewModelScope.launch {
            try {
                val books = bookDao.getAllBooks()
                val highlights = highlightDao.getAllHighlights()
                val bookmarks = bookmarkDao.getAllBookmarks()
                val sessions = readingSessionDao.getAllSessions()

                val backup = LibraryBackup(books, highlights, bookmarks, sessions)
                val jsonStr = Json.encodeToString(backup)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Backup de Woods Reader")
                    putExtra(Intent.EXTRA_TEXT, jsonStr)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(intent, "Exportar Biblioteca"))
            } catch (_: Exception) {}
        }
    }

    fun importLibrary(jsonStr: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val backup = Json.decodeFromString<LibraryBackup>(jsonStr)

                // Import into database
                bookDao.upsertAll(backup.books)
                highlightDao.upsertAll(backup.highlights)
                bookmarkDao.upsertAll(backup.bookmarks)
                readingSessionDao.upsertAll(backup.sessions)

                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al descodificar backup")
            }
        }
    }
}
