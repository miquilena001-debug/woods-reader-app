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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
    var selectedStudioTab by remember { mutableIntStateOf(0) }
    val hasDownloadedSherpaVoice = remember(selectedSherpaVoice, sherpaDownloadState) {
        SherpaVoiceOption.entries.any { isSherpaVoiceDownloaded(it) }
    }
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

    var speed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var pitch by remember(currentPitch) { mutableFloatStateOf(currentPitch) }
    LaunchedEffect(currentSpeed) { speed = currentSpeed }
    LaunchedEffect(currentPitch) { pitch = currentPitch }

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
        var showCompactSpeedSlider by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            ReaderTtsActiveControls(
                coverPath = coverPath,
                title = title,
                author = author,
                progress = progress,
                chapterProgress = chapterProgress,
                chapterInfo = chapterInfo,
                onOpenSettings = { showLanguageSheet = true },
                onDismiss = onDismiss,
                keepAliveManager = keepAliveManager,
                isUnrestrictedBattery = isUnrestrictedBattery,
                hazeState = hazeState,
                isLoadingTtsModel = isLoadingTtsModel,
                playing = playing,
                currentSentence = currentSentence,
                currentRange = currentRange,
                onSkipChapterBackward = onSkipChapterBackward,
                onSkipSentenceBackward = onSkipSentenceBackward,
                onPlay = onPlay,
                onPause = onPause,
                useSherpaTts = useSherpaTts,
                hasDownloadedSherpaVoice = hasDownloadedSherpaVoice,
                onRequestDownloadSherpaVoice = {
                    selectedStudioTab = 0
                    showLanguageSheet = true
                },
                onSkipSentenceForward = onSkipSentenceForward,
                onSkipChapterForward = onSkipChapterForward,
                speed = speed,
                onSpeedChange = {
                    speed = it
                    onSpeedChange(it)
                },
                showCompactSpeedSlider = showCompactSpeedSlider,
                onToggleSpeedSlider = { showCompactSpeedSlider = !showCompactSpeedSlider },
                sleepTimerRemaining = sleepTimerRemaining,
                onSleepTimer = onSleepTimer,
                onCancelSleepTimer = onCancelSleepTimer,
                onStop = onStop
            )

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
                        PremiumSlider(
                            label = "Tono de la Voz (Pitch)",
                            value = pitch,
                            onValueChange = { pitch = it; onPitchChange(it) },
                            valueRange = 0.5f..2.0f
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

                val studioTabs = listOf(
                    "🎙️ Voz e Idioma",
                    "🎛️ Acústica y Ambiente",
                    "📦 Exportar .M4A"
                )

                TabRow(
                    selectedTabIndex = selectedStudioTab,
                    containerColor = Color.Transparent,
                    contentColor = HiddenForest.Accent,
                    indicator = { tabPositions ->
                        if (selectedStudioTab in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedStudioTab]),
                                color = HiddenForest.Accent
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.25f),
                            thickness = 1.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    studioTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedStudioTab == index,
                            onClick = { selectedStudioTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selectedStudioTab == index) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selectedContentColor = HiddenForest.Accent,
                            unselectedContentColor = HiddenForest.TextSecondary
                        )
                    }
                }

                when (selectedStudioTab) {
                    0 -> {
                        ReaderTtsVoiceTab(
                            hasDownloadedSherpaVoice = hasDownloadedSherpaVoice,
                            useSherpaTts = useSherpaTts,
                            onUseSherpaTtsChange = onUseSherpaTtsChange,
                            useEdgeNeuralTts = useEdgeNeuralTts,
                            onUseEdgeNeuralTtsChange = onUseEdgeNeuralTtsChange,
                            selectedSherpaVoice = selectedSherpaVoice,
                            onSelectSherpaVoice = onSelectSherpaVoice,
                            isSherpaVoiceDownloaded = isSherpaVoiceDownloaded,
                            onDeleteSherpaVoice = onDeleteSherpaVoice,
                            sherpaDownloadState = sherpaDownloadState,
                            currentLanguage = currentLanguage,
                            onLanguageChange = onLanguageChange,
                            availableVoices = availableVoices,
                            currentVoiceName = currentVoiceName,
                            onVoiceChange = onVoiceChange
                        )
                    }
                    1 -> {
                        ReaderTtsAmbienceTab(
                            pitch = pitch,
                            onPitchChange = {
                                pitch = it
                                onPitchChange(it)
                            },
                            onOpenRules = {
                                showLanguageSheet = false
                                onOpenRules()
                            },
                            onAddRuleClick = { showAddRuleDialog = true },
                            lexiconManager = lexiconManager
                        )
                    }
                    2 -> {
                        ReaderTtsExportTab(
                            audioExportState = audioExportState,
                            isSherpaVoiceDownloaded = isSherpaVoiceDownloaded,
                            isProUnlocked = isProUnlocked,
                            onExportAudioChapters = onExportAudioChapters,
                            onCancelAudioExport = onCancelAudioExport,
                            onResetAudioExportState = onResetAudioExportState,
                            onRequestProUnlock = { showProUnlockDialog = true }
                        )
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
