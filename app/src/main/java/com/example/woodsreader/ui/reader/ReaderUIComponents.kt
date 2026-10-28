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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.example.woodsreader.core.share.QuoteVideoClipExporter
import kotlinx.coroutines.launch
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
