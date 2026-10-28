package com.example.woodsreader.core.web2epub

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class LocalEpubBuilderIllustrationsTest {

    private lateinit var tempEpubFile: File

    @Before
    fun setUp() {
        tempEpubFile = File.createTempFile("test_illust_", ".epub")
    }

    @After
    fun tearDown() {
        if (tempEpubFile.exists()) {
            tempEpubFile.delete()
        }
    }

    @Test
    fun `LocalEpubBuilder correctly embeds cover, chapter illustrations and styling`() {
        val builder = LocalEpubBuilder(
            outputFile = tempEpubFile,
            bookTitle = "Dead Wizard Chronicles",
            bookAuthor = "Archmage Author",
            languageCode = "es"
        )

        val coverBytes = "fake_cover_bytes".toByteArray()
        builder.setCoverImage(coverBytes)

        val illustBytes = "fake_illustration_bytes".toByteArray()
        val imgMarker = builder.registerChapterImage(illustBytes)
        assertEquals("[WOODS_IMG:images/illust_0001.jpg]", imgMarker)

        builder.appendChapter(
            rawTitle = "Chapter 1: Awaken",
            paragraphs = listOf(
                "A dark whisper echoed across the cavern.",
                imgMarker,
                "The grimoire opened on its own."
            ),
            sourceUrl = "https://example.com/novel/ch1"
        )

        val finished = builder.finishEpub()
        assertTrue(finished)

        ZipFile(tempEpubFile).use { zip ->
            // 1. Portada e Ilustración binarias en OEBPS/images/
            val coverEntry = zip.getEntry("OEBPS/images/cover.jpg")
            assertNotNull(coverEntry)
            val coverRead = zip.getInputStream(coverEntry).readBytes()
            assertArrayEquals(coverBytes, coverRead)

            val illustEntry = zip.getEntry("OEBPS/images/illust_0001.jpg")
            assertNotNull(illustEntry)
            val illustRead = zip.getInputStream(illustEntry).readBytes()
            assertArrayEquals(illustBytes, illustRead)

            // 2. Registro en OPF
            val opfEntry = zip.getEntry("OEBPS/content.opf")
            assertNotNull(opfEntry)
            val opfText = String(zip.getInputStream(opfEntry).readBytes(), Charsets.UTF_8)
            assertTrue(opfText.contains("""<item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""))
            assertTrue(opfText.contains("""<meta name="cover" content="cover-image"/>"""))
            assertTrue(opfText.contains("""<item id="img_0001" href="images/illust_0001.jpg" media-type="image/jpeg"/>"""))

            // 3. Renderizado de la imagen en el capítulo XHTML
            val chEntry = zip.getEntry("OEBPS/chapter_0001.xhtml")
            assertNotNull(chEntry)
            val chText = String(zip.getInputStream(chEntry).readBytes(), Charsets.UTF_8)
            assertTrue(chText.contains("""<div class="illust-box"><img src="images/illust_0001.jpg" alt="Ilustración"/></div>"""))
            assertTrue(chText.contains("<p>A dark whisper echoed across the cavern.</p>"))

            // 4. CSS de Ilustración
            val cssEntry = zip.getEntry("OEBPS/style.css")
            assertNotNull(cssEntry)
            val cssText = String(zip.getInputStream(cssEntry).readBytes(), Charsets.UTF_8)
            assertTrue(cssText.contains(".illust-box"))
        }
    }
}
