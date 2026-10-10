package com.example.woodsreader.data.model

import kotlinx.serialization.Serializable

// ── Enums del dominio — definidos aquí para que toda la app los comparta ──────
// NO tienen dependencias de Android — son puro dominio de negocio

@Serializable
enum class BookFormat {
    EPUB, PDF, MOBI, AZW, AZW3, CBZ, CBR, CBT,
    DOCX, ODT, RTF, MD, TXT, FB2, LIT, UNKNOWN
}

@Serializable
enum class ReadingStatus {
    NOT_STARTED, IN_PROGRESS, COMPLETED, ABANDONED
}

@Serializable
enum class HighlightColor(val hex: String) {
    YELLOW("#FFF176"),
    GREEN("#A5D6A7"),
    BLUE("#90CAF9"),
    PINK("#F48FB1"),
    RED("#EF9A9A")
}

/**
 * Convierte el hex string (#RRGGBB) a un Int compatible con Android.
 */
fun HighlightColor.toArgb(): Int {
    return android.graphics.Color.parseColor(this.hex)
}

@Serializable
enum class ReadingMode {
    MANUAL, TTS
}
