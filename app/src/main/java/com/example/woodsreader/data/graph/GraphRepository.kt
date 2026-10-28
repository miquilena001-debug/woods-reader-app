package com.example.woodsreader.data.graph

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphRepository @Inject constructor(
    private val graphDao: GraphDao
) {
    fun entities(bookId: String, maxCh: Int, limit: Int = 150): Flow<List<GraphEntity>> =
        graphDao.entities(bookId, maxCh, limit)

    fun relations(bookId: String, maxCh: Int, minW: Int = 3): Flow<List<GraphRelation>> =
        graphDao.relations(bookId, maxCh, minW)

    fun candidates(bookId: String): Flow<List<GraphEntity>> =
        graphDao.candidates(bookId)

    suspend fun nextMention(entityId: Long, fromCh: Int): GraphMention? =
        graphDao.nextMention(entityId, fromCh)

    suspend fun prevMention(entityId: Long, fromCh: Int): GraphMention? =
        graphDao.prevMention(entityId, fromCh)

    suspend fun insertEntity(entity: GraphEntity): Long =
        graphDao.insertEntity(entity)

    suspend fun insertEntities(entities: List<GraphEntity>): List<Long> =
        graphDao.insertEntities(entities)

    suspend fun upsertRelation(relation: GraphRelation): Long =
        graphDao.upsertRelation(relation)

    suspend fun upsertRelations(relations: List<GraphRelation>): List<Long> =
        graphDao.upsertRelations(relations)

    suspend fun updateEntity(entity: GraphEntity) =
        graphDao.updateEntity(entity)

    suspend fun updateRelation(relation: GraphRelation) =
        graphDao.updateRelation(relation)

    suspend fun insertMentions(mentions: List<GraphMention>) =
        graphDao.insertMentions(mentions)

    fun getMentionsForEntity(entityId: Long): Flow<List<GraphMention>> =
        graphDao.getMentionsForEntityFlow(entityId)

    suspend fun insertAlias(entityId: Long, alias: String) =
        graphDao.insertAlias(GraphAlias(entityId, alias))

    suspend fun deleteAlias(entityId: Long, alias: String) =
        graphDao.deleteAlias(GraphAlias(entityId, alias))

    fun getAliases(entityId: Long): Flow<List<GraphAlias>> =
        graphDao.getAliasesFlow(entityId)

    suspend fun getAliasesList(entityId: Long): List<GraphAlias> =
        graphDao.getAliases(entityId)

    suspend fun mergeInto(sourceId: Long, targetId: Long) =
        graphDao.mergeInto(sourceId, targetId)

    suspend fun exportAll(bookId: String): GraphExportData =
        graphDao.exportAll(bookId)

    suspend fun getEntityById(id: Long): GraphEntity? =
        graphDao.getEntityById(id)

    fun getEntityByIdFlow(id: Long): Flow<GraphEntity?> =
        graphDao.getEntityByIdFlow(id)

    suspend fun getEntityByName(bookId: String, name: String): GraphEntity? =
        graphDao.getEntityByName(bookId, name)

    suspend fun deleteEntity(id: Long) =
        graphDao.deleteEntityById(id)

    suspend fun deleteRelation(relation: GraphRelation) =
        graphDao.deleteRelation(relation)

    fun getRelationsForEntity(bookId: String, entityId: Long): Flow<List<GraphRelation>> =
        graphDao.getRelationsForEntityFlow(bookId, entityId)
}
