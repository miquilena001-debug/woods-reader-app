package com.example.woodsreader.ui.reader

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.woodsreader.core.system.WoodsKeepAliveManager
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

private val ORGANIC_WAVES = List(40) { i ->
    val x = (i - 20f) / 20f
    (1f - x * x).coerceIn(0f, 1f)
}

@Composable
fun ReaderTtsActiveControls(
    coverPath: String?,
    title: String,
    author: String,
    progress: Float,
    chapterProgress: Float,
    chapterInfo: ChapterInfo,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    keepAliveManager: WoodsKeepAliveManager?,
    isUnrestrictedBattery: Boolean,
    hazeState: HazeState,
    isLoadingTtsModel: Boolean,
    playing: Boolean,
    currentSentence: String,
    currentRange: IntRange?,
    onSkipChapterBackward: () -> Unit,
    onSkipSentenceBackward: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    useSherpaTts: Boolean,
    hasDownloadedSherpaVoice: Boolean,
    onRequestDownloadSherpaVoice: () -> Unit,
    onSkipSentenceForward: () -> Unit,
    onSkipChapterForward: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    showCompactSpeedSlider: Boolean,
    onToggleSpeedSlider: () -> Unit,
    sleepTimerRemaining: Long?,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        // ── 1. CABECERA DEL LIBRO ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!coverPath.isNullOrBlank()) {
                    AsyncImage(
                        model = coverPath,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 52.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(HiddenForest.Accent.copy(0.4f), HiddenForest.Surface)))
                            .border(1.dp, HiddenForest.Accent.copy(0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📖", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifEmpty { "Cargando..." },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp),
                        color = HiddenForest.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = author.ifEmpty { "Autor Desconocido" },
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val chapterTitle = chapterInfo.title?.takeIf { it.isNotBlank() } ?: "Capítulo actual"
                    val progressPct = if (progress > 0f) (progress * 100).roundToInt().coerceIn(1, 100)
                                      else if (chapterProgress > 0f) (chapterProgress * 100).roundToInt().coerceIn(1, 100)
                                      else 0
                    Text(
                        text = "$chapterTitle • $progressPct%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = HiddenForest.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Configuración TTS", tint = HiddenForest.TextSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimizar panel", tint = HiddenForest.TextSecondary, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (!isUnrestrictedBattery) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F382A).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛡️ Evitar que Android cierre Woods Reader con pantalla apagada",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color(0xFF6EE7B7)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Activa \"Sin restricciones\" en 1 toque para que el audio y la lectura nunca se detengan en segundo plano.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { keepAliveManager?.requestIgnoreBatteryOptimizations(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Activar Blindaje",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 2. TARJETA "NARRACIÓN ACTIVA" ──────────────────────────────────────
        ReaderCrystalCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
            if (isLoadingTtsModel) {
                Column(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = HiddenForest.Accent,
                        modifier = Modifier.size(20.dp).padding(bottom = 4.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "CARGANDO MODELO IA...",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                        color = HiddenForest.Accent
                    )
                }
            } else {
                Text(
                    text = if (playing) "NARRACIÓN ACTIVA" else "NARRACIÓN PAUSADA",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                    color = if (playing) HiddenForest.Accent else HiddenForest.TextLow,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp)
                )
            }

            val wavePhase by if (playing) {
                val waveTransition = rememberInfiniteTransition(label = "wave")
                waveTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = (Math.PI * 2.0).toFloat(),
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                    label = "wavePhase"
                )
            } else {
                remember { mutableFloatStateOf(0f) }
            }

            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                val barWidth = size.width / ORGANIC_WAVES.size
                val spacing = 2.dp.toPx()
                val actualBarWidth = (barWidth - spacing).coerceAtLeast(1f)

                ORGANIC_WAVES.forEachIndexed { i, bias ->
                    val phase = kotlin.math.sin(wavePhase + i * 0.2f).toFloat().coerceIn(-1f, 1f)
                    val heightFactor = if (playing) 0.18f + (bias * 0.82f * (0.65f + phase * 0.35f)) else (0.05f + 0.05f * bias)
                    val barHeight = (size.height * heightFactor).coerceAtLeast(3.dp.toPx())
                    val alpha = if (playing) 0.6f + phase * 0.4f else 0.25f
                    val color = if (playing) HiddenForest.Accent.copy(alpha = alpha) else HiddenForest.TextLow.copy(alpha = alpha)

                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x = i * barWidth + spacing / 2,
                            y = (size.height - barHeight) / 2
                        ),
                        size = androidx.compose.ui.geometry.Size(actualBarWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current sentence with word highlighting
            val annotatedString = buildAnnotatedString {
                val range = currentRange
                if (range != null && currentSentence.isNotEmpty()) {
                    val start = range.first.coerceIn(0, currentSentence.length)
                    val end = (range.last + 1).coerceIn(0, currentSentence.length)
                    if (start > 0) append(currentSentence.substring(0, start))
                    withStyle(SpanStyle(color = HiddenForest.Accent, fontWeight = FontWeight.Black, background = HiddenForest.Accent.copy(alpha = 0.12f))) {
                        append(currentSentence.substring(start, end))
                    }
                    if (end < currentSentence.length) append(currentSentence.substring(end))
                } else {
                    append(currentSentence.ifEmpty { "Selecciona un punto para comenzar la lectura" })
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp, max = 110.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, fontSize = 14.5.sp, textAlign = TextAlign.Center),
                    color = HiddenForest.Text.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { chapterProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = HiddenForest.Accent,
                trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 3. BLOQUE DE TRANSPORTE Y ACCIONES RÁPIDAS ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [⏮ Cap. Anterior] (IconButton 42.dp)
            IconButton(
                onClick = onSkipChapterBackward,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Capítulo anterior",
                    tint = HiddenForest.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // [⏪ Oración Anterior] (IconButton 46.dp)
            IconButton(
                onClick = onSkipSentenceBackward,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Oración anterior",
                    tint = HiddenForest.Text,
                    modifier = Modifier.size(28.dp)
                )
            }

            // [▶ Play / Pausa] (Botón circular central de 64.dp con gradiente HiddenForest.Accent y sombra luminosa)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = if (playing) 10.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = HiddenForest.Accent,
                        spotColor = HiddenForest.Accent
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                HiddenForest.Accent,
                                HiddenForest.Accent.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(1.5.dp, HiddenForest.Accent.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = !isLoadingTtsModel) {
                        if (!isLoadingTtsModel) {
                            if (playing) {
                                onPause()
                            } else {
                                // Si no hay ninguna voz offline descargada y se usa Sherpa, guiar automáticamente a la descarga
                                if (useSherpaTts && !hasDownloadedSherpaVoice) {
                                    onRequestDownloadSherpaVoice()
                                } else {
                                    onPlay()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingTtsModel) {
                    CircularProgressIndicator(
                        color = HiddenForest.Background,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pausar" else "Reproducir",
                        tint = HiddenForest.Background,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // [⏩ Oración Siguiente] (IconButton 46.dp)
            IconButton(
                onClick = onSkipSentenceForward,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Oración siguiente",
                    tint = HiddenForest.Text,
                    modifier = Modifier.size(28.dp)
                )
            }

            // [⏭ Cap. Siguiente] (IconButton 42.dp)
            IconButton(
                onClick = onSkipChapterForward,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Capítulo siguiente",
                    tint = HiddenForest.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Fila de Chips Interactivos de Audio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chip de Velocidad (⚡ ${String.format("%.2fx", speed)})
            val speedCycle = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f)
            val speedFormatted = String.format(java.util.Locale.US, "%.2fx", speed)
            Row(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (showCompactSpeedSlider) HiddenForest.Accent.copy(alpha = 0.20f)
                        else HiddenForest.SurfaceVariant.copy(alpha = 0.6f)
                    )
                    .border(
                        1.dp,
                        if (showCompactSpeedSlider) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        val next = speedCycle.firstOrNull { it > speed + 0.01f } ?: speedCycle.first()
                        onSpeedChange(next)
                    }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = speedFormatted,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Accent,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = if (showCompactSpeedSlider) Icons.Default.Tune else Icons.Default.UnfoldMore,
                    contentDescription = "Ajustar velocidad y tono",
                    tint = HiddenForest.Accent.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onToggleSpeedSlider)
                )
            }

            // Chip de Temporizador de Sueño (🌙)
            val isSleepActive = sleepTimerRemaining != null && sleepTimerRemaining != 0L
            val sleepText = when {
                sleepTimerRemaining == -1L -> "Fin Cap"
                sleepTimerRemaining != null && sleepTimerRemaining > 0 -> "${(sleepTimerRemaining + 59999) / 60000}m"
                else -> "Sueño"
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSleepActive) HiddenForest.Accent.copy(alpha = 0.18f)
                        else HiddenForest.SurfaceVariant.copy(alpha = 0.6f)
                    )
                    .border(
                        1.dp,
                        if (isSleepActive) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (sleepTimerRemaining == null || sleepTimerRemaining == 0L) {
                            onSleepTimer(15)
                        } else {
                            val mins = (sleepTimerRemaining / 60000).toInt()
                            when {
                                mins <= 15 -> onSleepTimer(30)
                                mins <= 30 -> onSleepTimer(45)
                                mins <= 45 -> onSleepTimer(60)
                                mins <= 60 -> onSleepTimer(-1)
                                else -> onCancelSleepTimer()
                            }
                        }
                    }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌙", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = sleepText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSleepActive) HiddenForest.Accent else HiddenForest.Text,
                    maxLines = 1
                )
            }

            // Chip de Parada (⏹ Detener)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HiddenForest.SurfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, HiddenForest.Outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable {
                        onStop()
                        onDismiss()
                    }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⏹", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Detener",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
