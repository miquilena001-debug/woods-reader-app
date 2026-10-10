import re

with open('app/src/main/java/com/example/woodsreader/data/repository/BookRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace single-line withContext(Dispatchers.IO) { ... }
# Example: = withContext(Dispatchers.IO) { bookDao.upsert(book) }
content = re.sub(r'=\s*withContext\(Dispatchers\.IO\)\s*\{\s*(.*?)\s*\}', r'= \1', content)

# Replace multi-line withContext(Dispatchers.IO) { ... }
# Example:
#         withContext(Dispatchers.IO) {
#             bookDao.updateProgress(
#                 id      = bookId,
#                 progress = progress,
#                 locator = locatorJson,
#                 time    = System.currentTimeMillis()
#             )
#         }
content = re.sub(r'=\s*withContext\(Dispatchers\.IO\)\s*\{\s*([\s\S]*?)\s*\}', r'= \1', content)

with open('app/src/main/java/com/example/woodsreader/data/repository/BookRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print('Done')
