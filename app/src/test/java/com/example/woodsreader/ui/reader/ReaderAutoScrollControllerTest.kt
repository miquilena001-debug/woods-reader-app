package com.example.woodsreader.ui.reader

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAutoScrollControllerTest {

    private fun createController(initialWpm: Int = ReaderAutoScrollController.DEFAULT_WPM): Pair<ReaderAutoScrollController, SharedPreferences.Editor> {
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.getInt(ReaderAutoScrollController.KEY_WPM, ReaderAutoScrollController.DEFAULT_WPM) } returns initialWpm
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        val controller = ReaderAutoScrollController { prefs }
        return Pair(controller, editor)
    }

    @Test
    fun testInitialWpmClamping() {
        // prefs returns 9999 -> 450
        val (c1, _) = createController(9999)
        assertEquals(450, c1.wpm.value)

        // returns 0 -> 100
        val (c2, _) = createController(0)
        assertEquals(100, c2.wpm.value)

        // returns 300 -> 300
        val (c3, _) = createController(300)
        assertEquals(300, c3.wpm.value)
    }

    @Test
    fun testToggleBehavior() {
        val (controller, _) = createController()

        // Initially inactive and not manually paused
        assertFalse(controller.isActive.value)
        assertFalse(controller.isManuallyPaused.value)

        // First toggle: becomes active (returns true)
        val result1 = controller.toggle()
        assertTrue(result1)
        assertTrue(controller.isActive.value)
        assertFalse(controller.isManuallyPaused.value)

        // Set manually paused while active
        controller.setManuallyPaused(true)
        assertTrue(controller.isManuallyPaused.value)

        // Second toggle: becomes inactive (returns false); manuallyPaused is NOT touched
        val result2 = controller.toggle()
        assertFalse(result2)
        assertFalse(controller.isActive.value)
        assertTrue(controller.isManuallyPaused.value)

        // Third toggle: becomes active again (returns true); manuallyPaused is cleared
        val result3 = controller.toggle()
        assertTrue(result3)
        assertTrue(controller.isActive.value)
        assertFalse(controller.isManuallyPaused.value)
    }

    @Test
    fun testResetAndDeactivate() {
        val (controller, _) = createController()

        // Set both to true
        controller.toggle()
        controller.setManuallyPaused(true)
        assertTrue(controller.isActive.value)
        assertTrue(controller.isManuallyPaused.value)

        // deactivate leaves manuallyPaused unchanged
        controller.deactivate()
        assertFalse(controller.isActive.value)
        assertTrue(controller.isManuallyPaused.value)

        // Reactivate
        controller.toggle()
        controller.setManuallyPaused(true)
        assertTrue(controller.isActive.value)
        assertTrue(controller.isManuallyPaused.value)

        // reset leaves both false
        controller.reset()
        assertFalse(controller.isActive.value)
        assertFalse(controller.isManuallyPaused.value)
    }

    @Test
    fun testSetWpmClampingAndPersistence() {
        val (controller, editor) = createController(220)

        // setWpm(10) -> clamped to 100, persisted
        controller.setWpm(10)
        assertEquals(100, controller.wpm.value)
        verify { editor.putInt("auto_scroll_wpm", 100) }

        // setWpm(9999) -> clamped to 450, persisted
        controller.setWpm(9999)
        assertEquals(450, controller.wpm.value)
        verify { editor.putInt("auto_scroll_wpm", 450) }
    }

    @Test
    fun testTogglePauseAndSetManuallyPaused() {
        val (controller, _) = createController()

        assertFalse(controller.isManuallyPaused.value)

        // togglePause flips to true
        controller.togglePause()
        assertTrue(controller.isManuallyPaused.value)

        // togglePause flips back to false
        controller.togglePause()
        assertFalse(controller.isManuallyPaused.value)

        // setManuallyPaused(true) sets to true
        controller.setManuallyPaused(true)
        assertTrue(controller.isManuallyPaused.value)

        // setManuallyPaused(false) sets to false
        controller.setManuallyPaused(false)
        assertFalse(controller.isManuallyPaused.value)
    }
}
