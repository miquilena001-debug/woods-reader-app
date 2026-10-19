package com.example.woodsreader.ui.reader

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Book
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.core.managers.LookupResult
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
    selectedText: String = "",
    lookupState: LookupResult = LookupResult.Idle,
    selectionRect: android.graphics.RectF? = null,
    onColorSelected: (HighlightColor) -> Unit,
    onSaveCharacter: ((name: String, role: String, note: String) -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExpandToSentence: (() -> Unit)? = null,
    onPlayTts: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onDictionary: (() -> Unit)? = null,
    onIgnoreTts: (() -> Unit)? = null,
    onErrorOpenBrowser: (() -> Unit)? = null,
    onSpeakSnippet: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val words = remember(selectedText) {
        selectedText.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    }
    val isShortText = words.isNotEmpty() && words.size in 1..5
    var showCharacterDialog by remember { mutableStateOf(false) }

    if (showCharacterDialog) {
        SaveCharacterDialog(
            initialName = selectedText.trim(),
            onDismiss = { showCharacterDialog = false },
            onConfirm = { name, role, note ->
                showCharacterDialog = false
                onSaveCharacter?.invoke(name, role, note)
            }
        )
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    Popup(
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() }
        ) {
        val positionModifier = if (selectionRect != null) {
            val isTopHalf = selectionRect.centerY() < screenHeightPx / 2f
            if (isTopHalf) {
                val topPaddingDp = with(density) { selectionRect.bottom.coerceAtLeast(0f).toDp() } + 48.dp
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = topPaddingDp.coerceIn(16.dp, (configuration.screenHeightDp.dp - 320.dp).coerceAtLeast(16.dp)),
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    )
            } else {
                val bottomPaddingDp = with(density) { (screenHeightPx - selectionRect.top.coerceAtLeast(0f)).toDp() } + 48.dp
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = bottomPaddingDp.coerceIn(16.dp, (configuration.screenHeightDp.dp - 320.dp).coerceAtLeast(16.dp)),
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp
                    )
            }
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 20.dp, end = 20.dp)
        }

        Box(
            modifier = positionModifier
                .hazeChild(
                    state = hazeState,
                    style = ReaderDesign.GlassStyle
                )
                .clip(RoundedCornerShape(24.dp))
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
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* Evita que clicks dentro del panel cierren el popup */ }
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ELIGE UN COLOR",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = HiddenForest.TextLow
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onExpandToSentence != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = HiddenForest.Accent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onExpandToSentence() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatQuote,
                                        contentDescription = "Ampliar a oración",
                                        tint = HiddenForest.Accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Ampliar a oración",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = HiddenForest.Accent
                                    )
                                }
                            }
                        }

                        if (isShortText && onSaveCharacter != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = HiddenForest.Accent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { showCharacterDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Guardar Personaje",
                                        tint = HiddenForest.Accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "+ Personaje",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = HiddenForest.Accent
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(bottom = 6.dp),
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
                
                if (onCopy != null || onShare != null || onPlayTts != null || onTranslate != null || onDictionary != null || onSearch != null || onIgnoreTts != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = HiddenForest.OutlineHi.copy(0.4f))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onCopy != null) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onCopy() }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Copiar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (onShare != null) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onShare() }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Compartir"
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Compartir",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.Text,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (onPlayTts != null) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlayTts() }
                                    .padding(vertical = 4.dp),
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

                        if (onTranslate != null) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTranslate() }
                                    .padding(vertical = 4.dp),
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
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onDictionary() }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ActionIconCircle(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Definir"
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

                        if (onSearch != null) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSearch() }
                                    .padding(vertical = 4.dp),
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

                        if (onIgnoreTts != null) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onIgnoreTts() }
                                    .padding(vertical = 4.dp),
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

                AnimatedVisibility(
                    visible = lookupState !is LookupResult.Idle,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val context = LocalContext.current
                    val clipboardManager = LocalClipboardManager.current

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0E1F16))
                                .border(
                                    width = 1.dp,
                                    color = HiddenForest.Accent.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            when (val state = lookupState) {
                                is LookupResult.Loading -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = HiddenForest.Accent
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = state.modeTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = HiddenForest.TextSecondary
                                        )
                                    }
                                }
                                is LookupResult.Success -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = state.modeTitle,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = HiddenForest.Accent
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (onSpeakSnippet != null) {
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(HiddenForest.Accent.copy(alpha = 0.15f))
                                                            .clickable { onSpeakSnippet(state.primaryResult) }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                            contentDescription = "Escuchar",
                                                            tint = HiddenForest.Accent,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "🔊",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                                        )
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(HiddenForest.Accent.copy(alpha = 0.15f))
                                                        .clickable {
                                                            clipboardManager.setText(AnnotatedString(state.primaryResult))
                                                            Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copiar",
                                                        tint = HiddenForest.Accent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Copiar",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = HiddenForest.Accent,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = state.primaryResult,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = Color.White
                                        )

                                        if (state.secondaryDetail != null) {
                                            Text(
                                                text = state.secondaryDetail,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = HiddenForest.TextSecondary
                                            )
                                        }
                                    }
                                }
                                is LookupResult.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFFEF5350),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = state.message,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(HiddenForest.Accent.copy(alpha = 0.15f))
                                                    .clickable {
                                                        if (onErrorOpenBrowser != null) {
                                                            onErrorOpenBrowser()
                                                        } else {
                                                            val url = "https://translate.google.com/?sl=auto&tl=es&text=" + Uri.encode(state.modeTitle)
                                                            try {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                            } catch (_: Exception) {}
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInBrowser,
                                                    contentDescription = "Abrir en navegador",
                                                    tint = HiddenForest.Accent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Abrir en navegador",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = HiddenForest.Accent
                                                )
                                            }
                                        }
                                    }
                                }
                                LookupResult.Idle -> {}
                            }
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

