package com.example.woodsreader.keymaker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    GENERATOR("Generador"),
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
                    icon = { Icon(Icons.Default.Key, contentDescription = "Generador") },
                    label = { Text("Generar") },
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
                KeymakerTab.GENERATOR -> GeneratorScreen(
                    onSaveToHistory = { code, key, note ->
                        historyList = historyManager.addEntry(code, key, note)
                    }
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

@Composable
fun GeneratorScreen(
    onSaveToHistory: (code: String, key: String, note: String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var rawInput by remember { mutableStateOf("") }
    var buyerNote by remember { mutableStateOf("") }

    val cleanCode = remember(rawInput) { KeymakerEngine.extractInstallCode(rawInput) }
    val isValid = remember(cleanCode) { KeymakerEngine.isValidInstallCode(cleanCode) }
    val generatedKey = remember(cleanCode, isValid) {
        if (isValid) KeymakerEngine.generateProKey(cleanCode) else ""
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Código de Instalación",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WoodsTextPrimary
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).coerceToText(context).toString()
                                    val extracted = KeymakerEngine.extractInstallCode(text)
                                    rawInput = extracted
                                    Toast.makeText(context, "Código pegado: $extracted", Toast.LENGTH_SHORT).show()
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
                        onValueChange = { rawInput = it },
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
                                IconButton(onClick = { rawInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = WoodsTextMuted)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        )
                    )

                    // Estado de validación
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isValid) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WoodsEmerald, modifier = Modifier.size(16.dp))
                            Text("Código válido listo para activación", color = WoodsEmerald, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        } else if (rawInput.isBlank()) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = WoodsTextMuted, modifier = Modifier.size(16.dp))
                            Text("Pega o escribe el código de 8 caracteres que muestra Woods Reader", color = WoodsTextMuted, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            Text("Formato no reconocido. Debe ser WR-XXXX-XXXX", color = Color(0xFFEF5350), fontSize = 13.sp)
                        }
                    }

                    // Nota opcional
                    OutlinedTextField(
                        value = buyerNote,
                        onValueChange = { buyerNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre o nota del comprador (opcional)", fontSize = 13.sp) },
                        placeholder = { Text("Ej: Carlos (WhatsApp / PayPal)", color = WoodsTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WoodsGold,
                            unfocusedBorderColor = WoodsDarkSurfaceVariant,
                            focusedTextColor = WoodsTextPrimary,
                            unfocusedTextColor = WoodsTextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }
            }
        }

        // Card con la Clave Pro Generada
        item {
            AnimatedVisibility(
                visible = isValid && generatedKey.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WoodsDarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(WoodsGold)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = WoodsGold)
                            Text(
                                text = "CLAVE PRO OFICIAL",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = WoodsGoldBright,
                                letterSpacing = 1.sp
                            )
                        }

                        // Contenedor visual de la clave
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WoodsGoldContainer)
                                .border(1.dp, WoodsGold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = generatedKey,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 21.sp,
                                color = WoodsGoldBright,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Botones de acción
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Woods Pro Key", generatedKey)
                                    clipboard.setPrimaryClip(clip)
                                    onSaveToHistory(cleanCode, generatedKey, buyerNote)
                                    Toast.makeText(context, "✓ Clave copiada al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WoodsGold,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Copiar Clave", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onSaveToHistory(cleanCode, generatedKey, buyerNote)
                                    val message = KeymakerEngine.buildShareMessage(cleanCode, generatedKey, buyerNote)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message)
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Enviar Clave Pro")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WoodsEmeraldDark,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Compartir", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Botón de guardar en historial explícito
                        OutlinedButton(
                            onClick = {
                                onSaveToHistory(cleanCode, generatedKey, buyerNote)
                                Toast.makeText(context, "✓ Guardado en historial", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WoodsTextSecondary)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar en Historial", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

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
                        label = { Text("Clave Pro (PRO-XXXX-XXXX-XXXX)") },
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
                                    "La firma criptográfica HMAC-SHA256 coincide al 100% con este dispositivo."
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
                    text = "No hay claves en el historial",
                    color = WoodsTextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Las claves que generes aparecerán aquí",
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
                        text = "${historyList.size} Activaciones registradas",
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
                                text = entry.buyerNote.ifBlank { "Activación Pro" },
                                fontWeight = FontWeight.Bold,
                                color = WoodsTextPrimary,
                                fontSize = 15.sp
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

                        // Clave con fondo resaltado
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Acciones rápidas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Woods Pro Key", entry.proKey)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "✓ Clave copiada", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = WoodsGold)
                            }

                            IconButton(
                                onClick = {
                                    val message = KeymakerEngine.buildShareMessage(entry.installCode, entry.proKey, entry.buyerNote)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message)
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Enviar Clave Pro")
                                    context.startActivity(shareIntent)
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Compartir", tint = WoodsEmerald)
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
