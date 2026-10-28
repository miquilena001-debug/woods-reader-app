package com.example.woodsreader.data.graph

import android.content.Context
import com.example.woodsreader.ui.graph.ForceLayout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class NodePosition(
    val entityId: Long,
    val x: Float,
    val y: Float
)

@Serializable
data class GraphPositionsCacheData(
    val positions: List<NodePosition> = emptyList()
)

/**
 * FASE 12: Caché de posiciones (guardar x,y por entidad en JSON local) para apertura instantánea del grafo.
 */
object GraphPositionsCache {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getCacheFile(context: Context, bookId: String): File {
        val cleanId = bookId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
        return File(context.filesDir, "graph_positions_$cleanId.json")
    }

    /**
     * Carga las posiciones cacheadas asociadas a las entidades del libro.
     * Retorna un mapa de entityId -> Pair(x, y).
     */
    fun loadPositions(context: Context, bookId: String): Map<Long, Pair<Float, Float>> {
        return try {
            val file = getCacheFile(context, bookId)
            if (!file.exists()) return emptyMap()
            val text = file.readText()
            if (text.isBlank()) return emptyMap()
            val data = json.decodeFromString<GraphPositionsCacheData>(text)
            data.positions.associate { it.entityId to Pair(it.x, it.y) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Guarda las posiciones actuales de los nodos calculadas por [ForceLayout] en el archivo JSON local.
     */
    fun savePositions(
        context: Context,
        bookId: String,
        nodes: List<GraphEntity>,
        layout: ForceLayout
    ) {
        if (nodes.isEmpty() || layout.n <= 0) return
        try {
            val file = getCacheFile(context, bookId)
            val count = minOf(nodes.size, layout.n)
            val list = ArrayList<NodePosition>(count)
            for (i in 0 until count) {
                val entity = nodes[i]
                val x = layout.x[i]
                val y = layout.y[i]
                // Validar que no sean NaN ni infinitos antes de persistir
                if (!x.isNaN() && !y.isNaN() && !x.isInfinite() && !y.isInfinite()) {
                    list.add(NodePosition(entityId = entity.id, x = x, y = y))
                }
            }
            val data = GraphPositionsCacheData(list)
            val text = json.encodeToString(data)
            file.writeText(text)
        } catch (_: Exception) {
            // Error silencioso en caché no bloquea la app
        }
    }
}
