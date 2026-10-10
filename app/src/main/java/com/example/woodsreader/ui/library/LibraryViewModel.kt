package com.example.woodsreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.CollectionEntity
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.service.BookScannerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import android.net.Uri
import android.content.Intent
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
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val scannerService: BookScannerService,
    private val preferencesRepository: UserPreferencesRepository,
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
        viewModelScope.launch {
            // Auto-heal any missing or broken covers in the background
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
            bookRepository.upsertBook(book.copy(collectionId = collectionId))
        }
    }

    fun updateBookStatus(book: BookEntity, status: ReadingStatus) {
        viewModelScope.launch {
            bookRepository.upsertBook(book.copy(readingStatus = status))
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
