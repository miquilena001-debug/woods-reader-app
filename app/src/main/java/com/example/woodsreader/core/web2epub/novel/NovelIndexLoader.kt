package com.example.woodsreader.core.web2epub.novel

import org.jsoup.Jsoup
import java.net.URI

data class ChapterRef(
    val index: Int,
    val title: String,
    val url: String
)

data class NovelIndex(
    val indexUrl: String,
    val title: String,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val language: String,
    val chapters: List<ChapterRef>,
    val looksReversed: Boolean,
    val profileId: String,
    val warnings: List<String>
)

class NovelIndexLoader(
    private val fetcher: PageFetcher,
    private val registry: SiteProfileRegistry
) {

    private val numberRegex = Regex("""\d+""")

    suspend fun load(url: String): NovelIndex {
        val profile = registry.forUrl(url)
        val warnings = mutableListOf<String>()

        val page = fetcher.get(url)
        val html = CharsetDetector.decode(page.bytes, page.contentType)
        val doc = Jsoup.parse(html, page.finalUrl)

        // Si la URL pasada es directamente la de un capítulo, buscar el enlace a la novela principal
        // a través de clases como .booktitle, a[href*='/novel/'], a[href*='/book/'], breadcrumbs, etc.
        var mainDoc = doc
        var mainPageUrl = page.finalUrl

        if (mainPageUrl.contains("/chapter-", ignoreCase = true) || url.contains("/chapter-", ignoreCase = true)) {
            val bookLink = doc.select("a.booktitle, a.novel-title, a[href*='/novel/']:not([href*='/chapter-']), a[href*='/book/']:not([href*='/chapter-'])")
                .firstOrNull()?.attr("abs:href")?.trim()
                ?.substringBefore("?")
                ?: run {
                    // Buscar en scripts JSON-LD BreadcrumbList
                    val breadcrumbMatch = Regex("""item["']\s*:\s*["']([^"']*(?:/book/|/novel/)[^"'/]+)["']""")
                        .find(html)
                    breadcrumbMatch?.groupValues?.get(1)?.trim()
                }

            if (!bookLink.isNullOrBlank() && bookLink != mainPageUrl) {
                try {
                    val mainPage = fetcher.get(bookLink, referer = mainPageUrl)
                    mainDoc = Jsoup.parse(CharsetDetector.decode(mainPage.bytes, mainPage.contentType), mainPage.finalUrl)
                    mainPageUrl = mainPage.finalUrl
                } catch (e: Exception) {
                    warnings.add("No se pudo navegar a la portada de la novela desde el capítulo: ${e.message}")
                }
            }
        }

        // Si la URL principal contiene un enlace explícito a "/chapters" (ej. NovelFire, NovelPhoenix),
        // o si es una página de detalles que deriva su lista de capítulos a otra subpágina:
        var chapterDoc = mainDoc
        var chapterFinalUrl = mainPageUrl

        val chaptersNavHref = mainDoc.select("a.chapter-latest-container, a[href$='/chapters']").firstOrNull()?.attr("abs:href")?.trim()
            ?: if ((mainPageUrl.contains("/book/", ignoreCase = true) || mainPageUrl.contains("/novel/", ignoreCase = true)) && !mainPageUrl.contains("/chapter-", ignoreCase = true) && !mainPageUrl.endsWith("/chapters")) {
                mainPageUrl.trimEnd('/') + "/chapters"
            } else null

        if (!chaptersNavHref.isNullOrBlank() && chaptersNavHref != mainPageUrl) {
            try {
                val chPage = fetcher.get(chaptersNavHref, referer = mainPageUrl)
                chapterDoc = Jsoup.parse(CharsetDetector.decode(chPage.bytes, chPage.contentType), chPage.finalUrl)
                chapterFinalUrl = chPage.finalUrl
            } catch (e: Exception) {
                warnings.add("No se pudo cargar la subpágina de capítulos: ${e.message}")
            }
        }

        // 4. Metadatos (usar mainDoc para garantizar que portada, autor y descripción pertenezcan a la novela)
        // Título: novelTitleSelector -> meta[property=og:title] -> <title> antes de ' - '/'|'/'–'
        val rawTitle = if (!profile.novelTitleSelector.isNullOrBlank()) {
            mainDoc.select(profile.novelTitleSelector).firstOrNull()?.text()?.trim()
        } else null

        val title = rawTitle?.takeIf { it.isNotBlank() }
            ?: mainDoc.select("meta[property=og:title]").attr("content").trim().takeIf { it.isNotBlank() }
            ?: cleanTitle(mainDoc.title())
            ?: "Novela Web"

        // Autor: authorSelector -> meta[name=author] -> [itemprop=author]
        val rawAuthor = if (!profile.authorSelector.isNullOrBlank()) {
            profile.authorSelector.split(",")
                .map { it.trim() }
                .firstNotNullOfOrNull { sel ->
                    mainDoc.select(sel).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
                }
        } else null

        val author = rawAuthor?.takeIf { it.isNotBlank() }
            ?: mainDoc.select("meta[name=author]").attr("content").trim().takeIf { it.isNotBlank() }
            ?: mainDoc.select("[itemprop=author]").firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }

        // Descripción: descriptionSelector -> meta[property=og:description]
        val rawDesc = if (!profile.descriptionSelector.isNullOrBlank()) {
            profile.descriptionSelector.split(",")
                .map { it.trim() }
                .firstNotNullOfOrNull { sel ->
                    mainDoc.select(sel).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
                }
        } else null

        val description = rawDesc?.takeIf { it.isNotBlank() }
            ?: mainDoc.select("meta[property=og:description]").attr("content").trim().takeIf { it.isNotBlank() }
            ?: mainDoc.select("meta[name=description]").attr("content").trim().takeIf { it.isNotBlank() }

        // Portada: coverSelector (abs:src o abs:data-src) -> meta[property=og:image] absUrl("content")
        val coverUrl = if (!profile.coverSelector.isNullOrBlank()) {
            val img = mainDoc.select(profile.coverSelector).firstOrNull()
            img?.attr("abs:src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("abs:data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("abs:data-original")?.takeIf { it.isNotBlank() }
        } else null

        val finalCoverUrl = coverUrl?.takeIf { it.isNotBlank() }
            ?: mainDoc.select("meta[property=og:image]").firstOrNull()?.absUrl("content")?.takeIf { it.isNotBlank() }
            ?: mainDoc.select("meta[name=twitter:image]").firstOrNull()?.absUrl("content")?.takeIf { it.isNotBlank() }

        // Idioma: primeras 2 letras de html[lang] en minúsculas, default "en"
        val rawLang = doc.select("html").attr("lang").trim().lowercase()
        val language = if (rawLang.length >= 2) rawLang.take(2) else "en"

        // 2. Capítulos
        val rawChapterList = mutableListOf<Pair<String, String>>() // title to url

        fun extractLinksFrom(targetDoc: org.jsoup.nodes.Document, targetUrl: String) {
            val chapterListContainer = if (!profile.chapterListSelector.isNullOrBlank()) {
                targetDoc.select(profile.chapterListSelector).firstOrNull()
            } else null

            if (chapterListContainer == null && !profile.chapterListSelector.isNullOrBlank()) {
                warnings.add("No se encontró el contenedor de capítulos con selector: ${profile.chapterListSelector}")
            }

            val searchScope = chapterListContainer ?: if (profile.chapterListSelector.isNullOrBlank()) targetDoc else null

            if (searchScope != null) {
                val links = searchScope.select(profile.chapterLinkSelector)
                for (link in links) {
                    val href = link.attr("abs:href").trim()
                    if (isValidChapterUrl(href, targetUrl)) {
                        // Extraer texto limpio: si contiene un sub-elemento como .chapter-title o strong, preferirlo
                        val specificTitle = link.select(".chapter-title, strong, .name").firstOrNull()?.text()?.trim()
                        val rawText = link.text().trim()
                        // Limpiar timestamps pegados como '6 months ago', '4 days ago', etc.
                        val cleanTitle = (specificTitle?.takeIf { it.isNotBlank() } ?: rawText)
                            .replace(Regex("""\d+\s+(months?|days?|hours?|years?|minutes?|weeks?)\s+ago""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""^\d+\s*Chapter""", RegexOption.IGNORE_CASE), "Chapter")
                            .trim()

                        rawChapterList.add(cleanTitle to href)
                    }
                }
            }
        }

        extractLinksFrom(chapterDoc, chapterFinalUrl)

        // Soporte de paginación para la lista de capítulos (ej. NovelFire, NovelPhoenix ?page=2, ?page=3...)
        val paginationLinks = mutableSetOf<String>()
        val initialPages = mutableListOf<String>()

        initialPages.addAll(
            chapterDoc.select(".pagination a[href], .pagenav a[href], a[href*='page=']")
                .map { it.attr("abs:href").trim() }
                .filter { it.isNotBlank() && it != chapterFinalUrl && it.contains("page=") }
        )

        // Soportar también <select> con opciones de rango de páginas (?page=2, ?page=3...)
        val selectOptions = chapterDoc.select("select option[value*='page=']")
            .map { it.attr("value").trim() }
            .map { valOpt ->
                try {
                    URI(chapterFinalUrl).resolve(valOpt).toString()
                } catch (_: Exception) {
                    valOpt
                }
            }
            .filter { it.isNotBlank() && it != chapterFinalUrl && it.contains("page=") }
        initialPages.addAll(selectOptions)

        paginationLinks.addAll(initialPages)

        // Si hay paginación numérica, recorrer todas las páginas encontradas
        val visitedPages = mutableSetOf(chapterFinalUrl)
        while (true) {
            val unvisited = paginationLinks.filter { !visitedPages.contains(it) }
            if (unvisited.isEmpty()) break

            for (pageUrl in unvisited) {
                visitedPages.add(pageUrl)
                try {
                    val pPage = fetcher.get(pageUrl, referer = chapterFinalUrl)
                    val pDoc = Jsoup.parse(CharsetDetector.decode(pPage.bytes, pPage.contentType), pPage.finalUrl)
                    extractLinksFrom(pDoc, pPage.finalUrl)

                    // Descubrir nuevas páginas en caso de paginador deslizante (1, 2, 3 ... 8)
                    val newLinks = pDoc.select(".pagination a[href], .pagenav a[href], a[href*='page=']")
                        .map { it.attr("abs:href").trim() }
                        .filter { it.isNotBlank() && it.contains("page=") }
                    paginationLinks.addAll(newLinks)

                    val newSelectOptions = pDoc.select("select option[value*='page=']")
                        .map { it.attr("value").trim() }
                        .map { valOpt ->
                            try {
                                URI(pPage.finalUrl).resolve(valOpt).toString()
                            } catch (_: Exception) {
                                valOpt
                            }
                        }
                        .filter { it.isNotBlank() && it.contains("page=") }
                    paginationLinks.addAll(newSelectOptions)
                } catch (_: Exception) {
                }
            }
        }

        // Deduplicar por URL conservando el primer orden
        val deduplicated = LinkedHashMap<String, String>()
        for ((cTitle, cHref) in rawChapterList) {
            if (!deduplicated.containsKey(cHref)) {
                deduplicated[cHref] = cTitle
            }
        }

        var orderedEntries = deduplicated.entries.toList()
        if (profile.reverseToc) {
            orderedEntries = orderedEntries.reversed()
        }

        // 3. looksReversed
        val looksReversed = if (orderedEntries.size >= 2) {
            val firstNum = numberRegex.find(orderedEntries.first().value)?.value?.toIntOrNull()
            val lastNum = numberRegex.find(orderedEntries.last().value)?.value?.toIntOrNull()
            firstNum != null && lastNum != null && firstNum > lastNum
        } else false

        // Numerar index desde 1
        val chapters = orderedEntries.mapIndexed { idx, entry ->
            val indexNum = idx + 1
            val chapTitle = entry.value.ifBlank { "Capítulo $indexNum" }
            ChapterRef(
                index = indexNum,
                title = chapTitle,
                url = entry.key
            )
        }

        if (chapters.isEmpty()) {
            warnings.add("La lista de capítulos está vacía")
        }

        // 5. Paginación heurística en v1
        if (doc.select(".pagination, .pager, a.next_page, .nav-links").isNotEmpty()) {
            warnings.add("El índice puede estar paginado; se cargaron ${chapters.size} capítulos")
        }

        return NovelIndex(
            indexUrl = url,
            title = title,
            author = author,
            description = description,
            coverUrl = finalCoverUrl,
            language = language,
            chapters = chapters,
            looksReversed = looksReversed,
            profileId = profile.id,
            warnings = warnings
        )
    }

    private fun cleanTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.split(Regex("""\s*[-–—|]\s*""")).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun isValidChapterUrl(url: String, indexUrl: String): Boolean {
        if (url.isBlank()) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (url.startsWith("javascript:", ignoreCase = true)) return false

        // Ignorar enlace idéntico al índice o sólo fragmento
        val normalizedUrl = url.substringBefore('#')
        val normalizedIndex = indexUrl.substringBefore('#')
        if (normalizedUrl.equals(normalizedIndex, ignoreCase = true)) return false

        // Si la URL contiene /genre, /ranking, /latest, /search, o no contiene /chapter o un patrón de capítulo numérico, descartar
        val path = uri.path?.lowercase() ?: ""
        if (path.contains("/genre") || path.contains("/ranking") || path.contains("/latest") || path.contains("/account")) {
            return false
        }

        return true
    }
}
