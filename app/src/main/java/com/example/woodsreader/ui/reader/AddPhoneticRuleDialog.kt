package com.example.woodsreader.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun AddPhoneticRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (original: String, replacement: String) -> Unit
) {
    var originalText by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir Palabra Personalizada", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reemplaza la pronunciación de nombres o términos específicos antes de sintetizar con la voz de estudio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiddenForest.TextSecondary
                )
                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text("Texto Original (ej. Jiang Chen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Cómo pronunciarlo (ej. Yian Chen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (originalText.isNotBlank() && replacementText.isNotBlank()) {
                        onAdd(originalText.trim(), replacementText.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HiddenForest.Accent,
                    contentColor = HiddenForest.Background
                )
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = HiddenForest.TextSecondary)
            }
        },
        containerColor = HiddenForest.SurfaceVariant,
        shape = RoundedCornerShape(16.dp)
    )
}
