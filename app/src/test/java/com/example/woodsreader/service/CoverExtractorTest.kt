package com.example.woodsreader.service

import android.content.Context
import com.example.woodsreader.data.model.BookFormat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CoverExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var extractor: CoverExtractor
    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0

        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")

        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir

        extractor = CoverExtractor(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `getExistingCoverPath returns null when cover does not exist`() {
        val result = extractor.getExistingCoverPath("non_existent_hash")
        assertNull(result)
    }

    @Test
    fun `getExistingCoverPath finds cover in permanent filesDir`() {
        val coversDir = File(filesDir, "covers").apply { mkdirs() }
        val coverFile = File(coversDir, "cover_abc123.jpg").apply {
            writeText("dummy-cover-bytes")
        }

        val result = extractor.getExistingCoverPath("abc123")
        assertNotNull(result)
        assertEquals(coverFile.absolutePath, result)
    }

    @Test
    fun `getExistingCoverPath migrates legacy cover from cacheDir to filesDir`() {
        val legacyDir = File(cacheDir, "covers").apply { mkdirs() }
        val legacyFile = File(legacyDir, "cover_legacy999.jpg").apply {
            writeText("legacy-cover-data")
        }

        val result = extractor.getExistingCoverPath("legacy999")
        assertNotNull(result)

        // Verify it was copied to permanent filesDir
        val permanentFile = File(File(filesDir, "covers"), "cover_legacy999.jpg")
        assertTrue(permanentFile.exists())
        assertEquals("legacy-cover-data", permanentFile.readText())
        assertEquals(permanentFile.absolutePath, result)
    }

    @Test
    fun `extractCover returns existing cover path without re-extracting`() = runTest {
        val coversDir = File(filesDir, "covers").apply { mkdirs() }
        val coverFile = File(coversDir, "cover_existing.jpg").apply {
            writeText("existing-content")
        }

        val result = extractor.extractCover(
            source = File("some_file.pdf"),
            format = BookFormat.PDF,
            fileHash = "existing",
            title = "Some Title",
            author = "Some Author"
        )

        assertEquals(coverFile.absolutePath, result)
    }
}
