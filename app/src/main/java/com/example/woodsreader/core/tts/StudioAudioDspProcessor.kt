package com.example.woodsreader.core.tts

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

enum class StudioAcousticsProfile(val id: String, val label: String, val description: String) {
    STUDIO_WARM(
        id = "studio_warm",
        label = "Estudio Cálido (Broadcast)",
        description = "Cuerpo grave tipo podcast, sin brillo metálico y sibilancias domadas"
    ),
    NIGHT_INTIMATE(
        id = "night_intimate",
        label = "Lectura Nocturna (Suave)",
        description = "Voz sedosa y relajante con agudos atenuados para leer antes de dormir"
    ),
    CRISP_HD(
        id = "crisp_hd",
        label = "Claridad HD (Velocidad Alta)",
        description = "Articulación vocal extra nítida ideal para escuchar a 1.25x - 1.75x"
    );

    companion object {
        fun fromId(id: String?): StudioAcousticsProfile {
            return entries.firstOrNull { it.id == id } ?: STUDIO_WARM
        }
    }
}

object StudioAudioDspProcessor {

    private class BiquadFilter {
        var b0 = 1.0f
        var b1 = 0.0f
        var b2 = 0.0f
        var a1 = 0.0f
        var a2 = 0.0f
        var z1 = 0.0f
        var z2 = 0.0f

        fun configureHighPass(sampleRate: Int, freqHz: Float, q: Float = 0.7071f) {
            val w0 = (2.0 * PI * freqHz / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0f * q)
            val a0 = 1.0f + alpha
            b0 = ((1.0f + cosW0) * 0.5f) / a0
            b1 = -(1.0f + cosW0) / a0
            b2 = ((1.0f + cosW0) * 0.5f) / a0
            a1 = (-2.0f * cosW0) / a0
            a2 = (1.0f - alpha) / a0
        }

        fun configurePeakingEq(sampleRate: Int, freqHz: Float, q: Float, gainDb: Float) {
            val a = 10.0.pow(gainDb / 40.0).toFloat()
            val w0 = (2.0 * PI * freqHz / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0f * q)
            val a0 = 1.0f + alpha / a
            b0 = (1.0f + alpha * a) / a0
            b1 = (-2.0f * cosW0) / a0
            b2 = (1.0f - alpha * a) / a0
            a1 = (-2.0f * cosW0) / a0
            a2 = (1.0f - alpha / a) / a0
        }

