package com.example.woodsreader.core.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SleepTimerPreset(val minutes: Int, val label: String) {
    OFF(0, "Apagado"),
    MIN_15(15, "15 min"),
    MIN_30(30, "30 min"),
    MIN_45(45, "45 min"),
    MIN_60(60, "60 min"),
    END_OF_CHAPTER(-1, "Fin del Cap.")
}

@Singleton
class SmartSleepTimerManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdownJob: Job? = null

    private val _activePreset = MutableStateFlow(SleepTimerPreset.OFF)
    val activePreset: StateFlow<SleepTimerPreset> = _activePreset.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _fadeMultiplier = MutableStateFlow(1.0f)
    val fadeMultiplier: StateFlow<Float> = _fadeMultiplier.asStateFlow()

    var onTimerExpiredPauseAudio: (() -> Unit)? = null

    fun setSleepTimer(preset: SleepTimerPreset) {
        countdownJob?.cancel()
        _activePreset.value = preset
        _fadeMultiplier.value = 1.0f

        if (preset == SleepTimerPreset.OFF || preset == SleepTimerPreset.END_OF_CHAPTER) {
            _remainingSeconds.value = 0
            return
        }

        val totalSecs = preset.minutes * 60
        _remainingSeconds.value = totalSecs

        countdownJob = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000L)
                val next = _remainingSeconds.value - 1
                _remainingSeconds.value = next

                // Durante los últimos 20 segundos aplica un fade-out progresivo suave
                if (next in 1..20) {
                    _fadeMultiplier.value = (next / 20.0f).coerceIn(0.05f, 1.0f)
                }
            }
            _fadeMultiplier.value = 1.0f
            _activePreset.value = SleepTimerPreset.OFF
            onTimerExpiredPauseAudio?.invoke()
        }
    }

    fun consumeEndOfChapterStopIfNeeded(): Boolean {
        if (_activePreset.value == SleepTimerPreset.END_OF_CHAPTER) {
            _activePreset.value = SleepTimerPreset.OFF
            _fadeMultiplier.value = 1.0f
            return true
        }
        return false
    }
}
