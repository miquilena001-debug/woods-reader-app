package com.example.woodsreader.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.woodsreader.data.graph.EntityType
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onNavigateUp: () -> Unit,
    onNavigateToCandidates: (() -> Unit)? = null,
    onJumpToLocator: (Locator) -> Unit = {},
    viewModel: GraphViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val candidatesCount by viewModel.candidatesCount.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val tick by viewModel.tick.collectAsState()
    val selectedIdx by viewModel.selectedIdx.collectAsState()

    val selectedEntity by viewModel.selectedEntity.collectAsState()
    val selectedAliases by viewModel.selectedEntityAliases.collectAsState()
    val selectedRelations by viewModel.selectedEntityRelations.collectAsState()
    val selectedMentions by viewModel.selectedEntityMentions.collectAsState()
    val currentMentionIndex by viewModel.currentMentionIndex.collectAsState()

    val bookTitle by viewModel.bookTitle.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var showOptionsMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        viewModel.getExportJson()
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.bufferedWriter().use { it.write(json) }
                        }
                    }
                    Toast.makeText(context, "Glosario exportado con éxito", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        } ?: ""
                    }
                    if (json.isNotBlank()) {
                        val count = withContext(Dispatchers.IO) {
                            viewModel.importGlossary(json)
                        }
                        Toast.makeText(context, "Se importaron $count entidades del glosario", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Buscar entidad...", fontSize = 14.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (uiState.searchQuery.isNotEmpty()) {
                                            viewModel.setSearchQuery("")
                                        } else {
                                            isSearchExpanded = false
                                        }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Grafo",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                // Switch "Sin spoilers"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = "Sin spoilers",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = uiState.isSpoilerFree,
                                        onCheckedChange = { viewModel.setSpoilerFree(it) },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás"
                            )
                        }
                    },
                    actions = {
                        if (!isSearchExpanded) {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar"
                                )
                            }
                        }
                        // Botón "Analizar libro" / Indicador de progreso del worker
                        if (analysisProgress.isRunning) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${analysisProgress.progress}%", fontSize = 12.sp) },
                                leadingIcon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        } else {
                            AssistChip(
                                onClick = { viewModel.startBookAnalysis() },
                                label = { Text("Analizar", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }

                        // Botón "Candidatos (N)"
                        AssistChip(
                            onClick = { onNavigateToCandidates?.invoke() },
                            label = { Text("Candidatos ($candidatesCount)", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // Menú de opciones (Exportar / Importar Glosario JSON)
                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Más opciones"
                                )
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exportar glosario (JSON)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        val cleanTitle = bookTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
                                        val filename = if (cleanTitle.isNotBlank()) "${cleanTitle}_glosario.json" else "glosario.json"
                                        exportLauncher.launch(filename)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Importar glosario (JSON)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FileUpload,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (analysisProgress.isRunning) {
                    LinearProgressIndicator(
                        progress = { analysisProgress.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Chips horizontales por tipo de entidad
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EntityType.entries.forEach { type ->
                        val isSelected = type in uiState.selectedTypes
                        val chipColor = entityColor(type)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleTypeFilter(type) },
                            label = { Text(type.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.25f),
                                selectedLabelColor = chipColor
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentLayout = viewModel.layout

            if (currentLayout != null && uiState.nodes.isNotEmpty()) {
                GraphCanvas(
                    nodes = uiState.nodes,
                    edges = uiState.edges,
                    layout = currentLayout,
                    tick = tick,
                    selectedIdx = selectedIdx,
                    onSelect = { idx ->
                        if (idx != null && idx != selectedIdx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.selectNode(idx)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (analysisProgress.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analizando libro en segundo plano…",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${analysisProgress.progress}% completado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { analysisProgress.progress / 100f },
                            modifier = Modifier
                                .width(180.dp)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (uiState.nodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.isSpoilerFree) "Sin entidades descubiertas" else "Sin entidades en el grafo",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.isSpoilerFree) {
                                "Aún no hay personajes ni lugares descubiertos hasta tu capítulo actual de lectura. Puedes desactivar 'Sin spoilers' o analizar el libro ahora."
                            } else {
                                "Extrae automáticamente personajes, lugares, artefactos y sectas con sus relaciones usando el análisis offline."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.startBookAnalysis() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analizar libro ahora", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        // Nodos filtrados a 0 por búsqueda o filtro de tipo
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se encontraron coincidencias",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Prueba cambiando el texto de búsqueda o los filtros de tipo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.setSearchQuery("")
                            }
                        ) {
                            Text("Limpiar búsqueda")
                        }
                    }
                }
            }
        }

        // FASE 6 & 7: Ficha de entidad seleccionada (ModalBottomSheet) con salto al texto
        selectedEntity?.let { entity ->
            EntityBottomSheet(
                entity = entity,
                aliases = selectedAliases,
                relations = selectedRelations,
                mentions = selectedMentions,
                currentMentionIndex = currentMentionIndex,
                allEntities = uiState.nodes,
                onDismiss = { viewModel.selectNode(null) },
                onUpdateType = { newType -> viewModel.updateEntityType(entity.id, newType) },
                onUpdateDescription = { newDesc -> viewModel.updateEntityDescription(entity.id, newDesc) },
                onAddAlias = { alias -> viewModel.addAlias(entity.id, alias) },
                onDeleteAlias = { alias -> viewModel.deleteAlias(entity.id, alias) },
                onSelectRelationNode = { otherId -> viewModel.selectEntityById(otherId) },
                onAddRelation = { targetId, label -> viewModel.addRelation(entity.id, targetId, label) },
                onNextMention = { viewModel.selectNextMention() },
                onPrevMention = { viewModel.selectPrevMention() },
                onViewInText = {
                    viewModel.buildLocatorForCurrentMention()?.let { locator ->
                        onJumpToLocator(locator)
                    }
                }
            )
        }
    }
}
