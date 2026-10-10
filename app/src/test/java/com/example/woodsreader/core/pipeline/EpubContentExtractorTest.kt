package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EpubContentExtractorTest {

    private lateinit var extractor: EpubContentExtractor
    private lateinit var pipeline: ReadingPipeline

    @Before
    fun setUp() {
        extractor = EpubContentExtractor()
        pipeline = ReadingPipeline(
            noiseClassifier = NoiseClassifier(),
            imageAnnouncer = ImageAnnouncer(),
            wordExclusionEngine = WordExclusionEngine(),
            spanishTextFormatter = SpanishTextFormatter()
        )
    }

    @Test
    fun `extractBlocks handles blank or empty HTML gracefully`() {
        val resultEmpty = extractor.extractBlocks("", "ch1.xhtml")
        assertTrue(resultEmpty.isEmpty())

        val resultBlank = extractor.extractBlocks("   \n\t  ", "ch1.xhtml")
        assertTrue(resultBlank.isEmpty())
    }

    @Test
    fun `extractBlocks maps headings with appropriate relative font sizes`() {
        val html = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Capítulo 1</title></head>
            <body>
                <h1>Capítulo Primero</h1>
                <h2>La llegada al bosque</h2>
                <p>El camino era sombrío y fresco.</p>
            </body>
            </html>
        """.trimIndent()

        val blocks = extractor.extractBlocks(html, "chapter1.xhtml", pageIndex = 1)
        assertEquals(3, blocks.size)

        // H1
        assertEquals("chapter1.xhtml-blk-0", blocks[0].id)
        assertEquals("Capítulo Primero", blocks[0].text)
        assertEquals(24f, blocks[0].fontSize, 0.01f)
        assertFalse(blocks[0].isImage)
        assertTrue(blocks[0].isFirstLineOfPage)
        assertFalse(blocks[0].isLastLineOfPage)

        // H2
        assertEquals("chapter1.xhtml-blk-1", blocks[1].id)
        assertEquals("La llegada al bosque", blocks[1].text)
        assertEquals(20f, blocks[1].fontSize, 0.01f)

        // P
        assertEquals("chapter1.xhtml-blk-2", blocks[2].id)
        assertEquals("El camino era sombrío y fresco.", blocks[2].text)
        assertEquals(12f, blocks[2].fontSize, 0.01f)
        assertFalse(blocks[2].isFirstLineOfPage)
        assertTrue(blocks[2].isLastLineOfPage)
    }

    @Test
    fun `extractBlocks extracts standalone images with alt and src`() {
        val html = """
            <html>
            <body>
                <p>Antes de la ilustración.</p>
                <img src="../images/cover.jpg" alt="Mapa del reino" />
                <p>Después de la ilustración.</p>
            </body>
            </html>
        """.trimIndent()

        val blocks = extractor.extractBlocks(html, "sec1.xhtml", pageIndex = 0)
        assertEquals(3, blocks.size)

        val imageBlock = blocks[1]
        assertTrue(imageBlock.isImage)
        assertEquals("Mapa del reino", imageBlock.imageCaption)
        assertEquals("../images/cover.jpg", imageBlock.imageUri)
        assertEquals(1, imageBlock.orderIndex)
    }

    @Test
    fun `extractBlocks extracts figures with figcaption as caption`() {
        val html = """
            <html>
            <body>
                <figure>
                    <img src="chart.png" alt="Gráfico de ventas" />
                    <figcaption>Figura 3.1: Comparativa anual</figcaption>
                </figure>
            </body>
            </html>
        """.trimIndent()

        val blocks = extractor.extractBlocks(html, "sec2.xhtml", pageIndex = 2)
        assertEquals(1, blocks.size)

        val fig = blocks[0]
        assertTrue(fig.isImage)
        assertEquals("Figura 3.1: Comparativa anual", fig.imageCaption)
        assertEquals("chart.png", fig.imageUri)
    }

    @Test
    fun `extractBlocks integrated with ReadingPipeline produces clean narration units and announces images`() {
        val html = """
            <html>
            <body>
                <h1>El bosque encantado</h1>
                <p>— 1 —</p>
                <p>Caminaban en silencio por la senda de pinos.</p>
                <figure>
                    <img src="forest.jpg" alt="Ilustración del bosque al atardecer" />
                    <figcaption>El bosque al atardecer</figcaption>
                </figure>
                <p>Todos los derechos reservados. ISBN 978-987-1234-56-7</p>
            </body>
            </html>
        """.trimIndent()

        val rawBlocks = extractor.extractBlocks(html, "book.xhtml", pageIndex = 1)
        val processed = pipeline.process(rawBlocks, announceImages = true)

        // Verificamos que el número de página "— 1 —" y el copyright legal fueron descartados del TTS
        val ttsTexts = processed.ttsNarrationUnits.map { it.textToSpeak }

        // El título está presente (SpanishTextFormatter inserta \u00A0 tras 'El')
        assertTrue(ttsTexts.any { it.replace('\u00A0', ' ').contains("El bosque encantado") })

        // El párrafo está presente
        assertTrue(ttsTexts.any { it.replace('\u00A0', ' ').contains("Caminaban en silencio") })

        // La imagen está anunciada con su pie
        assertTrue(ttsTexts.any { it.startsWith("Imagen: El bosque al atardecer") })

        // El ruido NO está presente en TTS
        assertFalse(ttsTexts.any { it.contains("— 1 —") })
        assertFalse(ttsTexts.any { it.contains("ISBN") })
    }
}
