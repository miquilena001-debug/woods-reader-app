package com.example.woodsreader.data.graph

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class GraphEntityWithAliases(
    @Embedded val entity: GraphEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "entityId"
    )
    val aliases: List<GraphAlias>
)

data class GraphExportData(
    val entities: List<GraphEntityWithAliases>,
    val relations: List<GraphRelation>
)

@Dao
interface GraphDao {

    @Query("""
        SELECT * FROM graph_entity 
        WHERE bookId = :bookId 
          AND confirmed = 1 
          AND ignored = 0 
          AND firstChapterIndex <= :maxCh 
        ORDER BY mentionCount DESC 
        LIMIT :limit
    """)
    fun entities(bookId: String, maxCh: Int, limit: Int = 150): Flow<List<GraphEntity>>

    @Query("""
        SELECT * FROM graph_relation 
        WHERE bookId = :bookId 
          AND firstChapterIndex <= :maxCh 
          AND (manual = 1 OR weight >= :minW)
    """)
    fun relations(bookId: String, maxCh: Int, minW: Int = 3): Flow<List<GraphRelation>>

    @Query("""
        SELECT * FROM graph_entity 
        WHERE bookId = :bookId 
          AND confirmed = 0 
          AND ignored = 0 
        ORDER BY mentionCount DESC
    """)
    fun candidates(bookId: String): Flow<List<GraphEntity>>

    @Query("""
        SELECT * FROM graph_mention 
        WHERE entityId = :entityId 
          AND chapterIndex >= :fromCh 
        ORDER BY chapterIndex ASC 
        LIMIT 1
    """)
    suspend fun nextMention(entityId: Long, fromCh: Int): GraphMention?

    @Query("""
        SELECT * FROM graph_mention 
        WHERE entityId = :entityId 
          AND chapterIndex <= :fromCh 
        ORDER BY chapterIndex DESC 
        LIMIT 1
    """)
    suspend fun prevMention(entityId: Long, fromCh: Int): GraphMention?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntity(entity: GraphEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntities(entities: List<GraphEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelation(relation: GraphRelation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelations(relations: List<GraphRelation>): List<Long>

    @Update
    suspend fun updateEntity(entity: GraphEntity)

    @Update
    suspend fun updateRelation(relation: GraphRelation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMentions(mentions: List<GraphMention>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMention(mention: GraphMention): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: GraphAlias)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliases(aliases: List<GraphAlias>)

    @Delete
    suspend fun deleteAlias(alias: GraphAlias)

    @Query("SELECT * FROM graph_alias WHERE entityId = :entityId")
    fun getAliasesFlow(entityId: Long): Flow<List<GraphAlias>>

    @Query("SELECT * FROM graph_alias WHERE entityId = :entityId")
    suspend fun getAliases(entityId: Long): List<GraphAlias>

    @Query("DELETE FROM graph_alias WHERE entityId = :entityId")
    suspend fun deleteAliasesForEntity(entityId: Long)

    @Query("SELECT * FROM graph_entity WHERE id = :id")
    suspend fun getEntityById(id: Long): GraphEntity?

    @Query("SELECT * FROM graph_entity WHERE id = :id")
    fun getEntityByIdFlow(id: Long): Flow<GraphEntity?>

    @Query("SELECT * FROM graph_entity WHERE bookId = :bookId AND name = :name LIMIT 1")
    suspend fun getEntityByName(bookId: String, name: String): GraphEntity?

    @Delete
    suspend fun deleteEntity(entity: GraphEntity)

    @Query("DELETE FROM graph_entity WHERE id = :id")
    suspend fun deleteEntityById(id: Long)

    @Query("SELECT * FROM graph_relation WHERE bookId = :bookId AND (sourceId = :entityId OR targetId = :entityId)")
    fun getRelationsForEntityFlow(bookId: String, entityId: Long): Flow<List<GraphRelation>>

    @Query("SELECT * FROM graph_relation WHERE bookId = :bookId AND (sourceId = :entityId OR targetId = :entityId)")
    suspend fun getRelationsForEntity(bookId: String, entityId: Long): List<GraphRelation>

    @Delete
    suspend fun deleteRelation(relation: GraphRelation)

    @Query("SELECT * FROM graph_mention WHERE entityId = :entityId")
    suspend fun getMentionsForEntity(entityId: Long): List<GraphMention>

    @Query("SELECT * FROM graph_mention WHERE entityId = :entityId ORDER BY chapterIndex ASC")
    fun getMentionsForEntityFlow(entityId: Long): Flow<List<GraphMention>>

    @Transaction
    @Query("SELECT * FROM graph_entity WHERE bookId = :bookId")
    suspend fun getEntitiesWithAliases(bookId: String): List<GraphEntityWithAliases>

    @Query("SELECT * FROM graph_relation WHERE bookId = :bookId")
    suspend fun getAllRelations(bookId: String): List<GraphRelation>

    @Transaction
    suspend fun exportAll(bookId: String): GraphExportData {
        val entities = getEntitiesWithAliases(bookId)
        val relations = getAllRelations(bookId)
        return GraphExportData(entities, relations)
    }

    @Transaction
    suspend fun mergeInto(sourceId: Long, targetId: Long) {
        if (sourceId == targetId) return
        val source = getEntityById(sourceId) ?: return
        val target = getEntityById(targetId) ?: return

        // 1. Mover aliases del source al target
        val sourceAliases = getAliases(sourceId)
        for (alias in sourceAliases) {
            insertAlias(GraphAlias(entityId = targetId, alias = alias.alias))
        }
        // Agregar el nombre del source como alias del target
        insertAlias(GraphAlias(entityId = targetId, alias = source.name))

        // 2. Mover mentions al target (evitando violar índice único entityId, chapterIndex)
        val sourceMentions = getMentionsForEntity(sourceId)
        val targetMentions = getMentionsForEntity(targetId)
        val targetChapters = targetMentions.map { it.chapterIndex }.toSet()
        for (mention in sourceMentions) {
            if (mention.chapterIndex !in targetChapters) {
                insertMention(
                    mention.copy(
                        id = 0L,
                        entityId = targetId
                    )
                )
            }
        }

        // 3. Mover relaciones al target
        val sourceRelations = getRelationsForEntity(source.bookId, sourceId)
        val targetRelations = getRelationsForEntity(target.bookId, targetId)

        for (rel in sourceRelations) {
            val otherId = if (rel.sourceId == sourceId) rel.targetId else rel.sourceId
            if (otherId == targetId) {
                deleteRelation(rel)
                continue
            }

            val existing = targetRelations.firstOrNull {
                (it.sourceId == targetId && it.targetId == otherId) ||
                (it.sourceId == otherId && it.targetId == targetId)
            }

            if (existing != null) {
                val updated = existing.copy(
                    weight = existing.weight + rel.weight,
                    manual = existing.manual || rel.manual,
                    firstChapterIndex = minOf(existing.firstChapterIndex, rel.firstChapterIndex)
                )
                updateRelation(updated)
                deleteRelation(rel)
            } else {
                val reoriented = if (rel.sourceId == sourceId) {
                    rel.copy(sourceId = targetId)
                } else {
                    rel.copy(targetId = targetId)
                }
                updateRelation(reoriented)
            }
        }

        // 4. Actualizar conteo de menciones y primer capítulo del target
        val updatedTarget = target.copy(
            mentionCount = target.mentionCount + source.mentionCount,
            firstChapterIndex = minOf(target.firstChapterIndex, source.firstChapterIndex)
        )
        updateEntity(updatedTarget)

        // 5. Borrar source (el CASCADE se encarga del resto)
        deleteEntityById(sourceId)
    }
}
