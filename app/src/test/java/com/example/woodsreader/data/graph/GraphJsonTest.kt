package com.example.woodsreader.data.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphJsonTest {

    @Test
    fun testExportToJsonUsesNamesAndNoInternalIds() {
        val entity1 = GraphEntity(
            id = 101L,
            bookId = "book123",
            name = "Frodo Bolsón",
            type = EntityType.PERSONAJE,
            description = "Portador del Anillo",
            firstChapterIndex = 0,
            mentionCount = 50,
            confirmed = true
        )
        val entity2 = GraphEntity(
            id = 202L,
            bookId = "book123",
            name = "La Comarca",
            type = EntityType.LUGAR,
            description = "Tierra de los hobbits",
            firstChapterIndex = 0,
            mentionCount = 20,
            confirmed = true
        )

        val alias1 = GraphAlias(entityId = 101L, alias = "Portador")
        val alias2 = GraphAlias(entityId = 101L, alias = "Señor Bolsón")

        val relation = GraphRelation(
            id = 1L,
            bookId = "book123",
            sourceId = 101L,
            targetId = 202L,
            label = "habita en",
            weight = 5,
            manual = false,
            firstChapterIndex = 0
        )

        val exportData = GraphExportData(
            entities = listOf(
                GraphEntityWithAliases(entity = entity1, aliases = listOf(alias1, alias2)),
                GraphEntityWithAliases(entity = entity2, aliases = emptyList())
            ),
            relations = listOf(relation)
        )

        val jsonString = GraphJsonHelper.exportToJson("El Señor de los Anillos", exportData)

        // 1. Debe contener bookTitle y version = 1
        assertTrue(jsonString.contains("\"bookTitle\": \"El Señor de los Anillos\""))
        assertTrue(jsonString.contains("\"version\": 1"))

        // 2. Las relaciones deben referenciar a las entidades por NOMBRE, nunca por ID numérico
        assertTrue(jsonString.contains("\"source\": \"Frodo Bolsón\""))
        assertTrue(jsonString.contains("\"target\": \"La Comarca\""))
        assertTrue(jsonString.contains("\"label\": \"habita en\""))

        // No debe exponer IDs internos 101 o 202 en el JSON de entidades o relaciones
        assertFalse(jsonString.contains("101"))
        assertFalse(jsonString.contains("202"))

        // 3. Debe incluir los aliases
        assertTrue(jsonString.contains("Portador"))
        assertTrue(jsonString.contains("Señor Bolsón"))
    }

    @Test
    fun testParseJson() {
        val json = """
            {
              "version": 1,
              "bookTitle": "Dune",
              "entities": [
                {
                  "name": "Paul Atreides",
                  "type": "PERSONAJE",
                  "description": "Muad'Dib",
                  "aliases": ["Usul", "Kwisatz Haderach"]
                },
                {
                  "name": "Arrakis",
                  "type": "LUGAR",
                  "description": "Planeta desértico",
                  "aliases": ["Dune"]
                }
              ],
              "relations": [
                {
                  "source": "Paul Atreides",
                  "target": "Arrakis",
                  "label": "gobierna"
                }
              ]
            }
        """.trimIndent()

        val parsed = GraphJsonHelper.parseJson(json)
        assertEquals(1, parsed.version)
        assertEquals("Dune", parsed.bookTitle)
        assertEquals(2, parsed.entities.size)
        assertEquals("Paul Atreides", parsed.entities[0].name)
        assertEquals(EntityType.PERSONAJE, parsed.entities[0].type)
        assertEquals(listOf("Usul", "Kwisatz Haderach"), parsed.entities[0].aliases)
        assertEquals(1, parsed.relations.size)
        assertEquals("Paul Atreides", parsed.relations[0].source)
        assertEquals("Arrakis", parsed.relations[0].target)
        assertEquals("gobierna", parsed.relations[0].label)
    }
}
