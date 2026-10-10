package com.example.woodsreader.core.pipeline

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PdfBookViewSynchronizerTest {

    private lateinit var synchronizer: PdfBookViewSynchronizer

    @Before
    fun setUp() {
        synchronizer = PdfBookViewSynchronizer()
    }

    @Test
    fun `findBlockIndexForPage finds exact first block of target page`() {
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "1", text = "P1-B1", pageIndex = 1, orderIndex = 0),
            ReadingBlock.Paragraph(id = "2", text = "P1-B2", pageIndex = 1, orderIndex = 1),
            ReadingBlock.Paragraph(id = "3", text = "P2-B1", pageIndex = 2, orderIndex = 2),
            ReadingBlock.Paragraph(id = "4", text = "P2-B2", pageIndex = 2, orderIndex = 3),
            ReadingBlock.Paragraph(id = "5", text = "P3-B1", pageIndex = 3, orderIndex = 4)
        )

        assertEquals(0, synchronizer.findBlockIndexForPage(blocks, 1))
        assertEquals(2, synchronizer.findBlockIndexForPage(blocks, 2))
        assertEquals(4, synchronizer.findBlockIndexForPage(blocks, 3))
    }

    @Test
    fun `findBlockIndexForPage falls back cleanly when page has no blocks`() {
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "1", text = "P1-B1", pageIndex = 1, orderIndex = 0),
            ReadingBlock.Paragraph(id = "3", text = "P4-B1", pageIndex = 4, orderIndex = 1)
        )

        // Página 2 y 3 no tienen bloques, debe retornar el bloque más cercano anterior (página 1 -> índice 0)
        assertEquals(0, synchronizer.findBlockIndexForPage(blocks, 2))
        assertEquals(0, synchronizer.findBlockIndexForPage(blocks, 3))
        assertEquals(1, synchronizer.findBlockIndexForPage(blocks, 4))
    }

    @Test
    fun `findPageForBlockIndex correctly maps block back to original page`() {
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "1", text = "P1", pageIndex = 1, orderIndex = 0),
            ReadingBlock.Paragraph(id = "2", text = "P5", pageIndex = 5, orderIndex = 1),
            ReadingBlock.Paragraph(id = "3", text = "P9", pageIndex = 9, orderIndex = 2)
        )

        assertEquals(1, synchronizer.findPageForBlockIndex(blocks, 0, totalPages = 10))
        assertEquals(5, synchronizer.findPageForBlockIndex(blocks, 1, totalPages = 10))
        assertEquals(9, synchronizer.findPageForBlockIndex(blocks, 2, totalPages = 10))
    }

    @Test
    fun `findPageForBlockIndex clamps safely within total pages`() {
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "1", text = "P1", pageIndex = 50, orderIndex = 0)
        )

        assertEquals(20, synchronizer.findPageForBlockIndex(blocks, 0, totalPages = 20))
    }

    @Test
    fun `calculatePageInternalProgress computes fine-grained fractional progress`() {
        val blocks = listOf(
            ReadingBlock.Paragraph(id = "1", text = "P1-B1", pageIndex = 1, orderIndex = 0),
            ReadingBlock.Paragraph(id = "2", text = "P1-B2", pageIndex = 1, orderIndex = 1),
            ReadingBlock.Paragraph(id = "3", text = "P1-B3", pageIndex = 1, orderIndex = 2)
        )

        assertEquals(0.0f, synchronizer.calculatePageInternalProgress(blocks, 0), 0.001f)
        assertEquals(0.5f, synchronizer.calculatePageInternalProgress(blocks, 1), 0.001f)
        assertEquals(1.0f, synchronizer.calculatePageInternalProgress(blocks, 2), 0.001f)
    }
}
