package com.example.woodsreader.core.managers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LookupResult {
    data object Idle : LookupResult
    data class Loading(val modeTitle: String) : LookupResult
    data class Success(
        val modeTitle: String,
        val originalText: String,
        val primaryResult: String,
        val secondaryDetail: String? = null
    ) : LookupResult
    data class Error(val modeTitle: String, val message: String) : LookupResult
}

@Singleton
class InlineLookupManager @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun translatePartOfSpeech(pos: String): String {
        return when (pos.lowercase(Locale.ROOT).trim()) {
            "noun" -> "Sustantivo"
            "verb" -> "Verbo"
            "adjective" -> "Adjetivo"
            "adverb" -> "Adverbio"
            "pronoun" -> "Pronombre"
            "preposition" -> "Preposición"
            "conjunction" -> "Conjunción"
            "interjection" -> "Interjección"
            "phrase" -> "Expresión"
            "abbreviation" -> "Abreviatura"
            else -> pos.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    suspend fun translateToSpanish(text: String): LookupResult = withContext(Dispatchers.IO) {
        val clean = text.trim()
            .trim('"', '“', '”', '«', '»', '\'')
            .take(500)
        if (clean.isEmpty()) return@withContext LookupResult.Error("Traducción", "Texto vacío")

        runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&dt=bd&dt=at&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LookupResult.Error("Traducción", "Sin conexión (${response.code})")
                }
                val bodyStr = response.body?.string().orEmpty()
                val rootArray = JSONArray(bodyStr)
                val sentencesArray = rootArray.optJSONArray(0)
                val translatedBuilder = StringBuilder()

                if (sentencesArray != null) {
                    for (i in 0 until sentencesArray.length()) {
                        val seg = sentencesArray.optJSONArray(i)
                        val part = seg?.optString(0).orEmpty()
                        if (part.isNotEmpty() && part != "null") {
                            translatedBuilder.append(part)
                        }
                    }
                }

                val detectedLang = rootArray.optString(2, "auto").uppercase(Locale.ROOT)
                val detailsBuilder = StringBuilder()

                // 1. Variantes / Traducciones alternativas para frases o términos compuestos (dt=at)
                val altArray = rootArray.optJSONArray(5)
                if (altArray != null && altArray.length() > 0) {
                    val alternatives = mutableSetOf<String>()
                    val mainLower = translatedBuilder.toString().trim().lowercase(Locale.ROOT)
                    for (i in 0 until altArray.length()) {
                        val group = altArray.optJSONArray(i)?.optJSONArray(2) ?: continue
                        for (j in 0 until minOf(group.length(), 4)) {
                            val alt = group.optJSONArray(j)?.optString(0)?.trim().orEmpty()
                            if (alt.isNotBlank() && alt.lowercase(Locale.ROOT) != mainLower) {
                                alternatives.add(alt)
                            }
                        }
                    }
                    if (alternatives.isNotEmpty()) {
                        detailsBuilder.append("Alternativas: ${alternatives.take(4).joinToString(" / ")}")
                    }
                }

                // 2. Diccionario gramatical con etiquetas en español (dt=bd)
                val dictArray = rootArray.optJSONArray(1)
                if (dictArray != null && dictArray.length() > 0) {
                    for (i in 0 until minOf(dictArray.length(), 3)) {
                        val entry = dictArray.optJSONArray(i) ?: continue
                        val posEs = translatePartOfSpeech(entry.optString(0))
                        val terms = entry.optJSONArray(1)
                        if (terms != null && terms.length() > 0) {
                            val termList = (0 until minOf(terms.length(), 4)).mapNotNull { terms.optString(it) }
                            if (termList.isNotEmpty()) {
                                if (detailsBuilder.isNotEmpty()) detailsBuilder.append("\n")
                                detailsBuilder.append("• $posEs: ${termList.joinToString(", ")}")
                            }
                        }
                    }
                }

                val mainTranslation = translatedBuilder.toString().trim()
                if (mainTranslation.isBlank()) {
                    LookupResult.Error("Traducción", "No se encontró traducción")
                } else {
                    LookupResult.Success(
                        modeTitle = "Traducción ($detectedLang → ES)",
                        originalText = clean,
                        primaryResult = mainTranslation,
                        secondaryDetail = detailsBuilder.toString().takeIf { it.isNotBlank() }
                    )
                }
            }
        }.getOrElse {
            LookupResult.Error("Traducción", "Sin conexión a internet para traducir")
        }
    }

    suspend fun defineTerm(text: String): LookupResult = withContext(Dispatchers.IO) {
        val rawClean = text.trim()
            .trim('.', ',', ';', ':', '!', '?', '¡', '¿', '"', '«', '»', '“', '”', '(', ')', '\'')
            .replace(Regex("""\s+"""), " ")

        if (rawClean.isEmpty()) {
            return@withContext LookupResult.Error("Definición", "Selecciona una palabra o término válido")
        }

        runCatching {
            // 1. Consultar primero traducción y detección de idioma del término completo
            val encodedRaw = URLEncoder.encode(rawClean, "UTF-8")
            val gtxUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&dt=bd&dt=md&dt=ex&q=$encodedRaw"
            val gtxReq = Request.Builder().url(gtxUrl).header("User-Agent", "Mozilla/5.0").build()

            var detectedLang = "es"
            var spanishEquivalent = rawClean
            val gtxDefinitions = mutableListOf<String>()
            val gtxExamples = mutableListOf<String>()

            client.newCall(gtxReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONArray(resp.body?.string().orEmpty())
                    detectedLang = root.optString(2, "es").lowercase(Locale.ROOT)
                    val sents = root.optJSONArray(0)
                    val sb = StringBuilder()
                    if (sents != null) {
                        for (i in 0 until sents.length()) {
                            val p = sents.optJSONArray(i)?.optString(0).orEmpty()
                            if (p.isNotEmpty() && p != "null") sb.append(p)
                        }
                    }
                    if (sb.isNotBlank()) spanishEquivalent = sb.toString().trim()

                    // Extraer definiciones semánticas si vienen en dt=md (índice 12)
                    val defsRoot = root.optJSONArray(12)
                    if (defsRoot != null) {
                        for (i in 0 until minOf(defsRoot.length(), 2)) {
                            val posGroup = defsRoot.optJSONArray(i) ?: continue
                            val posEs = translatePartOfSpeech(posGroup.optString(0))
                            val entries = posGroup.optJSONArray(1) ?: continue
                            for (j in 0 until minOf(entries.length(), 2)) {
                                val defObj = entries.optJSONArray(j) ?: continue
                                val gloss = defObj.optString(0).trim()
                                val example = defObj.optString(2).trim()
                                if (gloss.isNotBlank()) {
                                    gtxDefinitions.add("[$posEs] $gloss")
                                }
                                if (example.isNotBlank() && gtxExamples.size < 2) {
                                    gtxExamples.add("«$example»")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Intentar obtener definición en español desde Wiktionary ES (para el término o su equivalente en español)
            val termsToTryInSpanishWiki = linkedSetOf(
                rawClean.lowercase(Locale.ROOT),
                spanishEquivalent.lowercase(Locale.ROOT),
                spanishEquivalent.lowercase(Locale.ROOT).split(" ").firstOrNull().orEmpty()
            ).filter { it.isNotBlank() }

            for (candidate in termsToTryInSpanishWiki) {
                val wikiDefs = fetchSpanishWiktionaryDefinitions(candidate)
                if (wikiDefs.isNotEmpty()) {
                    val headerTerm = if (!rawClean.equals(spanishEquivalent, ignoreCase = true)) {
                        "$rawClean → $spanishEquivalent"
                    } else {
                        rawClean
                    }
                    val secondary = if (candidate != spanishEquivalent.lowercase(Locale.ROOT) && rawClean.contains(" ")) {
                        "Término compuesto: «$spanishEquivalent» (definición base de «$candidate»)"
                    } else {
                        gtxExamples.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    }
                    return@withContext LookupResult.Success(
                        modeTitle = "Definición · $headerTerm",
                        originalText = rawClean,
                        primaryResult = wikiDefs.joinToString("\n"),
                        secondaryDetail = secondary
                    )
                }
            }

            // 3. Si el término es en inglés u otro idioma y Wiktionary ES no lo tuvo, traducir las definiciones obtenidas al español
            if (gtxDefinitions.isNotEmpty()) {
                val rawDefsJoined = gtxDefinitions.joinToString("\n")
                val defsInSpanish = if (detectedLang != "es") {
                    translatePlainTextToSpanish(rawDefsJoined)
                } else {
                    rawDefsJoined
                }
                val headerTerm = if (!rawClean.equals(spanishEquivalent, ignoreCase = true)) {
                    "$rawClean ($spanishEquivalent)"
                } else {
                    rawClean
                }
                return@withContext LookupResult.Success(
                    modeTitle = "Definición · $headerTerm",
                    originalText = rawClean,
                    primaryResult = defsInSpanish,
                    secondaryDetail = gtxExamples.takeIf { it.isNotEmpty() }?.joinToString("\n")
                )
            }

            // 4. Fallback explicativo para términos compuestos de novelas (ej. "Fire Fish")
            val words = rawClean.split(" ").filter { it.length > 1 }
            if (words.size > 1) {
                val wordBreakdown = words.mapNotNull { w ->
                    val tr = translatePlainTextToSpanish(w)
                    if (tr.isNotBlank()) "• $w: $tr" else null
                }
                return@withContext LookupResult.Success(
                    modeTitle = "Definición · $rawClean",
                    originalText = rawClean,
                    primaryResult = "Concepto / término compuesto: «$spanishEquivalent».",
                    secondaryDetail = wordBreakdown.takeIf { it.isNotEmpty() }?.joinToString("\n")
                )
            }

            LookupResult.Success(
                modeTitle = "Definición · $rawClean",
                originalText = rawClean,
                primaryResult = spanishEquivalent
            )
        }.getOrElse {
            LookupResult.Error("Definición", "Sin conexión para consultar el diccionario")
        }
    }

    private fun fetchSpanishWiktionaryDefinitions(term: String): List<String> {
        return runCatching {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val wikiUrl = "https://es.wiktionary.org/api/rest_v1/page/definition/$encoded"
            val request = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", "WoodsReader/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val json = JSONObject(response.body?.string().orEmpty())
                val esArray = json.optJSONArray("es") ?: return emptyList()
                val results = mutableListOf<String>()
                for (s in 0 until minOf(esArray.length(), 2)) {
                    val section = esArray.optJSONObject(s) ?: continue
                    val pos = section.optString("partOfSpeech").trim()
                    val defs = section.optJSONArray("definitions") ?: continue
                    for (i in 0 until minOf(defs.length(), 3)) {
                        val rawDef = defs.optJSONObject(i)?.optString("definition").orEmpty()
                        val stripped = rawDef
                            .replace(Regex("<[^>]*>"), "")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                        if (stripped.isNotBlank() && results.size < 3) {
                            val prefix = if (pos.isNotBlank()) "[$pos] " else ""
                            results.add("${results.size + 1}. $prefix$stripped")
                        }
                    }
                }
                results
            }
        }.getOrDefault(emptyList())
    }

    private fun translatePlainTextToSpanish(text: String): String {
        return runCatching {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&q=$encoded"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return text
                val sents = JSONArray(resp.body?.string().orEmpty()).optJSONArray(0) ?: return text
                val sb = StringBuilder()
                for (i in 0 until sents.length()) {
                    val p = sents.optJSONArray(i)?.optString(0).orEmpty()
                    if (p.isNotEmpty() && p != "null") sb.append(p)
                }
                sb.toString().trim().ifBlank { text }
            }
        }.getOrDefault(text)
    }
}
