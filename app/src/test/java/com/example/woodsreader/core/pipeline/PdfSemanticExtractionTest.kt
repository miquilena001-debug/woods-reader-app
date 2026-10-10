package com.example.woodsreader.core.pipeline

import com.example.woodsreader.core.managers.pdf.analysis.TextAnalyzer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PdfSemanticExtractionTest {

    private lateinit var analyzer: TextAnalyzer
    private lateinit var pipeline: ReadingPipeline

    @Before
    fun setUp() {
        analyzer = TextAnalyzer()
        pipeline = ReadingPipeline(
            noiseClassifier = NoiseClassifier(),
            imageAnnouncer = ImageAnnouncer(),
            wordExclusionEngine = WordExclusionEngine(),
            spanishTextFormatter = SpanishTextFormatter()
        )
    }

    @Test
    fun `convertPageToRawBlocks handles empty lines and images gracefully`() {
        val blocks = analyzer.convertPageToRawBlocks(emptyList(), pageIndex = 1)
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `convertPageToRawBlocks interleaves text and images in vertical coordinate order`() {
        val lines = listOf(
            TextAnalyzer.LineInfo(text = "Título de la sección", fontSize = 20f, y = 50f, xStart = 36f, xEnd = 300f),
            TextAnalyzer.LineInfo(text = "Párrafo posterior a la figura.", fontSize = 12f, y = 300f, xStart = 36f, xEnd = 500f)
        )
        val images = listOf(
            TextAnalyzer.PageImage(uri = "pdf://fig1.png", caption = "Figura 1: Mapa estelar", y = 150f)
        )

        val rawBlocks = analyzer.convertPageToRawBlocks(lines, pageIndex = 5, images = images)
        assertEquals(3, rawBlocks.size)

        // 1. Título
        assertEquals("pdf-p5-blk-0", rawBlocks[0].id)
        assertEquals("Título de la sección", rawBlocks[0].text)
        assertFalse(rawBlocks[0].isImage)
        assertTrue(rawBlocks[0].isFirstLineOfPage)
        assertFalse(rawBlocks[0].isLastLineOfPage)

        // 2. Imagen intercalada en Y = 150
        assertEquals("pdf-p5-img-1", rawBlocks[1].id)
        assertTrue(rawBlocks[1].isImage)
        assertEquals("Figura 1: Mapa estelar", rawBlocks[1].imageCaption)
        assertEquals("pdf://fig1.png", rawBlocks[1].imageUri)

        // 3. Párrafo posterior en Y = 300
        assertEquals("pdf-p5-blk-2", rawBlocks[2].id)
        assertEquals("Párrafo posterior a la figura.", rawBlocks[2].text)
        assertFalse(rawBlocks[2].isImage)
        assertFalse(rawBlocks[2].isFirstLineOfPage)
        assertTrue(rawBlocks[2].isLastLineOfPage)
    }

    @Test
    fun `convertPageToRawBlocks fed to ReadingPipeline filters out running headers and page numbers`() {
        val lines = listOf(
            TextAnalyzer.LineInfo(text = "Revista de Neurociencia — Vol. 14", fontSize = 10f, y = 20f, xStart = 36f, xEnd = 250f),
            TextAnalyzer.LineInfo(text = "Capítulo 2: Redes Neuronales", fontSize = 18f, y = 60f, xStart = 36f, xEnd = 350f),
            TextAnalyzer.LineInfo(text = "El aprendizaje profundo ha revolucionado la visión por ordenador.", fontSize = 12f, y = 120f, xStart = 36f, xEnd = 550f),
            TextAnalyzer.LineInfo(text = "Pág. 42", fontSize = 9f, y = 780f, xStart = 280f, xEnd = 320f)
        )

        val rawBlocks = analyzer.convertPageToRawBlocks(lines, pageIndex = 42)
        val processed = pipeline.process(rawBlocks, announceImages = true)

        val ttsList = processed.ttsNarrationUnits.map { it.textToSpeak }

        // El título y el cuerpo deben estar en TTS
        assertTrue(ttsList.any { it.contains("Capítulo 2") })
        assertTrue(ttsList.any { it.contains("El aprendizaje profundo") || it.replace('\u00A0', ' ').contains("El aprendizaje profundo") })

        // El número de página "Pág. 42" no debe estar en TTS
        assertFalse(ttsList.any { it.contains("Pág. 42") })
    }
}
