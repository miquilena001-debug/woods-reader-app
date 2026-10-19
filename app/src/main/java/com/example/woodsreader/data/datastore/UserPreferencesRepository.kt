package com.example.woodsreader.data.datastore

import com.example.woodsreader.model.PageTurnStyle
import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.model.TtsUiStyle
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<UserPreferences>
) {
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.distinctUntilChanged()

    // ── Core update helper ────────────────────────────────────────────────────

    /** Generic update — avoids boilerplate in every individual update function. */
    private suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData(transform)
    }

    suspend fun restorePreferences(newPrefs: UserPreferences) = update { newPrefs }

    // ── Reader appearance ─────────────────────────────────────────────────────

    suspend fun updateTheme(value: ReaderTheme)            = update { it.copy(theme = value) }
    suspend fun updateFontSize(value: Float)               = update { it.copy(fontSizeSp = value) }
    suspend fun updateLineSpacing(value: Float)            = update { it.copy(lineSpacingMultiplier = value) }
    suspend fun updateMargin(value: Float)                 = update { it.copy(horizontalMargin = value) }
    suspend fun updateTextAlign(value: String)             = update { it.copy(textAlign = value) }
    suspend fun updateFontFamily(value: String)            = update { it.copy(fontFamily = value) }
    suspend fun updateHyphenation(value: Boolean)          = update { it.copy(hyphenation = value) }

    // ── Navigation ────────────────────────────────────────────────────────────

    suspend fun updateVolumeKeysNavigation(value: Boolean) = update { it.copy(volumeKeysNavigation = value) }
    suspend fun updateScrollMode(value: ScrollMode)        = update { it.copy(scrollMode = value) }
    suspend fun updatePageTurnAnimation(value: Boolean)    = update { it.copy(pageTurnAnimation = value) }
    suspend fun updatePageTurnStyle(value: PageTurnStyle)  = update { it.copy(pageTurnStyle = value) }
    suspend fun updateInvertedTapZones(value: Boolean)     = update { it.copy(invertedTapZones = value) }

    // ── Onboarding ────────────────────────────────────────────────────────────

    suspend fun markOnboardingComplete()           = update { it.copy(hasCompletedOnboarding = true) }
    suspend fun resetOnboarding()                  = update { it.copy(hasCompletedOnboarding = false) }
    suspend fun markPermissionsGranted()           = update { it.copy(hasGrantedPermissions = true) }
    suspend fun updateLastReadBookId(id: String)   = update { it.copy(lastReadBookId = id) }

    // ── TTS ───────────────────────────────────────────────────────────────────

    suspend fun updateTtsSpeed(value: Float)       = update { it.copy(ttsSpeed = value) }
    suspend fun updateTtsPitch(value: Float)       = update { it.copy(ttsPitch = value) }
    suspend fun updateTtsLanguage(value: String?)  = update { it.copy(ttsLanguage = value) }
    suspend fun updateTtsVoiceName(value: String?) = update { it.copy(ttsVoiceName = value) }
    suspend fun updateTtsAutoStart(value: Boolean) = update { it.copy(ttsAutoStart = value) }

    suspend fun updateTtsAutoScroll(value: Boolean) = update { it.copy(ttsAutoScroll = value) }
    suspend fun updateUseSherpaTts(value: Boolean) = update { it.copy(useSherpaTts = value) }
    suspend fun updateTtsUiStyle(value: TtsUiStyle) = update { it.copy(ttsUiStyle = value) }
    suspend fun updateTtsHighlightStyle(value: com.example.woodsreader.model.TtsHighlightStyle) = update { it.copy(ttsHighlightStyle = value) }


    // ── Notifications ─────────────────────────────────────────────────────────

    suspend fun updateNotificationsEnabled(value: Boolean) = update { it.copy(notificationsEnabled = value) }

    // ── Analytics ─────────────────────────────────────────────────────────────

    suspend fun updateAnalyticsEnabled(value: Boolean) = update { it.copy(analyticsEnabled = value) }

    // ── Library scanner ───────────────────────────────────────────────────────

    suspend fun addCustomScanPath(path: String) = update { prefs ->
        if (path !in prefs.customScanPaths) prefs.copy(customScanPaths = prefs.customScanPaths + path)
        else prefs
    }

    suspend fun saveScanStats(lastScanTime: Long, filesScanned: Int, newBooksFound: Int) =
        update {
            it.copy(
                lastScanTime = lastScanTime,
                filesScannedCount = filesScanned,
                newBooksFoundCount = newBooksFound
            )
        }

    suspend fun addRecentSearch(query: String) = update { prefs ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@update prefs
        val newList = (listOf(trimmed) + prefs.recentSearches.filter { it != trimmed }).take(10)
        prefs.copy(recentSearches = newList)
    }

    suspend fun removeRecentSearch(query: String) = update { prefs ->
        prefs.copy(recentSearches = prefs.recentSearches.filter { it != query })
    }
}
