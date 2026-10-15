package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReadingPipelineTest {

    private lateinit var pipeline: ReadingPipeline

    @Before
    fun setUp() {
        pipeline = ReadingPipeline(
            noiseClassifier = NoiseClassifier(),
            imageAnnouncer = ImageAnnouncer(),
            wordExclusionEngine = WordExclusionEngine(),
            spanishTextFormatter = SpanishTextFormatter()
        )
    }

    @Test
    fun `process handles empty document gracefully`() {
        val result = pipeline.process(emptyList())
        assertTrue(result.displayBlocks.isEmpty())
        assertTrue(result.ttsNarrationUnits.isEmpty())
        assertEquals(0, result.metrics.totalRawBlocks)
    }

    @Test
    fun `process filters out noise and prevents it from entering TTS narration`() {
        val rawBlocks = listOf(
            RawDocumentBlock(id = "1", text = "Don Quijote de la Mancha", pageIndex = 1, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "2", text = "Capítulo Primero", fontSize = 18f, pageIndex = 1, orderIndex = 1),
            RawDocumentBlock(id = "3", text = "En un lugar de la Mancha...", pageIndex = 1, orderIndex = 2),
            RawDocumentBlock(id = "4", text = "— 1 —", pageIndex = 1, orderIndex = 3, isLastLineOfPage = true),
            RawDocumentBlock(id = "5", text = "Don Quijote de la Mancha", pageIndex = 2, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "6", text = "Vivía un hidalgo de los de lanza en astillero.", pageIndex = 2, orderIndex = 1),
            RawDocumentBlock(id = "7", text = "Todos los derechos reservados. ISBN 978-0-123456-78-9", pageIndex = 2, orderIndex = 2),
            RawDocumentBlock(id = "8", text = "— 2 —", pageIndex = 2, orderIndex = 3, isLastLineOfPage = true),
            RawDocumentBlock(id = "9", text = "Don Quijote de la Mancha", pageIndex = 3, orderIndex = 0, isFirstLineOfPage = true),
            RawDocumentBlock(id = "10", text = "Una olla de algo más vaca que carnero...", pageIndex = 3, orderIndex = 1),
            RawDocumentBlock(id = "11", text = "— 3 —", pageIndex = 3, orderIndex = 2, isLastLineOfPage = true)
        )

        val result = pipeline.process(rawBlocks, stripNoiseFromDisplay = true)

        // 1. Verificar que los números de página ("— 1 —", "— 2 —", "— 3 —") fueron filtrados
        assertFalse(result.displayBlocks.any { it is ReadingBlock.Paragraph && it.text.contains("— 1 —") })

        // 2. Verificar que el aviso legal ("Todos los derechos reservados") fue filtrado
        assertFalse(result.displayBlocks.any { it is ReadingBlock.Paragraph && it.text.contains("ISBN") })

        // 3. Verificar que el encabezado repetitivo ("Don Quijote de la Mancha") fue clasificado como ruido
        assertTrue(result.metrics.filteredNoiseBlocks >= 4)

        // 4. Verificar que NINGÚN ruido llegó a la narración TTS
        assertFalse(result.ttsNarrationUnits.any { it.textToSpeak.contains("— 1 —") })
        assertFalse(result.ttsNarrationUnits.any { it.textToSpeak.contains("ISBN") })
        assertFalse(result.ttsNarrationUnits.any { it.textToSpeak == "Don Quijote de la Mancha" })

        // 5. Verificar que el contenido real sí llegó al TTS (sin \u00A0) y a displayBlocks (con \u00A0)
        assertTrue(result.displayBlocks.any { it is ReadingBlock.Paragraph && it.text.contains("En\u00A0un\u00A0lugar") })
        assertTrue(result.ttsNarrationUnits.any { it.textToSpeak.contains("En un lugar") })
        assertTrue(result.ttsNarrationUnits.any { it.textToSpeak.contains("Vivía") })
    }

    @Test
    fun `process integrates images and creates verbal TTS announcements`() {
        val rawBlocks = listOf(
            RawDocumentBlock(id = "1", text = "El castillo del dragón", fontSize = 18f, pageIndex = 1, orderIndex = 0),
            RawDocumentBlock(
                id = "2",
                text = "",
                isImage = true,
                imageCaption = "El dragón sobrevolando la torre",
                pageIndex = 1,
                orderIndex = 1
            ),
            RawDocumentBlock(id = "3", text = "Los caballeros miraban con asombro.", pageIndex = 1, orderIndex = 2)
        )

        val result = pipeline.process(rawBlocks, announceImages = true)

        // La imagen debe estar en displayBlocks
        val imageBlock = result.displayBlocks.find { it is ReadingBlock.Image } as? ReadingBlock.Image
        assertNotNull(imageBlock)
        assertEquals("El dragón sobrevolando la torre", imageBlock?.caption)

        // Y debe existir una unidad de narración TTS para la imagen
        val imageNarration = result.ttsNarrationUnits.find { it.isAnnouncement }
        assertNotNull(imageNarration)
        assertEquals("Imagen: El dragón sobrevolando la torre", imageNarration?.textToSpeak)
    }

    @Test
    fun `process applies word exclusion rules during ingestion`() {
        val rawBlocks = listOf(
            RawDocumentBlock(id = "1", text = "La publicidad decía: compre ahora.", pageIndex = 1, orderIndex = 0)
        )
        val rules = listOf(
            WordExclusionRule(target = "publicidad", replacement = "noticia")
        )

        val result = pipeline.process(rawBlocks, rules = rules)
        val text = (result.displayBlocks.first() as ReadingBlock.Paragraph).text

        assertTrue(text.contains("noticia"))
        assertFalse(text.contains("publicidad"))
    }
}
