package com.example.woodsreader.core.translation.calibre

import com.example.woodsreader.core.translation.calibre.inplace.FakeTextTranslator
import com.example.woodsreader.core.translation.calibre.inplace.TestEpubFactory
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class CalibreStyleEpubTranslatorStructureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testInspectLocalEpubBuilderOutput() {
        val epubFile = File(tempFolder.root, "test_builder.epub")
        val builder = com.example.woodsreader.core.web2epub.LocalEpubBuilder(
            outputFile = epubFile,
            bookTitle = "Libro de Prueba (ES)",
            bookAuthor = "Autor de Prueba",
            languageCode = "es"
        )
        builder.appendChapter("Capítulo 1", listOf("Párrafo 1 en español.", "Párrafo 2 en español."), "ch1.xhtml")
        builder.appendChapter("Capítulo 2", listOf("Segundo capítulo."), "ch2.xhtml")
        val success = builder.finishEpub()

        assertTrue(success)
        assertTrue(epubFile.exists())
        assertTrue(epubFile.length() > 0)

        ZipFile(epubFile).use { zip ->
            val entries = zip.entries().toList()
            println("Entries in builder epub: " + entries.map { it.name })

            // Verificar mimetype
            val mimetypeEntry = zip.getEntry("mimetype")
            assertNotNull("mimetype debe existir", mimetypeEntry)
            assertEquals(java.util.zip.ZipEntry.STORED, mimetypeEntry.method)

            // Verificar container.xml
            val containerEntry = zip.getEntry("META-INF/container.xml")
            assertNotNull("container.xml debe existir", containerEntry)
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            val containerDoc = Jsoup.parse(containerXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals("container.xml errors: " + containerDoc.parser().errors, 0, containerDoc.parser().errors.size)

            // Verificar content.opf
            val opfEntry = zip.getEntry("OEBPS/content.opf")
            assertNotNull("content.opf debe existir", opfEntry)
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
            println("OPF content:\n$opfXml")
            val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals("opf errors: " + opfDoc.parser().errors, 0, opfDoc.parser().errors.size)

            // Verificar toc.ncx
            val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
            assertNotNull("toc.ncx debe existir", ncxEntry)
            val ncxXml = zip.getInputStream(ncxEntry).bufferedReader().readText()
            println("NCX content:\n$ncxXml")
            val ncxDoc = Jsoup.parse(ncxXml, "", Parser.xmlParser().setTrackErrors(10))
            assertEquals("ncx errors: " + ncxDoc.parser().errors, 0, ncxDoc.parser().errors.size)
        }
    }
}
