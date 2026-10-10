
import sys

with open("app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "viewModelScope.launch {" in line and "combine(" in "".join(lines[lines.index(line):lines.index(line)+3]):
        # check if it is our injected block
        idx = lines.index(line)
        if "ttsManager.ttsState," in lines[idx+2] or "userPreferences," in lines[idx+4]:
            skip = True
            
    if skip:
        if line.strip() == "}" and "_viewState.value = newState" in lines[lines.index(line)-2]:
            skip = False
        continue
        
    new_lines.append(line)

with open("app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt", "w", encoding="utf-8") as f:
    f.writelines(new_lines)

