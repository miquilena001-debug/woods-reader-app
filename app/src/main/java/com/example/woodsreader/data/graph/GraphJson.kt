package com.example.woodsreader.data.graph

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GraphGlossaryJson(
    val version: Int = 1,
    val bookTitle: String,
    val entities: List<GraphEntityJson> = emptyList(),
    val relations: List<GraphRelationJson> = emptyList()
)

@Serializable
data class GraphEntityJson(
    val name: String,
    val type: EntityType = EntityType.OTRO,
    val description: String = "",
    val aliases: List<String> = emptyList()
)

@Serializable
data class GraphRelationJson(
    val source: String,
    val target: String,
    val label: String = ""
)

object GraphJsonHelper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exporta los datos del grafo a formato JSON estándar según FASE 11:
     * {version: 1, bookTitle, entities: [{name, type, description, aliases[]}], relations: [{source, target, label}]}
     * Relaciones referenciadas por NOMBRE, nunca por ID numérico interno.
     */
    fun exportToJson(bookTitle: String, exportData: GraphExportData): String {
        val idToName = exportData.entities.associate { it.entity.id to it.entity.name }

        val entityJsons = exportData.entities.map { item ->
            GraphEntityJson(
                name = item.entity.name,
                type = item.entity.type,
                description = item.entity.description,
                aliases = item.aliases.map { it.alias }
            )
        }

        val relationJsons = exportData.relations.mapNotNull { rel ->
            val sourceName = idToName[rel.sourceId]
            val targetName = idToName[rel.targetId]
            if (sourceName != null && targetName != null && sourceName != targetName) {
                GraphRelationJson(
                    source = sourceName,
                    target = targetName,
                    label = rel.label
                )
            } else {
                null
            }
        }.distinct()

        val glossary = GraphGlossaryJson(
            version = 1,
            bookTitle = bookTitle,
            entities = entityJsons,
            relations = relationJsons
        )

        return json.encodeToString(glossary)
    }

    /**
     * Parsea un JSON de glosario.
     */
    fun parseJson(jsonString: String): GraphGlossaryJson {
        return json.decodeFromString(jsonString)
    }

    /**
     * Importa y fusiona un glosario JSON en Room para el libro especificado.
     * FASE 11: "al importar, fusionar por nombre/alias, confirmed=true."
     * Retorna el número de entidades importadas o fusionadas.
     */
    suspend fun importGlossary(
        bookId: String,
        jsonString: String,
        graphRepository: GraphRepository
    ): Int {
        val glossary = parseJson(jsonString)
        if (glossary.entities.isEmpty()) return 0

        // 1. Obtener entidades existentes con sus aliases para este libro
        val existingData = graphRepository.exportAll(bookId)
        val existingEntities = existingData.entities

        // Mapa de búsqueda rápida: nombre o alias en minúsculas -> entidad existente
        val existingLookup = HashMap<String, GraphEntityWithAliases>()
        for (item in existingEntities) {
            existingLookup[item.entity.name.trim().lowercase()] = item
            for (alias in item.aliases) {
                existingLookup[alias.alias.trim().lowercase()] = item
            }
        }

        // Mapa para resolver las relaciones por nombre de entidad importada -> id en Room
        val nameToEntityId = HashMap<String, Long>()

        var processedCount = 0

        for (entityJson in glossary.entities) {
            val trimmedName = entityJson.name.trim()
            if (trimmedName.isEmpty()) continue

            // Verificar si coincide con alguna entidad existente por nombre o por alguno de sus aliases
            var match: GraphEntityWithAliases? = existingLookup[trimmedName.lowercase()]
            if (match == null) {
                for (a in entityJson.aliases) {
                    val aliasMatch = existingLookup[a.trim().lowercase()]
                    if (aliasMatch != null) {
                        match = aliasMatch
                        break
                    }
                }
            }

            val finalId: Long

            if (match != null) {
                // Fusión con entidad existente: se confirma y se agregan descripciones y aliases faltantes
                val currentEntity = match.entity
                val updatedDesc = if (currentEntity.description.isBlank() && entityJson.description.isNotBlank()) {
                    entityJson.description.trim()
                } else {
                    currentEntity.description
                }
                val updatedType = if (currentEntity.type == EntityType.OTRO && entityJson.type != EntityType.OTRO) {
                    entityJson.type
                } else {
                    currentEntity.type
                }

                val updatedEntity = currentEntity.copy(
                    confirmed = true,
                    ignored = false,
                    type = updatedType,
                    description = updatedDesc
                )
                graphRepository.updateEntity(updatedEntity)
                finalId = currentEntity.id

                // Insertar aliases que no existan
                val currentAliases = match.aliases.map { it.alias.trim().lowercase() }.toSet()
                for (aliasStr in entityJson.aliases) {
                    val aTrim = aliasStr.trim()
                    if (aTrim.isNotEmpty() && !currentAliases.contains(aTrim.lowercase())) {
                        graphRepository.insertAlias(finalId, aTrim)
                    }
                }
            } else {
                // Nueva entidad: se inserta con confirmed = true
                val newEntity = GraphEntity(
                    bookId = bookId,
                    name = trimmedName,
                    type = entityJson.type,
                    description = entityJson.description.trim(),
                    firstChapterIndex = 0,
                    mentionCount = 1,
                    confirmed = true,
                    ignored = false
                )
                val insertedId = graphRepository.insertEntity(newEntity)
                finalId = if (insertedId > 0) {
                    insertedId
                } else {
                    graphRepository.getEntityByName(bookId, trimmedName)?.id ?: 0L
                }

                if (finalId > 0) {
                    for (aliasStr in entityJson.aliases) {
                        val aTrim = aliasStr.trim()
                        if (aTrim.isNotEmpty()) {
                            graphRepository.insertAlias(finalId, aTrim)
                        }
                    }
                }
            }

            if (finalId > 0) {
                nameToEntityId[trimmedName.lowercase()] = finalId
                for (a in entityJson.aliases) {
                    nameToEntityId[a.trim().lowercase()] = finalId
                }
                processedCount++
            }
        }

        // 2. Importar relaciones
        for (relJson in glossary.relations) {
            val sourceId = nameToEntityId[relJson.source.trim().lowercase()]
            val targetId = nameToEntityId[relJson.target.trim().lowercase()]

            if (sourceId != null && targetId != null && sourceId != targetId) {
                val relation = GraphRelation(
                    bookId = bookId,
                    sourceId = sourceId,
                    targetId = targetId,
                    label = relJson.label.trim(),
                    weight = 1,
                    manual = true,
                    firstChapterIndex = 0
                )
                graphRepository.upsertRelation(relation)
            }
        }

        return processedCount
    }
}
