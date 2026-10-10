package com.example.woodsreader.core.pipeline

/**
 * Gestor de anuncios y presentación de imágenes/figuras en libros (EPUB y PDF).
 * Permite tanto la integración visual como la verbalización inteligente en TTS.
 */
class ImageAnnouncer {

    /**
     * Construye el texto exacto a narrar por el motor de TTS para una imagen.
     * Retorna null si la locución de imágenes está desactivada por el usuario.
     */
    fun buildNarrationText(image: ReadingBlock.Image, announceImages: Boolean): String? {
        if (!announceImages) return null

        val cleanCaption = image.caption?.trim()?.takeIf { it.isNotBlank() }
        val cleanAlt = image.altText?.trim()?.takeIf { it.isNotBlank() }

        return when {
            cleanCaption != null -> "Imagen: $cleanCaption"
            cleanAlt != null -> "Imagen: $cleanAlt"
            else -> "Ilustración"
        }
    }

    /**
     * Construye la etiqueta visual para mostrar en la interfaz de lectura (Vista Libro).
     */
    fun buildDisplayLabel(image: ReadingBlock.Image): String {
        val cleanCaption = image.caption?.trim()?.takeIf { it.isNotBlank() }
        val cleanAlt = image.altText?.trim()?.takeIf { it.isNotBlank() }

        return cleanCaption ?: cleanAlt ?: "Figura sin descripción"
    }

    /**
     * Determina si un bloque de imagen tiene descripción textual disponible.
     */
    fun hasDescriptiveText(image: ReadingBlock.Image): Boolean {
        return !image.caption.isNullOrBlank() || !image.altText.isNullOrBlank()
    }
}
