package com.example.woodsreader.ui.reader

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Book
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
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
    onColorSelected: (HighlightColor) -> Unit,
    onSaveCharacter: ((name: String, role: String, note: String) -> Unit)? = null,
    onPlayTts: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onDictionary: (() -> Unit)? = null,
    onIgnoreTts: (() -> Unit)? = null,
    onErrorOpenBrowser: (() -> Unit)? = null,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = if (isShortText && onSaveCharacter != null) Arrangement.SpaceBetween else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ELIGE UN COLOR",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = HiddenForest.TextLow
                    )

                    if (isShortText && onSaveCharacter != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HiddenForest.Accent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showCharacterDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Guardar Personaje",
                                    tint = HiddenForest.Accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "+ Guardar Personaje",
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
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(HiddenForest.Accent.copy(alpha = 0.15f))
                                                    .clickable {
                                                        clipboardManager.setText(AnnotatedString(state.primaryResult))
                                                        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copiar",
                                                    tint = HiddenForest.Accent,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Copiar",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = HiddenForest.Accent
                                                )
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
