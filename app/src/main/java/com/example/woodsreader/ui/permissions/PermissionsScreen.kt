package com.example.woodsreader.ui.permissions

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(
    onRequestPermission: () -> Unit,
    onChooseFoldersManually: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Woods Reader",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "¿Cómo quieres buscar tus libros?",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val permissionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "Concede acceso para buscar libros automáticamente en el dispositivo. Si prefieres no concederlo, continúa y luego usa «Añadir carpeta» en Biblioteca. La carpeta elegida y sus subcarpetas se podrán sincronizar cuando quieras."
            } else {
                "Concede acceso para buscar libros automáticamente. Si prefieres no concederlo, continúa y luego usa «Añadir carpeta» en Biblioteca. La carpeta elegida y sus subcarpetas se podrán sincronizar cuando quieras."
            }
            
            Text(
                text = permissionText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Abrir Ajustes y conceder acceso total")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onChooseFoldersManually,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Continuar sin escaneo automático")
            }
        }
    }
}
