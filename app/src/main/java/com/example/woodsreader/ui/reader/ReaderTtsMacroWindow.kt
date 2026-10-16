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
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.EdgeVoiceOption
import com.example.woodsreader.core.tts.AudioExportState
import com.example.woodsreader.core.tts.AmbiencePreset
import com.example.woodsreader.core.tts.TtsPresetPack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

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
    coverPath: String? = null,
    title: String,
    author: String,
    progress: Float = 0f,
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
    sherpaDownloadState: SherpaDownloadState = SherpaDownloadState.Idle,
    currentPitch: Float,
    onPitchChange: (Float) -> Unit,
    onSkipSentenceForward: () -> Unit,
    onSkipSentenceBackward: () -> Unit,
    onSkipChapterForward: () -> Unit = {},
    onSkipChapterBackward: () -> Unit = {},
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    sleepTimerRemaining: Long?,
    activeAmbiencePreset: StateFlow<AmbiencePreset>,
    onToggleNextAmbience: () -> Unit,
    onInstallTtsPresetPack: suspend (TtsPresetPack) -> Int,
    currentSentenceFlow: StateFlow<String>,
    currentTtsRangeFlow: StateFlow<IntRange?>,
    chapterInfoFlow: StateFlow<ChapterInfo>,
    ttsUiStyle: TtsUiStyle,
    onTtsUiStyleChange: (TtsUiStyle) -> Unit,
    ttsHighlightStyle: TtsHighlightStyle,
    onTtsHighlightStyleChange: (TtsHighlightStyle) -> Unit,
    hasLastPosition: Boolean,
    onResumeLast: () -> Unit,
    selectedSherpaVoice: SherpaVoiceOption? = null,
    onSelectSherpaVoice: (SherpaVoiceOption) -> Unit = {},
    isSherpaVoiceDownloaded: (SherpaVoiceOption) -> Boolean = { false },
    onDeleteSherpaVoice: (SherpaVoiceOption) -> Unit = {},
    audioExportState: AudioExportState = AudioExportState.Idle,
    onExportChapterAudio: () -> Unit = {},
    onExportAudioChapters: (Int) -> Unit = { onExportChapterAudio() },
    onCancelAudioExport: () -> Unit = {},
    onResetAudioExportState: () -> Unit = {},
    useEdgeNeuralTts: Boolean = false,
    onUseEdgeNeuralTtsChange: (Boolean) -> Unit = {},
    selectedEdgeVoice: EdgeVoiceOption = EdgeVoiceOption.ALVARO_ES,
    onSelectEdgeVoice: (EdgeVoiceOption) -> Unit = {},
    onDismiss: () -> Unit
) {
    val currentSentence by currentSentenceFlow.collectAsStateWithLifecycle(initialValue = "")
    val currentRange by currentTtsRangeFlow.collectAsStateWithLifecycle(initialValue = null)
    val chapterInfo by chapterInfoFlow.collectAsStateWithLifecycle()
    val ambiencePreset by activeAmbiencePreset.collectAsStateWithLifecycle()
    val chapterProgress = chapterInfo.progress
    val scope = rememberCoroutineScope()
    var presetInstallMessage by remember { mutableStateOf<String?>(null) }

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
        var showCompactSpeedSlider by remember { mutableStateOf(false) }

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
            // ── 1. CABECERA DEL LIBRO ──────────────────────────────────────────
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
                            Text("📚", fontSize = 24.sp)
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
                            text = "$chapterTitle · $progressPct%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = HiddenForest.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showLanguageSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración TTS", tint = HiddenForest.TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimizar panel", tint = HiddenForest.TextSecondary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 2. TARJETA "NARRACIÓN ACTIVA" ──────────────────────────────────
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

                val wavePhase by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = Math.PI.toFloat() * 2f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "wave"
                )
                
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

            // ── 3. BLOQUE DE TRANSPORTE Y ACCIONES RÁPIDAS ─────────────────────
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

                // [⏯ Play / Pausa] (Botón circular central de 64.dp con gradiente HiddenForest.Accent y sombra luminosa)
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
                                if (playing) onPause() else onPlay()
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
                            speed = next
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
                            .clickable { showCompactSpeedSlider = !showCompactSpeedSlider }
                    )
                }

                // Chip de Temporizador de Sueño (🌙 ${if (sleepTimerRemaining != null) "${sleepTimerRemaining / 60000}m" else "Sueño"})
                val isSleepActive = sleepTimerRemaining != null
                val sleepText = if (isSleepActive) "${sleepTimerRemaining!! / 60000}m" else "Sueño"
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
                            if (sleepTimerRemaining == null) {
                                onSleepTimer(15)
                            } else {
                                val mins = (sleepTimerRemaining / 60000).toInt()
                                when {
                                    mins <= 15 -> onSleepTimer(30)
                                    mins <= 30 -> onSleepTimer(45)
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

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HiddenForest.SurfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, HiddenForest.Outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleNextAmbience)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌲", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = ambiencePreset.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (ambiencePreset == AmbiencePreset.OFF) HiddenForest.Text else HiddenForest.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val added = onInstallTtsPresetPack(TtsPresetPack.PINYIN_SPANISH)
                        presetInstallMessage = if (added == 0) {
                            "El preset fonético ya estaba instalado."
                        } else {
                            "Preset fonético instalado ($added reglas)."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Instalar preset fonético")
            }
            presetInstallMessage?.let { message ->
                Text(
                    text = message,
                    color = HiddenForest.Accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Slider compacto desplegable de velocidad y tono
            androidx.compose.animation.AnimatedVisibility(
                visible = showCompactSpeedSlider,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    ReaderCrystalCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                        PremiumSlider(
                            label = "Velocidad de Lectura",
                            value = speed,
                            onValueChange = { speed = it; onSpeedChange(it) },
                            valueRange = 0.5f..2.5f
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        var currentPitchState by remember(currentPitch) { mutableFloatStateOf(currentPitch) }
                        PremiumSlider(
                            label = "Tono de la Voz (Pitch)",
                            value = currentPitchState,
                            onValueChange = { currentPitchState = it; onPitchChange(it) },
                            valueRange = 0.5f..1.5f
                        )
                    }
                }
            }

            // Chip "Continuar desde donde pausé"
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

            Spacer(modifier = Modifier.height(20.dp))

            // ── 4. SELECTOR "ESTILO DE CONTROLES" ──────────────────────────────
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
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
                            .fillMaxHeight()
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

            Spacer(modifier = Modifier.height(20.dp))

            // ── 5. SELECTOR "ESTILO DE RESALTADO" ──────────────────────────────
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
                        Triple(TtsHighlightStyle.BACKGROUND_YELLOW, "Amarillo", Color(0xFFFFD600)),
                        Triple(TtsHighlightStyle.BACKGROUND_ACCENT, "Verde Bosque", Color(0xFF2ECC71)),
                        Triple(TtsHighlightStyle.BACKGROUND_BLUE, "Azul Zafiro", Color(0xFF2196F3)),
                        Triple(TtsHighlightStyle.BACKGROUND_ORANGE, "Naranja Coral", Color(0xFFFF7A45)),
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
                                                            fontSize = 12.sp,
                                                            color = HiddenForest.Text,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                                                fontWeight = FontWeight.Normal
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
                                                                .size(16.dp)
                                                                .clip(CircleShape)
                                                                .background(opt.third)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Desplazamiento Automático (Auto-Scroll) Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HiddenForest.SurfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, HiddenForest.Outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.ArrowDownward, null, tint = if (ttsAutoScroll) HiddenForest.Accent else HiddenForest.TextLow, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = HiddenForest.SurfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                Text("Configuración de Voz y TTS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
                Spacer(modifier = Modifier.height(16.dp))

                // ── SECCIÓN ONLINE: VOCES DE ESTUDIO ONLINE (EDGE NEURAL 10/10) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (useEdgeNeuralTts) Brush.linearGradient(
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
                            if (useEdgeNeuralTts) HiddenForest.Accent.copy(0.4f) else HiddenForest.Outline.copy(0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (useEdgeNeuralTts) HiddenForest.Accent.copy(0.2f)
                                        else HiddenForest.Background
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("☁️", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Voces de Estudio Online (Edge)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = HiddenForest.Text
                                )
                                Text(
                                    "Calidad Humana 10/10 · 0 MB en disco",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (useEdgeNeuralTts) HiddenForest.Accent else HiddenForest.TextLow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = useEdgeNeuralTts,
                                onCheckedChange = onUseEdgeNeuralTtsChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = HiddenForest.Background,
                                    checkedTrackColor = HiddenForest.Accent,
                                    uncheckedThumbColor = HiddenForest.TextLow,
                                    uncheckedTrackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                                )
                            )
                        }

                        if (useEdgeNeuralTts) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Elige tu Voz de Estudio:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = HiddenForest.Accent
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                EdgeVoiceOption.entries.forEach { option ->
                                    val isSelected = selectedEdgeVoice == option
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) HiddenForest.Accent.copy(alpha = 0.12f) else HiddenForest.Background)
                                            .border(
                                                1.dp,
                                                if (isSelected) HiddenForest.Accent.copy(alpha = 0.4f) else HiddenForest.Outline.copy(alpha = 0.25f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onSelectEdgeVoice(option) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onSelectEdgeVoice(option) },
                                            colors = RadioButtonDefaults.colors(selectedColor = HiddenForest.Accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                                color = Color.White
                                            )
                                            Text(
                                                text = option.regionLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = HiddenForest.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                    when (sherpaDownloadState) {
                                        is SherpaDownloadState.Downloading -> "Descargando voz: ${(sherpaDownloadState.progress * 100).toInt()}%"
                                        is SherpaDownloadState.Extracting -> "Instalando modelo de voz..."
                                        is SherpaDownloadState.Error -> "Error: ${sherpaDownloadState.message}"
                                        else -> if (useSherpaTts) "Sherpa ONNX · Español MX · Offline" else "Motor del sistema activo"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (useSherpaTts || sherpaDownloadState is SherpaDownloadState.Downloading) HiddenForest.Accent else HiddenForest.TextLow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val isBusy = sherpaDownloadState is SherpaDownloadState.Downloading || sherpaDownloadState is SherpaDownloadState.Extracting
                            Switch(
                                checked = useSherpaTts,
                                onCheckedChange = onUseSherpaTtsChange,
                                enabled = !isBusy,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = HiddenForest.Background,
                                    checkedTrackColor = HiddenForest.Accent,
                                    uncheckedThumbColor = HiddenForest.TextLow,
                                    uncheckedTrackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                                )
                            )
                        }

                        if (sherpaDownloadState is SherpaDownloadState.Downloading) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { sherpaDownloadState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = HiddenForest.Accent,
                                trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Descargando paquete oficial... ${(sherpaDownloadState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HiddenForest.Accent
                                )
                                if (sherpaDownloadState.totalBytes > 0) {
                                    val dlMb = sherpaDownloadState.downloadedBytes / (1024 * 1024)
                                    val totalMb = sherpaDownloadState.totalBytes / (1024 * 1024)
                                    Text(
                                        "$dlMb MB / $totalMb MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiddenForest.TextLow
                                    )
                                }
                            }
                        } else if (sherpaDownloadState is SherpaDownloadState.Extracting) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = HiddenForest.Accent,
                                trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Instalando y verificando modelo en disco...",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.Accent
                            )
                        } else if (sherpaDownloadState is SherpaDownloadState.Error) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Error: ${sherpaDownloadState.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (useSherpaTts) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Modelo de Voz IA (Sherpa ONNX)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = HiddenForest.Accent
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SherpaVoiceOption.entries.forEach { option ->
                            val isDownloaded = isSherpaVoiceDownloaded(option)
                            val isSelected = selectedSherpaVoice == option
                            val isDownloadingThis = (sherpaDownloadState is SherpaDownloadState.Downloading || sherpaDownloadState is SherpaDownloadState.Extracting) && isSelected

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) HiddenForest.Accent.copy(alpha = 0.12f) else HiddenForest.Background)
                                    .border(
                                        1.dp,
                                        if (isSelected) HiddenForest.Accent.copy(alpha = 0.4f) else HiddenForest.Outline.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = option.accent,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = HiddenForest.TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isDownloadingThis) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (sherpaDownloadState is SherpaDownloadState.Downloading) {
                                            val dl = sherpaDownloadState as SherpaDownloadState.Downloading
                                            Text(
                                                text = "${(dl.progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HiddenForest.Accent
                                            )
                                            LinearProgressIndicator(
                                                progress = { dl.progress },
                                                modifier = Modifier.width(70.dp).height(4.dp),
                                                color = HiddenForest.Accent
                                            )
                                        } else {
                                            Text(
                                                text = "Descomprimiendo...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HiddenForest.Accent
                                            )
                                        }
                                    }
                                } else if (isDownloaded) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(HiddenForest.Accent.copy(alpha = 0.2f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Activa",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = HiddenForest.Accent
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onSelectSherpaVoice(option) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                                    contentColor = HiddenForest.Accent
                                                )
                                            ) {
                                                Text("Activar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        IconButton(
                                            onClick = { onDeleteSherpaVoice(option) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar voz",
                                                tint = Color(0xFFEF9A9A),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onSelectSherpaVoice(option) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HiddenForest.Accent,
                                            contentColor = HiddenForest.Background
                                        )
                                    ) {
                                        Text("Descargar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
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

                Spacer(modifier = Modifier.height(14.dp))

                // ── SECCIÓN 4: EXPORTAR AUDIOLIBRO COMPRIMIDO (.M4A) ──
                Text(
                    text = "🎧 Exportar Audiolibro Comprimido (.M4A)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Text
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (val export = audioExportState) {
                    is AudioExportState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onExportAudioChapters(1) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HiddenForest.Accent),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = HiddenForest.Accent
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Cap. Actual", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { onExportAudioChapters(10) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HiddenForest.Accent),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = HiddenForest.Accent
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Próx. 10 Caps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { onExportAudioChapters(9999) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HiddenForest.Accent),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = HiddenForest.Accent
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Libro Completo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                                        text = "Cap. ${export.currentChapter}/${export.totalChapters} (${export.label}) · Oración ${export.currentSentence}/${export.totalSentences}",
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
    }
}
