package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.data.database.TtsRuleEntity
import com.example.woodsreader.core.tts.TtsPresetPack
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.Semantic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsRulesBottomSheet(
    rules: List<TtsRuleEntity>,
    onSaveRule: (TtsRuleEntity) -> Unit,
    onDeleteRule: (TtsRuleEntity) -> Unit,
    onInstallPresetPack: (TtsPresetPack) -> Unit,
    onDismiss: () -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HiddenForest.Surface.copy(alpha = 0.95f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Título ─────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = HiddenForest.Accent
                    )
                    Text(
                        text = "Diccionario de TTS",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = HiddenForest.TextLow)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Formulario de nueva regla ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(HiddenForest.SurfaceVariant)
                    .border(1.dp, HiddenForest.Outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AÑADIR REGLA DE PRONUNCIACIÓN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                    color = HiddenForest.Accent
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Palabra o patrón original", color = HiddenForest.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HiddenForest.Text,
                        unfocusedTextColor = HiddenForest.Text,
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.Outline,
                        cursorColor = HiddenForest.Accent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Pronunciar como", color = HiddenForest.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HiddenForest.Text,
                        unfocusedTextColor = HiddenForest.Text,
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.Outline,
                        cursorColor = HiddenForest.Accent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Usar Expresión Regular (Regex)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = HiddenForest.Text
                        )
                        Text(
                            text = "Permite patrones de búsqueda complejos",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextLow
                        )
                    }
                    Switch(
                        checked = isRegex,
                        onCheckedChange = { isRegex = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HiddenForest.Accent,
                            uncheckedThumbColor = HiddenForest.TextLow,
                            uncheckedTrackColor = HiddenForest.Background
                        )
                    )
                }

                Button(
                    onClick = {
                        if (pattern.trim().isNotEmpty() && replacement.trim().isNotEmpty()) {
                            onSaveRule(
                                TtsRuleEntity(
                                    pattern = pattern.trim(),
                                    replacement = replacement.trim(),
                                    isRegex = isRegex,
                                    isEnabled = true
                                )
                            )
                            pattern = ""
                            replacement = ""
                            isRegex = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HiddenForest.Accent,
                        contentColor = HiddenForest.Background
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Regla", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PAQUETES RÁPIDOS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = HiddenForest.TextLow
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TtsPresetPack.entries.forEach { pack ->
                    val icon = when (pack) {
                        TtsPresetPack.PINYIN_SPANISH -> "⚡"
                        TtsPresetPack.WEB_NOVEL_CLEANER -> "🧹"
                        TtsPresetPack.EDITORIAL_ABBREVIATIONS -> "📖"
                    }
                    AssistChip(
                        onClick = { onInstallPresetPack(pack) },
                        label = { Text("$icon ${pack.title}") },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = HiddenForest.Text,
                            containerColor = HiddenForest.SurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Lista de reglas existentes ──────────────────────────────────────────
            Text(
                text = "REGLAS ACTIVAS (${rules.size})",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = HiddenForest.TextLow
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay reglas definidas. Agrega una arriba.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextLow
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(HiddenForest.SurfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, HiddenForest.Outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = rule.pattern,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = HiddenForest.Text
                                    )
                                    if (rule.isRegex) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(HiddenForest.OutlineHi)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "REGEX",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                color = HiddenForest.TextSecondary
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Reemplazar con: ${rule.replacement}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.TextSecondary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = rule.isEnabled,
                                    onCheckedChange = { onSaveRule(rule.copy(isEnabled = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = HiddenForest.Accent,
                                        uncheckedThumbColor = HiddenForest.TextLow,
                                        uncheckedTrackColor = HiddenForest.Background
                                    )
                                )
                                IconButton(onClick = { onDeleteRule(rule) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = Semantic.Error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
