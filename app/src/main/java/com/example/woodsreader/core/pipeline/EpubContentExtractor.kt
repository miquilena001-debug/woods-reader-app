package com.example.woodsreader.core.pipeline

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptador de extracción semántica para contenido EPUB (HTML / XHTML).
 *
 * Parsea el documento DOM y lo convierte en una secuencia ordenada de [RawDocumentBlock],
 * preservando jerarquía de títulos, imágenes con pies/alt y marcadores de límites
 * de página/recurso requeridos por el [NoiseClassifier].
 */
@Singleton
class EpubContentExtractor @Inject constructor() {

    fun extractBlocks(
        htmlContent: String,
        resourceHref: String = "",
        pageIndex: Int = 0
    ): List<RawDocumentBlock> {
        if (htmlContent.isBlank()) return emptyList()

        val doc = Jsoup.parse(htmlContent)
        val body = doc.body() ?: return emptyList()

        val rawBlocks = mutableListOf<RawDocumentBlock>()
        var orderIndex = 0

        val candidateElements = body.select("h1, h2, h3, h4, h5, h6, p, figure, img, blockquote, hr, li")
        val processedElements = mutableSetOf<Element>()

        for (element in candidateElements) {
            if (element in processedElements) continue
            if (element.parents().any { it in processedElements }) continue

            val tag = element.tagName().lowercase(Locale.ROOT)
            when (tag) {
                "figure" -> {
                    processedElements.add(element)
                    val img = element.selectFirst("img")
                    val figcaption = element.selectFirst("figcaption")
                    val caption = figcaption?.text()?.trim() ?: img?.attr("alt")?.trim()
                    val src = img?.attr("src")?.trim()

                    if (img != null || !caption.isNullOrBlank()) {
                        rawBlocks.add(
                            RawDocumentBlock(
                                id = "$resourceHref-fig-${orderIndex++}",
                                text = caption ?: "",
                                isImage = true,
                                imageCaption = caption,
                                imageUri = src,
                                pageIndex = pageIndex,
                                orderIndex = rawBlocks.size,
                                fontSize = 12f
                            )
                        )
                    }
                }

                "img" -> {
                    processedElements.add(element)
                    val caption = element.attr("alt").trim().ifEmpty { null }
                    val src = element.attr("src").trim().ifEmpty { null }

                    rawBlocks.add(
                        RawDocumentBlock(
                            id = "$resourceHref-img-${orderIndex++}",
                            text = caption ?: "",
                            isImage = true,
                            imageCaption = caption,
                            imageUri = src,
                            pageIndex = pageIndex,
                            orderIndex = rawBlocks.size,
                            fontSize = 12f
                        )
                    )
                }

                "h1", "h2", "h3", "h4", "h5", "h6", "p", "blockquote", "li" -> {
                    val hasChildBlocks = element.children().select("h1, h2, h3, h4, h5, h6, p, figure, img, blockquote, li").isNotEmpty()
                    if (hasChildBlocks) {
                        // Elementos contenedores como blockquote o li que alojan párrafos u otros bloques
                        // no deben extraerse en bloque con .text() para no duplicar el contenido de sus hijos.
                        val directText = element.ownText().trim()
                        if (directText.isNotBlank()) {
                            rawBlocks.add(
                                RawDocumentBlock(
                                    id = "$resourceHref-blk-${orderIndex++}",
                                    text = directText,
                                    isImage = false,
                                    pageIndex = pageIndex,
                                    orderIndex = rawBlocks.size,
                                    fontSize = 12f
                                )
                            )
                        }
                        // Sus hijos serán procesados como sus propios bloques individuales
                        continue
                    }

                    processedElements.add(element)
                    val text = element.text().trim()
                    if (text.isNotBlank()) {
                        val fontSize = when (tag) {
                            "h1" -> 24f
                            "h2" -> 20f
                            "h3" -> 18f
                            "h4" -> 16f
                            "h5" -> 14f
                            "h6" -> 13f
                            else -> 12f
                        }

                        rawBlocks.add(
                            RawDocumentBlock(
                                id = "$resourceHref-blk-${orderIndex++}",
                                text = text,
                                isImage = false,
                                pageIndex = pageIndex,
                                orderIndex = rawBlocks.size,
                                fontSize = fontSize
                            )
                        )
                    }
                }
            }
        }

        // Si el documento carecía de etiquetas de bloque estándar pero contiene texto plano
        if (rawBlocks.isEmpty() && body.text().isNotBlank()) {
            val paragraphs = body.text().split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            for (p in paragraphs) {
                rawBlocks.add(
                    RawDocumentBlock(
                        id = "$resourceHref-raw-${orderIndex++}",
                        text = p,
                        isImage = false,
                        pageIndex = pageIndex,
                        orderIndex = rawBlocks.size,
                        fontSize = 12f
                    )
                )
            }
        }

        if (rawBlocks.isNotEmpty()) {
            val total = rawBlocks.size
            return rawBlocks.mapIndexed { idx, block ->
                block.copy(
                    isFirstLineOfPage = (idx == 0),
                    isLastLineOfPage = (idx == total - 1)
                )
            }
        }

        return rawBlocks
    }
}
