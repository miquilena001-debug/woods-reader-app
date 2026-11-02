package com.example.woodsreader.core.pdf

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ConvertUserPdfIntegrationTest {

    @Test
    fun convertRealUserPdf_producesStructuredCleanEpub() = runBlocking {
        val pdfName = "Becoming_the_Financial_Director_of_an_Academy_on_a_Brink_of_Bankruptcy.pdf"
        val pdfFile = listOf(
            File(pdfName),
            File("..", pdfName),
            File("c:/Users/Orbit/Downloads/woods reader", pdfName)
        ).firstOrNull { it.exists() }

        if (pdfFile == null) {
            println("PDF no encontrado, omitiendo test de integración.")
            return@runBlocking
        }

        val outEpub = File(pdfFile.parentFile ?: File("."), "Becoming_the_Financial_Director_of_an_Academy_on_a_Brink_of_Bankruptcy_Limpio.epub")
        if (outEpub.exists()) {
            outEpub.delete()
        }

        println("Iniciando conversión de PDF real: ${pdfFile.absolutePath} (${pdfFile.length() / 1024 / 1024} MB)...")
        val startTime = System.currentTimeMillis()

        val result = PdfCleanExtractorAndConverter.convertPdfToCleanEpub(
            pdfFile = pdfFile,
            outputEpubFile = outEpub,
            bookTitle = "Becoming the Financial Director of an Academy on a Brink of Bankruptcy",
            bookAuthor = "Autor desconocido",
            imageCompressor = { bytes -> bytes }, // En JVM usamos bytes directos
            onProgress = { p ->
                if (p.currentPage % 500 == 0 || p.currentPage == p.totalPages) {
                    println("Progreso: ${p.currentPage}/${p.totalPages} (${p.chaptersCreated} caps, ${p.illustrationsSaved} imgs, ${p.adsBlocked} spam)")
                }
            }
        )

        val durationSec = (System.currentTimeMillis() - startTime) / 1000
        println("Conversión finalizada en ${durationSec}s. Resultado: ${result.isSuccess}")
        assertTrue("La conversión debe ser exitosa", result.isSuccess)
        assertTrue("El archivo EPUB debe existir", outEpub.exists())
        assertTrue("El archivo EPUB debe tener contenido", outEpub.length() > 100_000)

        // Verificaciones críticas de los 5 síntomas
        ZipFile(outEpub).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val xhtmlEntries = entries.filter { it.name.endsWith(".xhtml") }
            println("Total capítulos XHTML generados: ${xhtmlEntries.size}")

            // Síntoma 1: Ningún archivo XHTML debe exceder los 205 KB
            for (entry in xhtmlEntries) {
                assertTrue(
                    "El archivo ${entry.name} excede el límite de seguridad (${entry.size} bytes)",
                    entry.size <= 205_000
                )
            }

            // Síntoma 4: Verificar metadatos en content.opf
            val opfEntry = zip.getEntry("OEBPS/content.opf")
            val opfText = zip.getInputStream(opfEntry).bufferedReader().readText()
            assertTrue("El título no debe tener guiones bajos", opfText.contains("<dc:title>Becoming the Financial Director of an Academy on a Brink of Bankruptcy</dc:title>"))
            assertFalse("El autor no debe ser Web Novel", opfText.contains("<dc:creator opf:role=\"aut\">Web Novel</dc:creator>"))

            // Síntoma 5: El capítulo 1 no debe ser un cascarón vacío de 575 bytes
            val chap1Entry = zip.getEntry("OEBPS/chapter_0001.xhtml")
            assertTrue("Capítulo 1 debe existir", chap1Entry != null)
            assertTrue("Capítulo 1 no debe estar vacío (${chap1Entry.size} bytes)", chap1Entry.size > 1500)

            // Síntoma 2 y 3: TOC no debe tener entradas desordenadas ni 10 capítulos de índice
            val tocEntry = zip.getEntry("OEBPS/toc.ncx")
            val tocText = zip.getInputStream(tocEntry).bufferedReader().readText()
            assertFalse("No debe tener Chapter 24 antes de Chapter 1", tocText.contains("<navLabel><text>Chapter 24: 5."))
        }

        println("¡Todas las verificaciones de fidelidad del EPUB pasaron exitosamente!")
    }
}
