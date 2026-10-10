package com.example.woodsreader.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.ui.theme.HiddenForest

/** Helper to create a premium shimmer gradient brush in Compose */
@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1000f,
    durationMillis: Int = 1200
): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.15f),
        Color.White.copy(alpha = 0.05f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer_brush")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

// ── Book Card Premium ────────────────────────────────────────────────────────────
/**
 * Tarjeta de libro compacta para la cuadrícula 3 columnas de la biblioteca.
 * Usa SharedElement para la transición hero al abrir el reader.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun WoodsBookCard(
    book: BookEntity,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(
                1.dp,
                when {
                    book.readingProgressPercent > 0 -> HiddenForest.Accent.copy(0.3f)
                    else -> HiddenForest.Outline
                },
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                role = Role.Button
                contentDescription = "Libro: ${book.title}, por ${book.author}. " +
                        "Formato: ${book.format.name}. " +
                        "Progreso: ${book.readingProgressPercent.toInt()}%. " +
                        if (book.readingProgressPercent >= 100) "Terminado" else ""
                onClick { onClick(); true }
            }
    ) {
        // Zona de portada
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.linearGradient(
                        listOf(HiddenForest.Accent.copy(0.18f), HiddenForest.Surface)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val coverFile = remember(book.coverCachePath) {
                book.coverCachePath?.let { java.io.File(it) }?.takeIf { it.exists() && it.length() > 0 }
            }

            if (coverFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverFile)
                        .memoryCacheKey("cover-grid-${book.id}")
                        .diskCacheKey("cover-grid-${book.id}")
                        .crossfade(true)
                        .build(),
                    placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    contentDescription = book.title,
                    onLoading = { isLoading = true },
                    onSuccess = { _ -> isLoading = false },
                    onError = { _ -> isLoading = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                state = rememberSharedContentState(key = "cover-${book.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(rememberShimmerBrush())
                    )
                }
            } else {
                BookInitials(title = book.title, size = 44.dp)
            }

            // Badge de estado
            when {
                book.readingProgressPercent >= 100 -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(HiddenForest.Accent.copy(0.25f))
                            .border(1.dp, HiddenForest.Accent.copy(0.5f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "✓", fontSize = 8.sp,
                            color = HiddenForest.Accent, fontWeight = FontWeight.Bold
                        )
                    }
                }
                book.readingProgressPercent == 0.0f -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(HiddenForest.Amber.copy(0.2f))
                            .border(1.dp, HiddenForest.Amber.copy(0.5f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "NEW", fontSize = 7.sp,
                            color = HiddenForest.Amber, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Información del libro
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = HiddenForest.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = book.author.ifBlank { "Desconocido" },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = HiddenForest.TextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (book.readingProgressPercent > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                WoodsProgressBar(progress = book.readingProgressPercent.toFloat() / 100f)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${book.readingProgressPercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = HiddenForest.TextLow,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ── Book Initials (portada generativa) ──────────────────────────────────────────
/**
 * Genera una portada con las iniciales del título cuando no hay imagen de portada.
 */
@Composable
fun BookInitials(title: String, size: Dp) {
    val initials = title.trim()
        .split(" ")
        .filter { it.length > 2 }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { title.take(2).uppercase() }

    Box(
        modifier = Modifier
            .width(size)
            .height(size * 1.4f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        HiddenForest.Accent.copy(alpha = 0.85f),
                        HiddenForest.AccentDim.copy(alpha = 0.55f)
                    )
                )
            )
            .border(1.dp, HiddenForest.Accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = HiddenForest.Text.copy(alpha = 0.92f)
        )
    }
}

// ── Progress Bar ─────────────────────────────────────────────────────────────────
/**
 * Barra de progreso de lectura con degradado horizontal.
 */
@Composable
fun WoodsProgressBar(progress: Float, color: Color = HiddenForest.Accent) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(HiddenForest.Outline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(HiddenForest.AccentDim, color))
                )
        )
    }
}

// ── Badge ────────────────────────────────────────────────────────────────────────
/**
 * Etiqueta de estado premium (formato, nuevo, terminado, etc.).
 */
