package com.example.woodsreader.core.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPackageSanitizerTest {
    @Test
    fun `repairs a spine reference to a missing NCX id`() {
        val packageDocument = """
            <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
              <opf:manifest>
                <opf:item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <opf:item id="chapter1" href="chapter1.html" media-type="application/xhtml+xml"/>
              </opf:manifest>
              <opf:spine toc="missingNcx"><opf:itemref idref="chapter1"/></opf:spine>
            </opf:package>
        """.trimIndent()

        val repaired = EpubPackageSanitizer.repairNcxReference(packageDocument)

        assertEquals(
            packageDocument.replace("""toc="missingNcx"""", """toc="ncx""""),
            repaired
        )
    }

    @Test
    fun `keeps a valid NCX reference unchanged`() {
        val packageDocument = """
            <package>
              <manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest>
              <spine toc="ncx"/>
            </package>
        """.trimIndent()

        assertEquals(packageDocument, EpubPackageSanitizer.repairNcxReference(packageDocument))
    }

    @Test
    fun `does not change a package without an NCX resource`() {
        val packageDocument = """
            <package>
              <manifest><item id="chapter1" href="chapter1.html" media-type="application/xhtml+xml"/></manifest>
              <spine toc="missingNcx"/>
            </package>
        """.trimIndent()

        assertEquals(packageDocument, EpubPackageSanitizer.repairNcxReference(packageDocument))
    }

    @Test
    fun `repairs cover property when meta cover is present`() {
        val packageDocument = """
            <opf:package version="3.0" xmlns:opf="http://www.idpf.org/2007/opf">
              <opf:metadata>
                <opf:meta name="cover" content="item_1"/>
              </opf:metadata>
              <opf:manifest>
                <opf:item id="item_1" href="item_1.jpg" media-type="image/jpeg"/>
              </opf:manifest>
            </opf:package>
        """.trimIndent()

        val repaired = EpubPackageSanitizer.repairCoverProperty(packageDocument)

        assertTrue(repaired.contains("""properties="cover-image""""))
    }

    @Test
    fun `does not add EPUB 3 cover property to EPUB 2 packages`() {
        val packageDocument = """
            <package version="2.0">
              <metadata><meta name="cover" content="cover"/></metadata>
              <manifest><item id="cover" href="cover.jpg" media-type="image/jpeg"/></manifest>
            </package>
        """.trimIndent()

        assertEquals(packageDocument, EpubPackageSanitizer.repairCoverProperty(packageDocument))
    }

    @Test
    fun `recognizes cover-image as a complete property token`() {
        val packageDocument = """
            <package version="3.0">
              <metadata><meta name="cover" content="cover"/></metadata>
              <manifest><item id="cover" href="cover.jpg" properties="cover-image-alt" media-type="image/jpeg"/></manifest>
            </package>
        """.trimIndent()

        val repaired = EpubPackageSanitizer.repairCoverProperty(packageDocument)

        assertTrue(repaired.contains("""properties="cover-image-alt cover-image""""))
    }
}
