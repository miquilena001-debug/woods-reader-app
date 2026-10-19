package com.example.woodsreader.core.tts

object SpanishAudiobookNormalizer {

    private val dividerRegex = Regex("""^[\s*=\-_~\u2022\u2726\u2727\u25C6\u25C7\u25AA\u25AB#]+$""", RegexOption.MULTILINE)
    private val repeatedSymbolsRegex = Regex("""[*=_~#]{2,}""")
    private val thousandDotRegex = Regex("""\b(\d{1,3})\.(\d{3})(?:\.(\d{3}))?\b""")
    private val thousandCommaRegex = Regex("""\b(\d{1,3}),(\d{3})(?:,(\d{3}))?\b""")
    private val elongatedVowelsRegex = Regex("""([a\u00E1e\u00E9i\u00EDo\u00F3u\u00FAA\u00C1E\u00C9I\u00CDO\u00D3U\u00DA])\1{2,}""")
    private val elongatedConsonantsRegex = Regex("""([rlsfjmzRLSFFJMZ])\1{2,}""")
    private val laughJaRegex = Regex("""\b(?:ja){2,}[ja]*\b""", RegexOption.IGNORE_CASE)
    private val laughJeRegex = Regex("""\b(?:je){2,}[je]*\b""", RegexOption.IGNORE_CASE)
    private val laughJiRegex = Regex("""\b(?:ji){2,}[ji]*\b""", RegexOption.IGNORE_CASE)
    private val multiExclamationRegex = Regex("""\u00A1{2,}""")
    private val multiExclamationEndRegex = Regex("""!{2,}""")
    private val multiQuestionRegex = Regex("""\u00BF{2,}""")
    private val multiQuestionEndRegex = Regex("""\?{2,}""")
    private val multiDotsRegex = Regex("""\.{3,}|\u2026+""")
    private val multiSpacesRegex = Regex("""[ \t]{2,}""")

    private val romanChapterRegex = Regex(
        """\b(Cap[\u00EDi]tulo|Tomo|Volumen|Parte|Libro|Nivel|Piso|Rango|Siglo)\s+([IVXLCDM]{1,7})\b""",
        RegexOption.IGNORE_CASE
    )

    private val abbreviations = listOf(
        Regex("""\bCaps?\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Cap\u00EDtulo ",
        Regex("""\b(?:Nvl|Lv|Lvl)\.?\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Nivel ",
        Regex("""\bVol\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "Volumen ",
        Regex("""\bP\u00E1g\.\s*(?=\d)""", RegexOption.IGNORE_CASE) to "P\u00E1gina ",
        Regex("""\bSr\.\s+""") to "Se\u00F1or ",
        Regex("""\bSra\.\s+""") to "Se\u00F1ora ",
        Regex("""\bSrta\.\s+""") to "Se\u00F1orita ",
        Regex("""\bDr\.\s+""") to "Doctor ",
        Regex("""\bDra\.\s+""") to "Doctora ",
        Regex("""\bProf\.\s+""") to "Profesor ",
        Regex("""\bGral\.\s+""") to "General ",
        Regex("""\bkm/h\b""", RegexOption.IGNORE_CASE) to "kil\u00F3metros por hora",
        Regex("""\bm/s\b""", RegexOption.IGNORE_CASE) to "metros por segundo",
        Regex("""\b(\d+)\s*km\b""", RegexOption.IGNORE_CASE) to "$1 kil\u00F3metros",
        Regex("""\b(\d+)\s*kg\b""", RegexOption.IGNORE_CASE) to "$1 kilogramos",
        Regex("""\b(\d+)\s*cm\b""", RegexOption.IGNORE_CASE) to "$1 cent\u00EDmetros",
        Regex("""\b(\d+)\s*mm\b""", RegexOption.IGNORE_CASE) to "$1 mil\u00EDmetros",
        Regex("""\b(\d+)\s*HP\b""") to "$1 puntos de vida",
        Regex("""\b(\d+)\s*MP\b""") to "$1 puntos de man\u00E1",
        Regex("""\b(\d+)\s*(?:EXP|XP)\b""") to "$1 puntos de experiencia",
        Regex("""\b(?:Hmph|Hmmpf|Hmpf)\b""", RegexOption.IGNORE_CASE) to "Jmf",
        Regex("""\b(?:Pff|Pfff+)\b""", RegexOption.IGNORE_CASE) to "Puf",
        Regex("""\b(?:Shh|Shhh+)\b""", RegexOption.IGNORE_CASE) to "Shh"
    )

    fun normalizeForSpeech(rawText: String): String {
        if (rawText.isBlank()) return ""
        var text = rawText
            .replace(dividerRegex, "")
            .replace(repeatedSymbolsRegex, " ")
            .replace("\u2014", " \u2014 ")
            .replace("\u2013", " \u2014 ")
            .replace("\u00AB", " \u00AB")
            .replace("\u00BB", "\u00BB ")

        // N\u00FAmeros con separador de miles (10.000 -> 10000)
        text = thousandDotRegex.replace(text) { m ->
            m.groupValues[1] + m.groupValues[2] + m.groupValues.getOrElse(3) { "" }
        }
        text = thousandCommaRegex.replace(text) { m ->
            m.groupValues[1] + m.groupValues[2] + m.groupValues.getOrElse(3) { "" }
        }

        // N\u00FAmeros romanos tras palabras clave
        text = romanChapterRegex.replace(text) { m ->
            val prefix = m.groupValues[1]
            val roman = m.groupValues[2].uppercase()
            val number = romanToInt(roman)
            if (number > 0) "$prefix $number" else m.value
        }

        // Abreviaturas de novelas y unidades
        for ((regex, replacement) in abbreviations) {
            text = regex.replace(text, replacement)
        }

        // Risas e interjecciones alargadas
        text = laughJaRegex.replace(text, "ja, ja, ja")
        text = laughJeRegex.replace(text, "je, je, je")
        text = laughJiRegex.replace(text, "ji, ji, ji")
        text = elongatedVowelsRegex.replace(text, "$1")
        text = elongatedConsonantsRegex.replace(text, "$1$1")
        text = multiExclamationRegex.replace(text, "\u00A1")
        text = multiExclamationEndRegex.replace(text, "!")
        text = multiQuestionRegex.replace(text, "\u00BF")
        text = multiQuestionEndRegex.replace(text, "?")
        text = multiDotsRegex.replace(text, "...")
        text = multiSpacesRegex.replace(text, " ")

        return text.trim()
    }

    private fun romanToInt(s: String): Int {
        val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var result = 0
        var prev = 0
        for (i in s.length - 1 downTo 0) {
            val curr = map[s[i]] ?: return -1
            if (curr < prev) result -= curr else result += curr
            prev = curr
        }
        return result
    }
}
