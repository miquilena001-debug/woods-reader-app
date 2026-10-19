package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.example.woodsreader.ui.theme.HiddenForest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import com.example.woodsreader.core.search.FastEpubSearchEngine
import androidx.compose.foundation.lazy.itemsIndexed
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@OptIn(ExperimentalMaterial3Api::class, org.readium.r2.shared.ExperimentalReadiumApi::class)
@Composable
fun SearchBottomSheet(
    publication: Publication,
    initialQuery: String = "",
    hazeState: HazeState,
    onResultClick: (Locator) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<Locator>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchFinished by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    
    fun performSearch() {
        if (query.isBlank()) return
        focusManager.clearFocus()

        // Cancelar búsqueda anterior si existe
        searchJob?.cancel()

        searchJob = scope.launch {
            // Resetear estado al iniciar
            results = emptyList()
            isSearching = true
            searchFinished = false
            searchError = null

            try {
                val bookId = publication.metadata.identifier ?: publication.metadata.title ?: "epub_book"
                FastEpubSearchEngine.searchPublicationStreaming(
                    bookId = bookId,
                    publication = publication,
                    rawQuery = query
                ).collect { progress ->
                    results = progress.hits.map { hit ->
                        val link = publication.readingOrder.getOrNull(hit.chapterIndex)
                        val url = link?.url()
                            ?: org.readium.r2.shared.util.Url(hit.chapterHref)
                            ?: org.readium.r2.shared.util.Url("#")!!
                        val hlStart = hit.matchStartInSnippet.coerceIn(0, hit.snippet.length)
                        val hlEnd = (hit.matchStartInSnippet + hit.matchLength).coerceIn(hlStart, hit.snippet.length)
                        Locator(
                            href = url,
                            mediaType = link?.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.XHTML,
                            title = hit.chapterTitle,
                            locations = Locator.Locations(
                                progression = hit.approximateProgression,
                                position = hit.chapterIndex + 1
                            ),
                            text = Locator.Text(
                                highlight = hit.snippet.substring(hlStart, hlEnd),
                                before = hit.snippet.substring(0, hlStart).removePrefix("…"),
                                after = hit.snippet.substring(hlEnd).removeSuffix("…")
                            )
                        )
                    }
                    if (progress.isFinished) {
                        searchFinished = true
                        isSearching = false
                    }
                }

            } catch (e: CancellationException) {
                // Cancelación normal (usuario inició nueva búsqueda) — no mostrar error
                throw e // Re-lanzar para que el scope sepa que fue cancelado

            } catch (e: Exception) {
                if (isActive) {
                    searchError = "Error al buscar: ${e.message}"
                }
            } finally {
                withContext(NonCancellable) {
                    isSearching = false
                }
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            performSearch()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            searchJob?.cancel()
            onDismiss()
        },
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        containerColor = Color(0xFF0C1811).copy(alpha = 0.98f),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .heightIn(max = 600.dp)
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
            Text(
                text = "BÚSQUEDA",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.Accent,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = HiddenForest.SurfaceVariant.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.2f))
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("¿Qué buscas hoy?", color = HiddenForest.TextLow) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HiddenForest.Accent) },
                    trailingIcon = {
                        val showClearIcon by remember { derivedStateOf { query.isNotEmpty() } }
                        if (showClearIcon) {
                            IconButton(onClick = {
                                searchJob?.cancel()
                                query = ""
                                results = emptyList()
                                searchFinished = false
                                searchError = null
                                isSearching = false
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Borrar", tint = HiddenForest.TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = HiddenForest.Accent
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Resultados de Búsqueda
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                when {
                    isSearching -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = HiddenForest.Accent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buscando...",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextLow
                            )
                        }
                    }
                    searchError != null -> {
                        Text(
                            text = searchError!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 16.dp)
                        )
                    }
                    searchFinished && results.isEmpty() -> {
                        Text(
                            text = "No se encontraron resultados para \"$query\"",
                            color = HiddenForest.TextLow,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 16.dp)
                        )
                    }
                    results.isNotEmpty() -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            item {
                                Text(
                                    text = "${results.size} resultado${if (results.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HiddenForest.Accent.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            itemsIndexed(
                                items = results,
                                key = { index, _ -> index }
                            ) { _, locator ->
                                SearchResultItem(locator = locator, onClick = { onResultClick(locator) })
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔍", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Escribe y pulsa la lupa para buscar",
                                color = HiddenForest.TextLow,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(locator: Locator, onClick: () -> Unit) {
    val annotatedSnippet = remember(locator) {
        val highlight = locator.text.highlight ?: ""
        val before = (locator.text.before ?: "").takeLast(40)
        val after = (locator.text.after ?: "").take(60)
        
        buildAnnotatedString {
            // Contexto previo
            if (before.isNotEmpty()) {
                append("…")
                append(before)
            }
            
            // Match con estilo Elite
            withStyle(SpanStyle(
                background = HiddenForest.Accent.copy(alpha = 0.25f),
                color = HiddenForest.Text,
                fontWeight = FontWeight.Bold
            )) {
                append(highlight)
            }
            
            // Contexto posterior
            if (after.isNotEmpty()) {
                append(after)
                append("…")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HiddenForest.SurfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = locator.title ?: "Sección del libro",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = HiddenForest.TextLow,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = annotatedSnippet,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = HiddenForest.Text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
