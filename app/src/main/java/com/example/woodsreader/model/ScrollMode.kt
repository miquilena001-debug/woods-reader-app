package com.example.woodsreader.model

import kotlinx.serialization.Serializable

/**
 * Modo de desplazamiento y navegación del lector de libros.
 *
 * - PAGINATED: Paso de páginas tradicional tipo libro físico (vuelta horizontal con páginas delimitadas).
 * - CONTINUOUS_VERTICAL: Desplazamiento continuo vertical (estilo feed / webtoon).
 * - CONTINUOUS_HORIZONTAL: Desplazamiento continuo horizontal.
 */
@Serializable
enum class ScrollMode {
    PAGINATED,
    CONTINUOUS_VERTICAL,
    CONTINUOUS_HORIZONTAL
}
