package com.example.woodsreader.core.web2epub.novel

import java.net.URI

class WattpadSiteAdapter : SiteAdapter {
    override val id: String = "wattpad"

    override fun matches(url: String): Boolean {
        if (url.isBlank()) return false
        val host = try {
            URI(url.trim()).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: ""
        return host == "wattpad.com" || host.endsWith(".wattpad.com")
    }

    override suspend fun parseIndex(html: String, finalUrl: String, fetcher: PageFetcher?): NovelIndex {
        throw FetchFailure.UnsupportedSite(
            siteName = "Wattpad",
            reason = "Wattpad no permite la importación automatizada de contenido según sus Términos de Servicio."
        )
    }

    override fun extractChapter(html: String, finalUrl: String, fallbackTitle: String?): ExtractedChapter {
        throw FetchFailure.UnsupportedSite(
            siteName = "Wattpad",
            reason = "Wattpad no permite la importación automatizada de contenido según sus Términos de Servicio."
        )
    }

    override val profile: SiteProfile = SiteProfile.DEFAULT.copy(
        id = "wattpad",
        domains = listOf("wattpad.com")
    )
}
