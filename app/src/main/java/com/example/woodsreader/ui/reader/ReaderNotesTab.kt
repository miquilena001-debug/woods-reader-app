package com.example.woodsreader.ui.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.woodsreader.core.managers.HighlightExporter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.toArgb
import com.example.woodsreader.core.managers.QuoteCardGenerator
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoteType { HIGHLIGHT, BOOKMARK }

enum class NotesFilter(val label: String) {
    TODOS("Todos"),
    PERSONAJES("Personajes"),
    CITAS("Citas"),
    MARCADORES("Marcadores")
}

data class UnifiedNote(
    val id: String,
    val type: NoteType,
    val title: String,
    val content: String,
    val timestamp: Long,
    val color: HighlightColor? = null,
    val isCharacter: Boolean = false,
    val characterRole: String? = null,
    val characterDescription: String? = null,
    val originalHighlight: HighlightEntity? = null,
    val originalBookmark: BookmarkEntity? = null
)

private val noteDateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

private fun parseCharacterNote(note: String): Pair<String, String> {
    val clean = note.trim()
    val match = Regex("""^\[PERSONAJE(?::\s*([^\]]+))?\]\s*(.*)$""", RegexOption.DOT_MATCHES_ALL).find(clean)
    return if (match != null) {
        val role = match.groupValues[1].trim().ifBlank { "PERSONAJE" }
        val desc = match.groupValues[2].trim()
        role to desc
    } else {
        "PERSONAJE" to clean.removePrefix("[PERSONAJE]").trim()
    }
}

