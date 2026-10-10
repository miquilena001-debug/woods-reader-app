package com.example.woodsreader.model

import kotlinx.serialization.Serializable

@Serializable
enum class TtsHighlightStyle {
    BACKGROUND_YELLOW,    // Fondo amarillo semi-transparente (actual)
    BACKGROUND_ACCENT,    // Fondo con color accent (verde bosque)
    UNDERLINE,            // Subrayado accent
    BACKGROUND_BLUE,      // Fondo azul
    BACKGROUND_ORANGE,    // Fondo naranja
    NONE                  // Sin resaltado
}
