package com.example.woodsreader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.woodsreader.ui.library.WoodsBadge
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.StudioAcousticsProfile
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs       by viewModel.preferences.collectAsStateWithLifecycle()
    val isScanning  by viewModel.isScanning.collectAsStateWithLifecycle(false)
    val isEnriching by viewModel.isEnriching.collectAsStateWithLifecycle(false)
    val progress    by viewModel.scanProgress.collectAsStateWithLifecycle("")
    val storageSizes by viewModel.storageSizes.collectAsStateWithLifecycle()
    val sherpaDownloadState by viewModel.sherpaDownloadState.collectAsStateWithLifecycle()
    val selectedSherpaVoice by viewModel.selectedSherpaVoice.collectAsStateWithLifecycle()
    val showSherpaDownloadDialog by viewModel.showSherpaDownloadDialog.collectAsStateWithLifecycle()
    val acousticsProfile by viewModel.acousticsProfile.collectAsStateWithLifecycle()
    val expressiveProsodyEnabled by viewModel.expressiveProsodyEnabled.collectAsStateWithLifecycle()
    val dramatizedDialogueEnabled by viewModel.dramatizedDialogueEnabled.collectAsStateWithLifecycle()
    val selectedLanguageFilter by viewModel.selectedLanguageFilter.collectAsStateWithLifecycle()
    val autoDetectBookLanguage by viewModel.autoDetectBookLanguage.collectAsStateWithLifecycle()
    val builtInLexiconEnabled by viewModel.builtInLexiconEnabled.collectAsStateWithLifecycle()
    val customPhoneticRules by viewModel.customPhoneticRules.collectAsStateWithLifecycle()
    var showAddRuleDialog by remember { mutableStateOf(false) }
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()
    val isBusy = isScanning || isEnriching
    val context = LocalContext.current

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportFullBackup(uri)
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreFullBackup(uri)
    }

    // Calculate storage sizes on display
    LaunchedEffect(Unit) {
        viewModel.calculateStorageSizes(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HiddenForest.Background)
    ) {
        // Subtle radial glow
        Box(modifier = Modifier.fillMaxSize().drawWithCache {
            val brush = Brush.radialGradient(
                    colors = listOf(HiddenForest.AccentGlow.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = size.width
                )
            onDrawBehind {
                drawRect(brush = brush)
            }
        })

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ─────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "PERSONALIZACIÓN",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Ajustes",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = HiddenForest.Text
                    )
                }
            }

            // ── Sección: Biblioteca ────────────────────────────────
            item {
                SettingsSection("📚 Biblioteca") {
                    SettingsActionRow(
                        icon     = when {
                            isScanning  -> Icons.Default.Sync
                            isEnriching -> Icons.Default.AutoAwesome
                            else        -> Icons.Default.Refresh
                        },
                        title    = when {
                            isScanning  -> "Buscando libros…"
                            isEnriching -> "Enriqueciendo metadatos…"
                            else        -> "Sincronizar biblioteca"
                        },
                        subtitle = progress.ifBlank { "Buscar libros nuevos en el dispositivo" },
                        enabled  = !isBusy,
                        onClick  = { viewModel.startScan() }
                    )
                }
            }

            // ── Sección: Motor de Voz y Narración (TTS) ────────────
            item {
                SettingsSection("🔊 Motor de Voz y Narración (TTS)") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val currentEngineMode = if (prefs.useSherpaTts) TtsEngineMode.SHERPA_OFFLINE else TtsEngineMode.SYSTEM

                        // Selector de 2 modos: Estudio IA Offline vs Voz del Sistema
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(HiddenForest.SurfaceVariant)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                Triple(TtsEngineMode.SHERPA_OFFLINE, "🎙️ Estudio IA Offline", "VITS HD"),
                                Triple(TtsEngineMode.SYSTEM, "📱 Voz del Sistema", "Nativo")
                            )
                            modes.forEach { (mode, title, badge) ->
                                val isSelected = currentEngineMode == mode
                                Surface(
                                    onClick = { viewModel.setTtsEngineMode(mode) },
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

                        when (currentEngineMode) {
                            TtsEngineMode.EDGE_ONLINE, TtsEngineMode.SHERPA_OFFLINE -> {
                                // Tarjeta "🎛️ Masterización Acústica y Dramatización"
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

                                        // Selector de Perfil Acústico DSP (3 chips horizontales con horizontalScroll)
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
                                                val isSelected = acousticsProfile == prof
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { viewModel.setAcousticsProfile(prof) },
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
                                            text = acousticsProfile.description,
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
                                                checked = expressiveProsodyEnabled,
                                                onCheckedChange = { viewModel.setExpressiveProsodyEnabled(it) },
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
                                                checked = dramatizedDialogueEnabled,
                                                onCheckedChange = { viewModel.setDramatizedDialogueEnabled(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = HiddenForest.Accent,
                                                    checkedTrackColor = HiddenForest.Accent.copy(alpha = 0.35f),
                                                    uncheckedTrackColor = HiddenForest.Background
                                                )
                                            )
                                        }
                                    }
                                }
                                val downloadState = sherpaDownloadState
                                Text(
                                    text = when (downloadState) {
                                        is SherpaDownloadState.Downloading -> "Descargando voz: ${(downloadState.progress * 100).toInt()}%"
                                        is SherpaDownloadState.Extracting -> "Instalando modelo de voz..."
                                        is SherpaDownloadState.Error -> "Error: ${downloadState.message}"
                                        else -> "✓ Voz basada en IA que se ejecuta 100% offline (sin internet)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (downloadState is SherpaDownloadState.Error) MaterialTheme.colorScheme.error else HiddenForest.Accent,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                if (downloadState is SherpaDownloadState.Downloading) {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = HiddenForest.Accent,
                                        trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Descargando paquete oficial... ${(downloadState.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HiddenForest.Accent
                                        )
                                        if (downloadState.totalBytes > 0) {
                                            val dlMb = downloadState.downloadedBytes / (1024 * 1024)
                                            val totalMb = downloadState.totalBytes / (1024 * 1024)
                                            Text(
                                                "$dlMb MB / $totalMb MB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HiddenForest.TextLow
                                            )
                                        }
                                    }
                                } else if (downloadState is SherpaDownloadState.Extracting) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = HiddenForest.Accent,
                                        trackColor = HiddenForest.Outline.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        "Instalando y verificando modelo en disco...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiddenForest.Accent
                                    )
                                }

                                // Switch "Detección Automática de Idioma"
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            "Auto-detectar Idioma del Libro",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                            color = Color.White
                                        )
                                        Text(
                                            "Selecciona automáticamente la voz según el idioma del capítulo (ES, EN, PT, FR, DE, IT)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = HiddenForest.TextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = autoDetectBookLanguage,
                                        onCheckedChange = { viewModel.setAutoDetectBookLanguage(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = HiddenForest.Accent,
                                            checkedTrackColor = HiddenForest.Accent.copy(alpha = 0.35f),
                                            uncheckedTrackColor = HiddenForest.Background
                                        )
                                    )
                                }

                                Text(
                                    text = "Catálogo de 12 Voces IA Offline (VITS HD Multilingüe)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = HiddenForest.TextLow,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )

                                // Filtro por idioma (Chips horizontales con horizontalScroll)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val languages = SupportedTtsLanguage.entries
                                    languages.forEach { lang ->
                                        val isSelected = selectedLanguageFilter == lang
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setSelectedLanguageFilter(lang) },
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

                                val filteredVoices = SherpaVoiceOption.entries.filter { it.language == selectedLanguageFilter }
                                val voicesToShow = if (filteredVoices.isNotEmpty()) filteredVoices else SherpaVoiceOption.entries
                                voicesToShow.forEach { option ->
                                    val isDownloaded = viewModel.isVoiceDownloaded(option)
                                    val isSelected = selectedSherpaVoice == option
                                    val isDownloadingThis = (sherpaDownloadState is SherpaDownloadState.Downloading || sherpaDownloadState is SherpaDownloadState.Extracting) && isSelected
                                    val sizeBytes = if (isDownloaded) viewModel.getVoiceSizeBytes(option) else 0L
                                    val sizeDisplay = if (sizeBytes > 0) "${sizeBytes / (1024 * 1024)} MB" else "~60 MB"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
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
                                                    val dlState = sherpaDownloadState as SherpaDownloadState.Downloading
                                                    Text(
                                                        text = "${(dlState.progress * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = HiddenForest.Accent
                                                    )
                                                    LinearProgressIndicator(
                                                        progress = { dlState.progress },
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
                                                    WoodsBadge(text = "ACTIVA · $sizeDisplay", color = HiddenForest.Accent)
                                                } else {
                                                    WoodsBadge(text = sizeDisplay, color = HiddenForest.TextLow)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Button(
                                                        onClick = { viewModel.selectAndActivateSherpaVoice(option) },
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(32.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = HiddenForest.Accent.copy(alpha = 0.2f),
                                                            contentColor = HiddenForest.Accent
                                                        )
                                                    ) {
                                                        Text("Usar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                IconButton(
                                                    onClick = { viewModel.deleteSherpaVoice(option) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Eliminar voz",
                                                        tint = Color(0xFFEF9A9A),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.downloadSherpaVoice(option) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = HiddenForest.Accent,
                                                    contentColor = HiddenForest.Background
                                                )
                                            ) {
                                                Text("Descargar ($sizeDisplay)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            TtsEngineMode.SYSTEM -> {
                                Text(
                                    text = "Usa el sintetizador Text-To-Speech nativo configurado en Ajustes de tu dispositivo Android.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.TextLow,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        SettingsSliderRow(
                            title = "Velocidad de voz por defecto",
                            value = prefs.ttsSpeed,
                            range = 0.5f..3.0f,
                            displayValue = "%.2fx".format(Locale.getDefault(), prefs.ttsSpeed),
                            onValueChange = { viewModel.setTtsSpeed(it) }
                        )

                        SettingsSliderRow(
                            title = "Tono de voz por defecto",
                            value = prefs.ttsPitch,
                            range = 0.5f..2.0f,
                            displayValue = "%.2fx".format(Locale.getDefault(), prefs.ttsPitch),
                            onValueChange = { viewModel.setTtsPitch(it) }
                        )

                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // ── Card Glosario Fonético (Novelas Web y Nombres) ────
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.3f))
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
                                            "Pronunciación adaptada de términos como Qi, Dantian, Jianghu, Wuxia, NPC...",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = HiddenForest.TextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = builtInLexiconEnabled,
                                        onCheckedChange = { viewModel.setBuiltInLexiconEnabled(it) },
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
                                                    .background(HiddenForest.Background.copy(alpha = 0.6f))
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
                                                    onClick = { viewModel.removeCustomPhoneticRule(rule.id) },
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
                }
            }

            // ── Sección: General ───────────────────────────────────
            item {
                SettingsSection("⚙️ General") {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        SettingsToggleRow(
                            icon     = Icons.Default.Notifications,
                            title    = "Notificaciones de lectura",
                            subtitle = "Controles TTS en la pantalla de bloqueo",
                            checked  = prefs.notificationsEnabled,
                            onCheck  = { viewModel.setNotificationsEnabled(it) }
                        )
                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        SettingsToggleRow(
                            icon     = Icons.Default.GraphicEq,
                            title    = "TTS automático al abrir",
                            subtitle = "Empieza a leer en voz alta sin tocar",
                            checked  = prefs.ttsAutoStart,
                            onCheck  = { viewModel.setTtsAutoStart(it) }
                        )
                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        SettingsToggleRow(
                            icon     = Icons.Default.BarChart,
                            title    = "Estadísticas de lectura",
                            subtitle = "Registrar páginas y tiempo de sesión",
                            checked  = prefs.analyticsEnabled,
                            onCheck  = { viewModel.setAnalyticsEnabled(it) }
                        )
                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Filled.RotateLeft,
                            title = "Restablecer Bienvenida",
                            subtitle = "Volver a mostrar la guía de inicio al arrancar",
                            onClick = { viewModel.resetOnboarding() }
                        )
                    }
                }
            }

            // ── Sección: Bóveda de Respaldo y Datos ────────────────
            item {
                SettingsSection("🛡️ Bóveda de Respaldo y Datos") {
                    var showJsonOptions by remember { mutableStateOf(false) }
                    var showImportDialog by remember { mutableStateOf(false) }
                    var importError by remember { mutableStateOf<String?>(null) }
                    var importSuccess by remember { mutableStateOf(false) }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Guarda tus libros, progreso, notas y colecciones en un archivo seguro .woodsbackup o restáuralos en cualquier dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextLow
                        )

                        // Botón Primario: Exportar .woodsbackup
                        Button(
                            onClick = {
                                viewModel.clearBackupStatusMessage()
                                createBackupLauncher.launch("woods_reader_backup.woodsbackup")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HiddenForest.Accent,
                                contentColor = HiddenForest.Background
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exportar respaldo (.woodsbackup)", fontWeight = FontWeight.Bold)
                        }

                        // Botón Secundario: Restaurar .woodsbackup
                        OutlinedButton(
                            onClick = {
                                viewModel.clearBackupStatusMessage()
                                restoreBackupLauncher.launch(
                                    arrayOf("application/json", "application/octet-stream", "*/*")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = HiddenForest.Accent
                            ),
                            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restaurar desde archivo (.woodsbackup)", fontWeight = FontWeight.Medium)
                        }

                        backupStatusMessage?.let { message ->
                            Surface(
                                color = HiddenForest.Accent.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = HiddenForest.Accent,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Desplegable compacto para JSON rápido
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showJsonOptions = !showJsonOptions }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Herramientas de respaldo rápido JSON",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextLow
                            )
                            Icon(
                                imageVector = if (showJsonOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showJsonOptions) "Contraer" else "Expandir",
                                tint = HiddenForest.TextLow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (showJsonOptions) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                            ) {
                                SettingsActionRow(
                                    icon = Icons.Default.Upload,
                                    title = "Compartir copia JSON",
                                    subtitle = "Exportar texto JSON plano vía selector de compartir",
                                    onClick = { viewModel.exportLibrary(context) }
                                )
                                HorizontalDivider(
                                    color = HiddenForest.Outline.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                SettingsActionRow(
                                    icon = Icons.Default.Download,
                                    title = "Pegar respaldo JSON",
                                    subtitle = "Importar biblioteca pegando texto JSON directamente",
                                    onClick = { showImportDialog = true }
                                )
                            }
                        }

                        if (showImportDialog) {
                            var backupJsonText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = {
                                    showImportDialog = false
                                    importError = null
                                    importSuccess = false
                                },
                                title = { Text("Importar Biblioteca JSON", color = HiddenForest.Text) },
                                text = {
                                    Column {
                                        Text("Pega el texto JSON de tu copia de seguridad a continuación:", color = HiddenForest.TextLow, modifier = Modifier.padding(bottom = 8.dp))
                                        OutlinedTextField(
                                            value = backupJsonText,
                                            onValueChange = { backupJsonText = it },
                                            placeholder = { Text("Pegue el JSON aquí...", color = HiddenForest.TextLow.copy(alpha = 0.5f)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = HiddenForest.Text,
                                                unfocusedTextColor = HiddenForest.Text,
                                                focusedBorderColor = HiddenForest.Accent,
                                                unfocusedBorderColor = HiddenForest.Outline,
                                                cursorColor = HiddenForest.Accent
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                        if (importError != null) {
                                            Text(importError!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                                        }
                                        if (importSuccess) {
                                            Text("¡Biblioteca importada con éxito!", color = HiddenForest.Accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.importLibrary(
                                                backupJsonText,
                                                onSuccess = {
                                                    importSuccess = true
                                                    importError = null
                                                    backupJsonText = ""
                                                },
                                                onError = {
                                                    importError = it
                                                    importSuccess = false
                                                }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = HiddenForest.Accent)
                                    ) {
                                        Text("Importar", color = HiddenForest.Background)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showImportDialog = false }) {
                                        Text("Cerrar", color = HiddenForest.TextLow)
                                    }
                                },
                                containerColor = HiddenForest.SurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Sección: Almacenamiento ────────────────────────────
            item {
                SettingsSection("💾 Almacenamiento") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StorageItemRow("Base de datos", formatBytes(storageSizes.databaseBytes))
                        StorageItemRow("Caché de portadas", formatBytes(storageSizes.coversBytes))
                        if (storageSizes.sherpaBytes > 0) {
                            StorageItemRow("Voces Offline (Sherpa VITS)", formatBytes(storageSizes.sherpaBytes))
                        } else if (prefs.useSherpaTts || storageSizes.espeakBytes > 0) {
                            StorageItemRow("Léxicos de Voces Offline", formatBytes(storageSizes.espeakBytes))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (storageSizes.sherpaBytes > 0) {
                            Button(
                                onClick = { viewModel.deleteSherpaModel(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Eliminar voces offline Sherpa (~${formatBytes(storageSizes.sherpaBytes)})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Button(
                            onClick = { viewModel.clearCoverCache(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = HiddenForest.Accent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HiddenForest.Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Limpiar caché de portadas", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // ── Sección: Acerca de Woods Reader ────────────────────
            item {
                SettingsSection("🌿 Acerca de Woods Reader") {
                    val technicalSheetText = remember {
                        """
                        Woods Reader — Ficha Técnica de Construcción
                        • Aplicación: Woods Reader
                        • Versión: v1.0.0-ALPHA • Build 2026.10
                        • Desarrollador: Woods Studios
                        • Versión de Compilación: 1.0.0-alpha (Code 1) • Release Candidate
                        • Plataforma & UI: Android 14+ (API 34) • Kotlin 2.0 • Jetpack Compose
                        • Motor de Lectura: Readium Kotlin Toolkit 3.0.3 + PDF Reflow Engine
                        • Arquitectura de Voz: Sherpa ONNX VITS HD (Local) + DSP Studio Master
                        • Exportador de Audio: Hardware MediaCodec AAC-LC (.M4A)
                        • Base de Datos: Room SQLite Indexada + WoodsBackup Vault
                        """.trimIndent()
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Encabezado con icono estilizado y badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                HiddenForest.Accent.copy(alpha = 0.25f),
                                                Color(0xFF0F2618)
                                            )
                                        )
                                    )
                                    .border(1.dp, HiddenForest.Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = HiddenForest.Accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Woods Reader",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 17.sp
                                        ),
                                        color = HiddenForest.Text
                                    )
                                    WoodsBadge(text = "v1.0.0-ALPHA • Build 2026.10", color = HiddenForest.Amber)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Desarrollado por Woods Studios",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = HiddenForest.TextSecondary
                                )
                            }
                        }

                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.35f),
                            thickness = 1.dp
                        )

                        // Tabla / Filas de especificaciones de construcción
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuildSpecRow(
                                label = "Versión de Compilación",
                                value = "1.0.0-alpha (Code 1) • Release Candidate"
                            )
                            BuildSpecRow(
                                label = "Plataforma & UI",
                                value = "Android 14+ (API 34) • Kotlin 2.0 • Jetpack Compose"
                            )
                            BuildSpecRow(
                                label = "Motor de Lectura",
                                value = "Readium Kotlin Toolkit 3.0.3 + PDF Reflow Engine"
                            )
                            BuildSpecRow(
                                label = "Arquitectura de Voz",
                                value = "Sherpa ONNX VITS HD (Local) + DSP Studio Master"
                            )
                            BuildSpecRow(
                                label = "Exportador de Audio",
                                value = "Hardware MediaCodec AAC-LC (.M4A)"
                            )
                            BuildSpecRow(
                                label = "Base de Datos",
                                value = "Room SQLite Indexada + WoodsBackup Vault"
                            )
                        }

                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.35f),
                            thickness = 1.dp
                        )

                        // Pie de tarjeta con botón compacto para copiar ficha técnica
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Ficha Técnica Woods Reader", technicalSheetText)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "📋 Ficha técnica copiada al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HiddenForest.Accent.copy(alpha = 0.15f),
                                contentColor = HiddenForest.Accent
                            ),
                            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📋 Copiar ficha técnica",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(140.dp)) }
        }

        if (showSherpaDownloadDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSherpaDownloadDialog() },
                title = {
                    Text(
                        "Descargar Voz Neuronal (IA)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text
                    )
                },
                text = {
                    Text(
                        "La voz neural de alta calidad (Sherpa ONNX) se ejecuta 100% offline en tu dispositivo.\n\nRequiere una descarga única de aproximadamente 64 MB. ¿Deseas descargarla ahora?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadSherpaModel() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HiddenForest.Accent,
                            contentColor = HiddenForest.Background
                        )
                    ) {
                        Text("Descargar (~64 MB)", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSherpaDownloadDialog() }) {
                        Text("Cancelar", color = HiddenForest.TextLow)
                    }
                },
                containerColor = HiddenForest.Surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showAddRuleDialog) {
            com.example.woodsreader.ui.reader.AddPhoneticRuleDialog(
                onDismiss = { showAddRuleDialog = false },
                onAdd = { orig, phon -> viewModel.addCustomPhoneticRule(orig, phon) }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color    = HiddenForest.Accent,
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HiddenForest.SurfaceVariant)
                .border(1.dp, HiddenForest.Outline, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title,    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = HiddenForest.Text)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = HiddenForest.TextLow)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheck,
            colors = SwitchDefaults.colors(
                checkedThumbColor    = HiddenForest.Background,
                checkedTrackColor    = HiddenForest.Accent,
                uncheckedThumbColor  = HiddenForest.TextLow,
                uncheckedTrackColor  = HiddenForest.Background,
                uncheckedBorderColor = HiddenForest.Outline
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon, tint = if (enabled) HiddenForest.Accent else HiddenForest.TextLow)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title,    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (enabled) HiddenForest.Text else HiddenForest.TextLow)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = HiddenForest.TextLow)
        }
        if (enabled) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HiddenForest.TextLow, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = HiddenForest.Text)
            Text(text = displayValue, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Accent)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = HiddenForest.Accent,
                activeTrackColor = HiddenForest.Accent,
                inactiveTrackColor = HiddenForest.Outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StorageItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HiddenForest.TextLow)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Text)
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, tint: Color = HiddenForest.Accent) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(HiddenForest.Background)
            .border(1.dp, HiddenForest.Outline, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun BuildSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1711).copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = HiddenForest.TextLow,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp
            ),
            color = HiddenForest.Text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f)
        )
    }
}

