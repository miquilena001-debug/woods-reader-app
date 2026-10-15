package com.example.woodsreader.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.WorkerThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import android.media.AudioFormat
import android.media.AudioTrack
import timber.log.Timber
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import android.content.res.AssetManager
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


private const val TAG = "TtsManager"

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsRuleDao: com.example.woodsreader.data.database.TtsRuleDao,
    private val userPreferencesRepository: com.example.woodsreader.data.datastore.UserPreferencesRepository,
    private val sherpaModelManager: SherpaModelManager
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    @Volatile
    private var offlineTts: com.k2fsa.sherpa.onnx.OfflineTts? = null
    private val sherpaMutex = Mutex()
    @Volatile
    private var activeAudioTrack: AudioTrack? = null
    private val totalFramesWritten = AtomicLong(0L)
    @Volatile
    private var isSpeakingCancelled = false
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel = _isLoadingModel.asStateFlow()

    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var basePitch: Float = 1.0f
    private var baseRate: Float = 1.0f
    @Volatile
    private var currentBookId: String? = null

    fun setCurrentBookId(bookId: String?) {
        currentBookId = bookId
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Estructuras de apoyo para el Orquestador Contextual Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private enum class ChunkType { NARRATOR, DIALOGUE, PAUSE }
    
    private data class SpeechChunk(
        val text: String,
        val type: ChunkType,
        val delayMs: Long = 0L
    )
    
    private data class SpeechSegment(
        val text: String,
        val pauseMs: Long = 0L,
        val isExclamation: Boolean = false,
        val isQuestion: Boolean = false
    )
    
    private data class PlaybackItem(
        val text: String,
        val type: ChunkType,
        val pauseMs: Long,
        val isExclamation: Boolean = false,
        val isQuestion: Boolean = false,
        val charOffset: Int = 0
    )

    private val wordExclusionEngine = com.example.woodsreader.core.pipeline.WordExclusionEngine()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stop() // Parar inmediatamente si perdemos el foco
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setRate(0.8f * baseRate) // Reducir velocidad si el sistema pide "ducking"
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                setRate(baseRate) // Restaurar velocidad original
            }
        }
    }

    suspend fun initialize(locale: Locale = Locale.getDefault(), voiceName: String? = null): Boolean {
        if (isReady && tts != null) {
            if (voiceName != null) setVoice(voiceName)
            return true
        }
        
        return suspendCancellableCoroutine { cont ->
            tts = TextToSpeech(context) { status ->
                isReady = status == TextToSpeech.SUCCESS
                if (isReady) {
                    tts?.setLanguage(locale)
                    if (voiceName != null) setVoice(voiceName)
                }
                cont.resume(isReady) { }
            }
            cont.invokeOnCancellation {
                tts?.stop()
                tts?.shutdown()
                tts = null
                isReady = false
            }
        }
    }

    suspend fun speak(text: String, onRangeUpdate: ((IntRange) -> Unit)? = null) {
        val useSherpa = try {
            userPreferencesRepository.userPreferencesFlow.first().useSherpaTts
        } catch (_: Exception) {
            false
        }

        if (useSherpa) {
            if (initSherpaIfNeeded()) {
                speakWithSherpa(text, onRangeUpdate)
                return
            }
        }

        if (!isReady || tts == null) return

        
        val activeRules = try {
            ttsRuleDao.getActiveRulesForBook(currentBookId)
        } catch (_: Exception) {
            emptyList()
        }
        
        val exclusionRules = activeRules.map {
            com.example.woodsreader.core.pipeline.WordExclusionRule(
                id = it.id.toString(),
                target = it.pattern,
                replacement = it.replacement,
                isRegex = it.isRegex
            )
        }
        val processedText = wordExclusionEngine.filterText(text, exclusionRules)
        
        // Ã¢â€â‚¬Ã¢â€â‚¬ SegmentaciÃƒÂ³n y orquestaciÃƒÂ³n contextual Ã¢â€â‚¬Ã¢â€â‚¬
        val items = parseTextToPlaybackItems(processedText)
        var dialogueCount = 0
        
        isSpeakingCancelled = false
        requestAudioFocus()
        try {
            for (item in items) {
                // Verificar cancelación de corrutinas en cada paso del bucle
                if (isSpeakingCancelled || !currentCoroutineContext().isActive || !isReady || tts == null) break
                
                // Si es un item de pausa pura, aplicar delay y continuar
                if (item.type == ChunkType.PAUSE || item.text.isBlank()) {
                    kotlinx.coroutines.delay(item.pauseMs)
                    continue
                }
                
                // Ajustar entonaciÃƒÂ³n y velocidad dinÃƒÂ¡micamente segÃƒÂºn el rol
                when (item.type) {
                    ChunkType.DIALOGUE -> {
                        tts?.setPitch(basePitch)
                        tts?.setSpeechRate(baseRate * 0.95f)
                    }
                    ChunkType.NARRATOR -> {
                        tts?.setPitch(basePitch)
                        tts?.setSpeechRate(baseRate)
                    }
                    else -> {}
                }
                
                // Refuerzo especial para signos de exclamaciÃƒÂ³n
                if (item.isExclamation) {
                    tts?.setPitch(basePitch * 1.1f)
                }
                
                val id = "utterance_${System.currentTimeMillis()}"
                
                suspendCancellableCoroutine<Unit> { cont ->
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (cont.isActive) cont.resume(Unit) { }
                        }
                        override fun onError(utteranceId: String?) {
                            if (cont.isActive) cont.resume(Unit) { }
                        }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (cont.isActive) {
                                if (isSpeakingCancelled) {
                                    cont.cancel()
                                } else {
                                    cont.resume(Unit) { }
                                }
                            }
                        }
                    })
                    
                    tts?.speak(item.text, TextToSpeech.QUEUE_FLUSH, null, id)
                    
                    cont.invokeOnCancellation {
                        stop()
                    }
                }
                
                if (isSpeakingCancelled || !currentCoroutineContext().isActive) break
                
                // Restaurar los valores por defecto del lector
                tts?.setPitch(basePitch)
                tts?.setSpeechRate(baseRate)
                
                // Retraso pos-oraciÃƒÂ³n para pausas naturales
                if (item.pauseMs > 0) {
                    kotlinx.coroutines.delay(item.pauseMs)
                }
            }
        } finally {
            abandonAudioFocus()
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ LÃƒÂ³gica de Parseo y TokenizaciÃƒÂ³n Contextual Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    
    private fun splitIntoChunks(text: String): List<SpeechChunk> {
        val chunks = mutableListOf<SpeechChunk>()
        val currentText = StringBuilder()
        var inDialogue = false
        var dialogueStartedByDash = false
        var i = 0

        fun flushCurrent() {
            val content = currentText.toString().trim()
            if (content.isNotEmpty()) {
                chunks.add(SpeechChunk(content, if (inDialogue) ChunkType.DIALOGUE else ChunkType.NARRATOR))
                currentText.clear()
            }
        }

        while (i < text.length) {
            val char = text[i]
            when (char) {
                '\n', '\r' -> {
                    flushCurrent()
                    // En español un salto de párrafo cierra el turno de diálogo abierto por raya o comillas
                    inDialogue = false
                    dialogueStartedByDash = false
                    chunks.add(SpeechChunk("", ChunkType.PAUSE, 350L))
                }
                // Comillas de apertura explícitas: siempre inician diálogo
                '\u00AB', '\u201C' -> {
                    flushCurrent()
                    inDialogue = true
                    dialogueStartedByDash = false
                    chunks.add(SpeechChunk("", ChunkType.PAUSE, 180L))
                }
                // Comillas de cierre explícitas: siempre cierran diálogo
                '\u00BB', '\u201D' -> {
                    flushCurrent()
                    inDialogue = false
                    dialogueStartedByDash = false
                    chunks.add(SpeechChunk("", ChunkType.PAUSE, 180L))
                }
                // Comilla recta doble: alterna estado de diálogo dentro del mismo párrafo
                '"' -> {
                    flushCurrent()
                    inDialogue = !inDialogue
                    dialogueStartedByDash = false
                    chunks.add(SpeechChunk("", ChunkType.PAUSE, 180L))
                }
                // Raya de diálogo (em-dash — o en-dash –)
                '\u2014', '\u2013' -> {
                    val prevChar = if (i > 0) text[i - 1] else '\n'
                    val nextChar = if (i + 1 < text.length) text[i + 1] else ' '
                    val isEndOfClauseDash = nextChar == '.' || nextChar == ',' || nextChar == ';' ||
                            nextChar == ':' || nextChar == '!' || nextChar == '?'

                    flushCurrent()
                    if (prevChar == '\n' || prevChar == '\r' || i == 0) {
                        // Raya al inicio de párrafo: inicia diálogo
                        inDialogue = true
                        dialogueStartedByDash = true
                    } else if (dialogueStartedByDash && isEndOfClauseDash) {
                        // Raya que cierra un inciso del narrador (ej. "—dijo Juan—. ¡Vamos!")
                        inDialogue = true
                    } else {
                        inDialogue = !inDialogue
                    }
                    chunks.add(SpeechChunk("", ChunkType.PAUSE, 200L))
                }
                else -> {
                    currentText.append(char)
                }
            }
            i++
        }

        flushCurrent()
        return chunks
    }

    private fun splitIntoSegments(text: String): List<SpeechSegment> {
        val segments = mutableListOf<SpeechSegment>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val char = text[i]
            current.append(char)
            
            // DetecciÃƒÂ³n de salto de pÃƒÂ¡rrafo (nueva lÃƒÂ­nea)
            if (char == '\n' || char == '\r') {
                val segText = current.substring(0, current.length - 1).trim()
                if (segText.isNotEmpty()) {
                    segments.add(SpeechSegment(segText, 800L))
                }
                current.clear()
                while (i + 1 < text.length && (text[i + 1] == '\n' || text[i + 1] == '\r')) {
                    i++
                }
                i++
                continue
            }
            
            // DetecciÃƒÂ³n especial de puntos suspensivos
            if (char == '.' && i + 2 < text.length && text[i+1] == '.' && text[i+2] == '.') {
                current.append("..")
                segments.add(SpeechSegment(current.toString().trim(), 600L))
                current.clear()
                i += 3
                continue
            }
            
            if (char == '.' || char == '?' || char == '!' || char == ',' || char == ';' || char == ':') {
                val pause = when (char) {
                    '.', '?', '!' -> 400L
                    ',', ';', ':' -> 180L
                    else -> 0L
                }
                segments.add(
                    SpeechSegment(
                        text = current.toString().trim(),
                        pauseMs = pause,
                        isExclamation = char == '!',
                        isQuestion = char == '?'
                    )
                )
                current.clear()
            }
            i++
        }
        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) {
            segments.add(SpeechSegment(remaining, 0L))
        }
        return segments
    }

    private fun parseTextToPlaybackItems(text: String): List<PlaybackItem> {
        val chunks = splitIntoChunks(text)
        val playbackItems = mutableListOf<PlaybackItem>()
        
        for (chunk in chunks) {
            if (chunk.type == ChunkType.PAUSE) {
                playbackItems.add(PlaybackItem("", ChunkType.PAUSE, chunk.delayMs))
                continue
            }
            
            val segments = splitIntoSegments(chunk.text)
            for (seg in segments) {
                if (seg.text.isNotEmpty()) {
                    playbackItems.add(
                        PlaybackItem(
                            text = seg.text,
                            type = chunk.type,
                            pauseMs = seg.pauseMs,
                            isExclamation = seg.isExclamation,
                            isQuestion = seg.isQuestion
                        )
                    )
                }
            }
        }
        return playbackItems
    }

    private fun splitIntoSegmentsForSherpa(text: String): List<SpeechSegment> {
        val segments = mutableListOf<SpeechSegment>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val char = text[i]
            current.append(char)
            
            // DetecciÃƒÂ³n de salto de pÃƒÂ¡rrafo (nueva lÃƒÂ­nea)
            if (char == '\n' || char == '\r') {
                val segText = current.substring(0, current.length - 1).trim()
                if (segText.isNotEmpty()) {
                    segments.add(SpeechSegment(segText, 800L))
                }
                current.clear()
                while (i + 1 < text.length && (text[i + 1] == '\n' || text[i + 1] == '\r')) {
                    i++
                }
                i++
                continue
            }

            // DetecciÃƒÂ³n especial de puntos suspensivos
            if (char == '.' && i + 2 < text.length && text[i+1] == '.' && text[i+2] == '.') {
                current.append("..")
                segments.add(SpeechSegment(current.toString().trim(), 350L))
                current.clear()
                i += 3
                continue
            }
            
            // Para Sherpa, dividimos por lÃƒÂ­mites de oraciÃƒÂ³n: punto, interrogaciÃƒÂ³n, exclamaciÃƒÂ³n
            if (char == '.' || char == '?' || char == '!') {
                segments.add(
                    SpeechSegment(
                        text = current.toString().trim(),
                        pauseMs = 350L,
                        isExclamation = char == '!',
                        isQuestion = char == '?'
                    )
                )
                current.clear()
            }
            i++
        }
        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) {
            segments.add(SpeechSegment(remaining, 0L))
        }
        return segments
    }

    private fun parseTextToPlaybackItemsForSherpa(text: String): List<PlaybackItem> {
        val chunks = splitIntoChunks(text)
        val playbackItems = mutableListOf<PlaybackItem>()
        var currentSearchIndex = 0
        
        for (chunk in chunks) {
            if (chunk.type == ChunkType.PAUSE) {
                playbackItems.add(PlaybackItem("", ChunkType.PAUSE, chunk.delayMs))
                continue
            }
            
            val segments = splitIntoSegmentsForSherpa(chunk.text)
            for (seg in segments) {
                if (seg.text.isNotEmpty()) {
                    val foundIndex = text.indexOf(seg.text, currentSearchIndex)
                    val offset = if (foundIndex != -1) {
                        currentSearchIndex = foundIndex + seg.text.length
                        foundIndex
                    } else {
                        currentSearchIndex
                    }
                    
                    playbackItems.add(
                        PlaybackItem(
                            text = seg.text,
                            type = chunk.type,
                            pauseMs = seg.pauseMs,
                            isExclamation = seg.isExclamation,
                            isQuestion = seg.isQuestion,
                            charOffset = offset
                        )
                    )
                }
            }
        }
        return playbackItems
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ GestiÃƒÂ³n del Audio Focus Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val track = activeAudioTrack
        activeAudioTrack = null
        if (track != null) {
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
            } catch (_: Exception) {
            } finally {
                try { track.release() } catch (_: Exception) {}
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    fun stop() {
        isSpeakingCancelled = true
        tts?.stop()
        abandonAudioFocus()
    }

    fun shutdown() {
        isSpeakingCancelled = true
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        abandonAudioFocus()
        try {
            offlineTts?.release()
        } catch (_: Exception) {}
        offlineTts = null
        try {
            ttsScope.coroutineContext.cancelChildren()
        } catch (_: Exception) {}
    }


    fun setRate(rate: Float) { 
        baseRate = rate
        if (isReady) tts?.setSpeechRate(rate) 
    }
    
    fun setPitch(pitch: Float) { 
        basePitch = pitch
        if (isReady) tts?.setPitch(pitch) 
    }

    fun getAvailableVoices(): List<String> {
        if (!isReady || tts == null) return emptyList()
        return try {
            val currentLanguage = tts?.voice?.locale
            tts?.voices?.filter { voice ->
                currentLanguage == null || voice.locale.language == currentLanguage.language
            }?.map { it.name } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setVoice(voiceName: String): Boolean {
        if (!isReady || tts == null) return false
        return try {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun copyAssetsFolder(assetManager: AssetManager, assetDirPath: String, targetDirPath: String) {
        val assets = try {
            assetManager.list(assetDirPath) ?: emptyArray()
        } catch (e: IOException) {
            emptyArray()
        }

        if (assets.isEmpty()) {
            copyAssetFile(assetManager, assetDirPath, targetDirPath)
        } else {
            val targetDir = File(targetDirPath)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            for (asset in assets) {
                val subAssetPath = if (assetDirPath.isEmpty()) asset else "$assetDirPath/$asset"
                val subTargetDirPath = "$targetDirPath/$asset"
                copyAssetsFolder(assetManager, subAssetPath, subTargetDirPath)
            }
        }
    }

    private fun copyAssetFile(assetManager: AssetManager, assetFilePath: String, targetFilePath: String) {
        try {
            assetManager.open(assetFilePath).use { inputStream ->
                File(targetFilePath).outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Timber.w("Could not copy asset as file: $assetFilePath (might be a directory or missing file)")
        }
    }

    suspend fun resetSherpa() = sherpaMutex.withLock {
        try {
            offlineTts?.release()
        } catch (e: Exception) {
            Timber.w(e, "Error releasing OfflineTts")
        }
        offlineTts = null
    }

    suspend fun initSherpaIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (offlineTts != null) return@withContext true
        sherpaMutex.withLock {
            if (offlineTts != null) return@withLock true
            try {
                Timber.d("Initializing Sherpa ONNX...")
                _isLoadingModel.value = true
                ensureNativeLibLoaded()

                if (sherpaModelManager.isModelReady()) {
                    Timber.d("Loading Sherpa ONNX from disk: ${sherpaModelManager.modelDir.absolutePath}")
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
                    offlineTts = com.k2fsa.sherpa.onnx.OfflineTts(null, config)
                    Timber.i("Sherpa ONNX initialized successfully from disk")
                    return@withLock true
                }

                // Fallback a assets si el modelo todavía estuviera en el APK
                try {
                    val espeakDir = File(context.filesDir, "espeak-ng-data")
                    if (!espeakDir.exists() || espeakDir.list()?.isEmpty() == true) {
                        Timber.d("Copying espeak-ng-data from assets to ${espeakDir.absolutePath}...")
                        copyAssetsFolder(
                            context.assets,
                            "tts/vits-piper-es_ES-davefx-medium/espeak-ng-data",
                            espeakDir.absolutePath
                        )
                    }

                    val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
                        model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
                            vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                                model = "tts/vits-piper-es_ES-davefx-medium/es_ES-davefx-medium.onnx",
                                lexicon = "",
                                tokens = "tts/vits-piper-es_ES-davefx-medium/tokens.txt",
                                dataDir = espeakDir.absolutePath,
                                noiseScale = 0.667f,
                                noiseScaleW = 0.8f,
                                lengthScale = 1.0f
                            ),
                            numThreads = 4,
                            debug = false
                        )
                    )
                    offlineTts = com.k2fsa.sherpa.onnx.OfflineTts(context.assets, config)
                    Timber.i("Sherpa ONNX initialized from assets fallback")
                    true
                } catch (assetErr: Exception) {
                    Timber.w(assetErr, "Modelo no encontrado en assets ni en disco")
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Sherpa ONNX")
                false
            } finally {
                _isLoadingModel.value = false
            }
        }
    }

    companion object {
        @Volatile
        private var nativeLibLoaded = false

        fun ensureNativeLibLoaded() {
            if (!nativeLibLoaded) {
                synchronized(this) {
                    if (!nativeLibLoaded) {
                        try {
                            System.loadLibrary("sherpa-onnx-jni")
                            nativeLibLoaded = true
                            Timber.d("Sherpa ONNX native library loaded on demand")
                        } catch (e: UnsatisfiedLinkError) {
                            Timber.w("Could not load sherpa-onnx-jni manually, relying on auto-load")
                        }
                    }
                }
            }
        }
    }

    private fun getOrCreateAudioTrack(sampleRate: Int): AudioTrack? {
        val currentTrack = activeAudioTrack
        if (currentTrack != null) {
            if (currentTrack.sampleRate == sampleRate && currentTrack.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    if (currentTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        currentTrack.play()
                    }
                    return currentTrack
                } catch (e: Exception) {
                    Timber.w(e, "Error starting existing AudioTrack, recreating")
                    try { currentTrack.release() } catch (_: Exception) {}
                }
            } else {
                try {
                    currentTrack.stop()
                    currentTrack.release()
                } catch (_: Exception) {}
                activeAudioTrack = null
            }
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        if (bufferSize <= 0) {
            android.util.Log.e(TAG, "Invalid buffer size for AudioTrack: $bufferSize")
            return null
        }

        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to build AudioTrack", e)
            return null
        }

        try {
            newTrack.setVolume(0.0f)
        } catch (_: Exception) {}

        activeAudioTrack = newTrack
        totalFramesWritten.set(0L) // Reset counter for new track
        try {
            newTrack.play()
            // Rampa de volumen (Fade-in de 100ms) en segundo plano
            ttsScope.launch(Dispatchers.Default) {
                try {
                    val steps = 5
                    for (i in 1..steps) {
                        if (activeAudioTrack != newTrack) break
                        newTrack.setVolume(i.toFloat() / steps)
                        delay(20L)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start AudioTrack", e)
        }
        return newTrack
    }

    private fun estimateWordRanges(text: String): List<Triple<IntRange, Float, Float>> {
        val regex = "[\\p{L}\\p{N}]+".toRegex()
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val weights = matches.map { it.value.length + 2.0f }
        val totalWeight = weights.sum()

        val result = mutableListOf<Triple<IntRange, Float, Float>>()
        var currentFraction = 0.0f
        for (i in matches.indices) {
            val weight = weights[i]
            val fracLength = weight / totalWeight
            val startFrac = currentFraction
            val endFrac = currentFraction + fracLength
            result.add(Triple(matches[i].range, startFrac, endFrac))
            currentFraction = endFrac
        }
        return result
    }

    private suspend fun playPcmSamples(
        samples: FloatArray,
        sampleRate: Int,
        playbackRate: Int,
        text: String = "",
        charOffset: Int = 0,
        onRangeUpdate: ((IntRange) -> Unit)? = null
    ) = withContext(Dispatchers.Default) {
        val track = getOrCreateAudioTrack(sampleRate) ?: return@withContext

        try {
            // 1. Wait for any previously written audio to finish playing before changing the playback rate
            val startWaitTime = System.currentTimeMillis()
            val currentRate = track.playbackRate
            val remainingFrames = totalFramesWritten.get() - track.playbackHeadPosition
            val remainingMs = if (remainingFrames > 0 && currentRate > 0) {
                (remainingFrames.toFloat() / currentRate * 1000L).toLong()
            } else {
                0L
            }
            val timeoutMs = remainingMs + 500L

            while (!isSpeakingCancelled && activeAudioTrack == track && track.playbackHeadPosition < totalFramesWritten.get() && currentCoroutineContext().isActive) {
                if (System.currentTimeMillis() - startWaitTime > timeoutMs) {
                    break
                }
                kotlinx.coroutines.delay(20L)
            }

            // 2. Safely apply the new playback rate
            try {
                if (track.playbackRate != playbackRate) {
                    track.playbackRate = playbackRate
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to set playback rate: $playbackRate", e)
            }

            // 3. Estimate word ranges and start monitoring
            val startFrame = totalFramesWritten.get()
            val monitorJob = if (onRangeUpdate != null && text.isNotEmpty()) {
                val wordRanges = estimateWordRanges(text)
                launch(Dispatchers.Default) {
                    var lastRange: IntRange? = null
                    while (isActive && track.playbackHeadPosition < startFrame + samples.size) {
                        val head = track.playbackHeadPosition
                        val played = (head - startFrame).coerceAtLeast(0)
                        val fraction = played.toFloat() / samples.size
                        
                        val activeWord = wordRanges.find { fraction >= it.second && fraction < it.third }
                        if (activeWord != null) {
                            val absoluteRange = IntRange(
                                start = charOffset + activeWord.first.first,
                                endInclusive = charOffset + activeWord.first.last
                            )
                            if (absoluteRange != lastRange) {
                                lastRange = absoluteRange
                                onRangeUpdate(absoluteRange)
                            }
                        }
                        delay(25L)
                    }
                }
            } else {
                null
            }

            // 4. Write samples
            try {
                val chunkSize = 4096
                var offset = 0
                while (!isSpeakingCancelled && offset < samples.size && currentCoroutineContext().isActive) {
                    if (track.state != AudioTrack.STATE_INITIALIZED || activeAudioTrack != track) {
                        break
                    }
                    val size = minOf(chunkSize, samples.size - offset)
                    val result = track.write(samples, offset, size, AudioTrack.WRITE_BLOCKING)
                    if (result < 0) {
                        android.util.Log.e(TAG, "AudioTrack.write() error: $result")
                        break
                    }
                    offset += size
                    totalFramesWritten.addAndGet(size.toLong())
                }
            } finally {
                monitorJob?.cancel()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during PCM playback", e)
        }
    }

    private suspend fun speakWithSherpa(text: String, onRangeUpdate: ((IntRange) -> Unit)? = null) = coroutineScope {
        isSpeakingCancelled = false
        val activeRules = try {
            ttsRuleDao.getActiveRulesForBook(currentBookId)
        } catch (_: Exception) {
            emptyList()
        }

        val exclusionRules = activeRules.map {
            com.example.woodsreader.core.pipeline.WordExclusionRule(
                id = it.id.toString(),
                target = it.pattern,
                replacement = it.replacement,
                isRegex = it.isRegex
            )
        }
        val processedText = wordExclusionEngine.filterText(text, exclusionRules)

        val items = parseTextToPlaybackItemsForSherpa(processedText)

        class GeneratedAudioItem(
            val samples: FloatArray?,
            val nominalSampleRate: Int,
            val playbackSampleRate: Int,
            val pauseMs: Long,
            val text: String,
            val charOffset: Int
        )

        val audioChannel = Channel<GeneratedAudioItem>(capacity = 2)
        var dialogueCount = 0

        val producerJob = launch(Dispatchers.Default) {
            try {
                for (item in items) {
                    if (!isActive) break

                    if (item.type == ChunkType.PAUSE || item.text.isBlank()) {
                        audioChannel.send(GeneratedAudioItem(null, 0, 0, item.pauseMs, item.text, item.charOffset))
                        continue
                    }

                    // Combined pitch factor: basePitch (user's setting)
                    val currentPitchFactor = basePitch.coerceIn(0.5f, 2.0f)

                    // Compensate speed for pitch-shifted sample playback
                    val speedMultiplier = when (item.type) {
                        ChunkType.DIALOGUE -> 0.95f
                        else -> 1.0f
                    }
                    val targetSpeed = (baseRate * speedMultiplier).coerceIn(0.1f, 3.0f)
                    val generationSpeed = (targetSpeed / currentPitchFactor).coerceIn(0.1f, 3.0f)

                    val generatedAudio = try {
                        offlineTts?.generate(
                            text = item.text,
                            sid = 0,
                            speed = generationSpeed
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Sherpa generate failed for: ${item.text}", e)
                        null
                    }

                    if (generatedAudio != null && generatedAudio.samples.isNotEmpty()) {
                        val playbackRate = (generatedAudio.sampleRate * currentPitchFactor).toInt()
                        audioChannel.send(
                            GeneratedAudioItem(
                                samples = generatedAudio.samples,
                                nominalSampleRate = generatedAudio.sampleRate,
                                playbackSampleRate = playbackRate,
                                pauseMs = item.pauseMs,
                                text = item.text,
                                charOffset = item.charOffset
                            )
                        )
                    } else {
                        audioChannel.send(GeneratedAudioItem(null, 0, 0, item.pauseMs, item.text, item.charOffset))
                    }
                }
            } finally {
                audioChannel.close()
            }
        }

        requestAudioFocus()
        try {
            for (audioItem in audioChannel) {
                if (!currentCoroutineContext().isActive) break

                if (audioItem.samples != null && audioItem.samples.isNotEmpty()) {
                    playPcmSamples(
                        samples = audioItem.samples,
                        sampleRate = audioItem.nominalSampleRate,
                        playbackRate = audioItem.playbackSampleRate,
                        text = audioItem.text,
                        charOffset = audioItem.charOffset,
                        onRangeUpdate = onRangeUpdate
                    )
                }

                if (audioItem.pauseMs > 0) {
                    kotlinx.coroutines.delay(audioItem.pauseMs)
                }
            }

            // Wait for the very last segment to finish playing before releasing the track
            val track = activeAudioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                val startWaitTime = System.currentTimeMillis()
                val currentRate = track.playbackRate
                val remainingFrames = totalFramesWritten.get() - track.playbackHeadPosition
                val remainingMs = if (remainingFrames > 0 && currentRate > 0) {
                    (remainingFrames.toFloat() / currentRate * 1000L).toLong()
                } else {
                    0L
                }
                val timeoutMs = remainingMs + 600L

                while (!isSpeakingCancelled && activeAudioTrack == track && track.playbackHeadPosition < totalFramesWritten.get() && currentCoroutineContext().isActive) {
                    if (System.currentTimeMillis() - startWaitTime > timeoutMs) {
                        break
                    }
                    kotlinx.coroutines.delay(20L)
                }
            }
        } finally {
            producerJob.cancel()
            abandonAudioFocus()
        }
    }

    /**
     * Narra una secuencia ordenada de unidades semánticas limpias provenientes del ReadingPipeline.
     * Las unidades de anuncio de imagen se preceden y suceden de pausas naturales.
     */
    suspend fun speakNarrationUnits(
        units: List<com.example.woodsreader.core.pipeline.TtsNarrationUnit>,
        startIndex: Int = 0,
        onUnitStarted: ((com.example.woodsreader.core.pipeline.TtsNarrationUnit) -> Unit)? = null,
        onRangeUpdate: ((blockId: String, range: IntRange) -> Unit)? = null
    ) {
        if (units.isEmpty() || startIndex !in units.indices) return

        for (i in startIndex until units.size) {
            if (!ttsScope.isActive) break

            val unit = units[i]
            onUnitStarted?.invoke(unit)

            if (unit.isAnnouncement) {
                delay(200L)
            }

            speak(unit.textToSpeak) { range ->
                onRangeUpdate?.invoke(unit.blockId, range)
            }

            if (unit.isAnnouncement) {
                delay(300L)
            }
        }
    }
}


