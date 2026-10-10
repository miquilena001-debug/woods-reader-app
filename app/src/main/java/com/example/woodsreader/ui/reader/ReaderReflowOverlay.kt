package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.ExperimentalReadiumApi
import com.example.woodsreader.data.model.ReflowItem
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import coil3.request.crossfade
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler

/**
 * Premium Reflow View for PDFs.
 * Transforms static pages into fluid "Liquid Glass" reading sanctuary.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderReflowOverlay(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    reflowState: com.example.woodsreader.data.model.ReflowState,
    ttsActiveBlockIndex: Int = -1,
    fontScale: Float,
    hazeState: HazeState,
    isLoadingMore: Boolean = false,
    ttsLocator: Locator? = null,
    isTtsPlaying: Boolean = false,
    currentTtsSentence: String = "",
    preferences: com.example.woodsreader.data.datastore.UserPreferences,
    onLoadMore: () -> Unit = {},
    onLoadPrevious: () -> Unit = {},
    onScroll: (Int, Boolean) -> Unit = { _, _ -> },

    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrev: () -> Unit = {},
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val items = reflowState.items
    var isControlsVisible by remember { mutableStateOf(true) }
    
    // Precalculate readable items and element indices to keep rendering at O(1) complexity
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
    
    // M-3.2 / 4.1: Auto-scroll al TTS
    LaunchedEffect(ttsActiveBlockIndex, preferences.ttsAutoScroll) {
        if (preferences.ttsAutoScroll && ttsActiveBlockIndex >= 0) {
            val targetItem = readableItems.getOrNull(ttsActiveBlockIndex)
            if (targetItem != null) {
                val indexInAllItems = items.indexOf(targetItem)
                if (indexInAllItems != -1 && !listState.isScrollInProgress) {
                    listState.animateScrollToItem(indexInAllItems)
                }
            }
        }
    }
    
    // M-2.2: Sincronización de scroll (Reflow -> PDF) + Pausa de TTS
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    
    LaunchedEffect(firstVisibleIndex, isScrolling) {
        if (isVisible && items.isNotEmpty()) {
            onScroll(firstVisibleIndex, isScrolling)
        }
    }
    
    // DETECCIÓN DE SCROLL (Infinite Scroll con Prefetch)
    LaunchedEffect(listState, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && !isLoadingMore && items.isNotEmpty()) {
                    val lastVisibleIndex = visibleItems.last().index
                    val firstVisibleIndexLocal = visibleItems.first().index
                    
                    if (lastVisibleIndex >= items.size - 3) {
                        onLoadMore()
                    }
                    if (firstVisibleIndexLocal <= 2) {
                        onLoadPrevious()
                    }
                }

            }
    }

    // Capture the Android back button when the overlay is open so we don't exit the whole reader
    BackHandler(enabled = isVisible) {
        onDismiss()
    }

    var zoomedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    val theme = preferences.theme
    val (bgColor, textColor) = remember(theme) {
        when (theme) {
            com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST -> Color(0xFF0A0D0A) to Color(0xFFD4E8D4)
            com.example.woodsreader.model.ReaderTheme.DAY -> Color(0xFFFAFAFA) to Color(0xFF1A1A1A)
            com.example.woodsreader.model.ReaderTheme.SEPIA -> Color(0xFFFBF0D9) to Color(0xFF5B4636)
            com.example.woodsreader.model.ReaderTheme.AMOLED -> Color(0xFF000000) to Color(0xFFE0E0E0)
            com.example.woodsreader.model.ReaderTheme.SUNSET -> Color(0xFF1A0D08) to Color(0xFFEDD5C0)
        }
    }
    
    val composeFontFamily = remember(preferences.fontFamily) {
        when (preferences.fontFamily) {
            "serif", "lora" -> FontFamily.Serif
            "sans-serif", "inter" -> FontFamily.SansSerif
            "monospace", "jetbrains mono" -> FontFamily.Monospace
            else -> FontFamily.Serif
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
        enter = scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(380, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(280)),
        exit = scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior de estado móvil
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MODO MÓVIL",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            color = HiddenForest.Accent
                        )
                        Text(
                            text = "Lectura Fluida Optimizada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiddenForest.TextLow
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                }

                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = HiddenForest.Accent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when {
                                    reflowState.currentLoadingPage == -1 -> "Abriendo archivo PDF..."
                                    reflowState.currentLoadingPage > 0 -> "Analizando página ${reflowState.currentLoadingPage}..."
                                    else -> "Preparando lectura fluida..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiddenForest.TextLow
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = items,
                            key = { item -> getItemKey(item) }
                        ) { item ->
                            when (item) {
                                is ReflowItem.Header -> {
                                    val itemIndex = readableItemToIndex[item] ?: -1
                                    val isHighlighted = itemIndex == ttsActiveBlockIndex
                                    
                                    HeaderItem(
                                        text = item.content,
                                        level = item.level,
                                        fontFamily = composeFontFamily,
                                        textColor = textColor,
                                        isHighlighted = isHighlighted,
                                        currentTtsSentence = currentTtsSentence,
                                        modifier = Modifier.fillParentMaxWidth().background(if (isHighlighted) com.example.woodsreader.ui.theme.HiddenForest.AccentGlow.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(4.dp))
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
                                        textColor = textColor,
                                        isHighlighted = isHighlighted,
                                        currentTtsSentence = currentTtsSentence,
                                        modifier = Modifier.fillParentMaxWidth().background(if (isHighlighted) com.example.woodsreader.ui.theme.HiddenForest.AccentGlow.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(4.dp))
                                    )
                                }
                                is ReflowItem.PageNumber -> {
                                }
                                is ReflowItem.Image -> {
                                    ReflowImageItem(
                                        item = item,
                                        onZoom = { zoomedImageBytes = it }
                                    )
                                }
                                is ReflowItem.Ignored -> {
                                }
                                is ReflowItem.Text -> {
                                    val element = item.element
                                    val textContent = element.locator.text.highlight
                                    if (!textContent.isNullOrBlank()) {
                                        val itemIndex = elementToTextIndex[element] ?: -1
                                        val isHighlighted = itemIndex == ttsActiveBlockIndex

                                        var itemVisible by remember { mutableStateOf(false) }
                                        LaunchedEffect(Unit) {
                                            kotlinx.coroutines.delay((itemIndex * 30L).coerceAtMost(450L))
                                            itemVisible = true
                                        }

                                        AnimatedVisibility(
                                            visible = itemVisible,
                                            enter = fadeIn(tween(300)) + slideInVertically(
                                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                                            ) { 20 }
                                        ) {
                                            Text(
                                                text = textContent,
                                                fontSize = (18 * fontScale).sp,
                                                lineHeight = (28 * fontScale * preferences.lineSpacingMultiplier).sp,
                                                color = if (isHighlighted) HiddenForest.Accent else textColor,
                                                fontFamily = composeFontFamily,
                                                textAlign = composeTextAlign,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isHighlighted) HiddenForest.AccentGlow.copy(alpha = 0.1f)
                                                        else Color.Transparent,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = HiddenForest.Accent,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, bgColor)
                        )
                    )
            )

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(32.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .height(80.dp)
                        .padding(horizontal = 24.dp)
                        .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        TextButton(onClick = { onSpeedChange(if (currentSpeed >= 2.0f) 0.5f else currentSpeed + 0.25f) }) {
                            Text(
                                text = "${currentSpeed}x",
                                color = HiddenForest.Accent,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        IconButton(onClick = onSkipPrev) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White)
                        }

                        FloatingActionButton(
                            onClick = onPlayPause,
                            containerColor = HiddenForest.Accent,
                            contentColor = Color.Black,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }

                        IconButton(onClick = onSkipNext) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White)
                        }

                        if (isTtsPlaying) {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.width(40.dp).clip(CircleShape),
                                color = HiddenForest.Accent,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
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
fun ReflowImageItem(item: ReflowItem.Image, onZoom: (ByteArray) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageHash = remember(item.imageBytes) { item.imageBytes.hashCode() }
    coil3.compose.AsyncImage(
        model = coil3.request.ImageRequest.Builder(context)
            .data(item.imageBytes)
            .memoryCacheKey("reflow-img-${item.pageNumber}-$imageHash")
            .diskCacheKey("reflow-img-${item.pageNumber}-$imageHash")
            .size(480, 640)
            .crossfade(true)
            .build(),
        placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
        error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
        contentDescription = "Imagen PDF",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 320.dp)
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onZoom(item.imageBytes) }
            .background(Color.White.copy(alpha = 0.05f))
    )
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
private fun getItemKey(item: ReflowItem): Any {
    return when (item) {
        is ReflowItem.Header -> "header_${item.pageNumber}_${item.content.hashCode()}"
        is ReflowItem.Paragraph -> "para_${item.pageNumber}_${item.content.hashCode()}"
        is ReflowItem.PageNumber -> "page_${item.pageNumber}"
        is ReflowItem.Image -> "img_${item.pageNumber}_${item.imageBytes.size}"
        is ReflowItem.Ignored -> "ignore_${item.pageNumber}"
        is ReflowItem.Text -> "text_${item.element.locator.href}_${item.element.locator.locations.progression ?: 0.0}_${item.element.hashCode()}"
    }
}

@Composable
fun HeaderItem(
    text: String,
    level: Int,
    fontFamily: FontFamily,
    textColor: Color,
    isHighlighted: Boolean,
    currentTtsSentence: String,
    modifier: Modifier = Modifier
) {
    val typography = MaterialTheme.typography
    val headerStyle = when (level) {
        1 -> typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = fontFamily)
        2 -> typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = fontFamily)
        3 -> typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontFamily = fontFamily)
        else -> typography.titleSmall.copy(fontWeight = FontWeight.Normal, fontFamily = fontFamily)
    }

    val annotatedText = remember(text, isHighlighted, currentTtsSentence, textColor) {
        if (isHighlighted && currentTtsSentence.isNotEmpty()) {
            val idx = text.indexOf(currentTtsSentence, ignoreCase = true)
            if (idx != -1) {
                buildAnnotatedString {
                    if (idx > 0) append(text.substring(0, idx))
                    withStyle(
                        SpanStyle(
                            color = HiddenForest.Accent,
                            fontWeight = FontWeight.Bold,
                            background = HiddenForest.AccentGlow.copy(alpha = 0.15f)
                        )
                    ) {
                        append(text.substring(idx, idx + currentTtsSentence.length))
                    }
                    if (idx + currentTtsSentence.length < text.length) {
                        append(text.substring(idx + currentTtsSentence.length))
                    }
                }
            } else {
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = HiddenForest.Accent,
                            fontWeight = FontWeight.Bold,
                            background = HiddenForest.AccentGlow.copy(alpha = 0.15f)
                        )
                    ) {
                        append(text)
                    }
                }
            }
        } else {
            buildAnnotatedString { append(text) }
        }
    }

    Text(
        text = annotatedText,
        style = headerStyle,
        color = if (isHighlighted && currentTtsSentence.isEmpty()) HiddenForest.Accent else textColor,
        modifier = modifier
            .padding(top = if (level == 1) 24.dp else 16.dp, bottom = 8.dp)
    )
}

@Composable
fun ParagraphItem(
    text: String,
    fontFamily: FontFamily,
    textAlign: TextAlign,
    lineSpacing: Float,
    textColor: Color,
    isHighlighted: Boolean,
    currentTtsSentence: String,
    modifier: Modifier = Modifier
) {
    val annotatedText = remember(text, isHighlighted, currentTtsSentence, textColor) {
        if (isHighlighted && currentTtsSentence.isNotEmpty()) {
            val idx = text.indexOf(currentTtsSentence, ignoreCase = true)
            if (idx != -1) {
                buildAnnotatedString {
                    if (idx > 0) append(text.substring(0, idx))
                    withStyle(
                        SpanStyle(
                            color = HiddenForest.Accent,
                            fontWeight = FontWeight.Bold,
                            background = HiddenForest.AccentGlow.copy(alpha = 0.15f)
                        )
                    ) {
                        append(text.substring(idx, idx + currentTtsSentence.length))
                    }
                    if (idx + currentTtsSentence.length < text.length) {
                        append(text.substring(idx + currentTtsSentence.length))
                    }
                }
            } else {
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = HiddenForest.Accent,
                            fontWeight = FontWeight.Bold,
                            background = HiddenForest.AccentGlow.copy(alpha = 0.15f)
                        )
                    ) {
                        append(text)
                    }
                }
            }
        } else {
            buildAnnotatedString { append(text) }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = (1.6 * lineSpacing).em,
            textAlign = textAlign,
            fontFamily = fontFamily
        ),
        color = if (isHighlighted && currentTtsSentence.isEmpty()) HiddenForest.Accent else textColor,
        modifier = modifier
            .padding(vertical = 4.dp)
    )
}
