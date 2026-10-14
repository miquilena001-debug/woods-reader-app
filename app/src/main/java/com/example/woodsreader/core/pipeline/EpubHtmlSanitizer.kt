package com.example.woodsreader.core.pipeline

import java.nio.charset.Charset

/**
 * Sanitizador automático de recursos HTML / XHTML para publicaciones EPUB.
 *
 * Corrige fallos comunes en EPUBs provenientes de scrapers web o herramientas de conversión:
 * 1. Falta de atributo `xmlns="http://www.w3.org/1999/xhtml"` en la etiqueta `<html>`:
 *    Al servirse como `application/xhtml+xml`, Blink/Chromium interpreta el archivo como XML
 *    genérico en un espacio de nombres nulo y activa su visor de depuración XML interno,
 *    mostrando etiquetas como `<p>...</p>` en color púrpura y texto sin formato ni estilos CSS.
 * 2. Ausencia de etiqueta `<head>` o `</head>`:
 *    Impide que el inyector de Readium agregue las hojas de estilo Readium CSS y scripts de navegación.
 * 3. Fragmentos sin etiqueta raíz `<html>`:
 *    Se envuelven en una estructura de documento XHTML estándar bien formada.
 * 4. Inyección del nombre del capítulo en `<h1 class="woods-reader-chapter-title">` al inicio del `<body>`
 *    cuando el documento carece de encabezado inicial.
 */
object EpubHtmlSanitizer {

    private val HTML_OPEN_TAG_REGEX = Regex("""<html(\s+[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val HTML_ANY_TAG_REGEX = Regex("""<html[^>]*>""", RegexOption.IGNORE_CASE)
    private val DEFAULT_XMLNS_REGEX = Regex("""(?:^|\s)xmlns\s*=""", RegexOption.IGNORE_CASE)
    private val XML_DECLARATION_REGEX = Regex("""^\uFEFF?\s*<\?xml\b.*?\?>\s*""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val DOCTYPE_REGEX = Regex("""^\s*<!DOCTYPE\b.*?>\s*""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val XML_ENCODING_REGEX = Regex("""<\?xml[^>]+encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val META_CHARSET_REGEX = Regex("""<meta[^>]+charset=["']?([^"'\s;>]+)""", RegexOption.IGNORE_CASE)

    fun sanitize(html: String, chapterTitle: String? = null): String {
        if (html.isBlank()) return html

        var result = html
        val match = HTML_OPEN_TAG_REGEX.find(result)
        if (match != null) {
            val fullTag = match.value
            if (!DEFAULT_XMLNS_REGEX.containsMatchIn(fullTag)) {
                val attributes = match.groupValues[1]
                val newTag = if (attributes.isBlank()) {
                    """<html xmlns="http://www.w3.org/1999/xhtml">"""
                } else {
                    """<html xmlns="http://www.w3.org/1999/xhtml"$attributes>"""
                }
                result = result.replaceRange(match.range, newTag)
            }
        } else {
            // El fragmento carece de <html>: envolver en estructura XHTML válida
            val fragment = html
                .replaceFirst(XML_DECLARATION_REGEX, "")
                .replaceFirst(DOCTYPE_REGEX, "")
            result = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title></title></head>
<body>
$fragment
</body>
</html>"""
        }

        // Asegurar que exista </head> para inyección de estilos y scripts de Readium CSS
        if (!result.contains("</head>", ignoreCase = true)) {
            val bodyIndex = result.indexOf("<body", ignoreCase = true)
            result = if (bodyIndex != -1) {
                result.substring(0, bodyIndex) + "<head></head>\n" + result.substring(bodyIndex)
            } else {
                val htmlMatch = HTML_ANY_TAG_REGEX.find(result)
                if (htmlMatch != null) {
                    result.substring(0, htmlMatch.range.last + 1) + "\n<head></head>" + result.substring(htmlMatch.range.last + 1)
                } else {
                    result
                }
            }
        }

        return chapterTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { addChapterHeading(result, it) }
            ?: result
    }

    fun encodeSanitized(original: String, sanitized: String, originalCharset: Charset): ByteArray {
        val outputCharset = if (HTML_OPEN_TAG_REGEX.containsMatchIn(original)) {
            originalCharset
        } else {
            Charsets.UTF_8
        }
        return sanitized.toByteArray(outputCharset)
    }

    private fun addChapterHeading(html: String, title: String): String {
        val bodyMatch = Regex("""<body\b[^>]*>""", RegexOption.IGNORE_CASE).find(html) ?: return html
        val bodyContent = html.substring(bodyMatch.range.last + 1)
        val firstContent = bodyContent.replaceFirst(Regex("""^(?:\s|<!--.*?-->)*""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        if (Regex("""^<(?:h[1-6]|header)\b""", RegexOption.IGNORE_CASE).containsMatchIn(firstContent)) {
            return html
        }

        val safeTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        val bodySample = bodyContent.take(1500)
        if (bodySample.contains(safeTitle, ignoreCase = true) || bodySample.contains(title, ignoreCase = true)) {
            return html
        }

        var result = html
        if (!result.contains("woods-reader-chapter-title", ignoreCase = true)) {
            val styleBlock = """<style type="text/css">
.woods-reader-chapter-title {
  font-size: 1.35em !important;
  font-weight: 700 !important;
  margin-top: 1.2em !important;
  margin-bottom: 1.2em !important;
  text-align: center !important;
  line-height: 1.3 !important;
  page-break-after: avoid !important;
  break-after: avoid !important;
}
</style>"""
            val headEnd = result.indexOf("</head>", ignoreCase = true)
            if (headEnd != -1) {
                result = result.substring(0, headEnd) + styleBlock + "\n" + result.substring(headEnd)
            }
        }

        val recheckedBodyMatch = Regex("""<body\b[^>]*>""", RegexOption.IGNORE_CASE).find(result) ?: return result
        val heading = """<h1 class="woods-reader-chapter-title">$safeTitle</h1>"""
        val insertAt = recheckedBodyMatch.range.last + 1
        return result.substring(0, insertAt) + "\n$heading\n" + result.substring(insertAt)
    }

    /**
     * Detecta el juego de caracteres a partir de la declaración XML o etiqueta <meta charset>.
     * Por defecto retorna UTF-8 si no se detecta otra codificación válida.
     */
    fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.isEmpty()) return Charsets.UTF_8
        val sampleLength = minOf(bytes.size, 512)
        val headerSample = String(bytes, 0, sampleLength, Charsets.ISO_8859_1)

        val xmlMatch = XML_ENCODING_REGEX.find(headerSample)
        if (xmlMatch != null) {
            try {
                return Charset.forName(xmlMatch.groupValues[1])
            } catch (_: Exception) {}
        }

        val metaMatch = META_CHARSET_REGEX.find(headerSample)
        if (metaMatch != null) {
            try {
                return Charset.forName(metaMatch.groupValues[1])
            } catch (_: Exception) {}
        }

        return Charsets.UTF_8
    }
}