@Composable
fun WoodsBadge(text: String, color: Color = HiddenForest.Accent) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = color
        )
    }
}

// ── Book Skeleton ────────────────────────────────────────────────────────────────
/**
 * Tarjeta esqueleto (shimmer) que se muestra mientras se carga la biblioteca.
 */
@Composable
fun WoodsBookSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(1.dp, HiddenForest.Outline, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(HiddenForest.Surface),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = HiddenForest.Accent,
                strokeWidth = 2.dp
            )
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(10.dp)
                    .background(HiddenForest.Outline, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(8.dp)
                    .background(HiddenForest.Surface, RoundedCornerShape(4.dp))
            )
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────────
/**
 * Pantalla de estado vacío cuando no hay libros en la biblioteca.
 */
@Composable
fun EmptyLibrary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📭", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tu biblioteca está vacía",
            style = MaterialTheme.typography.titleMedium,
            color = HiddenForest.TextSecondary
        )
        Text(
            text = "Pulsa «Sincronizar» para buscar libros",
            style = MaterialTheme.typography.bodySmall,
            color = HiddenForest.TextLow
        )
    }
}

// ── Scan Stats Card ──────────────────────────────────────────────────────────────
/**
 * Tarjeta informativa de estado del último escaneo de biblioteca.
 * Se oculta automáticamente si no hay datos de escaneo disponibles.
 */
