package com.example.woodsreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.database.CollectionEntity
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.Semantic
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookOptionsBottomSheet(
    book: BookEntity,
    collections: List<CollectionEntity>,
    onDismiss: () -> Unit,
    onStatusChange: (ReadingStatus) -> Unit,
    onCollectionChange: (String?) -> Unit,
    onDeleteClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HiddenForest.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(HiddenForest.Outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Portada + Título + Autor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(HiddenForest.Accent.copy(0.3f), HiddenForest.SurfaceVariant)
                            )
                        )
                        .border(1.dp, HiddenForest.Accent.copy(0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val coverFile = remember(book.coverCachePath) {
                        book.coverCachePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                    }

                    if (coverFile != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(coverFile)
                                .memoryCacheKey("cover-bottomsheet-${book.id}")
                                .diskCacheKey("cover-bottomsheet-${book.id}")
                                .crossfade(true)
                                .build(),
                            placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                            error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = book.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = HiddenForest.Accent
                            )
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.author.ifBlank { "Autor Desconocido" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Sección 1: Metadatos
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "INFORMACIÓN DEL ARCHIVO",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextLow,
                    letterSpacing = 1.sp
                )
                
                val sizeMb = book.fileSizeBytes.toDouble() / (1024 * 1024)
                val sizeFormatted = String.format(Locale.US, "%.2f MB", sizeMb)
                
                MetadataRow(label = "Formato:", value = book.format.name)
                MetadataRow(label = "Tamaño:", value = sizeFormatted)
                MetadataRow(label = "Ruta:", value = book.filePath, maxLines = 2)
            }

            HorizontalDivider(color = HiddenForest.Outline, thickness = 1.dp)

            // Sección 2: Estado de lectura
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ESTADO DE LECTURA",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextLow,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statuses = listOf(
                        Triple(ReadingStatus.NOT_STARTED, "Sin Empezar", HiddenForest.TextLow),
                        Triple(ReadingStatus.IN_PROGRESS, "Leyendo", HiddenForest.Accent),
                        Triple(ReadingStatus.COMPLETED, "Terminado", HiddenForest.Amber),
                        Triple(ReadingStatus.ABANDONED, "Abandonado", Semantic.Error)
                    )

                    statuses.forEach { (status, label, color) ->
                        val isSelected = book.readingStatus == status
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f)
                                    else HiddenForest.SurfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) color else HiddenForest.Outline,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onStatusChange(status) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 9.sp
                                ),
                                color = if (isSelected) color else HiddenForest.TextSecondary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = HiddenForest.Outline, thickness = 1.dp)

            // Sección 3: Colecciones
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ASIGNAR A COLECCIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextLow,
                    letterSpacing = 1.sp
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isSelected = book.collectionId == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) HiddenForest.Accent.copy(alpha = 0.15f)
                                    else HiddenForest.SurfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) HiddenForest.Accent else HiddenForest.Outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onCollectionChange(null) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Ninguna",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) HiddenForest.Text else HiddenForest.TextSecondary
                            )
                        }
                    }

                    items(collections, key = { it.id }) { collection ->
                        val isSelected = book.collectionId == collection.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) HiddenForest.Accent.copy(alpha = 0.15f)
                                    else HiddenForest.SurfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) HiddenForest.Accent else HiddenForest.Outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onCollectionChange(collection.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = collection.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) HiddenForest.Text else HiddenForest.TextSecondary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = HiddenForest.Outline, thickness = 1.dp)

            // Sección 4: Acción de eliminación
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Semantic.Error.copy(alpha = 0.1f))
                    .border(1.dp, Semantic.Error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onDeleteClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Semantic.Error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Eliminar de la biblioteca",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Semantic.Error
                    )
                }
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = HiddenForest.TextLow,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = HiddenForest.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
