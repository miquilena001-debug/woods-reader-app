import os

path = r'app\src\main\java\com\example\woodsreader\ui\reader\ReaderViewModel.kt'
with open(path, 'rb') as f:
    content = f.read().decode('utf-8', errors='replace')

new_init_logic = """
    init {
        readingStartTime = System.currentTimeMillis()

        // Inicializar el servicio TTS inmediatamente
        epubFormatManager.initialize(
            context = context,
            stateChanged = { mode, sentence ->
                switchSessionMode(mode)
                _currentTtsSentence.value = sentence
            },
            locatorChanged = { locator ->
                _currentTtsLocator.value = locator
            }
        )

        // Propagar flujos del EpubFormatManager al ViewModel
        viewModelScope.launch {
            epubFormatManager.currentTtsSentence.collectLatest { _currentTtsSentence.value = it }
        }
        viewModelScope.launch {
            epubFormatManager.currentTtsRange.collectLatest { _currentTtsRange.value = it }
        }
        viewModelScope.launch {
            epubFormatManager.currentTtsLocator.collectLatest { locator ->
                if (locator != null) _currentTtsLocator.value = locator
            }
        }

        viewModelScope.launch {
            pdfReflowManager.currentTtsPage.collect { pageNumber ->
                if (isPdfBook && !isReflowMode.value) {
                    val pub = publication ?: return@collect
                    val link = pub.readingOrder.firstOrNull() ?: return@collect
                    val locator = org.readium.r2.shared.publication.Locator(
                        href = link.href as org.readium.r2.shared.util.Url,
                        mediaType = link.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.PDF,
                        locations = org.readium.r2.shared.publication.Locator.Locations(position = pageNumber)
                    )
                    (navigator as? org.readium.r2.navigator.VisualNavigator)?.go(locator, animated = false)
                }
            }
        }

        viewModelScope.launch {
            userPreferences
                .map { Triple(it.ttsSpeed, it.ttsLanguage, it.ttsPitch) }
                .distinctUntilChanged()
                .collectLatest { (speed, lang, pitch) ->
                    epubFormatManager.syncPreferences(speed, lang, pitch)
                }
        }

        viewModelScope.launch {
            userPreferences.collect { prefs -> _currentSpeed.value = prefs.ttsSpeed }
        }
        loadBook()
    }
"""

lines = content.splitlines(keepends=True)
start_line_idx = -1
end_line_idx = -1
for i, line in enumerate(lines):
    if 'init {' in line and i > 150:
        start_line_idx = i
    if start_line_idx != -1 and 'loadBook()' in line:
        for j in range(i, len(lines)):
            if '}' in lines[j] and len(lines[j].strip()) == 1:
                end_line_idx = j
                break
        break

if start_line_idx != -1 and end_line_idx != -1:
    new_content = ''.join(lines[:start_line_idx]) + new_init_logic + ''.join(lines[end_line_idx+1:])
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print('SUCCESS: Patched init block')
else:
    print('ERROR: Could not find init block boundaries')
