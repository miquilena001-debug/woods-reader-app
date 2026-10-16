package com.example.woodsreader.core.tts

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface AudioExportState {
    data object Idle : AudioExportState
    data class Exporting(val progress: Float, val currentSentence: Int, val totalSentences: Int) : AudioExportState
    data class Completed(val fileName: String) : AudioExportState
    data class Error(val message: String) : AudioExportState
}

@Singleton
class ChapterAudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sherpaModelManager: SherpaModelManager
) {
    private val _exportState = MutableStateFlow<AudioExportState>(AudioExportState.Idle)
    val exportState: StateFlow<AudioExportState> = _exportState.asStateFlow()

    fun resetState() {
        _exportState.value = AudioExportState.Idle
    }

    suspend fun exportTextToWav(
        bookTitle: String,
        chapterTitle: String,
        paragraphs: List<String>,
        speed: Float = 1.0f
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!sherpaModelManager.isModelReady()) {
                throw IllegalStateException("Primero debes descargar un modelo de voz Sherpa ONNX")
            }

            val sentences = paragraphs
                .flatMap { it.split(Regex("""(?<=[.!?…])\s+""")) }
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (sentences.isEmpty()) {
                throw IllegalArgumentException("El capítulo no contiene texto integrable para exportar")
            }

            _exportState.value = AudioExportState.Exporting(0f, 0, sentences.size)
            TtsManager.ensureNativeLibLoaded()

            val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
                model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
                    vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                        model = sherpaModelManager.onnxFile.absolutePath,
                        lexicon = "",
                        tokens = sherpaModelManager.tokensFile.absolutePath,
                        dataDir = sherpaModelManager.espeakDir.absolutePath,
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f
                    ),
                    numThreads = 4,
                    debug = false
                )
            )

            val tts = com.k2fsa.sherpa.onnx.OfflineTts(null, config)
            val tempPcmFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pcm")
            var sampleRate = 22050
            var totalSamples = 0L

            try {
                BufferedOutputStream(FileOutputStream(tempPcmFile)).use { pcmOut ->
                    val shortBuffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)

                    for ((idx, sentence) in sentences.withIndex()) {
                        if (!coroutineContext.isActive) {
                            throw kotlinx.coroutines.CancellationException("Exportación cancelada")
                        }
                        _exportState.value = AudioExportState.Exporting(
                            progress = idx.toFloat() / sentences.size.toFloat(),
                            currentSentence = idx + 1,
                            totalSentences = sentences.size
                        )

                        val audio = tts.generate(text = sentence, sid = 0, speed = speed.coerceIn(0.5f, 2.5f))
                        if (audio.sampleRate > 0) sampleRate = audio.sampleRate

                        for (sample in audio.samples) {
                            val pcm16 = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            if (shortBuffer.remaining() < 2) {
                                pcmOut.write(shortBuffer.array(), 0, shortBuffer.position())
                                shortBuffer.clear()
                            }
                            shortBuffer.putShort(pcm16)
                            totalSamples++
                        }

                        // Pausa natural de 250ms entre oraciones
                        val silenceSamples = (sampleRate * 0.25f).toInt()
                        repeat(silenceSamples) {
                            if (shortBuffer.remaining() < 2) {
                                pcmOut.write(shortBuffer.array(), 0, shortBuffer.position())
                                shortBuffer.clear()
                            }
                            shortBuffer.putShort(0)
                            totalSamples++
                        }
                    }

                    if (shortBuffer.position() > 0) {
                        pcmOut.write(shortBuffer.array(), 0, shortBuffer.position())
                    }
                    pcmOut.flush()
                }

                val safeBook = bookTitle.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").trim().take(30).ifBlank { "Libro" }
                val safeChap = chapterTitle.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").trim().take(30).ifBlank { "Capitulo" }
                val fileName = "${safeBook}_${safeChap}.wav".replace(" ", "_")

                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.TITLE, "$bookTitle - $chapterTitle")
                    put(MediaStore.Audio.Media.ARTIST, "Woods Reader TTS")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WoodsReader")
                    }
                }

                val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear el archivo de audio en Music/WoodsReader")

                resolver.openOutputStream(audioUri)?.use { wavOut ->
                    writeWavHeader(wavOut, totalSamples * 2L, sampleRate, 1, 16)
                    tempPcmFile.inputStream().use { pcmIn ->
                        pcmIn.copyTo(wavOut, bufferSize = 32 * 1024)
                    }
                }

                _exportState.value = AudioExportState.Completed(fileName)
                fileName
            } finally {
                try { tts.release() } catch (_: Exception) {}
                if (tempPcmFile.exists()) tempPcmFile.delete()
            }
        }.onFailure { err ->
            _exportState.value = AudioExportState.Error(err.localizedMessage ?: "Error al exportar audio")
        }
    }

    private fun writeWavHeader(
        out: OutputStream,
        pcmDataBytes: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val totalDataLen = pcmDataBytes + 36L

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size para PCM
            putShort(1) // AudioFormat 1 = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataBytes.toInt())
        }
        out.write(header.array())
    }
}
