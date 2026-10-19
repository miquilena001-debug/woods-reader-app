package com.example.woodsreader.core.tts

enum class SupportedTtsLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String
) {
    SPANISH("es", "Español", "🇪🇸"),
    ENGLISH("en", "English", "🇺🇸"),
    PORTUGUESE("pt", "Português", "🇧🇷"),
    FRENCH("fr", "Français", "🇫🇷"),
    GERMAN("de", "Deutsch", "🇩🇪"),
    ITALIAN("it", "Italiano", "🇮🇹");

    companion object {
        fun fromCode(code: String?): SupportedTtsLanguage {
            if (code.isNullOrBlank()) return SPANISH
            val prefix = code.lowercase().take(2)
            return entries.firstOrNull { it.code == prefix } ?: SPANISH
        }
    }
}

object MultilingualAudiobookNormalizer {

    private val dividerRegex = Regex("""^[\s*=\-_~•✦✧◆◇▪▫#]+$""", RegexOption.MULTILINE)
    private val repeatedSymbolsRegex = Regex("""[*=_~#]{2,}""")
    private val multiDotsRegex = Regex("""\.{3,}|…+""")
    private val multiSpacesRegex = Regex("""[ \t]{2,}""")
    private val elongatedCharsRegex = Regex("""([a-zA-ZáéíóúÁÉÍÓÚàèìòùÀÈÒÙâêîôûÂÊÎÔÛäëïöüÄËÏÖÜãõÃÕ])\1{2,}""")

