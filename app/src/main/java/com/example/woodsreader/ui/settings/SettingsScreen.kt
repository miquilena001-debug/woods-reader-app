package com.example.woodsreader.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.woodsreader.ui.library.WoodsBadge
import com.example.woodsreader.ui.theme.HiddenForest
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs       by viewModel.preferences.collectAsStateWithLifecycle()
    val isScanning  by viewModel.isScanning.collectAsStateWithLifecycle(false)
    val isEnriching by viewModel.isEnriching.collectAsStateWithLifecycle(false)
    val progress    by viewModel.scanProgress.collectAsStateWithLifecycle("")
    val storageSizes by viewModel.storageSizes.collectAsStateWithLifecycle()
    val isBusy = isScanning || isEnriching
    val context = LocalContext.current

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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
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

            // ── Sección: Configuración de Voz (TTS) ────────────────
            item {
                SettingsSection("🔊 Configuración de Voz (TTS)") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.RecordVoiceOver,
                            title = "Voz neural local (Sherpa ONNX)",
                            subtitle = "Voz basada en IA que se ejecuta 100% offline",
                            checked = prefs.useSherpaTts,
                            onCheck = { viewModel.setUseSherpaTts(it) }
                        )

                        if (prefs.useSherpaTts) {
                            SettingsActionRow(
                                icon = Icons.Default.Info,
                                title = "Modelo TTS Activo",
                                subtitle = "vits-piper-es_ES-davefx-medium (es_ES)",
                                enabled = false,
                                onClick = {}
                            )
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
                            icon = Icons.Default.RotateLeft,
                            title = "Restablecer Bienvenida",
                            subtitle = "Volver a mostrar la guía de inicio al arrancar",
                            onClick = { viewModel.resetOnboarding() }
                        )
                    }
                }
            }

            // ── Sección: Respaldo de Biblioteca ────────────────────
            item {
                SettingsSection("📦 Respaldo de Biblioteca") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsActionRow(
                            icon = Icons.Default.Upload,
                            title = "Exportar biblioteca",
                            subtitle = "Compartir o guardar copia de seguridad (JSON)",
                            onClick = { viewModel.exportLibrary(context) }
                        )

                        HorizontalDivider(
                            color = HiddenForest.Outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        var showImportDialog by remember { mutableStateOf(false) }
                        var importError by remember { mutableStateOf<String?>(null) }
                        var importSuccess by remember { mutableStateOf(false) }

                        SettingsActionRow(
                            icon = Icons.Default.Download,
                            title = "Importar biblioteca",
                            subtitle = "Restaurar copia de seguridad desde texto JSON",
                            onClick = { showImportDialog = true }
                        )

                        if (showImportDialog) {
                            var backupJsonText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = {
                                    showImportDialog = false
                                    importError = null
                                    importSuccess = false
                                },
                                title = { Text("Importar Biblioteca", color = HiddenForest.Text) },
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
                        if (prefs.useSherpaTts || storageSizes.espeakBytes > 0) {
                            StorageItemRow("Léxicos de Voz Neural", formatBytes(storageSizes.espeakBytes))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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

            // ── Sección: Acerca de ─────────────────────────────────
            item {
                SettingsSection("🌿 Acerca de") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Woods Reader",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text
                            )
                            WoodsBadge(text = "v1.0.0-alpha", color = HiddenForest.Amber)
                        }
                        Text(
                            text = "Android 14+ · Kotlin 2.0 · Jetpack Compose\n" +
                                   "Readium KT 3.0.3 · 100% código abierto",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = HiddenForest.TextLow
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
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
