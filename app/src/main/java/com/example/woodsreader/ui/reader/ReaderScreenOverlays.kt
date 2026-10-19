package com.example.woodsreader.ui.reader

import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import com.example.woodsreader.core.managers.LookupResult
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.databinding.FragmentReaderContainerBinding
import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.TtsHighlightStyle
import com.example.woodsreader.model.TtsUiStyle
import com.example.woodsreader.ui.reader.animation.BookPageTurnOverlay
import com.example.woodsreader.ui.reader.animation.BookPageTurnState
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.LiterataFamily
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl

internal fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Crea una versión ligera de Publication para EpubNavigatorFactory / EpubNavigatorFragment.
 * ⚠️ SOLUCIÓN CRÍTICA PARA LIBROS CON CIENTOS O MILES DE CAPÍTULOS:
 * Readium 3.0.3 tiene un bug severo en EpubNavigatorFragment.tableOfContentsTitleByHref$2 donde
 * realiza copias recursivas con el operador '+' sobre HashMaps por cada enlace del TOC en el hilo principal.
 * En libros con > 80 capítulos (ej. web novels con 2,225 capítulos), esto genera más de 2.5 millones de
 * entradas de mapa y congela la UI durante más de 40 segundos hasta provocar java.lang.OutOfMemoryError.
 * EpubNavigatorFragment solo usa tableOfContents para nombrar el Locator, no para renderizar el libro
 * (el renderizado se hace 100% sobre readingOrder).
 * Pasando tableOfContents vacío al navegador, el libro abre de forma INSTANTÁNEA (0 ms).
 * El TOC completo original se mantiene intacto en state.publication para TocBottomSheet.
 */
internal fun org.readium.r2.shared.publication.Publication.toLightweightNavPublication(): org.readium.r2.shared.publication.Publication =
    com.example.woodsreader.core.pipeline.EpubPackageSanitizer.toLightweightNavPublication(this)

/**
 * Contenedor desacoplado para el Navigator de Readium (EPUB o PDF).
 * Aísla AndroidViewBinding y las llamadas a setLayerType y FragmentManager
 * de las recomposiciones continuas del Lector, evitando destrucción de texturas GPU en Mali.
 */
