# Python script to extract data.json from maquetado.md
with open('maquetado.md', 'r', encoding='utf-8') as f:
    text = f.read()

parts = text.split('```json')
for p in parts:
    sub = p.split('```')[0].strip()
    if '"books"' in sub:
        with open('mockup/data.json', 'w', encoding='utf-8') as out:
            out.write(sub)
        print('Saved mockup/data.json successfully.')
        break
