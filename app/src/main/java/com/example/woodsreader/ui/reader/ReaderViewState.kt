package com.example.woodsreader.ui.reader

data class ReaderViewState(
    val isTtsPlaying: Boolean = false,
    val currentSpeed: Float = 1.0f,
    val ttsHighlightStyle: com.example.woodsreader.model.TtsHighlightStyle = com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_YELLOW,
    val sleepTimerMinutes: Int? = null,
    val readingMode: com.example.woodsreader.data.model.ReadingMode = com.example.woodsreader.data.model.ReadingMode.MANUAL,
    val fontSize: Float = 16f,
    val lineSpacing: Float = 1.5f,
    val isBookmarked: Boolean = false,
    val globalProgress: Float = 0f,
    val chapterProgress: Float = 0f,
    val activeNarrationBlockId: String? = null,
    val activeNarrationWordRange: IntRange? = null,
    val isPdfBookViewActive: Boolean = false,
    val pdfBookViewBlockIndex: Int = 0
)
