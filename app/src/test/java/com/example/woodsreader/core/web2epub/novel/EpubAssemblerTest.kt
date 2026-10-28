package com.example.woodsreader.core.web2epub.novel

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class EpubAssemblerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `assemble creates valid EPUB 3 zip structure with clean XML`() {
        val assembler = EpubAssembler()
        val outputFile = File(tempFolder.root, "test_book.epub")

        val metadata = EpubMetadata(
            title = "Test Novel: With <Special> & Characters\nNewline",
            author = "Awesome Author & Co.",
            description = "A wonderful description <br> with & characters",
            sourceUrl = "https://example.com/novel"
        )

        val chapters = listOf(
            EpubChapterInput(
                title = "Chapter 1: The Beginning",
                fileName = "chapter_0001.xhtml",
                contentXhtml = "<div class=\"chapter-content\"><p>First paragraph.</p></div>"
            ),
            EpubChapterInput(
                title = "Chapter 2: The Journey",
                fileName = "chapter_0002.xhtml",
                contentXhtml = "<div class=\"chapter-content\"><p>Second paragraph.</p></div>"
            )
        )

        val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val images = listOf(
            EpubImageInput(
                relativeZipPath = "OEBPS/images/cover.jpg",
                mimeType = "image/jpeg",
                bytes = coverBytes,
                isCover = true
            )
        )

        assembler.assemble(metadata, chapters, images, outputFile)
        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 0)

        // Validar entradas dentro del Zip
        ZipFile(outputFile).use { zip ->
            // 1. mimetype debe ser la primera entrada y STORED
            val entries = zip.entries().toList()
            assertTrue(entries.isNotEmpty())
            val firstEntry = entries[0]
            assertEquals("mimetype", firstEntry.name)
            assertEquals(java.util.zip.ZipEntry.STORED, firstEntry.method)

            val mimetypeContent = zip.getInputStream(firstEntry).bufferedReader().readText()
            assertEquals("application/epub+zip", mimetypeContent)

            // 2. container.xml existe y es XML válido
            val containerEntry = zip.getEntry("META-INF/container.xml")
            assertNotNull(containerEntry)
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            val containerDoc = Jsoup.parse(containerXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals(0, containerDoc.parser().errors.size)

            // 3. content.opf existe y es XML válido
            val opfEntry = zip.getEntry("OEBPS/content.opf")
            assertNotNull(opfEntry)
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
            val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals(0, opfDoc.parser().errors.size)

            // Comprobar que no hay newlines rotos en el título del OPF
            val titleEl = opfDoc.selectFirst("dc\\:title, title")
            assertNotNull(titleEl)
            assertTrue(!titleEl!!.text().contains("\n"))

            // 4. nav.xhtml y toc.ncx existen y son XML válidos
            val navEntry = zip.getEntry("OEBPS/nav.xhtml")
            assertNotNull(navEntry)
            val navXml = zip.getInputStream(navEntry).bufferedReader().readText()
            val navDoc = Jsoup.parse(navXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals(0, navDoc.parser().errors.size)

            val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
            assertNotNull(ncxEntry)
            val ncxXml = zip.getInputStream(ncxEntry).bufferedReader().readText()
            val ncxDoc = Jsoup.parse(ncxXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals(0, ncxDoc.parser().errors.size)

            // 5. Portada y capítulos
            assertNotNull(zip.getEntry("OEBPS/cover.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/chapter_0001.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/chapter_0002.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/images/cover.jpg"))
            assertNotNull(zip.getEntry("OEBPS/style.css"))
        }
    }
}
