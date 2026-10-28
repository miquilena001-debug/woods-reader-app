package com.example.woodsreader.util

import com.example.woodsreader.data.graph.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityHeuristicsTest {

    @Test
    fun testSuggestType() {
        assertEquals(EntityType.SECTA, EntityHeuristics.suggestType("Secta del Loto Carmesí"))
        assertEquals(EntityType.SECTA, EntityHeuristics.suggestType("Clan Lin"))
        assertEquals(EntityType.SECTA, EntityHeuristics.suggestType("Pabellón de las Nubes"))
        assertEquals(EntityType.SECTA, EntityHeuristics.suggestType("Templo Sagrado"))

        assertEquals(EntityType.LUGAR, EntityHeuristics.suggestType("Montaña del Dragón Oculto"))
        assertEquals(EntityType.LUGAR, EntityHeuristics.suggestType("Valle del Fénix"))
        assertEquals(EntityType.LUGAR, EntityHeuristics.suggestType("Ciudad Yan"))
        assertEquals(EntityType.LUGAR, EntityHeuristics.suggestType("Reino Celestial"))
        assertEquals(EntityType.LUGAR, EntityHeuristics.suggestType("Bosque Brumoso"))

        assertEquals(EntityType.ARTEFACTO, EntityHeuristics.suggestType("Espada Sagrada del Dragón"))
        assertEquals(EntityType.ARTEFACTO, EntityHeuristics.suggestType("Píldora del Yin Yang"))
        assertEquals(EntityType.ARTEFACTO, EntityHeuristics.suggestType("Talismán Ancestral"))
        assertEquals(EntityType.ARTEFACTO, EntityHeuristics.suggestType("Perla Espiritual"))

        assertEquals(EntityType.RANGO, EntityHeuristics.suggestType("Etapa de Condensación de Qi"))
        assertEquals(EntityType.RANGO, EntityHeuristics.suggestType("Fase Tardía"))
        assertEquals(EntityType.RANGO, EntityHeuristics.suggestType("Núcleo Dorado"))
        assertEquals(EntityType.RANGO, EntityHeuristics.suggestType("Fundación Celestial"))
        assertEquals(EntityType.RANGO, EntityHeuristics.suggestType("Reino de los Cielos"))

        assertEquals(EntityType.OTRO, EntityHeuristics.suggestType("Lin Dong"))
        assertEquals(EntityType.OTRO, EntityHeuristics.suggestType("Xiao Yan"))
    }

    @Test
    fun testStopwords() {
        assertTrue(EntityHeuristics.isStopword("El"))
        assertTrue(EntityHeuristics.isStopword("La"))
        assertTrue(EntityHeuristics.isStopword("Pero"))
        assertTrue(EntityHeuristics.isStopword("Entonces"))
        assertTrue(EntityHeuristics.isStopword("Capítulo"))
        assertTrue(EntityHeuristics.isStopword("Sin embargo"))

        assertFalse(EntityHeuristics.isStopword("Lin Dong"))
        assertFalse(EntityHeuristics.isStopword("Secta"))
        assertFalse(EntityHeuristics.isStopword("Montaña"))
    }

    @Test
    fun testMidSentence() {
        val paragraph1 = "En aquel momento, Lin Dong miró al horizonte."
        val idxLinDong = paragraph1.indexOf("Lin Dong")
        assertTrue(EntityHeuristics.isMidSentence(paragraph1, idxLinDong))

        val paragraph2 = "Lin Dong miró al horizonte."
        assertFalse(EntityHeuristics.isMidSentence(paragraph2, 0))

        val paragraph3 = "Había silencio. Lin Dong habló."
        val idxLinDong3 = paragraph3.indexOf("Lin Dong")
        assertFalse(EntityHeuristics.isMidSentence(paragraph3, idxLinDong3))

        val paragraph4 = "¡Lin Dong, ten cuidado!"
        val idxLinDong4 = paragraph4.indexOf("Lin Dong")
        assertFalse(EntityHeuristics.isMidSentence(paragraph4, idxLinDong4))
    }

    @Test
    fun testCandidateRegex() {
        val text = "Lin Dong y Xiao Yan visitaron la Secta de la Espada."
        val matches = EntityHeuristics.CANDIDATE_REGEX.findAll(text).map { it.value }.toList()
        assertTrue(matches.contains("Lin Dong"))
        assertTrue(matches.contains("Xiao Yan"))
        assertTrue(matches.contains("Secta de la Espada") || matches.contains("Secta"))
    }

    @Test
    fun testStripHtml() {
        val html = "<html><body><p>Hola <b>Lin Dong</b>&nbsp;&amp;&nbsp;amigos.</p></body></html>"
        val plain = EntityHeuristics.stripHtml(html)
        assertTrue(plain.contains("Hola Lin Dong & amigos."))
    }
}
