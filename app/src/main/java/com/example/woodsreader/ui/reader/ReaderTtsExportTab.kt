package com.example.woodsreader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.core.tts.AudioExportState
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun ReaderTtsExportTab(
    audioExportState: AudioExportState,
    isSherpaVoiceDownloaded: (SherpaVoiceOption) -> Boolean,
    isProUnlocked: Boolean,
    onExportAudioChapters: (Int) -> Unit,
    onCancelAudioExport: () -> Unit,
    onResetAudioExportState: () -> Unit,
    onRequestProUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // ── SECCIÓN 4: EXPORTAR AUDIOLIBRO COMPRIMIDO (.M4A) ──
        Text(
            text = "⚡ Exportar Audiolibro Comprimido (.M4A)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = HiddenForest.Text
        )
        Spacer(modifier = Modifier.height(8.dp))

        var showNoVoiceWarning by remember { mutableStateOf(false) }
        val hasDownloadedVoice = remember(isSherpaVoiceDownloaded) {
            SherpaVoiceOption.entries.any { isSherpaVoiceDownloaded(it) }
        }

        if (showNoVoiceWarning && !hasDownloadedVoice) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E281F),
                border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Descarga una Voz Offline",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = HiddenForest.Accent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Para exportar audiolibros .M4A comprimidos a máxima velocidad sin cortes, descarga al menos una voz offline (como Alejandro o Carlos) en el modo '🎙 IA Local'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showNoVoiceWarning = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Entendido", color = HiddenForest.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        when (val export = audioExportState) {
            is AudioExportState.Idle -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasDownloadedVoice) {
                                showNoVoiceWarning = false
                                onExportAudioChapters(1)
                            } else {
                                showNoVoiceWarning = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HiddenForest.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HiddenForest.Accent
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Cap. Actual",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (isProUnlocked) {
                                if (hasDownloadedVoice) {
                                    showNoVoiceWarning = false
                                    onExportAudioChapters(10)
                                } else {
                                    showNoVoiceWarning = true
                                }
                            } else {
                                onRequestProUnlock()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HiddenForest.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HiddenForest.Accent
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isProUnlocked) "10 Capítulos" else "10 Caps 🔒",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (isProUnlocked) {
                                if (hasDownloadedVoice) {
                                    showNoVoiceWarning = false
                                    onExportAudioChapters(9999)
                                } else {
                                    showNoVoiceWarning = true
                                }
                            } else {
                                onRequestProUnlock()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HiddenForest.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HiddenForest.Accent
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isProUnlocked) "Todo el Libro" else "Libro 🔒",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            is AudioExportState.Exporting -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = HiddenForest.Background,
                    border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Cap. ${export.currentChapter}/${export.totalChapters} (${export.label}) — Oración ${export.currentSentence}/${export.totalSentences}",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.Accent,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onCancelAudioExport,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Cancelar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { export.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = HiddenForest.Accent,
                            trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                        )
                    }
                }
            }
            is AudioExportState.Completed -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = HiddenForest.Background,
                    border = BorderStroke(1.dp, Color(0xFF81C784))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✓ Guardado en Music/WoodsReader: ${export.fileName} (${export.sizeKb} KB)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF81C784),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onResetAudioExportState) {
                            Text("Cerrar", color = HiddenForest.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            is AudioExportState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = HiddenForest.Background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Error: ${export.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onResetAudioExportState) {
                            Text("Reintentar", color = HiddenForest.Accent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
