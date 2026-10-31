package com.example.woodsreader.ui.reader

import com.example.woodsreader.core.managers.ReadingSessionManager
import com.example.woodsreader.data.model.ReadingMode
import kotlinx.coroutines.CoroutineScope
import org.readium.r2.shared.publication.Locator

class ReaderReadingSessionCoordinator(
    private val sessionManager: ReadingSessionManager,
    private val bookId: String,
    private val scope: CoroutineScope,
    private val visibleLocator: () -> Locator?,
    private val ttsLocator: () -> Locator?,
    private val isTtsPlaying: () -> Boolean
) {
    private fun sessionLocator(): Locator? =
        if (sessionManager.currentSessionMode == ReadingMode.TTS || isTtsPlaying()) {
            ttsLocator() ?: visibleLocator()
        } else {
            visibleLocator()
        }

    /** Returns the locator's totalProgression (caller stores it in lastRecordedProgression) or null. */
    fun start(mode: ReadingMode, initialLocator: Locator?): Double? {
        val locator = visibleLocator() ?: initialLocator
        sessionManager.startNewSession(scope, bookId, mode, locator)
        return locator?.locations?.totalProgression
    }

    fun stop() {
        sessionManager.stopActiveSession(bookId = bookId, currentLocator = sessionLocator())
    }

    fun switchMode(newMode: ReadingMode) {
        sessionManager.switchSessionMode(scope, bookId, newMode, sessionLocator())
    }

    fun recordActivity() {
        sessionManager.recordActivity(scope, bookId, visibleLocator())
    }
}