    // Reglas específicas por idioma
    private val englishRules = listOf(
        Regex("""\bCh\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Chapter ",
        Regex("""\bChap\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Chapter ",
        Regex("""\b(?:Lv|Lvl)\.?\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Level ",
        Regex("""\bVol\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Volume ",
        Regex("""\bMr\.\s+""") to "Mister ",
        Regex("""\bMrs\.\s+""") to "Missus ",
        Regex("""\bMs\.\s+""") to "Miss ",
        Regex("""\bDr\.\s+""") to "Doctor ",
        Regex("""\bProf\.\s+""") to "Professor ",
        Regex("""\bSt\.\s+""") to "Saint ",
        Regex("""\bGen\.\s+""") to "General ",
        Regex("""\b(\d+)\s*HP\b""") to "$1 health points",
        Regex("""\b(\d+)\s*MP\b""") to "$1 mana points",
        Regex("""\b(\d+)\s*(?:EXP|XP)\b""") to "$1 experience points",
        Regex("""\bkm/h\b""", RegexOption.IGNORE_CASE) to "kilometers per hour",
        Regex("""\bmph\b""", RegexOption.IGNORE_CASE) to "miles per hour"
    )

    private val portugueseRules = listOf(
        Regex("""\bCaps?\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Capítulo ",
        Regex("""\b(?:Nvl|Nv|Lv|Lvl)\.?\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Nível ",
        Regex("""\bSr\.\s+""") to "Senhor ",
        Regex("""\bSra\.\s+""") to "Senhora ",
        Regex("""\bDr\.\s+""") to "Doutor ",
        Regex("""\bDra\.\s+""") to "Doutora ",
        Regex("""\bProf\.\s+""") to "Professor ",
        Regex("""\b(\d+)\s*HP\b""") to "$1 pontos de vida",
        Regex("""\b(\d+)\s*MP\b""") to "$1 pontos de mana"
    )

    private val frenchRules = listOf(
        Regex("""\bChap\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Chapitre ",
        Regex("""\b(?:Niv|Lv|Lvl)\.?\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Niveau ",
        Regex("""\bM\.\s+""") to "Monsieur ",
        Regex("""\bMme\.\s+""") to "Madame ",
        Regex("""\bMlle\.\s+""") to "Mademoiselle ",
        Regex("""\bDr\.\s+""") to "Docteur ",
        Regex("""\b(\d+)\s*PV\b""") to "$1 points de vie",
        Regex("""\b(\d+)\s*PM\b""") to "$1 points de mana"
    )

    private val germanRules = listOf(
        Regex("""\bKap\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Kapitel ",
        Regex("""\b(?:St|Lv|Lvl)\.?\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Stufe ",
        Regex("""\bDr\.\s+""") to "Doktor ",
        Regex("""\bProf\.\s+""") to "Professor ",
        Regex("""\bz\.B\.\b""", RegexOption.IGNORE_CASE) to "zum Beispiel",
        Regex("""\bd\.h\.\b""", RegexOption.IGNORE_CASE) to "das heißt"
    )

    private val italianRules = listOf(
        Regex("""\bCap\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Capitolo ",
        Regex("""\b(?:Liv|Lv|Lvl)\.?\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Livello ",
        Regex("""\bSig\.\s+""") to "Signor ",
        Regex("""\bSig\.ra\s+""") to "Signora ",
        Regex("""\bDott\.\s+""") to "Dottor ",
        Regex("""\bProf\.\s+""") to "Professor "
    )

    fun detectDominantLanguage(sampleText: String): SupportedTtsLanguage {
        if (sampleText.isBlank()) return SupportedTtsLanguage.SPANISH
        val lower = sampleText.take(1500).lowercase()

        val scores = mutableMapOf(
            SupportedTtsLanguage.SPANISH to 0,
            SupportedTtsLanguage.ENGLISH to 0,
            SupportedTtsLanguage.PORTUGUESE to 0,
            SupportedTtsLanguage.FRENCH to 0,
            SupportedTtsLanguage.GERMAN to 0,
            SupportedTtsLanguage.ITALIAN to 0
        )

        if (lower.contains("ñ") || lower.contains("¿") || lower.contains("¡")) {
            scores[SupportedTtsLanguage.SPANISH] = scores.getValue(SupportedTtsLanguage.SPANISH) + 8
        }
        if (lower.contains("ã") || lower.contains("õ") || lower.contains("ç")) {
            scores[SupportedTtsLanguage.PORTUGUESE] = scores.getValue(SupportedTtsLanguage.PORTUGUESE) + 6
        }
        if (lower.contains("ß") || lower.contains("ä") || lower.contains("ö")) {
            scores[SupportedTtsLanguage.GERMAN] = scores.getValue(SupportedTtsLanguage.GERMAN) + 6
        }
        if (lower.contains("œ") || lower.contains(" c'") || lower.contains(" d'") || lower.contains(" l'")) {
            scores[SupportedTtsLanguage.FRENCH] = scores.getValue(SupportedTtsLanguage.FRENCH) + 5
        }

        val words = lower.split(Regex("""\W+""")).filter { it.length >= 2 }
        for (w in words) {
            when (w) {
                "el", "la", "los", "las", "que", "del", "pero", "como", "para", "hacia", "dijo", "capítulo" ->
                    scores[SupportedTtsLanguage.SPANISH] = scores.getValue(SupportedTtsLanguage.SPANISH) + 2
                "the", "and", "that", "with", "from", "have", "this", "they", "would", "there", "said", "chapter" ->
                    scores[SupportedTtsLanguage.ENGLISH] = scores.getValue(SupportedTtsLanguage.ENGLISH) + 2
                "não", "uma", "para", "com", "está", "isso", "pelo", "você", "então", "disse", "muito" ->
                    scores[SupportedTtsLanguage.PORTUGUESE] = scores.getValue(SupportedTtsLanguage.PORTUGUESE) + 2
                "les", "des", "est", "dans", "pour", "pas", "sur", "plus", "avec", "tout", "mais", "chapitre" ->
                    scores[SupportedTtsLanguage.FRENCH] = scores.getValue(SupportedTtsLanguage.FRENCH) + 2
                "der", "die", "das", "und", "nicht", "mit", "sich", "auf", "für", "von", "dem", "kapitel" ->
                    scores[SupportedTtsLanguage.GERMAN] = scores.getValue(SupportedTtsLanguage.GERMAN) + 2
                "che", "non", "per", "una", "con", "del", "della", "sono", "questo", "anche", "disse", "capitolo" ->
                    scores[SupportedTtsLanguage.ITALIAN] = scores.getValue(SupportedTtsLanguage.ITALIAN) + 2
            }
        }

        return scores.maxByOrNull { it.value }?.key ?: SupportedTtsLanguage.SPANISH
    }

    fun normalizeForLanguage(
        rawText: String,
        language: SupportedTtsLanguage,
        lexiconManager: PronunciationLexiconManager? = null
    ): String {
        if (rawText.isBlank()) return ""

        // 1. Aplicar primero el diccionario fonético (nombres propios, términos Xuanhuan/LitRPG y reglas del usuario)
        val lexiconApplied = lexiconManager?.applyLexicon(rawText, language) ?: rawText

        // 2. Si es español, delegar al normalizador literario español completo
        if (language == SupportedTtsLanguage.SPANISH) {
            return SpanishAudiobookNormalizer.normalizeForSpeech(lexiconApplied)
        }

        var text = lexiconApplied
            .replace(dividerRegex, "")
            .replace(repeatedSymbolsRegex, " ")
            .replace("—", " — ")
            .replace("–", " — ")

        val rules = when (language) {
            SupportedTtsLanguage.ENGLISH -> englishRules
            SupportedTtsLanguage.PORTUGUESE -> portugueseRules
            SupportedTtsLanguage.FRENCH -> frenchRules
            SupportedTtsLanguage.GERMAN -> germanRules
            SupportedTtsLanguage.ITALIAN -> italianRules
            SupportedTtsLanguage.SPANISH -> emptyList()
        }

        for ((regex, replacement) in rules) {
            text = regex.replace(text, replacement)
        }

        text = elongatedCharsRegex.replace(text, "$1$1")
        text = multiDotsRegex.replace(text, "...")
        text = multiSpacesRegex.replace(text, " ")

        return text.trim()
    }
}
