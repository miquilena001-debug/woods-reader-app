package com.example.woodsreader.core.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class PhoneticRule(
    val id: String,
    val originalWord: String,
    val phoneticReplacement: String,
    val caseSensitive: Boolean = false,
    val isBuiltIn: Boolean = false
)

@Singleton
class PronunciationLexiconManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("woods_pronunciation_lexicon", Context.MODE_PRIVATE)

    private val _builtInEnabled = MutableStateFlow(prefs.getBoolean("built_in_lexicon_enabled", true))
    val builtInEnabled: StateFlow<Boolean> = _builtInEnabled.asStateFlow()

    private val _customRules = MutableStateFlow<List<PhoneticRule>>(loadCustomRules())
    val customRules: StateFlow<List<PhoneticRule>> = _customRules.asStateFlow()

    // Reglas preinstaladas para que el TTS pronuncie con naturalidad términos de Novelas Web / Cultivo / Fantasía
    private val builtInSpanishWebNovelRules = listOf(
        Regex("""\bQi\b""") to "Chi",
        Regex("""\bqi\b""") to "chi",
        Regex("""\bDantian\b""", RegexOption.IGNORE_CASE) to "Dantián",
        Regex("""\bJianghu\b""", RegexOption.IGNORE_CASE) to "Yiang-ju",
        Regex("""\bXianxia\b""", RegexOption.IGNORE_CASE) to "Sian-sia",
        Regex("""\bXuanhuan\b""", RegexOption.IGNORE_CASE) to "Suan-juan",
        Regex("""\bWuxia\b""", RegexOption.IGNORE_CASE) to "Wu-sia",
        Regex("""\bQigong\b""", RegexOption.IGNORE_CASE) to "Chi-gong",
        Regex("""\bQing\b""") to "Ching",
        Regex("""\bQian\b""") to "Chian",
        Regex("""\bQin\b""") to "Chin",
        Regex("""\bQiao\b""") to "Chiao",
        Regex("""\bXiao\b""") to "Siao",
        Regex("""\bXie\b""") to "Sie",
        Regex("""\bXue\b""") to "Sue",
        Regex("""\bXuan\b""") to "Suan",
        Regex("""\bZhan\b""") to "Chan",
        Regex("""\bZhang\b""") to "Chang",
        Regex("""\bZhao\b""") to "Chao",
        Regex("""\bZhou\b""") to "Chou",
        Regex("""\bZhu\b""") to "Chu",
        Regex("""\bNPCs\b""") to "ene pe ces",
        Regex("""\bNPC\b""") to "ene pe ce",
        Regex("""\bMMORPG\b""") to "eme eme o erre pe ge",
        Regex("""\bRPG\b""") to "erre pe ge",
        Regex("""\bPvP\b""", RegexOption.IGNORE_CASE) to "jugador contra jugador",
        Regex("""\bAFK\b""", RegexOption.IGNORE_CASE) to "ausente"
    )

    fun setBuiltInEnabled(enabled: Boolean) {
        _builtInEnabled.value = enabled
        prefs.edit().putBoolean("built_in_lexicon_enabled", enabled).apply()
    }

    fun addCustomRule(original: String, phonetic: String, caseSensitive: Boolean = false) {
        val cleanOrig = original.trim()
        val cleanPhon = phonetic.trim()
        if (cleanOrig.isEmpty() || cleanPhon.isEmpty()) return

        val updated = _customRules.value.filterNot {
            it.originalWord.equals(cleanOrig, ignoreCase = !caseSensitive)
        } + PhoneticRule(
            id = System.currentTimeMillis().toString(),
            originalWord = cleanOrig,
            phoneticReplacement = cleanPhon,
            caseSensitive = caseSensitive,
            isBuiltIn = false
        )
        _customRules.value = updated
        saveCustomRules(updated)
    }

    fun removeCustomRule(id: String) {
        val updated = _customRules.value.filterNot { it.id == id }
        _customRules.value = updated
        saveCustomRules(updated)
    }

    fun applyLexicon(text: String, language: SupportedTtsLanguage): String {
        if (text.isBlank()) return text
        var result = text

        // 1. Aplicar reglas personalizadas del usuario
        for (rule in _customRules.value) {
            val opts = if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val pattern = Regex("""\b${Regex.escape(rule.originalWord)}\b""", opts)
            result = pattern.replace(result, rule.phoneticReplacement)
        }

        // 2. Aplicar glosario fonético de novelas web en español si está activo
        if (_builtInEnabled.value && language == SupportedTtsLanguage.SPANISH) {
            for ((regex, replacement) in builtInSpanishWebNovelRules) {
                result = regex.replace(result, replacement)
            }
        }

        return result
    }

    private fun loadCustomRules(): List<PhoneticRule> {
        val raw = prefs.getString("custom_rules_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<PhoneticRule>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PhoneticRule(
                        id = obj.optString("id", i.toString()),
                        originalWord = obj.getString("originalWord"),
                        phoneticReplacement = obj.getString("phoneticReplacement"),
                        caseSensitive = obj.optBoolean("caseSensitive", false),
                        isBuiltIn = false
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun saveCustomRules(rules: List<PhoneticRule>) {
        val arr = JSONArray()
        for (r in rules) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("originalWord", r.originalWord)
                put("phoneticReplacement", r.phoneticReplacement)
                put("caseSensitive", r.caseSensitive)
            }
            arr.put(obj)
        }
        prefs.edit().putString("custom_rules_json", arr.toString()).apply()
    }
}
