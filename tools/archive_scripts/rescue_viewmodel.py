import os
import re

path = r'app\src\main\java\com\example\woodsreader\ui\reader\ReaderViewModel.kt'
with open(path, 'rb') as f:
    content = f.read().decode('utf-8', errors='replace')

# 1. Collapse multiple newlines
content = re.sub(r'\n\s*\n', '\n\n', content)
# It seems some lines became just whitespace + \n
lines = [line.rstrip() for line in content.splitlines()]
# Remove empty strings that are consecutive
cleaned_lines = []
for i, line in enumerate(lines):
    if i > 0 and not line and not cleaned_lines[-1]:
        continue
    cleaned_lines.append(line)

content = '\n'.join(cleaned_lines)

# 2. Fix the init block
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

# Re-search for init and loadBook in cleaned content
cleaned_lines = content.splitlines()
start_idx = -1
load_book_idx = -1

for i, line in enumerate(cleaned_lines):
    if 'init {' in line and i > 100:
        start_idx = i
    if 'private fun loadBook()' in line:
        load_book_idx = i
        break

if start_idx != -1 and load_book_idx != -1:
    final_content = '\n'.join(cleaned_lines[:start_idx]) + '\n' + new_init_logic + '\n' + '\n'.join(cleaned_lines[load_book_idx:])
    with open(path, 'w', encoding='utf-8') as f:
        f.write(final_content)
    print('SUCCESS: Rescued and fixed ReaderViewModel.kt')
else:
    print(f'ERROR: Boundaries not found (start={start_idx}, loadBook={load_book_idx})')
