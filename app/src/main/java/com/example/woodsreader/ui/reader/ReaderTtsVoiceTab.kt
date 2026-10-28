package com.example.woodsreader.ui.reader

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun ReaderTtsVoiceTab(
    hasDownloadedSherpaVoice: Boolean,
    useSherpaTts: Boolean,
    onUseSherpaTtsChange: (Boolean) -> Unit,
    useEdgeNeuralTts: Boolean,
    onUseEdgeNeuralTtsChange: (Boolean) -> Unit,
    selectedSherpaVoice: SherpaVoiceOption?,
    onSelectSherpaVoice: (SherpaVoiceOption) -> Unit,
    isSherpaVoiceDownloaded: (SherpaVoiceOption) -> Boolean,
    onDeleteSherpaVoice: (SherpaVoiceOption) -> Unit,
    sherpaDownloadState: SherpaDownloadState,
    currentLanguage: String?,
    onLanguageChange: (String?) -> Unit,
    availableVoices: List<String>,
    currentVoiceName: String?,
    onVoiceChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val studioPrefs = remember(context) { context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE) }
    val sherpaModelManager = remember(context) { SherpaModelManager(context.applicationContext) }
    val autoDetectBookLanguage by sherpaModelManager.autoDetectBookLanguage.collectAsStateWithLifecycle()
    val selectedLanguageFilter by sherpaModelManager.selectedLanguageFilter.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        // ── PESTAÑA 1: VOZ E IDIOMA ──
        if (!hasDownloadedSherpaVoice) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0C2419),
                border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎙️", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voces de Estudio IA (Offline)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF6EE7B7)
                            )
                            Text(
                                text = "Calidad humana natural sin consumir datos",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Descarga tu primera Voz de Estudio IA (~60 MB) para escuchar y exportar tus libros con calidad humana 100% sin internet.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectSherpaVoice(SherpaVoiceOption.CLAUDE_MX) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Claudia HD (Latam)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onSelectSherpaVoice(SherpaVoiceOption.DAVEFX_ES) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.85f),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = "David HD (España)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

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
                Triple(ReaderMacroEngineMode.SYSTEM, "🗣️ Voz del Sistema", "Nativo")
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

        when (currentEngineMode) {
            ReaderMacroEngineMode.EDGE_ONLINE -> {}
            ReaderMacroEngineMode.SHERPA_OFFLINE -> {
                // Switch "🌐 Auto-detectar idioma del libro"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "🌐 Auto-detectar idioma del libro",
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
                                    text = "${option.accent} • $sizeDisplay",
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
    }
}
