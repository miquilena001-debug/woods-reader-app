package com.example.woodsreader.core.tts

enum class VoiceActRole {
    NARRATOR,
    DIALOGUE,
    NARRATOR_ASIDE,
    SYSTEM_ALERT
}

enum class ProsodyMood {
    NEUTRAL,
    EXCLAMATION,
    QUESTION,
    SUSPENSE,
    FAST_ACTION,
    SOLEMN
}

data class ProsodySegment(
    val cleanText: String,
    val role: VoiceActRole,
    val mood: ProsodyMood,
    val speedMultiplier: Float,
    val gainMultiplier: Float,
    val postPauseMs: Int
)

object AudiobookProsodyEngine {

    private val verbaDicendiRegex = Regex(
        """^\s*(?:dijo|pregunt\u00F3|respondi\u00F3|exclam\u00F3|susurr\u00F3|murmur\u00F3|grit\u00F3|a\u00F1adi\u00F3|continu\u00F3|contest\u00F3|pens\u00F3|replic\u00F3|interrumpi\u00F3|suspir\u00F3|ri\u00F3|sonri\u00F3|orden\u00F3|explic\u00F3)\b""",
        RegexOption.IGNORE_CASE
    )

    fun analyzeSentence(
        rawSentence: String,
        expressiveProsodyEnabled: Boolean = true,
        language: SupportedTtsLanguage = SupportedTtsLanguage.SPANISH,
        lexiconManager: PronunciationLexiconManager? = null
    ): List<ProsodySegment> {
        val normalized = MultilingualAudiobookNormalizer.normalizeForLanguage(
            rawText = rawSentence,
            language = language,
            lexiconManager = lexiconManager
        )
        if (normalized.isBlank()) return emptyList()

        // Detectar cajas de Sistema LitRPG: [Sistema: ...] o 【...】
        if ((normalized.startsWith("[") && normalized.endsWith("]")) ||
            (normalized.startsWith("\u3010") && normalized.endsWith("\u3011"))
        ) {
            val inner = normalized.removeSurrounding("[", "]").removeSurrounding("\u3010", "\u3011").trim()
            if (inner.isNotBlank()) {
                return listOf(
                    ProsodySegment(
                        cleanText = inner,
                        role = VoiceActRole.SYSTEM_ALERT,
                        mood = ProsodyMood.NEUTRAL,
                        speedMultiplier = if (expressiveProsodyEnabled) 1.03f else 1.0f,
                        gainMultiplier = 1.04f,
                        postPauseMs = if (expressiveProsodyEnabled) 420 else 280
                    )
                )
            }
        }

        val rawParts = splitDialogueAndNarration(normalized)
        val result = ArrayList<ProsodySegment>(rawParts.size)

        for ((index, pair) in rawParts.withIndex()) {
            val (partText, baseRole) = pair
            val cleaned = partText
                .replace("\u2014", "")
                .replace("\u00AB", "")
                .replace("\u00BB", "")
                .replace("\"", "")
                .replace("\u201C", "")
                .replace("\u201D", "")
                .replace(Regex("""[¿¡]{2,}"""), "¿")
                .replace(Regex("""[?!]{2,}"""), "?")
                .trim()

            if (cleaned.isBlank() || !cleaned.any { it.isLetterOrDigit() }) continue

            val mood = detectMood(cleaned)
            val isLastPart = index == rawParts.lastIndex

            val speedMult = if (!expressiveProsodyEnabled) {
                1.0f
            } else {
                when (baseRole) {
                    VoiceActRole.NARRATOR_ASIDE -> 1.06f
                    VoiceActRole.SYSTEM_ALERT -> 1.03f
                    VoiceActRole.DIALOGUE -> when (mood) {
                        ProsodyMood.EXCLAMATION -> 1.05f
                        ProsodyMood.QUESTION -> 0.99f
                        ProsodyMood.SUSPENSE -> 0.92f
                        ProsodyMood.FAST_ACTION -> 1.04f
                        ProsodyMood.SOLEMN -> 0.95f
                        ProsodyMood.NEUTRAL -> 1.01f
                    }
                    VoiceActRole.NARRATOR -> when (mood) {
                        ProsodyMood.SUSPENSE -> 0.93f
                        ProsodyMood.FAST_ACTION -> 1.03f
                        ProsodyMood.SOLEMN -> 0.96f
                        else -> 0.99f
                    }
                }
            }

            val gainMult = when (baseRole) {
                VoiceActRole.NARRATOR_ASIDE -> 0.89f
                VoiceActRole.DIALOGUE -> 1.05f
                VoiceActRole.SYSTEM_ALERT -> 1.04f
                VoiceActRole.NARRATOR -> 0.98f
            }

            val pauseMs = if (!expressiveProsodyEnabled) {
                if (isLastPart) 260 else 140
            } else {
                calculateNaturalPauseMs(cleaned, mood, isLastPart, baseRole)
            }

            result.add(
                ProsodySegment(
                    cleanText = cleaned,
                    role = baseRole,
                    mood = mood,
                    speedMultiplier = speedMult,
                    gainMultiplier = gainMult,
                    postPauseMs = pauseMs
                )
            )
        }

        return result
    }

