package com.example.woodsreader.core.translation.calibre.inplace

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubZipWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWriteInPlacePreservesAssetsAndReplacesTarget() {
        val originalEpub = TestEpubFactory.createMinimalEpub3()
        val outputFile = File(tempFolder.root, "output_translated.epub")

        val modifiedCh1 = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="es">
            <body>
                <h1>Capítulo 1: Amanecer</h1>
                <p>El amanecer fue silencioso.</p>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        ZipFile(originalEpub).use { srcZip ->
            EpubZipWriter.writeInPlace(
                sourceZipFile = srcZip,
                outputFile = outputFile,
                modifiedEntries = mapOf("EPUB/text/ch1.xhtml" to modifiedCh1)
            )
        }

        assertTrue("El archivo de salida debe existir", outputFile.exists())
        assertTrue("El archivo debe tener un tamaño razonable", outputFile.length() > 0)

        ZipFile(outputFile).use { outZip ->
            val entries = outZip.entries().toList()
            assertEquals("mimetype", entries[0].name)
            assertEquals(ZipEntry.STORED, entries[0].method)

            // Entrada modificada
            val ch1Entry = outZip.getEntry("EPUB/text/ch1.xhtml")
            assertNotNull(ch1Entry)
            val ch1Content = outZip.getInputStream(ch1Entry).bufferedReader().readText()
            assertTrue(ch1Content.contains("Capítulo 1: Amanecer"))

            // Entrada no modificada (imagen)
            val imgEntry = outZip.getEntry("EPUB/images/dot.png")
            assertNotNull(imgEntry)
            val imgBytes = outZip.getInputStream(imgEntry).readBytes()
            assertArrayEquals(TestEpubFactory.DUMMY_PNG_1X1, imgBytes)
        }
    }
}
