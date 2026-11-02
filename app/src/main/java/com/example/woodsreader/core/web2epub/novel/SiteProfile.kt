package com.example.woodsreader.core.web2epub.novel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
data class SiteProfile(
    val id: String,
    val domains: List<String> = emptyList(),
    val chapterListSelector: String? = null,
    val chapterLinkSelector: String = "a[href]",
    val reverseToc: Boolean = false,
    val contentSelectors: List<String> = emptyList(),
    val titleSelector: String? = null,
    val novelTitleSelector: String? = null,
    val removeSelectors: List<String> = emptyList(),
    val removeTextContains: List<String> = emptyList(),
    val coverSelector: String? = null,
    val authorSelector: String? = null,
    val descriptionSelector: String? = null,
    val nextLinkSelector: String? = null,
    val needsWebView: Boolean = false,
    val minDelayMs: Long = 1000L,
    val maxDelayMs: Long = 3000L
) {
    companion object {
        val DEFAULT = SiteProfile(
            id = "default",
            domains = emptyList(),
            chapterListSelector = null,
            chapterLinkSelector = "a[href]",
            reverseToc = false,
            contentSelectors = listOf(
                "#chr-content",
                "#chapter-content",
                ".chapter-c",
                ".reading-content",
                ".text-left",
                ".entry-content",
                ".post-content",
                ".chapter-inner",
                "#chapter-container",
                "article"
            ),
            titleSelector = "h1, h2.chapter-title, .chapter-title, .entry-title, .chr-title",
            novelTitleSelector = null,
            removeSelectors = listOf(
                "script",
                "style",
                "noscript",
                "nav",
                "header",
                "footer",
                "iframe",
                "svg",
                "button",
                "form",
                ".ads",
                ".adsbygoogle",
                ".sharedaddy",
                ".advertisement",
                ".comment-respond",
                ".post-navigation",
                ".tr-comment",
                ".wp-block-buttons"
            ),
            removeTextContains = listOf(
                "report chapter",
                "patreon.com",
                "window.adsbygoogle",
                "google-auto-placed"
            ),
            coverSelector = null,
            authorSelector = null,
            descriptionSelector = null,
            nextLinkSelector = "a[rel='next'], a.next_page, a#next_chap, a.btn-next, a#next-btn, a.next-chapter",
            needsWebView = false,
            minDelayMs = 1000L,
            maxDelayMs = 3000L
        )
    }
}

class SiteProfileRegistry(val profiles: List<SiteProfile>) {

    fun toAdapterRegistry(): SiteAdapterRegistry =
        SiteAdapterRegistry.fromProfiles(assetProfiles = profiles)

    fun forUrl(url: String): SiteProfile {
        val host = extractHost(url)
        if (host.isEmpty()) return SiteProfile.DEFAULT

        return profiles.firstOrNull { profile ->
            profile.domains.any { d ->
                val domain = d.lowercase().trim()
                domain.isNotEmpty() && (host == domain || host.endsWith(".$domain"))
            }
        } ?: SiteProfile.DEFAULT
    }

    private fun extractHost(url: String): String {
        return try {
            val trimmed = url.trim()
            val withScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
            URI(withScheme).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private val jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun parse(json: String): SiteProfile {
            return jsonFormat.decodeFromString(SiteProfile.serializer(), json)
        }
    }
}
