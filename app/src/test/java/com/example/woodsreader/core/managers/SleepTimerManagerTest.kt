package com.example.woodsreader.core.managers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testStartSleepTimerCountdownAndExpiration() = runTest(testDispatcher) {
        var speedChangedTo: Float? = null
        var timerExpiredCalled = false

        val manager = SleepTimerManager(
            timeProvider = object : TimeProvider {
                override fun currentTimeMillis() = testScheduler.currentTime
                override fun elapsedRealtime() = testScheduler.currentTime
                override fun currentDateString() = ""
            }
        )

        // Start 1 minute (60,000ms) timer with 1.0f speed
        manager.startSleepTimer(
            minutes = 1,
            currentSpeed = 1.0f,
            scope = testScope,
            onSpeedChange = { speedChangedTo = it },
            onTimerExpired = { timerExpiredCalled = true }
        )

        assertNotNull(manager.sleepTimerRemaining.value)
        assertTrue(manager.sleepTimerRemaining.value!! > 50000L)

        // Advance time by 45 seconds (45,000ms).
        // Timer should not have expired, but remaining time should decrease.
        advanceTimeBy(45000L)
        assertFalse(timerExpiredCalled)
        assertTrue(manager.sleepTimerRemaining.value!! <= 20000L)

        // In the last 20 seconds, the speed should ramp down from 1.0f towards 0.5f.
        assertNotNull(speedChangedTo)
        assertTrue(speedChangedTo!! < 1.0f)

        // Advance to expiration (another 20 seconds)
        advanceTimeBy(20000L)
        assertTrue(timerExpiredCalled)
        assertNull(manager.sleepTimerRemaining.value)
    }

    @Test
    fun testCancelSleepTimerRestoresSpeed() = runTest(testDispatcher) {
        var speedChangedTo: Float? = null
        val manager = SleepTimerManager(
            timeProvider = object : TimeProvider {
                override fun currentTimeMillis() = testScheduler.currentTime
                override fun elapsedRealtime() = testScheduler.currentTime
                override fun currentDateString() = ""
            }
        )

        manager.startSleepTimer(
            minutes = 5,
            currentSpeed = 1.5f,
            scope = testScope,
            onSpeedChange = { speedChangedTo = it },
            onTimerExpired = {}
        )
        
        // Cancel timer
        manager.cancelSleepTimer(onSpeedChange = { speedChangedTo = it })

        assertNull(manager.sleepTimerRemaining.value)
        // Original speed 1.5f should be restored in the finally block
        assertEquals(1.5f, speedChangedTo)
    }

    @Test
    fun testCustomDurationSleepTimerCountdownAndExpiration() = runTest(testDispatcher) {
        var speedChangedTo: Float? = null
        var timerExpiredCalled = false

        val manager = SleepTimerManager(
            timeProvider = object : TimeProvider {
                override fun currentTimeMillis() = testScheduler.currentTime
                override fun elapsedRealtime() = testScheduler.currentTime
                override fun currentDateString() = ""
            }
        )

        // Inicia temporizador personalizado de 20 minutos (1.200.000 ms) a velocidad 1.25x
        manager.startSleepTimer(
            minutes = 20,
            currentSpeed = 1.25f,
            scope = testScope,
            onSpeedChange = { speedChangedTo = it },
            onTimerExpired = { timerExpiredCalled = true }
        )

        assertNotNull(manager.sleepTimerRemaining.value)
        assertEquals(20 * 60 * 1000L, manager.sleepTimerRemaining.value)

        // Avanzar 10 minutos (600.000 ms): el temporizador disminuye y no expira
        advanceTimeBy(10 * 60 * 1000L)
        assertFalse(timerExpiredCalled)
        assertTrue(manager.sleepTimerRemaining.value!! in 595000L..605000L)

        // Avanzar hasta los últimos 15 segundos (9 min + 45 s = 585.000 ms transcurridos adicionales)
        advanceTimeBy(9 * 60 * 1000L + 45000L)
        assertFalse(timerExpiredCalled)
        assertTrue(manager.sleepTimerRemaining.value!! <= 20000L)
        assertNotNull(speedChangedTo)
        assertTrue(speedChangedTo!! < 1.25f)

        // Avanzar a la expiración total (20 segundos adicionales)
        advanceTimeBy(20000L)
        assertTrue(timerExpiredCalled)
        assertNull(manager.sleepTimerRemaining.value)
        assertEquals(1.25f, speedChangedTo)
    }

    @Test
    fun testEndOfChapterSleepTimer() = runTest(testDispatcher) {
        var speedChangedTo: Float? = null
        var timerExpiredCalled = false

        val manager = SleepTimerManager(
            timeProvider = object : TimeProvider {
                override fun currentTimeMillis() = testScheduler.currentTime
                override fun elapsedRealtime() = testScheduler.currentTime
                override fun currentDateString() = ""
            }
        )

        // Inicia temporizador con -1 (Fin del capítulo)
        manager.startSleepTimer(
            minutes = -1,
            currentSpeed = 1.2f,
            scope = testScope,
            onSpeedChange = { speedChangedTo = it },
            onTimerExpired = { timerExpiredCalled = true }
        )

        assertEquals(-1L, manager.sleepTimerRemaining.value)

        // Avanzar el reloj 2 horas: no debe expirar por tiempo
        advanceTimeBy(2 * 60 * 60 * 1000L)
        assertFalse(timerExpiredCalled)
        assertEquals(-1L, manager.sleepTimerRemaining.value)

        // Cancelar el temporizador restaura null y la velocidad
        manager.cancelSleepTimer(onSpeedChange = { speedChangedTo = it })
        assertNull(manager.sleepTimerRemaining.value)
        assertEquals(1.2f, speedChangedTo)
    }

    @Test
    fun testCancelCustomSleepTimerDuringRampDownRestoresSpeed() = runTest(testDispatcher) {
        var speedChangedTo: Float? = null
        val manager = SleepTimerManager(
            timeProvider = object : TimeProvider {
                override fun currentTimeMillis() = testScheduler.currentTime
                override fun elapsedRealtime() = testScheduler.currentTime
                override fun currentDateString() = ""
            }
        )

        manager.startSleepTimer(
            minutes = 20,
            currentSpeed = 1.4f,
            scope = testScope,
            onSpeedChange = { speedChangedTo = it },
            onTimerExpired = {}
        )

        // Avanzar a la zona de rampa de frenado (últimos 10 segundos: 19 min + 50 seg)
        advanceTimeBy(19 * 60 * 1000L + 50000L)
        assertNotNull(speedChangedTo)
        assertTrue(speedChangedTo!! < 1.4f)

        // Cancelar durante la atenuación restaura la velocidad original
        manager.cancelSleepTimer(onSpeedChange = { speedChangedTo = it })
        assertNull(manager.sleepTimerRemaining.value)
        assertEquals(1.4f, speedChangedTo)
    }
}
