package com.example.woodsreader.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.ui.theme.HiddenForest

/**
 * Diálogo modal para desbloquear "Woods Reader PRO — Edición Vitalicia" mediante
 * validación criptográfica HMAC-SHA256 vinculada al código de instalación del dispositivo.
 */
@Composable
fun WoodsProUnlockDialog(
    proLicenseManager: ProLicenseManager,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit = {}
) {
    val context = LocalContext.current
    val installCode = proLicenseManager.deviceInstallCode
    var inputKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successActivated by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0C1811),
            border = BorderStroke(1.dp, Color(0xFF233B2C)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Cabecera con Insignia PRO ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(HiddenForest.Amber.copy(alpha = 0.35f), Color.Transparent)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👑", fontSize = 28.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "✨ Woods Reader PRO",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = HiddenForest.Text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Edición Vitalicia Anti-Piratería",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = HiddenForest.Accent
                    )
                }

                // ── Lista de Beneficios Exclusivos ────────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF13261C).copy(alpha = 0.7f),
                    border = BorderStroke(0.8.dp, Color(0xFF233B2C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProBenefitRow("🎧", "Exportación masiva de audiolibros .M4A por lotes (Próx. 10 Caps y Todo el Libro)")
                        ProBenefitRow("🖼️", "Tarjetas de citas Ultra HD sin marca de agua")
                        ProBenefitRow("⚡", "Motor Woods X-Ray ilimitado (Personajes, Lugares y Técnicas)")
                        ProBenefitRow("🌲", "Paisajes Sonoros de Estudio y Ambientes Forestales HD")
                    }
                }

                // ── Sección: ID del Dispositivo ──────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ID DE TU DISPOSITIVO:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = HiddenForest.TextSecondary
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF09140E),
                        border = BorderStroke(1.dp, Color(0xFF233B2C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = installCode,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.5.sp
                                ),
                                color = HiddenForest.Accent
                            )

                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Woods Device ID", installCode))
                                    Toast.makeText(context, "ID copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar ID",
                                    tint = HiddenForest.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Envía este ID al soporte o genera tu clave con el keygen.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = HiddenForest.TextLow
                    )
                }

                // ── Campo de Texto para Ingresar la Clave PRO ──────────────────
                if (!successActivated) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "INGRESAR CLAVE PRO:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = HiddenForest.TextSecondary
                        )

                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = {
                                inputKey = it.uppercase()
                                errorMessage = null
                            },
                            placeholder = {
                                Text(
                                    text = "PRO-XXXX-XXXX-XXXX",
                                    color = HiddenForest.TextLow,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            isError = errorMessage != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = HiddenForest.Text,
                                unfocusedTextColor = HiddenForest.Text,
                                focusedBorderColor = HiddenForest.Accent,
                                unfocusedBorderColor = Color(0xFF233B2C),
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                cursorColor = HiddenForest.Accent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                        }

                        Button(
                            onClick = {
                                if (inputKey.isBlank()) {
                                    errorMessage = "Por favor ingresa la clave de licencia."
                                    return@Button
                                }
                                val ok = proLicenseManager.activateWithLicenseKey(inputKey)
                                if (ok) {
                                    successActivated = true
                                    Toast.makeText(context, "¡Woods Reader PRO desbloqueado con éxito!", Toast.LENGTH_LONG).show()
                                    onUnlocked()
                                } else {
                                    errorMessage = "Clave no válida para este dispositivo."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HiddenForest.Accent,
                                contentColor = Color(0xFF0A140F)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Activar Licencia Vitalicia",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = HiddenForest.Accent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HiddenForest.Accent)
                            Text(
                                text = "¡Licencia PRO activada con éxito para este dispositivo!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Accent
                            )
                        }
                    }
                }

                // ── Botón Cerrar ──────────────────────────────────────────────
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (successActivated) "Continuar" else "Cerrar",
                        color = HiddenForest.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProBenefitRow(icon: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = icon, fontSize = 16.sp)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            ),
            color = HiddenForest.Text.copy(alpha = 0.9f)
        )
    }
}
