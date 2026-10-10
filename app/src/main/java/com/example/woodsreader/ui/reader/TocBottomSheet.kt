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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        containerColor = HiddenForest.Surface.copy(alpha = 0.7f),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
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
            Text(
                text = "TABLA DE CONTENIDOS",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextLow,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val listState = rememberLazyListState()
            val toc = remember(publication) {
                publication.tableOfContents.ifEmpty { publication.readingOrder }
            }
            val currentIndex = remember(toc, currentHref) {
                toc.indexOfFirst { hrefsMatch(it.href.toString(), currentHref) }.coerceAtLeast(0)
            }
            // Scroll automático al capítulo actual al abrir el TOC
            LaunchedEffect(currentIndex) {
                if (currentIndex > 0) listState.scrollToItem(currentIndex)
            }
            if (toc.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No se encontró un índice para este libro.", color = HiddenForest.TextLow)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = toc,
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
                .background(if (isCurrent) HiddenForest.Accent.copy(alpha = 0.1f) else Color.Transparent)
                .clickable { onChapterClick(link) }
                .padding(vertical = 12.dp, horizontal = (8 + level * 16).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
