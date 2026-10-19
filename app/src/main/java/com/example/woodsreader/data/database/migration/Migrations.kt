package com.example.woodsreader.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_title` ON `books` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_author` ON `books` (`author`)")
    }
}

/**
 * Migración v2 -> v3: Añade índice compuesto en reading_sessions(mode, startAt) 
 * para optimizar las consultas de estadísticas.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_mode_startAt` " +
            "ON `reading_sessions` (`mode`, `startAt`)"
        )
    }
}

/**
 * Migración v3 -> v4: Añade columnas de fallback de locator a `books`.
 * Permite restaurar la posición de lectura aunque el CFI quede inválido
 * (p.ej. si el EPUB se re-empaqueta o cambia su estructura interna).
 * Columnas NULLABLE con default seguro para no romper datos existentes.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN fallbackLocatorHref TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN fallbackLocatorPosition INTEGER DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_collectionId` ON `books` (`collectionId`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tts_rules` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`pattern` TEXT NOT NULL, " +
            "`replacement` TEXT NOT NULL, " +
            "`isRegex` INTEGER NOT NULL, " +
            "`isEnabled` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_title` ON `books` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_author` ON `books` (`author`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tts_rules` ADD COLUMN `bookId` TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_rules_bookId` ON `tts_rules` (`bookId`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_filePath` ON `books` (`filePath`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_coverCachePath` ON `books` (`coverCachePath`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_rules_isEnabled_bookId` ON `tts_rules` (`isEnabled`, `bookId`)")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9
)
