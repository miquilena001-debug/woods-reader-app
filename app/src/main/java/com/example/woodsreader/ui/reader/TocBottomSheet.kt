package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocBottomSheet(
    publication: Publication,
    currentHref: String?,
    hazeState: HazeState,
    onChapterClick: (Link) -> Unit,
    onDismiss: () -> Unit
) {
    // Ignora el fragmento (#section) al comparar hrefs para que sub-secciones resalten correctamente
    fun hrefsMatch(linkHref: String, current: String?): Boolean {
        if (current == null) return false
        return linkHref.substringBefore("#") == current.substringBefore("#")
    }

    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        containerColor = Color(0xFF0C1811).copy(alpha = 0.98f),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .border(BorderStroke(1.dp, Color(0xFF233B2C)), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .hazeChild(
                state = hazeState,
                style = ReaderDesign.GlassStyle
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TABLA DE CONTENIDOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.Accent,
                    letterSpacing = 2.sp
                )
            }

            // Buscador rápido de capítulos
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text("Buscar capítulo por número o título...", color = HiddenForest.TextLow.copy(alpha = 0.6f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HiddenForest.Accent, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = HiddenForest.TextLow, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HiddenForest.Text,
                    unfocusedTextColor = HiddenForest.Text,
                    focusedBorderColor = HiddenForest.Accent,
                    unfocusedBorderColor = HiddenForest.Outline.copy(alpha = 0.3f),
                    focusedContainerColor = HiddenForest.Background.copy(alpha = 0.6f),
                    unfocusedContainerColor = HiddenForest.Background.copy(alpha = 0.4f),
                    cursorColor = HiddenForest.Accent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )

            val listState = rememberLazyListState()
            val rawToc = remember(publication) {
                publication.tableOfContents.ifEmpty { publication.readingOrder }
            }

            val filteredToc = remember(rawToc, searchQuery) {
                if (searchQuery.isBlank()) {
                    rawToc
                } else {
                    val q = searchQuery.trim().lowercase()
                    rawToc.filter { link ->
                        val title = (link.title ?: link.href.toString()).lowercase()
                        title.contains(q) || link.children.any { child ->
                            (child.title ?: child.href.toString()).lowercase().contains(q)
                        }
                    }
                }
            }

            val currentIndex = remember(filteredToc, currentHref) {
                filteredToc.indexOfFirst { hrefsMatch(it.href.toString(), currentHref) }.coerceAtLeast(0)
            }

            // Scroll automático al capítulo actual al abrir el TOC
            LaunchedEffect(currentIndex) {
                if (currentIndex >= 0 && filteredToc.isNotEmpty()) {
                    listState.scrollToItem(currentIndex.coerceAtLeast(0))
                }
            }

            if (filteredToc.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No se encontraron capítulos coincidentes." else "No se encontró un índice para este libro.",
                        color = HiddenForest.TextLow
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filteredToc,
                        key = { link -> link.href.toString() }
                    ) { link: Link ->
                        TocItem(
                            link = link,
                            isCurrent = hrefsMatch(link.href.toString(), currentHref),
                            onChapterClick = onChapterClick,
                            currentHref = currentHref,
                            hrefsMatch = ::hrefsMatch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TocItem(
    link: Link,
    isCurrent: Boolean,
    onChapterClick: (Link) -> Unit,
    currentHref: String?,
    hrefsMatch: (String, String?) -> Boolean,
    level: Int = 0
) {
    val displayTitle = remember(link) {
        link.title?.trim()?.ifBlank { null }
            ?: link.href.toString().substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifBlank { null }
            ?: "Sección"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isCurrent) Color(0xFF1A3826) else Color.Transparent)
                .border(
                    width = if (isCurrent) 1.dp else 0.dp,
                    color = if (isCurrent) HiddenForest.Accent.copy(alpha = 0.65f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onChapterClick(link) }
                .padding(vertical = 12.dp, horizontal = (8 + level * 16).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(HiddenForest.Accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent || level == 0) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (level == 0) 16.sp else 14.sp
                ),
                color = if (isCurrent) HiddenForest.Accent else HiddenForest.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isCurrent) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = HiddenForest.Accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "LEYENDO AHORA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = HiddenForest.Accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
        
        // Sub-capítulos con hrefsMatch propagado
        link.children.forEach { child ->
            TocItem(
                link = child,
                isCurrent = hrefsMatch(child.href.toString(), currentHref),
                onChapterClick = onChapterClick,
                currentHref = currentHref,
                hrefsMatch = hrefsMatch,
                level = level + 1
            )
        }
    }
}
