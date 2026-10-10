package com.example.woodsreader.model

import kotlinx.serialization.Serializable

/**
 * Estilos de animación para el paso de página en modo [ScrollMode.PAGINATED].
 */
@Serializable
enum class PageTurnStyle {
    /**
     * Animación 3D hiperrealista estilo libro físico:
     * - Curvatura y rotación en perspectiva sobre el lomo (eje Y).
     * - Iluminación dinámica de papel y sombras de lomo.
     * - Cara posterior de la hoja con textura y tono de papel según el tema.
     * - Sombra proyectada suave sobre la página entrante.
     */
    BOOK_FLIP,

    /**
     * Desplazamiento lateral suave (slide horizontal) con sombra de borde.
     */
    SLIDE,

    /**
     * Salto instantáneo de página sin animación para máxima rapidez.
     */
    NONE
}
