package com.example.woodsreader.core.pipeline

import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Regla de exclusión o reemplazo de palabras y frases.
 */
data class WordExclusionRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val target: String,
    val replacement: String = "",
    val isRegex: Boolean = false,
    val isCaseSensitive: Boolean = false,
    val bookId: String? = null // null indica regla global para todos los libros
)

/**
 * Motor de alto rendimiento para exclusión y sustitución de palabras en tiempo real.
 * Diseñado para procesar miles de palabras por segundo sin impacto en el hilo de UI.
 */
@Singleton
class WordExclusionEngine @Inject constructor() {

    /**
     * Aplica la lista de reglas sobre el texto recibido.
     * Si una palabra es eliminada (replacement vacío), normaliza los espacios resultantes.
     */
    fun filterText(text: String, rules: List<WordExclusionRule>, currentBookId: String? = null): String {
        if (text.isBlank() || rules.isEmpty()) return text

        var processed = text.replace('\u00A0', ' ')

        for (rule in rules) {
            if (rule.bookId != null && currentBookId != null && rule.bookId != currentBookId) continue
            val target = rule.target.replace('\u00A0', ' ').trim()
            if (target.isEmpty()) continue

            processed = if (rule.isRegex) {
                applyRegexRule(processed, target, rule.replacement, rule.isCaseSensitive)
            } else {
                applyExactWordRule(processed, target, rule.replacement, rule.isCaseSensitive)
            }
        }

        // Colapsar espacios múltiples resultantes de eliminaciones
        return processed
            .replace(Regex("""[ \t\u00A0]+"""), " ")
            .replace(Regex("""\s+([.,;:!?])"""), "$1")
            .trim()
    }

    private fun applyExactWordRule(
        source: String,
        target: String,
        replacement: String,
        caseSensitive: Boolean
    ): String {
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        val escaped = Pattern.quote(target)
        val prefix = if (target.first().isLetterOrDigit()) "\\b" else "(?<=^|\\s)"
        val suffix = if (target.last().isLetterOrDigit()) "\\b" else "(?=$|\\s|[.,;:!?])"
        val pattern = Pattern.compile("(?i)$prefix$escaped$suffix", flags)
        return pattern.matcher(source).replaceAll(replacement)
    }

    private fun applyRegexRule(
        source: String,
        regexString: String,
        replacement: String,
        caseSensitive: Boolean
    ): String {
        return try {
            val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            val pattern = Pattern.compile(regexString, flags)
            pattern.matcher(source).replaceAll(replacement)
        } catch (_: Exception) {
            source // Si la expresión regular es inválida, no modifica el texto original
        }
    }

    /**
     * Crea una regla a partir de una palabra o frase seleccionada en la pantalla de lectura.
     */
    fun createRuleFromSelection(
        selectedText: String,
        bookId: String? = null,
        isGlobal: Boolean = true
    ): WordExclusionRule {
        return WordExclusionRule(
            target = selectedText.trim(),
            replacement = "",
            isRegex = false,
            isCaseSensitive = false,
            bookId = if (isGlobal) null else bookId
        )
    }
}
