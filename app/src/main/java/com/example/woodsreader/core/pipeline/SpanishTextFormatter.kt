package com.example.woodsreader.core.pipeline

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formateador tipográfico avanzado especializado en idioma español.
 * Evita preposiciones huérfanas al final de línea y prepara el texto para justificación editorial.
 */
@Singleton
class SpanishTextFormatter @Inject constructor() {

    companion object {
        private const val NON_BREAKING_SPACE = '\u00A0'
        private const val SOFT_HYPHEN = '\u00AD'

        // Monosílabos y preposiciones comunes que nunca deben quedar solas al final de línea
        private val ORPHAN_WORDS = setOf(
            "a", "e", "i", "o", "u", "y",
            "de", "en", "con", "por", "para", "sin", "sobre", "tras", "hacia",
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "al", "del", "ni", "si", "no", "que", "se"
        )
    }

    /**
     * Vincula preposiciones y artículos con la palabra siguiente mediante un espacio de no separación,
     * evitando que queden aisladas al final de la línea.
     */
    fun bindOrphanPrepositions(text: String): String {
        if (text.isBlank()) return text

        val words = text.split(" ")
        if (words.size <= 1) return text

        val builder = StringBuilder()
        for (i in words.indices) {
            val word = words[i]
            builder.append(word)

            if (i < words.size - 1) {
                val cleanWord = word.lowercase().trimEnd('.', ',', ';', ':', '!', '?', ')', '»', '"')
                if (ORPHAN_WORDS.contains(cleanWord)) {
                    builder.append(NON_BREAKING_SPACE)
                } else {
                    builder.append(' ')
                }
            }
        }

        return builder.toString()
    }

    /**
     * Normaliza comillas angulares latinas (« »), guiones largos de diálogo (—)
     * y signos dobles españoles de interrogación y exclamación (¿ ?, ¡ !).
     */
    fun normalizeTypography(text: String): String {
        return text
            .replace(Regex("""--"""), "—")
            .replace(Regex("""¿\s+"""), "¿")
            .replace(Regex("""¡\s+"""), "¡")
            .replace(Regex("""«\s+"""), "«")
            .replace(Regex("""\s+»"""), "»")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Formatea un párrafo completo aplicando todas las reglas tipográficas en español.
     */
    fun formatParagraph(text: String): String {
        val normalized = normalizeTypography(text)
        return bindOrphanPrepositions(normalized)
    }
}
