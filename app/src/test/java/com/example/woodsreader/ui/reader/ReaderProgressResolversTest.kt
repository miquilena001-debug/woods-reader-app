package com.example.woodsreader.ui.reader

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Locator

class ReaderProgressResolversTest {

    private fun mockLoc(href: String = "test.html"): Locator {
        val loc = mockk<Locator>(relaxed = true)
        val url = mockk<org.readium.r2.shared.util.Url>(relaxed = true)
        every { url.toString() } returns href
        every { loc.href } returns url
        return loc
    }

    // =========================================================================
    // resolveFlushCurrentLocator
    // =========================================================================

    @Test
    fun resolveFlushCurrentLocator_ttsActive_returnsTtsLocatorAndDoesNotCallParser() {
        val ttsLoc = mockLoc("tts.html")
        val navLoc = mockLoc("nav.html")
        var parserCalled = false

        val result = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = ttsLoc,
            ttsIsActive = true,
            navLocator = navLoc,
            annotationsLocator = mockLoc("annot.html"),
            lastSavedLocatorJson = "{\"href\":\"saved.html\"}",
            parseLocator = { parserCalled = true; mockLoc("parsed.html") }
        )

        assertEquals(ttsLoc, result)
        assertFalse("parseLocator no debe ser invocado si ttsLocator resuelve", parserCalled)
    }

    @Test
    fun resolveFlushCurrentLocator_ttsInactive_ignoresTtsLocatorAndTakesNavLocator() {
        val ttsLoc = mockLoc("tts.html")
        val navLoc = mockLoc("nav.html")
        var parserCalled = false

        val result = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = ttsLoc,
            ttsIsActive = false,
            navLocator = navLoc,
            annotationsLocator = mockLoc("annot.html"),
            lastSavedLocatorJson = "{\"href\":\"saved.html\"}",
            parseLocator = { parserCalled = true; mockLoc("parsed.html") }
        )

        assertEquals(navLoc, result)
        assertFalse("parseLocator no debe ser invocado", parserCalled)
    }

    @Test
    fun resolveFlushCurrentLocator_navAndTtsNull_returnsAnnotationsLocator() {
        val annotLoc = mockLoc("annot.html")
        var parserCalled = false

        val result = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = null,
            ttsIsActive = false,
            navLocator = null,
            annotationsLocator = annotLoc,
            lastSavedLocatorJson = "{\"href\":\"saved.html\"}",
            parseLocator = { parserCalled = true; mockLoc("parsed.html") }
        )

        assertEquals(annotLoc, result)
        assertFalse("parseLocator no debe ser invocado", parserCalled)
    }

    @Test
    fun resolveFlushCurrentLocator_memoryLocatorsNull_parsesLastSavedJson() {
        val parsedLoc = mockLoc("parsed.html")
        var parsedJsonArg: String? = null

        val result = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = null,
            ttsIsActive = false,
            navLocator = null,
            annotationsLocator = null,
            lastSavedLocatorJson = "{\"href\":\"saved.html\"}",
            parseLocator = { parsedJsonArg = it; parsedLoc }
        )

        assertEquals(parsedLoc, result)
        assertEquals("{\"href\":\"saved.html\"}", parsedJsonArg)
    }

    @Test
    fun resolveFlushCurrentLocator_savedJsonNull_returnsNullWithoutCallingParser() {
        var parserCalled = false

        val result = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = null,
            ttsIsActive = false,
            navLocator = null,
            annotationsLocator = null,
            lastSavedLocatorJson = null,
            parseLocator = { parserCalled = true; mockLoc("parsed.html") }
        )

        assertNull(result)
        assertFalse("parseLocator no debe llamarse si lastSavedLocatorJson es nulo", parserCalled)
    }

    @Test
    fun resolveFlushCurrentLocator_parserReturnsNull_returnsNull() {
        var parserCalled = false

        val result = ReaderProgressResolvers.resolveFlushCurrentLocator(
            ttsLocator = null,
            ttsIsActive = false,
            navLocator = null,
            annotationsLocator = null,
            lastSavedLocatorJson = "corrupt",
            parseLocator = { parserCalled = true; null }
        )

        assertNull(result)
        assertTrue("parseLocator debe haber sido invocado", parserCalled)
    }

    // =========================================================================
    // resolveFallbackLocator
    // =========================================================================

    @Test
    fun resolveFallbackLocator_navLocatorAvailable_returnsNavAndDoesNotCallLambdas() {
        val navLoc = mockLoc("nav.html")
        var parseInitialCalled = false
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = navLoc,
            lastTtsLocator = mockLoc("last.html"),
            currentTtsLocator = mockLoc("cur.html"),
            initialLocatorJson = "{\"href\":\"init.html\"}",
            parseInitial = { parseInitialCalled = true; mockLoc("init.html") },
            firstLinkLocator = { firstLinkCalled = true; mockLoc("first.html") }
        )

        assertEquals(navLoc, result)
        assertFalse("parseInitial no debe ser invocado", parseInitialCalled)
        assertFalse("firstLinkLocator no debe ser invocado", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_navNull_prefersLastTtsLocatorOverCurrent() {
        val lastLoc = mockLoc("last.html")
        val curLoc = mockLoc("cur.html")
        var parseInitialCalled = false
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = lastLoc,
            currentTtsLocator = curLoc,
            initialLocatorJson = "{\"href\":\"init.html\"}",
            parseInitial = { parseInitialCalled = true; mockLoc("init.html") },
            firstLinkLocator = { firstLinkCalled = true; mockLoc("first.html") }
        )

        assertEquals(lastLoc, result)
        assertFalse("parseInitial no debe ser invocado", parseInitialCalled)
        assertFalse("firstLinkLocator no debe ser invocado", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_navAndLastTtsNull_returnsCurrentTtsLocator() {
        val curLoc = mockLoc("cur.html")
        var parseInitialCalled = false
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = null,
            currentTtsLocator = curLoc,
            initialLocatorJson = "{\"href\":\"init.html\"}",
            parseInitial = { parseInitialCalled = true; mockLoc("init.html") },
            firstLinkLocator = { firstLinkCalled = true; mockLoc("first.html") }
        )

        assertEquals(curLoc, result)
        assertFalse("parseInitial no debe ser invocado", parseInitialCalled)
        assertFalse("firstLinkLocator no debe ser invocado", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_ttsNull_parsesInitialJsonLazily() {
        val initLoc = mockLoc("init.html")
        var parsedArg: String? = null
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = null,
            currentTtsLocator = null,
            initialLocatorJson = "{\"href\":\"init.html\"}",
            parseInitial = { parsedArg = it; initLoc },
            firstLinkLocator = { firstLinkCalled = true; mockLoc("first.html") }
        )

        assertEquals(initLoc, result)
        assertEquals("{\"href\":\"init.html\"}", parsedArg)
        assertFalse("firstLinkLocator no debe llamarse si initialJson resuelve", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_blankInitialJson_doesNotCallParseInitialAndCallsFirstLink() {
        val firstLoc = mockLoc("first.html")
        var parseInitialCalled = false
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = null,
            currentTtsLocator = null,
            initialLocatorJson = "   ",
            parseInitial = { parseInitialCalled = true; mockLoc("init.html") },
            firstLinkLocator = { firstLinkCalled = true; firstLoc }
        )

        assertEquals(firstLoc, result)
        assertFalse("parseInitial no debe llamarse si el JSON está en blanco", parseInitialCalled)
        assertTrue("firstLinkLocator debe llamarse como fallback", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_nullInitialJson_callsFirstLink() {
        val firstLoc = mockLoc("first.html")
        var parseInitialCalled = false
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = null,
            currentTtsLocator = null,
            initialLocatorJson = null,
            parseInitial = { parseInitialCalled = true; mockLoc("init.html") },
            firstLinkLocator = { firstLinkCalled = true; firstLoc }
        )

        assertEquals(firstLoc, result)
        assertFalse("parseInitial no debe llamarse", parseInitialCalled)
        assertTrue("firstLinkLocator debe llamarse", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_parseInitialReturnsNull_callsFirstLink() {
        val firstLoc = mockLoc("first.html")
        var parseInitialCalled = false
        var firstLinkCalled = false

        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = null,
            currentTtsLocator = null,
            initialLocatorJson = "corrupt",
            parseInitial = { parseInitialCalled = true; null },
            firstLinkLocator = { firstLinkCalled = true; firstLoc }
        )

        assertEquals(firstLoc, result)
        assertTrue("parseInitial debe llamarse", parseInitialCalled)
        assertTrue("firstLinkLocator debe llamarse tras fallo de parseInitial", firstLinkCalled)
    }

    @Test
    fun resolveFallbackLocator_allNull_returnsNull() {
        val result = ReaderProgressResolvers.resolveFallbackLocator(
            navLocator = null,
            lastTtsLocator = null,
            currentTtsLocator = null,
            initialLocatorJson = null,
            parseInitial = { null },
            firstLinkLocator = { null }
        )

        assertNull(result)
    }
}
