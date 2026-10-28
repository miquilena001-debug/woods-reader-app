package com.example.woodsreader.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.data.graph.EntityType
import com.example.woodsreader.data.graph.GraphAlias
import com.example.woodsreader.data.graph.GraphEntity
import com.example.woodsreader.data.graph.GraphMention
import com.example.woodsreader.data.graph.GraphRelation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntityBottomSheet(
    entity: GraphEntity,
    aliases: List<GraphAlias>,
    relations: List<Pair<GraphRelation, GraphEntity>>,
    mentions: List<GraphMention> = emptyList(),
    currentMentionIndex: Int = 0,
    allEntities: List<GraphEntity>,
    onDismiss: () -> Unit,
    onUpdateType: (EntityType) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onAddAlias: (String) -> Unit,
    onDeleteAlias: (String) -> Unit,
    onSelectRelationNode: (Long) -> Unit,
    onAddRelation: (targetId: Long, label: String) -> Unit,
    onNextMention: () -> Unit = {},
    onPrevMention: () -> Unit = {},
    onViewInText: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isEditingDesc by remember(entity.id) { mutableStateOf(false) }
    var tempDesc by remember(entity.id, entity.description) { mutableStateOf(entity.description) }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showAddAliasDialog by remember { mutableStateOf(false) }
    var showAddRelationDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Cabecera: Nombre + Tipo editable ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${entity.mentionCount} menciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Chip de Tipo desplegable
                Box {
                    val color = entityColor(entity.type)
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showTypeMenu = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = entity.type.name,
                                color = color,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Cambiar tipo",
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        EntityType.entries.forEach { typeOption ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(entityColor(typeOption), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(typeOption.name)
                                    }
                                },
                                onClick = {
                                    onUpdateType(typeOption)
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Descripción editable ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Descripción",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isEditingDesc) {
                        IconButton(
                            onClick = { isEditingDesc = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar descripción",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (isEditingDesc) {
                    OutlinedTextField(
                        value = tempDesc,
                        onValueChange = { tempDesc = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe una descripción...") },
                        maxLines = 4
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            tempDesc = entity.description
                            isEditingDesc = false
                        }) {
                            Text("Cancelar")
                        }
                        Button(onClick = {
                            onUpdateDescription(tempDesc.trim())
                            isEditingDesc = false
                        }) {
                            Text("Guardar")
                        }
                    }
                } else {
                    Text(
                        text = entity.description.ifBlank { "Sin descripción disponible." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entity.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Aliases ───────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alias (${aliases.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { showAddAliasDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir alias",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    aliases.forEach { alias ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(alias.alias, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Eliminar alias",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onDeleteAlias(alias.alias) }
                                )
                            }
                        )
                    }
                    if (aliases.isEmpty()) {
                        Text(
                            text = "Sin alias conocidos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Relaciones ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Relaciones directas (${relations.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (relations.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(relations) { (rel, otherNode) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRelationNode(otherNode.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(entityColor(otherNode.type), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = otherNode.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (rel.label.isNotBlank()) {
                                            Text(
                                                text = " (${rel.label})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "peso: ${rel.weight}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No se encontraron relaciones directas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── FASE 7: Menciones en el texto con flechas anterior / siguiente ─
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mentions.isNotEmpty()) {
                            "Aparición ${currentMentionIndex + 1} de ${mentions.size}"
                        } else {
                            "Menciones en el texto"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (mentions.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onPrevMention,
                                enabled = currentMentionIndex > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Mención anterior",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onNextMention,
                                enabled = currentMentionIndex < mentions.size - 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Mención siguiente",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                val currentMention = mentions.getOrNull(currentMentionIndex)
                if (currentMention != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Capítulo ${currentMention.chapterIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "«${currentMention.snippet}»",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Sin menciones registradas para esta entidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Botones de acción inferiores ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onViewInText,
                    enabled = mentions.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver en texto")
                }

                Button(
                    onClick = { showAddRelationDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Añadir relación")
                }
            }
        }
    }

    // ── Diálogo: Añadir Alias ─────────────────────────────────────────────────
    if (showAddAliasDialog) {
        var newAliasText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAliasDialog = false },
            title = { Text("Añadir alias") },
            text = {
                OutlinedTextField(
                    value = newAliasText,
                    onValueChange = { newAliasText = it },
                    label = { Text("Nombre alternativo") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAliasText.isNotBlank()) {
                            onAddAlias(newAliasText.trim())
                            showAddAliasDialog = false
                        }
                    }
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAliasDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ── Diálogo: Añadir Relación (Destino + Etiqueta) ─────────────────────────
    if (showAddRelationDialog) {
        var selectedTargetId by remember { mutableStateOf<Long?>(null) }
        var relationLabel by remember { mutableStateOf("") }
        var targetDropdownExpanded by remember { mutableStateOf(false) }

        val candidateTargets = remember(allEntities, entity.id) {
            allEntities.filter { it.id != entity.id }
        }

        AlertDialog(
            onDismissRequest = { showAddRelationDialog = false },
            title = { Text("Añadir relación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Origen: ${entity.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Selector de entidad destino
                    Box {
                        val targetNode = candidateTargets.firstOrNull { it.id == selectedTargetId }
                        OutlinedButton(
                            onClick = { targetDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(targetNode?.name ?: "Seleccionar destino...")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = targetDropdownExpanded,
                            onDismissRequest = { targetDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            candidateTargets.forEach { targetOption ->
                                DropdownMenuItem(
                                    text = { Text(targetOption.name) },
                                    onClick = {
                                        selectedTargetId = targetOption.id
                                        targetDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = relationLabel,
                        onValueChange = { relationLabel = it },
                        label = { Text("Etiqueta (ej: Maestro, Rival, Amigo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedTargetId != null,
                    onClick = {
                        selectedTargetId?.let { targetId ->
                            onAddRelation(targetId, relationLabel.trim())
                            showAddRelationDialog = false
                        }
                    }
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRelationDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
