package com.example.woodsreader.keymaker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WoodsKeymakerTheme {
                KeymakerApp()
            }
        }
    }
}

enum class KeymakerTab(val title: String) {
    GENERATOR("Emitir"),
    VERIFIER("Verificador"),
    HISTORY("Historial")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeymakerApp() {
    val context = LocalContext.current
    val historyManager = remember { KeyHistoryManager(context) }
    var selectedTab by remember { mutableStateOf(KeymakerTab.GENERATOR) }
    var historyList by remember { mutableStateOf(historyManager.getHistory()) }

    Scaffold(
        containerColor = WoodsDarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WoodsDarkSurface,
                    titleContentColor = WoodsTextPrimary
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🌲 Woods Keymaker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = WoodsTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WoodsGoldContainer)
                                .border(1.dp, WoodsGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO ADMIN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = WoodsGoldBright,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = WoodsDarkSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == KeymakerTab.GENERATOR,
                    onClick = { selectedTab = KeymakerTab.GENERATOR },
                    icon = { Icon(Icons.Default.Key, contentDescription = "Emitir") },
                    label = { Text("Emitir") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WoodsGold,
                        selectedTextColor = WoodsGold,
                        indicatorColor = WoodsGoldContainer,
                        unselectedIconColor = WoodsTextMuted,
                        unselectedTextColor = WoodsTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == KeymakerTab.VERIFIER,
                    onClick = { selectedTab = KeymakerTab.VERIFIER },
                    icon = { Icon(Icons.Default.Verified, contentDescription = "Verificador") },
                    label = { Text("Verificar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WoodsGold,
                        selectedTextColor = WoodsGold,
                        indicatorColor = WoodsGoldContainer,
                        unselectedIconColor = WoodsTextMuted,
                        unselectedTextColor = WoodsTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == KeymakerTab.HISTORY,
                    onClick = {
                        historyList = historyManager.getHistory()
                        selectedTab = KeymakerTab.HISTORY
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (historyList.isNotEmpty()) {
                                    Badge(
                                        containerColor = WoodsGold,
                                        contentColor = Color.Black
                                    ) {
                                        Text("${historyList.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Historial")
                        }
                    },
                    label = { Text("Historial") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WoodsGold,
                        selectedTextColor = WoodsGold,
                        indicatorColor = WoodsGoldContainer,
                        unselectedIconColor = WoodsTextMuted,
                        unselectedTextColor = WoodsTextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                KeymakerTab.GENERATOR -> IssuerScreen(
                    historyManager = historyManager,
                    onLicenseIssued = { historyList = historyManager.getHistory() }
                )
                KeymakerTab.VERIFIER -> VerifierScreen()
                KeymakerTab.HISTORY -> HistoryScreen(
                    historyList = historyList,
                    onDelete = { id ->
                        historyList = historyManager.removeEntry(id)
                    },
                    onClearAll = {
                        historyManager.clearAll()
                        historyList = emptyList()
                    }
                )
            }
        }
    }
}

// ─── Pantalla: Emitir licencia ────────────────────────────────────────────────

@Composable
fun IssuerScreen(
    historyManager: KeyHistoryManager,
    onLicenseIssued: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rawInput by remember { mutableStateOf("") }
    val cleanCode = remember(rawInput) { KeymakerEngine.extractInstallCode(rawInput) }
    val isValid = remember(cleanCode) { KeymakerEngine.isValidInstallCode(cleanCode) }

    var buyerNote by remember { mutableStateOf("") }
    var issuedLicense by remember { mutableStateOf("") }
    var isSigning by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Tarjeta de entrada ─────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WoodsDarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Código de instalación del cliente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WoodsTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).coerceToText(context).toString()
                                    val extracted = KeymakerEngine.extractInstallCode(text)
                                    rawInput = extracted
                                    issuedLicense = ""
                                    errorMsg = ""
                                    Toast.makeText(context, "Código detectado: $extracted", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WoodsGold)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pegar", fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = {
                            rawInput = it
                            issuedLicense = ""
                            errorMsg = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: WR-A1B2-C3D4", color = WoodsTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isValid) WoodsEmerald else WoodsGold,
                            unfocusedBorderColor = WoodsDarkSurfaceVariant,
                            focusedTextColor = WoodsTextPrimary,
                            unfocusedTextColor = WoodsTextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        ),
                        trailingIcon = {
                            if (rawInput.isNotBlank()) {
                                IconButton(onClick = { rawInput = ""; issuedLicense = ""; errorMsg = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = WoodsTextMuted)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            isValid -> {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WoodsEmerald, modifier = Modifier.size(16.dp))
                                Text("Código válido — listo para firmar", color = WoodsEmerald, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            rawInput.isBlank() -> {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = WoodsTextMuted, modifier = Modifier.size(16.dp))
                                Text("Pega el código WR-XXXX-XXXX que muestra Woods Reader al cliente", color = WoodsTextMuted, fontSize = 13.sp)
                            }
                            else -> {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                Text("Formato no reconocido. Debe ser WR-XXXX-XXXX", color = Color(0xFFEF5350), fontSize = 13.sp)
                            }
                        }
                    }

                    // Nota del comprador
                    OutlinedTextField(
                        value = buyerNote,
                        onValueChange = { buyerNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nota del cliente (ej: Juan García)", color = WoodsTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Nota del cliente (opcional)", color = WoodsTextMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WoodsGold,
                            unfocusedBorderColor = WoodsDarkSurfaceVariant,
                            focusedTextColor = WoodsTextPrimary,
                            unfocusedTextColor = WoodsTextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    // Botón de firma
                    Button(
                        onClick = {
                            if (!isValid) return@Button
                            isSigning = true
                            issuedLicense = ""
                            errorMsg = ""
                            scope.launch {
                                try {
                                    val license = withContext(Dispatchers.Default) {
                                        KeymakerEngine.signInstallCode(cleanCode)
                                    }
                                    historyManager.addEntry(cleanCode, license, buyerNote)
                                    onLicenseIssued()
                                    issuedLicense = license
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Error al firmar"
                                } finally {
                                    isSigning = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isValid && !isSigning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WoodsGold,
                            contentColor = Color.Black,
                            disabledContainerColor = WoodsGold.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.4f)
                        )
                    ) {
                        if (isSigning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Firmando con ECDSA…", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Emitir licencia Pro", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // ── Error ──────────────────────────────────────────────────────────
        if (errorMsg.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331616)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF5350))
                        Text(errorMsg, color = Color(0xFFEF5350), fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Resultado de licencia emitida ──────────────────────────────────
        if (issuedLicense.isNotBlank()) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF132A17)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(WoodsEmerald)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = WoodsEmerald)
                                Text(
                                    text = "✓ LICENCIA EMITIDA",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = WoodsEmerald,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Clave en caja monospace
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WoodsDarkSurface)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = issuedLicense,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = WoodsGoldBright,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // Botones de acción
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Copiar solo la clave
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Pro license", issuedLicense))
                                        Toast.makeText(context, "Clave copiada", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WoodsEmerald)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copiar clave", fontSize = 13.sp)
                                }

                                // Compartir mensaje completo
                                Button(
                                    onClick = {
                                        val shareMsg = KeymakerEngine.buildShareMessage(cleanCode, issuedLicense, buyerNote)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Woods Pro message", shareMsg))
                                        Toast.makeText(context, "Mensaje completo copiado", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WoodsEmeraldDark,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copiar mensaje", fontSize = 13.sp)
                                }
                            }

                            Text(
                                text = "Guardada en historial. Comparte la clave con tu cliente.",
                                color = WoodsTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Pantalla: Verificador ────────────────────────────────────────────────────

@Composable
fun VerifierScreen() {
    val context = LocalContext.current
    var testCode by remember { mutableStateOf("") }
    var testKey by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<Boolean?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WoodsDarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Verificador Criptográfico de Licencias",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = WoodsTextPrimary
                    )
                    Text(
                        text = "Comprueba si una clave dada por un usuario es auténtica para su dispositivo antes de darle soporte.",
                        color = WoodsTextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = testCode,
                        onValueChange = {
                            testCode = it
                            verificationResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código de Instalación (WR-XXXX-XXXX)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WoodsGold,
                            unfocusedBorderColor = WoodsDarkSurfaceVariant,
                            focusedTextColor = WoodsTextPrimary,
                            unfocusedTextColor = WoodsTextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    OutlinedTextField(
                        value = testKey,
                        onValueChange = {
                            testKey = it
                            verificationResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Clave firmada (PRO-...)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WoodsGold,
                            unfocusedBorderColor = WoodsDarkSurfaceVariant,
                            focusedTextColor = WoodsTextPrimary,
                            unfocusedTextColor = WoodsTextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    Button(
                        onClick = {
                            val cleanCode = KeymakerEngine.extractInstallCode(testCode)
                            if (!KeymakerEngine.isValidInstallCode(cleanCode)) {
                                Toast.makeText(context, "El código de instalación no es válido", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            verificationResult = KeymakerEngine.verifyKey(cleanCode, testKey)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WoodsGold,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Verificar Autenticidad", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            verificationResult?.let { isValid ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isValid) Color(0xFF132A17) else Color(0xFF331616)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (isValid) WoodsEmerald else Color(0xFFEF5350))
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isValid) WoodsEmerald else Color(0xFFEF5350),
                            modifier = Modifier.size(36.dp)
                        )
                        Column {
                            Text(
                                text = if (isValid) "✓ LICENCIA AUTÉNTICA" else "✕ LICENCIA INVÁLIDA",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (isValid) WoodsEmerald else Color(0xFFEF5350)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isValid)
                                    "La firma ECDSA coincide con la clave pública oficial y este dispositivo."
                                else
                                    "Esta clave no corresponde a este código de instalación o ha sido alterada.",
                                color = WoodsTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Pantalla: Historial ──────────────────────────────────────────────────────

@Composable
fun HistoryScreen(
    historyList: List<KeyHistoryEntry>,
    onDelete: (id: String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("¿Vaciar historial?") },
            text = { Text("Se eliminarán todas las claves guardadas localmente en este dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showConfirmDialog = false
                    }
                ) {
                    Text("Vaciar", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (historyList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = WoodsTextMuted,
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    text = "No hay licencias en el historial",
                    color = WoodsTextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Emite tu primera licencia en la pestaña Emitir.",
                    color = WoodsTextMuted,
                    fontSize = 13.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${historyList.size} licencias emitidas",
                        color = WoodsTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = { showConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = WoodsTextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Vaciar", color = WoodsTextMuted, fontSize = 13.sp)
                    }
                }
            }

            items(historyList, key = { it.id }) { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = WoodsDarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.buyerNote.ifBlank { "Sin nota" },
                                fontWeight = FontWeight.Bold,
                                color = WoodsTextPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = entry.formattedDate,
                                color = WoodsTextMuted,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Dispositivo:", color = WoodsTextMuted, fontSize = 12.sp)
                            Text(
                                text = entry.installCode,
                                fontFamily = FontFamily.Monospace,
                                color = WoodsTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(WoodsGoldContainer.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = entry.proKey,
                                fontFamily = FontFamily.Monospace,
                                color = WoodsGoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copiar clave
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Pro license", entry.proKey))
                                    Toast.makeText(context, "Clave copiada", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WoodsGold)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copiar", fontSize = 12.sp)
                            }

                            // Compartir mensaje
                            OutlinedButton(
                                onClick = {
                                    val shareMsg = KeymakerEngine.buildShareMessage(entry.installCode, entry.proKey, entry.buyerNote)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Woods Pro message", shareMsg))
                                    Toast.makeText(context, "Mensaje copiado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WoodsTextSecondary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mensaje", fontSize = 12.sp)
                            }

                            IconButton(onClick = { onDelete(entry.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = WoodsTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
