package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.request.crossfade
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.data.model.ReflowState
import com.example.woodsreader.model.TtsHighlightStyle
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.LiterataFamily
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/**
 * Vista Libro para PDFs.
 * Renderiza el texto extraído y limpiado por ReadingPipeline como un libro fluido,
 * con la misma estética tipográfica, temas y navegación que un EPUB.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderReflowOverlay(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    reflowState: ReflowState,
    ttsActiveBlockIndex: Int = -1,
    fontScale: Float = 1.0f,
    hazeState: HazeState,
    isLoadingMore: Boolean = false,
    ttsLocator: Locator? = null,
    isTtsPlaying: Boolean = false,
    currentTtsSentence: String = "",
    preferences: UserPreferences,
    onLoadMore: () -> Unit = {},
    onLoadPrevious: () -> Unit = {},
    onScroll: (Int, Boolean) -> Unit = { _, _ -> },
    onToggleControls: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrev: () -> Unit = {},
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val items = reflowState.items
    val coroutineScope = rememberCoroutineScope()

    // Precalcular items legibles para O(1)
    val readableItems = remember(items) {
        items.filter { it is ReflowItem.Header || it is ReflowItem.Paragraph || it is ReflowItem.Text }
    }
    val readableItemToIndex = remember(readableItems) {
        readableItems.mapIndexed { index, item -> item to index }.toMap()
    }
    val textElements = remember(items) {
        items.filterIsInstance<ReflowItem.Text>().map { it.element }
    }
    val elementToTextIndex = remember(textElements) {
        textElements.mapIndexed { index, elem -> elem to index }.toMap()
    }

    // Auto-scroll al bloque TTS activo
    LaunchedEffect(ttsActiveBlockIndex, preferences.ttsAutoScroll) {
        if (preferences.ttsAutoScroll && ttsActiveBlockIndex >= 0 && items.isNotEmpty()) {
            val targetItem = readableItems.getOrNull(ttsActiveBlockIndex)
            if (targetItem != null) {
                val indexInAllItems = items.indexOf(targetItem)
                if (indexInAllItems in items.indices && !listState.isScrollInProgress) {
                    listState.animateScrollToItem(indexInAllItems)
                }
            }
        }
    }

    // Sincronización de scroll
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    LaunchedEffect(firstVisibleIndex, isScrolling) {
        if (isVisible && items.isNotEmpty()) {
            onScroll(firstVisibleIndex, isScrolling)
        }
    }

    // Detección de carga incremental hacia adelante (evita auto-desplazamiento infinito hacia atrás)
    LaunchedEffect(listState, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && !isLoadingMore && items.isNotEmpty()) {
                    val lastVisibleIndex = visibleItems.last().index
                    if (lastVisibleIndex >= items.size - 3) {
                        onLoadMore()
                    }
                }
            }
    }

    var zoomedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Colores del tema del libro idénticos al EPUB
    val theme = preferences.theme
    val (bgColor, textColor) = remember(theme) {
        when (theme) {
            com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST -> Color(0xFF0A0D0A) to Color(0xFFD4E8D4)
            com.example.woodsreader.model.ReaderTheme.DAY -> Color(0xFFFAF9F6) to Color(0xFF242424)
            com.example.woodsreader.model.ReaderTheme.SEPIA -> Color(0xFFF7EED8) to Color(0xFF4A3728)
            com.example.woodsreader.model.ReaderTheme.AMOLED -> Color(0xFF000000) to Color(0xFFE0E0E0)
            com.example.woodsreader.model.ReaderTheme.SUNSET -> Color(0xFF1A0D08) to Color(0xFFEDD5C0)
        }
    }

    val composeFontFamily = remember(preferences.fontFamily) {
        when (preferences.fontFamily.lowercase()) {
            "sans-serif", "inter" -> FontFamily.SansSerif
            "monospace", "jetbrains mono" -> FontFamily.Monospace
            else -> LiterataFamily
        }
    }

    val composeTextAlign = remember(preferences.textAlign) {
        when (preferences.textAlign) {
            "justify" -> TextAlign.Justify
            "left" -> TextAlign.Left
            "right" -> TextAlign.Right
            "center" -> TextAlign.Center
            else -> TextAlign.Start
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width
                            val x = offset.x
                            // 50% central: alternar barras de control (como en EPUB)
                            if (x in (width * 0.25f)..(width * 0.75f)) {
                                onToggleControls()
                            } else if (x < width * 0.25f) {
                                // Tap lateral izquierdo: avanzar hacia arriba
                                if (items.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val current = listState.firstVisibleItemIndex
                                        listState.animateScrollToItem((current - 3).coerceAtLeast(0))
                                    }
                                }
                            } else {
                                // Tap lateral derecho: avanzar hacia abajo
                                if (items.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val current = listState.firstVisibleItemIndex
                                        val maxIdx = (items.size - 1).coerceAtLeast(0)
                                        listState.animateScrollToItem((current + 3).coerceIn(0, maxIdx))
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = HiddenForest.Accent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                reflowState.currentLoadingPage == -1 -> "Abriendo libro..."
                                reflowState.currentLoadingPage > 0 -> "Preparando página ${reflowState.currentLoadingPage}..."
                                else -> "Cargando libro..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f),
                            fontFamily = composeFontFamily
                        )
                    }
                }
            } else {
                val horizontalMargin = (preferences.horizontalMargin.takeIf { it > 0 } ?: 20f).dp

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalMargin),
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 72.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = items,
                        key = { index, item -> getItemKey(item, index) }
                    ) { index, item ->
                        when (item) {
                            is ReflowItem.Header -> {
                                val itemIndex = readableItemToIndex[item] ?: -1
                                val isHighlighted = itemIndex == ttsActiveBlockIndex

                                HeaderItem(
                                    text = item.content,
                                    level = item.level,
                                    fontFamily = composeFontFamily,
                                    fontSizeSp = preferences.fontSizeSp,
                                    textColor = textColor,
                                    isHighlighted = isHighlighted,
                                    currentTtsSentence = currentTtsSentence,
                                    ttsHighlightStyle = preferences.ttsHighlightStyle,
                                    theme = preferences.theme
                                )
                            }
                            is ReflowItem.Paragraph -> {
                                val itemIndex = readableItemToIndex[item] ?: -1
                                val isHighlighted = itemIndex == ttsActiveBlockIndex

                                ParagraphItem(
                                    text = item.content,
                                    fontFamily = composeFontFamily,
                                    textAlign = composeTextAlign,
                                    lineSpacing = preferences.lineSpacingMultiplier,
                                    fontSizeSp = preferences.fontSizeSp,
                                    textColor = textColor,
                                    isHighlighted = isHighlighted,
                                    currentTtsSentence = currentTtsSentence,
                                    hyphenation = preferences.hyphenation,
                                    ttsHighlightStyle = preferences.ttsHighlightStyle,
                                    theme = preferences.theme
                                )
                            }
                            is ReflowItem.PageNumber -> { /* Filtrado para flujo editorial limpio */ }
                            is ReflowItem.Image -> {
                                ReflowImageItem(
                                    item = item,
                                    onZoom = { zoomedImageBytes = it }
                                )
                            }
                            is ReflowItem.Ignored -> { /* Filtrado */ }
                            is ReflowItem.Text -> {
                                val element = item.element
                                val textContent = element.locator.text.highlight
                                if (!textContent.isNullOrBlank()) {
                                    val itemIndex = elementToTextIndex[element] ?: -1
                                    val isHighlighted = itemIndex == ttsActiveBlockIndex
                                    ParagraphItem(
                                        text = textContent,
                                        fontFamily = composeFontFamily,
                                        textAlign = composeTextAlign,
                                        lineSpacing = preferences.lineSpacingMultiplier,
                                        fontSizeSp = preferences.fontSizeSp,
                                        textColor = textColor,
                                        isHighlighted = isHighlighted,
                                        currentTtsSentence = currentTtsSentence,
                                        hyphenation = preferences.hyphenation,
                                        ttsHighlightStyle = preferences.ttsHighlightStyle,
                                        theme = preferences.theme
                                    )
                                }
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = HiddenForest.Accent,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                // Pie de página sutil de libro (Running Folio)
                if (reflowState.totalPages > 0 && items.isNotEmpty()) {
                    val currentViewingPage = remember(firstVisibleIndex, items) {
                        val currentItem = items.getOrNull(firstVisibleIndex)
                        currentItem?.pageNumber ?: reflowState.currentPage
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 6.dp)
                            .background(bgColor.copy(alpha = 0.90f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Pág. $currentViewingPage de ${reflowState.totalPages}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = composeFontFamily,
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.50f)
                            )
                        )
                    }
                }
            }
        }
    }

    val bytesToZoom = zoomedImageBytes
    if (bytesToZoom != null) {
        ZoomableImageDialog(
            imageBytes = bytesToZoom,
            onDismiss = { zoomedImageBytes = null }
        )
    }
}

