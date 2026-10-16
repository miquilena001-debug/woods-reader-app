package com.example.woodsreader.core.pipeline

import kotlinx.serialization.Serializable

/**
 * Razones por las cuales un bloque de texto es clasificado como ruido en la lectura.
 */
@Serializable
enum class NoiseReason {
    PAGE_NUMBER,
    RUNNING_HEADER,
    RUNNING_FOOTER,
    URL_OR_CONTACT,
    LEGAL_COPYRIGHT,
    ISOLATED_ORNAMENT,
    TRANSLATOR_SPAM
}

/**
 * Jerarquía semántica de bloques de lectura.
 * Utilizada unificadamente para renderizado visual en Vista Libro y narración TTS.
 */
@Serializable
sealed class ReadingBlock {
    abstract val id: String
    abstract val pageIndex: Int
    abstract val orderIndex: Int

    @Serializable
    data class Paragraph(
        override val id: String,
        val text: String,
        override val pageIndex: Int,
        override val orderIndex: Int
    ) : ReadingBlock()

    @Serializable
    data class Heading(
        override val id: String,
        val text: String,
        val level: Int, // 1 = H1, 2 = H2, etc.
        override val pageIndex: Int,
        override val orderIndex: Int
    ) : ReadingBlock()

    @Serializable
    data class Image(
        override val id: String,
        val caption: String? = null,
        val altText: String? = null,
        val imageUri: String? = null,
        override val pageIndex: Int,
        override val orderIndex: Int
    ) : ReadingBlock()

    @Serializable
    data class Noise(
        override val id: String,
        val text: String,
        val reason: NoiseReason,
        override val pageIndex: Int,
        override val orderIndex: Int
    ) : ReadingBlock()
}

/**
 * Bloque sin procesar extraído de una fuente (EPUB o PDF).
 */
data class RawDocumentBlock(
    val id: String,
    val text: String,
    val isImage: Boolean = false,
    val imageCaption: String? = null,
    val imageUri: String? = null,
    val pageIndex: Int,
    val orderIndex: Int,
    val isFirstLineOfPage: Boolean = false,
    val isLastLineOfPage: Boolean = false,
    val fontSize: Float = 12f
)

/**
 * Unidad procesada lista para ser narrada por TTS (voz neuronal).
 */
data class TtsNarrationUnit(
    val id: String,
    val blockId: String,
    val textToSpeak: String,
    val pageIndex: Int,
    val isAnnouncement: Boolean = false
)

/**
 * Métricas de procesamiento del pipeline.
 */
data class PipelineMetrics(
    val totalRawBlocks: Int,
    val validContentBlocks: Int,
    val filteredNoiseBlocks: Int,
    val imagesFound: Int,
    val wordsProcessed: Long
)