@Composable
fun SaveCharacterDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val roles = listOf("Protagonista", "Aliado", "Enemigo", "Clan / Lugar", "Artefacto")
    var selectedRole by remember { mutableStateOf(roles[0]) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0E1A13))
                .border(1.dp, HiddenForest.Accent.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = HiddenForest.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Guardar Personaje o Término",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre / Término", color = HiddenForest.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.OutlineHi,
                        cursorColor = HiddenForest.Accent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ROL / CATEGORÍA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HiddenForest.TextLow
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roles.forEach { role ->
                            val isSelected = selectedRole == role
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) HiddenForest.Accent else Color(0xFF162B20),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HiddenForest.Accent else HiddenForest.OutlineHi.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.clickable { selectedRole = role }
                            ) {
                                Text(
                                    text = role,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color(0xFF080B08) else Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota o descripción (opcional)", color = HiddenForest.TextSecondary) },
                    placeholder = { Text("Ej. Aliado en el capítulo 4...", color = HiddenForest.TextLow.copy(alpha = 0.6f)) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = HiddenForest.Accent,
                        unfocusedBorderColor = HiddenForest.OutlineHi,
                        cursorColor = HiddenForest.Accent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = HiddenForest.TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name.trim(), selectedRole, note.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HiddenForest.Accent),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank()
                    ) {
                        Text(
                            text = "Guardar",
                            color = Color(0xFF080B08),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

enum class QuoteCardTheme(
    val title: String,
    val previewBg: Color,
    val textColor: Color,
    val authorColor: Color,
    val quoteColor: Color,
    val borderColor: Color,
    val isDark: Boolean
) {
    BOSQUE_ESMERALDA(
        title = "Bosque Esmeralda",
        previewBg = Color(0xFF0D2818),
        textColor = Color(0xFFE8D8B8),
        authorColor = Color(0xFFB5C9BC),
        quoteColor = Color(0xFF4CAF7D),
        borderColor = Color(0xFF2A593D),
        isDark = true
    ),
    PERGAMINO_CLASICO(
        title = "Pergamino Clásico",
        previewBg = Color(0xFFF7F3E9),
        textColor = Color(0xFF2B231D),
        authorColor = Color(0xFF6E5D4F),
        quoteColor = Color(0xFF8B5A2B),
        borderColor = Color(0xFFDCD2BE),
        isDark = false
    ),
    NOCHE_OLED(
        title = "Noche OLED",
        previewBg = Color(0xFF000000),
        textColor = Color(0xFFFFFFFF),
        authorColor = Color(0xFF9E9E9E),
        quoteColor = Color(0xFF00E676),
        borderColor = Color(0xFF333333),
        isDark = true
    )
}

private fun renderThemedQuoteBitmap(
    theme: QuoteCardTheme,
    quoteText: String,
    bookTitle: String,
    bookAuthor: String,
    chapterTitle: String
): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    when (theme) {
        QuoteCardTheme.BOSQUE_ESMERALDA -> {
            bgPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF0D2818.toInt(), 0xFF051A0E.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }
        QuoteCardTheme.PERGAMINO_CLASICO -> {
            bgPaint.color = 0xFFF7F3E9.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }
        QuoteCardTheme.NOCHE_OLED -> {
            bgPaint.color = 0xFF000000.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }
    }

    val cardRect = RectF(76f, 110f, width - 76f, height - 110f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFF13281D.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFFFBF8F0.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFF0A0A0A.toInt()
        }
    }
    canvas.drawRoundRect(cardRect, 52f, 52f, cardPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFF2A593D.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFFDCD2BE.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFF333333.toInt()
        }
    }
    canvas.drawRoundRect(cardRect, 52f, 52f, borderPaint)

    val quoteMarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFF4CAF7D.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFF8B5A2B.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFF00E676.toInt()
        }
        alpha = if (theme == QuoteCardTheme.PERGAMINO_CLASICO) 140 else 180
        textSize = 170f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    canvas.drawText("“", 136f, 290f, quoteMarkPaint)

    val cleanQuote = if (quoteText.length > 450) quoteText.take(447) + "..." else quoteText
    val quoteTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFFE8D8B8.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFF2B231D.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFFFFFFFF.toInt()
        }
        textSize = when {
            cleanQuote.length < 120 -> 50f
            cleanQuote.length < 240 -> 42f
            else -> 35f
        }
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    val textWidth = width - 296
    val staticLayout = StaticLayout.Builder
        .obtain(cleanQuote, 0, cleanQuote.length, quoteTextPaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(12f, 1.15f)
        .build()

    canvas.save()
    val textY = ((height - staticLayout.height) / 2f).coerceAtLeast(300f)
    canvas.translate(148f, textY)
    staticLayout.draw(canvas)
    canvas.restore()

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFF4CAF7D.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFF8B5A2B.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFF00E676.toInt()
        }
        strokeWidth = 3f
    }
    canvas.drawLine(148f, height - 320f, 268f, height - 320f, linePaint)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFFFFFFFF.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFF1D1713.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFFFFFFFF.toInt()
        }
        textSize = 34f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val clippedTitle = if (bookTitle.length > 40) bookTitle.take(37) + "..." else bookTitle
    canvas.drawText(clippedTitle, 148f, height - 265f, titlePaint)

    val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFFB5C9BC.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFF6E5D4F.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFF9E9E9E.toInt()
        }
        textSize = 26f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val subLine = if (chapterTitle.isNotBlank()) "$bookAuthor · $chapterTitle" else bookAuthor
    val clippedSub = if (subLine.length > 50) subLine.take(47) + "..." else subLine
    canvas.drawText(clippedSub, 148f, height - 220f, authorPaint)

    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (theme) {
            QuoteCardTheme.BOSQUE_ESMERALDA -> 0xFF4CAF7D.toInt()
            QuoteCardTheme.PERGAMINO_CLASICO -> 0xFF8B5A2B.toInt()
            QuoteCardTheme.NOCHE_OLED -> 0xFF00E676.toInt()
        }
        textSize = 22f
        letterSpacing = 0.15f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText("🌿 Woods Reader", 148f, height - 160f, brandPaint)

    return bitmap
}

