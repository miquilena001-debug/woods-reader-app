package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.ScrollMode
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

// ── Extension functions ──────────────────────────────────────────────

@OptIn(ExperimentalReadiumApi::class)
fun UserPreferences.toEpubPreferences(): EpubPreferences {
    val fontSizeMultiplier = fontSizeSp / 18.0
    val textAlignEnum = when (textAlign) {
        "justify" -> TextAlign.JUSTIFY
        "left"    -> TextAlign.LEFT
        else      -> TextAlign.START
    }
    val isScroll = scrollMode != ScrollMode.PAGINATED

    return when (theme) {
        ReaderTheme.HIDDEN_FOREST -> EpubPreferences(
            backgroundColor = Color(0xFF0A0D0A.toInt()), textColor = Color(0xFFD4E8D4.toInt()),
            fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = org.readium.r2.navigator.preferences.FontFamily(fontFamily),
            scroll = isScroll, theme = Theme.DARK
        )
        ReaderTheme.DAY -> EpubPreferences(
            theme = Theme.LIGHT, fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = org.readium.r2.navigator.preferences.FontFamily(fontFamily),
            scroll = isScroll
        )
        ReaderTheme.SEPIA -> EpubPreferences(
            theme = Theme.SEPIA, fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = org.readium.r2.navigator.preferences.FontFamily(fontFamily),
            scroll = isScroll
        )
        ReaderTheme.AMOLED -> EpubPreferences(
            backgroundColor = Color(0xFF000000.toInt()), textColor = Color(0xFFE0E0E0.toInt()),
            fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = org.readium.r2.navigator.preferences.FontFamily(fontFamily),
            scroll = isScroll
        )
        ReaderTheme.SUNSET -> EpubPreferences(
            backgroundColor = Color(0xFF1A0D08.toInt()), textColor = Color(0xFFEDD5C0.toInt()),
            fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = org.readium.r2.navigator.preferences.FontFamily(fontFamily),
            scroll = isScroll
        )
    }
}

@Suppress("UnusedReceiverParameter")
fun UserPreferences.toPdfPreferences(): PdfiumPreferences = PdfiumPreferences()

@Suppress("UnusedReceiverParameter")
fun ReaderTheme.toPdfPreferences(): PdfiumPreferences = PdfiumPreferences()

fun HighlightEntity.toLocator(): Locator? = try {
    Locator.fromJSON(org.json.JSONObject(this.locatorJson))
} catch (_: Exception) { null }

fun BookmarkEntity.toLocator(): Locator? = try {
    Locator.fromJSON(org.json.JSONObject(this.locatorJson))
} catch (_: Exception) { null }

data class ChapterInfo(
    val title: String? = null,
    val progress: Float = 0f,
    val timeRemainingMinutes: Int? = null
)
