package com.example.woodsreader.core.translation.calibre

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationEngineSelectionTest {
    @Test
    fun `missing API key disables the keyed translation engine`() {
        assertEquals(
            listOf(TranslationEngine.LEGACY),
            translationEngineOrder("")
        )
        assertEquals(
            listOf(TranslationEngine.LEGACY),
            translationEngineOrder("  ")
        )
    }

    @Test
    fun `configured API key tries keyed engine before legacy fallback`() {
        assertEquals(
            listOf(TranslationEngine.NEW_TEXT, TranslationEngine.LEGACY),
            translationEngineOrder("local-test-key")
        )
    }
}
