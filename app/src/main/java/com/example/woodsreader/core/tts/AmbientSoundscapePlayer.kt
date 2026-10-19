package com.example.woodsreader.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Singleton
class AmbientSoundscapePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smartSleepTimerManager: SmartSleepTimerManager
) {
    private val prefs = context.getSharedPreferences("woods_ambient_soundscape_prefs", Context.MODE_PRIVATE)
    private val studioPrefs = context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var streamJob: Job? = null

    private val _mode = MutableStateFlow(
        SoundscapeMode.fromId(
            prefs.getString("ambient_mode", null)
                ?: studioPrefs.getString("soundscape_mode", SoundscapeMode.OFF.id)
        )
    )
    val mode: StateFlow<SoundscapeMode> = _mode.asStateFlow()

    private val _volume = MutableStateFlow(
        (prefs.getFloat(
            "ambient_volume",
            studioPrefs.getFloat("soundscape_intensity", 0.35f)
        )).coerceIn(0.05f, 1.0f)
    )
    val volume: StateFlow<Float> = _volume.asStateFlow()

    @Volatile
    private var isTtsSpeaking: Boolean = false

    @Volatile
    private var isReaderVisible: Boolean = false

    fun setMode(newMode: SoundscapeMode) {
        _mode.value = newMode
        prefs.edit().putString("ambient_mode", newMode.id).apply()
        studioPrefs.edit().putString("soundscape_mode", newMode.id).apply()
        updatePlaybackState()
    }

    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0.05f, 1.0f)
        _volume.value = clamped
        prefs.edit().putFloat("ambient_volume", clamped).apply()
        studioPrefs.edit().putFloat("soundscape_intensity", clamped).apply()
    }

    fun onTtsSpeakingChanged(speaking: Boolean) {
        isTtsSpeaking = speaking
        updatePlaybackState()
    }

    fun onReaderVisibilityChanged(visible: Boolean) {
        isReaderVisible = visible
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        val shouldPlay = _mode.value != SoundscapeMode.OFF && (isReaderVisible || isTtsSpeaking)
        if (shouldPlay) {
            startStreamingIfNeeded()
        } else {
            stopStreaming()
        }
    }

    private fun startStreamingIfNeeded() {
        if (streamJob?.isActive == true) return

        streamJob = scope.launch {
            val sampleRate = 22050
            val chunkSamples = 1024 // ~46ms por bloque para respuesta inmediata
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 4)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            val pcmBuffer = ShortArray(chunkSamples)

            // Estados de síntesis acústica natural
            var b0 = 0f
            var b1 = 0f
            var b2 = 0f
            var b3 = 0f
            var b4 = 0f
            var b5 = 0f
            var b6 = 0f
            var brown1 = 0f
            var brown2 = 0f
            var lpState1 = 0f
            var lpState2 = 0f

            // Estados para gotas de lluvia y crepitar de leña con resonancia
            var dropEnv1 = 0f
            var dropPhase1 = 0.0
            var dropFreq1 = 1100.0
            var dropEnv2 = 0f
            var emberEnv = 0f
            var hearthRumbleState = 0f

            // Estados de viento de bosque
            var windPhase1 = 0.0
            var windPhase2 = 1.7
            var windFilterState = 0f

            var currentDuckGain = 1.0f
            var masterFadeIn = 0.0f

            try {
                track.play()
                while (isActive && _mode.value != SoundscapeMode.OFF && (isReaderVisible || isTtsSpeaking)) {
                    val activeMode = _mode.value
                    val userVol = _volume.value
                    val sleepFade = smartSleepTimerManager.fadeMultiplier.value

                    // Auto-Ducking suave: baja a 42% cuando el narrador TTS habla y sube a 100% en silencio
                    val targetDuck = if (isTtsSpeaking) 0.42f else 1.0f

                    for (i in 0 until chunkSamples) {
                        currentDuckGain += (targetDuck - currentDuckGain) * 0.0008f
                        if (masterFadeIn < 1.0f) {
                            masterFadeIn = (masterFadeIn + 0.00015f).coerceAtMost(1.0f)
                        }

                        // Generador Pink Noise base de Paul Kellet
                        val white = Random.nextFloat() * 2f - 1f
                        b0 = 0.99886f * b0 + white * 0.0555179f
                        b1 = 0.99332f * b1 + white * 0.0750759f
                        b2 = 0.96900f * b2 + white * 0.1538520f
                        b3 = 0.86650f * b3 + white * 0.3104856f
                        b4 = 0.55000f * b4 + white * 0.5329522f
                        b5 = -0.7616f * b5 - white * 0.0168980f
                        val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.14f
                        b6 = white * 0.115926f

                        val sample: Float = when (activeMode) {
                            SoundscapeMode.RAIN_WINDOW -> {
                                // Capa 1: Lluvia constante amortiguada contra cristal (Low-Pass ~1.6 kHz)
                                lpState1 += 0.28f * (pink - lpState1)
                                lpState2 += 0.22f * (lpState1 - lpState2)

                                // Capa 2: Gotas individuales resonantes sobre ventana
                                if (Random.nextFloat() < 0.0016f) {
                                    dropEnv1 = 0.28f + Random.nextFloat() * 0.32f
                                    dropFreq1 = 850.0 + Random.nextDouble() * 950.0
                                }
                                dropEnv1 *= 0.986f
                                dropPhase1 += (2.0 * PI * dropFreq1) / sampleRate
                                val waterDrop = sin(dropPhase1).toFloat() * dropEnv1 * 0.22f

                                // Capa 3: Salpicadura suave distante
                                if (Random.nextFloat() < 0.0025f) {
                                    dropEnv2 = 0.18f + Random.nextFloat() * 0.20f
                                }
                                dropEnv2 *= 0.991f
                                val patter = lpState1 * dropEnv2 * 0.65f

                                (lpState2 * 0.85f + waterDrop + patter) * 0.78f
                            }

                            SoundscapeMode.FIREPLACE -> {
                                // Capa 1: Calor grave de hogar (Doble integrador Browniano suave)
                                brown1 = ((brown1 + 0.028f * white) / 1.028f).coerceIn(-0.9f, 0.9f)
                                brown2 += 0.08f * (brown1 - brown2)
                                hearthRumbleState += 0.04f * (brown2 - hearthRumbleState)

                                // Capa 2: Chisporroteo orgánico de madera seca
                                if (Random.nextFloat() < 0.0011f) {
                                    emberEnv = 0.45f + Random.nextFloat() * 0.45f
                                }
                                emberEnv *= 0.968f
                                lpState1 += 0.42f * (white - lpState1)
                                val crackle = (white - lpState1) * emberEnv * 0.55f

                                (hearthRumbleState * 1.15f + crackle) * 0.75f
                            }

                            SoundscapeMode.FOREST_WIND -> {
                                // Brisa binaural modulada por dos LFOs lentos (ráfagas entre pinos)
                                windPhase1 += (2.0 * PI * 0.11) / sampleRate
                                windPhase2 += (2.0 * PI * 0.043) / sampleRate
                                if (windPhase1 > 2.0 * PI) windPhase1 -= 2.0 * PI
                                if (windPhase2 > 2.0 * PI) windPhase2 -= 2.0 * PI

                                val gust = 0.52f + 0.28f * sin(windPhase1).toFloat() + 0.20f * cos(windPhase2).toFloat()
                                val filterCoeff = (0.06f + 0.16f * gust).coerceIn(0.04f, 0.28f)
                                windFilterState += filterCoeff * (pink - windFilterState)
                                lpState2 += (filterCoeff * 0.8f) * (windFilterState - lpState2)

                                (lpState2 * (0.75f + 0.55f * gust)) * 0.88f
                            }

                            SoundscapeMode.OFF -> 0f
                        }

                        val finalFloat = (sample * userVol * currentDuckGain * sleepFade * masterFadeIn)
                            .coerceIn(-0.92f, 0.92f)
                        pcmBuffer[i] = (finalFloat * 32767f).toInt().toShort()
                    }

                    val written = track.write(pcmBuffer, 0, chunkSamples)
                    if (written < 0) break
                }
            } catch (_: Throwable) {
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
    }
}
