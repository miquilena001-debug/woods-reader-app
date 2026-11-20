package com.example.woodsreader.data.graph

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.woodsreader.data.database.BookEntity
import kotlinx.serialization.Serializable

enum class EntityType {
    PERSONAJE,
    SECTA,
    LUGAR,
    ARTEFACTO,
    RANGO,
    OTRO
}

class GraphTypeConverters {
    @TypeConverter
    fun entityTypeToString(type: EntityType): String = type.name

    @TypeConverter
    fun stringToEntityType(value: String): EntityType =
        try {
            EntityType.valueOf(value)
        } catch (_: Exception) {
            EntityType.OTRO
        }
}

@Serializable
@Immutable
@Entity(
    tableName = "graph_entity",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId", "name"], unique = true)
    ]
)
data class GraphEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: String,
    val name: String,
    val type: EntityType,
    val description: String = "",
    val firstChapterIndex: Int = 0,
    val mentionCount: Int = 0,
    val confirmed: Boolean = false,
    val ignored: Boolean = false
)

@Serializable
@Immutable
@Entity(
    tableName = "graph_alias",
    primaryKeys = ["entityId", "alias"],
    foreignKeys = [
        ForeignKey(
            entity = GraphEntity::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GraphAlias(
    val entityId: Long,
    val alias: String
)

@Serializable
@Immutable
@Entity(
    tableName = "graph_relation",
    foreignKeys = [
        ForeignKey(
            entity = GraphEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GraphEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("bookId"),
        Index("sourceId"),
        Index("targetId"),
        Index(
            value = ["bookId", "sourceId", "targetId", "label"],
            unique = true,
            name = "index_graph_relation_unique"
        )
    ]
)
data class GraphRelation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: String,
    val sourceId: Long,
    val targetId: Long,
    val label: String = "",
    val weight: Int = 1,
    val manual: Boolean = false,
    val firstChapterIndex: Int = 0
)

@Serializable
@Immutable
@Entity(
    tableName = "graph_mention",
    foreignKeys = [
        ForeignKey(
            entity = GraphEntity::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entityId", "chapterIndex"], unique = true)
    ]
)
data class GraphMention(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityId: Long,
    val chapterIndex: Int,
    val href: String,
    val progression: Double,
    val snippet: String
)
