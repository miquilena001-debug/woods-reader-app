package com.example.woodsreader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.woodsreader.core.web2epub.WebToEpubImporterDialog
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.CollectionEntity
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.Semantic
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import kotlin.math.roundToInt


// Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
// LIBRARYSCREEN Ã¢â‚¬â€ pantalla principal de coleccion
// Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val books          = viewModel.books.collectAsLazyPagingItems()
    val searchQuery    by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning     by viewModel.isScanning.collectAsStateWithLifecycle(initialValue = false)
    val lastReadBook   by viewModel.lastReadBook.collectAsStateWithLifecycle(initialValue = null)
    val finishedCount  by viewModel.finishedCount.collectAsStateWithLifecycle(initialValue = 0)
    val scanProgress   by viewModel.scanProgress.collectAsStateWithLifecycle(initialValue = "")
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val collections    by viewModel.collections.collectAsStateWithLifecycle()
    val activeCollectionFilter by viewModel.collectionFilter.collectAsStateWithLifecycle()

    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var showCustomScanPathsDialog by remember { mutableStateOf(false) }
    var showWebToEpubDialog by remember { mutableStateOf(false) }
    var collectionToDelete by remember { mutableStateOf<CollectionEntity?>(null) }
    var newCollectionName by remember { mutableStateOf("") }
    var bookForOptions by remember { mutableStateOf<BookEntity?>(null) }
    var bookToDeleteConfirm by remember { mutableStateOf<BookEntity?>(null) }
    var bookToTranslate by remember { mutableStateOf<BookEntity?>(null) }
    // Idiomas seleccionados para la traducción del libro actual
    var pendingTranslateSource by remember { mutableStateOf("auto") }
    var pendingTranslateTarget by remember { mutableStateOf("es") }
    // Muestra el LanguagePickerDialog antes de iniciar la traducción
    var showLanguagePicker by remember { mutableStateOf(false) }
    var bookPendingLanguagePick by remember { mutableStateOf<BookEntity?>(null) }

    // Exportación de audiolibro a M4A (Apple Liquid Glass)
    var showAudioExportDialog by remember { mutableStateOf(false) }
    var bookPendingAudioExport by remember { mutableStateOf<BookEntity?>(null) }
    var bookActiveAudioExport by remember { mutableStateOf<BookEntity?>(null) }
    val audioExportState by viewModel.audioExportState.collectAsStateWithLifecycle()
    val currentVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()

    var bookActivePdfConversion by remember { mutableStateOf<BookEntity?>(null) }
    val pdfConversionProgress by viewModel.pdfConversionProgress.collectAsStateWithLifecycle()

    val isTranslatingBook by viewModel.calibreStyleEpubTranslator.isTranslatingBook.collectAsStateWithLifecycle()
    val translationProgress by viewModel.calibreStyleEpubTranslator.progressState.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    val readingCount by viewModel.inProgressCount.collectAsStateWithLifecycle(initialValue = 0)

    // << CHANGE: ConfiguraciÃƒÂ³n del efecto Shimmer nativo (pulso de alpha)


    // Detectar si la biblioteca estÃƒÂ¡ cargando su primera pÃƒÂ¡gina
    val isInitialLoading = books.loadState.refresh is LoadState.Loading && books.itemCount == 0
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val columnsCount = when {
        screenWidthDp >= 1000 -> 6
        screenWidthDp >= 840  -> 5
        screenWidthDp >= 600  -> 4
        screenWidthDp < 360   -> 2
        else                  -> 3
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HiddenForest.Background)
    ) {
        // Brillo radial superior (niebla del bosque)
        Box(modifier = Modifier.fillMaxSize().drawWithCache {
            val brush = Brush.radialGradient(
                colors = listOf(HiddenForest.AccentGlow, Color.Transparent),
                center = Offset(size.width * 0.5f, 0f),
                radius = size.width * 0.9f
            )
            onDrawBehind {
                drawRect(brush = brush)
            }
        })

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 128.dp)
        ) {
            // Ã¢â€â‚¬Ã¢â€â‚¬ Header Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
            item {
                LibraryHeader(
                    isScanning   = isScanning,
                    scanProgress = scanProgress,
                    readingCount = readingCount,
                    finishedCount = finishedCount,
                    onScanClick  = {
                        if (isScanning) {
                            viewModel.cancelScan()
                        } else {
                            viewModel.startScan()
                        }
                    },
                    onAddFolderClick = { folderPickerLauncher.launch(null) },
                    onManageFoldersClick = { showCustomScanPathsDialog = true },
                    onWebToEpubClick = { showWebToEpubDialog = true }
                )
            }

            // ── Buscador ──
            item {
                WoodsSearchBar(
                    query        = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) }
                )
            }

            // ── Colecciones (Fila de Estanterías) ──
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        CollectionChip(
                            name = "Todos",
                            isSelected = activeCollectionFilter == null,
                            onClick = { viewModel.onCollectionFilterChanged(null) }
                        )
                    }

                    items(collections, key = { it.id }) { collection ->
                        CollectionChip(
                            name = collection.name,
                            isSelected = activeCollectionFilter == collection.id,
                            onClick = {
                                if (activeCollectionFilter == collection.id) {
                                    viewModel.onCollectionFilterChanged(null)
                                } else {
                                    viewModel.onCollectionFilterChanged(collection.id)
                                }
                            },
                            onLongClick = {
                                collectionToDelete = collection
                            }
                        )
                    }

                    item {
                        AddCollectionChip(
                            onClick = { showCreateCollectionDialog = true }
                        )
                    }
                }
            }

            // ── Estadísticas de Escaneo ──────────────────────────────────────────
            item {
                userPreferences?.let { prefs ->
                    ScanStatsCard(
                        lastScanTime = prefs.lastScanTime,
                        filesScanned = prefs.filesScannedCount,
                        newBooksFound = prefs.newBooksFoundCount,
                        onSyncClick = { viewModel.startScan() }
                    )
                }
            }

            // Ã¢â€â‚¬Ã¢â€â‚¬ Hero: Continuar Leyendo (oculto durante bÃƒÂºsqueda activa) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
            if (searchQuery.isBlank()) {
                lastReadBook?.let { book ->
                    item {
                        ContinueReadingHero(
                            bookId   = book.id,
                            title    = book.title,
                            author   = book.author.ifBlank { "Desconocido" },
                            progress = if (book.readingProgressPercent > 0f) {
                                (book.readingProgressPercent * 100).roundToInt().coerceIn(1, 100)
                            } else 0,
                            chapter  = "",
                            coverPath = book.coverCachePath,
                            onClick  = { onBookClick(book.id) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }


            // Ã¢â€â‚¬Ã¢â€â‚¬ Separador + tÃƒÂ­tulo de secciÃƒÂ³n Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TODOS LOS LIBROS — ${books.itemCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // << CHANGE: MÃƒÂ³dulo D-5 Ã¢â‚¬â€ CuadrÃƒÂ­cula Shimmer reactiva al estado inicial de carga
            if (isInitialLoading) {
                items(2, key = { "shimmer_row_$it" }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(columnsCount) {
                            ShimmerBookCard(
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Grid de libros — 3 columnas por fila ──────────────────────────
            // Patron Paging3 correcto: indice directo sobre LazyPagingItems.
            // books[index] == books.get(index): descarga bajo demanda respetando cache.
            // books.peek(index): lectura no destructiva para claves estables.
            val rowCount = (books.itemCount + columnsCount - 1) / columnsCount
            items(
                count = rowCount,
                key = { rowIndex -> books.peek(rowIndex * columnsCount)?.id ?: "row_$rowIndex" }
            ) { rowIndex ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (colIndex in 0 until columnsCount) {
                        val itemIndex = rowIndex * columnsCount + colIndex
                        val book: BookEntity? = if (itemIndex < books.itemCount) books[itemIndex] else null
                        Box(modifier = Modifier.weight(1f)) {
                            if (book != null) {
                                WoodsBookCard(
                                    book = book,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onBookClick(book.id)
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        bookForOptions = book
                                    }
                                )
                            }
                        }
                    }
                }
            }
            // Skeletons de carga al final
            if (isScanning) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(columnsCount) { Box(modifier = Modifier.weight(1f)) { WoodsBookSkeleton() } }
                    }
                }
            }

            if (books.itemCount == 0 && !isScanning) {
                item { EmptyLibrary() }
            }
        }

        // Diálogo para crear colección
        if (showCreateCollectionDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateCollectionDialog = false
                    newCollectionName = ""
                },
                containerColor = HiddenForest.Surface,
                titleContentColor = HiddenForest.Text,
                textContentColor = HiddenForest.TextSecondary,
                title = {
                    Text(
                        text = "Nueva Colección",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        label = { Text("Nombre de colección", color = HiddenForest.TextLow) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = HiddenForest.Text,
                            unfocusedTextColor = HiddenForest.TextSecondary,
                            focusedContainerColor = HiddenForest.SurfaceVariant,
                            unfocusedContainerColor = HiddenForest.SurfaceVariant,
                            focusedIndicatorColor = HiddenForest.Accent,
                            unfocusedIndicatorColor = HiddenForest.Outline,
                            focusedLabelColor = HiddenForest.Accent,
                            unfocusedLabelColor = HiddenForest.TextLow
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newCollectionName.isNotBlank()) {
                                viewModel.createCollection(newCollectionName.trim())
                            }
                            showCreateCollectionDialog = false
                            newCollectionName = ""
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = HiddenForest.Accent)
                    ) {
                        Text("Crear", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateCollectionDialog = false
                            newCollectionName = ""
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = HiddenForest.TextLow)
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showCustomScanPathsDialog) {
            val customPaths = userPreferences?.customScanPaths.orEmpty()
            AlertDialog(
                onDismissRequest = { showCustomScanPathsDialog = false },
                containerColor = HiddenForest.Surface,
                titleContentColor = HiddenForest.Text,
                textContentColor = HiddenForest.TextSecondary,
                title = { Text("Carpetas añadidas") },
                text = {
                    if (customPaths.isEmpty()) {
                        Text("Todavía no has añadido carpetas.")
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            customPaths.forEach { path ->
                                val uri = remember(path) { Uri.parse(path) }
                                val folderName = remember(path) {
                                    Uri.decode(uri.lastPathSegment.orEmpty())
                                        .substringAfterLast('/')
                                        .substringAfterLast(':')
                                        .ifBlank { path }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        folderName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HiddenForest.Text
                                    )
                                    Text(
                                        "Se incluyen sus subcarpetas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HiddenForest.TextLow
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { viewModel.scanCustomFolder(path) }) {
                                            Text("Actualizar")
                                        }
                                        TextButton(onClick = { viewModel.removeCustomScanPath(path) }) {
                                            Text("Quitar")
                                        }
                                    }
                                }
                                if (path != customPaths.last()) {
                                    HorizontalDivider(color = HiddenForest.Outline)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.scanCustomFolders()
                            showCustomScanPathsDialog = false
                        }
                    ) {
                        Text("Actualizar todas")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomScanPathsDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }

        // Diálogo para eliminar colección
        if (collectionToDelete != null) {
            val col = collectionToDelete!!
            AlertDialog(
                onDismissRequest = { collectionToDelete = null },
                containerColor = HiddenForest.Surface,
                titleContentColor = HiddenForest.Text,
                textContentColor = HiddenForest.TextSecondary,
                title = {
                    Text(
                        text = "Eliminar Colección",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text("¿Estás seguro de que deseas eliminar la colección \"${col.name}\"? Los libros asociados no se borrarán.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteCollection(col)
                            if (activeCollectionFilter == col.id) {
                                viewModel.onCollectionFilterChanged(null)
                            }
                            collectionToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Semantic.Error)
                    ) {
                        Text("Eliminar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { collectionToDelete = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = HiddenForest.TextLow)
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // BottomSheet de opciones de libro
        if (bookForOptions != null) {
            BookOptionsBottomSheet(
                book = bookForOptions!!,
                collections = collections,
                onDismiss = { bookForOptions = null },
                onStatusChange = { newStatus ->
                    viewModel.updateBookStatus(bookForOptions!!, newStatus)
                    bookForOptions = bookForOptions!!.copy(readingStatus = newStatus)
                },
                onCollectionChange = { newCollectionId ->
                    viewModel.addBookToCollection(bookForOptions!!, newCollectionId)
                    bookForOptions = bookForOptions!!.copy(collectionId = newCollectionId)
                },
                onDeleteClick = {
                    bookToDeleteConfirm = bookForOptions
                    bookForOptions = null
                },
                onTranslateClick = { selectedBook ->
                    bookPendingLanguagePick = selectedBook
                    bookForOptions = null
                    showLanguagePicker = true
                },
                onAudioExportClick = { selectedBook ->
                    bookPendingAudioExport = selectedBook
                    bookForOptions = null
                    showAudioExportDialog = true
                },
                onPdfToEpubClick = { selectedBook ->
                    bookActivePdfConversion = selectedBook
                    bookForOptions = null
                    viewModel.convertPdfToCleanEpub(selectedBook) { result ->
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(
                                context,
                                "¡EPUB limpio generado e importado exitosamente!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val err = result.exceptionOrNull()?.localizedMessage ?: "Error desconocido"
                            android.widget.Toast.makeText(
                                context,
                                "Error al convertir PDF: $err",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }

        // ── Selector de idioma (previo a la traducción) ──────────────────────
        if (showLanguagePicker && bookPendingLanguagePick != null) {
            LanguagePickerDialog(
                initialSourceLang = pendingTranslateSource,
                initialTargetLang = pendingTranslateTarget,
                onConfirm = { src, tgt ->
                    pendingTranslateSource = src
                    pendingTranslateTarget = tgt
                    bookToTranslate = bookPendingLanguagePick
                    bookPendingLanguagePick = null
                    showLanguagePicker = false
                },
                onDismiss = {
                    showLanguagePicker = false
                    bookPendingLanguagePick = null
                }
            )
        }

        if (bookToTranslate != null) {
            val book = bookToTranslate!!
            LaunchedEffect(book.id) {
                viewModel.translateEpubBook(
                    book = book,
                    sourceLang = pendingTranslateSource,
                    targetLang = pendingTranslateTarget
                ) { result ->
                    result.onFailure { err ->
                        android.widget.Toast.makeText(
                            context,
                            "Error al traducir: ${err.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    result.onSuccess {
                        android.widget.Toast.makeText(
                            context,
                            "¡Libro traducido agregado a la biblioteca!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    bookToTranslate = null
                }
            }

            CalibreTranslationProgressDialog(
                bookTitle = book.title,
                progress = translationProgress,
                onCancel = {
                    viewModel.cancelEpubTranslation()
                    bookToTranslate = null
                }
            )
        }

        // Diálogo de confirmación para eliminar libro
        if (bookToDeleteConfirm != null) {
            val book = bookToDeleteConfirm!!
            AlertDialog(
                onDismissRequest = { bookToDeleteConfirm = null },
                containerColor = HiddenForest.Surface,
                titleContentColor = HiddenForest.Text,
                textContentColor = HiddenForest.TextSecondary,
                title = {
                    Text(
                        text = "Eliminar Libro",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text("¿Deseas eliminar el libro \"${book.title}\" de tu biblioteca? El archivo físico no se borrará.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBook(book)
                            bookToDeleteConfirm = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Semantic.Error)
                    ) {
                        Text("Eliminar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { bookToDeleteConfirm = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = HiddenForest.TextLow)
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showWebToEpubDialog) {
            WebToEpubImporterDialog(
                proLicenseManager = viewModel.proLicenseManager,
                calibreTranslator = viewModel.calibreStyleEpubTranslator,
                onDismiss = { showWebToEpubDialog = false },
                onEpubGenerated = { epubFile ->
                    viewModel.importBookFile(epubFile)
                }
            )
        }

        // ── Diálogo de configuración de Exportación a Audio (Apple Liquid Glass) ──
        if (showAudioExportDialog && bookPendingAudioExport != null) {
            val installedVoices = remember(showAudioExportDialog) { viewModel.getInstalledVoices() }
            BookAudioExportDialog(
                book = bookPendingAudioExport!!,
                availableVoices = installedVoices,
                initialVoice = currentVoice,
                onConfirm = { maxChapters, speed, voice, includeLyrics ->
                    val targetBook = bookPendingAudioExport!!
                    showAudioExportDialog = false
                    bookPendingAudioExport = null
                    bookActiveAudioExport = targetBook
                    viewModel.exportBookToAudio(
                        book = targetBook,
                        maxChapters = maxChapters,
                        speed = speed,
                        voiceOption = voice,
                        includeSyncedLyrics = includeLyrics
                    ) { result ->
                        result.onSuccess {
                            val msg = if (includeLyrics) {
                                "¡Audiolibro con letra sincronizada (.LRC/.SRT) exportado en Música/WoodsReader!"
                            } else {
                                "¡Audiolibro exportado en Música/WoodsReader!"
                            }
                            android.widget.Toast.makeText(
                                context,
                                msg,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        result.onFailure { err ->
                            if (err !is kotlinx.coroutines.CancellationException) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Error al exportar audio: ${err.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onDismiss = {
                    showAudioExportDialog = false
                    bookPendingAudioExport = null
                }
            )
        }

        // ── Diálogo y Dock flotante de progreso de Exportación a Audio (Apple Liquid Glass) ──
        if (bookActiveAudioExport != null) {
            AudioExportProgressDialog(
                bookTitle = bookActiveAudioExport!!.title,
                exportState = audioExportState,
                onCancel = {
                    viewModel.cancelAudioExport()
                    bookActiveAudioExport = null
                },
                onDismiss = {
                    viewModel.resetAudioExportState()
                    bookActiveAudioExport = null
                }
            )
        }

        // ── Diálogo de Progreso de Conversión PDF a EPUB (Emerald Liquid Glass) ──
        if (bookActivePdfConversion != null && pdfConversionProgress != null) {
            PdfConversionProgressDialog(
                bookTitle = bookActivePdfConversion!!.title,
                progress = pdfConversionProgress!!,
                onCancel = {
                    viewModel.cancelPdfConversion()
                    bookActivePdfConversion = null
                },
                onDismiss = {
                    viewModel.dismissPdfConversionDialog()
                    bookActivePdfConversion = null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.8f else 0.2f,
        animationSpec = tween(300),
        label = "border_alpha"
    )
    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            listOf(HiddenForest.Accent.copy(0.25f), HiddenForest.AccentDim.copy(0.15f))
        )
    } else {
        Brush.horizontalGradient(
            listOf(HiddenForest.SurfaceVariant, HiddenForest.Surface)
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .border(
                1.dp,
                if (isSelected) HiddenForest.Accent.copy(borderAlpha) else HiddenForest.Outline,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                ),
                color = if (isSelected) HiddenForest.Text else HiddenForest.TextSecondary
            )
        }
    }
}

@Composable
fun AddCollectionChip(
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(
                1.dp,
                HiddenForest.Outline,
                RoundedCornerShape(16.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Crear Colección",
            tint = HiddenForest.Accent,
            modifier = Modifier.size(16.dp)
        )
    }
}



@Composable
private fun ShimmerBookCard(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .graphicsLayer { alpha = shimmerAlpha }
            .clip(RoundedCornerShape(12.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(1.dp, HiddenForest.Outline, RoundedCornerShape(12.dp))
    )
}
