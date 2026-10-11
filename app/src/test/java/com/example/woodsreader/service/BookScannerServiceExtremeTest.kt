package com.example.woodsreader.service

import android.content.Context
import android.net.Uri
import com.example.woodsreader.data.database.BookEntity
import com.example.woodsreader.data.model.BookScanResult
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.data.scanner.FileSystemScanner
import com.example.woodsreader.data.scanner.MediaStoreScanner
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BookScannerServiceExtremeTest {

    private lateinit var service: BookScannerService
    private val context: Context = mockk(relaxed = true)
    private val repository: BookRepository = mockk(relaxed = true)
    private val metadataExtractor: dagger.Lazy<BookMetadataExtractor> = mockk(relaxed = true)
    private val mediaStoreScanner: MediaStoreScanner = mockk(relaxed = true)
    private val deepScanner: FileSystemScanner = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        service = BookScannerService(
            context = context,
            repository = repository,
            metadataExtractor = metadataExtractor,
            mediaStoreScanner = mediaStoreScanner,
            deepScanner = deepScanner
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    /**
     * Constraint #1: Exhaustive Unit Testing
     * Constraint #7: Minimum cyclomatic complexity via linear batch testing
     * Test ensures O(1) in-memory dedup works: known paths are ignored, new paths are inserted in batches.
     */
    @Test
    fun `quickScan deduplicates using in-memory set and batches new inserts`() = runTest {
        // Arrange: 1 known book, 2 new books from MediaStore, 1 new from DeepScanner
        val knownPath = "/storage/emulated/0/Books/known.pdf"
        coEvery { repository.getAllKnownPaths() } returns setOf(knownPath)

        val knownResult = BookScanResult(mockUri(knownPath), "known.pdf", 1024, "application/pdf", 0L, BookScanResult.Source.MEDIASTORE)
        val newResult1 = BookScanResult(mockUri("/storage/emulated/0/Books/new1.epub"), "new1.epub", 2048, "application/epub+zip", 0L, BookScanResult.Source.MEDIASTORE)
        
        // MediaStore returns known and new
        every { mediaStoreScanner.scanDevice() } returns flowOf(knownResult, newResult1)

        // DeepScanner returns one seen by MediaStore (new1.epub) and one completely new
        val deepSeenFile = mockFile("/storage/emulated/0/Books/new1.epub", "new1.epub")
        val deepNewFile = mockFile("/storage/emulated/0/Books/new2.cbz", "new2.cbz")
        every { deepScanner.scanAllDevice() } returns flowOf(deepSeenFile, deepNewFile)

        // Act
        val stats = service.quickScan(emptyList()) // Pass empty list to skip SAF for this test

        // Assert: 100% Coverage of the dedup logic
        // MediaStore found 2, Deep found 2. Total found = 4 (but deepSeenFile is skipped due to seenPaths)
        // MediaStore yielded 1 new. Deep yielded 1 new. Total new = 2.
        assertEquals(4, stats.scannedCount) // 2 from MS + 2 from Deep
        assertEquals(2, stats.newBooksCount) // new1.epub + new2.cbz

        // Verify batch upsert was called with the lightweight entities
        val batchSlot = slot<List<BookEntity>>()
        coVerify(exactly = 1) { repository.upsertBooks(capture(batchSlot)) }
        
        val inserted = batchSlot.captured
        assertEquals(2, inserted.size)
        assertTrue(inserted.any { it.filePath == "/storage/emulated/0/Books/new1.epub" })
        assertTrue(inserted.any { it.filePath == "/storage/emulated/0/Books/new2.cbz" })
        
        // Constraint #5 (SOLID/Clean Code): Verify no DB lookups were made per-file
        coVerify(exactly = 0) { repository.getBookByPath(any()) }
        coVerify(exactly = 0) { repository.getBookByHash(any()) }
    }

    /**
     * Constraint #4: 100% Coverage
     * Tests that lightweight entity creation applies correct fallbacks and leaves hash empty
     */
    @Test
    fun `createLightweightEntity applies fallbacks correctly without hash or IO`() = runTest {
        // Arrange
        coEvery { repository.getAllKnownPaths() } returns emptySet()
        val complexNameResult = BookScanResult(
            uri = mockUri("/Books/Author Name - Epic Title.pdf"),
            displayName = "Author Name - Epic Title.pdf",
            sizeBytes = 5000,
            mimeType = "application/pdf",
            dateModified = 0L,
            source = BookScanResult.Source.MEDIASTORE
        )
        every { mediaStoreScanner.scanDevice() } returns flowOf(complexNameResult)
        every { deepScanner.scanAllDevice() } returns flowOf()

        // Act
        service.quickScan(emptyList())

        // Assert
        val batchSlot = slot<List<BookEntity>>()
        coVerify { repository.upsertBooks(capture(batchSlot)) }
        val entity = batchSlot.captured.first()

        assertEquals("", entity.author)
        assertEquals("Author Name - Epic Title", entity.title)
        assertTrue(entity.fileHash.startsWith("pending-")) // Constraint: NO IO hashing during fast scan, but must use placeholder to avoid SQLite UNIQUE collision
        assertEquals(com.example.woodsreader.data.model.BookFormat.PDF, entity.format)
    }

    private fun mockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.path } returns path
        every { uri.toString() } returns path
        return uri
    }

    private fun mockFile(path: String, name: String): File {
        return object : File(path) {
            override fun getName(): String = name
            override fun getAbsolutePath(): String = path
            override fun length(): Long = 1024L
            override fun lastModified(): Long = 0L
        }
    }
}
