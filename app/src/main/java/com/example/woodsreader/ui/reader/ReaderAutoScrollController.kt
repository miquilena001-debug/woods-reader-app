package com.example.woodsreader.ui.reader

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ReaderAutoScrollController(
    private val prefsProvider: () -> SharedPreferences
) {
    companion object {
        const val PREFS_NAME = "woods_teleprompter_prefs"
        const val KEY_WPM = "auto_scroll_wpm"
        const val DEFAULT_WPM = 220
        const val MIN_WPM = 100
        const val MAX_WPM = 450
    }

    private val prefs by lazy { prefsProvider() }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isManuallyPaused = MutableStateFlow(false)
    val isManuallyPaused: StateFlow<Boolean> = _isManuallyPaused.asStateFlow()

    private val _wpm = MutableStateFlow(prefs.getInt(KEY_WPM, DEFAULT_WPM).coerceIn(MIN_WPM, MAX_WPM))
    val wpm: StateFlow<Int> = _wpm.asStateFlow()

    /** Flips active. Returns the NEW value. When it becomes active, clears manual pause. */
    fun toggle(): Boolean {
        val willBeActive = !_isActive.value
        _isActive.value = willBeActive
        if (willBeActive) {
            _isManuallyPaused.value = false
        }
        return willBeActive
    }

    fun togglePause() { _isManuallyPaused.update { !it } }

    fun setManuallyPaused(paused: Boolean) { _isManuallyPaused.value = paused }

    /** active = false only (used by saveCurrentProgressBeforeExit). */
    fun deactivate() { _isActive.value = false }

    /** active = false THEN manuallyPaused = false (same order as the old code). */
    fun reset() {
        _isActive.value = false
        _isManuallyPaused.value = false
    }

    fun setWpm(wpm: Int) {
        val clamped = wpm.coerceIn(MIN_WPM, MAX_WPM)
        _wpm.value = clamped
        prefs.edit().putInt(KEY_WPM, clamped).apply()
    }
}
