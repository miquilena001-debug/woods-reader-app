package com.example.woodsreader.core.xray

import java.util.Locale

data class XRayEntity(
    val name: String,
    val category: XRayCategory,
    val mentionsInChapter: Int,
    val contextSnippets: List<String>
)

enum class XRayCategory(val label: String, val badgeEmoji: String) {
    CHARACTER("Personajes", "👤"),
    LOCATION_FACTION("Lugares y Clanes", "🏯"),
    SKILL_ITEM("Técnicas y Objetos", "⚡")
}

data class ChapterXRayReport(
    val chapterTitle: String,
    val estimatedReadingMinutes: Int,
    val dialoguePercentage: Int,
    val whereYouLeftOffRecap: List<String>,
    val characters: List<XRayEntity>,
    val locationsAndFactions: List<XRayEntity>,
    val skillsAndItems: List<XRayEntity>
)

object BookXRayEngine {

    private val commonSentenceStarters = setOf(
        // Artículos, demostrativos y posesivos
        "el", "la", "los", "las", "un", "una", "unos", "unas", "este", "esta", "estos", "estas",
        "ese", "esa", "esos", "esas", "aquel", "aquella", "aquellos", "aquellas",
        "mi", "tu", "su", "mis", "tus", "sus", "nuestro", "nuestra", "nuestros", "nuestras",

        // Preposiciones y conjunciones
        "pero", "sin", "con", "para", "por", "cuando", "mientras", "después", "antes", "aunque",
        "como", "si", "no", "sí", "ya", "en", "de", "del", "al", "desde", "hasta", "hacia",
        "sobre", "entre", "tras", "durante", "entonces", "luego", "además", "también", "incluso",
        "acaso", "quizás", "quizá", "tal", "todo", "toda", "todos", "todas", "porque", "pues",

        // Pronombres y partículas interrogativas / exclamativas
        "qué", "que", "quién", "quien", "quiénes", "quienes", "cómo", "dónde", "donde",
        "cuándo", "cuál", "cuales", "cuáles", "cuánto", "cuánta", "cuántos", "cuántas",
        "cuanto", "cuanta", "por qué",

        // Modismos, interjecciones y fórmulas de diálogo inicial
        "ah", "oh", "eh", "ay", "uy", "uff", "vaya", "oye", "oigan", "mira", "miren",
        "escucha", "escuchen", "vamos", "basta", "alto", "cuidado", "espera", "esperen",
        "hola", "adiós", "gracias", "perdón", "disculpa", "disculpe", "maldición", "diablos",
        "demonios", "dios", "cielos", "hombre", "mujer", "bueno", "bien", "bienvenido", "bienvenida",
        "claro", "seguro", "cierto", "ciertamente", "obviamente", "exacto", "exactamente",
        "nunca", "jamás", "tampoco", "nada", "nadie", "alguien", "algo", "alguno", "alguna",
        "algunos", "algunas", "ninguno", "ninguna", "otro", "otra", "otros", "otras", "mismo",
        "misma", "mismos", "mismas", "demás", "mucho", "mucha", "muchos", "muchas", "poco",
        "poca", "pocos", "pocas", "bastante", "bastantes", "demasiado", "demasiada", "demasiados",
        "demasiadas", "menos", "más", "tan", "tanto", "tanta", "tantos", "tantas",

        // Verbos de inicio de diálogo o relato
        "dijo", "dice", "decía", "dijeron", "preguntó", "pregunta", "preguntaron", "respondió",
        "responde", "respondieron", "gritó", "gritaron", "pensó", "pensaron", "susurró", "murmuró",
        "exclamó", "sabes", "sabe", "saben", "sabía", "entiendo", "entiendes", "entiende",
        "creo", "crees", "cree", "parece", "parecía", "puede", "puedo", "puedes", "pueden",
        "podía", "podían", "podría", "podrían", "debo", "debes", "debe", "deben", "debía",
        "tengo", "tienes", "tiene", "tienen", "tenía", "tenían", "hay", "había", "hubo", "habrá",
        "era", "eran", "es", "son", "fue", "fueron", "será", "sería", "estaba", "estaban",
        "está", "están", "estuvo", "estuvieron", "veo", "ves", "ve", "ven", "vio", "vieron",
        "hace", "hacía", "hizo", "hicieron", "va", "van", "iba", "iban", "siento", "siente", "sintió",
        "capítulo", "parte", "volumen", "libro", "sección",

        // English sentence / dialogue starters
        "the", "a", "an", "and", "but", "when", "while", "after", "before", "they", "there",
        "this", "that", "these", "those", "with", "from", "into", "only", "even", "still",
        "however", "although", "because", "what", "who", "where", "why", "how", "yes", "no",
        "not", "never", "yeah", "nope", "sure", "right", "alright", "okay", "please", "thanks",
        "thank", "hello", "hey", "hi", "goodbye", "bye", "look", "listen", "wait", "come", "go",
        "stop", "see", "tell", "say", "said", "think", "thought", "know", "knew", "maybe",
        "perhaps", "well", "so", "now", "then", "suddenly", "finally", "indeed", "actually",
        "of", "in", "on", "at", "to", "for", "by", "is", "are", "was", "were", "it", "he", "she", "we", "you", "i"
    )

