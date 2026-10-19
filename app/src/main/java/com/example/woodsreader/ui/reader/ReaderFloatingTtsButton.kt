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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Text
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import java.util.Locale

// ── Animated equalizer bars shown inside the button when playing ─────────────
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

// ── Main floating TTS button (Unified Liquid Glass Capsule) ──────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderFloatingTtsButton(
    modifier: Modifier = Modifier,
    ttsState: TtsState,
    isLoadingTtsModel: Boolean,
    showTtsMacroWindow: Boolean,
    isReflowMode: Boolean = false,
    chapterProgress: Float,
    speed: Float = 1.0f,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onSpeedClick: () -> Unit = {},
    onStop: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val isTtsActive = ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED
    val isPlaying = ttsState == TtsState.PLAYING
    val haptic = LocalHapticFeedback.current

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val minOffsetX = with(density) { -(screenWidthPx - 210.dp.toPx()) }
    val maxOffsetX = with(density) { 10.dp.toPx() }
    val minOffsetY = with(density) { -(screenHeightPx - 180.dp.toPx()) }
    val maxOffsetY = with(density) { 10.dp.toPx() }

    // Drag offsets for free positioning (bounded within safe screen margins)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Subtle green breathing animation while narrating actively
    val infiniteTransition = rememberInfiniteTransition(label = "floatingPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val activeBorderColor = if (isPlaying) {
        HiddenForest.Accent.copy(alpha = pulseGlow)
    } else {
        HiddenForest.Accent.copy(alpha = 0.35f)
    }
    val ambientGlow = if (isPlaying) {
        HiddenForest.Accent.copy(alpha = pulseGlow * 0.45f)
    } else {
        HiddenForest.Accent.copy(alpha = 0.20f)
    }

    // Progress arc animation
    val animatedProgress by animateFloatAsState(
        targetValue = chapterProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    AnimatedVisibility(
        visible = isTtsActive && !showTtsMacroWindow,
        enter = fadeIn(tween(350)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(tween(250)) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(minOffsetX, maxOffsetX)
                    offsetY = (offsetY + dragAmount.y).coerceIn(minOffsetY, maxOffsetY)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = if (isPlaying) 16.dp else 8.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = ambientGlow,
                    spotColor = ambientGlow
                )
                .clip(RoundedCornerShape(28.dp))
                .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
                .background(Color(0xFF0E1F16).copy(alpha = 0.94f))
                .border(
                    width = if (isPlaying) 1.5.dp else 1.dp,
                    color = activeBorderColor,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Botón compacto X (28.dp) para detener TTS
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Detener TTS",
                        tint = HiddenForest.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // 2. Badge de velocidad
                val speedFormatted = String.format(Locale.US, "%.2fx", speed)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HiddenForest.Accent.copy(alpha = 0.15f))
                        .border(0.5.dp, HiddenForest.Accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSpeedClick()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speedFormatted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HiddenForest.Accent
                    )
                }

                // 3. Botón compacto para expandir panel completo
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Abrir panel completo",
                        tint = HiddenForest.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 3. Botón circular Play / Pausa (44.dp) con borde de progreso
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp)
                ) {
                    val progressColor = if (isPlaying) HiddenForest.Accent else HiddenForest.TextSecondary
                    val trackColor = HiddenForest.Outline.copy(alpha = 0.3f)
                    val sweepAngle = animatedProgress * 360f

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.matchParentSize()
                    ) {
                        val strokeWidth = 2.dp.toPx()
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

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) HiddenForest.Accent
                                else HiddenForest.SurfaceVariant
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
                        if (isLoadingTtsModel) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = if (isPlaying) HiddenForest.Background else HiddenForest.Accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Crossfade(
                                targetState = isPlaying,
                                animationSpec = tween(200),
                                label = "playPauseIcon"
                            ) { playingState ->
                                Icon(
                                    imageVector = if (playingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(
                                        if (playingState) R.string.tts_action_pause else R.string.tts_action_play
                                    ),
                                    tint = if (playingState) HiddenForest.Background else HiddenForest.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
