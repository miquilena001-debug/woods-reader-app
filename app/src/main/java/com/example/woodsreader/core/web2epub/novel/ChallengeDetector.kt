package com.example.woodsreader.core.web2epub.novel

object ChallengeDetector {

    private val CHALLENGE_TITLE_KEYWORDS = listOf(
        "just a moment...",
        "attention required!",
        "security check",
        "cloudflare"
    )

    fun looksLikeChallenge(status: Int, headers: Map<String, String>, html: String): Boolean {
        val serverHeader = headers.entries
            .firstOrNull { it.key.equals("server", ignoreCase = true) }
            ?.value?.lowercase() ?: ""

        // 1. Códigos HTTP de bloqueo de Cloudflare
        if ((status == 403 || status == 503) && serverHeader.contains("cloudflare")) {
            return true
        }

        if (html.isBlank()) return false

        val sample = html.take(15 * 1024).lowercase()

        // 2. Título de página característico de Cloudflare Turnstile / Challenge
        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(sample)
        val titleText = titleMatch?.groupValues?.getOrNull(1)?.trim()?.lowercase() ?: ""
        if (CHALLENGE_TITLE_KEYWORDS.any { titleText.contains(it) }) {
            return true
        }

        // 3. Elementos interactivos activos de desafío (Turnstile o challenge-running)
        if (sample.contains("id=\"challenge-running\"") ||
            sample.contains("id=\"challenge-stage\"") ||
            sample.contains("class=\"cf-turnstile\"") ||
            sample.contains("id=\"turnstile-wrapper\"")
        ) {
            return true
        }

        return false
    }
}
