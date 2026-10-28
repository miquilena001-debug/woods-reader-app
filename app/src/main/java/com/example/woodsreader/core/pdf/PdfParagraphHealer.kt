package com.example.woodsreader.core.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import java.io.StringWriter

/**
 * Modelo que representa un capítulo identificado en un documento PDF.
 */
data class PdfChapter(
    val chapterIndex: Int,
    val title: String,
    val pageNumber: Int,
    val snippet: String = ""
)

/**
 * Motor inteligente de curación y normalización tipográfica para PDFs en Woods Reader:
 * 1. Line-Unwrapping: Elimina cortes artificiales de renglón uniendo oraciones incompletas para TTS continuo.
 * 2. Purga de Ruido: Descarta números de página sueltos y encabezados/pies de página residuales.
 * 3. Detección Automática de Capítulos (TOC Virtual): Escanea novelas web empaquetadas en PDF (ej. 350 caps)
 *    generando la Tabla de Contenidos estructurada para Readium y el lector.
 */
object PdfParagraphHealer {

    private val SENTENCE_END_PUNCTUATION = setOf('.', '!', '?', ':', '”', '"', '»', '…', '—')

    private val PAGE_NUMBER_REGEX = Regex(
        """(?i)^\s*(?:page|p[áa]g(?:ina|\.)?|[-—–~#*])*\s*(\d+)\s*(?:of|de|/|\s*\d+)?\s*(?:[-—–~#*])*\s*$"""
    )

    private val CHAPTER_HEADING_REGEX = Regex(
        """(?i)^\s*(?:\[|\()? *(?:chapter|cap[íi]tulo|cap\.?|episode|episodio|secci[óo]n|pr[óo]logo|prologue|ep[íi]logo|epilogue)\b(?:\s*(\d+|[ivxlcdm]+))?(?:[:.\-–—\s]+(.*))? *(?:\]|\))?$"""
    )

    private val NUMBERED_NOVEL_HEADING_REGEX = Regex(
        """^\s*(\d{1,4})\s*[-:–—.]\s+(.+)$"""
    )

    private val HYPHEN_CHARS = setOf('-', '\u00ad', '\u2010')

