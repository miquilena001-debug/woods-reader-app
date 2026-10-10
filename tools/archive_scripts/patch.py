
import sys

with open("app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "init {" in line:
        insert_idx = i + 1
        break

sync_code = """
        viewModelScope.launch {
            combine(
                ttsManager.ttsState,
                currentSpeed,
                userPreferences,
                globalProgress,
                chapterInfo
            ) { state, speed, prefs, globalProg, chapter ->
                ReaderViewState(
                    isTtsPlaying = state == com.example.woodsreader.core.tts.TtsState.PLAYING,
                    currentSpeed = speed,
                    ttsHighlightStyle = prefs.ttsHighlightStyle,
                    readingMode = com.example.woodsreader.data.model.ReadingMode.MANUAL, // placeholder
                    fontSize = prefs.fontSize,
                    lineSpacing = prefs.lineSpacing,
                    globalProgress = globalProg,
                    chapterProgress = chapter.progress
                )
            }.collect { newState ->
                _viewState.value = newState
            }
        }
"""

lines.insert(insert_idx, sync_code)

# Add properties inside the class
for i, line in enumerate(lines):
    if "private val _uiState =" in line:
        prop_idx = i
        break

prop_code = """
    private val _viewState = MutableStateFlow(ReaderViewState())
    val viewState: StateFlow<ReaderViewState> = _viewState.asStateFlow()
"""
lines.insert(prop_idx, prop_code)

with open("app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt", "w", encoding="utf-8") as f:
    f.writelines(lines)

