package com.example.woodsreader.core.managers.pdf.analysis

import android.graphics.RectF
import com.example.woodsreader.core.pipeline.NoiseClassifier
import com.example.woodsreader.core.pipeline.RawDocumentBlock
import com.example.woodsreader.core.pipeline.SpanishTextFormatter
import timber.log.Timber

class TextAnalyzer(
    private val baseFontSize: Float = 12f,
    private val headerThreshold: Float = 1.3f,
    private val pageMargins: RectF = RectF(36f, 36f, 36f, 36f),
    private val noiseClassifier: NoiseClassifier = NoiseClassifier(),
    private val spanishFormatter: SpanishTextFormatter = SpanishTextFormatter()
) {

    sealed class SemanticElement {
        abstract val pageNumber: Int
        abstract val rawText: String

        data class Header(override val rawText: String, val level: Int, override val pageNumber: Int) : SemanticElement()
        data class Paragraph(override val rawText: String, override val pageNumber: Int) : SemanticElement()
        data class PageNumber(override val rawText: String, override val pageNumber: Int) : SemanticElement()
        data class Ignored(override val rawText: String, override val pageNumber: Int, val reason: String) : SemanticElement()
    }

    fun analyzeLines(lines: List<LineInfo>, pageNumber: Int): List<SemanticElement> {
        if (lines.isEmpty()) return emptyList()

        val maxLineWidth = lines.maxOfOrNull { it.xEnd - it.xStart } ?: 0f
        val mergedElements = mutableListOf<SemanticElement>()
        val processedLines = mutableSetOf<String>()

        val currentParagraph = StringBuilder()
        var lastLineInfo: LineInfo? = null

        fun flushCurrentParagraph() {
            if (currentParagraph.isNotEmpty()) {
                val raw = currentParagraph.toString().trim()
                if (raw.isNotEmpty()) {
                    val formatted = spanishFormatter.formatParagraph(raw)
                    mergedElements.add(SemanticElement.Paragraph(formatted, pageNumber))
                }
                currentParagraph.setLength(0)
            }
        }

        for (i in lines.indices) {
            val line = lines[i]
            val text = line.text.trim()
            if (text.isEmpty()) continue

            val fontSize = line.fontSize
            val isFirstLine = (i == 0)
            val isLastLine = (i == lines.size - 1)

            // 1. Detección avanzada de número de página mediante NoiseClassifier
            if (noiseClassifier.isPageNumber(text)) {
                flushCurrentParagraph()
                mergedElements.add(SemanticElement.PageNumber(text, pageNumber))
                processedLines.add(text)
                lastLineInfo = null
                continue
            }

            // 2. Detección de ruido (ornamentos, avisos legales, URLs aisladas)
            val noiseReason = noiseClassifier.classify(
                text = text,
                isFirstLineOfPage = isFirstLine,
                isLastLineOfPage = isLastLine
            )
            if (noiseReason != null) {
                flushCurrentParagraph()
                mergedElements.add(SemanticElement.Ignored(text, pageNumber, noiseReason.name))
                processedLines.add(text)
                lastLineInfo = null
                continue
            }

            // 3. Detectar encabezados
            if (isLikelyHeader(fontSize, text)) {
                flushCurrentParagraph()
                val level = calculateHeaderLevel(fontSize)
                if (processedLines.contains(text)) {
                    mergedElements.add(SemanticElement.Ignored(text, pageNumber, "HEADER_REPEATED"))
                } else {
                    mergedElements.add(SemanticElement.Header(text, level, pageNumber))
                    processedLines.add(text)
                }
                lastLineInfo = null
                continue
            }

            // 4. Detectar límite de párrafo basado en la línea anterior
            val lastLine = lastLineInfo
            var isNewParagraph = false
            if (lastLine != null) {
                val yDiff = line.y - lastLine.y
                val lastLineWidth = lastLine.xEnd - lastLine.xStart
                
                // Si la distancia vertical es significativamente mayor que el tamaño de fuente, o si saltamos arriba (cambio de columna)
                val verticalBreak = yDiff > line.fontSize * 1.8f || yDiff < -line.fontSize
                
                // Si la línea anterior terminó temprano (menos del 82% del ancho máximo de línea de la página)
                val lastLineEndedEarly = lastLineWidth < maxLineWidth * 0.82f
                
                if (verticalBreak || lastLineEndedEarly) {
                    isNewParagraph = true
                }
            }

            if (isNewParagraph) {
                flushCurrentParagraph()
            }

            // 5. Unir líneas del mismo párrafo respetando cortes de palabra (guiones)
            if (currentParagraph.isNotEmpty()) {
                val currentTextStr = currentParagraph.toString()
                val endsWithHyphen = currentTextStr.endsWith('-') || 
                                     currentTextStr.endsWith('\u00ad') || 
                                     currentTextStr.endsWith('\u2010')
                val startsWithLetter = text.firstOrNull()?.isLetter() ?: false

                if (endsWithHyphen && startsWithLetter) {
                    // Eliminar guión de corte de palabra y unir sin espacio
                    currentParagraph.setLength(currentParagraph.length - 1)
                    currentParagraph.append(text)
                } else {
                    currentParagraph.append(" ").append(text)
                }
            } else {
                currentParagraph.append(text)
            }

            lastLineInfo = line
        }

        flushCurrentParagraph()

        return mergedElements
    }

    private fun isLikelyHeader(fontSize: Float, text: String): Boolean {
        return fontSize > baseFontSize * headerThreshold
    }

    private fun calculateHeaderLevel(fontSize: Float): Int {
        return when {
            fontSize > baseFontSize * 1.4f -> 1
            fontSize > baseFontSize * 1.3f -> 2
            fontSize > baseFontSize * 1.2f -> 3
            else -> 4
        }
    }

    data class LineInfo(
        val text: String,
        val fontSize: Float,
        val y: Float,
        val xStart: Float,
        val xEnd: Float
    )

    data class PageImage(
        val uri: String? = null,
        val caption: String? = null,
        val y: Float = 0f
    )

    /**
     * Convierte líneas e imágenes de una página PDF a una lista de [RawDocumentBlock]
     * compatible directamente con [ReadingPipeline].
     */
    fun convertPageToRawBlocks(
        lines: List<LineInfo>,
        pageIndex: Int,
        images: List<PageImage> = emptyList()
    ): List<RawDocumentBlock> {
        if (lines.isEmpty() && images.isEmpty()) return emptyList()

        val rawBlocks = mutableListOf<RawDocumentBlock>()
        var orderIndex = 0

        data class PageItem(val line: LineInfo?, val image: PageImage?, val y: Float)

        val items = mutableListOf<PageItem>()
        lines.forEach { items.add(PageItem(line = it, image = null, y = it.y)) }
        images.forEach { items.add(PageItem(line = null, image = it, y = it.y)) }
        items.sortBy { it.y }

        val firstLineIndex = items.indexOfFirst { it.line != null }
        val lastLineIndex = items.indexOfLast { it.line != null }

        for ((idx, item) in items.withIndex()) {
            if (item.image != null) {
                rawBlocks.add(
                    RawDocumentBlock(
                        id = "pdf-p$pageIndex-img-$orderIndex",
                        text = item.image.caption ?: "",
                        isImage = true,
                        imageCaption = item.image.caption,
                        imageUri = item.image.uri,
                        pageIndex = pageIndex,
                        orderIndex = orderIndex++,
                        fontSize = baseFontSize
                    )
                )
            } else if (item.line != null) {
                val line = item.line
                val trimmed = line.text.trim()
                if (trimmed.isNotEmpty()) {
                    rawBlocks.add(
                        RawDocumentBlock(
                            id = "pdf-p$pageIndex-blk-$orderIndex",
                            text = trimmed,
                            isImage = false,
                            pageIndex = pageIndex,
                            orderIndex = orderIndex++,
                            isFirstLineOfPage = (idx == firstLineIndex),
                            isLastLineOfPage = (idx == lastLineIndex),
                            fontSize = line.fontSize
                        )
                    )
                }
            }
        }

        return rawBlocks
    }
}
