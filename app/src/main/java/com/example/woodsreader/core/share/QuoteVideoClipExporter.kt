package com.example.woodsreader.core.share

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.TextPaint
import com.example.woodsreader.core.tts.TtsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

internal fun videoFramePtsUs(frameIndex: Long, fps: Int): Long =
    frameIndex * 1_000_000L / fps

@Singleton
class QuoteVideoClipExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager
) {
    private val _isGeneratingVideo = MutableStateFlow(false)
    val isGeneratingVideo: StateFlow<Boolean> = _isGeneratingVideo.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private data class WordTiming(
        val word: String,
        val startSec: Float,
        val endSec: Float
    )

    suspend fun exportQuoteVideoMp4(
        quoteText: String,
        bookTitle: String,
        chapterTitle: String,
        speechSpeed: Float = 1.0f
    ): Result<File> = withContext(Dispatchers.Default) {
        if (_isGeneratingVideo.value) {
            return@withContext Result.failure(IllegalStateException("Ya se está generando un video"))
        }
        _isGeneratingVideo.value = true
        _progress.value = 0.05f

        val outDir = File(context.cacheDir, "shared_video_quotes").apply { mkdirs() }
        val outputFile = File(outDir, "woods_quote_${System.currentTimeMillis()}.mp4")
        var completedOk = false

        try {
            val cleanQuote = quoteText.trim().take(420)
            if (cleanQuote.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Texto de cita vacío"))
            }

            // 1. Sintetizar audio masterizado en estudio con Sherpa-ONNX
            val audioPair = ttsManager.synthesizeExpressiveStudioAudio(cleanQuote, speechSpeed)
                ?: return@withContext Result.failure(
                    IllegalStateException("Descarga y activa primero una voz IA Offline para generar el video-clip.")
                )

            val rawSamples = audioPair.first
            val sampleRate = audioPair.second
            _progress.value = 0.25f

            // Añadir 0.45s de margen final para que el video no corte brusco al terminar la última palabra
            val tailSamples = (sampleRate * 0.45f).toInt()
            val fullSamples = FloatArray(rawSamples.size + tailSamples)
            System.arraycopy(rawSamples, 0, fullSamples, 0, rawSamples.size)

            val totalDurationSec = (fullSamples.size.toFloat() / sampleRate.toFloat()).coerceIn(2.5f, 35.0f)
            val wordTimings = buildWordTimings(cleanQuote, rawSamples.size.toFloat() / sampleRate.toFloat())

            // 2. Configurar codificadores H.264 (720x1280 @ 24fps) y AAC + MediaMuxer
            val width = 720
            val height = 1280
            val fps = 24
            val totalVideoFrames = (totalDurationSec * fps).toInt().coerceAtLeast(fps * 2)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_200_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            val pendingVideoPackets = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
            val pendingAudioPackets = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>()

            fun startMuxerIfReady() {
                if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    muxer.start()
                    muxerStarted = true
                    for ((buf, info) in pendingVideoPackets) {
                        muxer.writeSampleData(videoTrackIndex, buf, info)
                    }
                    pendingVideoPackets.clear()
                    for ((buf, info) in pendingAudioPackets) {
                        muxer.writeSampleData(audioTrackIndex, buf, info)
                    }
                    pendingAudioPackets.clear()
                }
            }

            var videoPacketIndex = 0L

            fun drainEncoder(
                encoder: MediaCodec,
                info: MediaCodec.BufferInfo,
                isVideo: Boolean,
                endOfStream: Boolean
            ) {
                if (endOfStream && isVideo) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = encoder.outputFormat
                        if (isVideo) {
                            videoTrackIndex = muxer.addTrack(newFormat)
                        } else {
                            audioTrackIndex = muxer.addTrack(newFormat)
                        }
                        startMuxerIfReady()
                    } else if (outIdx >= 0) {
                        val encodedData = encoder.getOutputBuffer(outIdx)
                        if (encodedData != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                            if (isVideo) {
                                info.presentationTimeUs = videoFramePtsUs(videoPacketIndex, fps)
                                videoPacketIndex++
                            }
                            encodedData.position(info.offset)
                            encodedData.limit(info.offset + info.size)
                            if (muxerStarted) {
                                val track = if (isVideo) videoTrackIndex else audioTrackIndex
                                muxer.writeSampleData(track, encodedData, info)
                            } else {
                                val copy = ByteBuffer.allocateDirect(info.size)
                                copy.put(encodedData)
                                copy.flip()
                                val copyInfo = MediaCodec.BufferInfo().apply {
                                    set(0, info.size, info.presentationTimeUs, info.flags)
                                }
                                if (isVideo) pendingVideoPackets.add(copy to copyInfo)
                                else pendingAudioPackets.add(copy to copyInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            try {
                // 3. Renderizar fotogramas de video en la Surface de hardware
                for (frameIdx in 0 until totalVideoFrames) {
                    val currentSec = frameIdx.toFloat() / fps.toFloat()
                    val canvas = runCatching { inputSurface.lockHardwareCanvas() }
                        .getOrElse { inputSurface.lockCanvas(null) }

                    try {
                        drawKaraokeFrame(
                            canvas = canvas,
                            width = width,
                            height = height,
                            bookTitle = bookTitle,
                            chapterTitle = chapterTitle,
                            wordTimings = wordTimings,
                            currentSec = currentSec,
                            samples = fullSamples,
                            sampleRate = sampleRate
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    drainEncoder(videoEncoder, videoBufferInfo, isVideo = true, endOfStream = false)
                    _progress.value = 0.25f + 0.50f * (frameIdx.toFloat() / totalVideoFrames.toFloat())
                    if (frameIdx % 8 == 0) yield()
                }
                drainEncoder(videoEncoder, videoBufferInfo, isVideo = true, endOfStream = true)
                if (videoPacketIndex != totalVideoFrames.toLong()) {
                    Timber.w("QuoteVideoClipExporter: video packet count mismatch (encoded=$videoPacketIndex, expected=$totalVideoFrames)")
                }

                // 4. Codificar pista de audio PCM -> AAC
                var sampleOffset = 0
                var audioEosSent = false
                while (!audioEosSent) {
                    val inIdx = audioEncoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val maxSamplesInChunk = (inBuf.remaining() / 2).coerceAtMost(2048)
                        val count = minOf(maxSamplesInChunk, fullSamples.size - sampleOffset)

                        if (count <= 0) {
                            val ptsUs = (sampleOffset.toLong() * 1_000_000L) / sampleRate.toLong()
                            audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            audioEosSent = true
                        } else {
                            inBuf.order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until count) {
                                val pcm16 = (fullSamples[sampleOffset + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                inBuf.putShort(pcm16)
                            }
                            val ptsUs = (sampleOffset.toLong() * 1_000_000L) / sampleRate.toLong()
                            audioEncoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                            sampleOffset += count
                        }
                    }
                    drainEncoder(audioEncoder, audioBufferInfo, isVideo = false, endOfStream = false)
                }
                drainEncoder(audioEncoder, audioBufferInfo, isVideo = false, endOfStream = true)

                _progress.value = 1.0f
                completedOk = true
            } finally {
                runCatching { videoEncoder.stop(); videoEncoder.release() }
                runCatching { inputSurface.release() }
                runCatching { audioEncoder.stop(); audioEncoder.release() }
                runCatching { if (muxerStarted) muxer.stop(); muxer.release() }
            }

            Result.success(outputFile)
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            if (!completedOk && outputFile.exists()) {
                runCatching { outputFile.delete() }
            }
            _isGeneratingVideo.value = false
        }
    }

    private fun buildWordTimings(text: String, voiceDurationSec: Float): List<WordTiming> {
        val rawWords = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (rawWords.isEmpty()) return emptyList()

        val weights = FloatArray(rawWords.size) { idx ->
            val w = rawWords[idx]
            val pauseBonus = when {
                w.endsWith(".") || w.endsWith("!") || w.endsWith("?") || w.endsWith("…") -> 3.2f
                w.endsWith(",") || w.endsWith(";") || w.endsWith(":") || w.endsWith("—") -> 1.8f
                else -> 0f
            }
            w.length.coerceAtLeast(2).toFloat() + pauseBonus
        }
        val totalWeight = weights.sum().coerceAtLeast(1f)
        val result = ArrayList<WordTiming>(rawWords.size)
        var accWeight = 0f
        for (i in rawWords.indices) {
            val start = (accWeight / totalWeight) * voiceDurationSec
            accWeight += weights[i]
            val end = (accWeight / totalWeight) * voiceDurationSec
            result.add(WordTiming(rawWords[i], start, end))
        }
        return result
    }

    private fun drawKaraokeFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        bookTitle: String,
        chapterTitle: String,
        wordTimings: List<WordTiming>,
        currentSec: Float,
        samples: FloatArray,
        sampleRate: Int
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor("#06110B"), Color.parseColor("#0C1E14"), Color.parseColor("#050D08")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Tarjeta interior Emerald Glass
        val cardRect = RectF(44f, 96f, width - 44f, height - 110f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#11261B")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E6F4E")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)
        canvas.drawRoundRect(cardRect, 36f, 36f, borderPaint)

        // Cabecera del Libro
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 20f
        }
        val shortBook = if (bookTitle.length > 36) bookTitle.take(34) + "…" else bookTitle
        val shortChap = if (chapterTitle.length > 42) chapterTitle.take(40) + "…" else chapterTitle
        canvas.drawText(shortBook, 84f, 168f, titlePaint)
        canvas.drawText(shortChap, 84f, 202f, subPaint)

        // Dibujo de palabras con resaltado Karaoke en tiempo real
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (wordTimings.size > 45) 34f else 40f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val highlightBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E4E38")
        }

        val leftMargin = 84f
        val maxRight = width - 84f
        val lineHeight = wordPaint.textSize * 1.48f
        var cursorX = leftMargin
        var cursorY = 300f
        val spaceWidth = wordPaint.measureText(" ")

        for (wt in wordTimings) {
            val wWidth = wordPaint.measureText(wt.word)
            if (cursorX + wWidth > maxRight) {
                cursorX = leftMargin
                cursorY += lineHeight
                if (cursorY > height - 310f) break
            }

            val isCurrent = currentSec >= wt.startSec && currentSec <= wt.endSec + 0.08f
            val isPast = currentSec > wt.endSec + 0.08f

            if (isCurrent) {
                val pill = RectF(
                    cursorX - 8f,
                    cursorY - wordPaint.textSize + 4f,
                    cursorX + wWidth + 8f,
                    cursorY + 12f
                )
                canvas.drawRoundRect(pill, 12f, 12f, highlightBoxPaint)
                wordPaint.color = Color.parseColor("#6EE7B7")
                wordPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            } else if (isPast) {
                wordPaint.color = Color.parseColor("#F3F4F6")
                wordPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            } else {
                wordPaint.color = Color.parseColor("#6B7280")
                wordPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }

            canvas.drawText(wt.word, cursorX, cursorY, wordPaint)
            cursorX += wWidth + spaceWidth
        }

        // Visualizador de Onda Acústica (calculado con la energía RMS del instante actual)
        val centerSample = (currentSec * sampleRate).toInt().coerceIn(0, samples.lastIndex)
        val barCount = 24
        val barWidth = 12f
        val barSpacing = 10f
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val startBarX = (width - totalBarsWidth) / 2f
        val baseBarY = height - 225f

        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
        }

        for (b in 0 until barCount) {
            val offset = centerSample + (b - barCount / 2) * 180
            var energy = 0f
            if (offset in 0 until (samples.size - 120)) {
                var sum = 0f
                for (k in 0 until 120) {
                    val s = samples[offset + k]
                    sum += s * s
                }
                energy = sqrt(sum / 120f)
            }
            val barH = (12f + (energy * 260f)).coerceIn(10f, 88f)
            val left = startBarX + b * (barWidth + barSpacing)
            val rect = RectF(left, baseBarY - barH / 2f, left + barWidth, baseBarY + barH / 2f)
            canvas.drawRoundRect(rect, 6f, 6f, wavePaint)
        }

        // Sello inferior de marca
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A7F3D0")
            textSize = 21f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("🌿 Woods Reader • Studio Voice AI", width / 2f, height - 145f, footerPaint)
    }
}
