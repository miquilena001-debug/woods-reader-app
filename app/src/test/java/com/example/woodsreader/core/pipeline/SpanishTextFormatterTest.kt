package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpanishTextFormatterTest {

    private lateinit var formatter: SpanishTextFormatter

    @Before
    fun setUp() {
        formatter = SpanishTextFormatter()
    }

    @Test
    fun `bindOrphanPrepositions replaces regular space after monosyllables with non-breaking space`() {
        val input = "Caminó hacia la casa de madera con su hermano."
        val output = formatter.bindOrphanPrepositions(input)

        // Monosílabos como "la", "de", "con", "su" deben tener \u00A0 detrás
        assertTrue(output.contains("hacia\u00A0la\u00A0casa"))
        assertTrue(output.contains("de\u00A0madera"))
        assertTrue(output.contains("con\u00A0su"))
    }

    @Test
    fun `normalizeTypography replaces double hyphen with em dash and trims spaces`() {
        val input = "Ella dijo: --No tengo miedo-- mientras sonreía."
        val output = formatter.normalizeTypography(input)

        assertEquals("Ella dijo: —No tengo miedo— mientras sonreía.", output)
    }

    @Test
    fun `normalizeTypography cleans spaces in Spanish inverted marks and Latin quotes`() {
        val input = "¿  Cómo estás ? ¡  Qué sorpresa ! Y dijo «  hola  » con entusiasmo."
        val output = formatter.normalizeTypography(input)

        assertEquals("¿Cómo estás ? ¡Qué sorpresa ! Y dijo «hola» con entusiasmo.", output)
    }

    @Test
    fun `formatParagraph runs complete typographic pipeline`() {
        val input = "En un lugar de la Mancha, de cuyo nombre no quiero acordarme..."
        val output = formatter.formatParagraph(input)

        assertTrue(output.startsWith("En\u00A0un\u00A0lugar"))
        assertTrue(output.contains("de\u00A0la\u00A0Mancha"))
    }
}
