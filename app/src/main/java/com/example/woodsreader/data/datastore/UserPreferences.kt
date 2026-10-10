package com.example.woodsreader.data.datastore

import com.example.woodsreader.model.PageTurnStyle
import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.model.TtsUiStyle
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * Source of truth for all user-configurable settings.
 * Persisted via DataStore with kotlinx.serialization.
 *
 * Adding a new setting:
 * 1. Add a field with a default value here.
 * 2. Add an `update*` function in [UserPreferencesRepository].
 * 3. If needed, connect it to a ViewModel or composable.
 */
@Serializable
@Stable
data class UserPreferences(
    // ── Reader appearance ────────────────────────────────────────────────────
    val theme: ReaderTheme = ReaderTheme.HIDDEN_FOREST,
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.2f,
    val horizontalMargin: Float = 12f,
    val textAlign: String = "justify",
    val fontFamily: String = "serif",
    val hyphenation: Boolean = true,

    // ── Navigation ────────────────────────────────────────────────────────────
    val volumeKeysNavigation: Boolean = true,
    val scrollMode: ScrollMode = ScrollMode.PAGINATED,
    val pageTurnAnimation: Boolean = true,
    val pageTurnStyle: PageTurnStyle = PageTurnStyle.BOOK_FLIP,
    val invertedTapZones: Boolean = false,

    // ── Onboarding ────────────────────────────────────────────────────────────
    val hasCompletedOnboarding: Boolean = false,
    val hasGrantedPermissions: Boolean = false,
    val lastReadBookId: String? = null,

    // ── TTS ───────────────────────────────────────────────────────────────────
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLanguage: String? = null,
    val ttsVoiceName: String? = null,
    val ttsAutoStart: Boolean = false,       // Auto-read on book open
    val ttsAutoScroll: Boolean = true,       // Auto-scroll screen when reading TTS
    val useSherpaTts: Boolean = false,       // Usar Sherpa ONNX (voz de IA local) en lugar del nativo
    val ttsUiStyle: TtsUiStyle = TtsUiStyle.BAR, // Estilo de controles TTS (BAR, FLOATING, PANEL)
    val ttsHighlightStyle: com.example.woodsreader.model.TtsHighlightStyle = com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_YELLOW,


    // ── Notifications ─────────────────────────────────────────────────────────
    val notificationsEnabled: Boolean = true, // TTS controls on lock screen

    // ── Analytics ─────────────────────────────────────────────────────────────
    val analyticsEnabled: Boolean = true,     // Track reading time & pages

    // ── Library scanner ───────────────────────────────────────────────────────
    val customScanPaths: List<String> = emptyList(),
    val lastScanTime: Long = 0L,
    val filesScannedCount: Int = 0,
    val newBooksFoundCount: Int = 0,
    val recentSearches: List<String> = emptyList(),
)
