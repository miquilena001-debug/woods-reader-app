package com.example.woodsreader.service

import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

interface Summarizer {
    /** Summarizes HTML or raw text into key sentences. */
    fun summarize(text: String, numSentences: Int = 4): String
    
    /** Strips HTML tags and normalizes whitespace. */
    fun stripHtml(html: String): String
}

class TextRankSummarizer @Inject constructor() : Summarizer {

    private companion object {
        const val MIN_SENTENCE_LEN = 35
        const val MAX_SENTENCES = 120
        const val DAMPING = 0.85f
        const val ITERATIONS = 30
        const val TOLERANCE = 0.0001f

        val HTML_SCRIPT_REGEX = Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL)
        val HTML_STYLE_REGEX = Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL)
        val HTML_TAG_REGEX = Regex("<[^>]+>")
        val HTML_ENTITY_REGEX = Regex("&#[0-9]+;")
        val WHITESPACE_REGEX = Regex("\\s+")
        val NUMERIC_REGEX = Regex("^[\\d\\s.,;:\u2013-]+$")
        val TOKEN_REGEX = Regex("[\\W_]+")

        // Optimized Regex for sentence splitting using positive lookbehind and lookahead
        val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])[\\s]+(?=[A-Z\u00c1\u00c9\u00cd\u00d3\u00da\u00d1\u00dc\"'\u00bf\u00a1])")

        val STOPWORDS = setOf(
            "de","la","el","en","y","a","los","se","del","las","un","por","con","no","una","su",
            "para","es","al","lo","como","mas","pero","sus","le","ya","o","fue","este","ha","si",
            "me","hay","ante","ellos","esto","antes","algunos","uno","ese","tambien","todo","esta",
            "entre","cuando","muy","sin","sobre","ser","tiene","era","asi","seria",
            "ni","nos","ademas","hasta","desde","otro","tanto","esos","unos","que","quien",
            "cual","cuales","donde","porque","aunque","mientras",
            "the","and","is","in","it","of","to","an","that","was","for","on","are","with",
            "he","she","they","we","you","at","be","this","have","from","or","by","but","not",
            "what","which","who","if","when","there","been","had","can","would","their","will",
            "all","were","said","so","up","out","about","into","then","than","him","her",
            "its","more","could","some","these","them","other","do","did","just","over","after"
        )

        val METADATA_KEYWORDS = listOf(
            "isbn", "copyright", "http", "www.", "todos los derechos", "all rights reserved", "\u00a9"
        )
    }

    override fun summarize(text: String, numSentences: Int): String {
        if (text.isBlank() || numSentences <= 0) return ""

        return try {
            val sentences = splitSentences(text)
            if (sentences.size <= numSentences) {
                return sentences.take(numSentences).joinToString(" ").trim()
            }

            val capped = sentences.take(MAX_SENTENCES)
            val tokenized = capped.map { tokenize(it) }
            val n = capped.size

            // 1D Array for contiguous memory allocation (Extreme optimization)
            val sim = FloatArray(n * n)
            
            // Calculate upper triangle and mirror it to save 50% computations
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val jaccard = jaccardSimilarity(tokenized[i], tokenized[j])
                    sim[i * n + j] = jaccard
                    sim[j * n + i] = jaccard
                }
            }

            // Normalize columns
            val normalized = FloatArray(n * n)
            for (j in 0 until n) {
                var colSum = 0f
                for (i in 0 until n) {
                    colSum += sim[i * n + j]
                }
                
                for (i in 0 until n) {
                    normalized[i * n + j] = if (colSum > 0f) sim[i * n + j] / colSum else 1f / n
                }
            }

            // PageRank iterations with early convergence
            val scores = FloatArray(n) { 1f / n }
            val temp = FloatArray(n)
            
            for (iter in 0 until ITERATIONS) {
                var maxDiff = 0f
                for (i in 0 until n) {
                    var sum = 0f
                    for (j in 0 until n) {
                        sum += normalized[j * n + i] * scores[j]
                    }
                    val newScore = (1f - DAMPING) / n + DAMPING * sum
                    maxDiff = maxOf(maxDiff, abs(newScore - scores[i]))
                    temp[i] = newScore
                }
                System.arraycopy(temp, 0, scores, 0, n)
                
                // Convergence check
                if (maxDiff < TOLERANCE) break
            }

            // Pick top-N, restore original order
            val topIndices = scores.indices
                .sortedByDescending { scores[it] }
                .take(numSentences)
                .sorted()

            topIndices.joinToString(" ") { capped[it] }.trim()
        } catch (e: Exception) {
            "" // Fail gracefully with empty string rather than crashing UI
        }
    }

    override fun stripHtml(html: String): String = html
        .replace(HTML_SCRIPT_REGEX, " ")
        .replace(HTML_STYLE_REGEX, " ")
        .replace(HTML_TAG_REGEX, " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(HTML_ENTITY_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun splitSentences(text: String): List<String> =
        text.split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.length >= MIN_SENTENCE_LEN && !looksLikeMetadata(it) }

    private fun looksLikeMetadata(s: String): Boolean {
        val lower = s.lowercase(Locale.ROOT)
        if (METADATA_KEYWORDS.any { lower.contains(it) }) return true
        return NUMERIC_REGEX.containsMatchIn(s)
    }

    private fun tokenize(sentence: String): Set<String> =
        sentence.lowercase(Locale.ROOT)
            .split(TOKEN_REGEX)
            .filter { it.length > 2 && !STOPWORDS.contains(it) }
            .toSet()

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var inter = 0
        // Optimize set intersection manually to avoid temporary object allocation overhead
        for (item in a) {
            if (b.contains(item)) inter++
        }
        val union = a.size + b.size - inter
        return if (union == 0) 0f else inter.toFloat() / union.toFloat()
    }
}
