package com.example.woodsreader.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun BookAudioExportDialog(
    book: BookEntity,
    availableVoices: List<SherpaVoiceOption>,
    initialVoice: SherpaVoiceOption,
    onConfirm: (maxChapters: Int, speed: Float, voice: SherpaVoiceOption, includeLyrics: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // 0: 1 Capítulo, 1: 10 Capítulos, 2: Libro Completo
    var selectedScope by remember { mutableIntStateOf(1) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var selectedVoice by remember(initialVoice) {
        mutableStateOf(
            if (availableVoices.any { it.id == initialVoice.id }) initialVoice
            else availableVoices.firstOrNull() ?: initialVoice
        )
    }
    var includeLyrics by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                            Color(0xFF38BDF8).copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Header (Apple Liquid Glass) ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                    ),
                                    RoundedCornerShape(13.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Convertir a Audiolibro",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Voz IA Local · AAC Comprimido (.M4A)",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextSecondary
                            )
                        }
                    }

                    // Botón circular Apple Glass de cierre
                    Box(
                        modifier = Modifier
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

                // ── Tarjeta de información del libro ─────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Portada en miniatura (estilo carátula Apple Glass)
                        val coverFile = remember(book.coverCachePath) {
                            book.coverCachePath?.takeIf { it.isNotBlank() }?.let { File(it) }
                        }
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (coverFile != null && coverFile.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(coverFile)
                                        .size(140, 200)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Portada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

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
                                    text = book.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.16f))
                                        .border(0.75.dp, Color(0xFF38BDF8).copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = book.format.name.ifBlank { "EPUB" },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                            if (book.author.isNotBlank()) {
                                Text(
                                    text = book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34D399))
                                )
                                Text(
                                    text = "Portada incrustada en audio M4A",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color(0xFF34D399).copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // ── Selector de modelo de voz IA (Apple Liquid Glass) ─────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modelo de Voz IA:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        if (availableVoices.isNotEmpty()) {
                            Text(
                                text = "${availableVoices.size} instaladas",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    if (availableVoices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFC8A84B).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFC8A84B).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "No hay modelos de voz descargados. Descarga uno en Ajustes > Voces IA.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2C872)
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableVoices) { voice ->
                                val isSelected = selectedVoice.id == voice.id
                                val bgBrush = if (isSelected) {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF38BDF8).copy(alpha = 0.28f),
                                            Color(0xFF0284C7).copy(alpha = 0.14f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.05f),
                                            Color.White.copy(alpha = 0.02f)
                                        )
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 150.dp, max = 210.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bgBrush)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            brush = if (isSelected) {
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF38BDF8), Color(0xFF34D399).copy(alpha = 0.6f))
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                                                )
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { selectedVoice = voice }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = voice.displayName,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = voice.accent,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (isSelected) Color(0xFFBAE6FD) else HiddenForest.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Selector de alcance de capítulos ────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Capítulos a exportar:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val scopes = listOf("1 Capítulo", "10 Caps", "Libro Completo")
                        scopes.forEachIndexed { index, label ->
                            val isSelected = selectedScope == index
                            val bgBrush = if (isSelected) {
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF38BDF8).copy(alpha = 0.35f),
                                        Color(0xFF0284C7).copy(alpha = 0.15f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.02f))
                                )
                            }

                            val borderBrush = if (isSelected) {
                                Brush.verticalGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.40f))
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(bgBrush)
                                    .border(if (isSelected) 1.5.dp else 1.dp, borderBrush, RoundedCornerShape(14.dp))
                                    .clickable { selectedScope = index }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // ── Selector de velocidad de narración ───────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Velocidad de narración:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val speeds = listOf(1.0f, 1.2f, 1.5f, 1.8f)
                        speeds.forEach { sp ->
                            val isSelected = selectedSpeed == sp
                            val bgBrush = if (isSelected) {
                                Brush.verticalGradient(
                                    listOf(
                                        HiddenForest.Accent.copy(alpha = 0.30f),
                                        HiddenForest.Accent.copy(alpha = 0.12f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.02f))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgBrush)
                                    .border(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) HiddenForest.Accent else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedSpeed = sp }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sp}x",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) HiddenForest.Accent else Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // ── Opción de Letra / Subtítulos Sincronizados (Apple Glass) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (includeLyrics) Color(0xFF38BDF8).copy(alpha = 0.09f)
                            else Color.White.copy(alpha = 0.03f)
                        )
                        .border(
                            width = 1.dp,
                            brush = if (includeLyrics) {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF38BDF8).copy(alpha = 0.45f), Color(0xFF34D399).copy(alpha = 0.30f))
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                                )
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { includeLyrics = !includeLyrics }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (includeLyrics) Color(0xFF38BDF8).copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.06f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = if (includeLyrics) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Subtítulos Sincronizados (LRC)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Incrustados internamente en el archivo .M4A",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (includeLyrics) Color(0xFFBAE6FD) else HiddenForest.TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = includeLyrics,
                            onCheckedChange = { includeLyrics = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }
                }

                // ── Botones de Acción (Apple Liquid Buttons) ───────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Cancelar (Frosted Glass)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancelar",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Botón Primario (Liquid Glass CTA)
                    val chaptersCount = when (selectedScope) {
                        0 -> 1
                        1 -> 10
                        else -> 9999
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = Color.Black.copy(alpha = 0.4f),
                                spotColor = Color(0xFF38BDF8).copy(alpha = 0.5f)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF10B981),
                                        Color(0xFF059669)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                            .clickable {
                                onConfirm(chaptersCount, selectedSpeed, selectedVoice, includeLyrics)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color(0xFF042414),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Iniciar Conversión",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF042414)
                            )
                        }
                    }
                }
            }
        }
    }
}