    private fun splitDialogueAndNarration(text: String): List<Pair<String, VoiceActRole>> {
        val trimmed = text.trim()

        // Caso 1: Línea que inicia con raya de diálogo española (—)
        if (trimmed.startsWith("\u2014") || trimmed.startsWith("-")) {
            val parts = trimmed.split("\u2014").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return listOf(trimmed to VoiceActRole.NARRATOR)
            val segments = ArrayList<Pair<String, VoiceActRole>>(parts.size)
            for ((i, part) in parts.withIndex()) {
                val role = if (i % 2 == 0) {
                    VoiceActRole.DIALOGUE
                } else if (verbaDicendiRegex.containsMatchIn(part)) {
                    VoiceActRole.NARRATOR_ASIDE
                } else {
                    VoiceActRole.NARRATOR
                }
                segments.add(part to role)
            }
            return segments
        }

        // Caso 2: Comillas («...» o "..." o "...") dentro del texto
        val quoteRegex = Regex("""(\u00AB[^\u00BB]+\u00BB|\u201C[^\u201D]+\u201D|"[^"]+")""")
        val matches = quoteRegex.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            return listOf(trimmed to VoiceActRole.NARRATOR)
        }

        val segments = ArrayList<Pair<String, VoiceActRole>>()
        var lastIdx = 0
        for (match in matches) {
            if (match.range.first > lastIdx) {
                val before = trimmed.substring(lastIdx, match.range.first).trim()
                if (before.isNotEmpty()) {
                    val role = if (verbaDicendiRegex.containsMatchIn(before)) {
                        VoiceActRole.NARRATOR_ASIDE
                    } else {
                        VoiceActRole.NARRATOR
                    }
                    segments.add(before to role)
                }
            }
            segments.add(match.value to VoiceActRole.DIALOGUE)
            lastIdx = match.range.last + 1
        }
        if (lastIdx < trimmed.length) {
            val after = trimmed.substring(lastIdx).trim()
            if (after.isNotEmpty()) {
                val role = if (verbaDicendiRegex.containsMatchIn(after)) {
                    VoiceActRole.NARRATOR_ASIDE
                } else {
                    VoiceActRole.NARRATOR
                }
                segments.add(after to role)
            }
        }
        return segments
    }

    private fun detectMood(text: String): ProsodyMood {
        return when {
            text.endsWith("...") || text.endsWith("\u2026") -> ProsodyMood.SUSPENSE
            text.contains("\u00A1") || text.endsWith("!") -> ProsodyMood.EXCLAMATION
            text.contains("\u00BF") || text.endsWith("?") -> ProsodyMood.QUESTION
            text.length < 42 && (text.contains("r\u00E1pido", true) || text.contains("golpe", true) || text.contains("corri\u00F3", true)) -> ProsodyMood.FAST_ACTION
            text.length > 175 -> ProsodyMood.SOLEMN
            else -> ProsodyMood.NEUTRAL
        }
    }

    private fun calculateNaturalPauseMs(
        text: String,
        mood: ProsodyMood,
        isLastPart: Boolean,
        role: VoiceActRole
    ): Int {
        if (!isLastPart) {
            return if (role == VoiceActRole.NARRATOR_ASIDE) 160 else 210
        }
        return when {
            mood == ProsodyMood.SUSPENSE -> 520
            mood == ProsodyMood.EXCLAMATION -> 380
            mood == ProsodyMood.QUESTION -> 410
            text.endsWith(":") || text.endsWith(";") -> 310
            role == VoiceActRole.DIALOGUE -> 350
            else -> 320
        }
    }
}
