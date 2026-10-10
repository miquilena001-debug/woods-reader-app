package com.example.woodsreader.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Bedtime
import com.example.woodsreader.model.TtsUiStyle
import com.example.woodsreader.model.TtsHighlightStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding

private val ORGANIC_WAVES = List(40) { i ->
    val x = (i - 20f) / 20f
    (1f - x * x).coerceIn(0f, 1f)
}

/**
 * TTS settings panel — opens as a ModalBottomSheet from the compact TtsBar.
 * Contains: visualizer, sentence display, speed/pitch, sleep timer, auto-scroll,
 * and voice/language configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTtsMacroWindow(
    hazeState: HazeState,
    title: String,
    author: String,
    ttsState: TtsState,
    isLoadingTtsModel: Boolean,
    currentSpeed: Float,
    currentLanguage: String?,
    ttsAutoScroll: Boolean,
    availableVoices: List<String>,
    currentVoiceName: String?,
    onVoiceChange: (String?) -> Unit,
    onOpenRules: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onTtsAutoScrollChange: (Boolean) -> Unit,
    useSherpaTts: Boolean,
    onUseSherpaTtsChange: (Boolean) -> Unit,
    currentPitch: Float,
    onPitchChange: (Float) -> Unit,
    onSkipSentenceForward: () -> Unit,
    onSkipSentenceBackward: () -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    sleepTimerRemaining: Long?,
    currentSentenceFlow: StateFlow<String>,
    currentTtsRangeFlow: StateFlow<IntRange?>,
    chapterInfoFlow: StateFlow<ChapterInfo>,
    ttsUiStyle: TtsUiStyle,
    onTtsUiStyleChange: (TtsUiStyle) -> Unit,
    ttsHighlightStyle: TtsHighlightStyle,
    onTtsHighlightStyleChange: (TtsHighlightStyle) -> Unit,
    hasLastPosition: Boolean,
    onResumeLast: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentSentence by currentSentenceFlow.collectAsStateWithLifecycle(initialValue = "")
    val currentRange by currentTtsRangeFlow.collectAsStateWithLifecycle(initialValue = null)
    val chapterInfo by chapterInfoFlow.collectAsStateWithLifecycle()
    val chapterProgress = chapterInfo.progress

    var showLanguageSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HiddenForest.Surface.copy(alpha = 0.85f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .hazeChild(state = hazeState, style = ReaderDesign.DeepGlassStyle)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
    ) {
        val playing = ttsState == TtsState.PLAYING
        var speed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseAlpha"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // BOOK HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(HiddenForest.Accent.copy(0.4f), HiddenForest.Surface)))
                            .border(1.dp, HiddenForest.Accent.copy(0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📚", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title.ifEmpty { "Cargando..." },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                            color = HiddenForest.Text, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(text = author.ifEmpty { "Autor Desconocido" }, style = MaterialTheme.typography.bodyMedium, color = HiddenForest.TextSecondary)
                    }
                }
                
                // Settings button for language
                IconButton(onClick = { showLanguageSheet = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Configuración TTS", tint = HiddenForest.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // VISUALIZER + CURRENT SENTENCE
            ReaderCrystalCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                if (isLoadingTtsModel) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
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
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = if (playing) HiddenForest.Accent else HiddenForest.TextLow,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    )
                }

                val wavePhase by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = Math.PI.toFloat() * 2f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "wave"
                )
                
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    val barWidth = size.width / ORGANIC_WAVES.size
                    val spacing = 2.dp.toPx()
                    val actualBarWidth = barWidth - spacing
                    
                    ORGANIC_WAVES.forEachIndexed { i, bias ->
                        val phase = kotlin.math.sin(wavePhase + i * 0.2f).toFloat().coerceIn(-1f, 1f)
                        val heightFactor = if (playing) 0.2f + (bias * 0.8f * (0.7f + phase * 0.3f)) else 0.15f * bias
                        val barHeight = size.height * heightFactor
                        val alpha = if (playing) 0.6f + phase * 0.4f else 0.3f
                        val color = if (playing) HiddenForest.Accent.copy(alpha = alpha) else HiddenForest.Outline.copy(alpha = alpha)
                        
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

                Spacer(modifier = Modifier.height(12.dp))

                // Current sentence with word highlighting
                val annotatedString = buildAnnotatedString {
                    val range = currentRange
                    if (range != null && currentSentence.isNotEmpty()) {
                        val start = range.first.coerceIn(0, currentSentence.length)
                        val end = (range.last + 1).coerceIn(0, currentSentence.length)
                        if (start > 0) append(currentSentence.substring(0, start))
                        withStyle(SpanStyle(color = HiddenForest.Accent, fontWeight = FontWeight.Black, background = HiddenForest.Accent.copy(alpha = 0.1f))) {
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
                        .heightIn(min = 40.dp, max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp, fontSize = 15.sp, textAlign = TextAlign.Center),
                        color = HiddenForest.Text.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { chapterProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = HiddenForest.Accent, trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = HiddenForest.Outline.copy(0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                // ── ESTILO DE CONTROLES TTS ──────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, null, tint = HiddenForest.Accent, modifier = Modifier.size(18.dp))
                    Text("Estilo de Controles", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Elige cómo se muestran los controles TTS", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data class StyleOption(
                        val style: TtsUiStyle,
                        val icon: androidx.compose.ui.graphics.vector.ImageVector,
                        val label: String,
                        val desc: String
                    )
                    val options = listOf(
                        StyleOption(TtsUiStyle.BAR, Icons.Default.ViewStream, "Barra", "Controles compactos"),
                        StyleOption(TtsUiStyle.FLOATING, Icons.Default.Adjust, "Flotante", "Botón rápido"),
                        StyleOption(TtsUiStyle.PANEL, Icons.Default.DashboardCustomize, "Panel", "Control completo")
                    )
                    options.forEach { opt ->
                        val isSelected = ttsUiStyle == opt.style
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(0.3f),
                            animationSpec = tween(300),
                            label = "borderColor"
                        )
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) HiddenForest.Accent.copy(0.12f) else HiddenForest.Background,
                            animationSpec = tween(300),
                            label = "bgColor"
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) HiddenForest.Accent else HiddenForest.TextLow,
                            animationSpec = tween(300),
                            label = "iconTint"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                .clickable { onTtsUiStyleChange(opt.style) }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) HiddenForest.Accent.copy(0.15f)
                                        else HiddenForest.SurfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    opt.icon, null,
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                opt.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) HiddenForest.Accent else HiddenForest.Text
                            )
                            Text(
                                opt.desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.TextLow,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                            )
                            // Selection indicator dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) HiddenForest.Accent else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Highlight Style Selector ─────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Palette, null, tint = HiddenForest.Accent, modifier = Modifier.size(18.dp))
                        Text("Estilo de Resaltado", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Elige cómo se remarcan las palabras al leer", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3x2 Grid for the 6 highlight styles
                    val highlightOptions = listOf(
                        Triple(TtsHighlightStyle.BACKGROUND_YELLOW, "Amarillo", Color(0xFFFFF176)),
                        Triple(TtsHighlightStyle.BACKGROUND_ACCENT, "Verde Bosque", HiddenForest.Accent),
                        Triple(TtsHighlightStyle.BACKGROUND_BLUE, "Azul", Color(0xFF64B5F6)),
                        Triple(TtsHighlightStyle.BACKGROUND_ORANGE, "Naranja", Color(0xFFFFB74D)),
                        Triple(TtsHighlightStyle.UNDERLINE, "Subrayado", HiddenForest.Accent),
                        Triple(TtsHighlightStyle.NONE, "Ninguno", Color.Transparent)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0 until 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val optIndex = row * 3 + col
                                    if (optIndex < highlightOptions.size) {
                                        val opt = highlightOptions[optIndex]
                                        val isSelected = ttsHighlightStyle == opt.first
                                        
                                        val borderColor by animateColorAsState(
                                            targetValue = if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(0.3f),
                                            animationSpec = tween(300),
                                            label = "hlBorder"
                                        )
                                        val bgColor by animateColorAsState(
                                            targetValue = if (isSelected) HiddenForest.Accent.copy(0.12f) else HiddenForest.Background,
                                            animationSpec = tween(300),
                                            label = "hlBg"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(bgColor)
                                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                                .clickable { onTtsHighlightStyleChange(opt.first) }
                                                .padding(vertical = 12.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Preview Box
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(HiddenForest.Surface)
                                                    .border(0.5.dp, HiddenForest.Outline.copy(0.2f), RoundedCornerShape(4.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when (opt.first) {
                                                    TtsHighlightStyle.UNDERLINE -> {
                                                        Text(
                                                            text = "Aa",
                                                            fontSize = 11.sp,
                                                            color = HiddenForest.Text,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                                            )
                                                        )
                                                    }
                                                    TtsHighlightStyle.NONE -> {
                                                        Text(
                                                            text = "X",
                                                            fontSize = 11.sp,
                                                            color = HiddenForest.TextLow,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    else -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(2.dp)
                                                                .clip(RoundedCornerShape(2.dp))
                                                                .background(opt.third.copy(alpha = 0.45f))
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                opt.second,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 10.sp
                                                ),
                                                color = if (isSelected) HiddenForest.Accent else HiddenForest.Text,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // COMPACT TRANSPORT CONTROLS (still present for when panel is open)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipSentenceBackward) { Icon(Icons.Default.SkipPrevious, null, tint = HiddenForest.Text, modifier = Modifier.size(28.dp)) }
                Surface(
                    onClick = {
                        if (!isLoadingTtsModel) {
                            if (playing) onPause() else onPlay()
                        }
                    },
                    enabled = !isLoadingTtsModel,
                    shape = CircleShape,
                    color = if (playing) HiddenForest.Accent else HiddenForest.Accent.copy(0.2f),
                    modifier = Modifier.size(52.dp).border(1.dp, HiddenForest.Accent.copy(0.4f), CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoadingTtsModel) {
                            CircularProgressIndicator(color = HiddenForest.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if (playing) HiddenForest.Background else HiddenForest.Accent, modifier = Modifier.size(26.dp))
                        }
                    }
                }
                IconButton(onClick = onSkipSentenceForward) { Icon(Icons.Default.SkipNext, null, tint = HiddenForest.Text, modifier = Modifier.size(28.dp)) }
                IconButton(onClick = onStop) { Icon(Icons.Default.Stop, null, tint = HiddenForest.TextSecondary, modifier = Modifier.size(24.dp)) }
            }

            // Chip "Continuar desde donde pausé" — solo visible cuando hay posición guardada y TTS no está activo
            androidx.compose.animation.AnimatedVisibility(
                visible = hasLastPosition && (ttsState == TtsState.STOPPED || ttsState == TtsState.IDLE),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HiddenForest.Accent.copy(alpha = 0.08f))
                            .border(1.dp, HiddenForest.Accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { onResumeLast() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = HiddenForest.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continuar desde donde pausé",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = HiddenForest.Accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SETTINGS: SPEED & SLEEP TIMER
            ReaderCrystalCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                PremiumSlider(
                    label = "Velocidad de Lectura", value = speed,
                    onValueChange = { speed = it; onSpeedChange(it) }, valueRange = 0.5f..2.5f
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = HiddenForest.Outline.copy(0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, null, tint = if (sleepTimerRemaining != null) HiddenForest.Accent else HiddenForest.TextLow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Temporizador de Sueño", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                    Spacer(modifier = Modifier.weight(1f))
                    if (sleepTimerRemaining != null) {
                        val minutes = (sleepTimerRemaining / 60000).toInt()
                        val seconds = ((sleepTimerRemaining % 60000) / 1000).toInt()
                        Text(String.format("%02d:%02d", minutes, seconds), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Accent)
                        IconButton(onClick = onCancelSleepTimer, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { mins ->
                        val isActive = sleepTimerRemaining != null
                        OutlinedButton(
                            onClick = { onSleepTimer(mins) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(1.dp, if (isActive) HiddenForest.Accent.copy(0.3f) else HiddenForest.Outline.copy(0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isActive) HiddenForest.Accent else HiddenForest.TextSecondary)
                        ) { Text("${mins}m", fontSize = 12.sp) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = HiddenForest.Outline.copy(0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ArrowDownward, null, tint = if (ttsAutoScroll) HiddenForest.Accent else HiddenForest.TextLow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Desplazamiento Automático", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
                        Text("Mover la página al avanzar la lectura", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = ttsAutoScroll,
                        onCheckedChange = onTtsAutoScrollChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HiddenForest.Background,
                            checkedTrackColor = HiddenForest.Accent,
                            uncheckedThumbColor = HiddenForest.TextLow,
                            uncheckedTrackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
    
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = HiddenForest.SurfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                Text("Configuración de Voz y TTS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
                Spacer(modifier = Modifier.height(16.dp))

                // ── SECCIÓN 0: MOTOR DE VOZ IA NEURONAL ─────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (useSherpaTts) Brush.linearGradient(
                                listOf(
                                    HiddenForest.Accent.copy(alpha = 0.15f),
                                    HiddenForest.Accent.copy(alpha = 0.05f)
                                )
                            ) else Brush.linearGradient(
                                listOf(
                                    HiddenForest.Background,
                                    HiddenForest.Background
                                )
                            )
                        )
                        .border(
                            1.dp,
                            if (useSherpaTts) HiddenForest.Accent.copy(0.4f) else HiddenForest.Outline.copy(0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (useSherpaTts) HiddenForest.Accent.copy(0.2f)
                                    else HiddenForest.Background
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🧠",
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Voz Neuronal IA",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text
                            )
                            Text(
                                if (useSherpaTts) "Sherpa ONNX · Español MX · Offline"
                                else "Motor del sistema activo",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (useSherpaTts) HiddenForest.Accent else HiddenForest.TextLow
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = useSherpaTts,
                            onCheckedChange = onUseSherpaTtsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HiddenForest.Background,
                                checkedTrackColor = HiddenForest.Accent,
                                uncheckedThumbColor = HiddenForest.TextLow,
                                uncheckedTrackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                var currentPitchState by remember(currentPitch) { mutableFloatStateOf(currentPitch) }
                PremiumSlider(
                    label = "Tono de la Voz (Pitch)",
                    value = currentPitchState,
                    onValueChange = {
                        currentPitchState = it
                        onPitchChange(it)
                    },
                    valueRange = 0.5f..1.5f
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── SECCIÓN 1: IDIOMA
                Text("Idioma del Motor", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = HiddenForest.Accent)
                Spacer(modifier = Modifier.height(8.dp))
                
                val languages = listOf(
                    null to "Predeterminado del libro",
                    "es" to "Español",
                    "en" to "Inglés",
                    "fr" to "Francés",
                    "de" to "Alemán",
                    "it" to "Italiano"
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { (code, name) ->
                        val isSelected = currentLanguage == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) HiddenForest.Accent.copy(0.2f) else HiddenForest.Background)
                                .border(1.dp, if (isSelected) HiddenForest.Accent else HiddenForest.Outline, RoundedCornerShape(10.dp))
                                .clickable { onLanguageChange(code) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) HiddenForest.Accent else HiddenForest.Text)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // ── SECCIÓN 2: VOCES DISPONIBLES
                if (availableVoices.isNotEmpty()) {
                    Text("Voces del Sistema", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = HiddenForest.Accent)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableVoices.forEach { voiceName ->
                            val isSelected = currentVoiceName == voiceName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) HiddenForest.Accent.copy(0.15f) else Color.Transparent)
                                    .clickable { onVoiceChange(voiceName) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = voiceName.substringAfter(" - ").substringBefore("#"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) HiddenForest.Accent else HiddenForest.Text
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Check, null, tint = HiddenForest.Accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
                
                // ── SECCIÓN 3: DICCIONARIO DE REGLAS
                Button(
                    onClick = {
                        showLanguageSheet = false
                        onOpenRules()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HiddenForest.Accent,
                        contentColor = HiddenForest.Background
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reglas de Pronunciación", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
