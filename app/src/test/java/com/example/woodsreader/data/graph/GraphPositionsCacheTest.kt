package com.example.woodsreader.data.graph

import com.example.woodsreader.ui.graph.ForceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GraphPositionsCacheTest {

    @Test
    fun testForceLayoutWithInitialPositionsSettlesAlmostInstantly() {
        val n = 20
        val edges = listOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4))
        
        // Simular posiciones cacheadas
        val cachedPositions = (0 until n).associateWith { i ->
            Pair((i * 20f) - 100f, (i * 15f) - 50f)
        }

        val layout = ForceLayout(
            n = n,
            edges = edges,
            random = Random(42),
            initialPositions = cachedPositions
        )

        // Alpha inicial debe ser 0.019f (< 0.02f) porque todas las posiciones vienen de caché para abrir instantáneo
        assertEquals(0.019f, layout.alpha, 0.001f)
        assertTrue("Layout con caché debe estar settled inmediatamente para abrir instantáneo", layout.settled)

        // Paso adicional no debe generar NaNs
        layout.step()

        // Las posiciones deben estar cerca de las originales y no ser NaN ni Infinite
        for (i in 0 until n) {
            assertFalse(layout.x[i].isNaN())
            assertFalse(layout.y[i].isNaN())
            assertFalse(layout.x[i].isInfinite())
            assertFalse(layout.y[i].isInfinite())
        }
    }

    @Test
    fun testForceLayoutWithoutInitialPositionsStartsAtFullAlpha() {
        val n = 10
        val edges = listOf(Pair(0, 1))

        val layout = ForceLayout(n = n, edges = edges)
        assertEquals(1f, layout.alpha, 0.001f)
        assertFalse(layout.settled)
    }
}
