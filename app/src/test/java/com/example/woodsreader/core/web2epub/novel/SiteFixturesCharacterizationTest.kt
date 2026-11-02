package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class SiteFixturesCharacterizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun loadAssetProfile(name: String): SiteProfile {
        val candidates = listOf(
            File("app/src/main/assets/site_profiles/$name"),
            File("src/main/assets/site_profiles/$name")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Asset profile $name not found in candidates: $candidates")
        return SiteProfileRegistry.parse(file.readText(Charsets.UTF_8))
    }

    private fun createPage(url: String, html: String): FetchedPage {
        return FetchedPage(
            requestedUrl = url,
            finalUrl = url,
            status = 200,
            bytes = html.toByteArray(Charsets.UTF_8),
            contentType = "text/html; charset=utf-8",
            headers = mapOf("content-type" to "text/html; charset=utf-8")
        )
    }

    @Test
    fun `novelfire real fixture end-to-end import characterization`() = runBlocking {
        val indexUrl = "https://novelfire.net/book/this-is-really-not-mechanical-ascension"
        val chaptersNavUrl = "https://novelfire.net/book/this-is-really-not-mechanical-ascension/chapters"
        val ch1Url = "https://novelfire.net/book/this-is-really-not-mechanical-ascension/chapter-1"
        val ch2Url = "https://novelfire.net/book/this-is-really-not-mechanical-ascension/chapter-2"

        val indexHtml = loadResource("sites/novelfire/index.html")
        val chaptersHtml = loadResource("sites/novelfire/chapters.html")
        val ch1Html = loadResource("sites/novelfire/chapter_1.html")
        val ch2Html = loadResource("sites/novelfire/chapter_2.html")

        val profile = loadAssetProfile("novelfire.json")
        val registry = SiteProfileRegistry(listOf(profile))

        val fakeFetcher = FakePageFetcher().apply {
            setResponse(indexUrl, createPage(indexUrl, indexHtml))
            setResponse(chaptersNavUrl, createPage(chaptersNavUrl, chaptersHtml))
            setResponse(ch1Url, createPage(ch1Url, ch1Html))
            setResponse(ch2Url, createPage(ch2Url, ch2Html))
        }

        val store = ImportStore(tempFolder.newFolder("store_nf"))
        val outputDir = tempFolder.newFolder("output_nf")

        val importer = NovelImporter(
            fetcher = fakeFetcher,
            profileRegistry = registry,
            store = store
        )

        val epubFile = importer.importNovel(
            novelUrl = indexUrl,
            outputDirectory = outputDir,
            startIndex = 0,
            endIndex = 2,
            forceReverse = false
        )

        assertNotNull("EPUB file must be generated", epubFile)
        assertTrue("EPUB file must exist physically", epubFile!!.exists())

        // 1. Validar metadatos en Store
        val jobId = ImportStore.computeJobId(indexUrl)
        val state = store.loadState(jobId)
        assertNotNull(state)
        assertEquals("This is really not mechanical ascension", state?.title)
        assertEquals("Reunion Dinner", state?.author)
        assertEquals(2, state?.chapters?.size)

        // 2. Validar títulos de capítulos y orden
        val ch1 = state!!.chapters[0]
        val ch2 = state.chapters[1]
        assertEquals(1, ch1.index)
        assertEquals("Chapter 1 - 1 1 First Control", ch1.title)
        assertEquals(2, ch2.index)
        assertEquals("Chapter 2 - 2 2 The Room Upstairs", ch2.title)

        // 3. Validar contenido limpio (longitud de texto > 0 y ausencia de anuncios)
        val ch1Content = store.loadChapterContent(jobId, ch1.fileName)
        val ch2Content = store.loadChapterContent(jobId, ch2.fileName)
        assertNotNull(ch1Content)
        assertNotNull(ch2Content)
        assertTrue(ch1.wordCount >= 15)
        assertTrue(ch2.wordCount >= 15)
        assertFalse("Anuncios .ad-container deben ser eliminados", ch1Content!!.contains("ad-container"))
        assertFalse("Anuncios .ads deben ser eliminados", ch1Content.contains("class=\"ads\""))
        assertFalse("Palabras removeTextContains deben ser eliminadas", ch1Content.contains("Novel Fire"))

        // 4. Validar entradas en nav.xhtml y toc.ncx dentro del ZIP
        ZipFile(epubFile).use { zip ->
            val navEntry = zip.getEntry("OEBPS/nav.xhtml")
            assertNotNull("nav.xhtml debe existir", navEntry)
            val navText = zip.getInputStream(navEntry).bufferedReader(Charsets.UTF_8).readText()
            assertTrue(navText.contains("Chapter 1 - 1 1 First Control"))
            assertTrue(navText.contains("Chapter 2 - 2 2 The Room Upstairs"))
            assertEquals(2, navText.split("<li><a href=\"chapter_").size - 1)

            val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
            assertNotNull("toc.ncx debe existir", ncxEntry)
            val ncxText = zip.getInputStream(ncxEntry).bufferedReader(Charsets.UTF_8).readText()
            assertTrue(ncxText.contains("<navPoint id=\"navpoint-1\""))
            assertTrue(ncxText.contains("<navPoint id=\"navpoint-2\""))
        }
    }

    @Test
    fun `novelusb real fixture end-to-end import characterization`() = runBlocking {
        val indexUrl = "https://novelusb.com/novel-book/almighty-daughter-runs-the-world-novel"
        val ch1Url = "https://novelusb.com/novel-book/almighty-daughter-runs-the-world-novel/chapter-1"
        val ch2Url = "https://novelusb.com/novel-book/almighty-daughter-runs-the-world-novel/chapter-2"

        val indexHtml = loadResource("sites/novelusb/index.html")
        val ch1Html = loadResource("sites/novelusb/chapter_1.html")
        val ch2Html = loadResource("sites/novelusb/chapter_2.html")

        val profile = loadAssetProfile("novelusb.json")
        val registry = SiteProfileRegistry(listOf(profile))

        val fakeFetcher = FakePageFetcher().apply {
            setResponse(indexUrl, createPage(indexUrl, indexHtml))
            setResponse(ch1Url, createPage(ch1Url, ch1Html))
            setResponse(ch2Url, createPage(ch2Url, ch2Html))
        }

        val store = ImportStore(tempFolder.newFolder("store_nu"))
        val outputDir = tempFolder.newFolder("output_nu")

        val importer = NovelImporter(
            fetcher = fakeFetcher,
            profileRegistry = registry,
            store = store
        )

        val epubFile = importer.importNovel(
            novelUrl = indexUrl,
            outputDirectory = outputDir,
            startIndex = 0,
            endIndex = 2,
            forceReverse = false
        )

        assertNotNull("EPUB file must be generated", epubFile)
        assertTrue("EPUB file must exist physically", epubFile!!.exists())

        // 1. Validar metadatos en Store
        val jobId = ImportStore.computeJobId(indexUrl)
        val state = store.loadState(jobId)
        assertNotNull(state)
        assertEquals("Almighty Daughter Runs The World", state?.title)
        assertEquals("Novel 2023", state?.author) // Extraído de meta[name=author]
        assertEquals(2, state?.chapters?.size)

        // 2. Validar títulos de capítulos y orden
        val ch1 = state!!.chapters[0]
        val ch2 = state.chapters[1]
        assertEquals(1, ch1.index)
        assertEquals("Chapter 1", ch1.title)
        assertEquals(2, ch2.index)
        assertEquals("Chapter 2", ch2.title)

        // 3. Validar contenido limpio
        val ch1Content = store.loadChapterContent(jobId, ch1.fileName)
        val ch2Content = store.loadChapterContent(jobId, ch2.fileName)
        assertNotNull(ch1Content)
        assertNotNull(ch2Content)
        assertTrue(ch1.wordCount >= 15)
        assertTrue(ch2.wordCount >= 15)

        // 4. Validar entradas en nav.xhtml y toc.ncx dentro del ZIP
        ZipFile(epubFile).use { zip ->
            val navEntry = zip.getEntry("OEBPS/nav.xhtml")
            assertNotNull("nav.xhtml debe existir", navEntry)
            val navText = zip.getInputStream(navEntry).bufferedReader(Charsets.UTF_8).readText()
            assertTrue(navText.contains("Chapter 1"))
            assertTrue(navText.contains("Chapter 2"))
            assertEquals(2, navText.split("<li><a href=\"chapter_").size - 1)

            val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
            assertNotNull("toc.ncx debe existir", ncxEntry)
            val ncxText = zip.getInputStream(ncxEntry).bufferedReader(Charsets.UTF_8).readText()
            assertTrue(ncxText.contains("<navPoint id=\"navpoint-1\""))
            assertTrue(ncxText.contains("<navPoint id=\"navpoint-2\""))
        }
    }
}
