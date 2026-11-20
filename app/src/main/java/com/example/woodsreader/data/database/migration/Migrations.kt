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

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_bookId_createdAt` ON `highlights` (`bookId`, `createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId_createdAt` ON `bookmarks` (`bookId`, `createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId_startAt` ON `reading_sessions` (`bookId`, `startAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TEMP TABLE graph_alias_backup AS SELECT * FROM graph_alias")
        db.execSQL("CREATE TEMP TABLE graph_relation_backup AS SELECT * FROM graph_relation")
        db.execSQL("CREATE TEMP TABLE graph_mention_backup AS SELECT * FROM graph_mention")

        db.execSQL("DROP TABLE graph_alias")
        db.execSQL("DROP TABLE graph_relation")
        db.execSQL("DROP TABLE graph_mention")
        db.execSQL(
            """
            CREATE TABLE graph_entity_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bookId TEXT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL,
                firstChapterIndex INTEGER NOT NULL,
                mentionCount INTEGER NOT NULL,
                confirmed INTEGER NOT NULL,
                ignored INTEGER NOT NULL,
                FOREIGN KEY(bookId) REFERENCES books(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO graph_entity_new (
                id, bookId, name, type, description, firstChapterIndex,
                mentionCount, confirmed, ignored
            )
            SELECT entity.id, entity.bookId, entity.name, entity.type, entity.description,
                   entity.firstChapterIndex, entity.mentionCount, entity.confirmed, entity.ignored
            FROM graph_entity AS entity
            WHERE EXISTS (SELECT 1 FROM books WHERE books.id = entity.bookId)
            """.trimIndent()
        )
        db.execSQL("DROP TABLE graph_entity")
        db.execSQL("ALTER TABLE graph_entity_new RENAME TO graph_entity")
        db.execSQL(
            "CREATE UNIQUE INDEX `index_graph_entity_bookId_name` " +
                "ON `graph_entity` (`bookId`, `name`)"
        )

        db.execSQL(
            """
            CREATE TABLE graph_alias (
                entityId INTEGER NOT NULL,
                alias TEXT NOT NULL,
                PRIMARY KEY(entityId, alias),
                FOREIGN KEY(entityId) REFERENCES graph_entity(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO graph_alias (entityId, alias)
            SELECT backup.entityId, backup.alias
            FROM graph_alias_backup AS backup
            WHERE EXISTS (SELECT 1 FROM graph_entity WHERE graph_entity.id = backup.entityId)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE graph_relation (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bookId TEXT NOT NULL,
                sourceId INTEGER NOT NULL,
                targetId INTEGER NOT NULL,
                label TEXT NOT NULL,
                weight INTEGER NOT NULL,
                manual INTEGER NOT NULL,
                firstChapterIndex INTEGER NOT NULL,
                FOREIGN KEY(sourceId) REFERENCES graph_entity(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(targetId) REFERENCES graph_entity(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO graph_relation (
                id, bookId, sourceId, targetId, label, weight, manual, firstChapterIndex
            )
            SELECT MIN(backup.id), backup.bookId, backup.sourceId, backup.targetId, backup.label,
                   SUM(backup.weight), MAX(backup.manual), MIN(backup.firstChapterIndex)
            FROM graph_relation_backup AS backup
            WHERE EXISTS (SELECT 1 FROM graph_entity WHERE graph_entity.id = backup.sourceId)
              AND EXISTS (SELECT 1 FROM graph_entity WHERE graph_entity.id = backup.targetId)
            GROUP BY backup.bookId, backup.sourceId, backup.targetId, backup.label
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX `index_graph_relation_bookId` ON `graph_relation` (`bookId`)"
        )
        db.execSQL(
            "CREATE INDEX `index_graph_relation_sourceId` ON `graph_relation` (`sourceId`)"
        )
        db.execSQL(
            "CREATE INDEX `index_graph_relation_targetId` ON `graph_relation` (`targetId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX `index_graph_relation_unique` " +
                "ON `graph_relation` (`bookId`, `sourceId`, `targetId`, `label`)"
        )

        db.execSQL(
            """
            CREATE TABLE graph_mention (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                entityId INTEGER NOT NULL,
                chapterIndex INTEGER NOT NULL,
                href TEXT NOT NULL,
                progression REAL NOT NULL,
                snippet TEXT NOT NULL,
                FOREIGN KEY(entityId) REFERENCES graph_entity(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO graph_mention (
                id, entityId, chapterIndex, href, progression, snippet
            )
            SELECT backup.id, backup.entityId, backup.chapterIndex, backup.href,
                   backup.progression, backup.snippet
            FROM graph_mention_backup AS backup
            WHERE EXISTS (SELECT 1 FROM graph_entity WHERE graph_entity.id = backup.entityId)
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX `index_graph_mention_entityId_chapterIndex` " +
                "ON `graph_mention` (`entityId`, `chapterIndex`)"
        )
        db.execSQL("DROP TABLE graph_alias_backup")
        db.execSQL("DROP TABLE graph_relation_backup")
        db.execSQL("DROP TABLE graph_mention_backup")
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
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_11_12
)
