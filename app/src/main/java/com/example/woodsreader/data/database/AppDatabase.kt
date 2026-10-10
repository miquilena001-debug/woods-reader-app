package com.example.woodsreader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos Room principal del proyecto.
 *
 * - exportSchema = true  → genera JSON de schema en app/schemas/ para AutoMigrations
 * - version 1            → la versión inicial; nunca bajar esta cifra en producción
 * - addTypeConverter(AppTypeConverters::class) junto con @ProvidedTypeConverter
 *   permite que Hilt inyecte el convertor con posibles dependencias propias
 */
@Database(
    entities = [
        BookEntity::class,
        BookFtsEntity::class,
        HighlightEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class,
        CollectionEntity::class,
        ReadingStatsEntity::class,
        TtsRuleEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    companion object {
        val MIGRATION_2_3 = com.example.woodsreader.data.database.migration.MIGRATION_2_3
        val MIGRATION_3_4 = com.example.woodsreader.data.database.migration.MIGRATION_3_4
        val MIGRATION_4_5 = com.example.woodsreader.data.database.migration.MIGRATION_4_5
        val MIGRATION_5_6 = com.example.woodsreader.data.database.migration.MIGRATION_5_6
    }

    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingStatsDao(): ReadingStatsDao
    abstract fun ttsRuleDao(): TtsRuleDao
}