@Composable
fun ReaderNavigatorContainer(
    state: ReaderUiState.Success,
    userPreferences: com.example.woodsreader.data.datastore.UserPreferences,
    epubPreferences: org.readium.r2.navigator.epub.EpubPreferences,
    pageTurnState: BookPageTurnState,
    ttsSelectionCallback: android.view.ActionMode.Callback,
    ttsState: TtsState,
    isReflowMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fm = remember(context) { context.findFragmentActivity()?.supportFragmentManager }
    val isPdf = state.book.format.name == "PDF"

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = if (ttsState == TtsState.PLAYING && isPdf && !isReflowMode) 2.dp else 0.dp,
                color = HiddenForest.Accent.copy(alpha = 0.5f)
            )
    ) {
        AndroidViewBinding(FragmentReaderContainerBinding::inflate) {
            val isDarkTheme = userPreferences.theme == ReaderTheme.HIDDEN_FOREST || 
                              userPreferences.theme == ReaderTheme.AMOLED || 
                              userPreferences.theme == ReaderTheme.SUNSET

            // ⚠️ OPTIMIZACIÓN CRÍTICA: Solo modificar layerType si difiere del actual
            // Llamar setLayerType() incondicionalmente destruía las texturas EGL en cada frame (Mali BAD ALLOC)
            val targetLayerType = if (isPdf && isDarkTheme) android.view.View.LAYER_TYPE_HARDWARE else android.view.View.LAYER_TYPE_NONE
            if (this.readerFragmentContainer.layerType != targetLayerType) {
                if (targetLayerType == android.view.View.LAYER_TYPE_HARDWARE) {
                    val colorMatrix = android.graphics.ColorMatrix(
                        floatArrayOf(
                            -1f,  0f,  0f, 0f, 255f,
                             0f, -1f,  0f, 0f, 255f,
                             0f,  0f, -1f, 0f, 255f,
                             0f,  0f,  0f, 1f,   0f
                        )
                    )
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                    }
                    this.readerFragmentContainer.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                } else {
                    this.readerFragmentContainer.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                }
            }

            val container = this.readerFragmentContainer
            val containerId = container.id
            val bookTag = "BOOK_${state.book.id}"

            // ⚠️ Garantizar que la transacción de fragmento se encole limpiamente cuando cambia el libro
            // o cuando el contenedor no tiene un fragmento adjunto
            if (fm != null && (container.tag != bookTag || fm.findFragmentById(containerId) == null)) {
                container.tag = bookTag
                val initialLocator = state.initialLocator
                    ?: state.initialLocatorJson?.let {
                        try { Locator.fromJSON(org.json.JSONObject(it)) } catch (_: Exception) { null }
                    }
                    ?: state.book.fallbackLocatorHref?.let { href ->
                        state.publication.readingOrder.find { it.href.toString() == href }?.let { link ->
                            state.publication.locatorFromLink(link)
                        }
                    }
                    ?: if (state.book.format.name == "PDF" && (state.book.fallbackLocatorPosition ?: 0) > 0) {
                        val link = state.publication.readingOrder.firstOrNull()
                        if (link != null) {
                            state.publication.locatorFromLink(link)?.copy(
                                locations = Locator.Locations(position = state.book.fallbackLocatorPosition)
                            )
                        } else null
                    } else null

                val magnetProperties = RsProperties()

                val factory = when (state.book.format.name) {
                    "EPUB" -> {
                        val navPublication = state.publication.toLightweightNavPublication()
                        EpubNavigatorFactory(navPublication).createFragmentFactory(
                            initialLocator = initialLocator,
                            initialPreferences = epubPreferences,
                            listener = object : EpubNavigatorFragment.Listener {
                                override fun onExternalLinkActivated(url: AbsoluteUrl) {}
                            },
                            configuration = EpubNavigatorFragment.Configuration(
                                selectionActionModeCallback = ttsSelectionCallback,
                                readiumCssRsProperties = magnetProperties
                            )
                        )
                    }
                    "PDF" -> PdfNavigatorFragment.createFactory(
                        publication = state.publication,
                        initialLocator = initialLocator,
                        listener = object : PdfNavigatorFragment.Listener {},
                        pdfEngineProvider = org.readium.adapter.pdfium.navigator.PdfiumEngineProvider(),
                        preferences = userPreferences.toPdfPreferences()
                    )
                    else -> null
                }
                if (factory != null) {
                    fm.fragmentFactory = factory
                    val fragmentClass = if (state.book.format.name == "EPUB") EpubNavigatorFragment::class.java.name else PdfNavigatorFragment::class.java.name
                    val newFragment = fm.fragmentFactory.instantiate(context.classLoader, fragmentClass)
                    fm.commit { replace(containerId, newFragment) }
                }
            }
        }

        // ── Capa de animación 3D de paso de página ───────────
        BookPageTurnOverlay(
            state = pageTurnState,
            style = userPreferences.pageTurnStyle,
            theme = userPreferences.theme,
            modifier = Modifier.fillMaxSize().zIndex(15f)
        )
    }
}

/**
 * Menú contextual de selección de texto y compartición de citas.
 */
@Composable
fun ReaderHighlightOverlay(
    selection: Selection?,
    lookupState: LookupResult,
    hazeState: HazeState,
    quoteToShare: String?,
    bookTitle: String,
    bookAuthor: String,
    chapterTitle: String,
    onColorSelected: (HighlightColor) -> Unit,
    onSaveCharacter: (name: String, role: String, note: String) -> Unit,
    onCopy: () -> Unit,
    onShare: (String) -> Unit,
    onExpandToSentence: () -> Unit,
    onPlayTts: (Locator) -> Unit,
    onSearch: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onDictionary: (String) -> Unit,
    onIgnoreTts: (String) -> Unit,
    onErrorOpenBrowser: (String) -> Unit,
    onSpeakSnippet: (String) -> Unit,
    onDismissHighlight: () -> Unit,
    onDismissQuoteShare: () -> Unit
) {
    if (selection != null) {
        val locator = selection.locator
        val highlightedText = locator.text.highlight ?: ""

        HighlightColorMenu(
            hazeState = hazeState,
            selectedText = highlightedText,
            lookupState = lookupState,
            selectionRect = selection.rect,
            onColorSelected = onColorSelected,
            onSaveCharacter = onSaveCharacter,
            onCopy = onCopy,
            onShare = { onShare(highlightedText) },
            onExpandToSentence = onExpandToSentence,
            onPlayTts = { onPlayTts(locator) },
            onSearch = { onSearch(highlightedText) },
            onTranslate = { onTranslate(highlightedText) },
            onDictionary = { onDictionary(highlightedText) },
            onIgnoreTts = { onIgnoreTts(highlightedText) },
            onErrorOpenBrowser = { onErrorOpenBrowser(highlightedText) },
            onSpeakSnippet = onSpeakSnippet,
            onDismiss = onDismissHighlight
        )
    }

    if (quoteToShare != null) {
        QuoteShareDialog(
            quoteText = quoteToShare,
            bookTitle = bookTitle,
            bookAuthor = bookAuthor,
            chapterTitle = chapterTitle,
            onDismiss = onDismissQuoteShare
        )
    }
}

