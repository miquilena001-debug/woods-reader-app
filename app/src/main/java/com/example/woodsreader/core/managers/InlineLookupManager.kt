package com.example.woodsreader.core.managers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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

    suspend fun translateToSpanish(text: String): LookupResult = withContext(Dispatchers.IO) {
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return@withContext LookupResult.Error("Traducción", "Texto vacío")

        runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&dt=bd&q=$encoded"
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

                val detectedLang = rootArray.optString(2, "auto").uppercase()
                val dictionaryBuilder = StringBuilder()
                val dictArray = rootArray.optJSONArray(1)
                if (dictArray != null && dictArray.length() > 0) {
                    for (i in 0 until minOf(dictArray.length(), 2)) {
                        val entry = dictArray.optJSONArray(i) ?: continue
                        val pos = entry.optString(0)
                        val terms = entry.optJSONArray(1)
                        if (terms != null && terms.length() > 0) {
                            val termList = (0 until minOf(terms.length(), 4)).mapNotNull { terms.optString(it) }
                            if (termList.isNotEmpty()) {
                                if (dictionaryBuilder.isNotEmpty()) dictionaryBuilder.append("\n")
                                dictionaryBuilder.append("• $pos: ${termList.joinToString(", ")}")
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
                        secondaryDetail = dictionaryBuilder.toString().takeIf { it.isNotBlank() }
                    )
                }
            }
        }.getOrElse {
            LookupResult.Error("Traducción", "Sin conexión a internet para traducir en línea")
        }
    }

    suspend fun defineTerm(text: String): LookupResult = withContext(Dispatchers.IO) {
        val cleanWord = text.trim().split(Regex("""\s+""")).firstOrNull()
            ?.trim('.', ',', ';', ':', '!', '?', '¡', '¿', '"', '«', '»', '“', '”', '(', ')')
            .orEmpty()

        if (cleanWord.isEmpty()) return@withContext LookupResult.Error("Definición", "Selecciona una palabra válida")

        runCatching {
            val encoded = URLEncoder.encode(cleanWord.lowercase(), "UTF-8")
            val wikiUrl = "https://es.wiktionary.org/api/rest_v1/page/definition/$encoded"
            val request = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", "WoodsReader/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val esArray = json.optJSONArray("es") ?: json.optJSONArray(json.keys().asSequence().firstOrNull() ?: "")
                    if (esArray != null && esArray.length() > 0) {
                        val firstSection = esArray.optJSONObject(0)
                        val partOfSpeech = firstSection?.optString("partOfSpeech").orEmpty()
                        val definitions = firstSection?.optJSONArray("definitions")
                        val defsText = mutableListOf<String>()
                        if (definitions != null) {
                            for (i in 0 until minOf(definitions.length(), 3)) {
                                val rawDef = definitions.optJSONObject(i)?.optString("definition").orEmpty()
                                val stripped = rawDef.replace(Regex("<[^>]*>"), "").trim()
                                if (stripped.isNotBlank()) defsText.add("${i + 1}. $stripped")
                            }
                        }
                        if (defsText.isNotEmpty()) {
                            return@withContext LookupResult.Success(
                                modeTitle = "Diccionario · $cleanWord" + if (partOfSpeech.isNotBlank()) " ($partOfSpeech)" else "",
                                originalText = cleanWord,
                                primaryResult = defsText.joinToString("\n")
                            )
                        }
                    }
                }
            }
            // Fallback automático: si no está en Wiktionary ES, usar el diccionario léxico del traductor
            translateToSpanish(cleanWord)
        }.getOrElse {
            LookupResult.Error("Definición", "Sin conexión para consultar el diccionario")
        }
    }
}
