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

# Search for the start of init
for i, line in enumerate(lines):
    if 'init {' in line and i > 150:
        start_line_idx = i
        break

# Search for the start of loadBook to find the end of the messed up init area
load_book_idx = -1
for i, line in enumerate(lines):
    if 'private fun loadBook()' in line:
        load_book_idx = i
        break

if start_line_idx != -1 and load_book_idx != -1:
    # The end of the init block should be the line before 'private fun loadBook()' 
    # but there might be some closing braces and empty lines.
    # We want to keep everything up to start_line_idx, insert new_init_logic, 
    # then keep everything from load_book_idx.
    new_content = ''.join(lines[:start_line_idx]) + new_init_logic + '\n' + ''.join(lines[load_book_idx:])
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print('SUCCESS: Cleaned up and patched ReaderViewModel.kt')
else:
    print(f'ERROR: Could not find boundaries (start={start_line_idx}, loadBook={load_book_idx})')
