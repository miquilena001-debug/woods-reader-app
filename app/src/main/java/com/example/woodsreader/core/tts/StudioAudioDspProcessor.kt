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
        label = "Estudio C\u00E1lido (Broadcast)",
        description = "Cuerpo grave tipo podcast, sin brillo met\u00E1lico y sibilancias domadas"
    ),
    NIGHT_INTIMATE(
        id = "night_intimate",
        label = "Lectura Nocturna (Suave)",
        description = "Voz sedosa y relajante con agudos atenuados para leer antes de dormir"
    ),
    CRISP_HD(
        id = "crisp_hd",
        label = "Claridad HD (Velocidad Alta)",
        description = "Articulaci\u00F3n vocal extra n\u00EDtida ideal para escuchar a 1.25x - 1.75x"
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
        postPauseMs: Int = 300,
        singleVoiceRoleShift: Boolean = true
    ): FloatArray {
        if (rawSamples.isEmpty() || sampleRate <= 0) return rawSamples

        // 1. Recortar silencios muertos extremos al inicio y al final del modelo VITS
        val trimThreshold = 0.0035f
        var startIdx = 0
        while (startIdx < rawSamples.size && abs(rawSamples[startIdx]) < trimThreshold) {
            startIdx++
        }
        var endIdx = rawSamples.lastIndex
        while (endIdx > startIdx && abs(rawSamples[endIdx]) < trimThreshold) {
            endIdx--
        }
        // Dejar un peque\u00F1o margen natural de 10ms antes y despu\u00E9s del corte
        val marginSamples = (sampleRate * 0.010f).toInt()
        startIdx = (startIdx - marginSamples).coerceAtLeast(0)
        endIdx = (endIdx + marginSamples).coerceAtMost(rawSamples.lastIndex)

        val activeLen = (endIdx - startIdx + 1).coerceAtLeast(1)
        val pauseSamples = ((postPauseMs.coerceIn(40, 1200) / 1000.0f) * sampleRate).toInt()
        val output = FloatArray(activeLen + pauseSamples)
        System.arraycopy(rawSamples, startIdx, output, 0, activeLen)

        // 2. Configurar cadena Biquad seg\u00FAn el perfil ac\u00FAstico y el rol dram\u00E1tico
        val hpFilter = BiquadFilter().apply { configureHighPass(sampleRate, 78f, 0.707f) }
        val warmthFilter = BiquadFilter()
        val deBoxFilter = BiquadFilter()
        val presenceFilter = BiquadFilter()
        val sibilanceDetector = BiquadFilter().apply { configurePeakingEq(sampleRate, 6300f, 2.2f, 12.0f) }

        val warmthGain = when (profile) {
            StudioAcousticsProfile.STUDIO_WARM -> 2.8f
            StudioAcousticsProfile.NIGHT_INTIMATE -> 3.4f
            StudioAcousticsProfile.CRISP_HD -> 1.4f
        } + if (singleVoiceRoleShift && role == VoiceActRole.NARRATOR) 0.7f else 0.0f

        val deBoxGain = when (profile) {
            StudioAcousticsProfile.STUDIO_WARM -> -2.5f
            StudioAcousticsProfile.NIGHT_INTIMATE -> -3.2f
            StudioAcousticsProfile.CRISP_HD -> -1.8f
        }

        val presenceGain = when (profile) {
            StudioAcousticsProfile.STUDIO_WARM -> 1.5f
            StudioAcousticsProfile.NIGHT_INTIMATE -> -1.8f
            StudioAcousticsProfile.CRISP_HD -> 2.8f
        } + if (singleVoiceRoleShift && role == VoiceActRole.DIALOGUE) 1.3f else 0.0f

        val warmthFreq = if (singleVoiceRoleShift && role == VoiceActRole.DIALOGUE) 165f else 132f
        val presenceFreq = if (role == VoiceActRole.SYSTEM_ALERT) 3600f else 4600f

        warmthFilter.configurePeakingEq(sampleRate, warmthFreq, 0.85f, warmthGain)
        deBoxFilter.configurePeakingEq(sampleRate, 2850f, 1.45f, deBoxGain)
        presenceFilter.configurePeakingEq(sampleRate, presenceFreq, 1.1f, presenceGain)

        // 3. Procesar EQ + De-Esser Din\u00E1mico + C\u00E1lculo RMS en una sola pasada
        var sumSquares = 0.0
        var deEssEnvelope = 0.0f
        val deEssThreshold = 0.14f

        for (i in 0 until activeLen) {
            var s = output[i]
            s = hpFilter.processSample(s)
            s = warmthFilter.processSample(s)
            s = deBoxFilter.processSample(s)
            s = presenceFilter.processSample(s)

            // De-Esser din\u00E1mico sobre sibilancias fuertes ('s', 'c', 'z')
            val sibEnergy = abs(sibilanceDetector.processSample(s))
            deEssEnvelope = 0.85f * deEssEnvelope + 0.15f * sibEnergy
            if (deEssEnvelope > deEssThreshold) {
                val reduction = (deEssThreshold / (deEssEnvelope + 0.0001f)).coerceIn(0.55f, 1.0f)
                s *= reduction
            }

            output[i] = s
            sumSquares += (s * s).toDouble()
        }

        // 4. Micro-Reflexi\u00F3n Temprana de Estudio (Cabina Ac\u00FAstica Haas 11ms a -27 dB)
        val delaySamples = (sampleRate * 0.0115f).toInt().coerceAtLeast(1)
        val roomMix = if (role == VoiceActRole.SYSTEM_ALERT) 0.085f else 0.042f
        if (activeLen > delaySamples) {
            for (i in activeLen - 1 downTo delaySamples) {
                output[i] = output[i] + output[i - delaySamples] * roomMix
            }
        }

        // 5. Normalizaci\u00F3n RMS Inteligente + Compresor/Limitador Anal\u00F3gico Soft-Knee
        val rms = sqrt(sumSquares / activeLen.toDouble()).toFloat().coerceAtLeast(0.0001f)
        val targetRms = 0.165f * gainMultiplier
        val autoMakeUpGain = (targetRms / rms).coerceIn(0.65f, 2.35f)

        val fadeInLen = (sampleRate * 0.008f).toInt().coerceIn(1, activeLen / 4 + 1)
        val fadeOutLen = (sampleRate * 0.014f).toInt().coerceIn(1, activeLen / 4 + 1)
        val fadeOutStart = (activeLen - fadeOutLen).coerceAtLeast(0)

        for (i in 0 until activeLen) {
            var s = output[i] * autoMakeUpGain

            // Fade-in cosenoidal anti-clic
            if (i < fadeInLen) {
                val env = 0.5f * (1.0f - cos(PI * i / fadeInLen).toFloat())
                s *= env
            }
            // Fade-out cosenoidal anti-clic
            if (i >= fadeOutStart) {
                val rel = i - fadeOutStart
                val env = 0.5f * (1.0f + cos(PI * rel / fadeOutLen).toFloat())
                s *= env
            }

            // Saturador / Limitador Soft-Knee suave a -0.3 dBFS
            output[i] = (0.96f * tanh(s * 1.08f)).coerceIn(-0.98f, 0.98f)
        }

        // 6. Rellenar la pausa pros\u00F3dica con Dither de Confort (-68 dBFS) para mantener activo el DAC/Bluetooth
        val ditherAmp = 0.00035f
        for (i in activeLen until output.size) {
            output[i] = (Random.nextFloat() - 0.5f) * ditherAmp
        }

        return output
    }
}
