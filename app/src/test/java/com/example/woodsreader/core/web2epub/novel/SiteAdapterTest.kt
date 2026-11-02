package com.example.woodsreader.core.web2epub.novel

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SiteAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val userProfile = SiteProfile(
        id = "user-profile",
        domains = listOf("example.com", "user.site")
    )

    private val assetProfile = SiteProfile(
        id = "asset-profile",
        domains = listOf("example.com", "asset.site")
    )

    private val codeAdapter = object : SiteAdapter {
        override val id: String = "code-adapter"
        override fun matches(url: String): Boolean = url.contains("example.com") || url.contains("code.site")
        override fun normalizeUrl(url: String): String = "$url?normalized=1"
        override fun indexUrl(url: String): String = "$url/navigate"
        override fun requestHeaders(url: String): Map<String, String> = mapOf("X-Test" to "1")
        override suspend fun parseIndex(html: String, finalUrl: String, fetcher: PageFetcher?): NovelIndex {
            return NovelIndex(finalUrl, "Code Title", "Code Author", null, null, "es", emptyList(), false, id, emptyList())
        }
        override fun extractChapter(html: String, finalUrl: String, fallbackTitle: String?): ExtractedChapter {
            return ExtractedChapter("Code Ch", "<p>Code</p>", 1, emptyList(), ChapterStatus.OK)
        }
        override val profile: SiteProfile = SiteProfile(id = "code-adapter", domains = listOf("code.site"))
    }

    @Test
    fun `ProfileSiteAdapter matches domain and subdomains correctly`() {
        val adapter = ProfileSiteAdapter(userProfile)
        assertEquals("user-profile", adapter.id)
        assertTrue(adapter.matches("https://example.com/novel/1"))
        assertTrue(adapter.matches("https://sub.example.com/novel/1"))
        assertTrue(adapter.matches("https://user.site/book"))
        assertFalse(adapter.matches("https://other.com/book"))
        assertEquals("https://example.com/1", adapter.normalizeUrl("https://example.com/1"))
        assertEquals("https://example.com/1", adapter.indexUrl("https://example.com/1"))
        assertTrue(adapter.requestHeaders("https://example.com").isEmpty())
    }

    @Test
    fun `SiteAdapterRegistry priority resolution user wins over code and assets`() {
        val registry = SiteAdapterRegistry.fromProfiles(
            userProfiles = listOf(userProfile),
            codeAdapters = listOf(codeAdapter),
            assetProfiles = listOf(assetProfile)
        )

        // example.com coincide con usuario, código y asset -> DEBE ganar usuario
        val matched = registry.forUrl("https://example.com/story")
        assertEquals("user-profile", matched.id)
    }

    @Test
    fun `SiteAdapterRegistry priority resolution code wins over assets`() {
        val registry = SiteAdapterRegistry.fromProfiles(
            userProfiles = emptyList(),
            codeAdapters = listOf(codeAdapter),
            assetProfiles = listOf(assetProfile)
        )

        // example.com coincide con código y asset -> DEBE ganar código
        val matched = registry.forUrl("https://example.com/story")
        assertEquals("code-adapter", matched.id)
    }

    @Test
    fun `SiteAdapterRegistry priority resolution assets wins over default`() {
        val registry = SiteAdapterRegistry.fromProfiles(
            userProfiles = emptyList(),
            codeAdapters = emptyList(),
            assetProfiles = listOf(assetProfile)
        )

        val matched = registry.forUrl("https://asset.site/story")
        assertEquals("asset-profile", matched.id)
    }

    @Test
    fun `SiteAdapterRegistry fallback to DEFAULT when no match`() {
        val registry = SiteAdapterRegistry.fromProfiles(
            userProfiles = listOf(userProfile),
            codeAdapters = listOf(codeAdapter),
            assetProfiles = listOf(assetProfile)
        )

        val matched = registry.forUrl("https://unregistered-domain.org/story")
        assertEquals("default", matched.id)
        assertEquals(SiteProfile.DEFAULT.id, matched.profile.id)
    }

    @Test
    fun `ProfileSiteAdapter delegates extractChapter and parseIndex correctly`() = runBlocking {
        val profile = SiteProfile(
            id = "test-extract",
            domains = listOf("test.org"),
            contentSelectors = listOf("#test-body"),
            titleSelector = "h1.title"
        )
        val adapter = ProfileSiteAdapter(profile)

        val chapterHtml = "<html><body><h1 class='title'>Capítulo Test</h1><div id='test-body'><p>Hola mundo</p></div></body></html>"
        val extracted = adapter.extractChapter(chapterHtml, "https://test.org/ch1", fallbackTitle = "Fallback")
        assertEquals("Capítulo Test", extracted.title)
        assertTrue(extracted.bodyXhtml.contains("Hola mundo"))
        // Menos de 100 palabras sin imágenes es considerado SHORT según regla de ContentExtractor
        assertEquals(ChapterStatus.SHORT, extracted.status)

        val indexHtml = "<html><head><meta property='og:title' content='Novela Test'></head><body><div id='chapter-list'><a href='https://test.org/ch1'>Capítulo 1</a></div></body></html>"
        val index = adapter.parseIndex(indexHtml, "https://test.org/index")
        assertEquals("Novela Test", index.title)
    }

    @Test
    fun `invalid user JSON files are safely ignored without throwing`() {
        val extraFolder = tempFolder.newFolder("user_profiles")

        // Archivo JSON válido
        File(extraFolder, "custom.json").writeText(
            """{"id": "custom-site", "domains": ["custom.org"]}""",
            Charsets.UTF_8
        )

        // Archivo JSON corrupto
        File(extraFolder, "corrupt.json").writeText(
            """{ id: corrupted not valid json }""",
            Charsets.UTF_8
        )

        val mockContext = mockk<android.content.Context>()
        every { mockContext.getExternalFilesDir(any()) } returns null
        every { mockContext.assets.list("site_profiles") } returns emptyArray()

        val loader = SiteProfileLoader(
            context = mockContext,
            extraDirs = listOf(extraFolder)
        )

        val registry = loader.loadRegistry()
        val matched = registry.forUrl("https://custom.org/book")
        assertEquals("custom-site", matched.id)
    }
}
