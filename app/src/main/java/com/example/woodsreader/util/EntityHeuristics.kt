package com.example.woodsreader.util

import com.example.woodsreader.data.graph.EntityType

object EntityHeuristics {

    /**
     * Regex de candidatos según FASE 8:
     * Una palabra en mayúscula seguida opcionalmente de hasta 3 palabras en mayúscula.
     * Ejemplo: "Lin Dong", "Secta de la Espada Celestial"
     */
    val CANDIDATE_REGEX = Regex("""\p{Lu}[\p{L}'-]+(?:\s+\p{Lu}[\p{L}'-]+){0,3}""")

    // Palabras clave para sugerir el tipo de entidad
    private val SECTA_REGEX = Regex("""(?i)\b(Secta|Clan|Pabell[oó]n|Templo|Palacio|Gremio|Facci[oó]n|Tribu|Alianza)\b""")
    private val LUGAR_REGEX = Regex("""(?i)\b(Monta[ñn]a|Valle|Ciudad|Reino|Bosque|Mar|Isla|R[ií]o|Lago|Continente|Cueva|Pico|Provincia|Distrito|Regi[oó]n|Tierras|Planeta)\b""")
    private val ARTEFACTO_REGEX = Regex("""(?i)\b(Espada|P[ií]ldora|Talism[aá]n|Perla|Tesoro|Lanza|Sable|Armadura|Anillo|Caldero|Espejo|Campana|Torre|Pagoda|Sello|Manual)\b""")
    private val RANGO_REGEX = Regex("""(?i)\b(Etapa|Fase|N[uú]cleo|Fundaci[oó]n|Alma|Reino\s+de|Nivel|Grado|Dan|Yuan|Cultivo)\b""")

    // Stopwords en español con mayúscula inicial y términos estructurales comunes en novelas
    private val STOPWORDS = setOf(
        "El", "La", "Los", "Las", "Un", "Una", "Unos", "Unas",
        "Y", "O", "U", "E", "Pero", "Aunque", "Sin", "Con", "Por", "Para",
        "Sin embargo", "No obstante", "Por lo tanto", "Por consiguiente",
        "Entonces", "Cuando", "Mientras", "Despues", "Después", "Antes", "Luego", "Pronto", "Tarde",
        "Como", "Donde", "Dónde", "Que", "Qué", "Quien", "Quién", "Cual", "Cuál", "Cuanto", "Cuánto",
        "Sobre", "Tras", "Desde", "Hasta", "Hacia", "Entre", "Bajo", "Contra",
        "Este", "Esta", "Estos", "Estas", "Ese", "Esa", "Esos", "Esas", "Aquel", "Aquella", "Aquellos", "Aquellas",
        "Yo", "Tu", "Tú", "El", "Él", "Ella", "Ellos", "Ellas", "Nosotros", "Nosotras", "Vosotros", "Usted", "Ustedes",
        "Mi", "Tu", "Su", "Mis", "Tus", "Sus", "Nuestro", "Nuestra", "Nuestros", "Nuestras",
        "Si", "Sí", "No", "Tal", "Tambien", "También", "Tampoco", "Asi", "Así", "Bien", "Mal",
        "Muy", "Mucho", "Muchos", "Mucha", "Muchas", "Poco", "Pocos", "Poca", "Pocas",
        "Todo", "Todos", "Toda", "Todas", "Nada", "Nadie", "Alguien", "Algun", "Algún", "Alguno", "Alguna", "Algunos", "Algunas",
        "Otro", "Otra", "Otros", "Otras", "Mismo", "Misma", "Mismos", "Mismas",
        "Capitulo", "Capítulo", "Parte", "Libro", "Volumen", "Tomo", "Prologo", "Prólogo", "Epilogo", "Epílogo",
        "Fin", "Continuara", "Continuará", "Autor", "Traductor", "Traduccion", "Traducción", "Nota",
        "Dia", "Día", "Noche", "Tarde", "Mañana", "Ayer", "Hoy", "Año", "Mes", "Hora",
        "Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve", "Diez",
        "Primero", "Segundo", "Tercero", "Primera", "Segunda", "Tercera"
    )

    /**
     * Sugiere un EntityType según palabras clave en el nombre.
     * Si no coincide con ninguna regla, retorna OTRO (según especificación FASE 8).
     */
    fun suggestType(name: String): EntityType = when {
        RANGO_REGEX.containsMatchIn(name) -> EntityType.RANGO
        SECTA_REGEX.containsMatchIn(name) -> EntityType.SECTA
        LUGAR_REGEX.containsMatchIn(name) -> EntityType.LUGAR
        ARTEFACTO_REGEX.containsMatchIn(name) -> EntityType.ARTEFACTO
        else -> EntityType.OTRO
    }