@Composable
fun QuoteShareDialog(
    quoteText: String,
    bookTitle: String,
    bookAuthor: String,
    chapterTitle: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTheme by remember { mutableStateOf(QuoteCardTheme.BOSQUE_ESMERALDA) }
    val cleanQuote = quoteText.trim()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14241B),
            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMPARTIR CITA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HiddenForest.Accent
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = HiddenForest.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuoteCardTheme.entries.forEach { theme ->
                        val isSelected = selectedTheme == theme
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) HiddenForest.Accent.copy(alpha = 0.25f) else Color(0xFF0E1A13),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.clickable { selectedTheme = theme }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(theme.previewBg)
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )
                                Text(
                                    text = theme.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) HiddenForest.Accent else HiddenForest.TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = if (selectedTheme == QuoteCardTheme.BOSQUE_ESMERALDA) {
                                Brush.verticalGradient(listOf(Color(0xFF0D2818), Color(0xFF051A0E)))
                            } else {
                                androidx.compose.ui.graphics.SolidColor(selectedTheme.previewBg)
                            }
                        )
                        .border(1.dp, selectedTheme.borderColor, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "“",
                            fontSize = 38.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.quoteColor,
                            lineHeight = 28.sp
                        )

                        Text(
                            text = cleanQuote,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontFamily = FontFamily.Serif
                            ),
                            color = selectedTheme.textColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(2.dp)
                                .background(selectedTheme.quoteColor)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = bookTitle.ifBlank { "Libro sin título" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = if (selectedTheme.isDark) Color.White else Color(0xFF1D1713),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val authorAndChapter = buildString {
                            if (bookAuthor.isNotBlank()) append(bookAuthor)
                            if (chapterTitle.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(chapterTitle)
                            }
                        }
                        if (authorAndChapter.isNotBlank()) {
                            Text(
                                text = authorAndChapter,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = selectedTheme.authorColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "🌿 Woods Reader",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = selectedTheme.quoteColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val bitmap = renderThemedQuoteBitmap(
                                theme = selectedTheme,
                                quoteText = cleanQuote,
                                bookTitle = bookTitle,
                                bookAuthor = bookAuthor.ifBlank { "Autor desconocido" },
                                chapterTitle = chapterTitle
                            )
                            val resolver = context.contentResolver
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "WoodsQuote_${System.currentTimeMillis()}.png")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WoodsReader")
                                }
                            }
                            var imageUri: Uri? = null
                            try {
                                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                if (imageUri != null) {
                                    resolver.openOutputStream(imageUri)?.use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, imageUri)
                                        putExtra(Intent.EXTRA_TEXT, "«${cleanQuote.take(180)}» — $bookTitle")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Compartir cita de Woods Reader").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                imageUri?.let { resolver.delete(it, null, null) }
                                Toast.makeText(context, "Error al compartir imagen", Toast.LENGTH_SHORT).show()
                            } finally {
                                bitmap.recycle()
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HiddenForest.Accent,
                            contentColor = Color(0xFF08140C)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "🖼️ Imagen",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val authorSuffix = if (bookAuthor.isNotBlank()) ", $bookAuthor" else ""
                            val shareText = "«$cleanQuote»\n\n— $bookTitle$authorSuffix\nLeído en Woods Reader 🌿"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Cita de $bookTitle")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Compartir cita como texto").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                            onDismiss()
                        },
                        border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.Accent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "📝 Texto",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
