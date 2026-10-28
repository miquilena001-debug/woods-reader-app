package com.example.woodsreader.core.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalCoroutinesApi::class)
class PdfCleanExtractorAndConverterTest {

    private lateinit var tempPdfFile: File
    private lateinit var tempEpubFile: File

    @Before
    fun setUp() {
        tempPdfFile = File.createTempFile("test_raw_", ".pdf")
        tempEpubFile = File.createTempFile("test_clean_", ".epub")

        val doc = PDDocument()
        try {
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            doc.save(tempPdfFile)
        } finally {
            doc.close()
        }
    }

    @After
    fun tearDown() {
        if (tempPdfFile.exists()) tempPdfFile.delete()
        if (tempEpubFile.exists()) tempEpubFile.delete()
    }

    @Test
    fun `calculateSha256 generates consistent cryptographic hash for deduplication`() {
        val bytes1 = "watermark_logo_bytes".toByteArray()
        val bytes2 = "watermark_logo_bytes".toByteArray()
        val bytes3 = "unique_illustration_bytes".toByteArray()

        val hash1 = PdfCleanExtractorAndConverter.calculateSha256(bytes1)
        val hash2 = PdfCleanExtractorAndConverter.calculateSha256(bytes2)
        val hash3 = PdfCleanExtractorAndConverter.calculateSha256(bytes3)

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
        assertEquals(64, hash1.length)
    }

    @Test
    fun `isSpamTextLine identifies discord, patreon, translation groups and URLs`() {
        assertTrue(PdfCleanExtractorAndConverter.isSpamTextLine("Lee gratis en https://novelupdates.com/dead-wizard"))
        assertTrue(PdfCleanExtractorAndConverter.isSpamTextLine("Únete a nuestro servidor en discord.gg/translators"))
        assertTrue(PdfCleanExtractorAndConverter.isSpamTextLine("Capítulo traducido por DarkScan - Apóyanos en patreon.com/darkscan"))
        assertTrue(PdfCleanExtractorAndConverter.isSpamTextLine("Canal de telegram: t.me/novelaspdf"))

        assertFalse(PdfCleanExtractorAndConverter.isSpamTextLine("El anciano mago cerró los ojos y comenzó a conjurar el hechizo."))
        assertFalse(PdfCleanExtractorAndConverter.isSpamTextLine("— La luna llena brillará esta noche sobre las montañas."))
    }

    @Test
    fun `normalizeForHeaderComparison harmonizes variable page numbers and symbols`() {
        val h1 = PdfCleanExtractorAndConverter.normalizeForHeaderComparison("Diary of a Dead Wizard — Page 14 • Woods Scan")
        val h2 = PdfCleanExtractorAndConverter.normalizeForHeaderComparison("Diary of a Dead Wizard — Page 28 • Woods Scan")

        assertEquals(h1, h2)
        assertEquals("diary of a dead wizard page # woods scan", h1)
    }

    @Test
    fun `convertPdfToCleanEpub purges spam headers, creates chapters and builds valid EPUB`() = runTest {
        var progressReported = false

        val result = PdfCleanExtractorAndConverter.convertPdfToCleanEpub(
            pdfFile = tempPdfFile,
            outputEpubFile = tempEpubFile,
            bookTitle = "Diary of a Dead Wizard",
            bookAuthor = "Immortal Author",
            languageCode = "es",
            imageCompressor = { it },
            pageTextProvider = { _, pageIdx ->
                if (pageIdx == 1) {
                    """
                    Diary of a Dead Wizard — Page 1 • Woods Scan
                    Chapter 1: The Return of Magic
                    The young apprentice discovered an ancient diary hidden beneath the floorboards.
                    Every page was written in glowing runes.
                    Visita nuestro Discord en discord.gg/novels para más capítulos.
                    """.trimIndent()
                } else {
                    """
                    Diary of a Dead Wizard — Page 2 • Woods Scan
                    Chapter 2: The Silent Forest
                    They set out before the sunrise, marching into the mist.
                    """.trimIndent()
                }
            },
            onProgress = { progress ->
                progressReported = true
                assertTrue(progress.totalPages == 2)
            }
        )

        assertTrue(result.isSuccess)
        assertTrue(progressReported)
        assertTrue(tempEpubFile.exists() && tempEpubFile.length() > 0)

        // Verificar el contenido del EPUB generado
        ZipFile(tempEpubFile).use { zip ->
            val opfEntry = zip.getEntry("OEBPS/content.opf")
            assertNotNull(opfEntry)
            val opfContent = String(zip.getInputStream(opfEntry).readBytes(), Charsets.UTF_8)
            assertTrue(opfContent.contains("Diary of a Dead Wizard"))
            assertTrue(opfContent.contains("ch_1"))
            assertTrue(opfContent.contains("ch_2"))

            // Verificar que el capítulo 1 no contiene la línea de Discord
            val ch1Entry = zip.getEntry("OEBPS/chapter_0001.xhtml")
            assertNotNull(ch1Entry)
            val ch1Content = String(zip.getInputStream(ch1Entry).readBytes(), Charsets.UTF_8)
            assertTrue(ch1Content.contains("The young apprentice discovered an ancient diary"))
            assertFalse(ch1Content.contains("discord.gg/novels"))
        }
    }
}
