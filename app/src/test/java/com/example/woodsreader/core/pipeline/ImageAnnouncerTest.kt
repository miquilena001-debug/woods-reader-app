package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ImageAnnouncerTest {

    private lateinit var announcer: ImageAnnouncer

    @Before
    fun setUp() {
        announcer = ImageAnnouncer()
    }

    @Test
    fun `buildNarrationText returns null when announcements are disabled`() {
        val image = ReadingBlock.Image(
            id = "img-1",
            caption = "Mapa de la Tierra Media",
            pageIndex = 1,
            orderIndex = 0
        )
        val result = announcer.buildNarrationText(image, announceImages = false)
        assertNull(result)
    }

    @Test
    fun `buildNarrationText formats caption when available`() {
        val image = ReadingBlock.Image(
            id = "img-1",
            caption = "Esquema del motor a vapor",
            pageIndex = 5,
            orderIndex = 2
        )
        val result = announcer.buildNarrationText(image, announceImages = true)
        assertEquals("Imagen: Esquema del motor a vapor", result)
    }

    @Test
    fun `buildNarrationText falls back to altText when caption is missing`() {
        val image = ReadingBlock.Image(
            id = "img-2",
            caption = null,
            altText = "Retrato del autor",
            pageIndex = 2,
            orderIndex = 1
        )
        val result = announcer.buildNarrationText(image, announceImages = true)
        assertEquals("Imagen: Retrato del autor", result)
    }

    @Test
    fun `buildNarrationText defaults to Ilustracion when no description exists`() {
        val image = ReadingBlock.Image(
            id = "img-3",
            caption = "",
            altText = null,
            pageIndex = 10,
            orderIndex = 4
        )
        val result = announcer.buildNarrationText(image, announceImages = true)
        assertEquals("Ilustración", result)
    }

    @Test
    fun `buildDisplayLabel returns clean UI badge title`() {
        val imageWithCaption = ReadingBlock.Image(id = "1", caption = "Figura 4.2", pageIndex = 1, orderIndex = 0)
        assertEquals("Figura 4.2", announcer.buildDisplayLabel(imageWithCaption))

        val imageWithoutCaption = ReadingBlock.Image(id = "2", caption = null, altText = null, pageIndex = 1, orderIndex = 0)
        assertEquals("Figura sin descripción", announcer.buildDisplayLabel(imageWithoutCaption))
    }

    @Test
    fun `hasDescriptiveText detects presence of metadata`() {
        val withDesc = ReadingBlock.Image(id = "1", caption = "Foto histórica", pageIndex = 1, orderIndex = 0)
        val withoutDesc = ReadingBlock.Image(id = "2", caption = "   ", altText = null, pageIndex = 1, orderIndex = 0)
        
        assertTrue(announcer.hasDescriptiveText(withDesc))
        assertFalse(announcer.hasDescriptiveText(withoutDesc))
    }
}
