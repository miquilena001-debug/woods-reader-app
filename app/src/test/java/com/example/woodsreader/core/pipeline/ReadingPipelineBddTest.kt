package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de Comportamiento (BDD / Gherkin) para el Pipeline de Lectura Unificado.
 */
class ReadingPipelineBddTest {

    private lateinit var pipeline: ReadingPipeline
    private lateinit var synchronizer: PdfBookViewSynchronizer

    @Before
    fun setUp() {
        pipeline = ReadingPipeline(
            noiseClassifier = NoiseClassifier(),
            imageAnnouncer = ImageAnnouncer(),
            wordExclusionEngine = WordExclusionEngine(),
            spanishTextFormatter = SpanishTextFormatter()
        )
        synchronizer = PdfBookViewSynchronizer()
    }

    /**
     * Feature: Supresión automática de ruido textual
     * Scenario: Omitir números de página y encabezados recurrentes al narrar y leer
     *   Given un libro con encabezados recurrentes "Cien Años de Soledad" y números de página
     *   When el pipeline procesa las páginas del documento
     *   Then el texto visible no contiene números de página aislados
     *   And la voz neuronal TTS no narra el encabezado repetitivo ni el número de página
     */
    @Test
    fun `Scenario - Suppress page numbers and running headers in narration and display`() {
        // Given
        val rawDocument = listOf(
            RawDocumentBlock(id = "p1-h", text = "Cien Años de Soledad", pageIndex = 1, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "p1-t", text = "Muchos años después, frente al pelotón...", pageIndex = 1, orderIndex = 1),
            RawDocumentBlock(id = "p1-f", text = "— 1 —", pageIndex = 1, orderIndex = 2, isLastLineOfPage = true),

            RawDocumentBlock(id = "p2-h", text = "Cien Años de Soledad", pageIndex = 2, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "p2-t", text = "El coronel Aureliano Buendía había de recordar...", pageIndex = 2, orderIndex = 1),
            RawDocumentBlock(id = "p2-f", text = "— 2 —", pageIndex = 2, orderIndex = 2, isLastLineOfPage = true),

            RawDocumentBlock(id = "p3-h", text = "Cien Años de Soledad", pageIndex = 3, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "p3-t", text = "Macondo era entonces una aldea de veinte casas...", pageIndex = 3, orderIndex = 1),
            RawDocumentBlock(id = "p3-f", text = "— 3 —", pageIndex = 3, orderIndex = 2, isLastLineOfPage = true)
        )

        // When
        val processed = pipeline.process(rawDocument, stripNoiseFromDisplay = true)

        // Then
        val visibleText = processed.displayBlocks.filterIsInstance<ReadingBlock.Paragraph>().map { it.text }
        assertFalse("El texto visible no debe contener números de página aislados", visibleText.any { it.contains("— 1 —") })
        assertFalse("El texto visible no debe contener números de página aislados", visibleText.any { it.contains("— 2 —") })

        val ttsSpokenText = processed.ttsNarrationUnits.map { it.textToSpeak }
        assertFalse("TTS no debe narrar el número de página", ttsSpokenText.any { it.contains("— 1 —") })
        assertFalse("TTS no debe narrar el encabezado repetitivo", ttsSpokenText.any { it == "Cien Años de Soledad" })
        assertTrue("TTS debe narrar la historia principal", ttsSpokenText.any { it.contains("pelotón") })
    }

    /**
     * Feature: Locución inteligente de figuras e imágenes
     * Scenario: Anunciar imágenes en TTS con su pie de foto
     *   Given un libro con una ilustración que contiene pie de foto "Plano de la ciudadela"
     *   When el pipeline procesa la imagen con locución habilitada
     *   Then se genera una unidad de narración que anuncia "Imagen: Plano de la ciudadela"
     */
    @Test
    fun `Scenario - Announce images in TTS with caption`() {
        // Given
        val rawDocument = listOf(
            RawDocumentBlock(
                id = "fig-1",
                text = "",
                isImage = true,
                imageCaption = "Plano de la ciudadela",
                pageIndex = 4,
                orderIndex = 0
            )
        )

        // When
        val processed = pipeline.process(rawDocument, announceImages = true)

        // Then
        val imageNarration = processed.ttsNarrationUnits.find { it.isAnnouncement }
        assertNotNull("Debe existir una unidad de narración para la imagen", imageNarration)
        assertEquals("Imagen: Plano de la ciudadela", imageNarration?.textToSpeak)
    }

    /**
     * Feature: Exclusión y reemplazo de palabras en tiempo real
     * Scenario: Ocultar muletillas sin mutilar palabras compuestas
     *   Given una regla para omitir la palabra "con"
     *   When se procesa una frase que contiene "con" y "continuar"
     *   Then la palabra "con" se elimina
     *   And la palabra "continuar" permanece intacta
     */
    @Test
    fun `Scenario - Exclude target word without damaging compound words`() {
        // Given
        val rawDocument = listOf(
            RawDocumentBlock(id = "1", text = "Debemos continuar con el viaje con valentía.", pageIndex = 1, orderIndex = 0)
        )
        val rules = listOf(
            WordExclusionRule(target = "con", replacement = "")
        )

        // When
        val processed = pipeline.process(rawDocument, rules = rules)

        // Then
        val paragraph = processed.displayBlocks.first() as ReadingBlock.Paragraph
        assertTrue("La palabra 'continuar' no debe ser mutilada", paragraph.text.contains("continuar"))
        assertFalse("La palabra 'con' debe ser eliminada", paragraph.text.contains(" con "))
    }

    /**
     * Feature: Sincronización bidireccional de PDF
     * Scenario: Alternar entre Vista Original y Vista Libro sin perder la posición
     *   Given un PDF en Vista Original en la página 5
     *   When el usuario conmuta a Vista Libro
     *   Then el visor salta al primer bloque de la página 5
     *   When el usuario avanza hasta el bloque de la página 7 y vuelve a Vista Original
     *   Then el visor original se posiciona exactamente en la página 7
     */
    @Test
    fun `Scenario - Switch between Original View and Book View seamlessly`() {
        // Given
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "b1", text = "Texto página 1", pageIndex = 1, orderIndex = 0),
            ReadingBlock.Paragraph(id = "b2", text = "Texto página 5", pageIndex = 5, orderIndex = 1),
            ReadingBlock.Paragraph(id = "b3", text = "Texto página 7", pageIndex = 7, orderIndex = 2)
        )

        // When (Original -> Vista Libro)
        val targetBlockIndex = synchronizer.findBlockIndexForPage(blocks, targetPage = 5)

        // Then
        assertEquals("Debe saltar al bloque correspondiente a la página 5", 1, targetBlockIndex)

        // When (Vista Libro -> Original)
        val restoredPage = synchronizer.findPageForBlockIndex(blocks, blockIndex = 2, totalPages = 10)

        // Then
        assertEquals("Debe retornar a la página 7 en la Vista Original", 7, restoredPage)
    }
}
