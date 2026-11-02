package com.example.woodsreader.core.web2epub.novel

import java.net.URI

class UnsupportedSitesAdapter : SiteAdapter {
    override val id: String = "unsupported-sites"

    private val blockedDomains = listOf(
        "wattpad.com"
    )

    override fun matches(url: String): Boolean {
        if (url.isBlank()) return false
        val host = try {
            URI(url.trim()).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: ""
        return blockedDomains.any { host == it || host.endsWith(".$it") }
    }

    override suspend fun parseIndex(html: String, finalUrl: String, fetcher: PageFetcher?): NovelIndex {
        throw FetchFailure.UnsupportedSite(
            siteName = "Sitio no soportado",
            reason = "Este sitio no permite la importación automatizada de contenido según sus Términos de Servicio."
        )
    }

    override fun extractChapter(html: String, finalUrl: String, fallbackTitle: String?): ExtractedChapter {
        throw FetchFailure.UnsupportedSite(
            siteName = "Sitio no soportado",
            reason = "Este sitio no permite la importación automatizada de contenido según sus Términos de Servicio."
        )
    }

    override val profile: SiteProfile = SiteProfile.DEFAULT.copy(
        id = "unsupported-sites"
    )
}
