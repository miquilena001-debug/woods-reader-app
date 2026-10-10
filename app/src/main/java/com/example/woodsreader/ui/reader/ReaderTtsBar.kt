package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.flow.StateFlow

/**
 * Ultra-compact TTS bottom bar — Moon+ Reader style.
 *
 * Layout (single row, ~48dp tall):
 *   [Stop] [◀◀] [⏯ Play/Pause] [▶▶] [1.0x ▼/▲] [⚙]
 *
 * The text highlighting happens IN the book via Readium Decorations,
 * so this bar is purely transport controls + progress — exactly like Moon+ Reader.
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
    onLock: () -> Unit,
    onExpandSettings: () -> Unit,
    onSpeedUp: (() -> Unit)? = null,
    onSpeedDown: (() -> Unit)? = null
) {
    // Still collect these so existing call sites don't break
    @Suppress("UNUSED_VARIABLE")
    val currentSentence by currentSentenceFlow.collectAsStateWithLifecycle(initialValue = "")
    @Suppress("UNUSED_VARIABLE")
    val currentRange by currentTtsRangeFlow.collectAsStateWithLifecycle(initialValue = null)
    val chapterInfo by chapterInfoFlow.collectAsStateWithLifecycle()
    val chapterProgress = chapterInfo.progress

    val playing = ttsState == TtsState.PLAYING

    // Pulse animation for play button glow
    val infiniteTransition = rememberInfiniteTransition(label = "ttsBarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.10f),
                    1.0f to Color.Transparent
                ),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(HiddenForest.Surface.copy(alpha = 0.94f))
            .navigationBarsPadding(),
        shadowElevation = 0.dp
    ) {
        Column {
            // ── Progress bar (thin accent line at the very top) ──────────
            LinearProgressIndicator(
                progress = { chapterProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = HiddenForest.Accent,
                trackColor = HiddenForest.Outline.copy(alpha = 0.12f),
                drawStopIndicator = {}
            )

            // ── Single-row transport controls ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ─── Left: Stop & Lock ───────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Detener",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Bloquear Pantalla",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ─── Center: transport cluster ───────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip word backward (⏪)
                    IconButton(
                        onClick = onSkipWordBackward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.FastRewind,
                            contentDescription = "Retroceder Palabra",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Skip sentence backward (◀◀)
                    IconButton(
                        onClick = onSkipBackward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Retroceder Oración",
                            tint = HiddenForest.Text,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // ─── Play / Pause (hero button) ──────────────────────
                    Box(contentAlignment = Alignment.Center) {
                        // Pulsing glow behind button when playing
                        if (playing) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                HiddenForest.Accent.copy(alpha = pulseAlpha),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                        Surface(
                            onClick = {
                                if (!isLoadingTtsModel) {
                                    if (playing) onPause() else onPlay()
                                }
                            },
                            enabled = !isLoadingTtsModel,
                            shape = CircleShape,
                            color = if (playing) HiddenForest.Accent else HiddenForest.Accent.copy(0.18f),
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, HiddenForest.Accent.copy(0.35f), CircleShape)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isLoadingTtsModel) {
                                    CircularProgressIndicator(
                                        color = HiddenForest.Accent,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) "Pausar" else "Reproducir",
                                        tint = if (playing) HiddenForest.Background else HiddenForest.Accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Skip sentence forward (▶▶)
                    IconButton(
                        onClick = onSkipForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Avanzar Oración",
                            tint = HiddenForest.Text,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Skip word forward (⏩)
                    IconButton(
                        onClick = onSkipWordForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "Avanzar Palabra",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ─── Right: Speed chip + Settings ────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Speed chip (tappable to open settings, tap to increase, long click to decrease)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HiddenForest.Accent.copy(alpha = 0.10f))
                            .border(
                                0.5.dp,
                                HiddenForest.Accent.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = { onSpeedUp?.invoke() },
                                onLongClick = { onSpeedDown?.invoke() }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${String.format("%.2f", currentSpeed)}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = HiddenForest.Accent
                        )
                    }

                    // Settings gear
                    IconButton(
                        onClick = onExpandSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configuración TTS",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