@Composable
fun ScanStatsCard(
    lastScanTime: Long,
    filesScanned: Int,
    newBooksFound: Int,
    onSyncClick: () -> Unit
) {
    if (lastScanTime == 0L && filesScanned == 0) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (newBooksFound > 0)
                HiddenForest.Surface.copy(alpha = 0.8f)
            else
                HiddenForest.SurfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (newBooksFound > 0) HiddenForest.AccentGlow.copy(alpha = 0.3f)
            else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (newBooksFound > 0) "✨ $newBooksFound libros nuevos" else "Biblioteca al día",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (newBooksFound > 0) HiddenForest.Text else HiddenForest.TextLow
                )
                val timeText = if (lastScanTime == 0L) "Nunca" else {
                    val diff = System.currentTimeMillis() - lastScanTime
                    when {
                        diff < 60000 -> "Justo ahora"
                        diff < 3600000 -> "Hace ${diff / 60000} min"
                        else -> java.text.SimpleDateFormat(
                            "HH:mm", java.util.Locale.getDefault()
                        ).format(java.util.Date(lastScanTime))
                    }
                }
                Text(
                    text = "Último: $timeText • $filesScanned archivos",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextLow.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onSyncClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = HiddenForest.AccentGlow.copy(alpha = 0.1f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sincronizar",
                    tint = HiddenForest.AccentGlow,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Recent Books History BottomSheet ─────────────────────────────────────────────
/**
 * Bottom sheet premium que muestra los últimos 10 libros abiertos.
 * Se invoca con un long-press en el botón central de la barra de navegación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentBooksSheet(
    books: List<BookEntity>,
    onBookClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HiddenForest.Surface,
        dragHandle = {
            // Handle visual premium
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(HiddenForest.Outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Encabezado
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HISTORIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Leídos recientemente",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(HiddenForest.SurfaceVariant)
                        .clickable { onDismiss() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar historial",
                        tint = HiddenForest.TextLow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (books.isEmpty()) {
                // Estado vacío
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📖", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aún no has abierto ningún libro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                        RecentBookRow(
                            book = book,
                            rank = index + 1,
                            onClick = {
                                onDismiss()
                                onBookClick(book.id)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

/** Fila individual de un libro en el historial. */
@Composable
private fun RecentBookRow(book: BookEntity, rank: Int, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(1.dp, HiddenForest.Outline, RoundedCornerShape(14.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Número de posición
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (rank == 1) HiddenForest.Accent.copy(0.2f)
                    else HiddenForest.Surface
                )
                .border(
                    1.dp,
                    if (rank == 1) HiddenForest.Accent.copy(0.5f) else HiddenForest.Outline,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = if (rank == 1) HiddenForest.Accent else HiddenForest.TextLow
            )
        }

        // Miniatura de portada
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        listOf(HiddenForest.Accent.copy(0.3f), HiddenForest.SurfaceVariant)
                    )
                )
                .border(1.dp, HiddenForest.Accent.copy(0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (book.coverCachePath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(book.coverCachePath!!))
                        .memoryCacheKey("cover-hist-${book.id}")
                        .diskCacheKey("cover-hist-${book.id}")
                        .size(70, 90)
                        .crossfade(false)
                        .build(),
                    placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = book.title.take(2).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    ),
                    color = HiddenForest.Accent
                )
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = HiddenForest.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author.ifBlank { "Desconocido" },
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (book.readingProgressPercent > 0f) {
                WoodsProgressBar(progress = book.readingProgressPercent / 100f)
            }
        }

        // Tiempo desde última apertura + progreso %
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            book.lastOpenedAt?.let { ts ->
                val ago = System.currentTimeMillis() - ts
                val label = when {
                    ago < 60_000L      -> "ahora"
                    ago < 3_600_000L   -> "${ago / 60_000} min"
                    ago < 86_400_000L  -> "${ago / 3_600_000} h"
                    else               -> "${ago / 86_400_000} d"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = HiddenForest.TextLow
                )
            }
            if (book.readingProgressPercent > 0f) {
                Text(
                    text = "${book.readingProgressPercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = HiddenForest.Accent
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = HiddenForest.Outline,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── Bottom Navigation Premium (Liquid Glass) ──────────────────────────────────────
/**
 * Barra de navegación inferior con estilo "Liquid Glass".
 * El botón central (Reader) tiene un tratamiento visual destacado.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WoodsBottomNavigation(
    currentRoute: String?,
    lastBookId: String?,
    recentBooks: List<BookEntity> = emptyList(),
    onNavigate: (String) -> Unit,
    onHistoryBookClick: (String) -> Unit = onNavigate
) {
    var showHistory by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Mostrar el historial si se solicita
    if (showHistory) {
        RecentBooksSheet(
            books = recentBooks,
            onBookClick = onHistoryBookClick,
            onDismiss = { showHistory = false }
        )
    }
    val navItems = listOf(
        Triple("library",  androidx.compose.material.icons.Icons.Default.Home,     "Inicio"),
        Triple("search",   androidx.compose.material.icons.Icons.Default.Search,   "Buscar"),
        Triple("reader",   androidx.compose.material.icons.Icons.Default.MenuBook, "Leer"),
        Triple("stats",    androidx.compose.material.icons.Icons.Default.BarChart, "Stats"),
        Triple("settings", androidx.compose.material.icons.Icons.Default.Settings, "Ajustes"),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            HiddenForest.SurfaceVariant.copy(alpha = 0.85f),
                            Color(0xCC050705)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (route, icon, label) ->
                val isActive = currentRoute == route
                val isCenter = index == 2

                Box(
                    modifier = Modifier
                        .weight(if (isCenter) 1.2f else 1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(if (isCenter) 24.dp else 16.dp))
                        .background(
                            when {
                                isCenter -> androidx.compose.ui.graphics.SolidColor(HiddenForest.Accent)
                                isActive -> androidx.compose.ui.graphics.SolidColor(HiddenForest.Accent.copy(alpha = 0.15f))
                                else -> androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                            }
                        )
                        .then(
                            if (isCenter) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        lastBookId?.let { onNavigate("reader/$it") }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showHistory = true
                                    }
                                )
                            } else {
                                Modifier.clickable { onNavigate(route) }
                            }
                        )
                        .padding(vertical = if (isCenter) 12.dp else 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = when {
                                isCenter -> Color.White
                                isActive -> HiddenForest.Accent
                                else -> HiddenForest.TextLow
                            },
                            modifier = Modifier.size(if (isCenter) 26.dp else 22.dp)
                        )
                        if (isCenter) {
                            // Pista visual del long-press para historial
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Historial",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isActive) HiddenForest.Accent else HiddenForest.TextLow
                            )
                        }
                    }
                }
            }
        }
    }
}

