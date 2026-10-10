package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.R
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    ttsState: TtsState,
    hasSelection: Boolean,
    hazeState: HazeState,
    onBack: () -> Unit,
    onHighlight: () -> Unit,
    onTtsToggle: () -> Unit,
    onMenuClick: () -> Unit
) {
    Surface(
        color = Color.Transparent, // Fondo transparente ya que usaremos el desenfoque
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(
                state = hazeState,
                style = ReaderDesign.GlassStyle
            )
            // Borde de cristal (Efecto Rim Light en la parte superior e inferior)
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.15f),
                    0.5f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.05f)
                ),
                shape = RectangleShape
            )
            .statusBarsPadding(),
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = HiddenForest.Text
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = HiddenForest.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.reader_reading_in_progress),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = HiddenForest.Accent.copy(0.7f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSelection) {
                    ReaderIconButton(
                    icon = Icons.Default.Brush,
                    contentDescription = stringResource(R.string.reader_highlight),
                    onClick = onHighlight,
                    tint = HiddenForest.Amber
                    )
                }
                
                // << CHANGE: AnimatedContent para animación Play/Pause suave
                val isPlaying = ttsState == TtsState.PLAYING
                IconButton(
                    onClick = onTtsToggle,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isPlaying) {
                                Modifier
                                    .background(HiddenForest.Accent.copy(0.12f))
                                    .border(1.dp, HiddenForest.Accent.copy(0.3f), RoundedCornerShape(12.dp))
                            } else Modifier
                        )
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                        },
                        label = "tts-icon-animation"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.GraphicEq,
                            contentDescription = stringResource(if (playing) R.string.reader_tts_pause else R.string.reader_tts_play),
                            tint = if (playing) HiddenForest.Accent else HiddenForest.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))

                ReaderIconButton(
                    icon = Icons.Default.MenuOpen,
                    contentDescription = stringResource(R.string.reader_menu),
                    onClick = onMenuClick,
                    tint = HiddenForest.Accent
                )
            }
        }
            
        HorizontalDivider(
            color = HiddenForest.Outline.copy(alpha = 0.25f),
            thickness = 0.5.dp
        )
    }
}
}

