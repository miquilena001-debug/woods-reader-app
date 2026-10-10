import re

with open('app/src/main/java/com/example/woodsreader/data/repository/BookRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix cleanupInvalidSessions
content = content.replace(
    '''    suspend fun cleanupInvalidSessions() = val irrelevant = sessionDao.getIrrelevantSessions()
        if (irrelevant.isNotEmpty()) sessionDao.deleteSessions(irrelevant)''',
    '''    suspend fun cleanupInvalidSessions() {
        val irrelevant = sessionDao.getIrrelevantSessions()
        if (irrelevant.isNotEmpty()) sessionDao.deleteSessions(irrelevant)
    }'''
)

# Fix repairZeroWordSessions
content = content.replace(
    '''    suspend fun repairZeroWordSessions() = val broken = sessionDao.getBrokenSessions()
        if (broken.isEmpty()) return@withContext

        broken.forEach { session ->
            val durationMin = ((session.endAt ?: return@forEach) - session.startAt) / 60_000.0
            if (durationMin > 0.5) {
                val estimatedWords = (durationMin * if (session.mode == ReadingMode.MANUAL) 100 else 150).toLong()
                sessionDao.updateWordsRead(session.id, estimatedWords)
            }
        }''',
    '''    suspend fun repairZeroWordSessions() {
        val broken = sessionDao.getBrokenSessions()
        if (broken.isEmpty()) return

        broken.forEach { session ->
            val durationMin = ((session.endAt ?: return@forEach) - session.startAt) / 60_000.0
            if (durationMin > 0.5) {
                val estimatedWords = (durationMin * if (session.mode == ReadingMode.MANUAL) 100 else 150).toLong()
                sessionDao.updateWordsRead(session.id, estimatedWords)
            }
        }
    }'''
)

content = content.replace(
    '''    suspend fun getReadingStats(date: String): ReadingStatsEntity? = readingStatsDao.getStatsForDate(date)''',
    '''    suspend fun getReadingStats(date: String): ReadingStatsEntity? = readingStatsDao.getStatsForDate(date)'''
)

with open('app/src/main/java/com/example/woodsreader/data/repository/BookRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print('Done fixing')
