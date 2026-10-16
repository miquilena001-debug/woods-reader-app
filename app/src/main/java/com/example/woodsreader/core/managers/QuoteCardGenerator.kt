package com.example.woodsreader.core.managers

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QuoteCardGenerator {

    suspend fun shareQuoteCard(
        context: Context,
        quoteText: String,
        bookTitle: String,
        bookAuthor: String,
        chapterTitle: String = ""
    ) = withContext(Dispatchers.IO) {
        val cleanQuote = quoteText.trim()
        require(cleanQuote.isNotEmpty()) { "No se puede compartir una cita vacía." }

        val bitmap = renderQuoteBitmap(
            quoteText = cleanQuote,
            bookTitle = bookTitle.trim(),
            bookAuthor = bookAuthor.trim().ifBlank { "Autor desconocido" },
            chapterTitle = chapterTitle.trim()
        )
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "WoodsQuote_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WoodsReader")
            }
        }

        var imageUri: android.net.Uri? = null
        try {
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("No se pudo crear la imagen para compartir.")
            val output = resolver.openOutputStream(imageUri)
                ?: error("No se pudo abrir la imagen para escribir.")
            output.use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    "No se pudo guardar la imagen para compartir."
                }
            }
        } catch (exception: Exception) {
            imageUri?.let { resolver.delete(it, null, null) }
            throw exception
        } finally {
            bitmap.recycle()
        }

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, "«${cleanQuote.take(180)}» — ${bookTitle.trim()}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Compartir cita de Woods Reader").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun renderQuoteBitmap(
        quoteText: String,
        bookTitle: String,
        bookAuthor: String,
        chapterTitle: String
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF0D1F16.toInt(), 0xFF07110C.toInt(), 0xFF0E2419.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val cardRect = RectF(76f, 110f, width - 76f, height - 110f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF13281D.toInt()
            alpha = 215
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFF4CAF7D.toInt()
            alpha = 110
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, borderPaint)

        val quoteMarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4CAF7D.toInt()
            alpha = 90
            textSize = 180f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", 136f, 310f, quoteMarkPaint)

        val cleanQuote = if (quoteText.length > 420) quoteText.take(417) + "..." else quoteText
        val quoteTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEBF5EE.toInt()
            textSize = when {
                cleanQuote.length < 140 -> 50f
                cleanQuote.length < 260 -> 42f
                else -> 36f
            }
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val textWidth = width - 296
        val staticLayout = StaticLayout.Builder
            .obtain(cleanQuote, 0, cleanQuote.length, quoteTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1.15f)
            .build()

        canvas.save()
        val textY = ((height - staticLayout.height) / 2f).coerceAtLeast(320f)
        canvas.translate(148f, textY)
        staticLayout.draw(canvas)
        canvas.restore()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4CAF7D.toInt()
            strokeWidth = 4f
        }
        canvas.drawLine(148f, height - 340f, 268f, height - 340f, linePaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val clippedTitle = if (bookTitle.length > 42) bookTitle.take(39) + "..." else bookTitle
        canvas.drawText(clippedTitle, 148f, height - 275f, titlePaint)

        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AB8A7.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val subLine = if (chapterTitle.isNotBlank()) "$bookAuthor · $chapterTitle" else bookAuthor
        val clippedSub = if (subLine.length > 52) subLine.take(49) + "..." else subLine
        canvas.drawText(clippedSub, 148f, height - 225f, authorPaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4CAF7D.toInt()
            textSize = 22f
            letterSpacing = 0.25f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("LEÍDO EN WOODS READER", 148f, height - 155f, brandPaint)

        return bitmap
    }
}
