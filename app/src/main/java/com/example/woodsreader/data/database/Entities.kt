package com.example.woodsreader.data.database

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.woodsreader.data.model.BookFormat
import com.example.woodsreader.data.model.HighlightColor
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.model.ReadingStatus
import kotlinx.serialization.Serializable
import java.util.UUID

// ── BookEntity ────────────────────────────────────────────────────────────────
@Serializable
@Immutable
@Entity(
    tableName = "books",
    indices = [
        Index("fileHash", unique = true),  // Detección de duplicados
        Index("lastOpenedAt"),             // Orden por libro más reciente
        Index("readingStatus"),            // Filtrar por estado
        Index("title"),                    // Índice para orden vertical alfabético
        Index("author"),                   // Índice para filtrado por autor
        Index("collectionId"),             // Índice para filtrado por colecciones
        Index("filePath"),                 // Búsqueda rápida por ruta de archivo
        Index("coverCachePath")            // Búsqueda rápida por carátula en caché
    ]
)
data class BookEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val title: String,
    val author: String = "",
    val language: String = "",
    val description: String = "",
    val format: BookFormat = BookFormat.UNKNOWN,
    val fileSizeBytes: Long = 0L,
    val fileHash: String,
    val readingStatus: ReadingStatus = ReadingStatus.NOT_STARTED,
    val currentLocatorJson: String? = null,    // Locator de Readium como JSON
    val readingProgressPercent: Float = 0f,
    val lastOpenedAt: Long? = null,
    val totalReadingTimeMs: Long = 0L,
    val chapterCount: Int = 0,
    val estimatedWordCount: Long = 0L,
    val coverCachePath: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val collectionId: String? = null,
    val userRating: Int? = null,               // 1-5, null = sin valorar
    val userNotes: String = "",
    val perBookSettingsJson: String? = null,
    // Fallback de posición: si el CFI (currentLocatorJson) queda inválido,
    // estos campos permiten restaurar al menos el capítulo y la posición aproximada.
    val fallbackLocatorHref: String? = null,
    val fallbackLocatorPosition: Int? = null
)

// ── HighlightEntity ───────────────────────────────────────────────────────────
@Serializable
@Immutable
@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class HighlightEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val locatorJson: String,
    val highlightedText: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val note: String = "",
    val chapterTitle: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ── BookmarkEntity ────────────────────────────────────────────────────────────
@Serializable
@Immutable
@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val locatorJson: String,
    val label: String = "",
    val isAutomatic: Boolean = false,          // true = bookmark al cerrar libro
    val createdAt: Long = System.currentTimeMillis()
)

// ── ReadingSessionEntity ──────────────────────────────────────────────────────
@Serializable
@Immutable
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("bookId"), 
        Index("startAt"),
        Index(value = ["mode", "startAt"]) // Optimización Parte 4
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val startAt: Long,
    val endAt: Long? = null,                   // null = sesión en curso
    val startLocatorJson: String,
    val endLocatorJson: String? = null,
    val mode: ReadingMode = ReadingMode.MANUAL,
    val wordsRead: Long = 0L
)

@Immutable
data class DailyWordCount(val dayOfWeek: Int, val totalWords: Long)

// ── CollectionEntity ──────────────────────────────────────────────────────────
@Immutable
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val coverBookId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ── TtsRuleEntity ─────────────────────────────────────────────────────────────
@Immutable
@Entity(
    tableName = "tts_rules",
    indices = [
        Index("bookId"),
        Index(value = ["isEnabled", "bookId"])
    ]
)
data class TtsRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
    val bookId: String? = null
)
