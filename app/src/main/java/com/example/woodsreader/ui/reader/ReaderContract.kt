package com.example.woodsreader.ui.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.woodsreader.data.database.BookEntity
import org.readium.r2.shared.publication.Publication

// ── Shared Enums ─────────────────────────────────────────────────────
enum class ReaderTab(val icon: ImageVector, val label: String) {
    TEXT(Icons.Default.MenuBook,    "Texto"),
    AUDIO(Icons.Default.GraphicEq,  "Audio"),
    NOTES(Icons.Default.Edit,       "Notas"),
}

// ── UI States ────────────────────────────────────────────────────────
sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class InitialLoading(val book: BookEntity) : ReaderUiState()
    data class Success(
        val book: BookEntity,
        val publication: Publication,
        val initialLocatorJson: String?
    ) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

// ── Narration Synchronization ─────────────────────────────────────────
data class NarrationHighlight(
    val blockId: String? = null,
    val wordRange: IntRange? = null
)
