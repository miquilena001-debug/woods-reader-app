package com.example.woodsreader.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class AmbiencePreset(val displayName: String, val subtitle: String) {
    OFF("Apagado", "Sin sonido de fondo"),
    GENTLE_RAIN("Lluvia en el Bosque", "Gotas suaves constantes sobre hojas"),
    NIGHT_WIND("Brisa Nocturna", "Viento profundo y envolvente en los pinos"),
    DEEP_FOCUS("Concentración Profunda", "Ruido marrón cálido para aislar distracciones")
}

@Singleton
class ForestAmbienceEngine @Inject constructor() {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null
    @Volatile
    private var audioTrack: AudioTrack? = null

    private val _activePreset = MutableStateFlow(AmbiencePreset.OFF)
    val activePreset: StateFlow<AmbiencePreset> = _activePreset.asStateFlow()

    private val _volume = MutableStateFlow(0.22f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    @Volatile
    private var sleepAttenuationFactor: Float = 1.0f

    fun setPreset(preset: AmbiencePreset) {
        if (_activePreset.value == preset) return
        _activePreset.value = preset
        if (preset == AmbiencePreset.OFF) {
            stop()
        } else if (playbackJob?.isActive != true) {
            startLoop()
        }
    }

    fun cycleNextPreset(): AmbiencePreset {
        val entries = AmbiencePreset.entries
        val nextIndex = (entries.indexOf(_activePreset.value) + 1) % entries.size
        val next = entries[nextIndex]
        setPreset(next)
        return next
    }

    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0.02f, 0.80f)
        _volume.value = clamped
        applyEffectiveVolume()
    }

    /**
     * Permite que el SleepTimerManager atenúe suavemente el ambiente en los últimos 20s.
     */
    fun setSleepAttenuation(factor: Float) {
        sleepAttenuationFactor = factor.coerceIn(0f, 1f)
        applyEffectiveVolume()
    }

    private fun applyEffectiveVolume() {
        val effective = (_volume.value * sleepAttenuationFactor).coerceIn(0f, 1f)
        try {
            audioTrack?.setVolume(effective)
        } catch (_: Exception) {}
    }

    private fun startLoop() {
        playbackJob?.cancel()
        sleepAttenuationFactor = 1.0f
        playbackJob = engineScope.launch {
            val sampleRate = 24000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(4096)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                null
            } ?: return@launch

            audioTrack = track
            applyEffectiveVolume()

            try {
                track.play()
                val bufferSize = 2048
                val buffer = FloatArray(bufferSize)

                var brownState = 0.0f
                var lowPassState = 0.0f
                var lfoPhase = 0.0

                while (isActive && _activePreset.value != AmbiencePreset.OFF) {
                    val preset = _activePreset.value
                    for (i in 0 until bufferSize) {
                        val white = Random.nextFloat() * 2f - 1f
                        brownState = ((brownState + (0.02f * white)) / 1.02f).coerceIn(-1f, 1f)

                        val sample = when (preset) {
                            AmbiencePreset.GENTLE_RAIN -> {
                                // Combinación de lluvia suave filtrada con micro-gotas
                                lowPassState = lowPassState * 0.82f + white * 0.18f
                                val droplet = if (Random.nextFloat() > 0.996f) white * 0.35f else 0f
                                (lowPassState * 0.45f + brownState * 0.40f + droplet) * 0.35f
                            }
                            AmbiencePreset.NIGHT_WIND -> {
                                // Viento del bosque modulado con oscilador de baja frecuencia (LFO)
                                lfoPhase += (2.0 * PI * 0.18) / sampleRate
                                if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
                                val windMod = (0.55f + 0.45f * sin(lfoPhase).toFloat())
                                lowPassState = lowPassState * 0.93f + brownState * 0.07f
                                lowPassState * windMod * 1.4f
                            }
                            AmbiencePreset.DEEP_FOCUS -> {
                                // Ruido marrón puro suavizado para máxima concentración de lectura
                                lowPassState = lowPassState * 0.90f + brownState * 0.10f
                                lowPassState * 1.25f
                            }
                            AmbiencePreset.OFF -> 0f
                        }
                        buffer[i] = sample.coerceIn(-0.95f, 0.95f)
                    }

                    val written = track.write(buffer, 0, bufferSize, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.pause()
                    track.flush()
                    track.stop()
                } catch (_: Exception) {}
                try {
                    track.release()
                } catch (_: Exception) {}
                if (audioTrack === track) {
                    audioTrack = null
                }
            }
        }
    }

    fun stop() {
        _activePreset.value = AmbiencePreset.OFF
        playbackJob?.cancel()
        playbackJob = null
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            try {
                track.pause()
                track.flush()
                track.stop()
            } catch (_: Exception) {}
            try {
                track.release()
            } catch (_: Exception) {}
        }
    }
}