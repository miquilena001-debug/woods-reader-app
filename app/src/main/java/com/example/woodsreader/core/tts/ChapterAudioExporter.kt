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
import com.example.woodsreader.core.system.WoodsKeepAliveManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import timber.log.Timber

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
    data class Completed(
        val fileName: String,
        val sizeKb: Long,
        val hasLyrics: Boolean = false
    ) : AudioExportState
    data class Error(val message: String) : AudioExportState
}

data class SyncedSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class ChapterTextChunk(
    val chapterTitle: String,
    val paragraphs: List<String>
)

@Singleton
class ChapterAudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sherpaModelManager: SherpaModelManager,
    private val ttsManager: TtsManager,
    private val woodsKeepAliveManager: WoodsKeepAliveManager
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

    /**
     * Limpia proactivamente archivos huérfanos o residuales (.lrc, .srt, .jpg, .txt)
     * de exportaciones anteriores para mantener la carpeta Music/WoodsReader limpia
     * conteniendo únicamente archivos de audio autónomos .m4a.
     */
    private fun cleanupLegacyExternalFiles() {
        runCatching {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "WoodsReader"
            )
            if (musicDir.exists() && musicDir.isDirectory) {
                val legacyExtensions = listOf(".lrc", ".srt", ".jpg", ".jpeg", ".png", ".txt")
                musicDir.listFiles()?.forEach { file ->
                    val nameLower = file.name.lowercase()
                    if (legacyExtensions.any { ext -> nameLower.endsWith(ext) }) {
                        file.delete()
                        Timber.d("Archivo residual eliminado: ${file.name}")
                    }
                }
            }
        }.onFailure { Timber.w(it, "Error en limpieza preventiva de archivos residuales") }
    }

    suspend fun exportChaptersToM4a(
        bookTitle: String,
        exportLabel: String,
        totalChaptersCount: Int,
        chapterProvider: suspend (index: Int) -> ChapterTextChunk?,
        speed: Float = 1.0f,
        voiceOption: SherpaVoiceOption? = null,
        includeSyncedLyrics: Boolean = true,
        coverImageFile: File? = null,
        bookAuthor: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Limpiar cualquier archivo huérfano .lrc/.srt/.jpg/.txt antes de iniciar
            cleanupLegacyExternalFiles()

            if (voiceOption != null) {
                ttsManager.switchSherpaVoice(voiceOption)
            }

            if (!sherpaModelManager.isModelReady()) {
                throw IllegalStateException("Primero descarga un modelo de voz IA en la sección de modelos Sherpa")
            }

            _exportState.value = AudioExportState.Exporting(0f, 1, totalChaptersCount, 0, 1, "Inicializando motor IA y compresor AAC...")
            TtsManager.ensureNativeLibLoaded()

            if (!ttsManager.initSherpaIfNeeded()) {
                throw IllegalStateException("No se pudo inicializar el motor Sherpa ONNX")
            }

            val tempM4aFile = File.createTempFile("woods_export_", ".m4a", context.cacheDir)
            val sampleRate = 22050

            try {
                woodsKeepAliveManager.acquireKeepAliveWakeLock()
                val syncedCues = encodeStreamToM4a(
                    outputFile = tempM4aFile,
                    sampleRate = sampleRate,
                    totalChapters = totalChaptersCount,
                    chapterProvider = chapterProvider,
                    speed = speed.coerceIn(0.5f, 2.5f)
                )
                coroutineContext.ensureActive()
                if (!tempM4aFile.isFile || tempM4aFile.length() == 0L) {
                    throw IllegalStateException("La exportación no produjo un archivo de audio válido")
                }

                val effectiveAuthor = bookAuthor.trim().ifBlank { "Woods Reader IA" }
                val lrcContent = if (includeSyncedLyrics && syncedCues.isNotEmpty()) {
                    generateLrc(syncedCues, bookTitle, effectiveAuthor)
                } else {
                    ""
                }

                // Incrustar en binario todo dentro del contenedor .M4A (Título, Autor, Álbum, Letras LRC y Portada)
                runCatching {
                    M4aMetadataEmbedder.embedAllIntoM4a(
                        m4aFile = tempM4aFile,
                        title = "$bookTitle - $exportLabel",
                        author = effectiveAuthor,
                        bookTitle = bookTitle,
                        synchronizedLyricsLrc = lrcContent,
                        coverFile = coverImageFile
                    )
                }.onFailure { Timber.w(it, "No se pudo incrustar metadatos dentro del archivo .M4A") }

                val safeBook = bookTitle.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").trim().take(28).ifBlank { "Libro" }
                val safeLabel = exportLabel.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").trim().take(28).ifBlank { "Audio" }
                val fileName = "${safeBook}_${safeLabel}.m4a".replace(Regex("""\s+"""), "_")
                val fileSizeKb = (tempM4aFile.length() / 1024L).coerceAtLeast(1L)

                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.TITLE, "$bookTitle - $exportLabel")
                    put(MediaStore.Audio.Media.ARTIST, effectiveAuthor)
                    put(MediaStore.Audio.Media.ALBUM, bookTitle)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WoodsReader")
                    }
                }

                var finalAudioUri: android.net.Uri? = null
                var published = false
                try {
                    val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("No se pudo guardar el archivo .m4a en Music/WoodsReader")
                    finalAudioUri = audioUri

                    val output = resolver.openOutputStream(audioUri)
                        ?: throw IllegalStateException("No se pudo abrir el archivo .m4a para escritura")
                    output.use { out ->
                        tempM4aFile.inputStream().use { input ->
                            input.copyTo(out, bufferSize = 32 * 1024)
                        }
                    }
                    coroutineContext.ensureActive()

                    published = true

                    // ÚNICO archivo generado: .m4a que contiene audio, portada y letras sincronizadas
                    val hasLyrics = lrcContent.isNotBlank()
                    _exportState.value = AudioExportState.Completed(fileName, fileSizeKb, hasLyrics = hasLyrics)
                    fileName
                } finally {
                    if (!published) {
                        finalAudioUri?.let { uri ->
                            runCatching {
                                if (resolver.delete(uri, null, null) == 0) {
                                    Timber.w("No se pudo eliminar la entrada parcial de MediaStore: $uri")
                                }
                            }.onFailure { Timber.e(it, "Error al eliminar una exportación parcial de MediaStore") }
                        }
                    }
                }
            } finally {
                woodsKeepAliveManager.releaseKeepAliveWakeLock()
                if (tempM4aFile.exists() && !tempM4aFile.delete()) {
                    Timber.w("No se pudo eliminar el archivo temporal de exportación")
                }
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
    ): List<SyncedSubtitleCue> {
        val syncedCues = mutableListOf<SyncedSubtitleCue>()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 48_000) // 48 kbps AAC: voz nítida y ~20 MB por hora
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        var totalSamplesSubmitted = 0L
        var lastPresentationTimeUs = 0L
        val bufferInfo = MediaCodec.BufferInfo()

        fun drainEncoder(endOfStream: Boolean) {
            val localCodec = codec ?: return
            if (endOfStream) {
                val inIdx = localCodec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val ptsUs = (totalSamplesSubmitted * 1_000_000L) / sampleRate
                    localCodec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }

            while (true) {
                val outIdx = localCodec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            val localMuxer = muxer ?: break
                            trackIndex = localMuxer.addTrack(localCodec.outputFormat)
                            localMuxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val encodedData = localCodec.getOutputBuffer(outIdx)
                        if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                            if (bufferInfo.presentationTimeUs < lastPresentationTimeUs) {
                                bufferInfo.presentationTimeUs = lastPresentationTimeUs + 100L
                            }
                            lastPresentationTimeUs = bufferInfo.presentationTimeUs
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        localCodec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }
        }

        suspend fun feedSamples(samples: FloatArray) {
            val localCodec = codec ?: return
            var offset = 0
            while (offset < samples.size) {
                coroutineContext.ensureActive()
                val inIdx = localCodec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val inBuf = localCodec.getInputBuffer(inIdx) ?: continue
                    inBuf.clear()
                    inBuf.order(ByteOrder.LITTLE_ENDIAN)
                    val maxShorts = minOf(samples.size - offset, inBuf.remaining() / 2)
                    for (i in 0 until maxShorts) {
                        val s = (samples[offset + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        inBuf.putShort(s)
                    }
                    val bytesWritten = maxShorts * 2
                    val ptsUs = (totalSamplesSubmitted * 1_000_000L) / sampleRate
                    localCodec.queueInputBuffer(inIdx, 0, bytesWritten, ptsUs, 0)
                    totalSamplesSubmitted += maxShorts
                    offset += maxShorts
                }
                drainEncoder(endOfStream = false)
            }
        }

        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
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

                    val sentenceStartMs = (totalSamplesSubmitted * 1000L) / sampleRate
                    val audio = ttsManager.synthesizeExpressiveStudioAudio(sentence, speed)
                    if (audio != null && audio.first.isNotEmpty()) {
                        feedSamples(audio.first)
                        val sentenceEndMs = (totalSamplesSubmitted * 1000L) / sampleRate
                        syncedCues.add(SyncedSubtitleCue(sentenceStartMs, sentenceEndMs, sentence))
                    }
                }
                feedSamples(silenceBetweenChapters)
            }

            if (!muxerStarted) {
                throw IllegalStateException("No se generó audio para exportar")
            }
            drainEncoder(endOfStream = true)
            return syncedCues
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun generateLrc(cues: List<SyncedSubtitleCue>, title: String, artist: String): String {
        val sb = StringBuilder()
        sb.append("[ti:").append(title).append("]\n")
        sb.append("[ar:").append(artist).append("]\n")
        sb.append("[al:").append(title).append("]\n")
        sb.append("[by:Woods Reader]\n")
        for (cue in cues) {
            val totalSec = cue.startMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hundredths = (cue.startMs % 1000) / 10
            val timeTag = String.format(java.util.Locale.US, "[%02d:%02d.%02d]", min, sec, hundredths)
            sb.append(timeTag).append(" ").append(cue.text).append("\n")
        }
        return sb.toString()
    }
}
