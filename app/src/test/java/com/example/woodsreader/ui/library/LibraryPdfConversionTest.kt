package com.example.woodsreader.ui.library

import android.content.Context
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.core.translation.calibre.CalibreStyleEpubTranslator
import com.example.woodsreader.core.tts.ChapterAudioExporter
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.ReadingStatus
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.service.BookScannerService
import com.example.woodsreader.service.CoverExtractor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPdfConversionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val scannerService: BookScannerService = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val proLicenseManager: ProLicenseManager = mockk(relaxed = true)
    private val calibreStyleEpubTranslator: CalibreStyleEpubTranslator = mockk(relaxed = true)
    private val chapterAudioExporter: ChapterAudioExporter = mockk(relaxed = true)
    private val sherpaModelManager: SherpaModelManager = mockk(relaxed = true)
    private val coverExtractor: CoverExtractor = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(UserPreferences())

        viewModel = LibraryViewModel(
            bookRepository = bookRepository,
            scannerService = scannerService,
            preferencesRepository = preferencesRepository,
            proLicenseManager = proLicenseManager,
            calibreStyleEpubTranslator = calibreStyleEpubTranslator,
            chapterAudioExporter = chapterAudioExporter,
            sherpaModelManager = sherpaModelManager,
            coverExtractor = coverExtractor,
            context = context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `convertPdfToCleanEpub reports failure when file does not exist`() = runTest(testDispatcher) {
        var callbackResult: Result<File>? = null
        val dummyBook = BookEntity(
            id = "test_pdf_1",
            title = "Inexistent Novel",
            author = "Unknown",
            filePath = "/path/to/nonexistent/novel.pdf",
            coverCachePath = null,
            readingProgressPercent = 0f,
            format = BookFormat.PDF,
            readingStatus = ReadingStatus.NOT_STARTED,
            fileHash = "hash123",
            fileSizeBytes = 1000L
        )

        viewModel.convertPdfToCleanEpub(dummyBook, ioDispatcher = testDispatcher) { result ->
            callbackResult = result
        }

        advanceUntilIdle()

        assertNotNull(callbackResult)
        assertTrue(callbackResult!!.isFailure)
        assertTrue(callbackResult!!.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `cancelPdfConversion cancels job and clears progress`() = runTest(testDispatcher) {
        val dummyBook = BookEntity(
            id = "test_pdf_2",
            title = "Novel 2",
            author = "Unknown",
            filePath = "/path/to/dummy.pdf",
            coverCachePath = null,
            readingProgressPercent = 0f,
            format = BookFormat.PDF,
            readingStatus = ReadingStatus.NOT_STARTED,
            fileHash = "hash456",
            fileSizeBytes = 2000L
        )

        viewModel.convertPdfToCleanEpub(dummyBook)
        viewModel.cancelPdfConversion()

        assertNull(viewModel.pdfConversionProgress.value)
    }

    @Test
    fun `dismissPdfConversionDialog resets progress to null`() {
        viewModel.dismissPdfConversionDialog()
        assertNull(viewModel.pdfConversionProgress.value)
    }
}
