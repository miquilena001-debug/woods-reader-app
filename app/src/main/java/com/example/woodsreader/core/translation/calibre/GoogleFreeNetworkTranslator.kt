package com.example.woodsreader.core.translation.calibre

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import com.example.woodsreader.BuildConfig
import com.example.woodsreader.core.translation.calibre.inplace.TextTranslator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Marcadores {{id_00000}} tolerantes a espacios, igual que el plugin Ebook Translator de Calibre. */
object Placeholders {
    fun token(id: Int, width: Int): String = "{{id_${id.toString().padStart(width, '0')}}}"

    fun regex(id: Int, width: Int): Regex {
        val digits = id.toString().padStart(width, '0').toCharArray().joinToString("""\s*""")
        return Regex("""(\{\{\s*)+id\s*_\s*$digits\s*(\s*\}\})+""")
    }
}

internal enum class TranslationEngine { NEW_TEXT, LEGACY }

internal fun translationEngineOrder(apiKey: String): List<TranslationEngine> =
    if (apiKey.isBlank()) {
        listOf(TranslationEngine.LEGACY)
    } else {
        listOf(TranslationEngine.NEW_TEXT, TranslationEngine.LEGACY)
    }

/**
 * Réplica del motor "Google (Free)" del plugin Ebook Translator (Calibre): sin API ni claves de usuario.
 * La ruta autenticada nueva solo se habilita en debug cuando hay una clave local; release usa el
 * endpoint legacy porque toda clave incluida en un APK puede extraerse.
 * Cadena de endpoints: translate-pa (nuevo) -> translate_a/single (antiguo).
 * Modo "Merge to Translate": agrupa párrafos con separador "\n\n" y los realinea al volver.
 */
