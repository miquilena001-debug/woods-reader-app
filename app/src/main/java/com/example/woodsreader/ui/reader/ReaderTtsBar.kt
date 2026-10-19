package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 2-Level Liquid Glass TTS bottom bar with swipe gestures.
 *
 * Level 1: Mini-Ticker with current sentence and fine progress bar
 * Level 2: Clean transport controls [Stop] [⏮] [⏪] [⏯ 48dp] [⏩] [⏭] [Speed 2.25x] [🎛 Panel]
 * Gestures: Swipe Up -> Open Panel, Swipe Down -> Minimize to Floating
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderTtsBar(
    modifier: Modifier = Modifier,
    ttsState: TtsState,
    isLoadingTtsModel: Boolean,
    currentSentenceFlow: StateFlow<String>,
    currentTtsRangeFlow: StateFlow<IntRange?>,
    chapterInfoFlow: StateFlow<ChapterInfo>,
    currentSpeed: Float,
    hazeState: HazeState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipWordForward: () -> Unit,
    onSkipWordBackward: () -> Unit,
    onSkipChapterForward: (() -> Unit)? = null,
    onSkipChapterBackward: (() -> Unit)? = null,
    onLock: () -> Unit = {},
    onExpandSettings: () -> Unit,
    onSpeedUp: (() -> Unit)? = null,
    onSpeedDown: (() -> Unit)? = null,
    onSpeedChange: ((Float) -> Unit)? = null,
    onSwipeUp: () -> Unit = onExpandSettings,
    onSwipeDown: () -> Unit = {}
) {
    val currentSentence by currentSentenceFlow.collectAsStateWithLifecycle(initialValue = "")
    @Suppress("UNUSED_VARIABLE")
    val currentRange by currentTtsRangeFlow.collectAsStateWithLifecycle(initialValue = null)
    val chapterInfo by chapterInfoFlow.collectAsStateWithLifecycle()
    val chapterProgress = chapterInfo.progress

    val playing = ttsState == TtsState.PLAYING
    var dragAccumulatorY by remember { mutableFloatStateOf(0f) }

    val speedCycle = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f)
    val speedFormatted = String.format(Locale.US, "%.2fx", currentSpeed)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 10.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = HiddenForest.Accent.copy(alpha = 0.35f),
                spotColor = HiddenForest.Accent.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(24.dp))
            .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF102218).copy(alpha = 0.96f),
                        Color(0xFF09130E).copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulatorY = 0f },
                    onDragEnd = { dragAccumulatorY = 0f },
                    onDragCancel = { dragAccumulatorY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatorY += dragAmount
                        if (dragAccumulatorY < -25f) {
                            dragAccumulatorY = 0f
                            onSwipeUp()
                        } else if (dragAccumulatorY > 25f) {
                            dragAccumulatorY = 0f
                            onSwipeDown()
                        }
                    }
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── NIVEL 1: Mini-Ticker superior + Barra de progreso fina ────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentSentence.ifBlank { "Reproduciendo narración..." },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val chapterTitle = chapterInfo.title?.takeIf { it.isNotBlank() }
                if (chapterTitle != null) {
                    Text(
                        text = chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = HiddenForest.Accent.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            LinearProgressIndicator(
                progress = { chapterProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape),
                color = HiddenForest.Accent,
                trackColor = Color.White.copy(alpha = 0.12f),
                drawStopIndicator = {}
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── NIVEL 2: Fila de Controles Limpios ────────────────────────
            // [⏹ Stop] · [⏮ Cap] · [⏪ Oración] · [⏯ Play/Pause 48.dp] · [⏩ Oración] · [⏭ Cap] · [Chip Velocidad 2.25x] · [🎛 Abrir Panel]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. [✕ Detener/Cerrar]
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Detener y cerrar",
                        tint = HiddenForest.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 2. [⏮ Cap]
                IconButton(
                    onClick = { onSkipChapterBackward?.invoke() ?: onSkipWordBackward() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Capítulo anterior",
                        tint = HiddenForest.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. [⏪ Oración]
                IconButton(
                    onClick = onSkipBackward,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Oración anterior",
                        tint = HiddenForest.Text,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 4. [⏯ Play/Pause 48.dp]
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = if (playing) 8.dp else 2.dp,
                            shape = CircleShape,
                            ambientColor = HiddenForest.Accent,
                            spotColor = HiddenForest.Accent
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    HiddenForest.Accent,
                                    HiddenForest.Accent.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(1.dp, HiddenForest.Accent.copy(alpha = 0.4f), CircleShape)
                        .clickable(enabled = !isLoadingTtsModel) {
                            if (playing) onPause() else onPlay()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingTtsModel) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = HiddenForest.Background,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                            tint = HiddenForest.Background,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 5. [⏩ Oración]
                IconButton(
                    onClick = onSkipForward,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Oración siguiente",
                        tint = HiddenForest.Text,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 6. [⏭ Cap]
                IconButton(
                    onClick = { onSkipChapterForward?.invoke() ?: onSkipWordForward() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Capítulo siguiente",
                        tint = HiddenForest.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 7. [Chip Velocidad 2.25x]
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HiddenForest.Accent.copy(alpha = 0.12f))
                        .border(0.5.dp, HiddenForest.Accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable {
                            val next = speedCycle.firstOrNull { it > currentSpeed + 0.01f } ?: speedCycle.first()
                            if (onSpeedChange != null) {
                                onSpeedChange(next)
                            } else {
                                onSpeedUp?.invoke()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speedFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = HiddenForest.Accent
                    )
                }

                // 8. [🎛 Abrir Panel]
                IconButton(
                    onClick = onExpandSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Abrir panel completo",
                        tint = HiddenForest.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