/**
 * Indicador HUD de Oración y Página actual TTS en modo PDF Normal (sin reflow).
 */
@Composable
fun ReaderPdfTtsHud(
    visible: Boolean,
    currentTtsPage: Int,
    isPaused: Boolean,
    currentTtsSentence: String,
    ttsHighlightStyle: TtsHighlightStyle,
    theme: ReaderTheme,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
            .padding(bottom = bottomPadding)
            .zIndex(4f)
    ) {
        Surface(
            color = HiddenForest.Surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = null,
                            tint = HiddenForest.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PÁGINA $currentTtsPage",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = HiddenForest.Accent
                        )
                    }
                    if (isPaused) {
                        Surface(
                            color = HiddenForest.TextSecondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "EN PAUSA",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = HiddenForest.TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (currentTtsSentence.isNotBlank()) {
                    val isDarkTheme = theme != ReaderTheme.DAY && theme != ReaderTheme.SEPIA
                    val hudHighlightColor = when (ttsHighlightStyle) {
                        TtsHighlightStyle.BACKGROUND_YELLOW -> if (isDarkTheme) Color(0xFFFFD600) else Color(0xFF9E6500)
                        TtsHighlightStyle.BACKGROUND_ACCENT -> if (isDarkTheme) Color(0xFF2ECC71) else Color(0xFF1B663E)
                        TtsHighlightStyle.BACKGROUND_BLUE -> if (isDarkTheme) Color(0xFF2196F3) else Color(0xFF0D47A1)
                        TtsHighlightStyle.BACKGROUND_ORANGE -> if (isDarkTheme) Color(0xFFFF7A45) else Color(0xFFB03A00)
                        TtsHighlightStyle.UNDERLINE -> if (isDarkTheme) HiddenForest.Text else Color(0xFF1B1B1B)
                        TtsHighlightStyle.NONE -> if (isDarkTheme) HiddenForest.Text else Color(0xFF1B1B1B)
                    }
                    val hudUnderline = if (ttsHighlightStyle == TtsHighlightStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None
                    val hudWeight = if (ttsHighlightStyle == TtsHighlightStyle.NONE) FontWeight.Normal else FontWeight.Medium

                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentTtsSentence,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = LiterataFamily,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = hudWeight
                            ),
                            color = hudHighlightColor,
                            textDecoration = hudUnderline,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Controles de reproducción TTS: Modo Barra inferior o Botón Flotante.
 */
@Composable
fun ReaderTtsControlsLayer(
    ttsUiStyle: TtsUiStyle,
    isTtsActive: Boolean,
    showTtsMacroWindow: Boolean,
    ttsState: TtsState,
    isLoadingTtsModel: Boolean,
    currentSentenceFlow: StateFlow<String>,
    currentTtsRangeFlow: StateFlow<IntRange?>,
    chapterInfoFlow: StateFlow<ChapterInfo>,
    currentSpeed: Float,
    chapterProgressForTts: Float,
    isReflowMode: Boolean,
    floatingBottomPadding: Dp,
    hazeState: HazeState,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSkipSentenceForward: () -> Unit,
    onSkipSentenceBackward: () -> Unit,
    onSkipWordForward: () -> Unit,
    onSkipWordBackward: () -> Unit,
    onSkipChapterForward: () -> Unit,
    onSkipChapterBackward: () -> Unit,
    onSpeedUp: () -> Unit,
    onSpeedDown: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onUpdateTtsUiStyle: (TtsUiStyle) -> Unit,
    onLockScreen: () -> Unit,
    onOpenMacroWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (ttsUiStyle) {
        TtsUiStyle.BAR -> {
            AnimatedVisibility(
                visible = isTtsActive && !showTtsMacroWindow,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = modifier
                    .zIndex(50f)
            ) {
                ReaderTtsBar(
                    ttsState = ttsState,
                    isLoadingTtsModel = isLoadingTtsModel,
                    currentSentenceFlow = currentSentenceFlow,
                    currentTtsRangeFlow = currentTtsRangeFlow,
                    chapterInfoFlow = chapterInfoFlow,
                    currentSpeed = currentSpeed,
                    hazeState = hazeState,
                    onPlay = {
                        if (ttsState == TtsState.IDLE || ttsState == TtsState.STOPPED) onPlay()
                        else onResume()
                    },
                    onPause = onPause,
                    onStop = onStop,
                    onSkipForward = onSkipSentenceForward,
                    onSkipBackward = onSkipSentenceBackward,
                    onSkipWordForward = onSkipWordForward,
                    onSkipWordBackward = onSkipWordBackward,
                    onSkipChapterForward = onSkipChapterForward,
                    onSkipChapterBackward = onSkipChapterBackward,
                    onLock = onLockScreen,
                    onExpandSettings = onOpenMacroWindow,
                    onSpeedUp = onSpeedUp,
                    onSpeedDown = onSpeedDown,
                    onSpeedChange = onSpeedChange,
                    onSwipeUp = {
                        onUpdateTtsUiStyle(TtsUiStyle.PANEL)
                        onOpenMacroWindow()
                    },
                    onSwipeDown = {
                        onUpdateTtsUiStyle(TtsUiStyle.FLOATING)
                    }
                )
            }
        }
        TtsUiStyle.FLOATING, TtsUiStyle.PANEL -> {
            if (ttsUiStyle == TtsUiStyle.PANEL) {
                LaunchedEffect(ttsState) {
                    if (ttsState == TtsState.PLAYING && !showTtsMacroWindow) {
                        onOpenMacroWindow()
                    }
                }
            }

            ReaderFloatingTtsButton(
                modifier = modifier
                    .padding(end = 20.dp, bottom = floatingBottomPadding)
                    .zIndex(20f),
                ttsState = ttsState,
                isLoadingTtsModel = isLoadingTtsModel,
                showTtsMacroWindow = showTtsMacroWindow,
                isReflowMode = isReflowMode,
                chapterProgress = chapterProgressForTts,
                speed = currentSpeed,
                hazeState = hazeState,
                onPlayPause = {
                    when (ttsState) {
                        TtsState.PLAYING -> onPause()
                        TtsState.PAUSED -> onResume()
                        TtsState.IDLE, TtsState.STOPPED -> onPlay()
                        else -> {}
                    }
                },
                onSpeedClick = {
                    onUpdateTtsUiStyle(TtsUiStyle.PANEL)
                    onOpenMacroWindow()
                },
                onStop = onStop,
                onOpenSettings = onOpenMacroWindow
            )
        }
    }
}

/**
 * BottomSheets externos para Diccionario y Traducción.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderExternalLookupSheets(
    showDictionarySheet: Boolean,
    showTranslateSheet: Boolean,
    selectedTextForQuery: String,
    onDismissDictionary: () -> Unit,
    onDismissTranslate: () -> Unit
) {
    val context = LocalContext.current

    if (showDictionarySheet) {
        val dictUrl = "https://www.wordreference.com/definicion/" + Uri.encode(selectedTextForQuery.trim())
        ModalBottomSheet(
            onDismissRequest = onDismissDictionary,
            dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
            containerColor = HiddenForest.Surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Definición: $selectedTextForQuery",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = HiddenForest.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "WordReference",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val processTextIntent = Intent().apply {
                                    action = Intent.ACTION_PROCESS_TEXT
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_PROCESS_TEXT, selectedTextForQuery)
                                    putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(processTextIntent, "Buscar definición"))
                                } catch (e: Exception) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, selectedTextForQuery)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Buscar"))
                                    } catch (_: Exception) {}
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "App Externa",
                                tint = HiddenForest.Accent
                            )
                        }
                        IconButton(onClick = onDismissDictionary) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = HiddenForest.TextSecondary
                            )
                        }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(
                                    WebView.RENDERER_PRIORITY_IMPORTANT,
                                    false
                                )
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    view?.reload()
                                    return true
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(dictUrl)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.clearHistory()
                        webView.removeAllViews()
                        webView.destroy()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    if (showTranslateSheet) {
        val translateUrl = "https://translate.google.com/?sl=auto&tl=es&text=" + Uri.encode(selectedTextForQuery)
        ModalBottomSheet(
            onDismissRequest = onDismissTranslate,
            dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
            containerColor = HiddenForest.Surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Traducción",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = HiddenForest.Text
                        )
                        Text(
                            text = "Google Translate",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                    }
                    IconButton(onClick = onDismissTranslate) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = HiddenForest.TextSecondary
                        )
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(
                                    WebView.RENDERER_PRIORITY_IMPORTANT,
                                    false
                                )
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    view?.reload()
                                    return true
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(translateUrl)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.clearHistory()
                        webView.removeAllViews()
                        webView.destroy()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
