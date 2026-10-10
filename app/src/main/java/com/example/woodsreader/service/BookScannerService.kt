package com.example.woodsreader.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.data.scanner.FileSystemScanner
import com.example.woodsreader.data.scanner.MediaStoreScanner
import com.example.woodsreader.data.model.BookScanResult
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookScannerService"

/**
 * Book discovery engine with three cooperative scan phases:
 *
 * ## Phase 1: Quick Scan
 * Discovers new books using MediaStore (fast, indexed) + DeepFileSystemScanner (thorough).
 * - **De-duplicates** between both scanners using a shared `seenPaths` set
 * - **Incremental**: skips hash computation if the file path already exists in Room
 * - Validates file extension + magic bytes header
 * - Inserts a lightweight [BookEntity] with basic info (title from filename, format)
 * - Debounced automatic triggers: waits 5s of inactivity before firing
 *
 * ## Phase 2: Deep Enrich
 * Processes books still missing enriched metadata (no cover, author = "Unknown Author").
 * - Opens each book with Readium's PublicationOpener (heavy I/O)
 * - Extracts real title, author, cover image, language, description
 * - Concurrency limited to [ENRICH_CONCURRENCY] parallel operations
 *
 * ## Phase 3: Orphan Cleanup
 * Detects books whose files were deleted from the filesystem and removes them from Room.
 *
 * ## Adding a new format
 * 1. Add the extension to [SUPPORTED_EXTENSIONS]
 * 2. Add the magic bytes check in [File.isBookFile]
 * 3. All scan modes automatically pick up the new format
 */
