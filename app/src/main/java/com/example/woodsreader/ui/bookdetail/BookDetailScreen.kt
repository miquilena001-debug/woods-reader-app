package com.example.woodsreader.ui.bookdetail

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.ReadingSessionEntity
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.ui.theme.HiddenForest
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BookDetailScreen(
    onNavigateUp: () -> Unit,
    onOpenReader: (bookId: String) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HiddenForest.Background)
    ) {
        when {
            uiState.isLoading -> BookDetailLoadingScreen()
            uiState.error != null -> BookDetailErrorScreen(
                error = uiState.error!!,
                onBack = onNavigateUp
            )
            uiState.book != null -> BookDetailContent(
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                onOpenReader = { onOpenReader(uiState.book!!.id) },
                onUpdateStatus = viewModel::updateReadingStatus,
                onUpdateRating = viewModel::updateRating,
                onUpdateNotes = viewModel::updateUserNotes,
                onGenerateSynopsis = viewModel::generateSynopsis,
                onDeleteHighlight = viewModel::deleteHighlight,
                onDeleteBookmark = viewModel::deleteBookmark,
                onShareHighlights = { shareHighlights(context, uiState.book!!, uiState.highlights) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailContent(
    uiState: BookDetailUiState,
    onNavigateUp: () -> Unit,
    onOpenReader: () -> Unit,
    onUpdateStatus: (ReadingStatus) -> Unit,
    onUpdateRating: (Int?) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onGenerateSynopsis: () -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onShareHighlights: () -> Unit
) {
    val book = uiState.book!!
    var selectedTab by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    // Header parallax effect
    val headerCollapse by remember { derivedStateOf { (scrollState.value / 400f).coerceIn(0f, 1f) } }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ── Book Cover Header ─────────────────────────────────────────────
            CoverHeroSection(
                book = book,
                onOpenReader = onOpenReader
            )

            // ── Metadata Card ─────────────────────────────────────────────────
            MetadataSection(
                book = book,
                stats = uiState.stats,
                isGeneratingSynopsis = uiState.isGeneratingSynopsis,
                onUpdateStatus = onUpdateStatus,
                onUpdateRating = onUpdateRating,
                onUpdateNotes = onUpdateNotes,
                onGenerateSynopsis = onGenerateSynopsis
            )

            // ── Tabbed Content ────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            TabbedAnnotations(
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
                highlights = uiState.highlights,
                bookmarks = uiState.bookmarks,
                sessions = uiState.sessions,
                stats = uiState.stats,
                onDeleteHighlight = onDeleteHighlight,
                onDeleteBookmark = onDeleteBookmark,
                onShareHighlights = onShareHighlights
            )
            Spacer(modifier = Modifier.height(120.dp))
        }

        // ── Top Bar (overlay) ────────────────────────────────────────────────
        DetailTopBar(
            title = book.title,
            collapse = headerCollapse,
            onBack = onNavigateUp
        )

        // ── FAB ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 24.dp, bottom = 32.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            ReadNowFab(
                progressPercent = book.readingProgressPercent,
                onClick = onOpenReader
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cover Hero Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoverHeroSection(
    book: BookEntity,
    onOpenReader: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        val coverFile = remember(book.coverCachePath) {
            book.coverCachePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
        }

        // Blurred background cover
        if (coverFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverFile)
                    .memoryCacheKey("cover-detail-blur-${book.id}")
                    .diskCacheKey("cover-detail-blur-${book.id}")
                    .crossfade(true)
                    .build(),
                placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Simulate blur via heavy scaling + alpha for pre-API31 compat
                        scaleX = 1.15f
                        scaleY = 1.15f
                        alpha = 0.55f
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                HiddenForest.AccentContainer,
                                HiddenForest.Background
                            )
                        )
                    )
            )
        }

        // Gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x88080B08),
                        0.5f to Color(0xCC080B08),
                        1f to HiddenForest.Background
                    )
                )
        )

        // Cover + info row
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 80.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Book cover card
            BookCoverCard(book = book, modifier = Modifier.size(width = 110.dp, height = 160.dp))

            // Title + author info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (book.format.name != "UNKNOWN") {
                    Surface(
                        color = HiddenForest.AccentContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = book.format.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.Accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    ),
                    color = HiddenForest.Text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotEmpty()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextSecondary
                    )
                }

                // Progress pill
                if (book.readingProgressPercent > 0f) {
                    val pct = (book.readingProgressPercent * 100).roundToInt()
                    LinearProgressIndicatorPill(progress = book.readingProgressPercent, pct = pct)
                }
            }
        }
    }
}

