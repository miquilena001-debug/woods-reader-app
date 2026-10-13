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
    onPermissionsGranted: () -> Unit,
    onRequestPermission: () -> Unit
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
                text = "Permiso requerido",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val permissionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "La app necesita acceso a 'Todos los archivos' para poder escanear el almacenamiento en busca de tus ePubs y audiolibros automáticamente."
            } else {
                "La app necesita acceso al almacenamiento para poder encontrar tus ePubs y audiolibros."
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
                Text(text = "Otorgar Permiso")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onPermissionsGranted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Explorar app sin escanear")
            }
        }
    }
}
