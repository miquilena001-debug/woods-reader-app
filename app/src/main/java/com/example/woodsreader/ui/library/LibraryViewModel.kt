package com.example.woodsreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.core.translation.calibre.CalibreStyleEpubTranslator
import com.example.woodsreader.core.translation.calibre.inplace.EpubPackageParser
import com.example.woodsreader.core.tts.ChapterAudioExporter
import com.example.woodsreader.core.tts.ChapterTextChunk
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.CollectionEntity
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.service.BookScannerService
import com.example.woodsreader.service.CoverExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import android.net.Uri
import android.content.Intent
import java.io.File
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import com.example.woodsreader.core.pdf.PdfCleanExtractorAndConverter
import com.example.woodsreader.core.pdf.PdfConversionProgress
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val scannerService: BookScannerService,
    private val preferencesRepository: UserPreferencesRepository,
    val proLicenseManager: ProLicenseManager,
    val calibreStyleEpubTranslator: CalibreStyleEpubTranslator,
    val chapterAudioExporter: ChapterAudioExporter,
    val sherpaModelManager: SherpaModelManager,
    private val coverExtractor: CoverExtractor,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // Rutas vigiladas por los FileObservers — se actualiza SOLO si cambian realmente
    private var currentWatchedPaths: List<String> = emptyList()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                // Solo reiniciar los watchers si las rutas cambiaron realmente.
                // Sin este guard, cada recomposición del NavGraph llama
                // stopWatching() + startWatching() y el ContentObserver reciente
                // dispara quickScan() inmediatamente al volver del lector.
                if (prefs.customScanPaths != currentWatchedPaths) {
                    currentWatchedPaths = prefs.customScanPaths
                    scannerService.startWatching(prefs.customScanPaths)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Allow UI first paint to complete smoothly before background cover enrichment starts
            delay(2_500)
            scannerService.enrichMissingCovers()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerService.stopWatching()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchTarget = MutableStateFlow("ALL")
    val searchTarget = _searchTarget.asStateFlow()

    private val _searchFormat = MutableStateFlow<String?>("ALL")
    val searchFormat = _searchFormat.asStateFlow()

    private val _searchStatus = MutableStateFlow<String?>("ALL")
    val searchStatus = _searchStatus.asStateFlow()

    private val _searchCollectionId = MutableStateFlow<String?>("ALL")
    val searchCollectionId = _searchCollectionId.asStateFlow()

    private val _searchSort = MutableStateFlow("TITLE_ASC")
    val searchSort = _searchSort.asStateFlow()

    val recentSearches: Flow<List<String>> = preferencesRepository.userPreferencesFlow
        .map { it.recentSearches }
        .distinctUntilChanged()

    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter = _statusFilter.asStateFlow()

    private val _collectionFilter = MutableStateFlow<String?>(null)
    val collectionFilter = _collectionFilter.asStateFlow()

    val isScanning = scannerService.isScanning
    val scanProgress = scannerService.scanProgress

    val lastReadBook: StateFlow<BookEntity?> = bookRepository.getLastOpenedBook()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Last 10 opened books — drives the history bottom sheet. */
    val recentBooks: StateFlow<List<BookEntity>> = bookRepository.getRecentBooks(10)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val finishedCount: StateFlow<Int> = bookRepository.getCompletedBooksCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val inProgressCount: StateFlow<Int> = bookRepository.getInProgressBooksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userPreferences = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val collections: StateFlow<List<CollectionEntity>> = bookRepository.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Orquestación reactiva de libros: Búsqueda > Colección > Filtro de Estado > Todos
    val books: Flow<PagingData<BookEntity>> = combine(
        _searchQuery.debounce(300),
        _statusFilter,
        _collectionFilter
    ) { query, status, collectionId ->
        Triple(query, status, collectionId)
    }.flatMapLatest { (query, status, collectionId) ->
        when {
            query.isNotBlank() -> bookRepository.searchBooks(query, pageSize = 30)
            collectionId != null -> bookRepository.getBooksByCollection(collectionId, pageSize = 30)
            status != null -> bookRepository.getBooksByStatus(status, pageSize = 30)
            else -> bookRepository.getAllBooks(pageSize = 30)
        }
    }.catch { e ->
        android.util.Log.e("LibraryViewModel", "Error loading books stream", e)
        emit(PagingData.empty())
    }.cachedIn(viewModelScope)

    val searchedBooks: Flow<PagingData<BookEntity>> = combine(
        listOf(
            _searchQuery.debounce(300),
            _searchTarget,
            _searchFormat,
            _searchStatus,
            _searchCollectionId,
            _searchSort
        )
    ) { array: Array<Any?> ->
        array
    }.flatMapLatest { array ->
        val query = array[0] as String
        val target = array[1] as String
        val format = array[2] as String?
        val status = array[3] as String?
        val collectionId = array[4] as String?
        val sort = array[5] as String
        
        bookRepository.searchAndFilterBooks(
            query = query,
            target = target,
            format = format,
            status = status,
            collectionId = collectionId,
            sortOrder = sort,
            pageSize = 30
        )
    }.catch { e ->
        android.util.Log.e("LibraryViewModel", "Error loading searched books stream", e)
        emit(PagingData.empty())
    }.cachedIn(viewModelScope)

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onSearchTargetChanged(target: String) {
        _searchTarget.value = target
    }

    fun onSearchFormatChanged(format: String?) {
        _searchFormat.value = format
    }

    fun onSearchStatusChanged(status: String?) {
        _searchStatus.value = status
    }

    fun onSearchCollectionIdChanged(collectionId: String?) {
        _searchCollectionId.value = collectionId
    }

    fun onSearchSortChanged(sort: String) {
        _searchSort.value = sort
    }

    fun saveSearchToHistory(query: String) = viewModelScope.launch {
        preferencesRepository.addRecentSearch(query)
    }

    fun deleteSearchFromHistory(query: String) = viewModelScope.launch {
        preferencesRepository.removeRecentSearch(query)
    }

    fun clearAllFilters() {
        _searchTarget.value = "ALL"
        _searchFormat.value = "ALL"
        _searchStatus.value = "ALL"
        _searchCollectionId.value = "ALL"
        _searchSort.value = "TITLE_ASC"
    }

    fun onStatusFilterChanged(newStatus: String?) {
        _statusFilter.value = newStatus
    }

    fun onCollectionFilterChanged(collectionId: String?) {
        _collectionFilter.value = collectionId
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            bookRepository.saveCollection(CollectionEntity(name = name))
        }
    }

    fun deleteCollection(collection: CollectionEntity) {
        viewModelScope.launch {
            bookRepository.deleteCollection(collection)
        }
    }

    fun addBookToCollection(book: BookEntity, collectionId: String?) {
        viewModelScope.launch {
            bookRepository.updateBookCollection(book.id, collectionId)
        }
    }

    fun updateBookStatus(book: BookEntity, status: ReadingStatus) {
        viewModelScope.launch {
            bookRepository.updateReadingStatus(book.id, status)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }

    fun startScan() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            scannerService.scan(prefs.customScanPaths)
        }
    }

    fun cancelScan() {
        scannerService.cancelScan()
    }

    fun importBookFile(file: File, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = scannerService.importSingleFile(file)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(success)
            }
        }
    }

    private var translationJob: kotlinx.coroutines.Job? = null

    fun translateEpubBook(
        book: BookEntity,
        sourceLang: String = "en",
        targetLang: String = "es",
        onComplete: ((Result<File>) -> Unit)? = null
    ) {
        if (translationJob?.isActive == true) return
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawPath = book.filePath
                val cleanPath = when {
                    rawPath.startsWith("file://") -> android.net.Uri.parse(rawPath).path ?: rawPath.removePrefix("file://")
                    rawPath.startsWith("file:") -> rawPath.removePrefix("file:")
                    else -> rawPath
                }
                val sourceFile = File(cleanPath)
                val resolvedSource = if (sourceLang == "auto") "en" else sourceLang
                val result = calibreStyleEpubTranslator.translateEntireEpub(
                    sourceEpubFile = sourceFile,
                    sourceLanguageCode = resolvedSource,
                    targetLanguageCode = targetLang
                )
                result.onSuccess { translatedFile ->
                    scannerService.importSingleFile(translatedFile)
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(result)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelado por el usuario: avisar a la UI (cerrar diálogo) y propagar la cancelación
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    onComplete?.invoke(Result.failure(e))
                }
                throw e
            }
        }
    }

    fun cancelEpubTranslation() {
        translationJob?.cancel()
    }

    private val _pdfConversionProgress = MutableStateFlow<PdfConversionProgress?>(null)
    val pdfConversionProgress: StateFlow<PdfConversionProgress?> = _pdfConversionProgress.asStateFlow()

    private var pdfConversionJob: kotlinx.coroutines.Job? = null

    fun convertPdfToCleanEpub(
        book: BookEntity,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
        onComplete: ((Result<File>) -> Unit)? = null
    ) {
        pdfConversionJob?.cancel()
        pdfConversionJob = viewModelScope.launch(ioDispatcher) {
            try {
                val rawPath = book.filePath
                val cleanPath = when {
                    rawPath.startsWith("file://") -> android.net.Uri.parse(rawPath).path ?: rawPath.removePrefix("file://")
                    rawPath.startsWith("file:") -> rawPath.removePrefix("file:")
                    else -> rawPath
                }
                val pdfFile = File(cleanPath)
                if (!pdfFile.exists()) {
                    val err = IllegalStateException("No se encontró el archivo PDF: $cleanPath")
                    withContext(Dispatchers.Main) { onComplete?.invoke(Result.failure(err)) }
                    return@launch
                }

                val parentDir = pdfFile.parentFile?.takeIf { it.canWrite() }
                    ?: runCatching {
                        val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                        val dir = File(docs, "WoodsReader/Epubs").apply { if (!exists()) mkdirs() }
                        dir.takeIf { it.canWrite() }
                    }.getOrNull()
                    ?: (context.getExternalFilesDir(null) ?: context.filesDir)
                val rawBase = pdfFile.nameWithoutExtension.replace('_', ' ').replace(Regex("""\s+"""), " ").trim()
                val safeBaseName = rawBase.replace(Regex("""[^\w\-_\s]"""), "").trim()
                val outputEpubFile = File(parentDir, "${safeBaseName}_Limpio.epub")

                val result = PdfCleanExtractorAndConverter.convertPdfToCleanEpub(
                    pdfFile = pdfFile,
                    outputEpubFile = outputEpubFile,
                    bookTitle = book.title.ifBlank { safeBaseName },
                    bookAuthor = if (book.author.isBlank() || book.author.equals("Web Novel", ignoreCase = true)) "Autor desconocido" else book.author,
                    languageCode = "es",
                    onProgress = { progress ->
                        _pdfConversionProgress.value = progress
                    }
                )

                result.onSuccess { cleanEpubFile ->
                    scannerService.importSingleFile(cleanEpubFile)
                }

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(result)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    onComplete?.invoke(Result.failure(e))
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(Result.failure(e))
                }
            }
        }
    }

    fun cancelPdfConversion() {
        pdfConversionJob?.cancel()
        _pdfConversionProgress.value = null
    }

    fun dismissPdfConversionDialog() {
        _pdfConversionProgress.value = null
    }

    val audioExportState = chapterAudioExporter.exportState

    fun isSherpaModelReady(): Boolean = sherpaModelManager.isModelReady()

    fun getInstalledVoices(): List<com.example.woodsreader.core.tts.SherpaVoiceOption> =
        sherpaModelManager.getInstalledModels()

    val selectedVoice: StateFlow<com.example.woodsreader.core.tts.SherpaVoiceOption> =
        sherpaModelManager.selectedVoice

    fun exportBookToAudio(
        book: BookEntity,
        maxChapters: Int,
        speed: Float = 1.0f,
        voiceOption: com.example.woodsreader.core.tts.SherpaVoiceOption? = null,
        includeSyncedLyrics: Boolean = true,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            var zipFile: ZipFile? = null
            try {
                val rawPath = book.filePath
                val cleanPath = when {
                    rawPath.startsWith("file://") -> android.net.Uri.parse(rawPath).path ?: rawPath.removePrefix("file://")
                    rawPath.startsWith("file:") -> rawPath.removePrefix("file:")
                    else -> rawPath
                }
                val sourceFile = File(cleanPath)
                if (!sourceFile.exists()) {
                    val err = IllegalStateException("No se encontró el archivo del libro: $cleanPath")
                    withContext(Dispatchers.Main) { onComplete?.invoke(Result.failure(err)) }
                    return@launch
                }

                zipFile = ZipFile(sourceFile)
                val epubPackage = EpubPackageParser.parse(zipFile)
                val readingOrder = epubPackage.readingOrderZipPaths
                if (readingOrder.isEmpty()) {
                    val err = IllegalStateException("El libro no contiene capítulos legibles")
                    withContext(Dispatchers.Main) { onComplete?.invoke(Result.failure(err)) }
                    return@launch
                }

                // Filtrar solo entradas que contengan texto legible (descartando portadas o páginas vacías)
                val currentZip = zipFile
                val contentEntries = readingOrder.filter { zipPath ->
                    val entry = findZipEntry(currentZip, zipPath) ?: return@filter false
                    val text = runCatching {
                        currentZip.getInputStream(entry).use { inStream ->
                            Jsoup.parse(inStream, "UTF-8", "").body()?.text().orEmpty()
                        }
                    }.getOrDefault("")
                    text.trim().length > 40
                }.ifEmpty { readingOrder }

                val countToExport = minOf(maxChapters, contentEntries.size)
                val label = when {
                    countToExport == 1 -> "Capitulo_1"
                    countToExport >= contentEntries.size -> "Libro_Completo"
                    else -> "Caps_1_a_$countToExport"
                }

                val coverFile = book.coverCachePath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                    ?: coverExtractor.getExistingCoverPath(book.fileHash)?.let { File(it) }
                    ?: File(context.filesDir, "covers/cover_${book.fileHash}.jpg").takeIf { it.exists() && it.length() > 0 }
                    ?: File(context.cacheDir, "covers/cover_${book.fileHash}.jpg").takeIf { it.exists() && it.length() > 0 }
                    ?: runCatching {
                        coverExtractor.extractCover(
                            source = sourceFile,
                            format = book.format,
                            fileHash = book.fileHash,
                            title = book.title,
                            author = book.author
                        )?.let { File(it) }
                    }.getOrNull()

                val result = chapterAudioExporter.exportChaptersToM4a(
                    bookTitle = book.title,
                    exportLabel = label,
                    totalChaptersCount = countToExport,
                    chapterProvider = { offset ->
                        val zipEntryPath = contentEntries.getOrNull(offset) ?: return@exportChaptersToM4a null
                        val entry = findZipEntry(currentZip, zipEntryPath) ?: return@exportChaptersToM4a null
                        val doc = currentZip.getInputStream(entry).use { inStream ->
                            Jsoup.parse(inStream, "UTF-8", "")
                        }
                        val title = doc.selectFirst("h1, h2, h3, title")?.text()?.takeIf { it.isNotBlank() } ?: "Capítulo ${offset + 1}"
                        val elements = doc.select("p, h1, h2, h3, h4, h5, h6")
                        val paragraphs = if (elements.isNotEmpty()) {
                            elements.map { it.text().trim() }.filter { it.isNotBlank() }
                        } else {
                            doc.body().text().split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        }
                        ChapterTextChunk(title, paragraphs)
                    },
                    speed = speed,
                    voiceOption = voiceOption,
                    includeSyncedLyrics = includeSyncedLyrics,
                    coverImageFile = coverFile,
                    bookAuthor = book.author
                )

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(result)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    onComplete?.invoke(Result.failure(e))
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(Result.failure(e))
                }
            } finally {
                try {
                    zipFile?.close()
                } catch (_: Exception) {}
            }
        }
        chapterAudioExporter.setActiveJob(job)
    }

    fun cancelAudioExport() {
        chapterAudioExporter.cancelExport()
    }

    fun resetAudioExportState() {
        chapterAudioExporter.resetState()
    }

    private fun findZipEntry(zip: ZipFile, path: String): java.util.zip.ZipEntry? {
        val cleanPath = path.replace('\\', '/')
        return zip.getEntry(cleanPath)
            ?: zip.entries().asSequence().firstOrNull { it.name.equals(cleanPath, ignoreCase = true) }
            ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(cleanPath.substringAfterLast('/'), ignoreCase = true) }
    }

    fun onFolderSelected(uri: Uri) {
        // Persistir el permiso para que sea válido tras reiniciar la app
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModelScope.launch {
                preferencesRepository.addCustomScanPath(uri.toString())
                // Iniciar escaneo inmediatamente tras añadir carpeta
                scannerService.scan(listOf(uri.toString()))
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Error taking persistable permission", e)
        }
    }
}
