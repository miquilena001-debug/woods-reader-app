package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UnsupportedSitesAdapterTest {

    private val adapter = UnsupportedSitesAdapter()

    @Test
    fun `matches returns true for blocked domains and subdomains`() {
        assertTrue(adapter.matches("https://www.wattpad.com/story/123456-example-story"))
        assertTrue(adapter.matches("https://wattpad.com/123456-chapter-1"))
        assertTrue(adapter.matches("https://m.wattpad.com/story/123456"))
    }

    @Test
    fun `matches returns false for other domains or blank urls`() {
        assertFalse(adapter.matches("https://novelfire.net/novel/mech"))
        assertFalse(adapter.matches("https://royalroad.com/fiction/123"))
        assertFalse(adapter.matches(""))
        assertFalse(adapter.matches("invalid-url"))
    }

    @Test
    fun `parseIndex throws FetchFailure UnsupportedSite without calling fetcher`() = runTest {
        val fakeFetcher = FakePageFetcher()

        try {
            adapter.parseIndex("", "https://www.wattpad.com/story/123", fakeFetcher)
            fail("Expected FetchFailure.UnsupportedSite")
        } catch (e: FetchFailure.UnsupportedSite) {
            assertEquals("Sitio no soportado", e.siteName)
            assertEquals("Este sitio no permite la importación automatizada de contenido según sus Términos de Servicio.", e.reason)
        }

        // Verifica que NUNCA se realizó ninguna petición de red
        assertEquals(0, fakeFetcher.callCount)
        assertTrue(fakeFetcher.requestedUrls.isEmpty())
    }

    @Test
    fun `extractChapter throws FetchFailure UnsupportedSite`() {
        try {
            adapter.extractChapter("<html>...</html>", "https://www.wattpad.com/123-ch1", null)
            fail("Expected FetchFailure.UnsupportedSite")
        } catch (e: FetchFailure.UnsupportedSite) {
            assertEquals("Sitio no soportado", e.siteName)
            assertEquals("Este sitio no permite la importación automatizada de contenido según sus Términos de Servicio.", e.reason)
        }
    }

    @Test
    fun `SiteAdapterRegistry resolves blocked domain to UnsupportedSitesAdapter by default`() {
        val registry = SiteAdapterRegistry.fromProfiles()
        val resolved = registry.forUrl("https://www.wattpad.com/story/999999-my-story")
        assertTrue(resolved is UnsupportedSitesAdapter)
        assertEquals("unsupported-sites", resolved.id)
    }
}
