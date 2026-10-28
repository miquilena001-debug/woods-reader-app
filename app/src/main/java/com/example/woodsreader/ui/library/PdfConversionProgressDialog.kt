package com.example.woodsreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.woodsreader.core.pdf.PdfConversionProgress
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun PdfConversionProgressDialog(
    bookTitle: String,
    progress: PdfConversionProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var isMinimized by remember { mutableStateOf(false) }

    val emeraldGradient = Brush.verticalGradient(
        listOf(
            Color(0xF2064E3B),
            Color(0xF8043629),
            Color(0xFC022119)
        )
    )
    val emeraldBorder = Brush.verticalGradient(
        listOf(
            Color(0xFF34D399).copy(alpha = 0.6f),
            Color(0xFF10B981).copy(alpha = 0.35f),
            Color(0xFF059669).copy(alpha = 0.15f)
        )
    )

    if (isMinimized && !progress.isFinished) {
        // ── Widget flotante minimizado (Apple Liquid Glass Dock) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.75f),
                        spotColor = Color(0xFF10B981).copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(emeraldGradient)
                    .border(1.25.dp, emeraldBorder, RoundedCornerShape(22.dp))
                    .clickable { isMinimized = false }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = {
                                if (progress.totalPages > 0) progress.currentPage.toFloat() / progress.totalPages.toFloat()
                                else 0f
                            },
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF34D399),
                            trackColor = Color(0xFF10B981).copy(alpha = 0.25f),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "⚡",
                            fontSize = 13.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFECFDF5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val pct = if (progress.totalPages > 0) (progress.currentPage * 100 / progress.totalPages) else 0
                        Text(
                            text = "$pct% • ${progress.statusMessage.ifBlank { "Procesando páginas..." }}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFFA7F3D0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { isMinimized = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expandir",
                            tint = Color(0xFFECFDF5),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = {
            if (progress.isFinished) onDismiss()
            else isMinimized = true
        },
        properties = DialogProperties(
            dismissOnBackPress = progress.isFinished,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.8f),
                    spotColor = Color(0xFF10B981).copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(emeraldGradient)
                .border(1.5.dp, emeraldBorder, RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header con botón minimizar / cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "⚡", fontSize = 20.sp)
                        Text(
                            text = if (progress.isFinished) "¡Conversión Finalizada!" else "Convertir PDF a EPUB",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFECFDF5)
                        )
                    }

                    if (!progress.isFinished) {
                        IconButton(
                            onClick = { isMinimized = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimizar",
                                tint = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }

                // Título del libro
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFD1FAE5),
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )

                // Barra de Progreso
                val fraction = if (progress.totalPages > 0) {
                    (progress.currentPage.toFloat() / progress.totalPages.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF34D399),
                        trackColor = Color(0xFF065F46)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (progress.totalPages > 0) "Pág ${progress.currentPage} de ${progress.totalPages}" else "Iniciando...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFFA7F3D0)
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color(0xFFECFDF5)
                        )
                    }
                }

                // Tarjetas de Estadísticas en Vivo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Capítulos",
                        value = "${progress.chaptersCreated}",
                        icon = "📑",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Ilustraciones",
                        value = "${progress.illustrationsSaved}",
                        icon = "🖼️",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Spam/Banners",
                        value = "${progress.adsBlocked}",
                        icon = "🚫",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Mensaje de estado
                Text(
                    text = progress.statusMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFFD1FAE5).copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Botones de acción
                if (progress.isFinished) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color(0xFF022119)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Abrir Biblioteca",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFCA5A5)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancelar Conversión")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF022C22).copy(alpha = 0.65f))
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = icon, fontSize = 14.sp)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = Color(0xFFECFDF5)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color(0xFFA7F3D0),
                maxLines = 1
            )
        }
    }
}
