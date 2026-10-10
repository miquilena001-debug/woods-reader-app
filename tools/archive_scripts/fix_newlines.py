import os

path = r'app\src\main\java\com\example\woodsreader\ui\reader\ReaderViewModel.kt'
with open(path, 'rb') as f:
    raw = f.read()

# Replace all \r\n with \n
text = raw.replace(b'\r\n', b'\n').decode('utf-8', errors='replace')
lines = text.split('\n')

new_lines = []
for i, line in enumerate(lines):
    if i > 0 and not line.strip() and not lines[i-1].strip():
        # Skip if both current and previous lines are effectively empty
        continue
    new_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(new_lines))

print(f'SUCCESS: De-doubled. Lines: {len(new_lines)}')
