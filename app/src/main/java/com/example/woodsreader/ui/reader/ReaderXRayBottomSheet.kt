package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.core.xray.ChapterXRayReport
import com.example.woodsreader.core.xray.XRayCategory
import com.example.woodsreader.core.xray.XRayEntity
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderXRayBottomSheet(
    report: ChapterXRayReport?,
    hazeState: HazeState,
    onSearchInBook: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(XRayCategory.CHARACTER) }
    var expandedEntityName by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        containerColor = Color(0xFF0C1811).copy(alpha = 0.98f),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .fillMaxHeight(0.88f)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .border(BorderStroke(1.dp, Color(0xFF233B2C)), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .hazeChild(
                state = hazeState,
                style = ReaderDesign.GlassStyle
            )
    ) {
        if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = HiddenForest.Accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
            return@ModalBottomSheet
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // ── Cabecera: Título y métricas del capítulo ──────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WOODS X-RAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.Accent,
                            letterSpacing = 2.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = report.chapterTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = HiddenForest.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar X-Ray",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Badges de Métricas del Capítulo ──────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF13261C).copy(alpha = 0.85f),
                        border = BorderStroke(0.8.dp, HiddenForest.Accent.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "⏱️ ~${report.estimatedReadingMinutes} min lectura",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = HiddenForest.Text
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF13261C).copy(alpha = 0.85f),
                        border = BorderStroke(0.8.dp, HiddenForest.Accent.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "💬 ${report.dialoguePercentage}% diálogo",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = HiddenForest.Text
                            )
                        }
                    }
                }
            }

            // ── Tarjeta "¿Dónde me quedé?" ────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF102017).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF233B2C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📍 ¿Dónde me quedé?",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = HiddenForest.Accent
                            )
                        }

                        if (report.whereYouLeftOffRecap.isEmpty()) {
                            Text(
                                text = "Estás comenzando este capítulo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextSecondary
                            )
                        } else {
                            report.whereYouLeftOffRecap.forEach { snippet ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .background(HiddenForest.Accent.copy(alpha = 0.7f), CircleShape)
                                    )
                                    Text(
                                        text = snippet,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            lineHeight = 18.sp,
                                            fontSize = 12.5.sp
                                        ),
                                        color = HiddenForest.Text.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Pestañas Interactivas (Categorías NER) ─────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F1E15))
                        .border(BorderStroke(0.8.dp, Color(0xFF233B2C)), RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    XRayCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        val count = when (cat) {
                            XRayCategory.CHARACTER -> report.characters.size
                            XRayCategory.LOCATION_FACTION -> report.locationsAndFactions.size
                            XRayCategory.SKILL_ITEM -> report.skillsAndItems.size
                        }

                        Surface(
                            onClick = {
                                selectedCategory = cat
                                expandedEntityName = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) HiddenForest.Accent.copy(alpha = 0.18f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${cat.badgeEmoji} ${cat.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) HiddenForest.Accent else HiddenForest.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "($count)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Light
                                    ),
                                    color = if (isSelected) HiddenForest.Accent.copy(alpha = 0.8f) else HiddenForest.TextLow
                                )
                            }
                        }
                    }
                }
            }

            // ── Lista de Entidades para la Categoría Seleccionada ───────────────
            val currentEntities = when (selectedCategory) {
                XRayCategory.CHARACTER -> report.characters
                XRayCategory.LOCATION_FACTION -> report.locationsAndFactions
                XRayCategory.SKILL_ITEM -> report.skillsAndItems
            }

            if (currentEntities.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se detectaron entidades destacadas en esta categoría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextLow
                        )
                    }
                }
            } else {
                items(currentEntities, key = { it.name }) { entity ->
                    val isExpanded = expandedEntityName == entity.name

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF102017).copy(alpha = 0.95f),
                        border = BorderStroke(
                            width = if (isExpanded) 1.dp else 0.8.dp,
                            color = if (isExpanded) HiddenForest.Accent.copy(alpha = 0.5f) else Color(0xFF1E3627)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedEntityName = if (isExpanded) null else entity.name
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = entity.category.badgeEmoji,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = entity.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = HiddenForest.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = HiddenForest.Accent.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, HiddenForest.Accent.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "${entity.mentionsInChapter}x",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = HiddenForest.Accent,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                        tint = HiddenForest.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Contenido expandido: Menciones de contexto y botón Buscar
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(
                                        color = Color(0xFF1E3627),
                                        thickness = 0.8.dp
                                    )

                                    Text(
                                        text = "Menciones en este capítulo:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = HiddenForest.Accent
                                    )

                                    entity.contextSnippets.forEach { snippet ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF09140E).copy(alpha = 0.9f),
                                            border = BorderStroke(0.5.dp, Color(0xFF1E3627)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "“$snippet”",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontStyle = FontStyle.Italic,
                                                    lineHeight = 17.sp,
                                                    fontSize = 12.sp
                                                ),
                                                color = HiddenForest.Text.copy(alpha = 0.85f),
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            onSearchInBook(entity.name)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HiddenForest.Accent.copy(alpha = 0.2f),
                                            contentColor = HiddenForest.Accent
                                        ),
                                        border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Buscar \"${entity.name}\" en todo el libro",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
