package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.ReflowItem
import com.example.woodsreader.domain.reader.ManageReaderAnnotationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.shared.publication.Locator

class ReaderAnnotationsController(
    private val useCase: ManageReaderAnnotationsUseCase,
    private val bookId: String
) {
    /** Plain field on purpose (read on hot paths). */
    var currentLocator: Locator? = null

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    fun refresh(bookmarks: List<BookmarkEntity>, isPdfOrReflow: Boolean, reflowPage: Int) {
        val locator = currentLocator ?: return
        _isCurrentPageBookmarked.value = useCase.isPageBookmarked(
            bookmarks = bookmarks, currentLocator = locator,
            isPdfOrReflow = isPdfOrReflow, reflowPage = reflowPage
        )
    }

    suspend fun addBookmark(
        navLocator: Locator?,
        locator: Locator?,
        label: String?,
        bookmarks: List<BookmarkEntity>,
        isPdfOrReflow: Boolean,
        lastKnownReflowPage: Int,
        chapterInfoTitle: String?,
        reflowItems: List<ReflowItem>,
        globalProgress: Float
    ) {
        val currentLoc = navLocator ?: locator
        val page = if (isPdfOrReflow) {
            lastKnownReflowPage
        } else {
            currentLoc?.locations?.position ?: 1
        }
        val bookmarkLocator = currentLoc?.let {
            if (isPdfOrReflow) {
                it.copy(locations = it.locations.copy(position = page))
            } else {
                it
            }
        } ?: return
        currentLocator = bookmarkLocator
        refresh(bookmarks, isPdfOrReflow, lastKnownReflowPage)
        if (_isCurrentPageBookmarked.value) return

        val chapter = chapterInfoTitle?.takeIf { it.isNotBlank() }
            ?: bookmarkLocator.title?.takeIf { it.isNotBlank() }
            ?: ""
        val snippet = bookmarkLocator.text.highlight?.trim()?.takeIf { it.isNotBlank() }
            ?: bookmarkLocator.text.after?.trim()?.take(120)?.takeIf { it.isNotBlank() }
            ?: if (isPdfOrReflow) {
                reflowItems.firstOrNull { item ->
                    item.pageNumber == page && (item is ReflowItem.Paragraph || item is ReflowItem.Header)
                }?.let { item ->
                    when (item) {
                        is ReflowItem.Paragraph -> item.content
                        is ReflowItem.Header -> item.content
                        else -> null
                    }?.trim()?.take(120)
                }
            } else {
                null
            }

        useCase.createBookmark(
            bookId = bookId,
            locator = bookmarkLocator,
            label = label,
            chapterTitle = chapter,
            page = page,
            progress = globalProgress,
            snippet = snippet
        )
    }

    suspend fun addHighlight(
        locator: Locator,
        color: HighlightColor,
        text: String,
        note: String,
        chapterInfoTitle: String?
    ) {
        val chapter = locator.title ?: chapterInfoTitle ?: ""
        useCase.saveHighlight(
            bookId = bookId,
            locator = locator,
            color = color,
            text = text,
            note = note,
            chapterTitle = chapter
        )
    }
}
