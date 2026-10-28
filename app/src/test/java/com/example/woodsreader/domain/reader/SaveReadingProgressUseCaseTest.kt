package com.example.woodsreader.domain.reader

import android.content.Context
import android.content.SharedPreferences
import com.example.woodsreader.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalCoroutinesApi::class)
class SaveReadingProgressUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)

    private lateinit var useCase: SaveReadingProgressUseCase

    @Before
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.commit() } returns true

        useCase = SaveReadingProgressUseCase(context, bookRepository)
    }

    private fun createMockLocator(
        hrefString: String = "chapter_1.xhtml",
        position: Int = 3,
        progression: Double = 0.2,
        totalProgression: Double = 0.15,
        jsonString: String = """{"href":"$hrefString"}"""
    ): Locator {
        val locator = mockk<Locator>(relaxed = true)
        val json = mockk<JSONObject>()
        every { json.toString() } returns jsonString
        every { locator.toJSON() } returns json
        val url = mockk<org.readium.r2.shared.util.Url>(relaxed = true)
        every { url.toString() } returns hrefString
        every { locator.href } returns url
        val locations = mockk<Locator.Locations>(relaxed = true)
        every { locations.position } returns position
        every { locations.progression } returns progression
        every { locations.totalProgression } returns totalProgression
        every { locator.locations } returns locations
        return locator
    }

    @Test
    fun testSaveProgressDebounced_dropsSubsequentCallWithinDebounceWindow() = runTest(testDispatcher) {
        val firstSaved = useCase.saveProgressDebounced(
            scope = testScope,
            bookId = "book_123",
            progress = 0.25f,
            locator = null,
            debounceMs = 800L,
            force = false
        )
        assertTrue(firstSaved)

        // Llamada inmediata posterior dentro de la ventana de debounce (800ms)
        val secondSaved = useCase.saveProgressDebounced(
            scope = testScope,
            bookId = "book_123",
            progress = 0.30f,
            locator = null,
            debounceMs = 800L,
            force = false
        )
        assertFalse(secondSaved)

        // Se verifica que a Room solo se despachó la primera
        coVerify(exactly = 1) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = "book_123",
                progress = 0.25f,
                locatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        }
    }

    @Test
    fun testSaveProgressDebounced_forceBypassesDebounceWindow() = runTest(testDispatcher) {
        val firstSaved = useCase.saveProgressDebounced(
            scope = testScope,
            bookId = "book_123",
            progress = 0.25f,
            locator = null,
            debounceMs = 800L,
            force = false
        )
        assertTrue(firstSaved)

        // Inmediata con force = true
        val secondSaved = useCase.saveProgressDebounced(
            scope = testScope,
            bookId = "book_123",
            progress = 0.30f,
            locator = null,
            debounceMs = 800L,
            force = true
        )
        assertTrue(secondSaved)

        coVerify(exactly = 1) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = "book_123",
                progress = 0.25f,
                locatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        }
        coVerify(exactly = 1) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = "book_123",
                progress = 0.30f,
                locatorJson = any(),
                fallbackHref = any(),
                fallbackPosition = any()
            )
        }
    }

    @Test
    fun testSaveProgressDebounced_emptyBookIdReturnsFalse() = runTest(testDispatcher) {
        val emptySaved = useCase.saveProgressDebounced(
            scope = testScope,
            bookId = "",
            progress = 0.1f,
            locator = null
        )
        val blankSaved = useCase.saveProgressDebounced(
            scope = testScope,
            bookId = "   ",
            progress = 0.1f,
            locator = null
        )
        assertFalse(emptySaved)
        assertFalse(blankSaved)

        coVerify(exactly = 0) {
            bookRepository.updateReadingProgressWithFallback(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testSaveTtsProgressDebounced_dropsSubsequentCallWithinTtsDebounceWindow() = runTest(testDispatcher) {
        val locator = createMockLocator("chap2.xhtml", position = 4, totalProgression = 0.4)

        val firstSaved = useCase.saveTtsProgressDebounced(
            scope = testScope,
            bookId = "book_tts",
            locator = locator,
            debounceMs = 10_000L
        )
        assertTrue(firstSaved)

        val secondSaved = useCase.saveTtsProgressDebounced(
            scope = testScope,
            bookId = "book_tts",
            locator = locator,
            debounceMs = 10_000L
        )
        assertFalse(secondSaved)

        coVerify(exactly = 1) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = "book_tts",
                progress = 0.4f,
                locatorJson = any(),
                fallbackHref = "chap2.xhtml",
                fallbackPosition = 4
            )
        }
    }

    @Test
    fun testSaveTtsProgressDebounced_emptyBookIdReturnsFalse() = runTest(testDispatcher) {
        val locator = createMockLocator()
        val emptySaved = useCase.saveTtsProgressDebounced(
            scope = testScope,
            bookId = "",
            locator = locator
        )
        assertFalse(emptySaved)

        coVerify(exactly = 0) {
            bookRepository.updateReadingProgressWithFallback(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testSaveCheckpointToPreferences_writesAndCommitsAllFields() {
        val success = useCase.saveCheckpointToPreferences(
            bookId = "book_pref",
            chapterIndex = 5,
            locatorJson = """{"href":"c5.xhtml"}""",
            progress = 0.65f
        )
        assertTrue(success)

        verify { context.getSharedPreferences("woods_last_reading_checkpoint", Context.MODE_PRIVATE) }
        verify { editor.putString("last_book_id", "book_pref") }
        verify { editor.putInt("last_chapter_index_book_pref", 5) }
        verify { editor.putString("last_locator_json_book_pref", """{"href":"c5.xhtml"}""") }
        verify { editor.putFloat("last_progress_book_pref", 0.65f) }
        verify { editor.putLong("last_saved_timestamp_book_pref", any()) }
        verify { editor.commit() }
    }

    @Test
    fun testSaveCheckpointToPreferences_emptyBookIdReturnsFalse() {
        val emptySaved = useCase.saveCheckpointToPreferences("", 0, "", 0f)
        val blankSaved = useCase.saveCheckpointToPreferences("   ", 0, "", 0f)
        assertFalse(emptySaved)
        assertFalse(blankSaved)

        verify(exactly = 0) { context.getSharedPreferences(any(), any()) }
    }

    @Test
    fun testFlushImmediately_savesPreferencesCheckpointAndDispatchesRoomUpdate() = runTest(testDispatcher) {
        val locator = createMockLocator("ch_pause.xhtml", position = 7, totalProgression = 0.5)

        useCase.flushImmediately(
            scope = testScope,
            bookId = "book_flush",
            chapterIndex = 6,
            progress = 0.5f,
            currentLocator = locator
        )

        // 1. SharedPreferences checkpoint sincrónico commit()
        verify { editor.putString("last_book_id", "book_flush") }
        verify { editor.putInt("last_chapter_index_book_flush", 6) }
        verify { editor.commit() }

        // 2. Room DB persistencia
        coVerify(exactly = 1) {
            bookRepository.updateReadingProgressWithFallback(
                bookId = "book_flush",
                progress = 0.5f,
                locatorJson = any(),
                fallbackHref = "ch_pause.xhtml",
                fallbackPosition = 7
            )
        }
    }

    @Test
    fun testFlushImmediately_emptyBookIdNoOps() = runTest(testDispatcher) {
        useCase.flushImmediately(
            scope = testScope,
            bookId = "",
            chapterIndex = 1,
            progress = 0.1f,
            currentLocator = null
        )

        verify(exactly = 0) { context.getSharedPreferences(any(), any()) }
        coVerify(exactly = 0) {
            bookRepository.updateReadingProgressWithFallback(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testSerializeLocator_nullReturnsEmptyString() {
        assertEquals("", useCase.serializeLocator(null))
    }

    @Test
    fun testSerializeLocator_validLocatorReturnsJsonString() {
        val locator = createMockLocator(jsonString = """{"test":123}""")
        assertEquals("""{"test":123}""", useCase.serializeLocator(locator))
    }

    @Test
    fun testGetAndSetLastSavedLocatorJson() {
        assertNull(useCase.getLastSavedLocatorJson())
        useCase.setLastSavedLocatorJson("""{"href":"stored.xhtml"}""")
        assertEquals("""{"href":"stored.xhtml"}""", useCase.getLastSavedLocatorJson())
    }
}
