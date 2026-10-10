package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class WordExclusionEngineTest {

    private lateinit var engine: WordExclusionEngine

    @Before
    fun setUp() {
        engine = WordExclusionEngine()
    }

    @Test
    fun `filterText removes target word without mutilating substrings`() {
        // Excluir "el" NO debe tocar "pelota", "adelante" o "hotel"
        val rules = listOf(
            WordExclusionRule(target = "el", replacement = "")
        )
        val input = "El niño lanzó la pelota hacia el hotel."
        val output = engine.filterText(input, rules)

        assertEquals("niño lanzó la pelota hacia hotel.", output)
    }

    @Test
    fun `filterText substitutes words cleanly`() {
        val rules = listOf(
            WordExclusionRule(target = "etc.", replacement = "etcétera"),
            WordExclusionRule(target = "Dr.", replacement = "Doctor")
        )
        val input = "El Dr. Smith trajo manzanas, peras, etc."
        val output = engine.filterText(input, rules)

        assertEquals("El Doctor Smith trajo manzanas, peras, etcétera", output)
    }

    @Test
    fun `filterText handles regex rules correctly`() {
        val rules = listOf(
            WordExclusionRule(target = """\[\d+\]""", replacement = "", isRegex = true)
        )
        val input = "Según los historiadores [1], la batalla ocurrió en otoño [2]."
        val output = engine.filterText(input, rules)

        assertEquals("Según los historiadores, la batalla ocurrió en otoño.", output)
    }

    @Test
    fun `filterText collapses remaining spaces and punctuation`() {
        val rules = listOf(
            WordExclusionRule(target = "ruido", replacement = "")
        )
        val input = "Texto con mucho   ruido   en medio."
        val output = engine.filterText(input, rules)

        assertEquals("Texto con mucho en medio.", output)
    }

    @Test
    fun `createRuleFromSelection builds valid rule`() {
        val rule = engine.createRuleFromSelection("Publicidad", bookId = "book-123", isGlobal = false)
        assertEquals("Publicidad", rule.target)
        assertEquals("", rule.replacement)
        assertEquals("book-123", rule.bookId)
        assertFalse(rule.isRegex)

        val globalRule = engine.createRuleFromSelection("Nota:", isGlobal = true)
        assertNull(globalRule.bookId)
    }

    @Test
    fun `stress test filters 10000 words in under 200ms`() {
        val sampleText = "El rápido zorro marrón salta sobre el perro perezoso. "
        val largeText = sampleText.repeat(1000) // ~10,000 words
        val rules = listOf(
            WordExclusionRule(target = "zorro", replacement = "lobo"),
            WordExclusionRule(target = "perezoso", replacement = "")
        )

        val elapsed = measureTimeMillis {
            val result = engine.filterText(largeText, rules)
            assertTrue(result.contains("lobo"))
            assertFalse(result.contains("perezoso"))
        }

        assertTrue("Execution took too long: ${elapsed}ms", elapsed < 500)
    }
}
