package com.example.woodsreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import com.example.woodsreader.R

// ── Literata — fuente principal de lectura ────────────────────────────────────
// Descarga en: https://fonts.google.com/specimen/Literata
// Archivo: Literata-VariableFont_opsz,wght.ttf → renombrar a literata_variable.ttf
// y colocar en res/font/

/** 
 * Definición de Literata usando fuente variable. 
 * Si el archivo no existe en res/font, fallará la compilación; 
 * en ese caso, volver a FontFamily.Serif temporalmente.
 */
val LiterataFamily = FontFamily(
    // Fuente Variable Regular (contiene pesos 300-900)
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.Normal,
        style = FontStyle.Normal
    ),
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.Medium,
        style = FontStyle.Normal
    ),
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.Bold,
        style = FontStyle.Normal
    ),
    // Fuente Variable Itálica
    Font(
        resId = R.font.literata_italic_variable,
        weight = FontWeight.Normal,
        style = FontStyle.Italic
    ),
    Font(
        resId = R.font.literata_italic_variable,
        weight = FontWeight.Medium,
        style = FontStyle.Italic
    ),
    Font(
        resId = R.font.literata_italic_variable,
        weight = FontWeight.Bold,
        style = FontStyle.Italic
    )
)

// ── Familias alternativas (para selector de fuente en ajustes) ────────────────
// TODO Fase 1D: cargar la fuente preferida del usuario desde DataStore
// val MerriweatherFamily = FontFamily(Font(R.font.merriweather_regular), ...)
// val EbGaramondFamily   = FontFamily(Font(R.font.eb_garamond_regular), ...)
// val SourceSansFamily   = FontFamily(Font(R.font.source_sans_3_variable), ...)

// ── Typography de Material 3 ──────────────────────────────────────────────────
val WoodsReaderTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 28.sp,
        lineHeight = 34.sp
    ),
    displayMedium = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 24.sp,
        lineHeight = 30.sp
    ),
    displaySmall = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 20.sp,
        lineHeight = 26.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 18.sp,
        lineHeight = 24.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 16.sp,
        lineHeight = 22.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = LiterataFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
)