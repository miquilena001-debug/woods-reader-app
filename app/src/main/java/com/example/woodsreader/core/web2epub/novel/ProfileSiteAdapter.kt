package com.example.woodsreader.core.web2epub.novel

import java.net.URI

/**
 * Adaptador que envuelve un [SiteProfile] declarativo (JSON).
 * Permite que los sitios simples sigan soportándose con sólo un archivo de configuración JSON.
 */
class ProfileSiteAdapter(
    override val profile: SiteProfile
) : SiteAdapter {

    override val id: String get() = profile.id

    override fun matches(url: String): Boolean {
        val host = extractHost(url)
        if (host.isEmpty()) return false
        return profile.domains.any { d ->
            val domain = d.lowercase().trim()
            domain.isNotEmpty() && (host == domain || host.endsWith(".$domain"))
        }
    }

    override suspend fun parseIndex(html: String, finalUrl: String, fetcher: PageFetcher?): NovelIndex {
        val effectiveFetcher = fetcher ?: object : PageFetcher {
            override suspend fun get(url: String, referer: String?): FetchedPage {
                return FetchedPage(
                    requestedUrl = url,
                    finalUrl = url,
                    status = 200,
                    bytes = html.toByteArray(Charsets.UTF_8),
                    contentType = "text/html; charset=utf-8",
                    headers = emptyMap()
                )
            }
        }
        val registry = SiteProfileRegistry(listOf(profile))
        return NovelIndexLoader(effectiveFetcher, registry).load(finalUrl)
    }

    override fun extractChapter(html: String, finalUrl: String, fallbackTitle: String?): ExtractedChapter {
        return ContentExtractor(profile).extract(html, finalUrl, fallbackTitle = fallbackTitle ?: "")
    }

    companion object {
        fun extractHost(url: String): String {
            return try {
                val trimmed = url.trim()
                val withScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
                URI(withScheme).host?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }
}
