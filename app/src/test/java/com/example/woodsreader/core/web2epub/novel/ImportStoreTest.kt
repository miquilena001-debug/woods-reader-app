package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `computeJobId is deterministic and sha1 hex`() {
        val url = "https://novelfire.net/book/test-novel"
        val id1 = ImportStore.computeJobId(url)
        val id2 = ImportStore.computeJobId(url)
        val id3 = ImportStore.computeJobId("https://novelfire.net/book/test-novel/")

        assertEquals(id1, id2)
        assertEquals(40, id1.length)
    }

    @Test
    fun `saveState and loadState work atomically`() {
        val store = ImportStore(tempFolder.root)
        val jobId = "test-job-123"

        val state = ImportJobState(
            jobId = jobId,
            novelUrl = "https://example.com/novel",
            title = "Mi Novela de Prueba",
            author = "Autor Ejemplo",
            description = "Descripción de prueba",
            coverUrl = "https://example.com/cover.jpg",
            coverLocalPath = "cover.jpg",
            totalChapters = 2,
            status = JobStatus.IN_PROGRESS,
            chapters = listOf(
                StoredChapterInfo(
                    index = 1,
                    title = "Capítulo 1",
                    url = "https://example.com/novel/ch1",
                    fileName = "chapter_0001.xhtml",
                    wordCount = 1500,
                    status = ChapterStatus.OK.name
                )
            )
        )

        store.saveState(state)

        val loaded = store.loadState(jobId)
        assertNotNull(loaded)
        assertEquals(state.jobId, loaded?.jobId)
        assertEquals(state.title, loaded?.title)
        assertEquals(1, loaded?.chapters?.size)
        assertEquals("chapter_0001.xhtml", loaded?.chapters?.get(0)?.fileName)
    }

    @Test
    fun `save and load chapter content`() {
        val store = ImportStore(tempFolder.root)
        val jobId = "job-ch-test"
        val xhtml = "<div><p>Hola mundo</p></div>"

        store.saveChapterContent(jobId, "chapter_0001.xhtml", xhtml)
        val loaded = store.loadChapterContent(jobId, "chapter_0001.xhtml")

        assertEquals(xhtml, loaded)
    }

    @Test
    fun `save and load images`() {
        val store = ImportStore(tempFolder.root)
        val jobId = "job-img-test"
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        store.saveImage(jobId, "OEBPS/images/abc.jpg", bytes)
        val loaded = store.loadImage(jobId, "abc.jpg")

        assertNotNull(loaded)
        assertEquals(4, loaded?.size)
        assertEquals(0x01.toByte(), loaded?.get(0))
    }

    @Test
    fun `deleteJob removes folder entirely`() {
        val store = ImportStore(tempFolder.root)
        val jobId = "job-to-delete"

        store.saveChapterContent(jobId, "ch1.xhtml", "<p>Test</p>")
        assertTrue(File(tempFolder.root, "jobs/$jobId").exists())

        store.deleteJob(jobId)
        assertNull(store.loadChapterContent(jobId, "ch1.xhtml"))
        assertTrue(!File(tempFolder.root, "jobs/$jobId").exists())
    }
}
