package com.example.woodsreader.core.web2epub.novel

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SiteProfileRegistryTest {

    private val sampleJson = """
    {
      "id": "novelusb",
      "domains": ["novelusb.com", "novelusb.net"],
      "chapterListSelector": "#list-chapter",
      "contentSelectors": ["#chr-content"],
      "novelTitleSelector": "h3.title",
      "titleSelector": "h2",
      "coverSelector": "div.book img",
      "descriptionSelector": ".desc-text"
    }
    """.trimIndent()

    @Test
    fun `parses valid site profile json correctly`() {
        val profile = SiteProfileRegistry.parse(sampleJson)
        assertEquals("novelusb", profile.id)
        assertEquals(listOf("novelusb.com", "novelusb.net"), profile.domains)
        assertEquals("#list-chapter", profile.chapterListSelector)
        assertEquals(listOf("#chr-content"), profile.contentSelectors)
        assertEquals("h3.title", profile.novelTitleSelector)
        assertEquals("h2", profile.titleSelector)
        assertEquals("div.book img", profile.coverSelector)
        assertEquals(".desc-text", profile.descriptionSelector)
    }

    @Test
    fun `exact domain match returns registered profile`() {
        val profile = SiteProfileRegistry.parse(sampleJson)
        val registry = SiteProfileRegistry(listOf(profile))

        val matched = registry.forUrl("https://novelusb.com/novel/read-chapter-1")
        assertEquals("novelusb", matched.id)
        assertEquals("#list-chapter", matched.chapterListSelector)
    }

    @Test
    fun `subdomain match returns registered profile`() {
        val profile = SiteProfileRegistry.parse(sampleJson)
        val registry = SiteProfileRegistry(listOf(profile))

        val matched = registry.forUrl("https://m.novelusb.com/novel/read-chapter-1")
        assertEquals("novelusb", matched.id)
    }

    @Test
    fun `unregistered domain returns default profile`() {
        val profile = SiteProfileRegistry.parse(sampleJson)
        val registry = SiteProfileRegistry(listOf(profile))

        val matched = registry.forUrl("https://unknown-novel-site.org/index")
        assertEquals(SiteProfile.DEFAULT.id, matched.id)
        assertEquals(SiteProfile.DEFAULT.contentSelectors, matched.contentSelectors)
    }

    @Test
    fun `json with unknown fields parses successfully ignoring unknown fields`() {
        val jsonWithExtra = """
        {
          "id": "custom",
          "domains": ["custom.org"],
          "chapterListSelector": "#toc",
          "extraField1": 12345,
          "nestedExtra": { "foo": "bar" }
        }
        """.trimIndent()

        val profile = SiteProfileRegistry.parse(jsonWithExtra)
        assertEquals("custom", profile.id)
        assertEquals(listOf("custom.org"), profile.domains)
        assertEquals("#toc", profile.chapterListSelector)
    }

    @Test
    fun `invalid json throws controlled exception in parse`() {
        val malformedJson = "{ id: missing_quotes, broken... "
        try {
            SiteProfileRegistry.parse(malformedJson)
            fail("Expected SerializationException or IllegalArgumentException")
        } catch (e: Exception) {
            assert(e is SerializationException || e is IllegalArgumentException)
        }
    }
}
