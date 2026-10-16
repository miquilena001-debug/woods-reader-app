package com.example.woodsreader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.EdgeVoiceOption
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
    val sherpaDownloadState by viewModel.sherpaDownloadState.collectAsStateWithLifecycle()
    val selectedSherpaVoice by viewModel.selectedSherpaVoice.collectAsStateWithLifecycle()
    val showSherpaDownloadDialog by viewModel.showSherpaDownloadDialog.collectAsStateWithLifecycle()
    val useEdgeNeuralTts by viewModel.useEdgeNeuralTts.collectAsStateWithLifecycle()
    val selectedEdgeVoice by viewModel.selectedEdgeVoice.collectAsStateWithLifecycle()
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
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 100.dp),
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
                            icon = Icons.Default.Cloud,
                            title = "☁️ Voces de Estudio Online (Edge Neural 10/10)",
                            subtitle = "Voces humanas de Azure (Álvaro, Elvira, Dalia, Jorge, Sebastián) · 0 MB en disco",
                            checked = useEdgeNeuralTts,
                            onCheck = { viewModel.setUseEdgeNeuralTts(it) }
                        )

                        if (useEdgeNeuralTts) {
                            Text(
                                text = "Catálogo de Voces de Estudio (Azure/Edge)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = HiddenForest.Accent,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )

                            EdgeVoiceOption.entries.forEach { option ->
                                val isSelected = selectedEdgeVoice == option
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.selectEdgeVoice(option) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) HiddenForest.Accent.copy(alpha = 0.12f) else HiddenForest.Background,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) HiddenForest.Accent.copy(alpha = 0.4f) else HiddenForest.Outline.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.selectEdgeVoice(option) },
                                            colors = RadioButtonDefaults.colors(selectedColor = HiddenForest.Accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
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

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val downloadState = sherpaDownloadState
                        SettingsToggleRow(
                            icon = Icons.Default.RecordVoiceOver,
                            title = "Voz neural local (Sherpa ONNX)",
                            subtitle = when (downloadState) {
                                is SherpaDownloadState.Downloading -> "Descargando voz: ${(downloadState.progress * 100).toInt()}%"
                                is SherpaDownloadState.Extracting -> "Instalando modelo de voz..."
                                is SherpaDownloadState.Error -> "Error: ${downloadState.message}"
                                else -> if (prefs.useSherpaTts) "Voz basada en IA que se ejecuta 100% offline"
                                        else "Voz de alta calidad (requiere descarga de ~64 MB)"
                            },
                            checked = prefs.useSherpaTts,
                            onCheck = { viewModel.setUseSherpaTts(it) }
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
                        } else if (downloadState is SherpaDownloadState.Error) {
                            Text(
                                "Error: ${downloadState.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (prefs.useSherpaTts) {
                            Text(
                                text = "Catálogo de Voces IA (Sherpa ONNX)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = HiddenForest.Accent,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )

                            SherpaVoiceOption.entries.forEach { option ->
                                val isDownloaded = viewModel.isVoiceDownloaded(option)
                                val isSelected = selectedSherpaVoice == option
                                val isDownloadingThis = (sherpaDownloadState is SherpaDownloadState.Downloading || sherpaDownloadState is SherpaDownloadState.Extracting) && isSelected

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
                                            text = option.accent,
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
                                                WoodsBadge(text = "ACTIVA", color = HiddenForest.Accent)
                                            } else {
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
                                                    imageVector = Icons.Default.Delete,
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
                                            Text("Descargar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
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

            item {
                SettingsSection("🗄️ Bóveda de Respaldo") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearBackupStatusMessage()
                                createBackupLauncher.launch("woods_reader_backup.woodsbackup")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = HiddenForest.Accent
                            ),
                            border = BorderStroke(1.dp, HiddenForest.Outline)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exportar respaldo (.woodsbackup)")
                        }

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
                            border = BorderStroke(1.dp, HiddenForest.Outline)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restaurar desde archivo")
                        }

                        backupStatusMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = HiddenForest.Accent
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
                            StorageItemRow("Modelo de Voz Neural (Sherpa)", formatBytes(storageSizes.sherpaBytes))
                        } else if (prefs.useSherpaTts || storageSizes.espeakBytes > 0) {
                            StorageItemRow("Léxicos de Voz Neural", formatBytes(storageSizes.espeakBytes))
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
                                Text("Eliminar modelo de voz neural (~${formatBytes(storageSizes.sherpaBytes)})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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

            item { Spacer(modifier = Modifier.height(120.dp)) }
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
