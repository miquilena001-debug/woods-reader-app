package com.example.woodsreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.woodsreader.core.tts.AudioExportState
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun AudioExportProgressDialog(
    bookTitle: String,
    exportState: AudioExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var isMinimized by remember { mutableStateOf(false) }

    // Si está en estado Idle o sin actividad, no renderizar
    if (exportState is AudioExportState.Idle) return

    if (isMinimized && exportState is AudioExportState.Exporting) {
        // ── Widget flotante minimizado (Apple Liquid Glass Dock) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.75f),
                        spotColor = Color(0xFF38BDF8).copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF2182E22),
                                Color(0xF8102118),
                                Color(0xFC0B1510)
                            )
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color(0xFF38BDF8).copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { isMinimized = false }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Squircle con icono animado de audio
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Título y progreso
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${(exportState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Text(
                            text = "Capítulo ${exportState.currentChapter} de ${exportState.totalChapters} · Oración ${exportState.currentSentence}/${exportState.totalSentences}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )

                        LinearProgressIndicator(
                            progress = { exportState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF38BDF8),
                            trackColor = Color.White.copy(alpha = 0.08f)
                        )
                    }

                    // Botones: Maximizar y Cancelar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .clickable { isMinimized = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Maximizar",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B1E1E).copy(alpha = 0.45f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), CircleShape)
                                .clickable { onCancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ── Diálogo Modal Completo (Apple Liquid Glass) ──
        Dialog(
            onDismissRequest = {
                if (exportState !is AudioExportState.Exporting) {
                    onDismiss()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = exportState !is AudioExportState.Exporting,
                dismissOnClickOutside = exportState !is AudioExportState.Exporting
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight()
                    .shadow(
                        elevation = 32.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.75f),
                        spotColor = Color(0xFF38BDF8).copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF0182E22),
                                Color(0xF6102118),
                                Color(0xFB0B1510)
                            )
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color(0xFF38BDF8).copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
            ) {
                // Botón superior derecho: Minimizar (si está exportando) o Cerrar
                if (exportState is AudioExportState.Exporting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable { isMinimized = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimizar",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (exportState) {
                        is AudioExportState.Exporting -> {
                            // Icono squircle glowing
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                        ),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Títulos
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Generando Audiolibro .M4A",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = bookTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Tarjeta de progreso frosted glass
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
                                        ),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { exportState.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF38BDF8),
                                        trackColor = Color.White.copy(alpha = 0.08f),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Capítulo ${exportState.currentChapter} de ${exportState.totalChapters}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF38BDF8).copy(alpha = 0.18f))
                                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${(exportState.progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Oración ${exportState.currentSentence} de ${exportState.totalSentences}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiddenForest.TextSecondary
                                    )
                                }
                            }

                            // Paso actual en cápsula frosted
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = exportState.label.ifBlank { "Sintetizando voz y codificando AAC..." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Botones de acción inferiores: Minimizar y Cancelar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                        .clickable { isMinimized = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Minimizar",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF3B1E1E).copy(alpha = 0.40f))
                                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                        .clickable { onCancel() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Cancelar",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFFCA5A5)
                                    )
                                }
                            }
                        }

                        is AudioExportState.Completed -> {
                            // Checkmark de éxito con glow
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(HiddenForest.Accent.copy(alpha = 0.20f))
                                    .border(1.5.dp, HiddenForest.Accent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = HiddenForest.Accent,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Text(
                                text = "¡Audiolibro Generado con Éxito!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            // Información del archivo generado
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = exportState.fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "${exportState.sizeKb} KB · Formato AAC (.M4A)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HiddenForest.Accent,
                                        textAlign = TextAlign.Center
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF34D399))
                                        )
                                        Text(
                                            text = "Todo-en-1: Portada incrustada en el propio archivo .m4a",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = Color(0xFF34D399),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    if (exportState.hasLyrics) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Subtitles,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Subtítulos sincronizados (LRC) integrados internamente",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = Color(0xFF38BDF8),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Guardado en la carpeta Música / WoodsReader (1 solo archivo)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiddenForest.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Botón Aceptar (Liquid Emerald CTA)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        spotColor = Color(0xFF10B981).copy(alpha = 0.5f)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF34D399),
                                                Color(0xFF10B981),
                                                Color(0xFF059669)
                                            )
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aceptar",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF042414)
                                )
                            }
                        }

                        is AudioExportState.Error -> {
                            // Icono de error con tono carmesí
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B1E1E).copy(alpha = 0.60f))
                                    .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.40f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "Error al Generar Audio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            Text(
                                text = exportState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.80f),
                                textAlign = TextAlign.Center
                            )

                            // Botón Cerrar (Frosted Glass)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cerrar",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White.copy(alpha = 0.90f)
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}
