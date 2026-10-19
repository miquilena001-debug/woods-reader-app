package com.example.woodsreader.core.tts

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class SoundscapeMode(
    val id: String,
    val label: String,
    val iconEmoji: String,
    val subtitle: String
) {
    OFF("off", "Sin Ambiente", "🔇", "Solo voz de estudio pura"),
    RAIN_WINDOW("rain", "Lluvia Nocturna", "🌧️", "Gotas suaves sobre cristal con auto-ducking"),
    FIREPLACE("fire", "Chimenea Cálida", "🔥", "Hogar de leña suave y envolvente"),
    FOREST_WIND("forest", "Bosque Sereno", "🌲", "Brisa suave entre hojas de pinos");

    companion object {
        fun fromId(id: String?): SoundscapeMode {
            return entries.firstOrNull { it.id == id } ?: OFF
        }
    }
}

class AmbientSoundscapeEngine {

    // Estados del filtro Pink Noise de Paul Kellet
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    // Estado de filtro Browniano y modulación LFO
    private var brownState = 0f
    private var lfoPhase = 0.0
    private var crackleDecay = 0f
    private var duckingEnvelope = 1.0f

    fun mixWithVoiceInPlace(
        voiceBuffer: FloatArray,
        sampleRate: Int,
        mode: SoundscapeMode,
        ambientIntensity: Float = 0.18f
    ) {
        if (mode == SoundscapeMode.OFF || voiceBuffer.isEmpty() || sampleRate <= 0) return

        val clampedIntensity = ambientIntensity.coerceIn(0.02f, 0.45f)
        val lfoIncrement = (2.0 * PI * 0.14) / sampleRate.toDouble()

        for (i in voiceBuffer.indices) {
            val voiceSample = voiceBuffer[i]
            val voiceEnergy = abs(voiceSample)

            // Auto-Ducking: cuando el narrador habla (voiceEnergy > 0.02), el ambiente baja al 42%
            val targetDuck = if (voiceEnergy > 0.025f) 0.42f else 1.0f
            val attackRelease = if (targetDuck < duckingEnvelope) 0.0025f else 0.00018f
            duckingEnvelope += (targetDuck - duckingEnvelope) * attackRelease

            val rawAmbient = when (mode) {
                SoundscapeMode.RAIN_WINDOW -> nextRainSample()
                SoundscapeMode.FIREPLACE -> nextFireplaceSample()
                SoundscapeMode.FOREST_WIND -> nextForestWindSample(lfoIncrement)
                SoundscapeMode.OFF -> 0f
            }

            val mixed = voiceSample + (rawAmbient * clampedIntensity * duckingEnvelope)
            voiceBuffer[i] = mixed.coerceIn(-0.98f, 0.98f)
        }
    }

    private fun nextPinkNoise(): Float {
        val white = Random.nextFloat() * 2f - 1f
        b0 = 0.99886f * b0 + white * 0.0555179f
        b1 = 0.99332f * b1 + white * 0.0750759f
        b2 = 0.96900f * b2 + white * 0.1538520f
        b3 = 0.86650f * b3 + white * 0.3104856f
        b4 = 0.55000f * b4 + white * 0.5329522f
        b5 = -0.7616f * b5 - white * 0.0168980f
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
        b6 = white * 0.115926f
        return (pink * 0.045f).coerceIn(-0.5f, 0.5f)
    }

    private fun nextRainSample(): Float {
        val pink = nextPinkNoise()
        // Micro-gotas suaves aleatorias amortiguadas
        if (Random.nextFloat() < 0.0008f) {
            crackleDecay = 0.12f * (0.5f + Random.nextFloat() * 0.5f)
        }
        crackleDecay *= 0.965f
        val droplet = (Random.nextFloat() * 2f - 1f) * crackleDecay
        return (pink * 0.65f + droplet * 0.35f)
    }

    private fun nextFireplaceSample(): Float {
        val white = Random.nextFloat() * 2f - 1f
        brownState = ((brownState + 0.02f * white) / 1.02f).coerceIn(-0.6f, 0.6f)
        // Crepitar cálido ocasional de madera
        if (Random.nextFloat() < 0.00045f) {
            crackleDecay = 0.22f
        }
        crackleDecay *= 0.94f
        val emberPop = white * crackleDecay
        return (brownState * 0.85f + emberPop * 0.28f)
    }

    private fun nextForestWindSample(lfoIncrement: Double): Float {
        lfoPhase += lfoIncrement
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        val windSwell = (0.55f + 0.45f * sin(lfoPhase).toFloat())
        return nextPinkNoise() * windSwell * 0.82f
    }
}