    private val strictExclusions = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "en", "con", "por", "para", "sin"
    )

    private val characterContextVerbs = setOf(
        "dijo", "preguntó", "respondió", "miró", "sonrió", "frunció", "gritó",
        "susurró", "pensó", "sintió", "caminó", "corrió", "habló", "asintió",
        "negó", "rió", "exclamó", "murmuró", "ordenó", "señor", "joven", "maestro",
        "anciano", "príncipe", "rey", "doctora", "doctor", "capitán", "general",
        "said", "asked", "replied", "looked", "smiled", "shouted", "whispered", "thought"
    )

    private val locationKeywords = setOf(
        "Ciudad", "Monte", "Montaña", "Bosque", "Valle", "Río", "Reino", "Imperio",
        "Secta", "Clan", "Gremio", "Academia", "Templo", "Palacio", "Torre", "Continente",
        "Aldea", "Pueblo", "Familia", "Pabellón", "Isla", "Mar", "Desierto", "Abismo",
        "City", "Mount", "Sect", "Clan", "Guild", "Kingdom", "Empire", "Palace", "Tower"
    )

    private val skillOrItemKeywords = setOf(
        "Técnica", "Arte", "Espada", "Píldora", "Elixir", "Hechizo", "Habilidad",
        "Nivel", "Rango", "Reino", "Sistema", "Sello", "Armadura", "Lanza", "Anillo",
        "Tesoro", "Fórmula", "Ley", "Dao", "Energía", "Skill", "Sword", "Pill", "Realm"
    )

    private val capitalizedPhraseRegex = Regex(
        """\b([A-ZÁÉÍÓÚÑ][a-záéíóúñ]{1,}(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{1,}){0,2})\b"""
    )

    private val bracketedSkillRegex = Regex("""(?:«([^»]{3,35})»|\[([^\]]{3,45})\]|【([^】]{3,45})】)""")

    private fun cleanToken(raw: String): String {
        return raw
            .trimStart('—', '–', '-', '―', '¿', '¡', '«', '“', '”', '"', '\'', '‘', '’', '(', '[', '{', '*')
            .trimEnd('?', '!', '.', ',', ';', ':', '…', '«', '»', '“', '”', '"', '\'', '‘', '’', ')', ']', '}', '*')
            .trim()
    }

    private fun isAtSentenceOrDialogueStart(text: CharSequence, matchStart: Int): Boolean {
        var i = matchStart - 1
        while (i >= 0 && text[i].isWhitespace()) {
            i--
        }
        if (i < 0) return true
        while (i >= 0 && (text[i] in "¿¡«“\"'‘—–-―([{")) {
            i--
            while (i >= 0 && text[i].isWhitespace()) {
                i--
            }
        }
        if (i < 0) return true
        return text[i] in ".!?:;\n\r"
    }

    fun analyzeChapter(
        chapterTitle: String,
        paragraphs: List<String>,
        currentParagraphIndex: Int
    ): ChapterXRayReport {
        val cleanParagraphs = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        val totalWords = cleanParagraphs.sumOf { it.split(Regex("""\s+""")).size }
        val estimatedMinutes = (totalWords / 210).coerceAtLeast(1)

        val dialogueLines = cleanParagraphs.count {
            it.startsWith("—") || it.startsWith("-") || it.startsWith("–") || it.contains("«") || it.contains("\"") || it.contains("“")
        }
        val dialoguePct = if (cleanParagraphs.isNotEmpty()) {
            ((dialogueLines * 100) / cleanParagraphs.size).coerceIn(0, 100)
        } else 0

        // 1. Generar "¿Dónde me quedé?" (3 párrafos inmediatamente anteriores a la posición actual)
        val safeIdx = currentParagraphIndex.coerceIn(0, (cleanParagraphs.size - 1).coerceAtLeast(0))
        val recapStart = (safeIdx - 2).coerceAtLeast(0)
        val recapList = if (cleanParagraphs.isNotEmpty()) {
            cleanParagraphs.subList(recapStart, (safeIdx + 1).coerceAtMost(cleanParagraphs.size))
                .map { if (it.length > 220) it.take(217) + "..." else it }
        } else emptyList()

        // 2. Extraer menciones y clasificar entidades
        val entityCounts = mutableMapOf<String, Int>()
        val entitySnippets = mutableMapOf<String, MutableList<String>>()
        val entityCategoryVotes = mutableMapOf<String, MutableMap<XRayCategory, Int>>()
        val entityMidSentenceCounts = mutableMapOf<String, Int>()

        for (para in cleanParagraphs) {
            // Términos entre corchetes o comillas angulares cortas (Habilidades / Sistema)
            for (m in bracketedSkillRegex.findAll(para)) {
                val term = cleanToken(m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] })
                if (term.length in 3..38 && term.split(" ").size <= 5) {
                    entityCounts[term] = (entityCounts[term] ?: 0) + 1
                    val snippets = entitySnippets.getOrPut(term) { mutableListOf() }
                    if (snippets.size < 3) {
                        snippets.add(if (para.length > 160) para.take(157) + "..." else para)
                    }
                    val votes = entityCategoryVotes.getOrPut(term) { mutableMapOf() }
                    votes[XRayCategory.SKILL_ITEM] = (votes[XRayCategory.SKILL_ITEM] ?: 0) + 3
                }
            }

            // Nombres propios y frases capitalizadas
            for (m in capitalizedPhraseRegex.findAll(para)) {
                val rawCandidate = m.groupValues[1]
                val candidate = cleanToken(rawCandidate)
                if (candidate.length < 3) continue

                val firstWord = cleanToken(candidate.substringBefore(" "))
                val firstWordLower = firstWord.lowercase(Locale.ROOT)
                val candidateLower = candidate.lowercase(Locale.ROOT)
                val atStart = isAtSentenceOrDialogueStart(para, m.range.first)

                if (atStart) {
                    if (commonSentenceStarters.contains(firstWordLower) || commonSentenceStarters.contains(candidateLower)) {
                        continue
                    }
                } else {
                    if (firstWordLower in strictExclusions) continue
                    entityMidSentenceCounts[candidate] = (entityMidSentenceCounts[candidate] ?: 0) + 1
                }

                entityCounts[candidate] = (entityCounts[candidate] ?: 0) + 1
                val snippets = entitySnippets.getOrPut(candidate) { mutableListOf() }
                if (snippets.size < 3) {
                    snippets.add(if (para.length > 160) para.take(157) + "..." else para)
                }

                val votes = entityCategoryVotes.getOrPut(candidate) { mutableMapOf() }
                val words = candidate.split(" ")

                when {
                    words.any { it in locationKeywords } ->
                        votes[XRayCategory.LOCATION_FACTION] = (votes[XRayCategory.LOCATION_FACTION] ?: 0) + 3
                    words.any { it in skillOrItemKeywords } ->
                        votes[XRayCategory.SKILL_ITEM] = (votes[XRayCategory.SKILL_ITEM] ?: 0) + 3
                    else -> {
                        val lowerPara = para.lowercase(Locale.ROOT)
                        val hasCharVerb = characterContextVerbs.any { lowerPara.contains(it) }
                        votes[XRayCategory.CHARACTER] = (votes[XRayCategory.CHARACTER] ?: 0) + if (hasCharVerb) 2 else 1
                    }
                }
            }
        }

        val allEntities = entityCounts.entries
            .filter { (name, count) ->
                val isSkill = entityCategoryVotes[name]?.containsKey(XRayCategory.SKILL_ITEM) == true
                val isLocation = entityCategoryVotes[name]?.containsKey(XRayCategory.LOCATION_FACTION) == true
                val midCount = entityMidSentenceCounts[name] ?: 0
                // Exigir al menos 2 menciones globales o haber aparecido a mitad de frase para personajes
                isSkill || isLocation || (count >= 2 && midCount >= 1) || count >= 4
            }
            .sortedByDescending { it.value }
            .map { (name, count) ->
                val bestCat = entityCategoryVotes[name]?.maxByOrNull { it.value }?.key ?: XRayCategory.CHARACTER
                XRayEntity(
                    name = name,
                    category = bestCat,
                    mentionsInChapter = count,
                    contextSnippets = entitySnippets[name] ?: emptyList()
                )
            }

        return ChapterXRayReport(
            chapterTitle = chapterTitle,
            estimatedReadingMinutes = estimatedMinutes,
            dialoguePercentage = dialoguePct,
            whereYouLeftOffRecap = recapList,
            characters = allEntities.filter { it.category == XRayCategory.CHARACTER }.take(15),
            locationsAndFactions = allEntities.filter { it.category == XRayCategory.LOCATION_FACTION }.take(10),
            skillsAndItems = allEntities.filter { it.category == XRayCategory.SKILL_ITEM }.take(12)
        )
    }
}
