package com.example.woodsreader.data.model

import androidx.compose.runtime.Immutable
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.Locator

/**
 * Modelo enriquecido para elementos de texto en modo Reflow de PDF.
 * Incluye semántica para mejor renderizado y experiencia de usuario.
 */
@Immutable
sealed class ReflowItem {
    abstract val pageNumber: Int

    /**
     * Texto simple (compatibilidad con extracción básica)
     */
    data class Text(
        val element: Content.Element,
        override val pageNumber: Int
    ) : ReflowItem()

    /**
     * Encabezado detectado por análisis tipográfico
     */
    data class Header(
        val content: String,
        val level: Int, // 1 = H1, 2 = H2, etc.
        override val pageNumber: Int
    ) : ReflowItem()

    /**
     * Párrafo normal con estilo de cuerpo
     */
    data class Paragraph(
        val content: String,
        override val pageNumber: Int
    ) : ReflowItem()

    /**
     * Número de página detectado (puede ocultarse opcionalmente)
     */
    data class PageNumber(
        val number: String,
        override val pageNumber: Int
    ) : ReflowItem()

    /**
     * Imagen extraída del PDF
     */
    data class Image(
        val imageBytes: ByteArray,
        override val pageNumber: Int
    ) : ReflowItem()

    /**
     * Elemento ignorado (headers/footers repetidos, ruido)
     */
    data class Ignored(
        val content: String,
        val reason: String,
        override val pageNumber: Int
    ) : ReflowItem()
}

/**
 * Estado del modo Reflow para PDFs
 */
@Immutable
data class ReflowState(
    val items: List<ReflowItem> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val isLoading: Boolean = false,
    val currentLoadingPage: Int = 0,
    val error: String? = null
)

/**
 * Elemento de texto simple para compatibilidad con extracción básica
 */
data class SimpleTextElement(
    val locator: Locator
)
