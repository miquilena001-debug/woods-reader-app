package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.domain.reader.ManageReaderAnnotationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Locator

class ReaderAnnotationsControllerTest {

    private val useCase = mockk<ManageReaderAnnotationsUseCase>(relaxed = true)
    private val bookId = "test_book_annotations"
    private val controller = ReaderAnnotationsController(useCase, bookId)

    @Test
    fun testRefreshWithNullLocatorKeepsFlagFalseAndNeverCallsUseCase() {
        controller.currentLocator = null
        controller.refresh(emptyList(), isPdfOrReflow = false, reflowPage = 1)
        assertFalse(controller.isCurrentPageBookmarked.value)
        verify(exactly = 0) { useCase.isPageBookmarked(any(), any(), any(), any()) }
    }

    @Test
    fun testRefreshCopiesIsPageBookmarkedResultIntoFlow() {
        val locator = mockk<Locator>(relaxed = true)
        controller.currentLocator = locator

        every { useCase.isPageBookmarked(any(), locator, false, 1) } returns true
        controller.refresh(emptyList(), isPdfOrReflow = false, reflowPage = 1)
        assertTrue(controller.isCurrentPageBookmarked.value)

        every { useCase.isPageBookmarked(any(), locator, false, 1) } returns false
        controller.refresh(emptyList(), isPdfOrReflow = false, reflowPage = 1)
        assertFalse(controller.isCurrentPageBookmarked.value)
    }

    @Test
    fun testAddBookmarkInReflowModeCopiesLocatorWithPositionAndCallsCreateBookmark() = runTest {
        val originalLoc = mockk<Locator>(relaxed = true)
        val copiedLoc = mockk<Locator>(relaxed = true)
        val locations = mockk<Locator.Locations>(relaxed = true)
        val copiedLocations = mockk<Locator.Locations>(relaxed = true)

        every { originalLoc.locations } returns locations
        every { locations.copy(position = 5) } returns copiedLocations
        every { originalLoc.copy(locations = copiedLocations) } returns copiedLoc
        every { useCase.isPageBookmarked(any(), any(), any(), any()) } returns false

        controller.addBookmark(
            navLocator = originalLoc,
            locator = null,
            label = "My Bookmark",
            bookmarks = emptyList(),
            isPdfOrReflow = true,
            lastKnownReflowPage = 5,
            chapterInfoTitle = "Chapter 1",
            reflowItems = emptyList(),
            globalProgress = 0.5f
        )

        coVerify {
            useCase.createBookmark(
                bookId = bookId,
                locator = copiedLoc,
                label = "My Bookmark",
                chapterTitle = "Chapter 1",
                page = 5,
                progress = 0.5f,
                snippet = any()
            )
        }
    }

