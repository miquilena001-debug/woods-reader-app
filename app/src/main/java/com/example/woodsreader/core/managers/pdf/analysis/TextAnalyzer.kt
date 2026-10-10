package com.example.woodsreader.core.managers.pdf.analysis

import android.graphics.RectF
import com.tom_roush.pdfbox.text.TextPosition
import timber.log.Timber
import kotlin.math.abs

class TextAnalyzer(
    private val baseFontSize: Float = 12f,
    private val headerThreshold: Float = 1.3f,
    private val pageMargins: RectF = RectF(36f, 36f, 36f, 36f)
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
                mergedElements.add(SemanticElement.Paragraph(currentParagraph.toString(), pageNumber))
                currentParagraph.setLength(0)
            }
        }

        for (line in lines) {
            val text = line.text.trim()
            if (text.isEmpty()) continue

            val fontSize = line.fontSize
            val yPosition = line.y

            // Detectar número de página
            if (isLikelyPageNumber(text, yPosition)) {
                flushCurrentParagraph()
                mergedElements.add(SemanticElement.PageNumber(text, pageNumber))
                processedLines.add(text)
                lastLineInfo = null
                continue
            }

            // Detectar encabezados
            if (isLikelyHeader(fontSize, text)) {
                flushCurrentParagraph()
                val level = calculateHeaderLevel(fontSize)
                if (processedLines.contains(text)) {
                    mergedElements.add(SemanticElement.Ignored(text, pageNumber, "header_repeated"))
                } else {
                    mergedElements.add(SemanticElement.Header(text, level, pageNumber))
                    processedLines.add(text)
                }
                lastLineInfo = null
                continue
            }

            // Detectar límite de párrafo basado en la línea anterior
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

            // Unir líneas del mismo párrafo
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

    private fun isLikelyPageNumber(text: String, yPosition: Float): Boolean {
        // Heurística: números pequeños en bordes superior/inferior (ajustar 50f/750f según tamaño)
        val isEdge = yPosition < 50f || yPosition > 750f
        return text.matches(Regex("\\d+")) && isEdge
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

    // Verifica sangría (indentación)
    private fun isIndented(xPosition: Float): Boolean {
        return xPosition > pageMargins.left + 20f 
    }

    data class LineInfo(
        val text: String,
        val fontSize: Float,
        val y: Float,
        val xStart: Float,
        val xEnd: Float
    )
}
