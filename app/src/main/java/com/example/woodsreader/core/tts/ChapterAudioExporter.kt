package com.example.woodsreader.core.tts

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface AudioExportState {
    data object Idle : AudioExportState
    data class Exporting(
        val progress: Float,
        val currentChapter: Int,
        val totalChapters: Int,
        val currentSentence: Int,
        val totalSentences: Int,
        val label: String
    ) : AudioExportState
    data class Completed(val fileName: String, val sizeKb: Long) : AudioExportState
    data class Error(val message: String) : AudioExportState
}

data class ChapterTextChunk(
    val chapterTitle: String,
    val paragraphs: List<String>
)

@Singleton
class ChapterAudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sherpaModelManager: SherpaModelManager,
    private val ttsManager: TtsManager
) {
    private val _exportState = MutableStateFlow<AudioExportState>(AudioExportState.Idle)
    val exportState: StateFlow<AudioExportState> = _exportState.asStateFlow()

    @Volatile
    private var activeExportJob: Job? = null

    fun setActiveJob(job: Job) {
        activeExportJob?.cancel()
        activeExportJob = job
    }

    fun cancelExport() {
        activeExportJob?.cancel()
        activeExportJob = null
        _exportState.value = AudioExportState.Idle
    }

    fun resetState() {
        _exportState.value = AudioExportState.Idle
    }

    suspend fun exportChaptersToM4a(
        bookTitle: String,
        exportLabel: String,
        totalChaptersCount: Int,
        chapterProvider: suspend (index: Int) -> ChapterTextChunk?,
        speed: Float = 1.0f
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!sherpaModelManager.isModelReady()) {
                throw IllegalStateException("Primero descarga un modelo de voz IA en la sección de modelos Sherpa")
            }

            _exportState.value = AudioExportState.Exporting(0f, 1, totalChaptersCount, 0, 1, "Inicializando motor IA y compresor AAC...")
            TtsManager.ensureNativeLibLoaded()

            if (!ttsManager.initSherpaIfNeeded()) {
                throw IllegalStateException("No se pudo inicializar el motor Sherpa ONNX")
            }

            val tempM4aFile = File(context.cacheDir, "woods_export_${System.currentTimeMillis()}.m4a")
            val sampleRate = 22050

            try {
                encodeStreamToM4a(
                    outputFile = tempM4aFile,
                    sampleRate = sampleRate,
                    totalChapters = totalChaptersCount,
                    chapterProvider = chapterProvider,
                    speed = speed.coerceIn(0.5f, 2.5f)
                )

                val safeBook = bookTitle.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").trim().take(28).ifBlank { "Libro" }
                val safeLabel = exportLabel.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").trim().take(28).ifBlank { "Audio" }
                val fileName = "${safeBook}_${safeLabel}.m4a".replace(Regex("""\s+"""), "_")
                val fileSizeKb = (tempM4aFile.length() / 1024L).coerceAtLeast(1L)

                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.TITLE, "$bookTitle - $exportLabel")
                    put(MediaStore.Audio.Media.ARTIST, "Woods Reader IA")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WoodsReader")
                    }
                }

                val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo guardar el archivo .m4a en Music/WoodsReader")

                resolver.openOutputStream(audioUri)?.use { out ->
                    tempM4aFile.inputStream().use { input ->
                        input.copyTo(out, bufferSize = 32 * 1024)
                    }
                }

                _exportState.value = AudioExportState.Completed(fileName, fileSizeKb)
                fileName
            } finally {
                if (tempM4aFile.exists()) tempM4aFile.delete()
            }
        }.onFailure { err ->
            if (err is CancellationException) {
                _exportState.value = AudioExportState.Idle
            } else {
                _exportState.value = AudioExportState.Error(err.localizedMessage ?: "Error al exportar audio")
            }
        }
    }

    private suspend fun encodeStreamToM4a(
        outputFile: File,
        sampleRate: Int,
        totalChapters: Int,
        chapterProvider: suspend (index: Int) -> ChapterTextChunk?,
        speed: Float
    ) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 48_000) // 48 kbps AAC: voz nítida y ~20 MB por hora
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var trackIndex = -1
        var muxerStarted = false
        var totalSamplesSubmitted = 0L
        var lastPresentationTimeUs = 0L
        val bufferInfo = MediaCodec.BufferInfo()

        fun drainEncoder(endOfStream: Boolean) {
            if (endOfStream) {
                val inIdx = codec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val ptsUs = (totalSamplesSubmitted * 1_000_000L) / sampleRate
                    codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }

            while (true) {
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val encodedData = codec.getOutputBuffer(outIdx)
                        if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                            if (bufferInfo.presentationTimeUs < lastPresentationTimeUs) {
                                bufferInfo.presentationTimeUs = lastPresentationTimeUs + 100L
                            }
                            lastPresentationTimeUs = bufferInfo.presentationTimeUs
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }
        }

        fun feedSamples(samples: FloatArray) {
            var offset = 0
            while (offset < samples.size) {
                val inIdx = codec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx) ?: continue
                    inBuf.clear()
                    inBuf.order(ByteOrder.LITTLE_ENDIAN)
                    val maxShorts = minOf(samples.size - offset, inBuf.remaining() / 2)
                    for (i in 0 until maxShorts) {
                        val s = (samples[offset + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        inBuf.putShort(s)
                    }
                    val bytesWritten = maxShorts * 2
                    val ptsUs = (totalSamplesSubmitted * 1_000_000L) / sampleRate
                    codec.queueInputBuffer(inIdx, 0, bytesWritten, ptsUs, 0)
                    totalSamplesSubmitted += maxShorts
                    offset += maxShorts
                }
                drainEncoder(endOfStream = false)
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val silenceBetweenChapters = FloatArray((sampleRate * 0.80f).toInt())

            for (chapIdx in 0 until totalChapters) {
                if (!coroutineContext.isActive) throw CancellationException()
                val chunk = chapterProvider(chapIdx) ?: continue
                val sentences = chunk.paragraphs
                    .flatMap { paragraph ->
                        sherpaModelManager.splitIntoSafeClauses(paragraph, maxChars = 110)
                    }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (sentences.isEmpty()) continue

                for ((sIdx, sentence) in sentences.withIndex()) {
                    if (!coroutineContext.isActive) throw CancellationException()
                    val globalProgress = ((chapIdx.toFloat() + (sIdx.toFloat() / sentences.size.toFloat())) / totalChapters.toFloat()).coerceIn(0f, 0.99f)
                    _exportState.value = AudioExportState.Exporting(
                        progress = globalProgress,
                        currentChapter = chapIdx + 1,
                        totalChapters = totalChapters,
                        currentSentence = sIdx + 1,
                        totalSentences = sentences.size,
                        label = chunk.chapterTitle
                    )

                    val audio = ttsManager.synthesizeExpressiveStudioAudio(sentence, speed)
                    if (audio != null && audio.first.isNotEmpty()) {
                        feedSamples(audio.first)
                    }
                }
                feedSamples(silenceBetweenChapters)
            }

            drainEncoder(endOfStream = true)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer.release() } catch (_: Exception) {}
        }
    }
}