@Composable
private fun LinearProgressIndicatorPill(progress: Float, pct: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Progreso",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextLow
            )
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = HiddenForest.Accent
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = HiddenForest.Accent,
            trackColor = HiddenForest.Outline
        )
    }
}

@Composable
private fun BookCoverCard(book: BookEntity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .ambientGlow(HiddenForest.Accent, radius = 24.dp, alpha = 0.3f)
    ) {
        val coverFile = remember(book.coverCachePath) {
            book.coverCachePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
        }

        if (coverFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverFile)
                    .memoryCacheKey("cover-detail-${book.id}")
                    .diskCacheKey("cover-detail-${book.id}")
                    .crossfade(true)
                    .build(),
                placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(HiddenForest.AccentContainer, HiddenForest.SurfaceVariant)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = HiddenForest.Accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        // Sheen overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.4f to Color.Transparent
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Metadata Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetadataSection(
    book: BookEntity,
    stats: BookStats,
    isGeneratingSynopsis: Boolean,
    onUpdateStatus: (ReadingStatus) -> Unit,
    onUpdateRating: (Int?) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onGenerateSynopsis: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status + Rating row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReadingStatusSelector(
                current = book.readingStatus,
                onSelect = onUpdateStatus
            )
            StarRating(
                rating = book.userRating,
                onRatingChange = onUpdateRating
            )
        }

        // Metadata grid
        MetadataGrid(book = book, stats = stats)

        // Description / Synopsis
        DescriptionCard(
            description = book.description,
            isGenerating = isGeneratingSynopsis,
            onGenerate = onGenerateSynopsis
        )

        // User notes
        UserNotesCard(
            notes = book.userNotes,
            onSave = onUpdateNotes
        )
    }
}

@Composable
private fun ReadingStatusSelector(current: ReadingStatus, onSelect: (ReadingStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val (icon, label, color) = when (current) {
        ReadingStatus.NOT_STARTED -> Triple(Icons.Default.RadioButtonUnchecked, "Sin empezar", HiddenForest.TextLow)
        ReadingStatus.IN_PROGRESS -> Triple(Icons.Default.PlayArrow, "Leyendo", HiddenForest.Accent)
        ReadingStatus.COMPLETED   -> Triple(Icons.Default.CheckCircle, "Completado", Color(0xFF4CAF72))
        ReadingStatus.ABANDONED   -> Triple(Icons.Default.Cancel, "Abandonado", Color(0xFFCF6679))
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(HiddenForest.SurfaceVariant)
                .border(1.dp, HiddenForest.Outline, RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = HiddenForest.TextLow, modifier = Modifier.size(14.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = HiddenForest.Surface
        ) {
            ReadingStatus.entries.forEach { status ->
                val (sIcon, sLabel, sColor) = when (status) {
                    ReadingStatus.NOT_STARTED -> Triple(Icons.Default.RadioButtonUnchecked, "Sin empezar", HiddenForest.TextLow)
                    ReadingStatus.IN_PROGRESS -> Triple(Icons.Default.PlayArrow, "Leyendo", HiddenForest.Accent)
                    ReadingStatus.COMPLETED   -> Triple(Icons.Default.CheckCircle, "Completado", Color(0xFF4CAF72))
                    ReadingStatus.ABANDONED   -> Triple(Icons.Default.Cancel, "Abandonado", Color(0xFFCF6679))
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(sIcon, contentDescription = null, tint = sColor, modifier = Modifier.size(16.dp))
                            Text(sLabel, color = HiddenForest.Text)
                        }
                    },
                    onClick = { onSelect(status); expanded = false },
                    leadingIcon = null
                )
            }
        }
    }
}

@Composable
private fun StarRating(rating: Int?, onRatingChange: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            Icon(
                imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "$star estrellas",
                tint = if (filled) HiddenForest.Amber else HiddenForest.TextLow,
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        onRatingChange(if (rating == star) null else star)
                    }
            )
        }
    }
}

