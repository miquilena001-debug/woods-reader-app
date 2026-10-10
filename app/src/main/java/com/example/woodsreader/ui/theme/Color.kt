package com.example.woodsreader.ui.theme

import androidx.compose.ui.graphics.Color

// ── Paleta HiddenForest (Bosque Oscuro — Mockup Premium) ──────────────────────
object HiddenForest {
    val Background      = Color(0xFF080B08)   // bg: #080B08
    val Surface         = Color(0xFF111811)   // bgPanel: #111811
    val SurfaceVariant  = Color(0xFF0E130E)   // bgCard: #0E130E
    val Accent          = Color(0xFF4CAF72)   // green: #4CAF72
    val AccentDim       = Color(0xFF2E6644)   // greenDim: #2E6644
    val AccentGlow      = Color(0x334CAF72)   // greenGlow: #4CAF7233
    val AccentContainer = Color(0xFF1A3321)
    val OnAccent        = Color(0xFF080B08)
    val Text            = Color(0xFFD4E8D4)   // textHi: #D4E8D4
    val TextSecondary   = Color(0xFF8BAE8B)   // textMid: #8BAE8B
    // << CHANGE: Elevado contraste de #4A6A4A a #7AAE7A para legibilidad en modo noche (WCAG AA)
    val TextLow         = Color(0xFF7AAE7A)
    val Outline         = Color(0xFF1C2A1C)   // border: #1C2A1C
    val OutlineHi       = Color(0xFF2E4A2E)   // borderHi: #2E4A2E
    val Amber           = Color(0xFFC8A84B)   // amber: #C8A84B
}

// ── Tema Day (lectura diurna) ─────────────────────────────────────────────────
object Day {
    val Background      = Color(0xFFF7F4EF)
    val Surface         = Color(0xFFFFFFFF)
    val SurfaceVariant  = Color(0xFFEEEBE6)
    val Accent          = Color(0xFF2E7D52)
    val AccentContainer = Color(0xFFB8DEC9)
    val OnAccent        = Color(0xFFFFFFFF)
    val Text            = Color(0xFF1A1A1A)
    val TextSecondary   = Color(0xFF555555)
    val Outline         = Color(0xFFCCCCCC)
}

// ── Tema Sepia (reducción de fatiga visual) ────────────────────────────────────
object Sepia {
    val Background      = Color(0xFFF5EDD6)
    val Surface         = Color(0xFFFBF5E4)
    val SurfaceVariant  = Color(0xFFECE2C8)
    val Accent          = Color(0xFF8B5E3C)
    val AccentContainer = Color(0xFFD4B896)
    val OnAccent        = Color(0xFFFFF8F0)
    val Text            = Color(0xFF3B2B1A)
    val TextSecondary   = Color(0xFF7A5C3E)
    val Outline         = Color(0xFFD4C4A0)
}

// ── Tema AMOLED (negro puro para pantallas OLED) ──────────────────────────────
object AMOLED {
    val Background      = Color(0xFF000000)   // Negro puro — ahorra batería en OLED
    val Surface         = Color(0xFF0D0D0D)
    val SurfaceVariant  = Color(0xFF1A1A1A)
    val Accent          = Color(0xFF4CAF72)
    val AccentContainer = Color(0xFF0F2A1A)
    val OnAccent        = Color(0xFF000000)
    val Text            = Color(0xFFE0E0E0)
    val TextSecondary   = Color(0xFF9E9E9E)
    val Outline         = Color(0xFF2A2A2A)
}

// ── Tema Sunset (tonos cálidos de atardecer) ──────────────────────────────────
object Sunset {
    val Background      = Color(0xFF1A0D08)
    val Surface         = Color(0xFF231209)
    val SurfaceVariant  = Color(0xFF2E1A10)
    val Accent          = Color(0xFFE8874A)
    val AccentContainer = Color(0xFF3D1F0A)
    val OnAccent        = Color(0xFF1A0500)
    val Text            = Color(0xFFEDD5C0)
    val TextSecondary   = Color(0xFFB8896E)
    val Outline         = Color(0xFF4A2E1A)
}

// ── Colores semánticos compartidos entre temas ────────────────────────────────
object Semantic {
    // Highlights para Temas Claros (Opacidad 80% - 0xCC)
    val HighlightYellowLight = Color(0xCCFFF176)
    val HighlightGreenLight  = Color(0xCCA5D6A7)
    val HighlightBlueLight   = Color(0xCC90CAF9)
    val HighlightPinkLight   = Color(0xCCF48FB1)
    val HighlightRedLight    = Color(0xCCEF9A9A)
    
    // Highlights para Temas Oscuros/OLED (Opacidad 25% - 0x40)
    val HighlightYellowDark  = Color(0x40FFF176)
    val HighlightGreenDark   = Color(0x40A5D6A7)
    val HighlightBlueDark    = Color(0x4090CAF9)
    val HighlightPinkDark    = Color(0x40F48FB1)
    val HighlightRedDark     = Color(0x40EF9A9A)
    // Estados
    val Error           = Color(0xFFCF6679)
    val Success         = Color(0xFF4CAF72)
    val Warning         = Color(0xFFFFB74D)
}