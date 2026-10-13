package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Publication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle

import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ReaderBottomSlider(
    navigator: org.readium.r2.navigator.Navigator?,
    globalProgressFlow: StateFlow<Float>,
    chapterInfoFlow: StateFlow<ChapterInfo>,
    bookTimeRemaining: String?,
    chapterTimeRemaining: String?,
    hazeState: HazeState,
    onProgressChange: (Float) -> Unit,
    onShowToc: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit
) {
    val globalProgress by globalProgressFlow.collectAsStateWithLifecycle()
    val chapterInfo by chapterInfoFlow.collectAsStateWithLifecycle()
    val chapterTitle = chapterInfo.title
    val chapterMinutes = chapterInfo.timeRemainingMinutes
    Surface(
        color = HiddenForest.Surface.copy(alpha = 0.88f),
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(
                state = hazeState,
                style = ReaderDesign.GlassStyle
            )
            // Borde de cristal (Efecto Rim en la parte superior)
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.15f),
                    0.5f to Color.Transparent
                ),
                shape = RectangleShape
            )
            .navigationBarsPadding(),
        shadowElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                color = HiddenForest.Outline.copy(alpha = 0.25f),
                thickness = 0.5.dp
            )
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                var isDragging by remember { mutableStateOf(false) }
                var sliderValue by remember { mutableStateOf(globalProgress) }

                // Sync externo solo cuando el usuario NO está arrastrando.
                // Sin esto, cualquier update de globalProgress (cada ~2s) resetea el slider.
                LaunchedEffect(globalProgress) {
                    if (!isDragging) sliderValue = globalProgress
                }

                Text(
                    text = String.format("%.1f%%", (if (isDragging) sliderValue else globalProgress) * 100),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Accent,
                    modifier = Modifier.width(48.dp)
                )

                Slider(
                    value = if (isDragging) sliderValue else globalProgress,
                    onValueChange = { 
                        if (!isDragging) isDragging = true
                        
                        // Haptic feedback cada 10%
                        if ((it * 100).toInt() % 10 == 0 && (sliderValue * 100).toInt() % 10 != 0) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onProgressChange(sliderValue)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = HiddenForest.Accent,
                        activeTrackColor = HiddenForest.Accent,
                        inactiveTrackColor = HiddenForest.Outline
                    )
                )

                IconButton(onClick = onShowToc) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Índice",
                        tint = HiddenForest.Accent
                    )
                }
            }
            
            if (chapterTitle != null || chapterTimeRemaining != null || bookTimeRemaining != null || chapterMinutes != null) {
                Text(
                    text = buildString {
                        if (chapterTitle != null) append(chapterTitle)
                        val extra = mutableListOf<String>()
                        chapterTimeRemaining?.let { extra.add(it) }
                        bookTimeRemaining?.let { extra.add(it) }
                        if (extra.isNotEmpty()) {
                            if (chapterTitle != null) append(" · ")
                            append(extra.joinToString(" | "))
                        } else if (chapterMinutes != null) {
                            if (chapterTitle != null) append(" · ")
                            append("$chapterMinutes min restantes")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = HiddenForest.TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Quick Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPreviousChapter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f), 
                        contentColor = HiddenForest.TextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cap. Anterior", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onNextChapter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HiddenForest.SurfaceVariant.copy(alpha = 0.5f), 
                        contentColor = HiddenForest.TextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
                ) {
                    Text("Cap. Siguiente", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        }
    }
}