@Composable
private fun MetadataGrid(book: BookEntity, stats: BookStats) {
    val fileSizeStr = formatFileSize(book.fileSizeBytes)
    val totalTimeStr = formatDuration(stats.totalTimeMs)

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MetadataCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AccessTime,
                    label = "Tiempo total",
                    value = totalTimeStr
                )
                VerticalDivider(modifier = Modifier.height(56.dp), color = HiddenForest.Outline)
                MetadataCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speed,
                    label = "WPM promedio",
                    value = if (stats.averageWpm > 0) "${stats.averageWpm.roundToInt()}" else "—"
                )
            }
            HorizontalDivider(color = HiddenForest.Outline)
            Row(modifier = Modifier.fillMaxWidth()) {
                MetadataCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.FolderOpen,
                    label = "Tamaño",
                    value = fileSizeStr
                )
                VerticalDivider(modifier = Modifier.height(56.dp), color = HiddenForest.Outline)
                MetadataCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.BarChart,
                    label = "Sesiones",
                    value = "${stats.sessionCount}"
                )
            }
            if (book.chapterCount > 0 || book.estimatedWordCount > 0) {
                HorizontalDivider(color = HiddenForest.Outline)
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (book.chapterCount > 0) {
                        MetadataCell(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Layers,
                            label = "Capítulos",
                            value = "${book.chapterCount}"
                        )
                    }
                    if (book.estimatedWordCount > 0) {
                        if (book.chapterCount > 0) {
                            VerticalDivider(modifier = Modifier.height(56.dp), color = HiddenForest.Outline)
                        }
                        MetadataCell(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Palabras est.",
                            value = formatWordCount(book.estimatedWordCount)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HiddenForest.Accent.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextLow
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = HiddenForest.Text
            )
        }
    }
}

@Composable
private fun DescriptionCard(
    description: String,
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sinopsis",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    color = HiddenForest.TextLow
                )
                // Botón regenerar (siempre visible, por si el usuario quiere actualizarla)
                if (!isGenerating) {
                    IconButton(
                        onClick = onGenerate,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Generar sinopsis",
                            tint = HiddenForest.Accent.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            when {
                isGenerating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = HiddenForest.Accent
                        )
                        Text(
                            text = "Analizando contenido del libro\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextSecondary
                        )
                    }
                }
                description.isBlank() -> {
                    Text(
                        text = "Este libro no tiene sinopsis en sus metadatos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextLow
                    )
                    TextButton(
                        onClick = onGenerate,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = HiddenForest.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Generar desde el contenido",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.Accent
                        )
                    }
                }
                else -> {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
                    )
                    if (description.length > 200) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (expanded) "Ver menos" else "Ver m\u00e1s",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.Accent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserNotesCard(notes: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draftNotes by remember(notes) { mutableStateOf(notes) }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis notas",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    color = HiddenForest.TextLow
                )
                IconButton(
                    onClick = {
                        if (editing) {
                            onSave(draftNotes)
                        }
                        editing = !editing
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (editing) "Guardar" else "Editar",
                        tint = if (editing) HiddenForest.Accent else HiddenForest.TextLow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = draftNotes,
                    onValueChange = { draftNotes = it },
                    placeholder = {
                        Text(
                            "Escribe tus reflexiones sobre este libro…",
                            color = HiddenForest.TextLow
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.Outline,
                        focusedTextColor = HiddenForest.Text,
                        unfocusedTextColor = HiddenForest.Text,
                        cursorColor = HiddenForest.Accent
                    ),
                    minLines = 3
                )
            } else {
                if (draftNotes.isNotEmpty()) {
                    Text(
                        text = draftNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextSecondary
                    )
                } else {
                    Text(
                        text = "Toca el lápiz para añadir notas personales",
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextLow,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tabbed Annotations
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TabbedAnnotations(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>,
    sessions: List<ReadingSessionEntity>,
    stats: BookStats,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onShareHighlights: () -> Unit
) {
    val tabs = listOf(
        Triple("Subrayados", Icons.Default.FormatQuote, highlights.size),
        Triple("Marcadores", Icons.Default.Bookmark, bookmarks.size),
        Triple("Historial", Icons.Default.History, sessions.size)
    )

    Column {
        // Tab row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = HiddenForest.Accent,
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = HiddenForest.Outline) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = HiddenForest.Accent,
                    height = 2.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, (label, icon, count) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabChange(index) },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == index) HiddenForest.Accent else HiddenForest.TextLow
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedTab == index) HiddenForest.Accent else HiddenForest.TextLow
                        )
                        if (count > 0) {
                            Surface(
                                color = if (selectedTab == index) HiddenForest.AccentContainer else HiddenForest.SurfaceVariant,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab == index) HiddenForest.Accent else HiddenForest.TextLow,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab content
        when (selectedTab) {
            0 -> HighlightsTab(
                highlights = highlights,
                onDelete = onDeleteHighlight,
                onShare = onShareHighlights
            )
            1 -> BookmarksTab(
                bookmarks = bookmarks,
                onDelete = onDeleteBookmark
            )
            2 -> SessionsTab(sessions = sessions, stats = stats)
        }
    }
}

