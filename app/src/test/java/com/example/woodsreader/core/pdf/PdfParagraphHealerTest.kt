package com.example.woodsreader.core.pdf

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.readium.r2.shared.util.Url

class PdfParagraphHealerTest {

    @Test
    fun `healParagraphs unwraps broken lines without ending punctuation`() {
        val rawLines = listOf(
            "The ancient wizard stepped through the portal,",
            "wondering what kind of magical catastrophe awaited",
            "him on the other side."
        )

        val healed = PdfParagraphHealer.healParagraphs(rawLines)
        assertEquals(1, healed.size)
        assertEquals(
            "The ancient wizard stepped through the portal, wondering what kind of magical catastrophe awaited him on the other side.",
            healed[0]
        )
    }

    @Test
    fun `healParagraphs preserves intentional paragraph breaks ending with periods`() {
        val rawLines = listOf(
            "The first chapter began with darkness.",
            "A second sentence came soon after.",
            "Then the second paragraph started on a new line."
        )

        val healed = PdfParagraphHealer.healParagraphs(rawLines)
        assertEquals(3, healed.size)
        assertEquals("The first chapter began with darkness.", healed[0])
        assertEquals("A second sentence came soon after.", healed[1])
        assertEquals("Then the second paragraph started on a new line.", healed[2])
    }

    @Test
    fun `healParagraphs joins hyphenated words at line wrap without space`() {
        val rawLines = listOf(
            "The necromancer performed an extraor-",
            "dinary ritual under the blood moon."
        )

        val healed = PdfParagraphHealer.healParagraphs(rawLines)
        assertEquals(1, healed.size)
        assertEquals("The necromancer performed an extraordinary ritual under the blood moon.", healed[0])
    }

    @Test
    fun `healParagraphs filters out isolated page numbers and headers`() {
        val rawLines = listOf(
            "Diary of a Dead Wizard", // repeated header
            "12",                    // page number
            "He cast the spell with trembling hands,",
            "expecting thunder and lightning.",
            "- 12 -",                // page number footer
            "Page 12"                // page number
        )

        val healed = PdfParagraphHealer.healParagraphs(rawLines)
        assertEquals(2, healed.size)
        assertEquals("Diary of a Dead Wizard", healed[0])
        assertEquals("He cast the spell with trembling hands, expecting thunder and lightning.", healed[1])
    }

    @Test
    fun `isChapterHeading detects novel chapter patterns in English and Spanish`() {
        assertTrue(PdfParagraphHealer.isChapterHeading("Chapter 1: The Beginning"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Chapter 350 - Finale"))
        assertTrue(PdfParagraphHealer.isChapterHeading("CHAPTER 001"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Capítulo 12: El Despertar"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Cap. 45"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Episode 9"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Prologue"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Prólogo"))
        assertTrue(PdfParagraphHealer.isChapterHeading("Epilogue"))
        assertTrue(PdfParagraphHealer.isChapterHeading("0001 - Reincarnation"))
        assertTrue(PdfParagraphHealer.isChapterHeading("[Chapter 42] The Secret"))

        assertFalse(PdfParagraphHealer.isChapterHeading("This is just a normal sentence mentioning chapter 5 in passing."))
        assertFalse(PdfParagraphHealer.isChapterHeading("The wizard walked down the hallway."))
        assertFalse(PdfParagraphHealer.isChapterHeading(""))
    }

    @Test
    fun `healParagraphs separates dialogue lines starting with dashes or quotes`() {
        val rawLines = listOf(
            "He looked at the boy and asked,",
            "— ¿Estás listo para aprender magia?",
            "— Siempre he estado listo —respondió él con firmeza."
        )

        val healed = PdfParagraphHealer.healParagraphs(rawLines)
        assertEquals(3, healed.size)
        assertEquals("He looked at the boy and asked,", healed[0])
        assertEquals("— ¿Estás listo para aprender magia?", healed[1])
        assertEquals("— Siempre he estado listo —respondió él con firmeza.", healed[2])
    }

    @Test
    fun `chaptersToTocLinks maps chapters to Readium Links with page targets`() {
        val chapters = listOf(
            PdfChapter(chapterIndex = 1, title = "Chapter 1: Rebirth", pageNumber = 1),
            PdfChapter(chapterIndex = 2, title = "Chapter 2: The Academy", pageNumber = 15)
        )

        val capturedHrefs = mutableListOf<String>()
        val links = PdfParagraphHealer.chaptersToTocLinks(chapters) { href ->
            capturedHrefs.add(href)
            mockk<Url>(relaxed = true)
        }
        assertEquals(2, links.size)
        assertEquals(listOf("/page_1#page=1", "/page_15#page=15"), capturedHrefs)
        assertEquals("Chapter 1: Rebirth", links[0].title)
        assertEquals("Chapter 2: The Academy", links[1].title)
    }
}
