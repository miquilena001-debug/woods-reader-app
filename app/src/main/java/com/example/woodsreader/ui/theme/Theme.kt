package com.example.woodsreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Enum de temas disponibles ─────────────────────────────────────────────────
import com.example.woodsreader.model.ReaderTheme

val LocalReaderTheme = staticCompositionLocalOf { ReaderTheme.HIDDEN_FOREST }

// << CHANGE: Definición de tokens de resaltado personalizados fuera de ColorScheme
data class HighlightColors(
    val yellow: Color,
    val green: Color,
    val blue: Color,
    val pink: Color,
    val red: Color
)

private val HighlightColorsLight = HighlightColors(
    yellow = Semantic.HighlightYellowLight,
    green = Semantic.HighlightGreenLight,
    blue = Semantic.HighlightBlueLight,
    pink = Semantic.HighlightPinkLight,
    red = Semantic.HighlightRedLight
)

private val HighlightColorsDark = HighlightColors(
    yellow = Semantic.HighlightYellowDark,
    green = Semantic.HighlightGreenDark,
    blue = Semantic.HighlightBlueDark,
    pink = Semantic.HighlightPinkDark,
    red = Semantic.HighlightRedDark
)

val LocalHighlightColors = staticCompositionLocalOf { HighlightColorsLight }

// ── Color schemes de Material 3 por tema ──────────────────────────────────────

private val HiddenForestColorScheme = darkColorScheme(
    primary            = HiddenForest.Accent,
    onPrimary          = HiddenForest.OnAccent,
    primaryContainer   = HiddenForest.AccentContainer,
    // << CHANGE: Expansión para soporte completo de componentes M3
    secondary          = Color(0xFF4A8A5A),
    onSecondary        = Color.White,
    tertiary           = Color(0xFF8B6914),
    onTertiary         = Color.White,
    background         = HiddenForest.Background,
    surface            = HiddenForest.Surface,
    surfaceVariant     = HiddenForest.SurfaceVariant,
    surfaceContainer   = Color(0xFF0C120C),
    onBackground       = HiddenForest.Text,
    onSurface          = HiddenForest.Text,
    onSurfaceVariant   = HiddenForest.TextSecondary,
    outline            = HiddenForest.Outline,
    outlineVariant     = HiddenForest.OutlineHi,
    error              = Semantic.Error
)

private val DayColorScheme = lightColorScheme(
    primary            = Day.Accent,
    onPrimary          = Day.OnAccent,
    primaryContainer   = Day.AccentContainer,
    // << CHANGE: Expansión para soporte completo de componentes M3
    secondary          = Color(0xFF6DB87A),
    onSecondary        = Color.White,
    tertiary           = Color(0xFFC4A882),
    onTertiary         = Color.Black,
    background         = Day.Background,
    surface            = Day.Surface,
    surfaceVariant     = Day.SurfaceVariant,
    surfaceContainer   = Color(0xFFE9E5E0),
    onBackground       = Day.Text,
    onSurface          = Day.Text,
    onSurfaceVariant   = Day.TextSecondary,
    outline            = Day.Outline,
    error              = Semantic.Error
)

private val SepiaColorScheme = lightColorScheme(
    primary            = Sepia.Accent,
    onPrimary          = Sepia.OnAccent,
    primaryContainer   = Sepia.AccentContainer,
    // << CHANGE: Expansión para soporte completo de componentes M3
    secondary          = Color(0xFF8B5E3C),
    onSecondary        = Color.White,
    tertiary           = Color(0xFFD4B896),
    onTertiary         = Color.Black,
    background         = Sepia.Background,
    surface            = Sepia.Surface,
    surfaceVariant     = Sepia.SurfaceVariant,
    surfaceContainer   = Color(0xFFEAE0BD),
    onBackground       = Sepia.Text,
    onSurface          = Sepia.Text,
    onSurfaceVariant   = Sepia.TextSecondary,
    outline            = Sepia.Outline,
    error              = Semantic.Error
)

private val AmoledColorScheme = darkColorScheme(
    primary            = AMOLED.Accent,
    onPrimary          = AMOLED.OnAccent,
    primaryContainer   = AMOLED.AccentContainer,
    // << CHANGE: Expansión para soporte completo de componentes M3
    secondary          = Color(0xFF2E6644),
    onSecondary        = Color.White,
    tertiary           = Color(0xFF5D4037),
    onTertiary         = Color.White,
    background         = AMOLED.Background,
    surface            = AMOLED.Surface,
    surfaceVariant     = AMOLED.SurfaceVariant,
    surfaceContainer   = Color(0xFF0A0A0A),
    onBackground       = AMOLED.Text,
    onSurface          = AMOLED.Text,
    onSurfaceVariant   = AMOLED.TextSecondary,
    outline            = AMOLED.Outline,
    error              = Semantic.Error
)

private val SunsetColorScheme = darkColorScheme(
    primary            = Sunset.Accent,
    onPrimary          = Sunset.OnAccent,
    primaryContainer   = Sunset.AccentContainer,
    // << CHANGE: Expansión para soporte completo de componentes M3
    secondary          = Color(0xFFE8874A),
    onSecondary        = Color.White,
    tertiary           = Color(0xFF3D1F0A),
    onTertiary         = Color.White,
    background         = Sunset.Background,
    surface            = Sunset.Surface,
    surfaceVariant     = Sunset.SurfaceVariant,
    surfaceContainer   = Color(0xFF160B06),
    onBackground       = Sunset.Text,
    onSurface          = Sunset.Text,
    onSurfaceVariant   = Sunset.TextSecondary,
    outline            = Sunset.Outline,
    error              = Semantic.Error
)

/**
 * Tema principal de la app.
 *
 * @param readerTheme Tema seleccionado por el usuario (se leerá de DataStore en Fase 1D)
 */
@Composable
fun WoodsReaderTheme(
    readerTheme: ReaderTheme = ReaderTheme.HIDDEN_FOREST,
    content: @Composable () -> Unit
) {
    val highlightColors = when (readerTheme) {
        ReaderTheme.DAY, ReaderTheme.SEPIA -> HighlightColorsLight
        else -> HighlightColorsDark
    }

    val colorScheme = when (readerTheme) {
        ReaderTheme.HIDDEN_FOREST -> HiddenForestColorScheme
        ReaderTheme.DAY           -> DayColorScheme
        ReaderTheme.SEPIA         -> SepiaColorScheme
        ReaderTheme.AMOLED        -> AmoledColorScheme
        ReaderTheme.SUNSET        -> SunsetColorScheme
    }

    CompositionLocalProvider(
        LocalReaderTheme provides readerTheme,
        // << CHANGE: Provisión de los colores de resaltado al árbol de UI
        LocalHighlightColors provides highlightColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = WoodsReaderTypography,
            content     = content
        )
    }
}