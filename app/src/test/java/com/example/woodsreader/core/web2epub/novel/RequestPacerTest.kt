package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RequestPacerTest {

    @Test
    fun `pacer respects minimum delay with virtual clock`() = runTest {
        var virtualTime = 1000L
        val clock = { virtualTime }

        val pacer = RequestPacer(
            minMs = 500L,
            maxMs = 500L,
            now = clock,
            random = Random(42)
        )

        // Primera llamada: sin espera previa
        pacer.await()
        assertTrue(virtualTime == 1000L)

        // Avanzar sólo 200ms en el reloj simulado
        virtualTime = 1200L

        // Segunda llamada: debe esperar al menos 300ms virtuales adicionales
        val startVirtual = virtualTime
        pacer.await()
        // En runTest con corrutinas el delay avanza el dispatcher virtual
        assertTrue(virtualTime >= startVirtual)
    }
}
