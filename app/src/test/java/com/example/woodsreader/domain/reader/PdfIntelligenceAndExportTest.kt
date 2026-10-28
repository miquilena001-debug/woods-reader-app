package com.example.woodsreader.domain.reader

import com.example.woodsreader.core.search.FastEpubSearchEngine
import com.example.woodsreader.core.tts.ChapterAudioExporter
import com.example.woodsreader.core.tts.ChapterTextChunk
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PdfIntelligenceAndExportTest {

    private lateinit var dummyPdfFile: File
    private val testScope = TestScope()

    @Before
    fun setUp() {
        FastEpubSearchEngine.clearCache()
        dummyPdfFile = File.createTempFile("dummy_novel_", ".pdf")
        val doc = PDDocument()
        try {
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            doc.save(dummyPdfFile)
        } finally {
            doc.close()
        }
    }

    @After
    fun tearDown() {
        FastEpubSearchEngine.clearCache()
        if (dummyPdfFile.exists()) {
            dummyPdfFile.delete()
        }
    }

    @Test
    fun `searchPdfStreaming finds occurrences across PDF pages with snippets and page links`() = runTest {
        val uniqueBookId = "test_book_pdf_${System.currentTimeMillis()}"
        val flow = FastEpubSearchEngine.searchPdfStreaming(
            bookId = uniqueBookId,
            pdfFile = dummyPdfFile,
            publication = null,
            rawQuery = "spellbook",
            startFromPage = 1,
            pageTextExtractor = { _, page ->
                if (page == 1) {
                    "Chapter 1: The Magic Awakens\nThe young sorcerer found an ancient spellbook in the woods."
                } else {
                    "Chapter 2: The Academy\nAt dawn they reached the grand hall."
                }
            }
        )

        val progressList = flow.toList()
        assertTrue(progressList.isNotEmpty())
        val finalProgress = progressList.last()
        assertTrue(finalProgress.isFinished)
        assertEquals(1, finalProgress.hits.size)

        val hit = finalProgress.hits[0]
        assertEquals(1, hit.chapterIndex)
        assertTrue(hit.chapterHref.contains("page=1") || hit.chapterHref.contains("page_1"))
        assertTrue(hit.snippet.contains("spellbook"))
    }

    @Test
    fun `exportPdfChapters triggers batch export with healed chapter text`() = runTest {
        val chapterAudioExporter = mockk<ChapterAudioExporter>(relaxed = true)
        val coordinator = ReaderIntelligenceAndExportCoordinator(
            chapterAudioExporter = chapterAudioExporter
        )

        val book = BookEntity(
            id = "test_pdf_id",
            title = "Wizard Novel",
            author = "Grand Magus",
            filePath = dummyPdfFile.absolutePath,
            fileHash = "dummy_hash_123",
            format = BookFormat.PDF
        )

        val slotChunkProvider = slot<suspend (Int) -> ChapterTextChunk?>()
        coEvery {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = any(),
                exportLabel = any(),
                totalChaptersCount = any(),
                chapterProvider = capture(slotChunkProvider),
                speed = any(),
                voiceOption = any(),
                includeSyncedLyrics = any(),
                coverImageFile = any(),
                bookAuthor = any()
            )
        } returns Result.success("/sdcard/Music/WoodsReader/test.m4a")

        coordinator.exportPdfChapters(
            scope = testScope,
            book = book,
            pdfFile = dummyPdfFile,
            startPage = 1,
            maxChaptersOrPagesToExport = 2,
            speed = 1.0f,
            coverFile = null,
            pdfPageExtractor = { _, from, _ ->
                if (from == 1) {
                    "Chapter 1: The Magic Awakens\nThe young sorcerer discovered an ancient spellbook in the woods."
                } else {
                    "Chapter 2: The Academy\nAt dawn they reached the sanctuary."
                }
            }
        )

        testScope.testScheduler.advanceUntilIdle()

        coVerify {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = "Wizard Novel",
                exportLabel = any(),
                totalChaptersCount = 2,
                chapterProvider = any(),
                speed = 1.0f,
                voiceOption = null,
                includeSyncedLyrics = true,
                coverImageFile = null,
                bookAuthor = "Grand Magus"
            )
        }

        val chunk0 = slotChunkProvider.captured.invoke(0)
        assertTrue(chunk0 != null)
        assertTrue(chunk0!!.chapterTitle.isNotBlank())
        assertTrue(chunk0.paragraphs.any { it.contains("young sorcerer") || it.contains("ancient spellbook") })
    }
}
