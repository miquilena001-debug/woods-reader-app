import os

path = r'app\src\main\java\com\example\woodsreader\ui\reader\ReaderViewModel.kt'
with open(path, 'rb') as f:
    content = f.read().decode('utf-8', errors='replace')

# Normalize newlines
content = content.replace('\r\n', '\n')
lines = content.split('\n')

# Filter out redundant empty lines
cleaned_lines = []
for i, line in enumerate(lines):
    stripped = line.strip()
    if not stripped:
        if i > 0 and not cleaned_lines[-1].strip():
            continue
    cleaned_lines.append(line)

# Final join
final_content = '\n'.join(cleaned_lines)

with open(path, 'w', encoding='utf-8') as f:
    f.write(final_content)

print(f'SUCCESS: Cleaned up newlines. New line count: {len(cleaned_lines)}')
