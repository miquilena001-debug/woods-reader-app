package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class NovelImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `importNovel orchestrates whole pipeline and produces valid EPUB 3`() = runBlocking {
        val novelUrl = "https://example.com/novel/toc"
        val ch1Url = "https://example.com/novel/capitulo-1"
        val ch2Url = "https://example.com/novel/capitulo-2"

        fun createPage(url: String, html: String): FetchedPage {
            return FetchedPage(
                requestedUrl = url,
                finalUrl = url,
                status = 200,
                bytes = html.toByteArray(Charsets.UTF_8),
                contentType = "text/html; charset=utf-8",
                headers = emptyMap()
            )
        }

        val fakeFetcher = FakePageFetcher().apply {
            setResponse(novelUrl, createPage(novelUrl, Fixtures.TOC_DESCENDING_HTML))
            setResponse(ch1Url, createPage(ch1Url, Fixtures.CHAPTER_RICH_HTML))
            setResponse(ch2Url, createPage(ch2Url, Fixtures.CHAPTER_ONLY_BR_HTML))
        }

        val registry = SiteProfileRegistry(emptyList())
        val store = ImportStore(tempFolder.newFolder("store"))
        val outputDir = tempFolder.newFolder("output")

        val importer = NovelImporter(
            fetcher = fakeFetcher,
            profileRegistry = registry,
            store = store
        )

        val epubFile = importer.importNovel(
            novelUrl = novelUrl,
            outputDirectory = outputDir,
            startIndex = 0,
            endIndex = 2,
            forceReverse = false
        )

        assertNotNull(epubFile)
        assertTrue(epubFile!!.exists())
        assertEquals("Novela de Prueba.epub", epubFile.name)

        // Validar que el EPUB 3 tiene estructura correcta y entradas esperadas
        ZipFile(epubFile).use { zip ->
            assertNotNull(zip.getEntry("mimetype"))
            assertNotNull(zip.getEntry("META-INF/container.xml"))
            assertNotNull(zip.getEntry("OEBPS/content.opf"))
            assertNotNull(zip.getEntry("OEBPS/nav.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/chapter_0001.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/chapter_0002.xhtml"))
        }

        // Validar que el estado quedó grabado en el store
        val jobId = ImportStore.computeJobId(novelUrl)
        val state = store.loadState(jobId)
        assertNotNull(state)
        assertEquals(JobStatus.COMPLETED, state?.status)
        assertEquals(2, state?.chapters?.size)
    }
}
