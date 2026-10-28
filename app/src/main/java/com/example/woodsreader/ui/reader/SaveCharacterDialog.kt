package com.example.woodsreader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun SaveCharacterDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val roles = listOf("Protagonista", "Aliado", "Enemigo", "Clan / Lugar", "Artefacto")
    var selectedRole by remember { mutableStateOf(roles[0]) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0E1A13))
                .border(1.dp, HiddenForest.Accent.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = HiddenForest.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Guardar Personaje o Término",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre / Término", color = HiddenForest.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.OutlineHi,
                        cursorColor = HiddenForest.Accent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ROL / CATEGORÍA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HiddenForest.TextLow
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roles.forEach { role ->
                            val isSelected = selectedRole == role
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) HiddenForest.Accent else Color(0xFF162B20),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HiddenForest.Accent else HiddenForest.OutlineHi.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.clickable { selectedRole = role }
                            ) {
                                Text(
                                    text = role,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color(0xFF080B08) else Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota o descripción (opcional)", color = HiddenForest.TextSecondary) },
                    placeholder = { Text("Ej. Aliado en el capítulo 4...", color = HiddenForest.TextLow.copy(alpha = 0.6f)) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.OutlineHi,
                        cursorColor = HiddenForest.Accent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = HiddenForest.TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name.trim(), selectedRole, note.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HiddenForest.Accent),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank()
                    ) {
                        Text(
                            text = "Guardar",
                            color = Color(0xFF080B08),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
