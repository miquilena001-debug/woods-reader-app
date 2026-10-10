import os

path = r'app\src\main\java\com\example\woodsreader\ui\reader\ReaderViewModel.kt'
with open(path, 'rb') as f:
    raw = f.read()

# Remove all \r
text = raw.replace(b'\r', b'').decode('utf-8', errors='replace')
lines = text.split('\n')

new_lines = []
for line in lines:
    if line.strip():
        new_lines.append(line)
    else:
        if not new_lines or new_lines[-1].strip():
            new_lines.append('')

with open(path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(new_lines))

print(f'SUCCESS: De-doubled. Lines: {len(new_lines)}')
