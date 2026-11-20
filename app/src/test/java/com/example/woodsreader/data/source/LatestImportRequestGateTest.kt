package com.example.woodsreader.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestImportRequestGateTest {
    @Test
    fun `a newer ACTION_VIEW request invalidates earlier work`() {
        val gate = LatestImportRequestGate()
        val older = gate.begin()
        val latest = gate.begin()

        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(latest))
    }
}
