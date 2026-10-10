package com.example.woodsreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun LibraryHeader(
    isScanning: Boolean, 
    scanProgress: String,
    readingCount: Int, 
    finishedCount: Int, 
    onScanClick: () -> Unit,
    onAddFolderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "MI COLECCION",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextLow,
                letterSpacing = 2.sp
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = HiddenForest.Text)) {
                        append("Biblioteca")
                    }
                },
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
                color = HiddenForest.Text
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WoodsBadge(text = "$readingCount leyendo", color = HiddenForest.Accent)
            WoodsBadge(text = "$finishedCount leidos", color = HiddenForest.Amber)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(HiddenForest.SurfaceVariant)
                .border(1.dp, if (isScanning) HiddenForest.Accent else HiddenForest.Outline, RoundedCornerShape(12.dp))
                .clickable { onScanClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = HiddenForest.Accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = scanProgress.ifBlank { "Escaneando…" },
                        style = MaterialTheme.typography.labelMedium, 
                        color = HiddenForest.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar escaneo",
                        tint = HiddenForest.Accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = HiddenForest.TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sincronizar", style = MaterialTheme.typography.labelMedium, color = HiddenForest.TextSecondary)
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(HiddenForest.SurfaceVariant)
                .border(1.dp, HiddenForest.Outline, RoundedCornerShape(12.dp))
                .clickable { onAddFolderClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = "Añadir Carpeta", tint = HiddenForest.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun WoodsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(1.dp, HiddenForest.Outline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = HiddenForest.TextLow,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = HiddenForest.Text),
                decorationBox = @Composable { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Buscar titulo, autor(es)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiddenForest.TextLow
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Limpiar",
                    tint = HiddenForest.TextLow,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}
