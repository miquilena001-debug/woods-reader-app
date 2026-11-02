package com.example.woodsreader.core.pdf

import com.example.woodsreader.core.web2epub.LocalEpubBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class PdfChapterHeuristicsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isChapterHeading_acceptsValidHeadings() {
        assertTrue(PdfParagraphHealer.isChapterHeading("Chapter 1: The Beginning"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Capítulo 42"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Episode 15"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Prólogo"))
        assertTrue(PdfParagraphHealer.isChapterHeading("1. El presidente oculta las finanzas"))
    }

    @Test
    fun isChapterHeading_rejectsDialogueAndCompleteSentences() {
        assertFalse(PdfParagraphHealer.isChapterHeading("— ¿Qué estás diciendo, muchacho?"))
        assertFalse(PdfParagraphHealer.isChapterHeading("\"I won't let you do this,\" said the man."))
        assertFalse(PdfParagraphHealer.isChapterHeading("“Es hora de partir.”"))
        assertFalse(PdfParagraphHealer.isChapterHeading("Naturalmente, no debería haberse sentido intimidada."))
        assertFalse(PdfParagraphHealer.isChapterHeading("This line ends with a period."))
    }

    @Test
    fun isTableOfContentsPage_detectsIndexPages() {
        val indexPageLines = listOf(
            "Convertirse en el Director Financiero de una Academia",
            "2. 1. El presidente oculta las finanzas",
            "3. 1. El presidente oculta las finanzas",
            "4. 1. El presidente oculta las finanzas",
            "5. 1. El presidente oculta las finanzas",
            "6. 1. El presidente oculta las finanzas"
        )
        assertTrue(PdfParagraphHealer.isTableOfContentsPage(indexPageLines))

        val regularPageLines = listOf(
            "Chapter 1: Quiere liberar un monstruo",
            "Aunque ir a casa solo significa ir al dormitorio dentro de la Academia.",
            "Mirando a mi jefe, que era la causa de mi trabajo extra.",
            "¿Cómo podría quejarme cuando ella también trabajaba?",
            "Era el mismo documento de aprobación que había recibido."
        )
        assertFalse(PdfParagraphHealer.isTableOfContentsPage(regularPageLines))
    }

    @Test
    fun extractChapterNumber_extractsCorrectIntegers() {
        assertEquals(1, PdfParagraphHealer.extractChapterNumber("Chapter 1: The Beginning"))
        assertEquals(42, PdfParagraphHealer.extractChapterNumber("Capítulo 42"))
        assertEquals(105, PdfParagraphHealer.extractChapterNumber("Episode 105 - Climax"))
        assertEquals(24, PdfParagraphHealer.extractChapterNumber("24. Some Title"))
        assertEquals(null, PdfParagraphHealer.extractChapterNumber("Prólogo"))
    }

    @Test
    fun localEpubBuilder_splitsOversizedChapterSafely() {
        val outEpub = File(tempFolder.root, "test_split.epub")
        val builder = LocalEpubBuilder(
            outputFile = outEpub,
            bookTitle = "Split Test Book",
            bookAuthor = "Test Author"
        )

        // Generate ~300 KB of text (3,000 paragraphs of 100 chars)
        val bigParagraphList = (1..3000).map { i ->
            "Párrafo de prueba número $i con texto suficientemente largo para acumular tamaño en el buffer XHTML."
        }

        builder.appendChapter("Capítulo Gigante", bigParagraphList, "giant_chapter")
        val finished = builder.finishEpub()
        assertTrue(finished)
        assertTrue(outEpub.exists())

        // Verify with ZipFile that multiple XHTML chapters were created and each is <= 200 KB
        ZipFile(outEpub).use { zip ->
            val xhtmlEntries = zip.entries().asSequence().filter { it.name.endsWith(".xhtml") }.toList()
            assertTrue("Debería haberse dividido en al menos 2 partes", xhtmlEntries.size >= 2)
            for (entry in xhtmlEntries) {
                assertTrue("Cada archivo XHTML debe ser <= 200 KB (${entry.size} bytes)", entry.size <= 205_000)
            }
        }
    }
}
