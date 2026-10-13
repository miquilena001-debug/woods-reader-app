package com.example.woodsreader.core.pipeline

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado integral del procesamiento del pipeline de lectura.
 */
data class ProcessedDocument(
    val displayBlocks: List<ReadingBlock>,
    val ttsNarrationUnits: List<TtsNarrationUnit>,
    val metrics: PipelineMetrics
)

/**
 * Pipeline central y unificado de lectura.
 * Procesa bloques sin procesar tanto de EPUB como de PDF, filtra ruido,
 * aplica exclusiones de palabras, da formato tipográfico en español y prepara
 * las unidades de narración neuronal.
 */
@Singleton
class ReadingPipeline @Inject constructor(
    private val noiseClassifier: NoiseClassifier,
    private val imageAnnouncer: ImageAnnouncer,
    private val wordExclusionEngine: WordExclusionEngine,
    private val spanishTextFormatter: SpanishTextFormatter
) {

    /**
     * Procesa una lista de bloques brutos de documento.
     */
    fun process(
        rawBlocks: List<RawDocumentBlock>,
        rules: List<WordExclusionRule> = emptyList(),
        announceImages: Boolean = true,
        stripNoiseFromDisplay: Boolean = true
    ): ProcessedDocument {
        if (rawBlocks.isEmpty()) {
            return ProcessedDocument(
                displayBlocks = emptyList(),
                ttsNarrationUnits = emptyList(),
                metrics = PipelineMetrics(0, 0, 0, 0, 0L)
            )
        }

        // 1. Detectar encabezados y pies recurrentes entre páginas
        val pageTopsAndBottoms = extractPageBoundaries(rawBlocks)
        val repetitiveHeadersFooters = noiseClassifier.detectRepetitiveHeadersFooters(pageTopsAndBottoms)

        val displayBlocks = mutableListOf<ReadingBlock>()
        val narrationUnits = mutableListOf<TtsNarrationUnit>()

        var totalNoiseBlocks = 0
        var totalImages = 0
        var totalWordsProcessed = 0L

        for (raw in rawBlocks) {
            if (raw.isImage) {
                totalImages++
                val imageBlock = ReadingBlock.Image(
                    id = raw.id,
                    caption = raw.imageCaption,
                    altText = null,
                    imageUri = raw.imageUri,
                    pageIndex = raw.pageIndex,
                    orderIndex = raw.orderIndex
                )
                displayBlocks.add(imageBlock)

                val narrationText = imageAnnouncer.buildNarrationText(imageBlock, announceImages)
                if (narrationText != null) {
                    narrationUnits.add(
                        TtsNarrationUnit(
                            id = "tts-${imageBlock.id}",
                            blockId = imageBlock.id,
                            textToSpeak = narrationText,
                            pageIndex = imageBlock.pageIndex,
                            isAnnouncement = true
                        )
                    )
                }
                continue
            }

            // 2. Clasificar si es ruido
            val noiseReason = noiseClassifier.classify(
                text = raw.text,
                isFirstLineOfPage = raw.isFirstLineOfPage,
                isLastLineOfPage = raw.isLastLineOfPage,
                repetitiveHeadersFooters = repetitiveHeadersFooters
            )

            if (noiseReason != null) {
                totalNoiseBlocks++
                val noiseBlock = ReadingBlock.Noise(
                    id = raw.id,
                    text = raw.text,
                    reason = noiseReason,
                    pageIndex = raw.pageIndex,
                    orderIndex = raw.orderIndex
                )
                if (!stripNoiseFromDisplay) {
                    displayBlocks.add(noiseBlock)
                }
                // El ruido NUNCA entra a las unidades de narración TTS
                continue
            }

            // 3. Aplicar exclusión de palabras
            val filteredText = wordExclusionEngine.filterText(raw.text, rules)
            if (filteredText.isBlank()) continue

            // 4. Formatear tipográficamente para español
            val formattedText = spanishTextFormatter.formatParagraph(filteredText)

            // Contabilizar palabras
            totalWordsProcessed += formattedText.split(Regex("""\s+""")).size

            // 5. Determinar si es título o párrafo estándar
            val block = if (isLikelyHeading(raw.fontSize, formattedText)) {
                ReadingBlock.Heading(
                    id = raw.id,
                    text = formattedText,
                    level = calculateHeadingLevel(raw.fontSize),
                    pageIndex = raw.pageIndex,
                    orderIndex = raw.orderIndex
                )
            } else {
                ReadingBlock.Paragraph(
                    id = raw.id,
                    text = formattedText,
                    pageIndex = raw.pageIndex,
                    orderIndex = raw.orderIndex
                )
            }

            displayBlocks.add(block)
            narrationUnits.add(
                TtsNarrationUnit(
                    id = "tts-${block.id}",
                    blockId = block.id,
                    textToSpeak = formattedText,
                    pageIndex = block.pageIndex,
                    isAnnouncement = false
                )
            )
        }

        val metrics = PipelineMetrics(
            totalRawBlocks = rawBlocks.size,
            validContentBlocks = displayBlocks.size,
            filteredNoiseBlocks = totalNoiseBlocks,
            imagesFound = totalImages,
            wordsProcessed = totalWordsProcessed
        )

        return ProcessedDocument(displayBlocks, narrationUnits, metrics)
    }

    private fun extractPageBoundaries(blocks: List<RawDocumentBlock>): List<Pair<String?, String?>> {
        val byPage = blocks.groupBy { it.pageIndex }
        return byPage.map { (_, pageBlocks) ->
            val first = pageBlocks.firstOrNull { !it.isImage }?.text
            val last = pageBlocks.lastOrNull { !it.isImage }?.text
            Pair(first, last)
        }
    }

    private fun isLikelyHeading(fontSize: Float, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("“") || trimmed.startsWith("\"") || trimmed.startsWith("«") ||
            trimmed.startsWith("—") || trimmed.startsWith("–") || trimmed.startsWith("- ")
        ) {
            return false // Diálogo, nunca es un título
        }
        if (trimmed.endsWith(".") || trimmed.endsWith(".”") || trimmed.endsWith("…”") || trimmed.endsWith("!»") || trimmed.endsWith("?»")) {
            return false // Oración o diálogo con puntuación de cierre
        }
        if (trimmed.startsWith("Capítulo", ignoreCase = true) ||
            trimmed.startsWith("Capitulo", ignoreCase = true) ||
            trimmed.startsWith("Chapter", ignoreCase = true) ||
            trimmed.startsWith("Parte", ignoreCase = true) ||
            trimmed.startsWith("Prólogo", ignoreCase = true) ||
            trimmed.startsWith("Prologo", ignoreCase = true) ||
            trimmed.startsWith("Epílogo", ignoreCase = true) ||
            trimmed.startsWith("Epilogo", ignoreCase = true) ||
            trimmed.startsWith("#")
        ) {
            return true
        }
        // Solo encabezados reales: tamaño notablemente mayor que el texto base y longitud corta sin puntuación
        if (fontSize >= 18f && trimmed.length < 60) return true
        return false
    }

    private fun calculateHeadingLevel(fontSize: Float): Int {
        return when {
            fontSize >= 20f -> 1
            fontSize >= 16f -> 2
            else -> 3
        }
    }
}
