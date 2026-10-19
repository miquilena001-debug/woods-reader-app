package com.example.woodsreader.core.xray

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
        "El", "La", "Los", "Las", "Un", "Una", "Unos", "Unas", "Pero", "Sin", "Con",
        "Para", "Por", "Cuando", "Mientras", "Después", "Antes", "Aunque", "Como",
        "Si", "No", "Sí", "Ya", "En", "De", "Del", "Al", "Desde", "Hasta", "Hacia",
        "Sobre", "Entre", "Tras", "Durante", "Entonces", "Luego", "Además", "También",
        "Incluso", "Acaso", "Quizás", "Tal", "Todo", "Toda", "Todos", "Todas", "Este",
        "Esta", "Estos", "Estas", "Ese", "Esa", "Esos", "Esas", "Aquel", "Aquella",
        "Mi", "Tu", "Su", "Nuestro", "Nuestra", "Qué", "Quién", "Cómo", "Dónde",
        "Cuándo", "Por", "Capítulo", "Parte", "Volumen", "Libro", "The", "And", "But",
        "When", "While", "After", "Before", "They", "There", "This", "That", "With",
        "From", "Into", "Only", "Even", "Still", "However", "Although", "Because"
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
        """(?<![.!?:—«"“\n]\s)\b([A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}){0,2})\b"""
    )

    private val bracketedSkillRegex = Regex("""(?:«([^»]{3,35})»|\[([^\]]{3,45})\]|【([^】]{3,45})】)""")

    fun analyzeChapter(
        chapterTitle: String,
        paragraphs: List<String>,
        currentParagraphIndex: Int
    ): ChapterXRayReport {
        val cleanParagraphs = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        val totalWords = cleanParagraphs.sumOf { it.split(Regex("""\s+""")).size }
        val estimatedMinutes = (totalWords / 210).coerceAtLeast(1)

        val dialogueLines = cleanParagraphs.count {
            it.startsWith("—") || it.startsWith("-") || it.contains("«") || it.contains("\"") || it.contains("“")
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

        for (para in cleanParagraphs) {
            // Términos entre corchetes o comillas angulares cortas (Habilidades / Sistema)
            for (m in bracketedSkillRegex.findAll(para)) {
                val term = (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).trim()
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
                val candidate = m.groupValues[1].trim()
                val firstWord = candidate.substringBefore(" ")
                if (firstWord in commonSentenceStarters || candidate.length < 3) continue

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
                        val lowerPara = para.lowercase()
                        val hasCharVerb = characterContextVerbs.any { lowerPara.contains(it) }
                        votes[XRayCategory.CHARACTER] = (votes[XRayCategory.CHARACTER] ?: 0) + if (hasCharVerb) 2 else 1
                    }
                }
            }
        }

        val allEntities = entityCounts.entries
            .filter { it.value >= 2 || entityCategoryVotes[it.key]?.containsKey(XRayCategory.SKILL_ITEM) == true }
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
