package com.example.woodsreader.ui.graph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForceLayoutTest {

    @Test
    fun testForceLayoutSettlesWithoutNaN() {
        val n = 150
        val edges = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n - 1) {
            edges.add(Pair(i, i + 1))
            if (i % 3 == 0 && i + 3 < n) {
                edges.add(Pair(i, i + 3))
            }
        }

        val layout = ForceLayout(n, edges)

        repeat(500) {
            layout.step()
        }

        assertTrue("Layout should be settled after 500 steps (alpha < 0.02f)", layout.settled)

        for (i in 0 until n) {
            assertFalse("x[$i] must not be NaN", layout.x[i].isNaN())
            assertFalse("y[$i] must not be NaN", layout.y[i].isNaN())
            assertFalse("vx[$i] must not be NaN", layout.vx[i].isNaN())
            assertFalse("vy[$i] must not be NaN", layout.vy[i].isNaN())

            assertFalse("x[$i] must not be Infinite", layout.x[i].isInfinite())
            assertFalse("y[$i] must not be Infinite", layout.y[i].isInfinite())
        }
    }
}
