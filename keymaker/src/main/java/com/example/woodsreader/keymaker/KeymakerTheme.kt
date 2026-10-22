package com.example.woodsreader.keymaker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WoodsDarkBackground = Color(0xFF0C100D)
val WoodsDarkSurface = Color(0xFF131A15)
val WoodsDarkSurfaceVariant = Color(0xFF1B261E)
val WoodsDarkSurfaceCard = Color(0xFF162118)

val WoodsEmerald = Color(0xFF4CAF50)
val WoodsEmeraldDark = Color(0xFF2E7D32)
val WoodsGold = Color(0xFFFFB300)
val WoodsGoldBright = Color(0xFFFFD54F)
val WoodsGoldContainer = Color(0xFF332600)

val WoodsTextPrimary = Color(0xFFECEFF1)
val WoodsTextSecondary = Color(0xFF90A4AE)
val WoodsTextMuted = Color(0xFF607D8B)

private val DarkColorScheme = darkColorScheme(
    primary = WoodsGold,
    onPrimary = Color.Black,
    primaryContainer = WoodsGoldContainer,
    onPrimaryContainer = WoodsGoldBright,
    secondary = WoodsEmerald,
    onSecondary = Color.Black,
    background = WoodsDarkBackground,
    onBackground = WoodsTextPrimary,
    surface = WoodsDarkSurface,
    onSurface = WoodsTextPrimary,
    surfaceVariant = WoodsDarkSurfaceVariant,
    onSurfaceVariant = WoodsTextSecondary,
    error = Color(0xFFEF5350),
    onError = Color.Black
)

@Composable
fun WoodsKeymakerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