@Composable
fun ReaderNotesTab(
    hazeState: HazeState,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>,
    bookTitle: String,
    bookAuthor: String,
    onHighlightClick: (HighlightEntity) -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(NotesFilter.TODOS) }

    val unifiedFeed = remember(highlights, bookmarks) {
        val list = mutableListOf<UnifiedNote>()
        
        // Convert Highlights
        highlights.forEach { hl ->
            val isChar = hl.note.startsWith("[PERSONAJE")
            val (charRole, charDesc) = if (isChar) parseCharacterNote(hl.note) else null to null
            list.add(
                UnifiedNote(
                    id = hl.id,
                    type = NoteType.HIGHLIGHT,
                    title = hl.chapterTitle,
                    content = hl.highlightedText,
                    timestamp = hl.createdAt,
                    color = hl.color,
                    isCharacter = isChar,
                    characterRole = charRole,
                    characterDescription = charDesc,
                    originalHighlight = hl
                )
            )
        }
        
        // Convert Bookmarks
        bookmarks.forEach { bm ->
            val (headerBadge, fallbackDisplay) = bookmarkDisplay(bm)
            list.add(
                UnifiedNote(
                    id = bm.id,
                    type = NoteType.BOOKMARK,
                    title = headerBadge,
                    content = bm.label.ifBlank { fallbackDisplay },
                    timestamp = bm.createdAt,
                    originalBookmark = bm
                )
            )
        }
        
        list.sortedByDescending { it.timestamp }
    }

    val filteredFeed = remember(unifiedFeed, selectedFilter) {
        when (selectedFilter) {
            NotesFilter.TODOS -> unifiedFeed
            NotesFilter.PERSONAJES -> unifiedFeed.filter { it.isCharacter }
            NotesFilter.CITAS -> unifiedFeed.filter { it.type == NoteType.HIGHLIGHT && !it.isCharacter }
            NotesFilter.MARCADORES -> unifiedFeed.filter { it.type == NoteType.BOOKMARK }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIS NOTAS Y MARCADORES",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = HiddenForest.Accent
            )
            
            if (highlights.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val shareText = HighlightExporter.exportToMarkdown(bookTitle, bookAuthor, highlights)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Subrayados de $bookTitle")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Exportar Notas"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Exportar notas",
                        tint = HiddenForest.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Fila de Chips de Filtro (Todos | Personajes | Citas | Marcadores)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotesFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) HiddenForest.Accent else HiddenForest.Surface.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) HiddenForest.Accent else HiddenForest.OutlineHi.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = if (isSelected) Color(0xFF080B08) else HiddenForest.TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (filteredFeed.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (selectedFilter) {
                            NotesFilter.PERSONAJES -> "👤"
                            NotesFilter.CITAS -> "📝"
                            NotesFilter.MARCADORES -> "🔖"
                            NotesFilter.TODOS -> "🏜️"
                        },
                        fontSize = 40.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (selectedFilter) {
                            NotesFilter.PERSONAJES -> "No tienes personajes guardados"
                            NotesFilter.CITAS -> "No tienes citas guardadas"
                            NotesFilter.MARCADORES -> "No tienes marcadores guardados"
                            NotesFilter.TODOS -> "No tienes notas ni marcadores"
                        },
                        color = HiddenForest.TextLow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
            ) {
                items(filteredFeed, key = { it.id }) { note ->
                    ReaderUnifiedNoteItem(
                        hazeState = hazeState,
                        note = note,
                        onNoteClick = { 
                            if (note.type == NoteType.HIGHLIGHT) onHighlightClick(note.originalHighlight!!)
                            else if (note.originalBookmark != null) onBookmarkClick(note.originalBookmark)
                        },
                        onDeleteClick = {
                            if (note.type == NoteType.HIGHLIGHT) onDeleteHighlight(note.originalHighlight!!)
                            else onDeleteBookmark(note.originalBookmark!!)
                        },
                        onShareClick = {
                            val highlight = note.originalHighlight
                            if (highlight != null) {
                                scope.launch {
                                    QuoteCardGenerator.shareQuoteCard(
                                        context = context,
                                        quoteText = highlight.highlightedText,
                                        bookTitle = bookTitle,
                                        bookAuthor = bookAuthor,
                                        chapterTitle = highlight.chapterTitle
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun bookmarkDisplay(bookmark: BookmarkEntity): Pair<String, String> {
    val jsonObj = runCatching { JSONObject(bookmark.locatorJson) }.getOrNull()
    val locationsObj = jsonObj?.optJSONObject("locations")
    val position = locationsObj?.optInt("position", 0) ?: 0
    val progress = locationsObj?.optDouble("totalProgression", -1.0) ?: -1.0
    val locatorTitle = jsonObj?.optString("title", "").orEmpty()
    val textObj = jsonObj?.optJSONObject("text")
    val highlightText = textObj?.optString("highlight", "")?.takeIf { it.isNotBlank() }
        ?: textObj?.optString("after", "")?.take(100)
        ?: ""

    val headerBadge = buildString {
        append("MARCADOR")
        if (position > 0) append(" · PÁGINA $position")
        if (progress >= 0.0) {
            append(" · ${String.format(Locale.US, "%.1f%%", progress * 100.0)}")
        }
    }
    val fallbackDisplay = when {
        highlightText.isNotBlank() -> "\"${highlightText.trim()}\""
        locatorTitle.isNotBlank() -> locatorTitle
        position > 0 -> "Marcador en la página $position"
        else -> "Posición guardada en el libro"
    }
    return headerBadge to fallbackDisplay
}

@Composable
fun ReaderUnifiedNoteItem(
    hazeState: HazeState,
    note: UnifiedNote,
    onNoteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(state = hazeState, style = ReaderDesign.GlassStyle)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF162B20).copy(alpha = 0.92f))
            .border(
                1.dp,
                if (note.isCharacter) HiddenForest.Amber.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(24.dp)
            )
            .clickable { onNoteClick() }
            .padding(16.dp)
    ) {
        if (note.isCharacter) {
            // Tarjeta de Personaje
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icono Personaje
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HiddenForest.Amber.copy(alpha = 0.18f))
                        .border(1.dp, HiddenForest.Amber.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Personaje",
                        tint = HiddenForest.Amber,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                ) {
                    // Badge con rol extraído + Capítulo al lado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = HiddenForest.Amber.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, HiddenForest.Amber.copy(alpha = 0.5f))
                        ) {
                            val roleDisplay = if (note.characterRole != null && note.characterRole.isNotBlank() && !note.characterRole.equals("PERSONAJE", ignoreCase = true)) {
                                "PERSONAJE · ${note.characterRole.uppercase()}"
                            } else {
                                "PERSONAJE"
                            }
                            Text(
                                text = roleDisplay,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = HiddenForest.Amber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (note.title.isNotBlank()) {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = HiddenForest.TextLow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Nombre del personaje en tipografía grande (16.sp, FontWeight.Bold, color blanco)
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Descripción limpia debajo sin el prefijo [PERSONAJE: ...]
                    if (!note.characterDescription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note.characterDescription,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = HiddenForest.Text,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fecha
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = HiddenForest.TextLow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = noteDateFormat.format(Date(note.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                    }
                }

                // Borrar
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.align(Alignment.Top)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Borrar",
                        tint = HiddenForest.TextLow.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // Cita o Marcador Normal
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Indicador de Tipo / Color
                if (note.type == NoteType.HIGHLIGHT && note.color != null) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(note.color.toArgb()))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HiddenForest.Accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (note.type == NoteType.BOOKMARK) Icons.Default.Bookmark else Icons.AutoMirrored.Filled.StickyNote2,
                            contentDescription = null,
                            tint = HiddenForest.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                ) {
                    // Título / Capítulo
                    Text(
                        text = note.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = HiddenForest.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Contenido
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = HiddenForest.Text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Fecha
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = HiddenForest.TextLow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = noteDateFormat.format(Date(note.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                    }
                }

                if (note.type == NoteType.HIGHLIGHT) {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.align(Alignment.Top)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir cita como imagen",
                            tint = HiddenForest.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.align(Alignment.Top)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Borrar",
                        tint = HiddenForest.TextLow.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
