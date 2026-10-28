package com.example.woodsreader.core.translation.calibre.inplace

import com.example.woodsreader.core.translation.calibre.Placeholders
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

object XhtmlTranslatorDoc {

    private val tagRegex = Regex("""<[^>]+>""")

    private data class ElementMask(
        val element: Element,
        val originalInnerHtml: String,
        val maskedText: String,
        val tokenToTag: Map<String, Pair<Int, String>> // token -> (index, originalTag)
    )

    /**
     * Traduce un documento XHTML preservando su estructura XML, estilos, imágenes y etiquetas inline.
     */
    suspend fun translate(
        xhtmlContent: String,
        translator: TextTranslator,
        sourceLang: String = "en",
        targetLang: String = "es"
    ): String {
        val doc = Jsoup.parse(xhtmlContent, "", Parser.xmlParser())
        doc.outputSettings()
            .prettyPrint(false)
            .syntax(Document.OutputSettings.Syntax.xml)
            .charset(Charsets.UTF_8)

        // Actualizar atributos de idioma en <html>
        val htmlEl = doc.selectFirst("html")
        if (htmlEl != null) {
            htmlEl.attr("xml:lang", targetLang)
            htmlEl.attr("lang", targetLang)
        }

        // Traducir <title> si existe
        val titleEl = doc.selectFirst("head > title, title")
        val originalTitle = titleEl?.text()?.trim().orEmpty()

        // 1. Encontrar elementos de texto terminales (párrafos, títulos, listas)
        val allBlocks = doc.select("p, h1, h2, h3, h4, h5, h6, blockquote, li, dd, dt, td, th, figcaption")
        val leafBlocks = allBlocks.filter { el ->
            el.children().select("p, h1, h2, h3, h4, h5, h6, blockquote, li, dd, dt, td, th, figcaption").isEmpty()
        }

        val masks = mutableListOf<ElementMask>()
        val textsToTranslate = mutableListOf<String>()

        if (originalTitle.isNotBlank()) {
            textsToTranslate.add(originalTitle)
        }

        for (el in leafBlocks) {
            val innerHtml = el.html().trim()
            if (innerHtml.isBlank()) continue

            // Reemplazar cada etiqueta HTML con un marcador {{id_NNNNN}}
            var tokenIdx = 0
            val tokenMap = mutableMapOf<String, Pair<Int, String>>()

            val masked = tagRegex.replace(innerHtml) { matchResult ->
                val id = tokenIdx++
                val token = Placeholders.token(id, 5)
                tokenMap[token] = Pair(id, matchResult.value)
                token
            }

            // Si tras enmascarar no hay texto alfanumérico legible, omitir
            if (masked.replace(Regex("""\{\{id_\d+\}\}"""), "").trim().isBlank()) {
                continue
            }

            masks.add(ElementMask(el, innerHtml, masked, tokenMap))
            textsToTranslate.add(masked)
        }

        if (textsToTranslate.isEmpty()) {
            return doc.outerHtml()
        }

        // 2. Traducir en lotes
        val translatedBatch = translator.translateBatch(textsToTranslate, sourceLang, targetLang)

        var batchIdx = 0
        if (originalTitle.isNotBlank() && titleEl != null) {
            val translatedTitle = translatedBatch[batchIdx++]
            if (translatedTitle.isNotBlank()) {
                titleEl.text(translatedTitle)
            }
        }

        // 3. Desenmascarar y reinsertar en el DOM
        for (mask in masks) {
            val translated = translatedBatch[batchIdx++]
            val restoredHtml = restoreTokens(translated, mask.tokenToTag)

            if (restoredHtml != null) {
                mask.element.html(restoredHtml)
            } else {
                // Si la traducción corrompió marcadores, conservar el HTML original seguro
                mask.element.html(mask.originalInnerHtml)
            }
        }

        return doc.outerHtml()
    }

    private fun restoreTokens(
        translatedText: String,
        tokenMap: Map<String, Pair<Int, String>>
    ): String? {
        if (tokenMap.isEmpty()) return translatedText

        var result = translatedText
        for ((_, pair) in tokenMap) {
            val (id, originalTag) = pair
            val regex = Placeholders.regex(id, 5)
            if (!regex.containsMatchIn(result)) {
                // Se perdió un marcador importante durante la traducción
                return null
            }
            result = regex.replace(result, originalTag)
        }
        return result
    }
}
