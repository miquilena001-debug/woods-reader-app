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
        val pageBodyFontSize = lines.filter { it.text.isNotBlank() }
            .groupBy { Math.round(it.fontSize * 10) / 10f }
            .maxByOrNull { it.value.size }?.key ?: baseFontSize
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
            if (isLikelyHeader(fontSize, text, pageBodyFontSize)) {
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

    private fun isLikelyHeader(fontSize: Float, text: String, pageBodyFontSize: Float = baseFontSize): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("“") || trimmed.startsWith("\"") || trimmed.startsWith("«") ||
            trimmed.startsWith("—") || trimmed.startsWith("–") || trimmed.startsWith("- ")
        ) {
            return false
        }
        if (trimmed.endsWith(".") || trimmed.endsWith(".”") || trimmed.endsWith("…”") || trimmed.endsWith("!»") || trimmed.endsWith("?»")) {
            return false
        }
        if (trimmed.length <= 80 && (
            trimmed.contains(Regex("""(?i)^(cap[íi]tulo|chapter|parte|secci[óo]n|pr[óo]logo|ep[íi]logo|\b[0-9IVXLCDM]+\b)""")) ||
            trimmed.startsWith("#")
        )) return true

        val threshold = (pageBodyFontSize * headerThreshold).coerceAtLeast(pageBodyFontSize + 2.5f)
        if (fontSize > threshold && trimmed.length <= 60) return true

        return false
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
     * agrupando líneas en párrafos reales continuos (estilo EPUB/editorial),
     * resolviendo guiones al final de línea y detectando inicios de diálogo y encabezados.
     */
    fun convertPageToRawBlocks(
        lines: List<LineInfo>,
        pageIndex: Int,
        images: List<PageImage> = emptyList()
    ): List<RawDocumentBlock> {
        if (lines.isEmpty() && images.isEmpty()) return emptyList()

        val rawBlocks = mutableListOf<RawDocumentBlock>()
        var orderIndex = 0

        // Filtrar líneas no vacías
        val validLines = lines.filter { it.text.isNotBlank() }
        val maxLineWidth = validLines.maxOfOrNull { it.xEnd - it.xStart } ?: 0f
        val minXStart = validLines.minOfOrNull { it.xStart } ?: 0f

        // Calcular tamaño de fuente modal (cuerpo principal del texto en esta página)
        val pageBodyFontSize = validLines
            .groupBy { Math.round(it.fontSize * 10) / 10f }
            .maxByOrNull { it.value.size }?.key ?: baseFontSize

        // Calcular espaciado vertical típico entre líneas
        val yDiffs = validLines.zipWithNext { a, b -> b.y - a.y }.filter { it > 0 }
        val typicalLineSpacing = if (yDiffs.isNotEmpty()) {
            yDiffs.sorted()[yDiffs.size / 2]
        } else {
            pageBodyFontSize * 1.25f
        }

        // Combinar líneas e imágenes ordenadas por posición vertical Y
        data class PageItem(val line: LineInfo? = null, val image: PageImage? = null, val y: Float)

        val allItems = mutableListOf<PageItem>()
        validLines.forEach { allItems.add(PageItem(line = it, y = it.y)) }
        images.forEach { allItems.add(PageItem(image = it, y = it.y)) }
        allItems.sortBy { it.y }

        val currentParagraph = StringBuilder()
        var lastLineInfo: LineInfo? = null

        var paragraphFirstLine = false
        var paragraphLastLine = false

        fun flushCurrentParagraph() {
            if (currentParagraph.isNotEmpty()) {
                val raw = currentParagraph.toString().trim()
                if (raw.isNotEmpty()) {
                    rawBlocks.add(
                        RawDocumentBlock(
                            id = "pdf-p$pageIndex-blk-$orderIndex",
                            text = raw,
                            isImage = false,
                            pageIndex = pageIndex,
                            orderIndex = orderIndex++,
                            isFirstLineOfPage = paragraphFirstLine,
                            isLastLineOfPage = paragraphLastLine,
                            fontSize = baseFontSize
                        )
                    )
                }
                currentParagraph.setLength(0)
                paragraphFirstLine = false
                paragraphLastLine = false
            }
        }

        val firstLineIndex = allItems.indexOfFirst { it.line != null }
        val lastLineIndex = allItems.indexOfLast { it.line != null }

        for ((idx, item) in allItems.withIndex()) {
            if (item.image != null) {
                flushCurrentParagraph()
                lastLineInfo = null
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
                val text = line.text.trim()
                if (text.isEmpty()) continue

                    val fontSize = line.fontSize
                    val isFirstLineOfPage = (idx == firstLineIndex)
                    val isLastLineOfPage = (idx == lastLineIndex)

                    // 1. Filtrar números de página aislados
                    if (noiseClassifier.isPageNumber(text)) {
                        flushCurrentParagraph()
                        lastLineInfo = null
                        continue
                    }

                    // 2. Filtrar encabezados y pies repetitivos o ruidosos
                    val noiseReason = noiseClassifier.classify(
                        text = text,
                        isFirstLineOfPage = isFirstLineOfPage,
                        isLastLineOfPage = isLastLineOfPage
                    )
                    if (noiseReason != null) {
                        flushCurrentParagraph()
                        lastLineInfo = null
                        continue
                    }

                    // 3. Encabezados y títulos de capítulo
                    if (isLikelyHeader(fontSize, text, pageBodyFontSize)) {
                        flushCurrentParagraph()
                        lastLineInfo = null
                        rawBlocks.add(
                            RawDocumentBlock(
                                id = "pdf-p$pageIndex-blk-$orderIndex",
                                text = text,
                                isImage = false,
                                pageIndex = pageIndex,
                                orderIndex = orderIndex++,
                                isFirstLineOfPage = isFirstLineOfPage,
                                isLastLineOfPage = isLastLineOfPage,
                                fontSize = maxOf(fontSize, 20f)
                            )
                        )
                        continue
                    }

                    // 4. Detección de límite de párrafo
                    val lastLine = lastLineInfo
                    var isNewParagraph = false

                    if (lastLine != null) {
                        val yDiff = line.y - lastLine.y
                        val lastLineWidth = lastLine.xEnd - lastLine.xStart
                        val lastTextTrimmed = lastLine.text.trim()

                        // A. Salto vertical grande (espacio entre párrafos o cambio de bloque)
                        val verticalBreak = yDiff > typicalLineSpacing * 1.55f || yDiff > line.fontSize * 1.8f || yDiff < -line.fontSize

                        // B. Inicio de diálogo en español (raya em-dash, guión o comillas)
                        val isDialogueStart = text.startsWith("—") || text.startsWith("–") || text.startsWith("- ") ||
                                text.startsWith("«") || text.startsWith("\"")

                        // C. La línea anterior terminó con puntuación de cierre
                        val lastEndedWithPunctuation = lastTextTrimmed.endsWith(".") || lastTextTrimmed.endsWith("!") ||
                                lastTextTrimmed.endsWith("?") || lastTextTrimmed.endsWith("…") ||
                                lastTextTrimmed.endsWith("»") || lastTextTrimmed.endsWith("\"") ||
                                lastTextTrimmed.endsWith("—")

                        // D. Línea anterior terminó corto (< 82% del ancho máximo de línea)
                        val lastLineEndedEarly = lastLineWidth < maxLineWidth * 0.82f

                        // E. Sangría de primera línea en libro impreso
                        val isIndented = line.xStart > minXStart + 12f

                        if (verticalBreak || isDialogueStart || (lastLineEndedEarly && lastEndedWithPunctuation) || (isIndented && lastEndedWithPunctuation)) {
                            isNewParagraph = true
                        }
                    }

                    if (isNewParagraph) {
                        flushCurrentParagraph()
                    }

                    // 5. Unir líneas del párrafo resolviendo cortes de palabra (guiones)
                    if (currentParagraph.isNotEmpty()) {
                        val currentTextStr = currentParagraph.toString()
                        val endsWithHyphen = currentTextStr.endsWith('-') ||
                                currentTextStr.endsWith('\u00ad') ||
                                currentTextStr.endsWith('\u2010')
                        val startsWithLetter = text.firstOrNull()?.isLetter() ?: false

                        if (endsWithHyphen && startsWithLetter) {
                            currentParagraph.setLength(currentParagraph.length - 1)
                            currentParagraph.append(text)
                        } else {
                            currentParagraph.append(" ").append(text)
                        }
                    } else {
                        paragraphFirstLine = isFirstLineOfPage
                        currentParagraph.append(text)
                    }
                    paragraphLastLine = isLastLineOfPage

                    lastLineInfo = line
                }
            }

        flushCurrentParagraph()

        return rawBlocks
    }
}
