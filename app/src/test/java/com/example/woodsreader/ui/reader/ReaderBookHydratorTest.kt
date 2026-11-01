package com.example.woodsreader.ui.reader

import android.net.Uri
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.service.CoverExtractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Publication
import java.io.File

class ReaderBookHydratorTest {

    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val coverExtractor = mockk<CoverExtractor>(relaxed = true)
    private val uri = mockk<Uri>(relaxed = true)
    private val publication = mockk<Publication>(relaxed = true)

    private var lastLoggedMessage: String? = null
    private var lastLoggedThrowable: Throwable? = null

    private val hydrator = ReaderBookHydrator(
        bookRepository = bookRepository,
        userPreferencesRepository = userPreferencesRepository,
        coverExtractor = coverExtractor,
        logWarn = { msg, throwable ->
            lastLoggedMessage = msg
            lastLoggedThrowable = throwable
        }
    )

    private fun createBook(
        id: String = "book_1",
        title: String = "Hydration Test Book",
        coverCachePath: String? = null
    ): BookEntity = BookEntity(
        id = id,
        filePath = "/path/book.epub",
        title = title,
        author = "Author Name",
        fileHash = "hash123",
        format = BookFormat.EPUB,
        coverCachePath = coverCachePath
    )

    @Test
    fun testHydrate_callsMarkOpenedThenUpdateLastReadBookId() = runTest {
        val book = createBook(coverCachePath = "/existing/cover.jpg")
        val tempCover = File.createTempFile("cover", ".jpg")
        try {
            val bookWithRealCover = book.copy(coverCachePath = tempCover.absolutePath)
            hydrator.hydrate(bookWithRealCover, uri, publication)

            coVerifyOrder {
                bookRepository.markOpened("book_1")
                userPreferencesRepository.updateLastReadBookId("book_1")
            }
        } finally {
            tempCover.delete()
        }
    }

    @Test
    fun testHydrate_missingCover_extractsCoverAndUpdatesCoverPath() = runTest {
        val book = createBook(coverCachePath = null)
        coEvery {
            coverExtractor.extractCover(
                source = uri,
                format = book.format,
                fileHash = book.fileHash,
                title = book.title,
                author = book.author,
                publication = publication
            )
        } returns "/cache/covers/new_cover.jpg"

        hydrator.hydrate(book, uri, publication)

        coVerify {
            coverExtractor.extractCover(
                source = uri,
                format = book.format,
                fileHash = book.fileHash,
                title = book.title,
                author = book.author,
                publication = publication
            )
            bookRepository.updateCoverPath("book_1", "/cache/covers/new_cover.jpg")
        }
    }

    @Test
    fun testHydrate_presentCover_doesNotExtractCover() = runTest {
        val tempCover = File.createTempFile("cover", ".jpg")
        try {
            val book = createBook(coverCachePath = tempCover.absolutePath)
            hydrator.hydrate(book, uri, publication)

            coVerify(exactly = 0) {
                coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
                bookRepository.updateCoverPath(any(), any())
            }
        } finally {
            tempCover.delete()
        }
    }

    @Test
    fun testHydrate_extractReturnsNull_doesNotUpdateCoverPath() = runTest {
        val book = createBook(coverCachePath = null)
        coEvery {
            coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        } returns null

        hydrator.hydrate(book, uri, publication)

        coVerify {
            coverExtractor.extractCover(
                source = uri,
                format = book.format,
                fileHash = book.fileHash,
                title = book.title,
                author = book.author,
                publication = publication
            )
        }
        coVerify(exactly = 0) {
            bookRepository.updateCoverPath(any(), any())
        }
    }

    @Test
    fun testHydrate_markOpenedThrows_logsWarningAndDoesNotCrashOrExtractCover() = runTest {
        val book = createBook(coverCachePath = null)
        val error = RuntimeException("DB connection failed")
        coEvery { bookRepository.markOpened("book_1") } throws error

        hydrator.hydrate(book, uri, publication)

        assertNotNull(lastLoggedMessage)
        assertTrue(lastLoggedMessage!!.contains(book.title))
        assertEquals(error, lastLoggedThrowable)

        coVerify(exactly = 0) {
            coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
            bookRepository.updateCoverPath(any(), any())
        }
    }

    @Test
    fun testHydrate_extractCoverThrows_logsWarningAndDoesNotCrashOrUpdateCoverPath() = runTest {
        val book = createBook(coverCachePath = null)
        val error = RuntimeException("Corrupted publication archive")
        coEvery {
            coverExtractor.extractCover(any(), any(), any(), any(), any(), any())
        } throws error

        hydrator.hydrate(book, uri, publication)

        assertNotNull(lastLoggedMessage)
        assertTrue(lastLoggedMessage!!.contains(book.title))
        assertEquals(error, lastLoggedThrowable)

        coVerify(exactly = 0) {
            bookRepository.updateCoverPath(any(), any())
        }
    }
}
