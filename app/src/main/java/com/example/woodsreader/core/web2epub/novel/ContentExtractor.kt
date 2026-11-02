package com.example.woodsreader.core.web2epub.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist
import java.net.URI

enum class ChapterStatus {
    OK,
    SHORT,
    LOCKED,
    EMPTY
}

data class ExtractedChapter(
    val title: String,
    val bodyXhtml: String,
    val wordCount: Int,
    val imageSources: List<String>,
    val status: ChapterStatus
)

/**
 * Extractor puro de contenido de capítulos a XHTML limpio compatible con EPUB 3.
 * Sin dependencias de android.* según regla R-TEST.
 */
class ContentExtractor(
    private val profile: SiteProfile? = null
) {
    private val defaultLockedKeywords = listOf(
        "vip only", "unlock to read", "pay to read", "patreon", "subscribe to unlock",
        "buy coins", "capítulo bloqueado", "bloqueado para miembros"
    )

    fun extract(
        html: String,
        baseUri: String,
        fallbackTitle: String = ""
    ): ExtractedChapter {
        if (html.isBlank()) {
            return ExtractedChapter(
                title = fallbackTitle.ifBlank { "Capítulo" },
                bodyXhtml = "<div class=\"chapter-content\"><p></p></div>",
                wordCount = 0,
                imageSources = emptyList(),
                status = ChapterStatus.EMPTY
            )
        }

        val doc = Jsoup.parse(html, baseUri)

        // 1. Extraer título
        val title = extractTitle(doc, fallbackTitle)

        // 2. Extraer contenedor de contenido
        val contentElement = findContentContainer(doc)
            ?: return ExtractedChapter(
                title = title,
                bodyXhtml = "<div class=\"chapter-content\"><p></p></div>",
                wordCount = 0,
                imageSources = emptyList(),
                status = ChapterStatus.EMPTY
            )

        // 3. Clonar contenedor para manipulación
        val workingElement = contentElement.clone()

        // 3.5 Remover elementos ocultos por CSS (reglas en <style> e inline)
        removeHiddenCssElements(doc, workingElement)

        // 4. Remover elementos no deseados por selector
        val selectorsToRemove = mutableListOf(
            "script", "style", "noscript", "iframe", "svg", "button", "input", "form",
            ".advertisement", ".ad", ".ads", ".adsbygoogle", ".social-share", ".sharedaddy"
        )
        profile?.removeSelectors?.let { selectorsToRemove.addAll(it) }

        for (selector in selectorsToRemove) {
            try {
                workingElement.select(selector).remove()
            } catch (_: Exception) {
            }
        }

        // 5. Remover nodos de texto o elementos que contengan frases prohibidas
        val textPatterns = profile?.removeTextContains ?: emptyList()
        if (textPatterns.isNotEmpty()) {
            for (pattern in textPatterns) {
                if (pattern.isBlank()) continue
                val matches = workingElement.getElementsContainingOwnText(pattern)
                for (el in matches) {
                    el.remove()
                }
            }
        }

        // 6. Extraer y reescribir imágenes (data-src, data-original, src)
        val imageSources = mutableListOf<String>()
        val imgElements = workingElement.select("img")
        for (img in imgElements) {
            val src = resolveImageSrc(img, baseUri)
            if (src.isNotBlank() && (src.startsWith("http://") || src.startsWith("https://"))) {
                imageSources.add(src)
                img.attr("src", src)
                // Limpiar atributos lazy
                img.removeAttr("data-src")
                img.removeAttr("data-original")
                img.removeAttr("srcset")
                img.removeAttr("loading")
            } else {
                img.remove()
            }
        }

        // 7. Remover heading inicial si duplica el título del capítulo
        removeDuplicateTitleHeading(workingElement, title)

        // 8. Normalizar saltos de línea sueltos y convertir <br><br> en <p>
        normalizeParagraphs(workingElement)

        // 9. Sanitizar con Jsoup Safelist estricto para XHTML EPUB
        val safelist = Safelist.relaxed()
            .addTags("hr")
            .removeTags("table", "thead", "tbody", "tfoot", "tr", "th", "td") // Aplanar a texto si hay
            .addAttributes("img", "src", "alt", "title")
            .removeAttributes("img", "width", "height", "style")
            .removeAttributes("p", "style", "class")
            .removeAttributes("div", "style", "class")

        val cleaner = Cleaner(safelist)
        val cleanDoc = cleaner.clean(Document.createShell(baseUri).apply { body().appendChild(workingElement) })
        val bodyHtml = cleanDoc.body().children().firstOrNull()?.html()?.trim() ?: cleanDoc.body().html().trim()
        val bodyXhtml = "<div class=\"chapter-content\">\n$bodyHtml\n</div>"

        // 11. Conteo de palabras y validación de texto
        val rawText = workingElement.text().trim()
        val words = if (rawText.isBlank()) 0 else rawText.split(Regex("\\s+")).size

        // 12. Determinar status
        val status = determineStatus(rawText, words, imageSources.size)

        return ExtractedChapter(
            title = title,
            bodyXhtml = bodyXhtml,
            wordCount = words,
            imageSources = imageSources.distinct(),
            status = status
        )
    }

    private fun extractTitle(doc: Document, fallbackTitle: String): String {
        // Priorizar selector de título específico del perfil (dividiendo por comas para respetar prioridad de especificidad)
        profile?.titleSelector?.let { sel ->
            val subSelectors = sel.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (subSel in subSelectors) {
                val el = doc.selectFirst(subSel)
                if (el != null && el.text().isNotBlank()) {
                    return cleanTitleText(el.text())
                }
            }
        }

        // Si hay clase común de título de capítulo en el DOM, preferirla antes que el <h1> global
        val chapterTitleEl = doc.selectFirst(".chapter-title, span.chapter-title, strong.chapter-title")
        if (chapterTitleEl != null && chapterTitleEl.text().isNotBlank()) {
            return cleanTitleText(chapterTitleEl.text())
        }

        val h1 = doc.selectFirst("h1")
        if (h1 != null && h1.text().isNotBlank()) {
            // Si el h1 contiene una sub-etiqueta de título de capítulo, tomar solo esa parte
            val innerChapterTitle = h1.selectFirst(".chapter-title, span.chapter-title")?.text()?.trim()
            if (!innerChapterTitle.isNullOrBlank()) {
                return cleanTitleText(innerChapterTitle)
            }
            return cleanTitleText(h1.text())
        }

        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
        if (!ogTitle.isNullOrBlank()) {
            return cleanTitleText(ogTitle)
        }

        if (fallbackTitle.isNotBlank()) {
            return cleanTitleText(fallbackTitle)
        }

        return doc.title().ifBlank { "Capítulo" }.let { cleanTitleText(it) }
    }

    private fun cleanTitleText(title: String): String {
        return title.replace(Regex("""\[\s*\.\.\.\s*words\s*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\d+\s+(months?|days?|hours?|years?|minutes?|weeks?)\s+ago""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[\r\n\t]+"""), " ")
            .trim()
    }

    private fun findContentContainer(doc: Document): Element? {
        val selectors = profile?.contentSelectors ?: emptyList()
        for (selector in selectors) {
            val el = doc.selectFirst(selector)
            if (el != null && el.text().length > 50) {
                return el
            }
        }

        // Fallback genéricos
        val genericSelectors = listOf(
            "#chapter-content", ".chapter-content", "#content", ".content",
            ".reading-content", ".entry-content", ".text-content", "article",
            "#chapter-text", ".chapter-text"
        )
        for (selector in genericSelectors) {
            val el = doc.selectFirst(selector)
            if (el != null && el.text().length > 50) {
                return el
            }
        }

        // Fallback por densidad de texto (buscar el div o article con mayor cantidad de párrafos y texto)
        var bestElement: Element? = null
        var maxScore = 0

        val candidates = doc.select("div, article, section")
        for (el in candidates) {
            val pCount = el.select("p").size
            val textLength = el.text().length
            val score = pCount * 100 + textLength
            if (score > maxScore) {
                maxScore = score
                bestElement = el
            }
        }

        return bestElement ?: doc.body()
    }

    private fun resolveImageSrc(img: Element, baseUri: String): String {
        val candidates = listOf(
            img.attr("data-src"),
            img.attr("data-original"),
            img.attr("data-url"),
            img.attr("src")
        )

        for (candidate in candidates) {
            val trimmed = candidate.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("data:")) {
                return try {
                    URI(baseUri).resolve(trimmed).toString()
                } catch (_: Exception) {
                    trimmed
                }
            }
        }
        return ""
    }

    private fun normalizeParagraphs(container: Element) {
        // Remover el heading duplicado antes de convertir los br si existe
        // Si no contiene <p> pero tiene <br>, convertir bloques divididos por <br> a <p>
        val pTags = container.select("p")
        if (pTags.isEmpty() && container.select("br").isNotEmpty()) {
            val html = container.html()
            val splitByDoubleBr = html.split(Regex("(?i)<br\\s*/?>\\s*<br\\s*/?>"))
            val newHtml = StringBuilder()
            for (part in splitByDoubleBr) {
                val cleanedPart = part.replace(Regex("(?i)<br\\s*/?>"), " ").trim()
                if (cleanedPart.isNotBlank()) {
                    newHtml.append("<p>").append(cleanedPart).append("</p>\n")
                }
            }
            if (newHtml.isNotEmpty()) {
                container.html(newHtml.toString())
            }
        }
    }

    private fun removeDuplicateTitleHeading(container: Element, title: String) {
        val firstHeading = container.selectFirst("h1, h2, h3, h4") ?: return
        val headingText = firstHeading.text().trim()
        val normHeading = headingText.lowercase().replace(Regex("[^a-z0-9]"), "")
        val normTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")

        if (normHeading.isNotBlank() && (normHeading == normTitle || normTitle.contains(normHeading))) {
            firstHeading.remove()
        }
    }

    private fun determineStatus(text: String, wordCount: Int, imageCount: Int): ChapterStatus {
        if (wordCount == 0 && imageCount == 0) {
            return ChapterStatus.EMPTY
        }

        val lower = text.lowercase()
        for (kw in defaultLockedKeywords) {
            if (lower.contains(kw.lowercase())) {
                return ChapterStatus.LOCKED
            }
        }

        // Si tiene al menos una imagen, no se considera SHORT aunque tenga pocas palabras (ej. manga/ilustraciones)
        if (wordCount < 100 && imageCount == 0) {
            return ChapterStatus.SHORT
        }

        return ChapterStatus.OK
    }
    private fun removeHiddenCssElements(doc: Document, workingElement: Element) {
        // 1. Parsear reglas display:none y visibility:hidden en tags <style>
        val styleElements = (doc.select("style") + workingElement.select("style")).distinct()
        // Requiere propiedades explicitas (evita falsos positivos como --custom-display: none o background: none)
        val hideDeclarationRegex = Regex("""(?i)(?:^|[\s;{])(?:display\s*:\s*none|visibility\s*:\s*hidden)(?:\s*!important)?\s*(?:;|$)""")
        val simpleSelectorRegex = Regex("""^[a-zA-Z0-9]*[.#][a-zA-Z0-9_-]+$""")

        for (styleEl in styleElements) {
            val rawCss = listOf(styleEl.data(), styleEl.html())
                .firstOrNull { it.isNotBlank() } ?: continue
            val cleanCss = rawCss.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            // Descarta at-rules (@media, @supports, @keyframes, etc.) para no aplicar reglas condicionales de responsive/print
            val topLevelCss = stripAtRules(cleanCss)

            val ruleRegex = Regex("""([^{}]+)\{([^{}]+)\}""")
            for (match in ruleRegex.findAll(topLevelCss)) {
                val selectorsPart = match.groupValues[1]
                val declsPart = match.groupValues[2]
                if (hideDeclarationRegex.containsMatchIn(declsPart)) {
                    val subSelectors = selectorsPart.split(",")
                    for (subSel in subSelectors) {
                        val trimmedSel = subSel.trim()
                        if (simpleSelectorRegex.matches(trimmedSel)) {
                            try {
                                workingElement.select(trimmedSel).remove()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        // 2. Parsear style inline en workingElement
        for (el in workingElement.select("[style]")) {
            val inlineStyle = el.attr("style")
            if (hideDeclarationRegex.containsMatchIn(inlineStyle)) {
                try {
                    el.remove()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stripAtRules(css: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = css.length
        while (i < len) {
            val c = css[i]
            if (c == '@') {
                var j = i
                var inQuote: Char? = null
                while (j < len && (inQuote != null || (css[j] != ';' && css[j] != '{'))) {
                    val ch = css[j]
                    if (inQuote != null) {
                        if (ch == inQuote && (j == 0 || css[j - 1] != '\\')) inQuote = null
                    } else if (ch == '"' || ch == '\'') {
                        inQuote = ch
                    }
                    j++
                }
                if (j < len && css[j] == ';') {
                    i = j + 1
                } else if (j < len && css[j] == '{') {
                    var depth = 1
                    j++
                    inQuote = null
                    while (j < len && depth > 0) {
                        val ch = css[j]
                        if (inQuote != null) {
                            if (ch == inQuote && (j == 0 || css[j - 1] != '\\')) inQuote = null
                        } else if (ch == '"' || ch == '\'') {
                            inQuote = ch
                        } else if (ch == '{') {
                            depth++
                        } else if (ch == '}') {
                            depth--
                        }
                        j++
                    }
                    i = j
                } else {
                    i = j
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
