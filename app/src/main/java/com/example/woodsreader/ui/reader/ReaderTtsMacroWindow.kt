package com.example.woodsreader.ui.reader

import android.content.Context
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
import com.example.woodsreader.core.tts.StudioAcousticsProfile
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import com.example.woodsreader.core.tts.SoundscapeMode
import com.example.woodsreader.core.tts.PronunciationLexiconManager
import com.example.woodsreader.core.tts.PhoneticRule
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.core.tts.SleepTimerPreset
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.core.system.WoodsKeepAliveManager
import com.example.woodsreader.ui.common.WoodsProUnlockDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
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

enum class ReaderMacroEngineMode {
    EDGE_ONLINE,
    SHERPA_OFFLINE,
    SYSTEM
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
    proLicenseManager: ProLicenseManager? = null,
    keepAliveManager: WoodsKeepAliveManager? = null,
    onDismiss: () -> Unit
) {
    val currentSentence by currentSentenceFlow.collectAsStateWithLifecycle(initialValue = "")
    val currentRange by currentTtsRangeFlow.collectAsStateWithLifecycle(initialValue = null)
    val chapterInfo by chapterInfoFlow.collectAsStateWithLifecycle()
    val ambiencePreset by activeAmbiencePreset.collectAsStateWithLifecycle()
    val chapterProgress = chapterInfo.progress
    val scope = rememberCoroutineScope()
    var presetInstallMessage by remember { mutableStateOf<String?>(null) }
    var showProUnlockDialog by remember { mutableStateOf(false) }
    val isProUnlocked by proLicenseManager?.isProUnlocked?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) }
    val isUnrestrictedBattery by keepAliveManager?.isUnrestrictedBattery?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, keepAliveManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                keepAliveManager?.refreshBatteryStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showLanguageSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val studioPrefs = remember(context) { context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE) }
    val lexiconManager = remember(context) { PronunciationLexiconManager(context.applicationContext) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var currentSoundscape by remember {
        mutableStateOf(SoundscapeMode.fromId(studioPrefs.getString("soundscape_mode", SoundscapeMode.OFF.id)))
    }
    var soundscapeIntensity by remember {
        mutableFloatStateOf(studioPrefs.getFloat("soundscape_intensity", 0.18f))
    }

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

            Spacer(modifier = Modifier.height(10.dp))

            // ── 3A. SECCIÓN "🌙 Temporizador de Sueño Inteligente" ───────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌙", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Temporizador de Sueño Inteligente",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        val rem = sleepTimerRemaining
                        if (rem != null && rem != 0L) {
                            val liveCountdown = if (rem == -1L) {
                                "Al terminar capítulo"
                            } else {
                                val totalSecs = (rem / 1000).coerceAtLeast(0)
                                val m = totalSecs / 60
                                val s = totalSecs % 60
                                String.format(java.util.Locale.US, "%02d:%02d", m, s)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E676).copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = liveCountdown,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = Color(0xFF00E676)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val timerPresets = listOf(
                            0 to "Apagado",
                            15 to "15 min",
                            30 to "30 min",
                            45 to "45 min",
                            60 to "60 min",
                            -1 to "Fin del Cap."
                        )
                        timerPresets.forEach { (mins, label) ->
                            val isSelected = when (mins) {
                                0 -> sleepTimerRemaining == null || sleepTimerRemaining == 0L
                                -1 -> sleepTimerRemaining == -1L
                                else -> {
                                    if (sleepTimerRemaining != null && sleepTimerRemaining > 0) {
                                        val curMins = (sleepTimerRemaining + 59999) / 60000
                                        curMins in (mins - 2)..(mins + 2)
                                    } else false
                                }
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (mins == 0) {
                                        onCancelSleepTimer()
                                    } else {
                                        onSleepTimer(mins)
                                    }
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White,
                                    containerColor = HiddenForest.Background.copy(alpha = 0.6f),
                                    labelColor = HiddenForest.TextSecondary
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 3B. SECCIÓN "🌧️ Paisaje Sonoro de Fondo (Auto-Ducking)" ────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌧️", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paisaje Sonoro de Fondo (Auto-Ducking)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SoundscapeMode.entries.forEach { mode ->
                            val isSelected = currentSoundscape == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    currentSoundscape = mode
                                    studioPrefs.edit().putString("soundscape_mode", mode.id).apply()
                                },
                                label = { Text("${mode.iconEmoji} ${mode.label}", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White,
                                    containerColor = HiddenForest.Background.copy(alpha = 0.6f),
                                    labelColor = HiddenForest.TextSecondary
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }

                    if (currentSoundscape != SoundscapeMode.OFF) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Intensidad: ${(soundscapeIntensity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = HiddenForest.TextSecondary,
                                modifier = Modifier.width(90.dp)
                            )
                            Slider(
                                value = soundscapeIntensity,
                                onValueChange = {
                                    soundscapeIntensity = it
                                    studioPrefs.edit().putFloat("soundscape_intensity", it).apply()
                                },
                                valueRange = 0.05f..0.40f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = HiddenForest.Accent,
                                    activeTrackColor = HiddenForest.Accent,
                                    inactiveTrackColor = HiddenForest.Outline.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
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

                val context = LocalContext.current
                val studioPrefs = remember { context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE) }
                var studioProfile by remember {
                    mutableStateOf(StudioAcousticsProfile.fromId(studioPrefs.getString("acoustics_profile", null)))
                }
                var expressiveProsody by remember {
                    mutableStateOf(studioPrefs.getBoolean("expressive_prosody_enabled", true))
                }
                var dramatizedDialogue by remember {
                    mutableStateOf(studioPrefs.getBoolean("dramatized_dialogue_enabled", true))
                }
                val sherpaModelManager = remember(context) { SherpaModelManager(context.applicationContext) }
                val autoDetectBookLanguage by sherpaModelManager.autoDetectBookLanguage.collectAsStateWithLifecycle()
                val selectedLanguageFilter by sherpaModelManager.selectedLanguageFilter.collectAsStateWithLifecycle()
                val builtInLexiconEnabled by lexiconManager.builtInEnabled.collectAsStateWithLifecycle()
                val customPhoneticRules by lexiconManager.customRules.collectAsStateWithLifecycle()

                // Selector Unificado de 2 Modos: Estudio IA Offline vs Voz del Sistema
                val currentEngineMode = if (useSherpaTts) ReaderMacroEngineMode.SHERPA_OFFLINE else ReaderMacroEngineMode.SYSTEM

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HiddenForest.Background)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        Triple(ReaderMacroEngineMode.SHERPA_OFFLINE, "🎙️ Estudio IA Offline", "VITS HD"),
                        Triple(ReaderMacroEngineMode.SYSTEM, "📱 Voz del Sistema", "Nativo")
                    )
                    modes.forEach { (mode, title, badge) ->
                        val isSelected = currentEngineMode == mode
                        Surface(
                            onClick = {
                                when (mode) {
                                    ReaderMacroEngineMode.SHERPA_OFFLINE -> {
                                        onUseEdgeNeuralTtsChange(false)
                                        onUseSherpaTtsChange(true)
                                    }
                                    ReaderMacroEngineMode.SYSTEM -> {
                                        onUseEdgeNeuralTtsChange(false)
                                        onUseSherpaTtsChange(false)
                                    }
                                    else -> {}
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) HiddenForest.Accent.copy(alpha = 0.22f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, HiddenForest.Accent) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.White else HiddenForest.TextSecondary,
                                    maxLines = 1
                                )
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isSelected) HiddenForest.Accent else HiddenForest.TextLow,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Contenido dinámico según el modo activo
                when (currentEngineMode) {
                    ReaderMacroEngineMode.EDGE_ONLINE -> {}

                    ReaderMacroEngineMode.SHERPA_OFFLINE -> {
                        // Tarjeta "🎛️ Masterización Acústica y Dramatización"
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HiddenForest.Background.copy(alpha = 0.65f)),
                            border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎛️", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Masterización Acústica y Dramatización",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    "Perfil Acústico DSP (Master 32-bit)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = HiddenForest.Accent
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // 3 chips horizontales con horizontalScroll
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val profiles = listOf(
                                        StudioAcousticsProfile.STUDIO_WARM to "🎙️ Estudio Cálido",
                                        StudioAcousticsProfile.NIGHT_INTIMATE to "🌙 Nocturno Suave",
                                        StudioAcousticsProfile.CRISP_HD to "⚡ Claridad HD"
                                    )
                                    profiles.forEach { (prof, label) ->
                                        val isSelected = studioProfile == prof
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                studioProfile = prof
                                                studioPrefs.edit().putString("acoustics_profile", prof.id).apply()
                                            },
                                            label = { Text(label, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                                selectedLabelColor = Color.White,
                                                containerColor = HiddenForest.Surface,
                                                labelColor = HiddenForest.TextSecondary
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.3f)
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = studioProfile.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = HiddenForest.TextLow
                                )

                                HorizontalDivider(
                                    color = HiddenForest.Outline.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )

                                // Switch "Prosodia Expresiva y Respiración Natural"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            "Prosodia Expresiva y Respiración Natural",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                            color = Color.White
                                        )
                                        Text(
                                            "Ajusta pausas en puntos suspensivos, preguntas, exclamaciones y limpia símbolos de novelas web",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = HiddenForest.TextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = expressiveProsody,
                                        onCheckedChange = {
                                            expressiveProsody = it
                                            studioPrefs.edit().putBoolean("expressive_prosody_enabled", it).apply()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = HiddenForest.Accent,
                                            checkedTrackColor = HiddenForest.Accent.copy(alpha = 0.35f),
                                            uncheckedTrackColor = HiddenForest.Background
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Switch "Modo Novela Dramatizada (Narrador / Diálogos)"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            "Modo Novela Dramatizada (Narrador / Diálogos)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                            color = Color.White
                                        )
                                        Text(
                                            "Diferencia automáticamente los diálogos «...» o —...— y mensajes de [Sistema] de la voz del narrador",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = HiddenForest.TextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = dramatizedDialogue,
                                        onCheckedChange = {
                                            dramatizedDialogue = it
                                            studioPrefs.edit().putBoolean("dramatized_dialogue_enabled", it).apply()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = HiddenForest.Accent,
                                            checkedTrackColor = HiddenForest.Accent.copy(alpha = 0.35f),
                                            uncheckedTrackColor = HiddenForest.Background
                                        )
                                    )
                                }
                            }
                        }

                        // Switch "✨ Auto-detectar idioma del libro"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "✨ Auto-detectar idioma del libro",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                    color = Color.White
                                )
                                Text(
                                    "Ajusta automáticamente la voz según el idioma del libro (ES, EN, PT, FR, DE, IT)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = HiddenForest.TextSecondary
                                )
                            }
                            Switch(
                                checked = autoDetectBookLanguage,
                                onCheckedChange = { sherpaModelManager.setAutoDetectBookLanguage(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = HiddenForest.Accent,
                                    checkedTrackColor = HiddenForest.Accent.copy(alpha = 0.35f),
                                    uncheckedTrackColor = HiddenForest.Background
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Filtro de idiomas en carrusel horizontal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SupportedTtsLanguage.entries.forEach { lang ->
                                val isSelected = selectedLanguageFilter == lang
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { sherpaModelManager.setSelectedLanguageFilter(lang) },
                                    label = { Text("${lang.flagEmoji} ${lang.displayName}", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White,
                                        containerColor = HiddenForest.Surface,
                                        labelColor = HiddenForest.TextSecondary
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Voces Offline IA (Sherpa ONNX Piper VITS)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = HiddenForest.Accent
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (sherpaDownloadState is SherpaDownloadState.Downloading) {
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
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (sherpaDownloadState is SherpaDownloadState.Extracting) {
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
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (sherpaDownloadState is SherpaDownloadState.Error) {
                            Text(
                                "Error: ${sherpaDownloadState.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val filteredVoices = SherpaVoiceOption.entries.filter { it.language == selectedLanguageFilter }
                        val voicesToDisplay = if (filteredVoices.isNotEmpty()) filteredVoices else SherpaVoiceOption.entries

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            voicesToDisplay.forEach { option ->
                                val isDownloaded = isSherpaVoiceDownloaded(option)
                                val isSelected = selectedSherpaVoice == option
                                val secondaryDialogueVoiceId = studioPrefs.getString("secondary_dialogue_voice_id", null)
                                val isSecondaryDialogue = secondaryDialogueVoiceId == option.id
                                val isDownloadingThis = (sherpaDownloadState is SherpaDownloadState.Downloading || sherpaDownloadState is SherpaDownloadState.Extracting) && isSelected
                                val sizeBytes = if (isDownloaded) sherpaModelManager.getVoiceSizeBytes(option) else 0L
                                val sizeDisplay = if (sizeBytes > 0) "${sizeBytes / (1024 * 1024)} MB" else "~60 MB"

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
                                            text = "${option.accent} · $sizeDisplay",
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(HiddenForest.Accent.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "Instalada",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = HiddenForest.Accent
                                                )
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(HiddenForest.Accent)
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "Narrador",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = HiddenForest.Background
                                                    )
                                                }
                                            } else {
                                                Button(
                                                    onClick = { onSelectSherpaVoice(option) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = HiddenForest.Accent.copy(alpha = 0.25f),
                                                        contentColor = HiddenForest.Accent
                                                    )
                                                ) {
                                                    Text("Narrador", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                            if (isSecondaryDialogue) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF81C784).copy(alpha = 0.3f))
                                                        .clickable {
                                                            studioPrefs.edit().remove("secondary_dialogue_voice_id").apply()
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "Diálogos ✓",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color(0xFF81C784)
                                                    )
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = {
                                                        studioPrefs.edit().putString("secondary_dialogue_voice_id", option.id).apply()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.4f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = HiddenForest.TextSecondary
                                                    )
                                                ) {
                                                    Text("Diálogos", fontSize = 10.sp)
                                                }
                                            }

                                            IconButton(
                                                onClick = { onDeleteSherpaVoice(option) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Eliminar voz",
                                                    tint = Color(0xFFEF9A9A),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { onSelectSherpaVoice(option) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = HiddenForest.Accent,
                                                contentColor = HiddenForest.Background
                                            )
                                        ) {
                                            Text("Descargar ($sizeDisplay)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Card "📖 Glosario Fonético (Novelas Web y Nombres)"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HiddenForest.Background.copy(alpha = 0.65f)),
                            border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📖", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Glosario Fonético (Novelas Web y Nombres)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            "Reglas para Novelas Web (Xuanhuan / Xianxia / LitRPG)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                            color = Color.White
                                        )
                                        Text(
                                            "Pronunciación adaptada de términos chinos y RPG (Qi, Dantian, Jianghu, Wuxia, NPC...)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = HiddenForest.TextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = builtInLexiconEnabled,
                                        onCheckedChange = { lexiconManager.setBuiltInEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = HiddenForest.Accent,
                                            checkedTrackColor = HiddenForest.Accent.copy(alpha = 0.35f),
                                            uncheckedTrackColor = HiddenForest.Background
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = { showAddRuleDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.Accent),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ Añadir palabra personalizada", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                if (customPhoneticRules.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        customPhoneticRules.forEach { rule ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(HiddenForest.Surface.copy(alpha = 0.7f))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        rule.originalWord,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        "↳ ${rule.phoneticReplacement}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = HiddenForest.Accent
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { lexiconManager.removeCustomRule(rule.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Eliminar regla",
                                                        tint = Color(0xFFEF9A9A),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ReaderMacroEngineMode.SYSTEM -> {
                        Text(
                            text = "Usa el sintetizador Text-To-Speech nativo configurado en los Ajustes de tu dispositivo Android.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextLow,
                            modifier = Modifier.padding(vertical = 4.dp)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
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
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) HiddenForest.Accent else HiddenForest.Text,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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

                var showNoVoiceWarning by remember { mutableStateOf(false) }
                val hasDownloadedVoice = SherpaVoiceOption.entries.any { isSherpaVoiceDownloaded(it) }

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
                                "Para exportar audiolibros .M4A comprimidos a máxima velocidad sin cortes, descarga al menos una voz offline (como Alejandro o Carlos) en el modo '🧠 IA Local'.",
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
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                                        showProUnlockDialog = true
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
                                    text = if (isProUnlocked) "10 Capítulos" else "10 Caps ★",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                                        showProUnlockDialog = true
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
                                    text = if (isProUnlocked) "Todo el Libro" else "Libro ★",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

            if (showAddRuleDialog) {
                AddPhoneticRuleDialog(
                    onDismiss = { showAddRuleDialog = false },
                    onAdd = { orig, phon -> lexiconManager.addCustomRule(orig, phon) }
                )
            }

            if (showProUnlockDialog && proLicenseManager != null) {
                WoodsProUnlockDialog(
                    proLicenseManager = proLicenseManager,
                    onDismiss = { showProUnlockDialog = false }
                )
            }
        }
    }
}

@Composable
fun AddPhoneticRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (original: String, replacement: String) -> Unit
) {
    var originalText by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir Palabra Personalizada", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reemplaza la pronunciación de nombres o términos específicos antes de sintetizar con la voz de estudio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiddenForest.TextSecondary
                )
                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text("Texto Original (ej. Jiang Chen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Cómo pronunciarlo (ej. Yian Chen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (originalText.isNotBlank() && replacementText.isNotBlank()) {
                        onAdd(originalText.trim(), replacementText.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HiddenForest.Accent,
                    contentColor = HiddenForest.Background
                )
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = HiddenForest.TextSecondary)
            }
        },
        containerColor = HiddenForest.SurfaceVariant,
        shape = RoundedCornerShape(16.dp)
    )
}

