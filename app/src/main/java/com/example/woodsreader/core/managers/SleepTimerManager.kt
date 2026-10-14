package com.example.woodsreader.core.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

interface SleepTimer {
    val sleepTimerRemaining: StateFlow<Long?>
    fun startSleepTimer(minutes: Int, currentSpeed: Float, scope: CoroutineScope, onSpeedChange: (Float) -> Unit, onTimerExpired: () -> Unit)
    fun cancelSleepTimer(onSpeedChange: (Float) -> Unit)
}

class SleepTimerManager @Inject constructor(
    private val timeProvider: TimeProvider
) : SleepTimer {
    
    private var sleepTimerJob: Job? = null
    
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    override val sleepTimerRemaining = _sleepTimerRemaining.asStateFlow()

    private var cachedOriginalSpeed: Float = 1.0f

    override fun startSleepTimer(
        minutes: Int, 
        currentSpeed: Float, 
        scope: CoroutineScope, 
        onSpeedChange: (Float) -> Unit, 
        onTimerExpired: () -> Unit
    ) {
        cancelSleepTimer(onSpeedChange)
        if (minutes <= 0) return

        cachedOriginalSpeed = currentSpeed
        val durationMs = minutes * 60 * 1000L
        _sleepTimerRemaining.value = durationMs

        sleepTimerJob = scope.launch {
            val endTime = timeProvider.elapsedRealtime() + durationMs
            
            try {
                while (isActive && timeProvider.elapsedRealtime() < endTime) {
                    val remaining = endTime - timeProvider.elapsedRealtime()
                    _sleepTimerRemaining.value = remaining.coerceAtLeast(0)

                    // Smooth speed ramp down to 0.5x in the last 20 seconds
                    if (remaining <= 20000L) {
                        val progress = remaining.toFloat() / 20000L
                        val targetSpeed = 0.5f + (cachedOriginalSpeed - 0.5f) * progress
                        onSpeedChange(targetSpeed.coerceAtLeast(0.5f))
                    }

                    // Adaptive polling delay to save CPU, hitting 20s threshold and expiration accurately
                    val delayTime = when {
                        remaining <= 1000L -> remaining.coerceAtLeast(50L)
                        remaining <= 20000L -> minOf(1000L, remaining)
                        else -> minOf(5000L, remaining - 20000L)
                    }
                    delay(delayTime)
                }
                
                if (isActive) {
                    onTimerExpired()
                }
            } finally {
                // Ensure speed reverts if cancelled or finished
                _sleepTimerRemaining.value = null
                onSpeedChange(cachedOriginalSpeed)
            }
        }
    }

    override fun cancelSleepTimer(onSpeedChange: (Float) -> Unit) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (_sleepTimerRemaining.value != null) {
            _sleepTimerRemaining.value = null
            onSpeedChange(cachedOriginalSpeed)
        }
    }
}
