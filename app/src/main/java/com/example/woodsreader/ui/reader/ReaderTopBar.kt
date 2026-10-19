package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuOpen
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
    isPdf: Boolean = false,
    isReflowMode: Boolean = false,
    onToggleReflow: () -> Unit = {},
    onBack: () -> Unit,
    onHighlight: () -> Unit,
    onTtsToggle: () -> Unit,
    onXRayClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(
                state = hazeState,
                style = ReaderDesign.GlassStyle
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A140F).copy(alpha = 0.96f),
                        Color(0xFF0A140F).copy(alpha = 0.96f)
                    )
                )
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
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                if (isPdf) {
                    Surface(
                        onClick = onToggleReflow,
                        color = if (isReflowMode) HiddenForest.Accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isReflowMode) HiddenForest.Accent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isReflowMode) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (isReflowMode) HiddenForest.Accent else HiddenForest.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isReflowMode) "Libro" else "Original",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isReflowMode) HiddenForest.Accent else HiddenForest.TextSecondary
                            )
                        }
                    }
                }

                if (hasSelection) {
                    ReaderIconButton(
                    icon = Icons.Default.Brush,
                    contentDescription = stringResource(R.string.reader_highlight),
                    onClick = onHighlight,
                    tint = HiddenForest.Amber
                    )
                }
                
                val isTtsActive = ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED
                IconButton(
                    onClick = onTtsToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.reader_tts_play),
                        tint = if (isTtsActive) HiddenForest.Accent else HiddenForest.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onXRayClick != null) {
                    IconButton(
                        onClick = onXRayClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Woods X-Ray",
                            tint = HiddenForest.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))

                ReaderIconButton(
                    icon = Icons.AutoMirrored.Filled.MenuOpen,
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
