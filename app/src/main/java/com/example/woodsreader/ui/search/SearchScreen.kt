package com.example.woodsreader.ui.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.ui.library.LibraryViewModel
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.Semantic

@Composable
fun highlightText(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        if (query.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lower = text.lowercase()
        val qLower = query.lowercase()
        var start = 0
        var idx = lower.indexOf(qLower)
        while (idx >= 0) {
            append(text.substring(start, idx))
            withStyle(
                SpanStyle(
                    color = HiddenForest.Accent,
                    fontWeight = FontWeight.Bold,
                    background = HiddenForest.Accent.copy(alpha = 0.15f)
                )
            ) {
                append(text.substring(idx, idx + qLower.length))
            }
            start = idx + qLower.length
            idx = lower.indexOf(qLower, start)
        }
        append(text.substring(start))
    }
}

private fun getDescriptionSnippet(description: String, query: String): String {
    if (description.isBlank()) return ""
    if (query.isBlank()) return description.take(90) + if (description.length > 90) "..." else ""
    
    val idx = description.lowercase().indexOf(query.lowercase())
    if (idx == -1) return description.take(90) + if (description.length > 90) "..." else ""
    
    val start = (idx - 35).coerceAtLeast(0)
    val end = (idx + query.length + 55).coerceAtMost(description.length)
    
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < description.length) "..." else ""
    
    return prefix + description.substring(start, end).trim() + suffix
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBookClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchTarget by viewModel.searchTarget.collectAsStateWithLifecycle()
    val searchFormat by viewModel.searchFormat.collectAsStateWithLifecycle()
    val searchStatus by viewModel.searchStatus.collectAsStateWithLifecycle()
    val searchCollectionId by viewModel.searchCollectionId.collectAsStateWithLifecycle()
    val searchSort by viewModel.searchSort.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle(initialValue = emptyList())
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    
    val books = viewModel.searchedBooks.collectAsLazyPagingItems()
    val focusManager = LocalFocusManager.current
    
    var filtersExpanded by remember { mutableStateOf(false) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HiddenForest.Background)
    ) {
        // Brillo radial de fondo
        Box(modifier = Modifier.fillMaxSize().drawWithCache {
            val brush = Brush.radialGradient(
                    colors = listOf(HiddenForest.AccentGlow.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.width * 0.9f
                )
            onDrawBehind {
                drawRect(brush = brush)
            }
        })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header Row ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = HiddenForest.Text
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "BÚSQUEDA AVANZADA",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Biblioteca",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = HiddenForest.Text
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Campo de Búsqueda ──────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HiddenForest.SurfaceVariant)
                            .border(
                                1.dp,
                                if (query.isNotEmpty()) HiddenForest.Accent.copy(0.6f) else HiddenForest.Outline,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (query.isNotEmpty()) HiddenForest.Accent else HiddenForest.TextLow,
                                modifier = Modifier.size(20.dp)
                            )
                            BasicTextField(
                                value = query,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = HiddenForest.Text),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (query.isNotBlank()) {
                                        viewModel.saveSearchToHistory(query)
                                    }
                                    focusManager.clearFocus()
                                }),
                                decorationBox = @Composable { inner ->
                                    Box {
                                        if (query.isEmpty()) {
                                            Text(
                                                "Buscar título, autor, descripción...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = HiddenForest.TextLow
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar",
                                        tint = HiddenForest.TextLow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Historial Reciente ──────────────────────────────────────────
                if (recentSearches.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "BÚSQUEDAS RECIENTES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = HiddenForest.TextLow
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(recentSearches) { term ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(HiddenForest.SurfaceVariant.copy(alpha = 0.5f))
                                            .border(0.5.dp, HiddenForest.Outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .clickable { viewModel.onSearchQueryChanged(term) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = HiddenForest.TextLow,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = term,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HiddenForest.TextSecondary
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Eliminar",
                                                tint = Semantic.Error.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .clickable { viewModel.deleteSearchFromHistory(term) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Objetivo de Búsqueda (Search Target) ─────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BUSCAR EN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = HiddenForest.TextLow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "ALL" to "Todo",
                                "TITLE" to "Título",
                                "AUTHOR" to "Autor",
                                "DESCRIPTION" to "Descripción"
                            ).forEach { (targetCode, targetLabel) ->
                                val isSelected = searchTarget == targetCode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) HiddenForest.Accent.copy(alpha = 0.15f) else HiddenForest.SurfaceVariant.copy(0.4f))
                                        .border(
                                            1.dp,
                                            if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.5f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { viewModel.onSearchTargetChanged(targetCode) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = targetLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (isSelected) HiddenForest.Accent else HiddenForest.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Desplegable Filtros Avanzados ────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HiddenForest.SurfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, HiddenForest.Outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filtersExpanded = !filtersExpanded }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.FilterList, null, tint = HiddenForest.Accent, modifier = Modifier.size(18.dp))
                                Text("Filtros Avanzados", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
                            }
                            Icon(
                                imageVector = if (filtersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = HiddenForest.TextSecondary
                            )
                        }

                        AnimatedVisibility(
                            visible = filtersExpanded,
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                HorizontalDivider(color = HiddenForest.Outline.copy(alpha = 0.1f))

                                // FORMAT FILTERS
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Formato de Archivo", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("ALL" to "Cualquiera", "EPUB" to "EPUB", "PDF" to "PDF").forEach { (code, label) ->
                                            val isSelected = searchFormat == code
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) HiddenForest.Accent.copy(alpha = 0.12f) else Color.Transparent)
                                                    .border(0.5.dp, if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.onSearchFormatChanged(code) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(label, style = MaterialTheme.typography.bodySmall, color = if (isSelected) HiddenForest.Accent else HiddenForest.TextSecondary)
                                            }
                                        }
                                    }
                                }

                                // STATUS FILTERS
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Estado de Lectura", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "ALL" to "Cualquiera",
                                            "NOT_STARTED" to "Sin Empezar",
                                            "IN_PROGRESS" to "Leyendo",
                                            "COMPLETED" to "Terminado",
                                            "ABANDONED" to "Abandonado"
                                        ).forEach { (code, label) ->
                                            val isSelected = searchStatus == code
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) HiddenForest.Accent.copy(alpha = 0.12f) else Color.Transparent)
                                                    .border(0.5.dp, if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.onSearchStatusChanged(code) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(label, style = MaterialTheme.typography.bodySmall, color = if (isSelected) HiddenForest.Accent else HiddenForest.TextSecondary)
                                            }
                                        }
                                    }
                                }

                                // SHELVES / COLLECTION SELECTOR & SORT
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Collections Selector
                                    Box(modifier = Modifier.weight(1f)) {
                                        val selectedCollName = if (searchCollectionId == "ALL") "Todas" else collections.find { it.id == searchCollectionId }?.name ?: "Todas"
                                        OutlinedButton(
                                            onClick = { showCollectionMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.5.dp, HiddenForest.Outline.copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.TextSecondary)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Estante: $selectedCollName", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showCollectionMenu,
                                            onDismissRequest = { showCollectionMenu = false },
                                            modifier = Modifier.background(HiddenForest.Surface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Todas las colecciones", color = HiddenForest.Text) },
                                                onClick = { viewModel.onSearchCollectionIdChanged("ALL"); showCollectionMenu = false }
                                            )
                                            collections.forEach { coll ->
                                                DropdownMenuItem(
                                                    text = { Text(coll.name, color = HiddenForest.Text) },
                                                    onClick = { viewModel.onSearchCollectionIdChanged(coll.id); showCollectionMenu = false }
                                                )
                                            }
                                        }
                                    }

                                    // Sorting Selector
                                    Box(modifier = Modifier.weight(1f)) {
                                        val sortLabels = mapOf(
                                            "TITLE_ASC" to "Título (A-Z)",
                                            "TITLE_DESC" to "Título (Z-A)",
                                            "AUTHOR_ASC" to "Autor (A-Z)",
                                            "LAST_OPENED" to "Reciente",
                                            "DATE_ADDED" to "Agregado"
                                        )
                                        OutlinedButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.5.dp, HiddenForest.Outline.copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.TextSecondary)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Orden: ${sortLabels[searchSort] ?: "Reciente"}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            modifier = Modifier.background(HiddenForest.Surface)
                                        ) {
                                            sortLabels.forEach { (code, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label, color = HiddenForest.Text) },
                                                    onClick = { viewModel.onSearchSortChanged(code); showSortMenu = false }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Reset button
                                TextButton(
                                    onClick = { viewModel.clearAllFilters() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restaurar Filtros", style = MaterialTheme.typography.bodySmall, color = HiddenForest.Accent)
                                }
                            }
                        }
                    }
                }

                // ── Resultados ──────────────────────────────────────────
                val count = books.itemCount
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (query.isNotBlank()) "$count libro${if (count != 1) "s" else ""} encontrado${if (count != 1) "s" else ""}" else "Todos los libros ($count)",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                            color = HiddenForest.TextLow
                        )
                        if (query.isNotBlank() && count > 0) {
                            TextButton(onClick = { viewModel.saveSearchToHistory(query) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Guardar búsqueda", style = MaterialTheme.typography.labelSmall, color = HiddenForest.Accent)
                            }
                        }
                    }
                }

                if (count > 0) {
                    items(count) { idx ->
                        val book = books[idx]
                        if (book != null) {
                            SearchResultCard(
                                book = book,
                                query = query,
                                target = searchTarget,
                                onClick = {
                                    if (query.isNotBlank()) {
                                        viewModel.saveSearchToHistory(query)
                                    }
                                    onBookClick(book.id)
                                }
                            )
                        }
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🔎", fontSize = 42.sp)
                            Text(
                                text = if (query.isNotBlank()) "Sin resultados para «$query»" else "No se encontraron libros que coincidan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiddenForest.TextSecondary
                            )
                            Text(
                                text = "Intenta cambiar los filtros avanzados arriba",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextLow
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    book: BookEntity,
    query: String,
    target: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HiddenForest.SurfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, HiddenForest.Outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Portada
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(HiddenForest.Accent.copy(0.3f), HiddenForest.Surface)
                        )
                    )
                    .border(1.dp, HiddenForest.Accent.copy(0.3f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    book.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = HiddenForest.Accent
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Título resaltado
                val isTitleTarget = target == "TITLE" || target == "ALL"
                Text(
                    text = if (isTitleTarget) highlightText(book.title, query) else buildAnnotatedString { append(book.title) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Autor resaltado
                val isAuthorTarget = target == "AUTHOR" || target == "ALL"
                val authorStr = book.author.ifBlank { "Autor desconocido" }
                Text(
                    text = if (isAuthorTarget) highlightText(authorStr, query) else buildAnnotatedString { append(authorStr) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = HiddenForest.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Descripción o Snippet coincidente (si está buscando en descripción/todo y hay coincidencia)
                val isDescTarget = target == "DESCRIPTION" || target == "ALL"
                if (book.description.isNotBlank() && isDescTarget) {
                    val snippet = getDescriptionSnippet(book.description, query)
                    if (snippet.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = highlightText(snippet, query),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = HiddenForest.TextLow,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Badges para Formato y Colección
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HiddenForest.Outline.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = book.format.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                            color = HiddenForest.TextLow
                        )
                    }
                    if (book.collectionId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HiddenForest.Accent.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Colección",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                color = HiddenForest.Accent
                            )
                        }
                    }
                }
            }
            Text("▶", color = HiddenForest.TextLow, fontSize = 14.sp)
        }
    }
}
