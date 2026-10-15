package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.toArgb
import com.example.woodsreader.ui.theme.HiddenForest

/**
 * Common Design Tokens and UI components for the Reader system.
 * Implements a Solid, Fast, Librera-inspired aesthetic.
 */
object ReaderDesign {
    val SurfaceColor = HiddenForest.SurfaceVariant
    
    val GlassStyle = HazeStyle(
        backgroundColor = HiddenForest.Surface,
        tint = HazeTint(HiddenForest.Surface.copy(alpha = 0.4f)),
        blurRadius = 15.dp,
        noiseFactor = 0.1f
    )

    val DeepGlassStyle = HazeStyle(
        backgroundColor = Color(0xFF020302),
        tint = HazeTint(Color(0xFF020302).copy(alpha = 0.92f)),
        blurRadius = 20.dp,
        noiseFactor = 0.15f
    )
}

@Composable
fun ReaderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = HiddenForest.TextSecondary
) {
    val activeColor = HiddenForest.Accent
    val finalTint = if (active) activeColor else tint
    
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(if (active) Modifier.background(activeColor.copy(0.15f)) else Modifier)
            .then(
                if (active) Modifier.border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(activeColor.copy(0.4f), Color.Transparent)),
                    shape = RoundedCornerShape(14.dp)
                ) else Modifier
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = finalTint,
            modifier = Modifier.size(22.dp)
        )
    }
}


@Composable
fun ReaderSolidCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ReaderDesign.SurfaceColor)
            .border(1.dp, HiddenForest.Outline.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

/**
 * A glassmorphism card that uses Haze for a premium "Deep Crystal" look.
 */
@Composable
fun ReaderCrystalCard(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val hModifier = if (hazeState != null) {
        modifier.hazeChild(state = hazeState, style = ReaderDesign.GlassStyle)
    } else modifier

    Box(
        modifier = hModifier
            .clip(RoundedCornerShape(24.dp))
            .background(HiddenForest.Surface.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun PremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                Text(
                    text = String.format("%.2f", value),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = HiddenForest.Accent
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = HiddenForest.Accent,
                activeTrackColor = HiddenForest.Accent,
                inactiveTrackColor = HiddenForest.Outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun HighlightColorMenu(
    hazeState: HazeState,
    onColorSelected: (HighlightColor) -> Unit,
    onPlayTts: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onDictionary: (() -> Unit)? = null,
    onIgnoreTts: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 80.dp, start = 20.dp, end = 20.dp) // Elevado para que no tape la bottom bar
                .hazeChild(
                    state = hazeState,
                    style = ReaderDesign.GlassStyle
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF14261D).copy(alpha = 0.95f),
                            Color(0xFF0A140F).copy(alpha = 0.97f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            HiddenForest.Accent.copy(alpha = 0.30f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ELIGE UN COLOR",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = HiddenForest.TextLow,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HighlightColor.entries.forEach { colorEnum ->
                        Column(
                            modifier = Modifier.width(52.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorEnum.toArgb()))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .clickable { onColorSelected(colorEnum) }
                            )
                            Text(
                                text = when (colorEnum) {
                                    HighlightColor.YELLOW -> "Amarillo"
                                    HighlightColor.GREEN -> "Verde"
                                    HighlightColor.BLUE -> "Azul"
                                    HighlightColor.PINK -> "Rosa"
                                    HighlightColor.RED -> "Rojo"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = HiddenForest.TextLow,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                if (onPlayTts != null || onSearch != null || onTranslate != null || onDictionary != null || onIgnoreTts != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = HiddenForest.OutlineHi.copy(0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onPlayTts != null) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlayTts() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Narrar"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Narrar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        
                        if (onSearch != null) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSearch() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Buscar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (onTranslate != null) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTranslate() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Traducir"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Traducir",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (onDictionary != null) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onDictionary() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Diccionario"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Definir",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (onIgnoreTts != null) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onIgnoreTts() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Ignorar en TTS"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ignorar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
    }
}
}

@Composable
private fun ActionIconCircle(
    imageVector: ImageVector,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HiddenForest.Accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = HiddenForest.Accent,
            modifier = Modifier.size(20.dp)
        )
    }
}