@Composable
fun HeaderItem(
    text: String,
    level: Int,
    fontFamily: FontFamily,
    fontSizeSp: Float,
    textColor: Color,
    isHighlighted: Boolean,
    currentTtsSentence: String,
    ttsHighlightStyle: TtsHighlightStyle = TtsHighlightStyle.BACKGROUND_YELLOW,
    theme: com.example.woodsreader.model.ReaderTheme = com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST,
    modifier: Modifier = Modifier
) {
    val isChapterTitle = remember(text) {
        val trimmed = text.trim()
        trimmed.length <= 60 && (
            trimmed.contains(Regex("""(?i)^(cap[íi]tulo|chapter|parte|secci[óo]n|pr[óo]logo|ep[íi]logo|\b[0-9IVXLCDM]+\b)""")) ||
            trimmed.startsWith("#")
        )
    }

    val scale = when {
        isChapterTitle -> 1.6f
        level == 1 -> 1.45f
        level == 2 -> 1.25f
        level == 3 -> 1.15f
        else -> 1.05f
    }
    val headerSize = (fontSizeSp * scale).sp
    val headerWeight = if (level <= 2 || isChapterTitle) FontWeight.Bold else FontWeight.SemiBold
    val topPad = if (isChapterTitle) 38.dp else when (level) {
        1 -> 28.dp
        2 -> 20.dp
        else -> 14.dp
    }
    val bottomPad = if (isChapterTitle) 16.dp else when (level) {
        1 -> 12.dp
        2 -> 8.dp
        else -> 4.dp
    }

    val isDarkTheme = theme != com.example.woodsreader.model.ReaderTheme.DAY && theme != com.example.woodsreader.model.ReaderTheme.SEPIA
    val headerHighlightColor = when (ttsHighlightStyle) {
        TtsHighlightStyle.BACKGROUND_YELLOW -> if (isDarkTheme) Color(0xFFFFD600) else Color(0xFF9E6500)
        TtsHighlightStyle.BACKGROUND_ACCENT -> if (isDarkTheme) Color(0xFF2ECC71) else Color(0xFF1B663E)
        TtsHighlightStyle.BACKGROUND_BLUE -> if (isDarkTheme) Color(0xFF2196F3) else Color(0xFF0D47A1)
        TtsHighlightStyle.BACKGROUND_ORANGE -> if (isDarkTheme) Color(0xFFFF7A45) else Color(0xFFB03A00)
        TtsHighlightStyle.UNDERLINE -> textColor
        TtsHighlightStyle.NONE -> textColor
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPad, bottom = bottomPad),
        horizontalAlignment = if (isChapterTitle) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = text,
            fontSize = headerSize,
            fontWeight = headerWeight,
            fontFamily = fontFamily,
            color = if (isHighlighted) headerHighlightColor else textColor,
            textDecoration = if (isHighlighted && ttsHighlightStyle == TtsHighlightStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
            textAlign = if (isChapterTitle) TextAlign.Center else TextAlign.Start,
            letterSpacing = if (isChapterTitle) 1.5.sp else 0.sp,
            lineHeight = (fontSizeSp * scale * 1.3f).sp,
            modifier = Modifier.fillMaxWidth()
        )
        if (isChapterTitle) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(0.55f)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = textColor.copy(alpha = 0.22f),
                    thickness = 0.5.dp
                )
                Text(
                    text = "  ✦   ✦   ✦  ",
                    fontSize = 10.sp,
                    color = HiddenForest.Accent.copy(alpha = 0.70f),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = textColor.copy(alpha = 0.22f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
fun ParagraphItem(
    text: String,
    fontFamily: FontFamily,
    textAlign: TextAlign,
    lineSpacing: Float,
    fontSizeSp: Float,
    textColor: Color,
    isHighlighted: Boolean,
    currentTtsSentence: String,
    hyphenation: Boolean = true,
    ttsHighlightStyle: TtsHighlightStyle = TtsHighlightStyle.BACKGROUND_YELLOW,
    theme: com.example.woodsreader.model.ReaderTheme = com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = theme != com.example.woodsreader.model.ReaderTheme.DAY && theme != com.example.woodsreader.model.ReaderTheme.SEPIA
    val highlightSpanStyle = remember(ttsHighlightStyle, textColor, isDarkTheme) {
        when (ttsHighlightStyle) {
            TtsHighlightStyle.BACKGROUND_YELLOW -> SpanStyle(
                color = if (isDarkTheme) Color(0xFFFFD600) else Color(0xFF9E6500),
                fontWeight = FontWeight.SemiBold
            )
            TtsHighlightStyle.BACKGROUND_ACCENT -> SpanStyle(
                color = if (isDarkTheme) Color(0xFF2ECC71) else Color(0xFF1B663E),
                fontWeight = FontWeight.SemiBold
            )
            TtsHighlightStyle.BACKGROUND_BLUE -> SpanStyle(
                color = if (isDarkTheme) Color(0xFF2196F3) else Color(0xFF0D47A1),
                fontWeight = FontWeight.SemiBold
            )
            TtsHighlightStyle.BACKGROUND_ORANGE -> SpanStyle(
                color = if (isDarkTheme) Color(0xFFFF7A45) else Color(0xFFB03A00),
                fontWeight = FontWeight.SemiBold
            )
            TtsHighlightStyle.UNDERLINE -> SpanStyle(
                textDecoration = TextDecoration.Underline
            )
            TtsHighlightStyle.NONE -> SpanStyle()
        }
    }

    val annotatedText = remember(text, isHighlighted, currentTtsSentence, highlightSpanStyle, ttsHighlightStyle) {
        if (isHighlighted && currentTtsSentence.isNotEmpty() && ttsHighlightStyle != TtsHighlightStyle.NONE) {
            val idx = text.indexOf(currentTtsSentence, ignoreCase = true)
            if (idx != -1) {
                buildAnnotatedString {
                    if (idx > 0) append(text.substring(0, idx))
                    withStyle(highlightSpanStyle) {
                        append(text.substring(idx, idx + currentTtsSentence.length))
                    }
                    if (idx + currentTtsSentence.length < text.length) {
                        append(text.substring(idx + currentTtsSentence.length))
                    }
                }
            } else {
                buildAnnotatedString {
                    withStyle(highlightSpanStyle) {
                        append(text)
                    }
                }
            }
        } else {
            buildAnnotatedString { append(text) }
        }
    }

    val isDialogueOrQuote = remember(text) {
        val trimmed = text.trim()
        trimmed.startsWith("—") || trimmed.startsWith("–") || trimmed.startsWith("-") ||
        trimmed.startsWith("\"") || trimmed.startsWith("“") || trimmed.startsWith("«")
    }

    val textIndent = remember(textAlign, fontSizeSp, isDialogueOrQuote) {
        if (textAlign == TextAlign.Justify && !isDialogueOrQuote) {
            TextIndent(firstLine = (fontSizeSp * 1.1f).sp)
        } else {
            TextIndent.None
        }
    }

    val textStyle = remember(textIndent, hyphenation) {
        TextStyle(
            textIndent = textIndent,
            hyphens = if (hyphenation) Hyphens.Auto else Hyphens.None
        )
    }

    Text(
        text = annotatedText,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.35f * lineSpacing).sp,
        textAlign = textAlign,
        fontFamily = fontFamily,
        color = if (isHighlighted && ttsHighlightStyle != TtsHighlightStyle.NONE && ttsHighlightStyle != TtsHighlightStyle.UNDERLINE) {
            when (ttsHighlightStyle) {
                TtsHighlightStyle.BACKGROUND_YELLOW -> if (isDarkTheme) Color(0xFFFFD600) else Color(0xFF9E6500)
                TtsHighlightStyle.BACKGROUND_ACCENT -> if (isDarkTheme) Color(0xFF2ECC71) else Color(0xFF1B663E)
                TtsHighlightStyle.BACKGROUND_BLUE -> if (isDarkTheme) Color(0xFF2196F3) else Color(0xFF0D47A1)
                TtsHighlightStyle.BACKGROUND_ORANGE -> if (isDarkTheme) Color(0xFFFF7A45) else Color(0xFFB03A00)
                else -> textColor
            }
        } else textColor,
        style = textStyle,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}

@Composable
fun ReflowImageItem(item: ReflowItem.Image, onZoom: (ByteArray) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageHash = remember(item.imageBytes) { item.imageBytes.hashCode() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        coil3.compose.AsyncImage(
            model = coil3.request.ImageRequest.Builder(context)
                .data(item.imageBytes)
                .memoryCacheKey("reflow-img-${item.pageNumber}-$imageHash")
                .diskCacheKey("reflow-img-${item.pageNumber}-$imageHash")
                .size(720, 960)
                .crossfade(true)
                .build(),
            placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
            error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
            contentDescription = "Ilustración de la página ${item.pageNumber}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onZoom(item.imageBytes) }
        )
    }
}

@Composable
fun ZoomableImageDialog(
    imageBytes: ByteArray,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    fun getBounds(currentScale: Float): Pair<Float, Float> {
        val w = screenSize.width.toFloat()
        val h = screenSize.height.toFloat()
        val scaledW = currentScale * w
        val scaledH = currentScale * h
        val maxX = ((scaledW - w) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - h) / 2f).coerceAtLeast(0f)
        return maxX to maxY
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        coroutineScope.launch {
            val newScale = (scale.value * zoomChange).coerceIn(1f, 4f)
            scale.snapTo(newScale)
            val (maxX, maxY) = getBounds(newScale)
            val targetX = (offsetX.value + panChange.x * newScale).coerceIn(-maxX, maxX)
            val targetY = (offsetY.value + panChange.y * newScale).coerceIn(-maxY, maxY)
            offsetX.snapTo(targetX)
            offsetY.snapTo(targetY)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.Black.copy(alpha = 0.95f),
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { screenSize = it }
        ) {
            Box(contentAlignment = Alignment.Center) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val imageHash = remember(imageBytes) { imageBytes.hashCode() }
                coil3.compose.AsyncImage(
                    model = coil3.request.ImageRequest.Builder(context)
                        .data(imageBytes)
                        .memoryCacheKey("zoom-img-$imageHash")
                        .diskCacheKey("zoom-img-$imageHash")
                        .size(1080, 1920)
                        .build(),
                    placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            translationX = offsetX.value
                            translationY = offsetY.value
                        }
                        .transformable(state = transformableState)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    coroutineScope.launch {
                                        if (scale.value > 1.1f) {
                                            launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                            launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                            launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        } else {
                                            launch { scale.animateTo(2.5f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        }
                                    }
                                }
                            )
                        }
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private fun getItemKey(item: ReflowItem, index: Int): String {
    val base = if (item.id.isNotEmpty()) {
        item.id
    } else {
        when (item) {
            is ReflowItem.Header -> "hdr_${item.pageNumber}_${item.content.hashCode()}"
            is ReflowItem.Paragraph -> "par_${item.pageNumber}_${item.content.hashCode()}"
            is ReflowItem.PageNumber -> "pg_${item.pageNumber}_${item.number.hashCode()}"
            is ReflowItem.Image -> "img_${item.pageNumber}_${item.imageBytes.size}"
            is ReflowItem.Ignored -> "ign_${item.pageNumber}_${item.content.hashCode()}"
            is ReflowItem.Text -> "txt_${item.element.hashCode()}"
        }
    }
    return "${base}_$index"
}