@Singleton
class GoogleFreeNetworkTranslator @Inject constructor(
    @ApplicationContext private val context: Context
) : TextTranslator {
    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private class Shielded(val text: String, val ids: List<Int>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val cache = LruCache<String, String>(2000)
    private var consecutiveErrors = 0
    private var lastRequestAt = 0L

    private val whitespace = Regex("""\s+""")
    private val multiNewline = Regex("""\n{2,}""")

    // Glosario (cultivo / Xianxia / Wuxia / LitRPG): [término, traducción]
    private val glossary: List<Pair<Regex, String>> = listOf(
        "Nascent Soul" to "Alma Naciente",
        "Golden Core" to "Núcleo Dorado",
        "Foundation Establishment" to "Establecimiento de la Fundación",
        "Qi Condensation" to "Condensación de Qi",
        "Heavenly Dao" to "Dao Celestial",
        "Spiritual Sense" to "Sentido Espiritual",
        "Storage Ring" to "Anillo Espacial",
        "Jade Slip" to "Talismán de Jade",
        "Young Master" to "Joven Maestro",
        "Martial Brother" to "Hermano Marcial",
        "Martial Sister" to "Hermana Marcial",
        "Status Window" to "Ventana de Estado",
        "Skill Points" to "Puntos de Habilidad",
        "Dungeon" to "Mazmorra",
        "Dantian" to "Dantian",
        "Jianghu" to "Jianghu"
    ).map { (en, es) -> Regex("""\b${Regex.escape(en)}\b""", RegexOption.IGNORE_CASE) to es }

    fun resetErrorCounter() {
        consecutiveErrors = 0
    }

    suspend fun translateText(
        text: String,
        sourceLang: String = "en",
        targetLang: String = "es"
    ): String = translateBatch(listOf(text), sourceLang, targetLang).first()

    /**
     * Traduce varios textos (ya con marcadores) en pocas peticiones. Devuelve una lista del mismo tamaño.
     * Lanza IOException si la red falla tras reintentos o tras demasiados errores seguidos.
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String,
        targetLang: String
    ): List<String> = withContext(Dispatchers.IO) {
        val result = arrayOfNulls<String>(texts.size)
        val useGlossary = targetLang.startsWith("es", ignoreCase = true)

        val pending = ArrayList<Pair<Int, String>>()
        for ((i, raw) in texts.withIndex()) {
            val clean = raw.replace(whitespace, " ").trim()
            if (clean.isEmpty() || clean.none { it.isLetter() }) {
                result[i] = raw
                continue
            }
            val hit = cache.get(cacheKey(sourceLang, targetLang, clean))
            if (hit != null) result[i] = hit else pending.add(i to clean)
        }

        // Agrupar por "peso" (ASCII = 1, resto = 3 por la codificación de URL)
        val groups = ArrayList<List<Pair<Int, String>>>()
        var current = ArrayList<Pair<Int, String>>()
        var currentWeight = 0
        for (item in pending) {
            val w = weight(item.second)
            if (w > MERGE_WEIGHT) {
                if (current.isNotEmpty()) { groups.add(current); current = ArrayList(); currentWeight = 0 }
                groups.add(listOf(item))
                continue
            }
            if (currentWeight + w + 2 > MERGE_WEIGHT && current.isNotEmpty()) {
                groups.add(current); current = ArrayList(); currentWeight = 0
            }
            current.add(item)
            currentWeight += w + 2
        }
        if (current.isNotEmpty()) groups.add(current)

        for (group in groups) {
            currentCoroutineContext().ensureActive()
            val outputs = translateGroup(group.map { it.second }, sourceLang, targetLang, useGlossary)
            for ((k, item) in group.withIndex()) {
                cache.put(cacheKey(sourceLang, targetLang, item.second), outputs[k])
                result[item.first] = outputs[k]
            }
        }

        result.mapIndexed { i, r -> r ?: texts[i] }
    }

    // ───── Agrupado / realineado ─────

    private suspend fun translateGroup(
        items: List<String>,
        sl: String,
        tl: String,
        useGlossary: Boolean
    ): List<String> {
        if (items.size == 1 && weight(items[0]) > MERGE_WEIGHT) {
            return listOf(translateLong(items[0], sl, tl, useGlossary))
        }
        val shielded = items.map { if (useGlossary) shield(it) else Shielded(it, emptyList()) }

        val parts: List<String> = if (items.size == 1) {
            listOf(requestText(shielded[0].text, sl, tl).replace(whitespace, " ").trim())
        } else {
            val joined = shielded.joinToString(SEPARATOR) { it.text }
            val raw = requestText(joined, sl, tl)
            val split = raw.replace(multiNewline, SEPARATOR).trim().split(SEPARATOR)
            if (split.size == items.size) {
                split
            } else {
                // Desalineado: traducir cada elemento por separado (más lento, pero correcto)
                shielded.map { requestText(it.text, sl, tl).replace(whitespace, " ") }
            }
        }

        return items.indices.map { i -> finish(items[i], parts[i].trim(), shielded[i], sl, tl) }
    }

    private suspend fun finish(original: String, translated: String, shielded: Shielded, sl: String, tl: String): String {
        var out = unshield(translated, shielded)
        val lostTerm = shielded.ids.any { id -> !Placeholders.regex(id, GLOSSARY_WIDTH).containsMatchIn(translated) }
        if (lostTerm) {
            // El traductor se comió un marcador: reintentar sin escudo de glosario
            out = requestText(original, sl, tl).replace(whitespace, " ").trim()
        }
        return out.ifBlank { original }
    }

    private suspend fun translateLong(text: String, sl: String, tl: String, useGlossary: Boolean): String {
        val pieces = ArrayList<String>()
        var buffer = StringBuilder()
        for (sentence in text.split(Regex("""(?<=[.!?。！？])\s*""")).filter { it.isNotBlank() }) {
            val chunks = if (weight(sentence) > MERGE_WEIGHT) sentence.chunked(MERGE_WEIGHT / 3) else listOf(sentence)
            for (chunk in chunks) {
                if (weight(buffer.toString()) + weight(chunk) + 1 > MERGE_WEIGHT && buffer.isNotEmpty()) {
                    pieces.add(buffer.toString().trim()); buffer = StringBuilder()
                }
                buffer.append(chunk).append(' ')
            }
        }
        if (buffer.isNotBlank()) pieces.add(buffer.toString().trim())
        val translated = ArrayList<String>(pieces.size)
        for (piece in pieces) {
            translated.add(translateGroup(listOf(piece), sl, tl, useGlossary).first())
        }
        return translated.joinToString(" ")
    }

    private fun weight(s: String): Int = s.sumOf { if (it.code < 128) 1 else 3 as Int }

    private fun cacheKey(sl: String, tl: String, text: String) = "$sl|$tl|$text"

    // ───── Glosario con marcadores ─────

    private fun shield(text: String): Shielded {
        var out = text
        val used = ArrayList<Int>()
        glossary.forEachIndexed { i, (regex, _) ->
            if (regex.containsMatchIn(out)) {
                out = regex.replace(out, Placeholders.token(i, GLOSSARY_WIDTH))
                used.add(i)
            }
        }
        return Shielded(out, used)
    }

    private fun unshield(translated: String, shielded: Shielded): String {
        var out = translated
        for (id in shielded.ids) {
            val replacement = glossary[id].second
            out = Placeholders.regex(id, GLOSSARY_WIDTH).replace(out) { _ -> replacement }
        }
        return out
    }

    // ───── Red: cadena de endpoints con reintentos (estilo plugin) ─────

    private suspend fun requestText(text: String, sl: String, tl: String): String {
        var last: IOException? = null
        for (engine in translationEngineOrder(BuildConfig.TRANSLATE_API_KEY)) {
            for (attempt in 0 until MAX_ATTEMPTS) {
                currentCoroutineContext().ensureActive()
                pace()
                try {
                    val out = call(engine, text, sl, tl)
                    consecutiveErrors = 0
                    return out
                } catch (e: IOException) {
                    last = e
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        throw IOException(
                            "Traducción detenida tras $MAX_CONSECUTIVE_ERRORS errores seguidos (${e.message}). " +
                                "Google puede haber limitado tu IP; reintenta más tarde y continuará donde quedó.", e
                        )
                    }
                    if (e is HttpStatusException && e.code == 429) delay(RATE_LIMIT_WAIT_MS)
                    if (attempt < MAX_ATTEMPTS - 1) delay(5000L * (attempt + 1)) // 5 s, 10 s
                }
            }
        }
        throw IOException("No se pudo traducir: ${last?.message}", last)
    }

    private suspend fun pace() {
        val wait = lastRequestAt + (MIN_INTERVAL_MS..MAX_INTERVAL_MS).random() - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        lastRequestAt = SystemClock.elapsedRealtime()
    }

    private fun call(engine: TranslationEngine, text: String, sl: String, tl: String): String {
        val request = when (engine) {
            TranslationEngine.NEW_TEXT -> {
                val q = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate-pa.googleapis.com/v1/translate" +
                    "?params.client=gtx&query.source_language=$sl&query.target_language=$tl" +
                    "&query.display_language=en-US&data_types=TRANSLATION" +
                    "&key=${BuildConfig.TRANSLATE_API_KEY}&query.text=$q"
                Request.Builder().url(url)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("User-Agent", CHROME_UA)
                    .build()
            }
            TranslationEngine.LEGACY -> {
                val builder = Request.Builder()
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("User-Agent", LEGACY_UA)
                if (text.length <= 1800) {
                    val q = URLEncoder.encode(text, "UTF-8")
                    builder.url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&dj=1&q=$q")
                } else {
                    val body = FormBody.Builder()
                        .add("client", "gtx").add("sl", sl).add("tl", tl)
                        .add("dt", "t").add("dj", "1").add("q", text)
                        .build()
                    builder.url("https://translate.googleapis.com/translate_a/single").post(body)
                }
                builder.build()
            }
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body?.string().orEmpty()
            val parsed = try {
                when (engine) {
                    TranslationEngine.NEW_TEXT -> JSONObject(body).getString("translation")
                    TranslationEngine.LEGACY -> {
                        val sentences = JSONObject(body).getJSONArray("sentences")
                        val sb = StringBuilder()
                        for (i in 0 until sentences.length()) sb.append(sentences.getJSONObject(i).optString("trans", ""))
                        sb.toString()
                    }
                }
            } catch (e: JSONException) {
                throw IOException("Respuesta no válida (¿CAPTCHA o bloqueo?)", e)
            }
            if (parsed.isBlank()) throw IOException("Respuesta vacía")
            return parsed
        }
    }

    private companion object {
        const val SEPARATOR = "\n\n"
        const val MERGE_WEIGHT = 1800
        const val GLOSSARY_WIDTH = 6
        const val MAX_ATTEMPTS = 3
        const val MAX_CONSECUTIVE_ERRORS = 10
        const val MIN_INTERVAL_MS = 700L
        const val MAX_INTERVAL_MS = 1500L
        const val RATE_LIMIT_WAIT_MS = 15_000L

        const val CHROME_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        const val LEGACY_UA = "DeepLBrowserExtension/1.3.0 Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36"
    }
}
