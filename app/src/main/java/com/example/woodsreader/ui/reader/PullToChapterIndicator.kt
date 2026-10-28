package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.woodsreader.R
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@Composable
fun PullToChapterIndicator(
    progress: Float,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    if (progress == 0f) return
    
    val isForward = progress > 0
    val absProgress = kotlin.math.abs(progress)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.2f * absProgress))
    ) {
        Box(
            modifier = Modifier
                .align(if (isForward) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(bottom = if (isForward) 120.dp else 24.dp, top = if (!isForward) 120.dp else 24.dp)
                .graphicsLayer {
                    alpha = absProgress
                    scaleX = 0.8f + (0.2f * absProgress)
                    scaleY = 0.8f + (0.2f * absProgress)
                }
                .hazeChild(
                    state = hazeState,
                    style = ReaderDesign.GlassStyle
                )
                .clip(RoundedCornerShape(32.dp))
                .background(HiddenForest.Surface.copy(alpha = 0.8f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.2f),
                        1.0f to Color.Transparent
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = if (absProgress >= 0.95f) HiddenForest.Accent else HiddenForest.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(if (isForward) R.string.reader_next_chapter else R.string.reader_previous_chapter),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (absProgress >= 0.95f) HiddenForest.Accent else HiddenForest.TextSecondary
                )
            }
        }
    }
}
