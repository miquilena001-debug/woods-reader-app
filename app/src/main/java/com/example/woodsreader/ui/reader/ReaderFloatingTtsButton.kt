package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.woodsreader.R
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

// ── Animated equalizer bars shown below the button when playing ──────────────
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    // Three bars with staggered phases
    val barHeights = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + index * 120,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier.height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEach { animatedFraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(animatedFraction.value)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(HiddenForest.Accent)
            )
        }
    }
}

// ── Main floating TTS button ─────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderFloatingTtsButton(
    modifier: Modifier = Modifier,
    ttsState: TtsState,
    isLoadingTtsModel: Boolean,
    showTtsMacroWindow: Boolean,
    isReflowMode: Boolean,
    chapterProgress: Float,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isTtsActive = ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED
    val isPlaying = ttsState == TtsState.PLAYING
    val haptic = LocalHapticFeedback.current

    // ── Progress arc animation ───────────────────────────────────────────────
    val animatedProgress by animateFloatAsState(
        targetValue = chapterProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    // ── Pulsing glow when playing ────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 32f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )


    AnimatedVisibility(
        visible = isTtsActive && !showTtsMacroWindow && !isReflowMode,
        enter = fadeIn(tween(300)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(tween(250)) + scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(60.dp)
            ) {
                // ── Ring progress arc (behind the button) ────────────────────
                val progressColor = if (isPlaying) HiddenForest.Accent else HiddenForest.TextSecondary
                val trackColor = HiddenForest.Outline
                val sweepAngle = animatedProgress * 360f

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.matchParentSize()
                ) {
                    val strokeWidth = 2.5.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(inset, inset)

                    // Track ring
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress ring
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // ── Pulsing glow layer (only when playing) ───────────────────
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .drawWithCache {
            val brush = Brush.radialGradient(
                                        colors = listOf(
                                            HiddenForest.Accent.copy(alpha = glowAlpha),
                                            Color.Transparent
                                        ),
                                        radius = glowRadius.dp.toPx()
                                    )
            onDrawBehind {
                drawCircle(brush = brush)
            }
        }
                    )
                }

                // ── Glassmorphism circle button ──────────────────────────────
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
                        .background(HiddenForest.Surface.copy(alpha = 0.85f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    (if (isPlaying) HiddenForest.Accent else HiddenForest.TextSecondary)
                                        .copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                        .combinedClickable(
                            onClick = { onPlayPause() },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSettings()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Radial tint behind icon when playing
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            HiddenForest.Accent.copy(alpha = 0.20f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // ── Loading spinner / Play-Pause icon ────────────────────
                    if (isLoadingTtsModel) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = HiddenForest.Accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Crossfade(
                            targetState = isPlaying,
                            animationSpec = tween(200),
                            label = "playPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (playing) R.string.tts_action_pause else R.string.tts_action_play
                                ),
                                tint = if (playing) HiddenForest.Accent else HiddenForest.TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // ── Animated equalizer bars (only when playing) ──────────────────
            AnimatedVisibility(
                visible = isPlaying && !isLoadingTtsModel,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                EqualizerBars()
            }
        }
    }
}
