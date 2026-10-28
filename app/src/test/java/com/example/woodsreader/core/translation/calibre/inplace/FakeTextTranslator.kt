package com.example.woodsreader.core.translation.calibre.inplace

import java.io.IOException

/**
 * Traductor falso para pruebas unitarias sin red.
 * Conserva intactos los marcadores {{id_NNNNN}}.
 */
class FakeTextTranslator(
    var prefix: String = "[ES] ",
    var shouldDropMarker: Boolean = false,
    var shouldThrowIoException: Boolean = false
    // Nota: opción (c) TranslationBlockedException se habilitará en TR-05 cuando se defina dicha excepción.
) : TextTranslator {

    var callCount: Int = 0
        private set

    private val markerRegex = Regex("""(\{\{\s*)+id\s*_\s*\d+\s*(\s*\}\})+""")

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String,
        targetLang: String
    ): List<String> {
        callCount++
        if (shouldThrowIoException) {
            throw IOException("Error de red simulado en FakeTextTranslator")
        }
        return texts.map { text ->
            var translated = "$prefix$text"
            if (shouldDropMarker) {
                // Borrar el primer marcador para simular pérdida por parte del traductor
                translated = markerRegex.replaceFirst(translated, "")
            }
            translated
        }
    }
}
