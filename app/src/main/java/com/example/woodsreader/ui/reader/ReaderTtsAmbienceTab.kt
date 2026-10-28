package com.example.woodsreader.ui.reader

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import com.example.woodsreader.core.tts.PronunciationLexiconManager
import com.example.woodsreader.core.tts.StudioAcousticsProfile
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun ReaderTtsAmbienceTab(
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onOpenRules: () -> Unit,
    onAddRuleClick: () -> Unit,
    lexiconManager: PronunciationLexiconManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val studioPrefs = remember(context) { context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE) }
    var studioProfile by remember {
        mutableStateOf(StudioAcousticsProfile.fromId(studioPrefs.getString("acoustics_profile", null)))
    }
    var expressiveProsody by remember {
        mutableStateOf(studioPrefs.getBoolean("expressive_prosody_enabled", true))
    }
    var dramatizedDialogue by remember {
        mutableStateOf(studioPrefs.getBoolean("dramatized_dialogue_enabled", true))
    }
    val builtInLexiconEnabled by lexiconManager.builtInEnabled.collectAsStateWithLifecycle()
    val customPhoneticRules by lexiconManager.customRules.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        // ── PESTAÑA 2: ACÚSTICA Y AMBIENTE ──
        // Tarjeta "🎧 Masterización Acústica y Dramatización"
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HiddenForest.Background.copy(alpha = 0.65f)),
            border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎧", fontSize = 18.sp)
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
                        StudioAcousticsProfile.CRISP_HD to "✨ Claridad HD"
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
                            "Diferencia automáticamente los diálogos «...» o “...” y mensajes de [Sistema] de la voz del narrador",
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

        Spacer(modifier = Modifier.height(12.dp))
        Spacer(modifier = Modifier.height(16.dp))
        PremiumSlider(
            label = "Tono de la Voz (Pitch)",
            value = pitch,
            onValueChange = onPitchChange,
            valueRange = 0.5f..2.0f
        )

        Spacer(modifier = Modifier.height(12.dp))
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
                    onClick = onAddRuleClick,
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
                                        "➔ ${rule.phoneticReplacement}",
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

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onOpenRules,
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
