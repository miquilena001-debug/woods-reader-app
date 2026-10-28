package com.example.woodsreader.core.translation.calibre.inplace

interface TextTranslator {
    /** Devuelve una lista del MISMO tamaño y orden que [texts]. Puede lanzar IOException. */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String = "en",
        targetLang: String = "es"
    ): List<String>
}
