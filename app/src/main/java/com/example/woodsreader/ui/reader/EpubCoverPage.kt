package com.example.woodsreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.woodsreader.R
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.ui.theme.HiddenForest
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import java.io.File

@Composable
fun EpubCoverPage(
    book: BookEntity,
    publication: Publication? = null,
    onDismiss: () -> Unit
) {
    var inMemoryCover by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(book.id, publication) {
        val cacheFile = book.coverCachePath?.let(::File)
        if (cacheFile == null || !cacheFile.exists()) {
            publication?.let { pub ->
                try {
                    inMemoryCover = pub.cover()
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        HiddenForest.Surface,
                        HiddenForest.Background,
                        HiddenForest.Background
                    )
                )
            )
            .clickable(onClick = onDismiss)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    if (kotlin.math.abs(dragAmount.x) > 20f || kotlin.math.abs(dragAmount.y) > 20f) {
                        onDismiss()
                    }
                }
            }
            .zIndex(1000f)
            .padding(horizontal = 32.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val cacheFile = book.coverCachePath?.let(::File)
            val hasValidFile = cacheFile != null && cacheFile.exists()

            Box(
                modifier = Modifier
                    .width(230.dp)
                    .height(340.dp)
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(HiddenForest.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (hasValidFile) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(cacheFile)
                            .memoryCacheKey("epub-cover-page-${book.id}")
                            .diskCacheKey("epub-cover-page-${book.id}")
                            .size(460, 680)
                            .crossfade(250)
                            .build(),
                        placeholder = androidx.compose.ui.res.painterResource(R.drawable.placeholder),
                        error = androidx.compose.ui.res.painterResource(R.drawable.placeholder),
                        contentDescription = "Portada de ${book.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (inMemoryCover != null) {
                    Image(
                        bitmap = inMemoryCover!!.asImageBitmap(),
                        contentDescription = "Portada de ${book.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Portada de reserva estilizada
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        HiddenForest.Surface,
                                        HiddenForest.SurfaceVariant,
                                        HiddenForest.Background
                                    )
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = HiddenForest.Accent,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (book.author.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.TextLow,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                ),
                color = HiddenForest.Text,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.titleSmall,
                    color = HiddenForest.TextLow,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "TOCA PARA COMENZAR",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = HiddenForest.Accent
            )
        }
    }
}