    /**
     * Determina si el texto es una stopword o comienza con una stopword trivial.
     */
    fun isStopword(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.length < 2) return true
        if (STOPWORDS.contains(trimmed)) return true

        // Descartar frases donde la primera palabra es un artículo o conector stopword y solo tiene 2 palabras
        val words = trimmed.split(Regex("""\s+"""))
        if (words.size == 1 && STOPWORDS.contains(words[0])) return true
        if (words.size == 2 && STOPWORDS.contains(words[0]) && STOPWORDS.contains(words[1])) return true

        return false
    }

    /**
     * Determina si la aparición del candidato en el texto ocurrió a mitad de frase.
     * FASE 8: "no precedidas por '. ', '?', '!', inicio de párrafo, comilla de apertura"
     */
    fun isMidSentence(text: CharSequence, matchStart: Int): Boolean {
        if (matchStart <= 0) return false

        // Retroceder buscando el último carácter no-espacio antes del match
        var idx = matchStart - 1
        while (idx >= 0 && text[idx].isWhitespace()) {
            idx--
        }
        if (idx < 0) return false // Estaba al puro inicio del párrafo/texto

        val prevChar = text[idx]
        // Si el carácter anterior es signo de término de oración o comilla de apertura, no es mitad de frase
        return when (prevChar) {
            '.', '?', '!', '¿', '¡', '"', '“', '«', '\'', '—', '-', ':', ';' -> false
            else -> true
        }
    }

    /**
     * Extrae un snippet contextual de ~80 caracteres alrededor de la ocurrencia.
     */
    fun extractSnippet(text: String, matchStart: Int, matchLength: Int, targetRadius: Int = 40): String {
        val len = text.length
        if (len == 0) return ""

        val start = (matchStart - targetRadius).coerceAtLeast(0)
        val end = (matchStart + matchLength + targetRadius).coerceAtMost(len)

        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < len) "…" else ""

        return prefix + text.substring(start, end).replace(Regex("""\s+"""), " ").trim() + suffix
    }

    /**
     * Limpia etiquetas HTML de un capítulo y decodifica entidades comunes sin sobrecarga DOM.
     */
    fun stripHtml(html: String): String {
        val bodyStart = html.indexOf("<body", ignoreCase = true).let {
            if (it >= 0) html.indexOf('>', it) + 1 else 0
        }
        val sb = StringBuilder((html.length - bodyStart).coerceAtLeast(64))
        var inTag = false
        var inScriptOrStyle = false
        var lastWasSpace = true
        var i = bodyStart
        val len = html.length

        while (i < len) {
            val c = html[i]
            if (!inTag && c == '<') {
                inTag = true
                if (i + 6 < len) {
                    val sub = html.substring(i + 1, (i + 8).coerceAtMost(len)).lowercase()
                    if (sub.startsWith("script") || sub.startsWith("style") || sub.startsWith("head")) {
                        inScriptOrStyle = true
                    } else if (sub.startsWith("/script") || sub.startsWith("/style") || sub.startsWith("/head")) {
                        inScriptOrStyle = false
                    } else if (sub.startsWith("p") || sub.startsWith("/p") || sub.startsWith("div") || sub.startsWith("br")) {
                        sb.append("\n")
                        lastWasSpace = true
                    }
                }
            } else if (inTag && c == '>') {
                inTag = false
            } else if (!inTag && !inScriptOrStyle) {
                if (c == '&') {
                    val semi = html.indexOf(';', i + 1)
                    if (semi in (i + 2)..(i + 10)) {
                        val entity = html.substring(i + 1, semi)
                        val decoded = when (entity) {
                            "nbsp", "#160" -> ' '
                            "quot", "#34", "ldquo", "rdquo" -> '"'
                            "apos", "#39", "lsquo", "rsquo" -> '\''
                            "amp", "#38" -> '&'
                            "lt", "#60" -> '<'
                            "gt", "#62" -> '>'
                            "mdash", "ndash" -> '—'
                            else -> ' '
                        }
                        if (decoded == ' ') {
                            if (!lastWasSpace) {
                                sb.append(' ')
                                lastWasSpace = true
                            }
                        } else {
                            sb.append(decoded)
                            lastWasSpace = false
                        }
                        i = semi + 1
                        continue
                    }
                }

                if (c == '\r' || c == '\n') {
                    sb.append('\n')
                    lastWasSpace = true
                } else if (c.isWhitespace()) {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                } else {
                    sb.append(c)
                    lastWasSpace = false
                }
            }
            i++
        }
        return sb.toString().trim()
    }

    /**
     * Divide el texto plano en párrafos no vacíos.
     */
    fun splitIntoParagraphs(plainText: String): List<String> {
        return plainText.split(Regex("""\n+"""))
            .map { it.trim() }
            .filter { it.length >= 10 }
    }
}
