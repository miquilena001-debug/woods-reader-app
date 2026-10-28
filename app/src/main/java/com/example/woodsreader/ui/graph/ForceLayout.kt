package com.example.woodsreader.ui.graph

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Motor de simulación de fuerzas para el grafo de conocimiento.
 * Clase pura en Kotlin, sin dependencias de Android ni de Compose.
 *
 * @param n Número de nodos (máximo 150).
 * @param edges Lista de aristas como pares (índice_origen, índice_destino).
 * @param random Generador de números pseudoaleatorios para las posiciones iniciales.
 * @param initialPositions Mapa opcional de posiciones iniciales cacheadas por índice (x, y) para apertura instantánea.
 */
class ForceLayout(
    val n: Int,
    val edges: List<Pair<Int, Int>>,
    random: Random = Random(42),
    initialPositions: Map<Int, Pair<Float, Float>>? = null
) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)

    // Si todas las posiciones estaban en caché, empezar ya settled (alpha < 0.02f) para apertura instantánea sin saltos
    var alpha: Float = if (initialPositions != null && initialPositions.size >= n && n > 0) 0.019f else 1f

    val settled: Boolean
        get() = alpha < 0.02f

    init {
        for (i in 0 until n) {
            val cached = initialPositions?.get(i)
            if (cached != null) {
                x[i] = cached.first
                y[i] = cached.second
            } else {
                // Posición inicial aleatoria en [-400, 400]
                x[i] = random.nextFloat() * 800f - 400f
                y[i] = random.nextFloat() * 800f - 400f
            }
        }
    }

    /**
     * Ejecuta un paso de la simulación de fuerzas.
     * 1. Repulsión O(n^2) entre todos los pares.
     * 2. Atracción en aristas conectadas.
     * 3. Gravedad al centro.
     * 4. Amortiguación y enfriamiento de alpha.
     */
    fun step() {
        if (n <= 0) {
            alpha = 0f
            return
        }

        // 1. Repulsión O(n^2) entre todos los pares:
        // d2 = max(dx^2 + dy^2, 25); f = 9000/d2*alpha; empuja a lo largo de (dx,dy)/d
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = x[i] - x[j]
                val dy = y[i] - y[j]
                val d2 = max(dx * dx + dy * dy, 25f)
                val d = sqrt(d2)
                val f = (9000f / d2) * alpha
                val fx = (dx / d) * f
                val fy = (dy / d) * f

                vx[i] += fx
                vy[i] += fy
                vx[j] -= fx
                vy[j] -= fy
            }
        }

        // 2. Atracción en aristas:
        // f = (d - 120)*0.02*alpha hacia el otro nodo
        for ((u, v) in edges) {
            if (u in 0 until n && v in 0 until n && u != v) {
                val dx = x[v] - x[u]
                val dy = y[v] - y[u]
                val d2 = max(dx * dx + dy * dy, 0.01f)
                val d = sqrt(d2)
                val f = (d - 120f) * 0.02f * alpha
                val fx = (dx / d) * f
                val fy = (dy / d) * f

                vx[u] += fx
                vy[u] += fy
                vx[v] -= fx
                vy[v] -= fy
            }
        }

        // 3. Gravedad al centro: v -= pos * 0.01 * alpha
        // 4. Amortiguación v *= 0.85; pos += v
        for (i in 0 until n) {
            vx[i] -= x[i] * 0.01f * alpha
            vy[i] -= y[i] * 0.01f * alpha

            vx[i] *= 0.85f
            vy[i] *= 0.85f

            x[i] += vx[i]
            y[i] += vy[i]
        }

        // alpha *= 0.985
        alpha *= 0.985f
    }
}
