package com.example.woodsreader.domain.reader

import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

class ManageReaderAnnotationsUseCaseTest {

    private val bookRepository: BookRepository = mockk(relaxed = true)
    private lateinit var useCase: ManageReaderAnnotationsUseCase

    @Before
    fun setUp() {
        useCase = ManageReaderAnnotationsUseCase(bookRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockLocator(
        hrefStr: String,
        position: Int? = null,
        progression: Double? = null,
        totalProgression: Double? = null
    ): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val url = mockk<Url>(relaxed = true)
        every { url.toString() } returns hrefStr
        every { locator.href } returns url

        val locations = mockk<Locator.Locations>(relaxed = true)
        every { locations.position } returns position
        every { locations.progression } returns progression
        every { locations.totalProgression } returns totalProgression
        every { locator.locations } returns locations

        val json = mockk<org.json.JSONObject>()
        every { json.toString() } returns """{"href":"$hrefStr"}"""
        every { locator.toJSON() } returns json
        return locator
    }

    // ── Marcadores (isPageBookmarked & findMatchingBookmark) ─────────────────

    @Test
    fun testIsPageBookmarked_epubPositionMatch() {
        mockkObject(Locator.Companion)
        val currentLoc = createMockLocator("chap1.xhtml", position = 5)
        val savedLoc = createMockLocator("chap1.xhtml", position = 5)
        every { Locator.fromJSON(any()) } returns savedLoc

        val bookmark = BookmarkEntity(
            id = "bm_1",
            bookId = "book_1",
            locatorJson = """{"href":"chap1.xhtml"}""",
            label = "Página 5"
        )

        val isBookmarked = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = false,
            reflowPage = 1
        )
        assertTrue(isBookmarked)

        // Posición diferente
        val differentSavedLoc = createMockLocator("chap1.xhtml", position = 8)
        every { Locator.fromJSON(any()) } returns differentSavedLoc
        val isNotBookmarked = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = false,
            reflowPage = 1
        )
        assertFalse(isNotBookmarked)
    }

    @Test
    fun testIsPageBookmarked_epubProgressionMatch() {
        mockkObject(Locator.Companion)
        // Posiciones nulas, match por totalProgression (dentro de tolerancia 0.002)
        val currentLoc = createMockLocator("chap2.xhtml", position = null, totalProgression = 0.4500)
        val savedLoc = createMockLocator("chap2.xhtml", position = null, totalProgression = 0.4510)
        every { Locator.fromJSON(any()) } returns savedLoc

        val bookmark = BookmarkEntity(
            id = "bm_prog",
            bookId = "book_1",
            locatorJson = """{"href":"chap2.xhtml"}"""
        )

        val isBookmarked = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = false,
            reflowPage = 1
        )
        assertTrue(isBookmarked)

        // Fuera de tolerancia (diferencia 0.05 > 0.002)
        val distantSavedLoc = createMockLocator("chap2.xhtml", position = null, totalProgression = 0.5000)
        every { Locator.fromJSON(any()) } returns distantSavedLoc
        val isDistantBookmarked = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = false,
            reflowPage = 1
        )
        assertFalse(isDistantBookmarked)
    }

    @Test
    fun testIsPageBookmarked_pdfOrReflowPageMatch() {
        mockkObject(Locator.Companion)
        // En PDF o Reflow la posición se evalúa contra reflowPage
        val currentLoc = createMockLocator("doc.pdf", position = 1)
        val savedLoc = createMockLocator("doc.pdf", position = 42)
        every { Locator.fromJSON(any()) } returns savedLoc

        val bookmark = BookmarkEntity(
            id = "bm_pdf",
            bookId = "book_pdf",
            locatorJson = """{"href":"doc.pdf"}"""
        )

        val matched = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = true,
            reflowPage = 42
        )
        assertTrue(matched)

        val notMatched = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = true,
            reflowPage = 43
        )
        assertFalse(notMatched)
    }

    @Test
    fun testIsPageBookmarked_differentHrefReturnsFalse() {
        mockkObject(Locator.Companion)
        val currentLoc = createMockLocator("chap2.xhtml", position = 5)
        val savedLoc = createMockLocator("chap1.xhtml", position = 5)
        every { Locator.fromJSON(any()) } returns savedLoc

        val bookmark = BookmarkEntity(
            id = "bm_other",
            bookId = "book_1",
            locatorJson = """{"href":"chap1.xhtml"}"""
        )

        val matched = useCase.isPageBookmarked(
            bookmarks = listOf(bookmark),
            currentLocator = currentLoc,
            isPdfOrReflow = false,
            reflowPage = 5
        )
        assertFalse(matched)
    }

    @Test
    fun testFindMatchingBookmark_returnsCorrectBookmarkEntity() {
        mockkObject(Locator.Companion)
        val currentLoc = createMockLocator("chap3.xhtml", position = 10)
        val savedLoc = createMockLocator("chap3.xhtml", position = 10)
        every { Locator.fromJSON(any()) } returns savedLoc

        val bm1 = BookmarkEntity(id = "bm_1", bookId = "b1", locatorJson = "{}")
        val found = useCase.findMatchingBookmark(
            bookmarks = listOf(bm1),
            currentLocator = currentLoc,
            isPdfOrReflow = false,
            reflowPage = 1
        )
        assertEquals(bm1, found)
    }

    @Test
    fun testToggleBookmark_deletesIfExisting() = runTest {
        mockkObject(Locator.Companion)
        val currentLoc = createMockLocator("chap1.xhtml", position = 2)
        val savedLoc = createMockLocator("chap1.xhtml", position = 2)
        every { Locator.fromJSON(any()) } returns savedLoc

        val existingBm = BookmarkEntity(id = "bm_exist", bookId = "book_1", locatorJson = "{}")

        val result = useCase.toggleBookmark(
            bookId = "book_1",
            currentLocator = currentLoc,
            bookmarks = listOf(existingBm),
            isPdfOrReflow = false,
            reflowPage = 2
        )

        assertFalse(result) // eliminado
        coVerify(exactly = 1) { bookRepository.deleteBookmark(existingBm) }
    }

    @Test
    fun testToggleBookmark_createsIfNotExisting() = runTest {
        val currentLoc = createMockLocator("chap1.xhtml", position = 2)

        val result = useCase.toggleBookmark(
            bookId = "book_1",
            currentLocator = currentLoc,
            bookmarks = emptyList(),
            isPdfOrReflow = false,
            reflowPage = 2,
            chapterTitle = "Capítulo 1",
            progress = 0.15f
        )

        assertTrue(result) // creado
        coVerify(exactly = 1) { bookRepository.saveBookmark(any()) }
    }

    // ── Etiquetado Automático (buildAutoBookmarkLabel) ───────────────────────

    @Test
    fun testBuildAutoBookmarkLabel_withChapterPageAndSnippet() {
        val label = useCase.buildAutoBookmarkLabel(
            chapterTitle = "Capítulo 1",
            page = 5,
            progress = 0.254f,
            snippet = "En un agujero en el suelo vivía un hobbit."
        )
        assertTrue(label.contains("Capítulo 1 · Página 5 (25.4%)"))
        assertTrue(label.contains("\"En un agujero en el suelo vivía un hobbit.\""))
    }

    // ── Exportación Markdown y Texto Plano ───────────────────────────────────

    @Test
    fun testExportToMarkdown_formatsHighlightsProperly() {
        val hl1 = HighlightEntity(
            id = "hl_1",
            bookId = "b1",
            locatorJson = "{}",
            highlightedText = "El camino sigue y sigue.",
            color = HighlightColor.YELLOW,
            note = "Cita memorable sobre el viaje",
            chapterTitle = "Capítulo 1"
        )

        val markdown = useCase.exportToMarkdown(
            bookTitle = "El Señor de los Anillos",
            bookAuthor = "J.R.R. Tolkien",
            highlights = listOf(hl1)
        )

        assertTrue(markdown.contains("# Notas y Subrayados: El Señor de los Anillos"))
        assertTrue(markdown.contains("por J.R.R. Tolkien"))
        assertTrue(markdown.contains("## Capítulo 1"))
        assertTrue(markdown.contains("> El camino sigue y sigue."))
        assertTrue(markdown.contains("**Nota:** Cita memorable sobre el viaje"))
    }

    @Test
    fun testExportToPlainText_formatsPlainText() {
        val hl1 = HighlightEntity(
            id = "hl_1",
            bookId = "b1",
            locatorJson = "{}",
            highlightedText = "Frase simple.",
            note = ""
        )
        val text = useCase.exportToPlainText("Dune", "Frank Herbert", listOf(hl1))
        assertTrue(text.contains("Notas y Subrayados: Dune"))
        assertTrue(text.contains("\"Frase simple.\""))
    }

    // ── Citas con Formato (formatCitation) ───────────────────────────────────

    @Test
    fun testFormatCitation_includesAuthorAndChapterTitle() {
        val citation = useCase.formatCitation(
            text = "No todos los que vagan están perdidos",
            bookTitle = "La Comunidad del Anillo",
            author = "J.R.R. Tolkien",
            chapterTitle = "Bajo el sauce"
        )
        assertEquals(
            "\"No todos los que vagan están perdidos\"\n— La Comunidad del Anillo, J.R.R. Tolkien (Bajo el sauce)",
            citation
        )
    }

    // ── Decoraciones Readium (toDecoration) ──────────────────────────────────

    @Test
    fun testToDecoration_validHighlightReturnsDecoration() {
        mockkObject(Locator.Companion)
        val mockLocator = createMockLocator("chap1.xhtml")
        every { Locator.fromJSON(any()) } returns mockLocator

        val hl = HighlightEntity(
            id = "hl_dec_1",
            bookId = "b1",
            locatorJson = """{"href":"chap1.xhtml"}""",
            highlightedText = "Texto",
            color = HighlightColor.GREEN
        )

        val decoration = useCase.toDecoration(hl, tintColorArgb = 0x00FF00)
        assertNotNull(decoration)
        assertEquals("hl_dec_1", decoration!!.id)
        assertTrue(decoration.style is Decoration.Style.Highlight)
        assertEquals(0x00FF00, (decoration.style as Decoration.Style.Highlight).tint)
    }
}
