package com.example.woodsreader.model

import kotlinx.serialization.Serializable

/**
 * Style for the TTS control UI while reading.
 *  - BAR: Compact docked bar at the bottom (Moon+ Reader style — default)
 *  - FLOATING: Floating circular button (classic Woods Reader style)
 *  - PANEL: Full-screen bottom sheet panel (immersive visualizer)
 */
@Serializable
enum class TtsUiStyle {
    BAR,       // Barra compacta inferior (estilo Moon+ Reader)
    FLOATING,  // Botón flotante circular
    PANEL      // Panel completo con visualizador
}
