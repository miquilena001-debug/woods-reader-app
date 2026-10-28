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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.woodsreader.core.share.QuoteVideoClipExporter
import com.example.woodsreader.ui.theme.HiddenForest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuoteVideoClipEntryPoint {
    fun quoteVideoClipExporter(): QuoteVideoClipExporter
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
    val scope = rememberCoroutineScope()
    val videoClipExporter = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                QuoteVideoClipEntryPoint::class.java
            ).quoteVideoClipExporter()
        }.getOrNull()
    }

    var isGeneratingVideo by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableFloatStateOf(0f) }

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

                Button(
                    onClick = {
                        if (isGeneratingVideo) return@Button
                        val exporter = videoClipExporter ?: run {
                            Toast.makeText(context, "Exportador de video no disponible", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isGeneratingVideo = true
                        scope.launch {
                            val progressJob = launch {
                                exporter.progress.collect { p ->
                                    videoProgress = p
                                }
                            }
                            try {
                                val result = exporter.exportQuoteVideoMp4(
                                    quoteText = cleanQuote,
                                    bookTitle = bookTitle.ifBlank { "Libro sin título" },
                                    chapterTitle = chapterTitle,
                                    speechSpeed = 1.0f
                                )
                                progressJob.cancel()
                                result.onSuccess { videoFile ->
                                    val videoUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        videoFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, videoUri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "«${cleanQuote.take(180)}» — $bookTitle\nNarrado con Woods Reader 🌿"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Compartir Video-Cita con Voz IA").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                    onDismiss()
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        error.message ?: "Error al generar video-cita",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } finally {
                                progressJob.cancel()
                                isGeneratingVideo = false
                            }
                        }
                    },
                    enabled = !isGeneratingVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HiddenForest.Accent,
                        contentColor = Color(0xFF08140C)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGeneratingVideo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF08140C),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generando Video ${(videoProgress * 100).toInt()}%…",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Text(
                            text = "🎬 Compartir Video-Cita Con Voz IA (.MP4)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
