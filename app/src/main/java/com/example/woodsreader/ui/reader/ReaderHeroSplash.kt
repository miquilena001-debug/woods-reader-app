package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.ui.theme.HiddenForest

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderHeroSplash(
    uiState: ReaderUiState,
    heroBook: BookEntity?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    AnimatedVisibility(
        visible = uiState is ReaderUiState.Loading || uiState is ReaderUiState.InitialLoading,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.02f, animationSpec = tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HiddenForest.Background),
            contentAlignment = Alignment.Center
        ) {
            heroBook?.let { book ->
                // Fondo difuminado estático con baja opacidad para dar profundidad sin saturar texturas EGL Mali
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverCachePath?.let { java.io.File(it) })
                        .memoryCacheKey("cover-reader-splash-blur-${book.id}")
                        .diskCacheKey("cover-reader-splash-blur-${book.id}")
                        .size(400, 600) // Force downscale to fix Skia memory loop
                        .crossfade(400)
                        .build(),
                    placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.25f
                        },
                    contentScale = ContentScale.Crop
                )
                
                // Capa de degradado encima para oscurecer y dar legibilidad
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    HiddenForest.Background.copy(alpha = 0.4f),
                                    HiddenForest.Background.copy(alpha = 0.9f),
                                    HiddenForest.Background
                                )
                            )
                        )
                )

                // Contenedor principal con la portada física centrada y el spinner
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    // Portada del libro elevada con SharedElement
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HiddenForest.SurfaceVariant)
                            .border(1.dp, HiddenForest.Outline, RoundedCornerShape(12.dp))
                    ) {
                        if (book.coverCachePath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(java.io.File(book.coverCachePath))
                                    .memoryCacheKey("cover-reader-${book.id}")
                                    .diskCacheKey("cover-reader-${book.id}")
                                    .size(280, 400)
                                    .crossfade(true)
                                    .build(),
                                placeholder = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                                error = androidx.compose.ui.res.painterResource(com.example.woodsreader.R.drawable.placeholder),
                                contentDescription = book.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            state = rememberSharedContentState(key = "cover-${book.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Fallback de iniciales del libro si no tiene portada
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(HiddenForest.Accent.copy(0.18f), HiddenForest.Surface)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = book.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "L",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = HiddenForest.Accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    CircularProgressIndicator(
                        color = HiddenForest.Accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        ),
                        color = HiddenForest.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = book.author.ifBlank { "Autor Desconocido" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = HiddenForest.TextLow
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PREPARANDO LECTURA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HiddenForest.Accent
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(140.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = HiddenForest.Accent,
                        trackColor = HiddenForest.Accent.copy(alpha = 0.2f)
                    )
                }
            } ?: CircularProgressIndicator(color = HiddenForest.Accent)
        }
    }
}
