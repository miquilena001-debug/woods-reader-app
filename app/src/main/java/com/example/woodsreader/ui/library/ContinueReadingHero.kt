package com.example.woodsreader.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.woodsreader.ui.theme.HiddenForest
import java.io.File

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ContinueReadingHero(
    bookId: String,
    title: String, 
    author: String, 
    progress: Int, 
    chapter: String, 
    coverPath: String?, 
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = "CONTINUAR LEYENDO",
            style = MaterialTheme.typography.labelSmall,
            color = HiddenForest.TextLow,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(HiddenForest.SurfaceVariant, HiddenForest.Surface)
                    )
                )
                .border(1.dp, HiddenForest.OutlineHi, RoundedCornerShape(18.dp))
                .clickable { onClick() }
                .semantics {
                    role = Role.Button
                    contentDescription = "Continuar leyendo: $title, por $author. Progreso: $progress%"
                    onClick { onClick(); true }
                }
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Miniatura libro
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(HiddenForest.Accent.copy(0.35f), HiddenForest.SurfaceVariant)
                            )
                        )
                        .border(1.dp, HiddenForest.Accent.copy(0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val coverFile = remember(coverPath) {
                        coverPath?.takeIf { it.isNotBlank() }?.let { File(it) }
                    }

                    if (coverFile != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(coverFile)
                                .memoryCacheKey("cover-list-$bookId")
                                .diskCacheKey("cover-list-$bookId")
                                .crossfade(true)
                                .build(),
                            placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                            error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        state = rememberSharedContentState(key = "cover-$bookId"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text("📖", fontSize = 20.sp)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (chapter.isNotBlank()) "$chapter · $author" else author,
                        style = MaterialTheme.typography.bodySmall,
                        color = HiddenForest.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    WoodsProgressBar(progress = progress.toFloat() / 100f)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$progress% completado",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiddenForest.TextLow
                    )
                }

                // Boton Play
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(HiddenForest.Accent.copy(alpha = 0.18f))
                        .border(1.dp, HiddenForest.Accent.copy(0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Leer", tint = HiddenForest.Accent, modifier = Modifier.size(14.dp))
                        Text("Leer", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Accent)
                    }
                }
            }
        }
    }
}
