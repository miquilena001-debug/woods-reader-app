package com.example.woodsreader.core.translation.calibre.inplace

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubInPlaceTranslatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTranslateEpub3EndToEnd() = runBlocking {
        val originalEpub = TestEpubFactory.createMinimalEpub3()
        val outputFile = File(tempFolder.root, "translated_epub3.epub")

        val translator = EpubInPlaceTranslator()
        val fakeNetwork = FakeTextTranslator(prefix = "[ES] ")

        val result = translator.translate(
            sourceFile = originalEpub,
            outputFile = outputFile,
            translator = fakeNetwork,
            sourceLang = "en",
            targetLang = "es",
            cacheDir = tempFolder.root
        )

        assertTrue("La traducción debe ser exitosa", result.isSuccess)
        assertTrue("El archivo EPUB generado debe existir", outputFile.exists())

        ZipFile(outputFile).use { zip ->
            val pkg = EpubPackageParser.parse(zip)
            assertEquals("es", pkg.language)
            assertEquals("Test EPUB 3 (ES)", pkg.title)

            // 1. Mimetype verificado
            val entries = zip.entries().toList()
            assertEquals("mimetype", entries[0].name)
            assertEquals(ZipEntry.STORED, entries[0].method)

            // 2. Imagen preservada
            val imgEntry = zip.getEntry("EPUB/images/dot.png")
            assertNotNull("La imagen debe existir en el EPUB traducido", imgEntry)
            assertArrayEquals(TestEpubFactory.DUMMY_PNG_1X1, zip.getInputStream(imgEntry).readBytes())

            // 3. Capítulo 1 traducido preservando etiqueta img
            val ch1Entry = zip.getEntry("EPUB/text/ch1.xhtml")
            assertNotNull(ch1Entry)
            val ch1Xml = zip.getInputStream(ch1Entry).bufferedReader().readText()
            val ch1Doc = Jsoup.parse(ch1Xml, "", Parser.xmlParser())
            assertEquals("[ES] Chapter 1: Dawn", ch1Doc.selectFirst("h1")?.text())
            assertEquals("[ES] The dawn was silent.", ch1Doc.select("p")[0].text())
            assertNotNull("El tag img en capítulo 1 debe estar presente", ch1Doc.selectFirst("img"))

            // 4. NAV TOC traducido
            val navEntry = zip.getEntry("EPUB/nav.xhtml")
            assertNotNull(navEntry)
            val navXml = zip.getInputStream(navEntry).bufferedReader().readText()
            val navDoc = Jsoup.parse(navXml, "", Parser.xmlParser())
            val navLinks = navDoc.select("nav ol li a")
            assertEquals("[ES] Chapter 1", navLinks[0].text())
            assertEquals("[ES] Chapter 2", navLinks[1].text())
        }
    }

    @Test
    fun testTranslateEpub2EndToEnd() = runBlocking {
        val originalEpub = TestEpubFactory.createMinimalEpub2()
        val outputFile = File(tempFolder.root, "translated_epub2.epub")

        val translator = EpubInPlaceTranslator()
        val fakeNetwork = FakeTextTranslator(prefix = "[ES] ")

        val result = translator.translate(
            sourceFile = originalEpub,
            outputFile = outputFile,
            translator = fakeNetwork,
            sourceLang = "en",
            targetLang = "es",
            cacheDir = tempFolder.root
        )

        assertTrue(result.isSuccess)
        assertTrue(outputFile.exists())

        ZipFile(outputFile).use { zip ->
            val pkg = EpubPackageParser.parse(zip)
            assertEquals("es", pkg.language)
            assertEquals("Test EPUB 2 (ES)", pkg.title)

            // NCX traducido
            val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
            assertNotNull(ncxEntry)
            val ncxXml = zip.getInputStream(ncxEntry).bufferedReader().readText()
            val ncxDoc = Jsoup.parse(ncxXml, "", Parser.xmlParser())
            val navLabels = ncxDoc.select("navLabel > text")
            assertEquals("[ES] Chapter 1", navLabels[0].text())
            assertEquals("[ES] Chapter 2", navLabels[1].text())
        }
    }
}
