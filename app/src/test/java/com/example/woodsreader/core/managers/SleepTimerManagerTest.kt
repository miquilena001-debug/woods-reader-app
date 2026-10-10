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
}