@Composable
private fun HighlightsTab(
    highlights: List<HighlightEntity>,
    onDelete: (HighlightEntity) -> Unit,
    onShare: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (highlights.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onShare,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.Accent),
                    border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (highlights.isEmpty()) {
            EmptyTabMessage(
                icon = Icons.Default.FormatQuote,
                message = "Aún no tienes subrayados",
                sub = "Selecciona texto en el lector para guardar fragmentos"
            )
        } else {
            highlights.forEach { highlight ->
                HighlightCard(highlight = highlight, onDelete = onDelete)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HighlightCard(highlight: HighlightEntity, onDelete: (HighlightEntity) -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val accentColor = when (highlight.color.name) {
        "GREEN"  -> Color(0xFF4CAF72)
        "BLUE"   -> Color(0xFF90CAF9)
        "PINK"   -> Color(0xFFF48FB1)
        "RED"    -> Color(0xFFEF9A9A)
        else     -> Color(0xFFFFF176)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accentColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Chapter label
            if (highlight.chapterTitle.isNotEmpty()) {
                Text(
                    text = highlight.chapterTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = HiddenForest.TextLow
                )
            }
            // Highlighted text
            Text(
                text = "\"${highlight.highlightedText}\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = 22.sp
                ),
                color = HiddenForest.Text
            )
            // User note
            if (highlight.note.isNotEmpty()) {
                HorizontalDivider(color = HiddenForest.Outline)
                Text(
                    text = highlight.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = HiddenForest.TextSecondary
                )
            }
            // Footer row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(highlight.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextLow
                )
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar",
                        tint = HiddenForest.TextLow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = HiddenForest.Surface,
            title = { Text("¿Eliminar subrayado?", color = HiddenForest.Text) },
            text = { Text("Esta acción no se puede deshacer.", color = HiddenForest.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(highlight); showDeleteConfirm = false }) {
                    Text("Eliminar", color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = HiddenForest.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun BookmarksTab(bookmarks: List<BookmarkEntity>, onDelete: (BookmarkEntity) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (bookmarks.isEmpty()) {
            EmptyTabMessage(
                icon = Icons.Default.Bookmark,
                message = "Sin marcadores",
                sub = "Toca el ícono de marcador en el lector para guardar posiciones"
            )
        } else {
            bookmarks.forEach { bookmark ->
                BookmarkCard(bookmark = bookmark, onDelete = onDelete)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BookmarkCard(bookmark: BookmarkEntity, onDelete: (BookmarkEntity) -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = HiddenForest.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = bookmark.label.ifEmpty { "Marcador sin título" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.Text
                    )
                    Text(
                        text = formatDate(bookmark.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow
                    )
                }
            }
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = HiddenForest.TextLow,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = HiddenForest.Surface,
            title = { Text("¿Eliminar marcador?", color = HiddenForest.Text) },
            confirmButton = {
                TextButton(onClick = { onDelete(bookmark); showDeleteConfirm = false }) {
                    Text("Eliminar", color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = HiddenForest.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SessionsTab(sessions: List<ReadingSessionEntity>, stats: BookStats) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stats.sessionCount > 0) {
            // Card 1: Streaks & Current Week Checklist
            StreakDashboardCard(stats = stats)

            // Card 2: Weekly Activity Bar Chart
            WeeklyActivityChart(last7DaysMinutes = stats.last7DaysMinutes)

            // Card 3: Reading Style Distribution (Manual vs. TTS)
            ReadingStyleDistribution(stats = stats)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Historial de Sesiones",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = HiddenForest.TextLow
            )
        }

        val completedSessions = sessions.filter { it.endAt != null }
        if (completedSessions.isEmpty()) {
            EmptyTabMessage(
                icon = Icons.Default.History,
                message = "Sin sesiones registradas",
                sub = "Las sesiones de lectura aparecerán aquí"
            )
        } else {
            completedSessions.forEach { session ->
                SessionCard(session = session)
            }
        }
    }
}

@Composable
private fun StreakDashboardCard(stats: BookStats) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 32.sp
                    )
                    Column {
                        Text(
                            text = "${stats.currentStreak} días",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = HiddenForest.Text
                        )
                        Text(
                            text = "Racha de lectura actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextSecondary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stats.maxStreak} días",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = HiddenForest.Text
                    )
                    Text(
                        text = "Racha máxima",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow
                    )
                }
            }

            HorizontalDivider(color = HiddenForest.Outline)

            Text(
                text = "Semana Actual",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                color = HiddenForest.TextSecondary
            )

            val daysOfWeek = listOf("L", "M", "M", "J", "V", "S", "D")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                daysOfWeek.forEachIndexed { index, name ->
                    val dayNum = index + 1
                    val isActive = stats.activeDaysThisWeek.contains(dayNum)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) HiddenForest.Accent else HiddenForest.SurfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) HiddenForest.Accent else HiddenForest.Outline,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) HiddenForest.OnAccent else HiddenForest.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyActivityChart(last7DaysMinutes: List<Pair<String, Float>>) {
    val maxMinutes = last7DaysMinutes.maxOfOrNull { it.second } ?: 0f
    val scaleMax = if (maxMinutes > 0f) maxMinutes * 1.2f else 60f

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Actividad Semanal",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = HiddenForest.TextLow
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                last7DaysMinutes.forEach { (dayName, minutes) ->
                    val heightPercent = (minutes / scaleMax).coerceIn(0f, 1f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = heightPercent,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        label = "barHeight"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (minutes > 0f) {
                            val minutesFormatted = if (minutes >= 60f) {
                                String.format("%.1fh", minutes / 60f)
                            } else {
                                "${minutes.roundToInt()}m"
                            }
                            Text(
                                text = minutesFormatted,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = HiddenForest.Accent,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .fillMaxHeight(0.9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HiddenForest.Outline.copy(alpha = 0.3f))
                            )
                            
                            if (animatedHeight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(animatedHeight.coerceAtLeast(0.08f))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(HiddenForest.Accent, HiddenForest.AccentDim)
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (minutes > 0f) HiddenForest.Text else HiddenForest.TextLow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingStyleDistribution(stats: BookStats) {
    val totalTimeMs = stats.totalTtsTimeMs + stats.totalManualTimeMs
    if (totalTimeMs <= 0L) return

    val manualPercent = ((stats.totalManualTimeMs.toFloat() / totalTimeMs.toFloat()) * 100).roundToInt()
    val ttsPercent = 100 - manualPercent

    val manualDuration = formatDuration(stats.totalManualTimeMs)
    val ttsDuration = formatDuration(stats.totalTtsTimeMs)

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Distribución de Lectura",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = HiddenForest.TextLow
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(HiddenForest.Outline)
            ) {
                if (stats.totalManualTimeMs > 0) {
                    Box(
                        modifier = Modifier
                            .weight(stats.totalManualTimeMs.toFloat())
                            .fillMaxHeight()
                            .background(HiddenForest.Accent)
                    )
                }
                if (stats.totalTtsTimeMs > 0) {
                    Box(
                        modifier = Modifier
                            .weight(stats.totalTtsTimeMs.toFloat())
                            .fillMaxHeight()
                            .background(HiddenForest.Amber)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(HiddenForest.Accent))
                    Column {
                        Text(
                            text = "Manual ($manualPercent%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.Text
                        )
                        Text(
                            text = manualDuration,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = HiddenForest.TextSecondary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(HiddenForest.Amber))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Narración ($ttsPercent%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.Text
                        )
                        Text(
                            text = ttsDuration,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = HiddenForest.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ReadingSessionEntity) {
    val durationMs = (session.endAt ?: System.currentTimeMillis()) - session.startAt
    val wpm = if (durationMs > 0 && session.wordsRead > 0) {
        (session.wordsRead.toDouble() / (durationMs.toDouble() / 60000.0)).roundToInt()
    } else null

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val modeIcon = if (session.mode == ReadingMode.TTS) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.MenuBook
                val iconTint = if (session.mode == ReadingMode.TTS) HiddenForest.Amber else HiddenForest.Accent
                val bgContainer = if (session.mode == ReadingMode.TTS) HiddenForest.Amber.copy(alpha = 0.1f) else HiddenForest.AccentContainer
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = formatDate(session.startAt),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = HiddenForest.Text
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (session.mode == ReadingMode.TTS) "Narración" else "Manual",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                        if (session.wordsRead > 0) {
                            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(HiddenForest.OutlineHi))
                            Text(
                                text = "${session.wordsRead} palabras",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.TextSecondary
                            )
                        }
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Accent
                )
                if (wpm != null) {
                    Surface(
                        color = HiddenForest.Outline,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$wpm WPM",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = HiddenForest.TextLow,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar + FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(title: String, collapse: Float, onBack: () -> Unit) {
    val bgAlpha = (collapse * 1.4f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HiddenForest.Background.copy(alpha = bgAlpha))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = HiddenForest.Text
                )
            }
            AnimatedVisibility(
                visible = collapse > 0.7f,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReadNowFab(progressPercent: Float, onClick: () -> Unit) {
    val label = when {
        progressPercent <= 0f -> "Empezar"
        progressPercent >= 0.99f -> "Releer"
        else -> "Continuar"
    }

    FloatingActionButton(
        onClick = onClick,
        containerColor = HiddenForest.Accent,
        contentColor = HiddenForest.OnAccent,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.ambientGlow(HiddenForest.Accent, radius = 32.dp, alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared glass card component
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (accentColor != null) {
        Brush.verticalGradient(
            listOf(accentColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0.1f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HiddenForest.Surface)
            .border(1.dp, borderBrush, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading / Error States
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookDetailLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = HiddenForest.Accent)
            Text("Cargando libro…", color = HiddenForest.TextSecondary)
        }
    }
}

@Composable
private fun BookDetailErrorScreen(error: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(48.dp))
            Text(error, color = HiddenForest.TextSecondary, textAlign = TextAlign.Center)
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = HiddenForest.Accent)) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(icon: ImageVector, message: String, sub: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = HiddenForest.TextLow, modifier = Modifier.size(40.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = HiddenForest.TextSecondary)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = HiddenForest.TextLow, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun Modifier.ambientGlow(color: Color, radius: Dp, alpha: Float = 0.3f): Modifier =
    this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = android.graphics.Color.TRANSPARENT
                    setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha).toArgb())
                }
            }
            canvas.drawCircle(
                Offset(size.width / 2, size.height / 2),
                size.width / 2,
                paint
            )
        }
    }

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L    -> "—"
    bytes < 1024   -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else           -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0 min"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else      -> "${minutes} min"
    }
}

private fun formatWordCount(count: Long): String = when {
    count <= 0L    -> "—"
    count < 1000   -> "$count"
    else           -> String.format("%.1fK", count / 1000.0)
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

private fun shareHighlights(context: Context, book: BookEntity, highlights: List<HighlightEntity>) {
    if (highlights.isEmpty()) return
    val markdown = buildString {
        appendLine("# Subrayados: ${book.title}")
        if (book.author.isNotEmpty()) appendLine("*${book.author}*")
        appendLine()
        highlights.forEach { h ->
            if (h.chapterTitle.isNotEmpty()) appendLine("### ${h.chapterTitle}")
            appendLine("> ${h.highlightedText}")
            if (h.note.isNotEmpty()) appendLine("*${h.note}*")
            appendLine()
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, markdown)
        putExtra(Intent.EXTRA_SUBJECT, "Subrayados de ${book.title}")
    }
    context.startActivity(Intent.createChooser(intent, "Compartir subrayados"))
}
