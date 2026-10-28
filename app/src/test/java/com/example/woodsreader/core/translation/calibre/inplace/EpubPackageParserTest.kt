package com.example.woodsreader.core.translation.calibre.inplace

import org.junit.Assert.*
import org.junit.Test
import java.util.zip.ZipFile

class EpubPackageParserTest {

    @Test
    fun testParseEpub3() {
        val epubFile = TestEpubFactory.createMinimalEpub3()
        ZipFile(epubFile).use { zip ->
            val pkg = EpubPackageParser.parse(zip)
            assertEquals("EPUB/content.opf", pkg.opfPath)
            assertEquals("EPUB", pkg.opfDir)
            assertEquals("Test EPUB 3", pkg.title)
            assertEquals("Author One", pkg.creator)
            assertEquals("en", pkg.language)
            assertEquals(listOf("EPUB/text/ch1.xhtml", "EPUB/text/ch2.xhtml"), pkg.readingOrderZipPaths)
            assertEquals("EPUB/nav.xhtml", pkg.navZipPath)
        }
    }

    @Test
    fun testParseEpub2() {
        val epubFile = TestEpubFactory.createMinimalEpub2()
        ZipFile(epubFile).use { zip ->
            val pkg = EpubPackageParser.parse(zip)
            assertEquals("OEBPS/content.opf", pkg.opfPath)
            assertEquals("OEBPS", pkg.opfDir)
            assertEquals("Test EPUB 2", pkg.title)
            assertEquals("Author Two", pkg.creator)
            assertEquals("en", pkg.language)
            assertEquals(listOf("OEBPS/ch1.xhtml", "OEBPS/ch2.xhtml"), pkg.readingOrderZipPaths)
            assertEquals("OEBPS/toc.ncx", pkg.ncxZipPath)
        }
    }
}
