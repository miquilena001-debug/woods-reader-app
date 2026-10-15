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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import androidx.compose.material.icons.filled.Share
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
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoteType { HIGHLIGHT, BOOKMARK }

data class UnifiedNote(
    val id: String,
    val type: NoteType,
    val title: String,
    val content: String,
    val timestamp: Long,
    val color: HighlightColor? = null,
    val originalHighlight: HighlightEntity? = null,
    val originalBookmark: BookmarkEntity? = null
)

private val noteDateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

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
    val unifiedFeed = remember(highlights, bookmarks) {
        val list = mutableListOf<UnifiedNote>()
        
        // Convert Highlights
        highlights.forEach { hl ->
            list.add(
                UnifiedNote(
                    id = hl.id,
                    type = NoteType.HIGHLIGHT,
                    title = hl.chapterTitle,
                    content = hl.highlightedText,
                    timestamp = hl.createdAt,
                    color = hl.color,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIS NOTAS Y MARCADORES",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = HiddenForest.Accent
            )
            
            if (highlights.isNotEmpty()) {
                val context = LocalContext.current
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

        if (unifiedFeed.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏜️", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No tienes notas ni marcadores",
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
                items(unifiedFeed, key = { it.id }) { note ->
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
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(state = hazeState, style = ReaderDesign.GlassStyle)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF162B20).copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .clickable { onNoteClick() }
            .padding(16.dp)
    ) {
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

            // Botón de Borrar
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

// El componente anterior ReaderNoteItem puede ser eliminado o mantenido como deprecated.
// Por ahora lo eliminaremos para limpiar el archivo.