    /**
     * Determina si una línea de texto corresponde a un número de página o pie numérico.
     */
    fun isPageNumberLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.all { it.isDigit() }) return true
        return PAGE_NUMBER_REGEX.matches(trimmed)
    }

    /**
     * Determina si una línea de texto corresponde al título o encabezado de un capítulo.
     */
    fun isChapterHeading(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length > 90) return false

        // Evitar oraciones completas que solo mencionen la palabra capítulo
        if (trimmed.endsWith(".") || trimmed.endsWith(".”") || trimmed.endsWith("!”") || trimmed.endsWith("?”")) {
            return false
        }

        if (CHAPTER_HEADING_REGEX.matches(trimmed)) return true
        if (NUMBERED_NOVEL_HEADING_REGEX.matches(trimmed)) return true

        return false
    }

    /**
     * Extrae un título limpio para el capítulo identificado.
     */
    fun extractCleanChapterTitle(line: String, defaultIndex: Int): String {
        val trimmed = line.trim()
        val chapterMatch = CHAPTER_HEADING_REGEX.find(trimmed)
        if (chapterMatch != null) {
            val num = chapterMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            val extra = chapterMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            return when {
                num.isNotEmpty() && extra.isNotEmpty() -> "Chapter $num: $extra"
                num.isNotEmpty() -> "Chapter $num"
                extra.isNotEmpty() -> extra
                else -> trimmed
            }
        }

        val numberedMatch = NUMBERED_NOVEL_HEADING_REGEX.find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val title = numberedMatch.groupValues[2].trim()
            return "Chapter $num: $title"
        }

        return trimmed.ifBlank { "Capítulo $defaultIndex" }
    }

    /**
     * Realiza line-unwrapping inteligente y limpieza sobre una lista de líneas sin formato.
     */
    fun healParagraphs(rawLines: List<String>): List<String> {
        val result = mutableListOf<String>()
        val currentParagraph = StringBuilder()

        fun flush() {
            if (currentParagraph.isNotEmpty()) {
                val text = currentParagraph.toString().trim()
                if (text.isNotEmpty()) {
                    result.add(text)
                }
                currentParagraph.setLength(0)
            }
        }

        for (i in rawLines.indices) {
            val line = rawLines[i].trim()
            if (line.isEmpty()) {
                flush()
                continue
            }

            // Descartar números de página aislados
            if (isPageNumberLine(line)) {
                flush()
                continue
            }

            val isChapter = isChapterHeading(line)
            val isDialogueStart = line.startsWith("—") || line.startsWith("–") || line.startsWith("- ") ||
                    line.startsWith("«") || line.startsWith("\"") || line.startsWith("“")

            if (isChapter || isDialogueStart) {
                flush()
            }

            if (currentParagraph.isNotEmpty()) {
                val currentText = currentParagraph.toString()
                val lastChar = currentText.lastOrNull()
                val endsWithHyphen = lastChar != null && lastChar in HYPHEN_CHARS
                val startsWithLetter = line.firstOrNull()?.isLetter() == true

                if (endsWithHyphen && startsWithLetter) {
                    currentParagraph.setLength(currentParagraph.length - 1)
                    currentParagraph.append(line)
                } else {
                    currentParagraph.append(" ").append(line)
                }
            } else {
                currentParagraph.append(line)
            }

            // Si la línea actual concluye con puntuación de fin de oración o es un encabezado de capítulo, cerrar párrafo
            val lastCharInLine = line.lastOrNull()
            val endsWithSentencePunctuation = lastCharInLine != null && lastCharInLine in SENTENCE_END_PUNCTUATION
            if (isChapter || endsWithSentencePunctuation) {
                flush()
            }
        }

        flush()
        return result
    }

    /**
     * Escanea las páginas de un [PDDocument] detectando capítulos de novela web para construir la TOC virtual.
     */
    fun scanChapters(
        document: PDDocument,
        maxPagesToScan: Int = Int.MAX_VALUE,
        pageTextExtractor: ((doc: PDDocument, page: Int) -> String)? = null
    ): List<PdfChapter> {
        val totalPages = minOf(document.numberOfPages, maxPagesToScan)
        val chapters = mutableListOf<PdfChapter>()
        var chapterCounter = 1

        val stripper = if (pageTextExtractor == null) {
            runCatching {
                PDFTextStripper().apply { sortByPosition = true }
            }.getOrNull()
        } else null

        for (pageIdx in 1..totalPages) {
            try {
                val pageText = if (pageTextExtractor != null) {
                    pageTextExtractor(document, pageIdx)
                } else if (stripper != null) {
                    stripper.startPage = pageIdx
                    stripper.endPage = pageIdx
                    val writer = StringWriter()
                    stripper.writeText(document, writer)
                    writer.toString()
                } else {
                    ""
                }
                val lines = pageText.lines().map { it.trim() }.filter { it.isNotEmpty() }

                // Buscar encabezado de capítulo en las primeras 15 líneas de la página
                for (line in lines.take(15)) {
                    if (isChapterHeading(line)) {
                        val cleanTitle = extractCleanChapterTitle(line, chapterCounter)
                        // Evitar duplicar el mismo capítulo en la misma página
                        if (chapters.none { it.pageNumber == pageIdx && it.title == cleanTitle }) {
                            chapters.add(
                                PdfChapter(
                                    chapterIndex = chapterCounter++,
                                    title = cleanTitle,
                                    pageNumber = pageIdx,
                                    snippet = lines.dropWhile { it == line }.take(3).joinToString(" ")
                                )
                            )
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Si una página específica falla la extracción, continuar con las siguientes
            }
        }

        return chapters
    }

    /**
     * Convierte una lista de [PdfChapter] a una lista de [Link] de Readium con destinos de página URL "/page_N#page=N".
     * Permite inyectar un [urlFactory] personalizado para pruebas unitarias sin dependencias de Android framework.
     */
    fun chaptersToTocLinks(
        chapters: List<PdfChapter>,
        urlFactory: (String) -> Url? = { href ->
            try {
                Url(href) ?: Url.fromDecodedPath(href)
            } catch (_: Throwable) {
                null
            }
        }
    ): List<Link> {
        return chapters.mapNotNull { ch ->
            val hrefString = "/page_${ch.pageNumber}#page=${ch.pageNumber}"
            val url = urlFactory(hrefString) ?: return@mapNotNull null
            Link(
                href = url,
                title = ch.title
            )
        }
    }
}
