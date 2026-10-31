package com.example.woodsreader.domain.reader

import com.example.woodsreader.core.tts.ChapterAudioExporter
import com.example.woodsreader.core.tts.ChapterTextChunk
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReflowItem
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderIntelligenceAndExportCoordinatorExportAudioTest {

    private val testScope = TestScope()

    private fun createMockLink(hrefStr: String): Link {
        val link = mockk<Link>(relaxed = true)
        val linkUrl = mockk<Url>(relaxed = true)
        every { linkUrl.toString() } returns hrefStr
        val hrefObj = mockk<Href>(relaxed = true)
        every { hrefObj.toString() } returns hrefStr
        every { link.href } returns hrefObj
        return link
    }

    private fun createMockPublication(links: List<Link>): Publication {
        val pub = mockk<Publication>(relaxed = true)
        every { pub.readingOrder } returns links
        return pub
    }

    @Test
    fun `testEpubBranchWithMaxChapters1UsesStartChapterTitleAndCount1`() = runTest {
        val chapterAudioExporter = mockk<ChapterAudioExporter>(relaxed = true)
        val coordinator = ReaderIntelligenceAndExportCoordinator(chapterAudioExporter = chapterAudioExporter)

        val book = BookEntity(
            id = "epub_1",
            title = "Test EPUB",
            author = "Author",
            filePath = "non_existent.epub",
            fileHash = "hash1",
            format = BookFormat.EPUB
        )

        val link1 = createMockLink("chapter1.xhtml")
        val link2 = createMockLink("chapter2.xhtml")
        val publication = createMockPublication(listOf(link1, link2))

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
        } returns Result.success("/path/output.m4a")

        coordinator.exportAudio(
            scope = testScope,
            book = book,
            publication = publication,
            isPdfBook = false,
            isReflowMode = false,
            startChapTitle = "Capítulo 1",
            maxChaptersToExport = 1,
            speed = 1.0f,
            coverFile = null,
            currentTtsPage = 0,
            reflowItems = emptyList(),
            currentHref = "chapter1.xhtml",
            resolveChapterChunk = { targetIdx, _ ->
                ChapterTextChunk("Title $targetIdx", listOf("Paragraph 1"))
            }
        )

        testScope.testScheduler.advanceUntilIdle()

        coVerify {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = "Test EPUB",
                exportLabel = "Capítulo 1",
                totalChaptersCount = 1,
                chapterProvider = any(),
                speed = 1.0f,
                voiceOption = null,
                includeSyncedLyrics = true,
                coverImageFile = null,
                bookAuthor = "Author"
            )
        }
        verify { chapterAudioExporter.setActiveJob(any()) }

        val chunk = slotChunkProvider.captured.invoke(0)
        assertNotNull(chunk)
        assertEquals("Title 0", chunk!!.chapterTitle)
        assertEquals(listOf("Paragraph 1"), chunk.paragraphs)
    }

    @Test
    fun `testPdfBookWithNonExistentPathFallsToReflowBranch`() = runTest {
        val chapterAudioExporter = mockk<ChapterAudioExporter>(relaxed = true)
        val coordinator = ReaderIntelligenceAndExportCoordinator(chapterAudioExporter = chapterAudioExporter)

        val nonExistentPdfPath = File(System.getProperty("java.io.tmpdir"), "non_existent_${System.currentTimeMillis()}.pdf").absolutePath
        val book = BookEntity(
            id = "pdf_missing",
            title = "Missing PDF",
            author = "Author",
            filePath = nonExistentPdfPath,
            fileHash = "hash2",
            format = BookFormat.PDF
        )

        val publication = createMockPublication(emptyList())
        val reflowItems: List<ReflowItem> = listOf(
            ReflowItem.Header(content = "Header Text", level = 1, pageNumber = 1),
            ReflowItem.Paragraph(content = "Paragraph Text", pageNumber = 1)
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
        } returns Result.success("/path/output.m4a")

        coordinator.exportAudio(
            scope = testScope,
            book = book,
            publication = publication,
            isPdfBook = true,
            isReflowMode = false,
            startChapTitle = "Reflow Fallback Chap",
            maxChaptersToExport = 1,
            speed = 1.25f,
            coverFile = null,
            currentTtsPage = 0,
            reflowItems = reflowItems,
            currentHref = "",
            resolveChapterChunk = { _, _ -> null }
        )

        testScope.testScheduler.advanceUntilIdle()

        coVerify {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = "Missing PDF",
                exportLabel = "Reflow Fallback Chap",
                totalChaptersCount = 1,
                chapterProvider = any(),
                speed = 1.25f,
                voiceOption = null,
                includeSyncedLyrics = true,
                coverImageFile = null,
                bookAuthor = "Author"
            )
        }
        verify { chapterAudioExporter.setActiveJob(any()) }

        val chunk = slotChunkProvider.captured.invoke(0)
        assertNotNull(chunk)
        assertEquals("Reflow Fallback Chap", chunk!!.chapterTitle)
        assertEquals(listOf("Header Text", "Paragraph Text"), chunk.paragraphs)
    }

    @Test
    fun `testReflowModeTrueTakesReflowBranch`() = runTest {
        val chapterAudioExporter = mockk<ChapterAudioExporter>(relaxed = true)
        val coordinator = ReaderIntelligenceAndExportCoordinator(chapterAudioExporter = chapterAudioExporter)

        val book = BookEntity(
            id = "reflow_book",
            title = "Reflow Book",
            author = "Author",
            filePath = "dummy_epub.epub",
            fileHash = "hash3",
            format = BookFormat.EPUB
        )

        val publication = createMockPublication(emptyList())
        val reflowItems: List<ReflowItem> = listOf(
            ReflowItem.Paragraph(content = "Reflow content", pageNumber = 1)
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
        } returns Result.success("/path/output.m4a")

        coordinator.exportAudio(
            scope = testScope,
            book = book,
            publication = publication,
            isPdfBook = false,
            isReflowMode = true,
            startChapTitle = "Reflow Title",
            maxChaptersToExport = 1,
            speed = 1.0f,
            coverFile = null,
            currentTtsPage = 0,
            reflowItems = reflowItems,
            currentHref = "",
            resolveChapterChunk = { _, _ -> null }
        )

        testScope.testScheduler.advanceUntilIdle()

        coVerify {
            chapterAudioExporter.exportChaptersToM4a(
                bookTitle = "Reflow Book",
                exportLabel = "Reflow Title",
                totalChaptersCount = 1,
                chapterProvider = any(),
                speed = 1.0f,
                voiceOption = null,
                includeSyncedLyrics = true,
                coverImageFile = null,
                bookAuthor = "Author"
            )
        }
        verify { chapterAudioExporter.setActiveJob(any()) }

        val chunk = slotChunkProvider.captured.invoke(0)
        assertNotNull(chunk)
        assertEquals("Reflow Title", chunk!!.chapterTitle)
        assertEquals(listOf("Reflow content"), chunk.paragraphs)
    }
}