    @Test
    fun testAddBookmarkReturnsWithoutCreateBookmarkWhenIsPageBookmarkedIsTrue() = runTest {
        val loc = mockk<Locator>(relaxed = true)
        every { useCase.isPageBookmarked(any(), any(), any(), any()) } returns true

        controller.addBookmark(
            navLocator = loc,
            locator = null,
            label = null,
            bookmarks = emptyList(),
            isPdfOrReflow = false,
            lastKnownReflowPage = 1,
            chapterInfoTitle = null,
            reflowItems = emptyList(),
            globalProgress = 0f
        )

        coVerify(exactly = 0) { useCase.createBookmark(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun testAddBookmarkWithNullNavLocatorAndNullLocatorDoesNotCallCreateBookmark() = runTest {
        controller.addBookmark(
            navLocator = null,
            locator = null,
            label = null,
            bookmarks = emptyList(),
            isPdfOrReflow = false,
            lastKnownReflowPage = 1,
            chapterInfoTitle = null,
            reflowItems = emptyList(),
            globalProgress = 0f
        )

        coVerify(exactly = 0) { useCase.createBookmark(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun testChapterFallbackOrderChapterInfoTitleThenLocatorTitleThenEmpty() = runTest {
        val locWithTitle = mockk<Locator>(relaxed = true)
        every { locWithTitle.title } returns "Locator Title"
        every { useCase.isPageBookmarked(any(), any(), any(), any()) } returns false

        // 1. chapterInfoTitle takes precedence over locator.title
        controller.addBookmark(
            navLocator = locWithTitle,
            locator = null,
            label = null,
            bookmarks = emptyList(),
            isPdfOrReflow = false,
            lastKnownReflowPage = 1,
            chapterInfoTitle = "Chapter Info Title",
            reflowItems = emptyList(),
            globalProgress = 0f
        )
        coVerify { useCase.createBookmark(any(), any(), any(), chapterTitle = "Chapter Info Title", page = any(), progress = any(), snippet = any()) }

        // 2. chapterInfoTitle null/blank -> falls back to locator.title
        controller.addBookmark(
            navLocator = locWithTitle,
            locator = null,
            label = null,
            bookmarks = emptyList(),
            isPdfOrReflow = false,
            lastKnownReflowPage = 1,
            chapterInfoTitle = null,
            reflowItems = emptyList(),
            globalProgress = 0f
        )
        coVerify { useCase.createBookmark(any(), any(), any(), chapterTitle = "Locator Title", page = any(), progress = any(), snippet = any()) }

        // 3. both blank/null -> ""
        val locWithoutTitle = mockk<Locator>(relaxed = true)
        every { locWithoutTitle.title } returns null
        controller.addBookmark(
            navLocator = locWithoutTitle,
            locator = null,
            label = null,
            bookmarks = emptyList(),
            isPdfOrReflow = false,
            lastKnownReflowPage = 1,
            chapterInfoTitle = "",
            reflowItems = emptyList(),
            globalProgress = 0f
        )
        coVerify { useCase.createBookmark(any(), any(), any(), chapterTitle = "", page = any(), progress = any(), snippet = any()) }
    }

    @Test
    fun testAddHighlightUsesLocatorTitleBeforeChapterInfoTitleElseEmpty() = runTest {
        val locWithTitle = mockk<Locator>(relaxed = true)
        every { locWithTitle.title } returns "Loc Highlight Title"

        // 1. locator.title preferred over chapterInfoTitle
        controller.addHighlight(
            locator = locWithTitle,
            color = HighlightColor.YELLOW,
            text = "Highlighted Text",
            note = "Some Note",
            chapterInfoTitle = "Fallback Chapter"
        )
        coVerify {
            useCase.saveHighlight(
                bookId = bookId,
                locator = locWithTitle,
                color = HighlightColor.YELLOW,
                text = "Highlighted Text",
                note = "Some Note",
                chapterTitle = "Loc Highlight Title"
            )
        }

        // 2. locator.title is null -> uses chapterInfoTitle
        val locWithoutTitle = mockk<Locator>(relaxed = true)
        every { locWithoutTitle.title } returns null
        controller.addHighlight(
            locator = locWithoutTitle,
            color = HighlightColor.GREEN,
            text = "Text 2",
            note = "",
            chapterInfoTitle = "Fallback Chapter"
        )
        coVerify {
            useCase.saveHighlight(
                bookId = bookId,
                locator = locWithoutTitle,
                color = HighlightColor.GREEN,
                text = "Text 2",
                note = "",
                chapterTitle = "Fallback Chapter"
            )
        }

        // 3. both null -> ""
        controller.addHighlight(
            locator = locWithoutTitle,
            color = HighlightColor.BLUE,
            text = "Text 3",
            note = "",
            chapterInfoTitle = null
        )
        coVerify {
            useCase.saveHighlight(
                bookId = bookId,
                locator = locWithoutTitle,
                color = HighlightColor.BLUE,
                text = "Text 3",
                note = "",
                chapterTitle = ""
            )
        }
    }
}