@Singleton
class BookScannerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val metadataExtractor: Lazy<BookMetadataExtractor>,
    private val mediaStoreScanner: MediaStoreScanner,
    private val deepScanner: FileSystemScanner
) {
    // â”€â”€ Coroutine scope â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // â”€â”€ State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private val _isScanning   = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isEnriching  = MutableStateFlow(false)
    val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    // â”€â”€ Internal state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Limits concurrent Readium PublicationOpener calls to avoid OOM. */
    private val enrichSemaphore = Semaphore(ENRICH_CONCURRENCY)

    /** De-duplicates files currently being processed (thread-safe). */
    private val processingFiles = ConcurrentHashMap.newKeySet<String>()

    private val observers = mutableListOf<RecursiveFileObserver>()
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null

    /** Active scan job â€” can be cancelled by [cancelScan]. */
    private var activeScanJob: Job? = null

    // â”€â”€ Supported formats (extension â†’ magic bytes map) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    companion object {
        /** Max concurrent Readium opens during deep enrich. Keep low to avoid OOM. */
        private const val ENRICH_CONCURRENCY = 2

        /** Debounce delay for automatic re-scans triggered by file system changes. */
        private const val DEBOUNCE_MS = 5_000L

        /** File size bounds: 1KB minimum, 800MB maximum. */
        private val SIZE_RANGE = 1_024L..(800L * 1_024L * 1_024L)

        val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "cbz", "cbr", "cbt", "txt", "md", "mobi", "azw", "azw3", "fb2")

        /** Default paths to scan if no custom paths are configured. */
        val DEFAULT_SCAN_PATHS by lazy {
            listOf(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS).absolutePath,
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath,
                "/storage/emulated/0/Books",
                "/storage/emulated/0/Readium"
            ).distinct()
        }

        val DIRECTORY_BLACKLIST = listOf(
            "/android/data", "/android/media", "/android/obb",
            "/.thumbnails", "/dcim", "/pictures", "/movies",
            "/music", "/alarms", "/notifications", "/podcasts", "/ringtones"
        )
    }

    data class ScanStats(val scannedCount: Int, val newBooksCount: Int, val orphansRemoved: Int = 0)

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PUBLIC API
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Runs a full scan pipeline: Quick Scan â†’ Deep Enrich â†’ Orphan Cleanup.
     * The quick scan completes first and makes books visible immediately;
     * the deep enrich and cleanup run in the background.
     */
    fun scan(paths: List<String> = DEFAULT_SCAN_PATHS) {
        // Cancel any active scan before starting a new one
        activeScanJob?.cancel()
        activeScanJob = scope.launch {
            _isScanning.value = true
            try {
                // Phase 1: Quick Scan
                val stats = quickScan(paths)
                Log.d(TAG, "Quick scan done. Found=${stats.scannedCount} New=${stats.newBooksCount}")
                _scanProgress.value = "✓ Escaneo rápido: ${stats.scannedCount} analizados, ${stats.newBooksCount} nuevos"
            } finally {
                _isScanning.value = false
                delay(2_000)
                _scanProgress.value = ""
            }

            // Phase 2: Deep enrich (non-blocking for UI)
            if (isActive) deepEnrich()

            // Phase 3: Orphan cleanup
            if (isActive) cleanupOrphanedBooks()
        }
    }

    /** Cancels the current active scan. */
    fun cancelScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        _isScanning.value = false
        _isEnriching.value = false
        _scanProgress.value = ""
        Log.d(TAG, "Scan cancelled by user")
    }

    /**
     * Quick Scan â€” discovers new books using extension + magic bytes.
     * **Incremental**: skips hash computation for files already registered by path.
     * **De-duplicated**: MediaStore results are tracked to avoid reprocessing in DeepScanner.
     * Suspendable for use from WorkManager.
     */
    suspend fun quickScan(paths: List<String> = DEFAULT_SCAN_PATHS): ScanStats {
        var foundCount = 0
        var newCount   = 0

        // ── Pre-load all known paths into memory (1 single DB query) ───────────
        val knownPaths = repository.getAllKnownPaths()
        Log.d(TAG, "QuickScan: ${knownPaths.size} books already known")

        // Shared set to de-duplicate between MediaStore and DeepScanner
        val seenPaths = ConcurrentHashMap.newKeySet<String>()

        // Batch buffer for new books
        val newBooksBatch = mutableListOf<BookEntity>()

        // ── Phase 1/3: MediaStore (fastest — uses Android's indexed DB) ────────
        Log.d(TAG, "QuickScan [1/3]: MediaStore...")
        _scanProgress.value = "Fase 1/3 · MediaStore…"
        mediaStoreScanner.scanDevice().collect { result ->
            val path = if (result.uri.scheme == "file") {
                result.uri.path ?: result.uri.toString()
            } else {
                result.uri.toString()
            }
            
            if (!seenPaths.add(path)) return@collect
            foundCount++

            if (path !in knownPaths) {
                val entity = createLightweightEntity(path, result.displayName, result.sizeBytes, result.dateModified)
                newBooksBatch.add(entity)
                newCount++

                // Flush batch every 100 books
                if (newBooksBatch.size >= 100) {
                    repository.upsertBooks(newBooksBatch.toList())
                    newBooksBatch.clear()
                }
            }

            if (foundCount % 200 == 0) {
                _scanProgress.value = "Fase 1/3 · Buscando… $foundCount archivos ($newCount nuevos)"
            }
        }

        // ── Phase 2/3: Deep filesystem scan (only files NOT seen by MediaStore) ─
        Log.d(TAG, "QuickScan [2/3]: FileSystem...")
        _scanProgress.value = "Fase 2/3 · Explorando carpetas…"
        deepScanner.scanAllDevice().collect { file ->
            if (seenPaths.add(file.absolutePath) && file.absolutePath !in knownPaths) {
                val entity = createLightweightEntity(
                    file.absolutePath, file.name, file.length(), file.lastModified()
                )
                newBooksBatch.add(entity)
                newCount++

                if (newBooksBatch.size >= 100) {
                    repository.upsertBooks(newBooksBatch.toList())
                    newBooksBatch.clear()
                }
            }
            foundCount++
            if (foundCount % 200 == 0) {
                _scanProgress.value = "Fase 2/3 · Explorando… $foundCount archivos ($newCount nuevos)"
            }
        }

        // Flush remaining batch
        if (newBooksBatch.isNotEmpty()) {
            repository.upsertBooks(newBooksBatch.toList())
            newBooksBatch.clear()
        }

        // ── Phase 3/3: SAF custom paths ────────────────────────────────────────
        val safPaths = paths.filter { it.startsWith("content://") }
        if (safPaths.isNotEmpty()) {
            Log.d(TAG, "QuickScan [3/3]: ${safPaths.size} SAF paths...")
            _scanProgress.value = "Fase 3/3 · Carpetas personalizadas…"
            safPaths.forEach { path -> 
                val safStats = quickScanSafPath(path, knownPaths) 
                newCount += safStats.newBooksCount
                foundCount += safStats.scannedCount
            }
        }

        return ScanStats(foundCount, newCount)
    }

    /**
     * Deep Enrich — opens each unenriched book with Readium to extract full metadata.
     * Only processes books where author is still "Unknown Author" or cover is missing.
     * Loops in batches to handle large libraries completely.
     * Suspendable for use from WorkManager.
     */
    suspend fun deepEnrich() {
        if (_isEnriching.value) return
        _isEnriching.value = true
        val processedBookIds = HashSet<String>()
        var totalProcessed = 0

        try {
            while (true) {
                val unenriched = repository.getUnenrichedBooks(50).filter { it.id !in processedBookIds }
                if (unenriched.isEmpty()) {
                    Log.d(TAG, "DeepEnrich: all eligible books processed (total=$totalProcessed)")
                    break
                }

                Log.d(TAG, "DeepEnrich batch: ${unenriched.size} books to process")

                for (book in unenriched) {
                    processedBookIds.add(book.id)
                    totalProcessed++
                    _scanProgress.value = "Enriqueciendo metadatos ($totalProcessed)…"

                    try {
                        val file = if (book.filePath.startsWith("/")) File(book.filePath) else null
                        val uri = file?.let { Uri.fromFile(it) } ?: Uri.parse(book.filePath)

                        // Verify the file still exists before opening
                        if (file != null && !file.exists()) {
                            Log.w(TAG, "DeepEnrich: file missing for ${book.title}, skipping")
                            continue
                        }

                        // Compute hash if missing (books from fast scan)
                        val hash = if (book.fileHash.isEmpty() || book.fileHash.startsWith("pending-")) {
                            try { computePartialHash(uri) } catch (_: Exception) { "" }
                        } else {
                            book.fileHash
                        }

                        val enriched = enrichSemaphore.withPermit {
                            metadataExtractor.get().extractMetadata(
                                uri,
                                file?.name?.ifBlank { book.title } ?: book.title,
                                book.fileSizeBytes,
                                hash
                            )
                        } ?: continue

                        // Check if existing cover file on disk was missing
                        val existingCoverMissing = book.coverCachePath?.let { !File(it).exists() } ?: true
                        val resolvedCover = enriched.coverCachePath ?: if (existingCoverMissing) null else book.coverCachePath

                        // Update only fields that improved over fallback
                        val updated = book.copy(
                            title = if (enriched.title.isNotBlank()) enriched.title else book.title,
                            author = if (enriched.author.isNotBlank() && enriched.author != "Unknown Author") enriched.author else book.author,
                            description = enriched.description.ifBlank { book.description },
                            language = enriched.language.ifBlank { book.language },
                            coverCachePath = resolvedCover,
                            fileHash = if (hash.isNotEmpty()) hash else book.fileHash
                        )

                        if (updated != book) {
                            repository.upsertBook(updated)
                            Log.d(TAG, "DeepEnrich: enriched ${book.title} → author=${updated.author}, cover=${updated.coverCachePath != null}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DeepEnrich: error processing ${book.title}", e)
                    }
                }
            }
        } finally {
            _isEnriching.value = false
            _scanProgress.value = ""
        }
    }

    /**
     * Non-blocking self-healing pass for books missing covers.
     * Scans for books where coverCachePath is null or the file on disk was deleted.
     */
    suspend fun enrichMissingCovers() {
        if (_isEnriching.value || _isScanning.value) return
        _isEnriching.value = true
        Log.d(TAG, "enrichMissingCovers: checking for books with missing covers")

        try {
            val missing = repository.getBooksMissingCover(60)
            if (missing.isEmpty()) {
                Log.d(TAG, "enrichMissingCovers: no books missing covers")
                return
            }

            for (book in missing) {
                try {
                    val file = if (book.filePath.startsWith("/")) File(book.filePath) else null
                    val uri = file?.let { Uri.fromFile(it) } ?: Uri.parse(book.filePath)
                    if (file != null && !file.exists()) continue

                    val hash = if (book.fileHash.isNotBlank() && !book.fileHash.startsWith("pending-")) {
                        book.fileHash
                    } else {
                        try { computePartialHash(uri) } catch (_: Exception) { "" }
                    }

                    val enriched = enrichSemaphore.withPermit {
                        metadataExtractor.get().extractMetadata(
                            uri,
                            file?.name?.ifBlank { book.title } ?: book.title,
                            book.fileSizeBytes,
                            hash
                        )
                    }

                    if (enriched?.coverCachePath != null) {
                        repository.updateCoverPath(book.id, enriched.coverCachePath)
                        Log.d(TAG, "enrichMissingCovers: restored cover for ${book.title}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "enrichMissingCovers: failed for ${book.title}: ${e.message}")
                }
            }
        } finally {
            _isEnriching.value = false
        }
    }

    /**
     * Detects books whose source files were deleted from the filesystem
     * and removes them from Room to keep the library clean.
     */
    suspend fun cleanupOrphanedBooks() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            Log.w(TAG, "OrphanCleanup: MANAGE_EXTERNAL_STORAGE not granted, skipping orphan cleanup to protect books")
            return
        }

        val storageState = android.os.Environment.getExternalStorageState()
        if (storageState != android.os.Environment.MEDIA_MOUNTED && storageState != android.os.Environment.MEDIA_MOUNTED_READ_ONLY) {
            Log.w(TAG, "OrphanCleanup: External storage not mounted ($storageState), skipping cleanup to protect books")
            return
        }

        _scanProgress.value = "Verificando archivos existentes…"
        Log.d(TAG, "OrphanCleanup: starting")

        try {
            val allPaths = repository.getAllBookPaths()
            val orphansToDelete = mutableListOf<String>()

            for (bookPath in allPaths) {
                val path = bookPath.filePath

                // Only check legacy file:// and absolute paths — SAF content:// can't be verified
                val isAccessible = when {
                    path.startsWith("/") -> {
                        val f = File(path)
                        val parent = f.parentFile
                        if (parent != null && !parent.exists() && path.startsWith("/storage/") && !path.startsWith("/storage/emulated/")) {
                            true // SD card or external volume unmounted; protect book
                        } else {
                            f.exists()
                        }
                    }
                    path.startsWith("file://") -> {
                        val filePath = Uri.decode(path.removePrefix("file://"))
                        val f = File(filePath)
                        val parent = f.parentFile
                        if (parent != null && !parent.exists() && filePath.startsWith("/storage/") && !filePath.startsWith("/storage/emulated/")) {
                            true
                        } else {
                            f.exists()
                        }
                    }
                    else -> true // content:// URIs — assume accessible, can't verify cheaply
                }

                if (!isAccessible) {
                    orphansToDelete.add(bookPath.id)
                }
            }

            // Circuit breaker: do not wipe whole library on sudden mount loss
            if (allPaths.size > 4 && orphansToDelete.size.toFloat() / allPaths.size > 0.4f) {
                Log.w(TAG, "OrphanCleanup: Mass-deletion circuit breaker triggered (${orphansToDelete.size}/${allPaths.size}). Aborting cleanup.")
                return
            }

            for (id in orphansToDelete) {
                Log.d(TAG, "OrphanCleanup: removing orphan $id")
                repository.deleteBookById(id)
            }

            val orphanCount = orphansToDelete.size
            if (orphanCount > 0) {
                Log.d(TAG, "OrphanCleanup: removed $orphanCount orphaned books")
                _scanProgress.value = "✓ $orphanCount libros huérfanos eliminados"
                delay(2_000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OrphanCleanup: error", e)
        } finally {
            _scanProgress.value = ""
        }
    }

    /** Installs watchers for real-time detection of new/modified books. */
    fun startWatching(paths: List<String> = DEFAULT_SCAN_PATHS) {
        stopWatching()
        startContentObserver()
        paths.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                createFileObserver(dir).also {
                    it.startWatching()
                    observers.add(it)
                    Log.d(TAG, "FileObserver watching: $path")
                }
            }
        }
    }

    /** Stops all active watchers and cancels pending debounced scans. */
    fun stopWatching() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
        debounceJob?.cancel()
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // QUICK SCAN â€” Internal
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private suspend fun quickProcessFile(file: File): Boolean =
        quickProcessBook(
            uri          = Uri.fromFile(file),
            name         = file.name,
            size         = file.length(),
            lastModified = file.lastModified(),
            isLegacyFile = true
        )

    private suspend fun quickProcessScanResult(result: BookScanResult): Boolean =
        quickProcessBook(
            uri          = result.uri,
            name         = result.displayName,
            size         = result.sizeBytes,
            lastModified = result.dateModified,
            // Tratar como legacyFile si la URI es file:// (ya fue convertida desde MediaStore)
            // AsÃ­ el identificador guardado es la ruta fÃ­sica, no la URI de MediaStore.
            isLegacyFile = result.uri.scheme == "file"
        )

    /**
     * Core processing unit for Quick Scan.
     *
     * 1. De-duplicates by identifier (path or URI string)
     * 2. **Incremental**: checks if the path already exists in Room â†’ skips hash computation
     * 3. Computes a fast partial hash (first 1MB of file) only for new files
     * 4. Skips existing books (by hash); self-heals missing covers
     * 5. Inserts a new BookEntity using [BookMetadataExtractor]
     */
    private suspend fun quickProcessBook(uri: Uri, name: String, size: Long, lastModified: Long, isLegacyFile: Boolean): Boolean {
        val identifier = if (isLegacyFile) uri.path ?: return false else uri.toString()
        if (!processingFiles.add(identifier)) return false // Already being processed

        return try {
            // â”€â”€ Incremental check: skip hash if path already registered and hasn't changed â”€â”€â”€â”€â”€â”€
            val existingBook = repository.getBookByPath(identifier)
            if (existingBook != null) {
                // Book already exists at this path. Check if it has been modified.
                if (lastModified > 0 && lastModified <= existingBook.importedAt) {
                    // Not modified, skip hash computation and processing
                    return false
                }

                // If it HAS been modified, compute the hash
                val hash = computePartialHash(uri)
                if (hash == existingBook.fileHash) {
                    // File timestamp changed, but content hash is identical. Just update importedAt.
                    repository.upsertBook(existingBook.copy(importedAt = System.currentTimeMillis()))
                    return false
                }

                // Content hash changed! Extract new metadata
                val enriched = enrichSemaphore.withPermit {
                    metadataExtractor.get().extractMetadata(uri, name, size, hash)
                }
                if (enriched != null) {
                    // Update existing book record, retaining user settings/progress
                    val updatedBook = existingBook.copy(
                        title = enriched.title,
                        author = enriched.author,
                        language = enriched.language,
                        description = enriched.description,
                        fileSizeBytes = size,
                        fileHash = hash,
                        coverCachePath = enriched.coverCachePath ?: existingBook.coverCachePath,
                        importedAt = System.currentTimeMillis()
                    )
                    repository.upsertBook(updatedBook)
                    Log.d(TAG, "Updated modified book: $name")
                    return true
                }
                return false
            }

            val hash = computePartialHash(uri)
            val existing = repository.getBookByHash(hash)

            if (existing != null) {
                // Self-heal: update file path if book moved, restore missing cover
                val coverMissing = existing.coverCachePath?.let { !File(it).exists() } ?: true
                if (existing.filePath != identifier || coverMissing) {
                    enrichExistingBook(existing, uri, name, size, hash, identifier, coverMissing)
                }
                return false // Not a new book
            }

            // New book â€” extract metadata (uses Readium with timeout)
            val entity = enrichSemaphore.withPermit {
                metadataExtractor.get().extractMetadata(uri, name, size, hash)
            }

            if (entity != null) {
                repository.upsertBook(entity)
                Log.d(TAG, "Added: $name")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing: $name", e)
            false
        } finally {
            processingFiles.remove(identifier)
        }
    }

    private suspend fun enrichExistingBook(
        existing: BookEntity,
        uri: Uri,
        name: String,
        size: Long,
        hash: String,
        newIdentifier: String,
        coverMissing: Boolean
    ) {
        if (existing.filePath != newIdentifier) {
            repository.upsertBook(existing.copy(filePath = newIdentifier))
        }
        if (coverMissing) {
            val enriched = metadataExtractor.get().extractMetadata(uri, name, size, hash)
            enriched?.coverCachePath?.let { repository.updateCoverPath(existing.id, it) }
        }
    }

    private suspend fun quickScanSafPath(uriString: String, knownPaths: Set<String>): ScanStats {
        val rootUri = Uri.parse(uriString)
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return ScanStats(0, 0)

        var newCount = 0
        var foundCount = 0
        val newBooksBatch = mutableListOf<BookEntity>()
        val stack = ArrayDeque<DocumentFile>()
        stack.push(rootDoc)

        while (stack.isNotEmpty()) {
            val current = stack.pop()
            current.listFiles().forEach { file ->
                when {
                    file.isDirectory -> stack.push(file)
                    file.isBookFile() -> {
                        foundCount++
                        val path = file.uri.toString()
                        if (path !in knownPaths) {
                            val entity = createLightweightEntity(
                                path, file.name ?: "Unknown", file.length(), file.lastModified()
                            )
                            newBooksBatch.add(entity)
                            newCount++

                            if (newBooksBatch.size >= 100) {
                                repository.upsertBooks(newBooksBatch.toList())
                                newBooksBatch.clear()
                            }
                        }
                    }
                }
            }
        }
        if (newBooksBatch.isNotEmpty()) {
            repository.upsertBooks(newBooksBatch.toList())
        }
        return ScanStats(foundCount, newCount)
    }

    /**
     * Creates a lightweight BookEntity from filename metadata only.
     * No file I/O, no hash, no Readium. Designed for fast batch insertion.
     * Hash + full metadata will be populated later by deepEnrich().
     */
    private fun createLightweightEntity(path: String, name: String, size: Long, modified: Long): BookEntity {
        val nameWithoutExt = name.substringBeforeLast('.')
        val title = nameWithoutExt.trim().ifBlank { name }
        val author = ""
        return BookEntity(
            filePath = path,
            title = title,
            author = author,
            format = when (name.substringAfterLast('.', "").lowercase()) {
                "epub" -> BookFormat.EPUB
                "pdf"  -> BookFormat.PDF
                "cbz"  -> BookFormat.CBZ
                "cbr"  -> BookFormat.CBR
                "cbt"  -> BookFormat.CBT
                "txt"  -> BookFormat.TXT
                "md"   -> BookFormat.MD
                "mobi" -> BookFormat.MOBI
                "azw"  -> BookFormat.AZW
                "azw3" -> BookFormat.AZW3
                "fb2"  -> BookFormat.FB2
                else   -> BookFormat.UNKNOWN
            },
            fileSizeBytes = size,
            fileHash = "pending-${System.nanoTime()}-${path.hashCode()}",
            readingStatus = com.example.woodsreader.data.model.ReadingStatus.NOT_STARTED,
            importedAt = System.currentTimeMillis()
        )
    }

    // â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• 
    // FILE WATCHERS
    // â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• â• 

    private fun startContentObserver() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Debounced: only trigger after DEBOUNCE_MS of inactivity
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    Log.d(TAG, "MediaStore change detected â€” triggering debounced quick scan")
                    quickScan()
                }
            }
        }

        context.contentResolver.registerContentObserver(
            android.provider.MediaStore.Files.getContentUri("external"),
            true,
            contentObserver!!
        )
        Log.d(TAG, "ContentObserver registered")
    }

    private fun createFileObserver(dir: File): RecursiveFileObserver =
        RecursiveFileObserver(dir.absolutePath) { file ->
            if (file.isBookFile()) {
                // Debounce file observer events too
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    quickProcessFile(file)
                }
            }
        }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // HASH + FILE VALIDATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Computes a partial SHA-256 over the first 1MB of the file.
     * Fast enough for real-time use while still uniquely identifying books.
     *
     * This method is public so that [MainActivity] can reuse it for
     * external intent handling without duplicating the implementation.
     */
    fun computePartialHash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8_192)
            var totalRead = 0L
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1 && totalRead < 1_048_576L) {
                digest.update(buffer, 0, bytesRead)
                totalRead += bytesRead
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // â”€â”€ File validation extensions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun File.isBookFile(): Boolean {
        if (!isFile || !canRead()) return false
        if (length() !in SIZE_RANGE) return false
        val ext = extension.lowercase()
        if (ext !in SUPPORTED_EXTENSIONS) return false
        return validateMagicBytes(ext) { buffer -> inputStream().use { it.read(buffer); buffer } }
    }

    private fun DocumentFile.isBookFile(): Boolean {
        if (!isFile || !canRead()) return false
        if (length() !in SIZE_RANGE) return false
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        if (ext !in SUPPORTED_EXTENSIONS) return false
        return validateMagicBytes(ext) { buffer ->
            context.contentResolver.openInputStream(uri)?.use { it.read(buffer) }
            buffer
        }
    }

    private inline fun validateMagicBytes(ext: String, readBytes: (ByteArray) -> ByteArray): Boolean {
        return try {
            val buffer = readBytes(ByteArray(4))
            val header = buffer.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            when (ext) {
                "pdf"        -> header.startsWith("25504446") // %PDF
                "epub", "cbz" -> header.startsWith("504B0304") // PK.. (ZIP)
                else         -> true
            }
        } catch (_: Exception) { false }
    }

}


