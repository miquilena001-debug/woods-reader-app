package com.example.woodsreader.domain.reader

import com.example.woodsreader.core.managers.HighlightExporter
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Caso de uso responsable de la gestión integral de anotaciones de lectura:
 * - Subrayados (Highlights) y sus notas asociadas.
 * - Marcadores (Bookmarks) manuales y automáticos.
 * - Transformación a decoraciones visuales de Readium (Decoration).
 * - Exportación de notas y citas en formato Markdown / Texto plano.
 */
class ManageReaderAnnotationsUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    // ── Flujos de lectura ───────────────────────────────────────────────────

    fun getHighlights(bookId: String): Flow<List<HighlightEntity>> {
        return bookRepository.getHighlights(bookId)
    }

    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>> {
        return bookRepository.getBookmarks(bookId)
    }

    // ── Operaciones de Subrayados (Highlights) ──────────────────────────────

    suspend fun saveHighlight(
        bookId: String,
        locator: Locator,
        color: HighlightColor,
        text: String,
        note: String = "",
        chapterTitle: String = ""
    ): HighlightEntity {
        val chapter = chapterTitle.ifBlank { locator.title ?: "" }
        val entity = HighlightEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            highlightedText = text,
            color = color,
            note = note,
            chapterTitle = chapter,
            createdAt = System.currentTimeMillis()
        )
        bookRepository.saveHighlight(entity)
        return entity
    }

    suspend fun deleteHighlight(highlight: HighlightEntity) {
        bookRepository.deleteHighlight(highlight)
    }

    suspend fun updateHighlightColor(highlight: HighlightEntity, newColor: HighlightColor) {
        bookRepository.saveHighlight(highlight.copy(color = newColor))
    }

    suspend fun updateHighlightNote(highlight: HighlightEntity, newNote: String) {
        bookRepository.saveHighlight(highlight.copy(note = newNote))
    }

    // ── Operaciones de Marcadores (Bookmarks) ───────────────────────────────

    fun buildAutoBookmarkLabel(
        chapterTitle: String?,
        page: Int,
        progress: Float,
        snippet: String?
    ): String {
        val progressPct = String.format(Locale.US, "%.1f%%", (progress * 100f).coerceIn(0f, 100f))
        val chapter = chapterTitle?.trim().orEmpty()

        return buildString {
            if (chapter.isNotBlank()) append(chapter)
            if (page > 0) {
                if (isNotEmpty()) append(" · ")
                append("Página $page")
            }
            if (progressPct.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append("($progressPct)")
            }
            if (!snippet.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append("\"${snippet.take(110).trim()}\"")
            }
        }.ifBlank { "Página ${page.coerceAtLeast(1)} ($progressPct)" }
    }

    suspend fun createBookmark(
        bookId: String,
        locator: Locator,
        label: String? = null,
        chapterTitle: String? = null,
        page: Int = 1,
        progress: Float = 0f,
        snippet: String? = null,
        isAutomatic: Boolean = false
    ): BookmarkEntity {
        val effectiveLabel = label?.takeIf { it.isNotBlank() }
            ?: buildAutoBookmarkLabel(chapterTitle ?: locator.title, page, progress, snippet)

        val bookmark = BookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            label = effectiveLabel,
            isAutomatic = isAutomatic,
            createdAt = System.currentTimeMillis()
        )
        bookRepository.saveBookmark(bookmark)
        return bookmark
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookRepository.deleteBookmark(bookmark)
    }

    fun isPageBookmarked(
        bookmarks: List<BookmarkEntity>,
        currentLocator: Locator?,
        isPdfOrReflow: Boolean,
        reflowPage: Int
    ): Boolean {
        if (currentLocator == null) return false
        val position = if (isPdfOrReflow) reflowPage else currentLocator.locations.position ?: 0

        return bookmarks.any { bookmark ->
            val savedLocator = runCatching {
                Locator.fromJSON(JSONObject(bookmark.locatorJson))
            }.getOrNull() ?: return@any false

            if (savedLocator.href.toString() != currentLocator.href.toString()) {
                return@any false
            }
            val savedPosition = savedLocator.locations.position ?: 0
            if (position > 0 && savedPosition > 0) {
                savedPosition == position
            } else {
                val currentProgress = currentLocator.locations.totalProgression
                val savedProgress = savedLocator.locations.totalProgression
                (currentProgress != null && savedProgress != null && kotlin.math.abs(currentProgress - savedProgress) < 0.002) ||
                    (savedLocator.locations.progression != null && savedLocator.locations.progression == currentLocator.locations.progression)
            }
        }
    }

    fun findMatchingBookmark(
        bookmarks: List<BookmarkEntity>,
        currentLocator: Locator?,
        isPdfOrReflow: Boolean,
        reflowPage: Int
    ): BookmarkEntity? {
        if (currentLocator == null) return null
        val position = if (isPdfOrReflow) reflowPage else currentLocator.locations.position ?: 0

        return bookmarks.firstOrNull { bookmark ->
            val savedLocator = runCatching {
                Locator.fromJSON(JSONObject(bookmark.locatorJson))
            }.getOrNull() ?: return@firstOrNull false

            if (savedLocator.href.toString() != currentLocator.href.toString()) {
                return@firstOrNull false
            }
            val savedPosition = savedLocator.locations.position ?: 0
            if (position > 0 && savedPosition > 0) {
                savedPosition == position
            } else {
                val currentProgress = currentLocator.locations.totalProgression
                val savedProgress = savedLocator.locations.totalProgression
                (currentProgress != null && savedProgress != null && kotlin.math.abs(currentProgress - savedProgress) < 0.002) ||
                    (savedLocator.locations.progression != null && savedLocator.locations.progression == currentLocator.locations.progression)
            }
        }
    }

    suspend fun toggleBookmark(
        bookId: String,
        currentLocator: Locator?,
        bookmarks: List<BookmarkEntity>,
        isPdfOrReflow: Boolean,
        reflowPage: Int,
        chapterTitle: String? = null,
        progress: Float = 0f,
        snippet: String? = null
    ): Boolean {
        if (currentLocator == null) return false
        val existing = findMatchingBookmark(bookmarks, currentLocator, isPdfOrReflow, reflowPage)
        return if (existing != null) {
            deleteBookmark(existing)
            false
        } else {
            val page = if (isPdfOrReflow) reflowPage else currentLocator.locations.position ?: 1
            val adjustedLocator = if (isPdfOrReflow) {
                currentLocator.copy(locations = currentLocator.locations.copy(position = page))
            } else {
                currentLocator
            }
            createBookmark(
                bookId = bookId,
                locator = adjustedLocator,
                chapterTitle = chapterTitle,
                page = page,
                progress = progress,
                snippet = snippet
            )
            true
        }
    }

    // ── Decoraciones de Readium ─────────────────────────────────────────────

    fun toDecoration(highlight: HighlightEntity, tintColorArgb: Int): Decoration? {
        val locator = runCatching {
            Locator.fromJSON(JSONObject(highlight.locatorJson))
        }.getOrNull() ?: return null

        return Decoration(
            id = highlight.id,
            locator = locator,
            style = Decoration.Style.Highlight(tint = tintColorArgb)
        )
    }

    fun createDecorations(
        highlights: List<HighlightEntity>,
        colorResolver: (HighlightColor) -> Int
    ): List<Decoration> {
        return highlights.mapNotNull { hl ->
            val tint = colorResolver(hl.color)
            toDecoration(hl, tint)
        }
    }

    // ── Exportación de Apuntes y Citas ──────────────────────────────────────

    fun exportToMarkdown(bookTitle: String, bookAuthor: String, highlights: List<HighlightEntity>): String {
        return HighlightExporter.exportToMarkdown(bookTitle, bookAuthor, highlights)
    }

    fun exportToPlainText(bookTitle: String, bookAuthor: String, highlights: List<HighlightEntity>): String {
        return HighlightExporter.exportToPlainText(bookTitle, bookAuthor, highlights)
    }

    fun formatCitation(text: String, bookTitle: String, author: String, chapterTitle: String? = null): String {
        return buildString {
            append("\"${text.trim()}\"")
            appendLine()
            append("— $bookTitle")
            if (author.isNotBlank()) append(", $author")
            if (!chapterTitle.isNullOrBlank()) append(" ($chapterTitle)")
        }
    }
}
