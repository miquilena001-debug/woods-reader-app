package com.example.woodsreader.core.web2epub.novel

/**
 * Contrato extensible para la extracción y adaptación de sitios web a novelas.
 * No gestiona la descarga de capítulos: el pipeline común se encarga de ritmo,
 * robots, reanudación, retos Cloudflare y descarga de imágenes.
 */
interface SiteAdapter {
    val id: String
    fun matches(url: String): Boolean
    fun normalizeUrl(url: String): String = url                       // p.ej. añadir view_adult=true
    fun indexUrl(url: String): String = url                            // p.ej. AO3 -> /navigate
    fun requestHeaders(url: String): Map<String, String> = emptyMap()  // cookies/headers por sitio
    suspend fun parseIndex(html: String, finalUrl: String, fetcher: PageFetcher? = null): NovelIndex
    fun extractChapter(html: String, finalUrl: String, fallbackTitle: String? = null): ExtractedChapter
    val profile: SiteProfile                                           // ritmo, needsWebView, reverseToc
}
