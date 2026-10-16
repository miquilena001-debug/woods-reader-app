package com.example.woodsreader.core.pipeline

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clasificador determinista y de alto rendimiento de ruido textual en libros electrónicos (EPUB y PDF).
 * Detecta números de página, encabezados/pies recurrentes, URLs, fórmulas legales y ornamentos.
 */
@Singleton
class NoiseClassifier @Inject constructor() {

    companion object {
        private val PAGE_NUMBER_REGEX = Regex(
            """^(?:[-—–\s]*p[áa]g(?:ina)?\.?\s*\d+(?:\s*(?:de|\/)\s*\d+)?[-—–\s]*|\d+(?:\s*(?:de|\/)\s*\d+)?|[-—–\s]*\d+[-—–\s]*|\[\d+\]|\(\d+\))$""",
            RegexOption.IGNORE_CASE
        )

        private val ROMAN_NUMERAL_REGEX = Regex(
            """^[-—–\s]*(?:[ivxlcdm]+)[-—–\s]*$""",
            RegexOption.IGNORE_CASE
        )

        private val URL_OR_EMAIL_REGEX = Regex(
            """(?:https?://\S+|www\.\S+|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""",
            RegexOption.IGNORE_CASE
        )

        private val ORNAMENT_REGEX = Regex(
            """^[*\-—–•_~§#=\s]+$"""
        )

        private val LEGAL_KEYWORDS = listOf(
            "todos los derechos reservados",
            "all rights reserved",
            "depósito legal",
            "deposito legal",
            "isbn",
            "issn",
            "d.r. ©",
            "impreso en",
            "printed in",
            "primera edición",
            "primera edicion",
            "licencia creative commons",
            "registro de propiedad intelectual"
        )

        private val WEB_NOVEL_SPAM_KEYWORDS = listOf(
            "lee capítulos adelantados en patreon",
            "capítulos adelantados en patreon",
            "apóyanos en patreon",
            "únete a nuestro discord",
            "unete a nuestro discord",
            "traducido por",
            "traducción al español por",
            " visita nuestra web ",
            "lee gratis en",
            "novelas ligeras en español",
            "suscríbete a nuestro canal",
            "nota del traductor:",
            "n/t:"
        )
    }

    /**
     * Determina si una cadena es un número de página (arábigo, romano, o formateado como "Pág 4 de 100").
     */
    fun isPageNumber(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.length > 25) return false

        if (PAGE_NUMBER_REGEX.matches(trimmed)) return true
        if (trimmed.length <= 8 && ROMAN_NUMERAL_REGEX.matches(trimmed)) return true

        return false
    }

    /**
     * Determina si una cadena contiene principalmente URLs o correos electrónicos aislados.
     */
    fun isUrlOrContact(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val matches = URL_OR_EMAIL_REGEX.findAll(trimmed).toList()
        if (matches.isEmpty()) return false

        val matchedChars = matches.sumOf { it.value.length }
        return matchedChars.toFloat() / trimmed.length.toFloat() > 0.6f
    }

    /**
     * Determina si una cadena es un aviso legal, copyright o registro editorial.
     */
    fun isLegalBoilerplate(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty()) return false

        return LEGAL_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    /**
     * Detecta marcas de agua de scrapers, anuncios de Patreon/Discord y notas intrusivas en novelas web.
     */
    fun isTranslatorSpam(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty() || lower.length > 220) return false
        return WEB_NOVEL_SPAM_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    /**
     * Determina si una cadena es un ornamento visual o separador (e.g., "* * *", "---", "•••").
     */
    fun isIsolatedOrnament(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.length <= 15 && ORNAMENT_REGEX.matches(trimmed)
    }

    /**
     * Analiza las primeras y últimas líneas de cada página de un documento para extraer
     * encabezados o pies recurrentes (que aparecen en 3 o más páginas con el mismo texto).
     */
    fun detectRepetitiveHeadersFooters(pageTopAndBottomLines: List<Pair<String?, String?>>): Set<String> {
        if (pageTopAndBottomLines.size < 3) return emptySet()

        val headerFrequency = mutableMapOf<String, Int>()
        val footerFrequency = mutableMapOf<String, Int>()

        for ((top, bottom) in pageTopAndBottomLines) {
            top?.trim()?.takeIf { it.isNotBlank() && !isPageNumber(it) }?.let {
                val normalized = normalizeHeaderText(it)
                headerFrequency[normalized] = (headerFrequency[normalized] ?: 0) + 1
            }

            bottom?.trim()?.takeIf { it.isNotBlank() && !isPageNumber(it) }?.let {
                val normalized = normalizeHeaderText(it)
                footerFrequency[normalized] = (footerFrequency[normalized] ?: 0) + 1
            }
        }

        val repetitive = mutableSetOf<String>()
        val threshold = minOf(3, maxOf(2, (pageTopAndBottomLines.size * 0.25).toInt()))

        headerFrequency.filter { it.value >= threshold }.forEach { repetitive.add(it.key) }
        footerFrequency.filter { it.value >= threshold }.forEach { repetitive.add(it.key) }

        return repetitive
    }

    /**
     * Clasifica un bloque de texto y determina si es ruido y de qué tipo.
     * Retorna null si el texto es contenido legítimo de lectura.
     */
    fun classify(
        text: String,
        isFirstLineOfPage: Boolean = false,
        isLastLineOfPage: Boolean = false,
        repetitiveHeadersFooters: Set<String> = emptySet()
    ): NoiseReason? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        if (isPageNumber(trimmed)) {
            return NoiseReason.PAGE_NUMBER
        }

        if (isIsolatedOrnament(trimmed)) {
            return NoiseReason.ISOLATED_ORNAMENT
        }

        if (isUrlOrContact(trimmed)) {
            return NoiseReason.URL_OR_CONTACT
        }

        if (isLegalBoilerplate(trimmed)) {
            return NoiseReason.LEGAL_COPYRIGHT
        }

        if (isTranslatorSpam(trimmed)) {
            return NoiseReason.TRANSLATOR_SPAM
        }

        val normalized = normalizeHeaderText(trimmed)
        if (repetitiveHeadersFooters.contains(normalized)) {
            return if (isFirstLineOfPage) NoiseReason.RUNNING_HEADER else NoiseReason.RUNNING_FOOTER
        }

        return null
    }

    private fun normalizeHeaderText(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
