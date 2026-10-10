package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoiseClassifierTest {

    private lateinit var classifier: NoiseClassifier

    @Before
    fun setUp() {
        classifier = NoiseClassifier()
    }

    @Test
    fun `isPageNumber correctly identifies arabic numerals`() {
        assertTrue(classifier.isPageNumber("1"))
        assertTrue(classifier.isPageNumber("42"))
        assertTrue(classifier.isPageNumber("  120  "))
        assertTrue(classifier.isPageNumber("- 45 -"))
        assertTrue(classifier.isPageNumber("— 99 —"))
        assertTrue(classifier.isPageNumber("[12]"))
        assertTrue(classifier.isPageNumber("(34)"))
        assertTrue(classifier.isPageNumber("Pág. 15"))
        assertTrue(classifier.isPageNumber("Página 42"))
        assertTrue(classifier.isPageNumber("pág 12 de 350"))
        assertTrue(classifier.isPageNumber("15 / 200"))
    }

    @Test
    fun `isPageNumber correctly identifies roman numerals`() {
        assertTrue(classifier.isPageNumber("i"))
        assertTrue(classifier.isPageNumber("iv"))
        assertTrue(classifier.isPageNumber("XIV"))
        assertTrue(classifier.isPageNumber("xxiii"))
        assertTrue(classifier.isPageNumber("- viii -"))
    }

    @Test
    fun `isPageNumber rejects normal body text`() {
        assertFalse(classifier.isPageNumber("En 1984 George Orwell escribió su obra maestra."))
        assertFalse(classifier.isPageNumber("Capítulo 1: El comienzo del viaje"))
        assertFalse(classifier.isPageNumber("Eran más de 40 soldados en la colina."))
        assertFalse(classifier.isPageNumber(""))
    }

    @Test
    fun `isIsolatedOrnament identifies ornamental separators`() {
        assertTrue(classifier.isIsolatedOrnament("* * *"))
        assertTrue(classifier.isIsolatedOrnament("***"))
        assertTrue(classifier.isIsolatedOrnament("---"))
        assertTrue(classifier.isIsolatedOrnament("——"))
        assertTrue(classifier.isIsolatedOrnament("• • •"))
        assertTrue(classifier.isIsolatedOrnament("§ § §"))
        assertFalse(classifier.isIsolatedOrnament("Esto no es un ornamento *"))
    }

    @Test
    fun `isUrlOrContact identifies web and email boilerplate`() {
        assertTrue(classifier.isUrlOrContact("https://www.editorialplaneta.es"))
        assertTrue(classifier.isUrlOrContact("www.penguinlibros.com/contacto"))
        assertTrue(classifier.isUrlOrContact("contacto@editorial.com"))
        assertFalse(classifier.isUrlOrContact("Visita nuestra web oficial en https://example.com para más novedades literarias y eventos."))
    }

    @Test
    fun `isLegalBoilerplate identifies copyright disclaimers`() {
        assertTrue(classifier.isLegalBoilerplate("Todos los derechos reservados. Ninguna parte de este libro..."))
        assertTrue(classifier.isLegalBoilerplate("ISBN 978-84-08-17612-4"))
        assertTrue(classifier.isLegalBoilerplate("Depósito legal: B-12345-2024"))
        assertTrue(classifier.isLegalBoilerplate("D.R. © 2023 Editorial Woods"))
        assertFalse(classifier.isLegalBoilerplate("El protagonista juró defender sus derechos con honor."))
    }

    @Test
    fun `detectRepetitiveHeadersFooters identifies multi-page recurring lines`() {
        val simulatedPages = listOf(
            Pair("Cien años de soledad", "1"),
            Pair("Cien años de soledad", "2"),
            Pair("Cien años de soledad", "3"),
            Pair("Cien años de soledad", "4"),
            Pair("Cien años de soledad", "5")
        )

        val repetitive = classifier.detectRepetitiveHeadersFooters(simulatedPages)
        assertTrue(repetitive.contains("cien años de soledad"))
        // El número de página es detectado por isPageNumber y no debe ser tratado como header
        assertFalse(repetitive.contains("1"))
    }

    @Test
    fun `classify integrates all rules into correct NoiseReason`() {
        assertEquals(NoiseReason.PAGE_NUMBER, classifier.classify("— 23 —"))
        assertEquals(NoiseReason.ISOLATED_ORNAMENT, classifier.classify("* * *"))
        assertEquals(NoiseReason.LEGAL_COPYRIGHT, classifier.classify("Todos los derechos reservados"))
        assertEquals(NoiseReason.URL_OR_CONTACT, classifier.classify("https://woodsreader.app"))
        
        val headers = setOf("capitulo primero")
        assertEquals(NoiseReason.RUNNING_HEADER, classifier.classify("Capítulo Primero", isFirstLineOfPage = true, repetitiveHeadersFooters = headers))
        assertEquals(NoiseReason.RUNNING_FOOTER, classifier.classify("Capítulo Primero", isLastLineOfPage = true, repetitiveHeadersFooters = headers))

        // Contenido legítimo debe retornar null
        assertNull(classifier.classify("Muchos años después, frente al pelotón de fusilamiento..."))
    }
}