        fun processSample(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    fun masterAudioSegment(
        rawSamples: FloatArray,
        sampleRate: Int,
        profile: StudioAcousticsProfile = StudioAcousticsProfile.STUDIO_WARM,
        role: VoiceActRole = VoiceActRole.NARRATOR,
        gainMultiplier: Float = 1.0f,
        postPauseMs: Int = 260,
        singleVoiceRoleShift: Boolean = true
    ): FloatArray {
        if (rawSamples.isEmpty() || sampleRate <= 0) return rawSamples

        // 1. Recorte suave de silencios muertos extremos conservando ataque natural de 14ms
        val trimThreshold = 0.0025f
        var startIdx = 0
        while (startIdx < rawSamples.size && abs(rawSamples[startIdx]) < trimThreshold) {
            startIdx++
        }
        var endIdx = rawSamples.lastIndex
        while (endIdx > startIdx && abs(rawSamples[endIdx]) < trimThreshold) {
            endIdx--
        }
        val marginSamples = (sampleRate * 0.014f).toInt()
        startIdx = (startIdx - marginSamples).coerceAtLeast(0)
        endIdx = (endIdx + marginSamples).coerceAtMost(rawSamples.lastIndex)

        val activeLen = (endIdx - startIdx + 1).coerceAtLeast(1)
        val pauseSamples = ((postPauseMs.coerceIn(60, 900) / 1000.0f) * sampleRate).toInt()
        val output = FloatArray(activeLen + pauseSamples)

        // Headroom inicial (-2.5 dB) antes de los filtros EQ para impedir saturación interna
        val preScale = 0.75f
        for (i in 0 until activeLen) {
            output[i] = rawSamples[startIdx + i] * preScale
        }

        // 2. Filtros Biquad suaves y seguros para cualquier Sample Rate (16kHz / 22.05kHz)
        val hpFilter = BiquadFilter().apply { configureHighPass(sampleRate, 75f, 0.707f) }
        val warmthFilter = BiquadFilter()
        val deBoxFilter = BiquadFilter()
        val presenceFilter = BiquadFilter()

        val warmthGain = when (profile) {
            StudioAcousticsProfile.STUDIO_WARM -> 1.6f
            StudioAcousticsProfile.NIGHT_INTIMATE -> 2.1f
            StudioAcousticsProfile.CRISP_HD -> 0.8f
        } + if (singleVoiceRoleShift && role == VoiceActRole.NARRATOR) 0.4f else 0.0f

        val deBoxGain = when (profile) {
            StudioAcousticsProfile.STUDIO_WARM -> -1.8f
            StudioAcousticsProfile.NIGHT_INTIMATE -> -2.4f
            StudioAcousticsProfile.CRISP_HD -> -1.2f
        }

        val presenceGain = when (profile) {
            StudioAcousticsProfile.STUDIO_WARM -> 0.9f
            StudioAcousticsProfile.NIGHT_INTIMATE -> -1.5f
            StudioAcousticsProfile.CRISP_HD -> 1.8f
        } + if (singleVoiceRoleShift && role == VoiceActRole.DIALOGUE) 0.7f else 0.0f

        val warmthFreq = if (singleVoiceRoleShift && role == VoiceActRole.DIALOGUE) 155f else 130f
        val safePresenceFreq = (if (role == VoiceActRole.SYSTEM_ALERT) 3400f else 4200f)
            .coerceAtMost(sampleRate * 0.38f)

        warmthFilter.configurePeakingEq(sampleRate, warmthFreq, 0.8f, warmthGain)
        deBoxFilter.configurePeakingEq(sampleRate, 2750f, 1.3f, deBoxGain)
        presenceFilter.configurePeakingEq(sampleRate, safePresenceFreq, 1.0f, presenceGain)

        // Detector de sibilancia seguro respecto a Nyquist
        val sibFreq = 5800f.coerceAtMost(sampleRate * 0.42f)
        val sibilanceDetector = BiquadFilter().apply { configurePeakingEq(sampleRate, sibFreq, 1.8f, 6.0f) }

        var sumSquares = 0.0
        var peakVal = 0.0001f
        var deEssEnvelope = 0.0f
        val deEssThreshold = 0.16f

        for (i in 0 until activeLen) {
            var s = output[i]
            s = hpFilter.processSample(s)
            s = warmthFilter.processSample(s)
            s = deBoxFilter.processSample(s)
            s = presenceFilter.processSample(s)

            val sibEnergy = abs(sibilanceDetector.processSample(s))
            deEssEnvelope = 0.88f * deEssEnvelope + 0.12f * sibEnergy
            if (deEssEnvelope > deEssThreshold) {
                val reduction = (deEssThreshold / (deEssEnvelope + 0.0001f)).coerceIn(0.68f, 1.0f)
                s *= reduction
            }

            output[i] = s
            val absS = abs(s)
            if (absS > peakVal) peakVal = absS
            sumSquares += (s * s).toDouble()
        }

        // 3. Normalización Estándar Audible (-19.5 dBFS RMS) con Protección Anti-Clipping
        val rms = sqrt(sumSquares / activeLen.toDouble()).toFloat().coerceAtLeast(0.0001f)
        val targetRms = 0.105f * gainMultiplier.coerceIn(0.85f, 1.08f)
        val desiredGain = (targetRms / rms).coerceIn(0.55f, 1.45f)
        // Nunca permitir que la ganancia supere el techo limpio de 0.82f (-1.7 dBFS)
        val maxSafeGain = (0.82f / peakVal).coerceAtMost(1.45f)
        val finalGain = minOf(desiredGain, maxSafeGain)

        val fadeInLen = (sampleRate * 0.010f).toInt().coerceIn(1, activeLen / 4 + 1)
        val fadeOutLen = (sampleRate * 0.016f).toInt().coerceIn(1, activeLen / 4 + 1)
        val fadeOutStart = (activeLen - fadeOutLen).coerceAtLeast(0)

        for (i in 0 until activeLen) {
            var s = output[i] * finalGain

            if (i < fadeInLen) {
                val env = 0.5f * (1.0f - cos(PI * i / fadeInLen).toFloat())
                s *= env
            }
            if (i >= fadeOutStart) {
                val rel = i - fadeOutStart
                val env = 0.5f * (1.0f + cos(PI * rel / fadeOutLen).toFloat())
                s *= env
            }

            // Limitador transparente con techo en +/- 0.85f (cero distorsión en DAC móvil)
            output[i] = (0.85f * tanh(s / 0.85f)).coerceIn(-0.85f, 0.85f)
        }

        // 4. Silencio limpio con dither imperceptible (-74 dBFS)
        val ditherAmp = 0.00018f
        for (i in activeLen until output.size) {
            output[i] = (Random.nextFloat() - 0.5f) * ditherAmp
        }

        return output
    }
}
