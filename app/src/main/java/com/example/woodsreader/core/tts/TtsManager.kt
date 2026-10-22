package com.example.woodsreader.core.tts

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.PlaybackParams
import android.os.Build
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.WorkerThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException


private const val TAG = "TtsManager"

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsRuleDao: com.example.woodsreader.data.database.TtsRuleDao,
    private val userPreferencesRepository: com.example.woodsreader.data.datastore.UserPreferencesRepository,
    val sherpaModelManager: SherpaModelManager,
    private val edgeNeuralTtsClient: EdgeNeuralTtsClient,
    private val pronunciationLexiconManager: PronunciationLexiconManager,
    val smartSleepTimerManager: SmartSleepTimerManager,
    val ambientSoundscapePlayer: AmbientSoundscapePlayer,
    private val woodsKeepAliveManager: com.example.woodsreader.core.system.WoodsKeepAliveManager
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    @Volatile
    private var offlineTts: com.k2fsa.sherpa.onnx.OfflineTts? = null
    @Volatile
    private var loadedVoiceId: String? = null
    private val nativeTtsMutex = Mutex()
    @Volatile
    private var activeAudioTrack: AudioTrack? = null
    @Volatile
    private var persistentSherpaTrack: AudioTrack? = null
    @Volatile
    private var persistentTrackSampleRate: Int = 0
    private val totalFramesWritten = AtomicLong(0L)
    @Volatile
    private var isSpeakingCancelled = false
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun isSherpaReady(): Boolean {
        return offlineTts != null || sherpaModelManager.isModelReady() || sherpaModelManager.getInstalledModels().isNotEmpty()
    }

    // ── Pre-Síntesis Lookahead (0ms Latencia, hilo secuencial único) ───────────
    private val preSynthesisDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val preSynthesisScope = CoroutineScope(SupervisorJob() + preSynthesisDispatcher)
    private val preSynthesisCache = ConcurrentHashMap<Int, Deferred<Pair<FloatArray, Int>?>>()

    fun clearPreSynthesisCache() {
        preSynthesisCache.values.forEach { it.cancel() }
        preSynthesisCache.clear()
    }

    // ── Woods Studio Voice HD: Configuración Acústica y Prosodia ──────────────
    private val studioPrefs: SharedPreferences =
        context.getSharedPreferences("woods_studio_voice_hd", Context.MODE_PRIVATE)

    private val _acousticsProfile = MutableStateFlow(
        StudioAcousticsProfile.fromId(studioPrefs.getString("acoustics_profile", null))
    )
    val acousticsProfile: StateFlow<StudioAcousticsProfile> = _acousticsProfile.asStateFlow()

    private val _expressiveProsodyEnabled = MutableStateFlow(
        studioPrefs.getBoolean("expressive_prosody_enabled", true)
    )
    val expressiveProsodyEnabled: StateFlow<Boolean> = _expressiveProsodyEnabled.asStateFlow()

    private val _dramatizedDialogueEnabled = MutableStateFlow(
        studioPrefs.getBoolean("dramatized_dialogue_enabled", true)
    )
    val dramatizedDialogueEnabled: StateFlow<Boolean> = _dramatizedDialogueEnabled.asStateFlow()

    private val _secondaryDialogueVoiceId = MutableStateFlow<String?>(
        studioPrefs.getString("secondary_dialogue_voice_id", null)
    )
    val secondaryDialogueVoiceId: StateFlow<String?> = _secondaryDialogueVoiceId.asStateFlow()

    // ── Woods Studio Voice HD: Paisajes Sonoros Procedurales en Tiempo Real ──
    val ambientSoundscapeEngine = AmbientSoundscapeEngine()

    val soundscapeMode: StateFlow<SoundscapeMode> = ambientSoundscapePlayer.mode
    val soundscapeIntensity: StateFlow<Float> = ambientSoundscapePlayer.volume

    private val studioPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            "acoustics_profile" -> _acousticsProfile.value = StudioAcousticsProfile.fromId(sp.getString(key, null))
            "expressive_prosody_enabled" -> _expressiveProsodyEnabled.value = sp.getBoolean(key, true)
            "dramatized_dialogue_enabled" -> _dramatizedDialogueEnabled.value = sp.getBoolean(key, true)
            "secondary_dialogue_voice_id" -> _secondaryDialogueVoiceId.value = sp.getString(key, null)
            "soundscape_mode" -> {
                val mode = SoundscapeMode.fromId(sp.getString(key, null))
                ambientSoundscapePlayer.setMode(mode)
            }
            "soundscape_intensity" -> {
                val intensity = sp.getFloat(key, 0.35f)
                ambientSoundscapePlayer.setVolume(intensity)
            }
        }
    }

    fun setAcousticsProfile(profile: StudioAcousticsProfile) {
        _acousticsProfile.value = profile
        studioPrefs.edit().putString("acoustics_profile", profile.id).apply()
    }

    fun setExpressiveProsodyEnabled(enabled: Boolean) {
        _expressiveProsodyEnabled.value = enabled
        studioPrefs.edit().putBoolean("expressive_prosody_enabled", enabled).apply()
    }

    fun setDramatizedDialogueEnabled(enabled: Boolean) {
        _dramatizedDialogueEnabled.value = enabled
        studioPrefs.edit().putBoolean("dramatized_dialogue_enabled", enabled).apply()
    }

    fun setSecondaryDialogueVoiceId(voiceId: String?) {
        _secondaryDialogueVoiceId.value = voiceId
        if (voiceId != null) {
            studioPrefs.edit().putString("secondary_dialogue_voice_id", voiceId).apply()
        } else {
            studioPrefs.edit().remove("secondary_dialogue_voice_id").apply()
        }
    }

    fun setSoundscapeMode(mode: SoundscapeMode) {
        ambientSoundscapePlayer.setMode(mode)
    }

    fun setSoundscapeIntensity(intensity: Float) {
        ambientSoundscapePlayer.setVolume(intensity)
    }

    // ── Woods Studio Voice HD: Filtros Multilingües y Auto-Detección ─────────
    val selectedLanguageFilter: StateFlow<SupportedTtsLanguage> = sherpaModelManager.selectedLanguageFilter
    val autoDetectBookLanguage: StateFlow<Boolean> = sherpaModelManager.autoDetectBookLanguage
    val downloadedModelIds: StateFlow<Set<String>> = sherpaModelManager.downloadedModelIds

    fun setSelectedLanguageFilter(language: SupportedTtsLanguage) {
        sherpaModelManager.setSelectedLanguageFilter(language)
    }

    fun setAutoDetectBookLanguage(enabled: Boolean) {
        sherpaModelManager.setAutoDetectBookLanguage(enabled)
    }

    fun deleteDownloadedModel(modelId: String): Boolean {
        return sherpaModelManager.deleteDownloadedModel(modelId)
    }

    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel = _isLoadingModel.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var wifiLock: WifiManager.WifiLock? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentNarrationUnitIndex = MutableStateFlow<Int>(-1)
    val currentNarrationUnitIndex: StateFlow<Int> = _currentNarrationUnitIndex.asStateFlow()

    @Volatile
    private var isNarratingSequence = false

    @Volatile
    private var wasPlayingBeforeTransientLoss = false

    @Volatile
    var onQueueCompletedNaturally: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    @Volatile
    private var hasAudioFocus = false
    @Volatile
    private var isSynthesizingOrPlayingSherpa = false
    @Volatile
    private var userRequestedStop = false

    private var basePitch: Float = 1.0f
    private var baseRate: Float = 1.0f
    private val _currentPitch = MutableStateFlow(1.0f)
    val currentPitch: StateFlow<Float> = _currentPitch.asStateFlow()
    @Volatile
    private var currentBookId: String? = null

    init {
        // Desactivación forzada de TTS online por internet (Edge Neural TTS)
        edgeNeuralTtsClient.setEnabled(false)
        studioPrefs.registerOnSharedPreferenceChangeListener(studioPrefsListener)

        smartSleepTimerManager.onTimerExpiredPauseAudio = {
            pause()
        }

        ttsScope.launch {
            sherpaModelManager.selectedVoice.collect { newVoice ->
                val currentLoaded = loadedVoiceId
                if (currentLoaded != null && currentLoaded != newVoice.id) {
                    clearPreSynthesisCache()
                    resetSherpa()
                }
            }
        }
        ttsScope.launch {
            _isPlaying.collect { playing ->
                ambientSoundscapePlayer.onTtsSpeakingChanged(playing)
            }
        }
        ttsScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                if (!prefs.useSherpaTts && loadedVoiceId != null) {
                    clearPreSynthesisCache()
                    resetSherpa()
                }
                setRate(prefs.ttsSpeed)
                setPitch(prefs.ttsPitch)
            }
        }
        ttsScope.launch {
            initialize()
        }
    }

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
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (isSynthesizingOrPlayingSherpa && !userRequestedStop) {
                    android.util.Log.w(TAG, "Ignorando AUDIOFOCUS_LOSS porque Sherpa está sintetizando o hablando activamente")
                    return@OnAudioFocusChangeListener
                }
                hasAudioFocus = false
                wasPlayingBeforeTransientLoss = false
                stop() // Parar inmediatamente si perdemos el foco permanentemente
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isSynthesizingOrPlayingSherpa && !userRequestedStop) {
                    android.util.Log.w(TAG, "Ignorando AUDIOFOCUS_LOSS_TRANSIENT porque Sherpa está sintetizando o hablando activamente")
                    return@OnAudioFocusChangeListener
                }
                if (_isPlaying.value) {
                    wasPlayingBeforeTransientLoss = true
                    pause() // Pausar limpiamente ante llamada o audio temporal
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setRate(0.8f * baseRate) // Reducir velocidad si el sistema pide "ducking"
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                setRate(baseRate) // Restaurar velocidad original
                if (wasPlayingBeforeTransientLoss) {
                    wasPlayingBeforeTransientLoss = false
                    resume() // Reanudar automáticamente al recuperar el foco
                }
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
                cont.resume(isReady)
            }
            cont.invokeOnCancellation {
                tts?.stop()
                tts?.shutdown()
                tts = null
                isReady = false
            }
        }
    }

    suspend fun speak(text: String, onRangeUpdate: ((IntRange) -> Unit)? = null): Boolean {
        userRequestedStop = false
        _isPlaying.value = true
        if (!isNarratingSequence) {
            acquireAudioLocks()
        }
        try {
            val useSherpa = try {
                userPreferencesRepository.userPreferencesFlow.first().useSherpaTts
            } catch (_: Exception) {
                false
            } || (isSherpaReady() && !isReady)

            if (useSherpa && initSherpaIfNeeded()) {
                isSpeakingCancelled = false
                isSynthesizingOrPlayingSherpa = true
                requestAudioFocus()
                try {
                    val cleanText = SpanishAudiobookNormalizer.normalizeForSpeech(text)
                        .replace(":", ",")
                        .trim()

                    if (cleanText.isBlank() || !cleanText.any { it.isLetterOrDigit() }) {
                        return false
                    }

                    val clauses = if (cleanText.length > 65) {
                        splitIntoStreamingClauses(cleanText)
                    } else {
                        listOf(cleanText)
                    }

                    if (clauses.size <= 1) {
                        val audio = synthesizeExpressiveStudioAudio(cleanText, baseRate, basePitch)
                        if (audio != null && audio.first.isNotEmpty()) {
                            val played = playPcmSamplesBlocking(audio.first, audio.second)
                            if (played) return true
                        } else {
                            Timber.w("Sherpa devolvió audio vacío para '$text'. Usando motor TTS nativo como respaldo sin cortar audio focus.")
                        }
                    } else {
                        val audioChannel = kotlinx.coroutines.channels.Channel<Pair<FloatArray, Int>>(capacity = 2)
                        var playedAny = false
                        coroutineScope {
                            val producerJob = launch(Dispatchers.Default) {
                                try {
                                    for (clause in clauses) {
                                        if (!isActive || userRequestedStop) break
                                        val audio = synthesizeExpressiveStudioAudio(clause, baseRate, basePitch)
                                        if (audio != null && audio.first.isNotEmpty()) {
                                            audioChannel.send(audio)
                                        }
                                    }
                                } finally {
                                    audioChannel.close()
                                }
                            }

                            try {
                                for (audio in audioChannel) {
                                    if (userRequestedStop) break
                                    val played = playPcmSamplesBlocking(audio.first, audio.second)
                                    if (played) playedAny = true
                                }
                            } finally {
                                producerJob.cancel()
                            }
                        }
                        if (playedAny) return true
                    }
                } finally {
                    isSynthesizingOrPlayingSherpa = false
                }
            }

            // Si el texto carece de caracteres pronunciables (solo puntuación o espacios), ignorar sin cortar audio focus
            if (text.isBlank() || !text.any { it.isLetterOrDigit() }) {
                return false
            }

            if (!isReady || tts == null) {
                val ok = withTimeoutOrNull(2500L) {
                    initialize()
                } ?: false
                if (!ok || tts == null || !isReady) return false
            }

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

            // Segmentación y orquestación contextual
            val items = parseTextToPlaybackItems(processedText)
            var dialogueCount = 0

            isSpeakingCancelled = false
            requestAudioFocus()
            var spokenAtLeastOne = false
            for (item in items) {
                // Verificar cancelación de corrutinas en cada paso del bucle
                if (isSpeakingCancelled || !currentCoroutineContext().isActive || !isReady || tts == null) break

                // Si es un item de pausa pura, aplicar delay y continuar
                if (item.type == ChunkType.PAUSE || item.text.isBlank()) {
                    kotlinx.coroutines.delay(item.pauseMs)
                    continue
                }

                val rolePitchModifier = when {
                    item.type == ChunkType.DIALOGUE && item.isExclamation -> 1.08f
                    item.type == ChunkType.DIALOGUE && item.isQuestion -> 1.06f
                    item.type == ChunkType.DIALOGUE -> 1.04f
                    item.isExclamation -> 1.04f
                    else -> 1.0f
                }
                val speechRateMultiplier = when {
                    item.type == ChunkType.DIALOGUE && item.isExclamation -> 1.02f
                    item.type == ChunkType.DIALOGUE -> 0.96f
                    else -> 1.0f
                }
                tts?.setPitch((basePitch * rolePitchModifier).coerceIn(0.5f, 2.0f))
                tts?.setSpeechRate((baseRate * speechRateMultiplier).coerceIn(0.1f, 3.0f))

                val id = "utterance_${System.currentTimeMillis()}"

                suspendCancellableCoroutine<Unit> { cont ->
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if ((utteranceId == null || utteranceId == id) && cont.isActive) cont.resume(Unit)
                        }
                        override fun onError(utteranceId: String?) {
                            if ((utteranceId == null || utteranceId == id) && cont.isActive) cont.resume(Unit)
                        }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if ((utteranceId == null || utteranceId == id) && cont.isActive) {
                                if (isSpeakingCancelled) {
                                    cont.cancel()
                                } else {
                                    cont.resume(Unit)
                                }
                            }
                        }
                    })

                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
                    }
                    tts?.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, id)

                    cont.invokeOnCancellation {
                        stop()
                    }
                }
                spokenAtLeastOne = true

                if (isSpeakingCancelled || !currentCoroutineContext().isActive) break

                // Restaurar los valores por defecto del lector
                tts?.setPitch(basePitch)
                tts?.setSpeechRate(baseRate)

                // Retraso pos-oración para pausas naturales
                if (item.pauseMs > 0) {
                    kotlinx.coroutines.delay(item.pauseMs)
                }
            }
            return spokenAtLeastOne
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error during speak()")
            return false
        } finally {
            if (isSpeakingCancelled) {
                if (!isSynthesizingOrPlayingSherpa || userRequestedStop) {
                    _isPlaying.value = false
                    releaseAudioLocks()
                    abandonAudioFocus()
                }
            }
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ LÃƒÂ³gica de Parseo y TokenizaciÃƒÂ³n Contextual Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    
    private fun splitIntoChunks(text: String): List<SpeechChunk> {
        val chunks = mutableListOf<SpeechChunk>()
        val currentText = StringBuilder()
        var inDialogue = false
        var i = 0
        val trimmedStart = text.trimStart()
        val startsAsDialogueParagraph = trimmedStart.startsWith('\u2014') ||
            trimmedStart.startsWith('\u2013') ||
            trimmedStart.startsWith("- ")

        while (i < text.length) {
            val char = text[i]
            val isDash = char == '\u2014' || char == '\u2013'
            val isQuoteOpen = char == '\u00AB' || char == '\u201C'
            val isQuoteClose = char == '\u00BB' || char == '\u201D'
            val isStraightQuote = char == '"'

            if (isDash || isQuoteOpen || isQuoteClose || isStraightQuote) {
                val content = currentText.toString().trim()
                if (content.isNotEmpty()) {
                    chunks.add(SpeechChunk(content, if (inDialogue) ChunkType.DIALOGUE else ChunkType.NARRATOR))
                    currentText.clear()
                }
                inDialogue = when {
                    isQuoteOpen -> true
                    isQuoteClose -> false
                    isStraightQuote -> !inDialogue
                    isDash && i == text.indexOfFirst { !it.isWhitespace() } && startsAsDialogueParagraph -> true
                    else -> !inDialogue
                }
                chunks.add(SpeechChunk("", ChunkType.PAUSE, 180L))
            } else {
                if (char == '\n') {
                    val content = currentText.toString().trim()
                    if (content.isNotEmpty()) {
                        chunks.add(SpeechChunk(content, if (inDialogue) ChunkType.DIALOGUE else ChunkType.NARRATOR))
                        currentText.clear()
                    }
                    inDialogue = false
                    chunks.add(SpeechChunk("", ChunkType.PAUSE, 450L))
                } else {
                    currentText.append(char)
                }
            }
            i++
        }

        val finalContent = currentText.toString().trim()
        if (finalContent.isNotEmpty()) {
            chunks.add(SpeechChunk(finalContent, if (inDialogue) ChunkType.DIALOGUE else ChunkType.NARRATOR))
        }

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

    private fun acquireAudioLocks() {
        woodsKeepAliveManager.acquireKeepAliveWakeLock()
        runCatching {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WoodsReader:TtsWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(45 * 60 * 1000L) // Timeout de 45 min, renovable en cada capítulo
        }

        runCatching {
            if (edgeNeuralTtsClient.isEnabled.value) {
                if (wifiLock == null) {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wifiManager?.createWifiLock(mode, "WoodsReader:TtsWifiLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                wifiLock?.let {
                    if (!it.isHeld) {
                        it.acquire()
                    }
                }
            } else {
                releaseWifiLock()
            }
        }
    }

    private fun releaseAudioLocks() {
        woodsKeepAliveManager.releaseKeepAliveWakeLock()
        releaseWakeLock()
        releaseWifiLock()
    }

    private fun acquireLocks() = acquireAudioLocks()
    private fun releaseLocks() = releaseAudioLocks()

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        }
    }

    private fun releaseWifiLock() {
        runCatching {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        hasAudioFocus = true
        return true
    }

    private fun abandonAudioFocus() {
        hasAudioFocus = false
        // No-op para no interferir con el AudioFocusManager de Readium que administra la sesión completa
    }

    fun pause() {
        userRequestedStop = true
        isSynthesizingOrPlayingSherpa = false
        _isPlaying.value = false
        clearPreSynthesisCache()
        releaseAudioLocks()
        runCatching {
            persistentSherpaTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.pause()
                    it.flush()
                }
            }
        }
        runCatching {
            activeAudioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED && it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.pause()
                }
            }
        }
        edgeNeuralTtsClient.pausePlayback()
        tts?.stop()
        abandonAudioFocus()
    }

    fun resume() {
        userRequestedStop = false
        _isPlaying.value = true
        acquireAudioLocks()
        runCatching {
            persistentSherpaTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED && it.playState == AudioTrack.PLAYSTATE_PAUSED) {
                    it.play()
                }
            }
        }
        runCatching {
            activeAudioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED && it.playState == AudioTrack.PLAYSTATE_PAUSED) {
                    it.play()
                }
            }
        }
        edgeNeuralTtsClient.resumePlayback()
    }

    fun stop() {
        userRequestedStop = true
        isSynthesizingOrPlayingSherpa = false
        _isPlaying.value = false
        _currentNarrationUnitIndex.value = -1
        isNarratingSequence = false
        wasPlayingBeforeTransientLoss = false
        clearPreSynthesisCache()
        releaseAudioLocks()
        runCatching {
            persistentSherpaTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.pause()
                    it.flush()
                }
            }
        }
        edgeNeuralTtsClient.stopPlayback()
        isSpeakingCancelled = true
        tts?.stop()
        abandonAudioFocus()
    }

    fun release() {
        shutdown()
    }

    fun shutdown() {
        userRequestedStop = true
        isSynthesizingOrPlayingSherpa = false
        _isPlaying.value = false
        _currentNarrationUnitIndex.value = -1
        isNarratingSequence = false
        wasPlayingBeforeTransientLoss = false
        clearPreSynthesisCache()
        releaseAudioLocks()
        edgeNeuralTtsClient.stopPlayback()
        isSpeakingCancelled = true
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        abandonAudioFocus()
        runCatching {
            persistentSherpaTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.pause()
                    it.flush()
                    it.stop()
                    it.release()
                }
            }
            persistentSherpaTrack = null
        }
        try {
            if (nativeTtsMutex.tryLock()) {
                try {
                    offlineTts?.release()
                } finally {
                    offlineTts = null
                    nativeTtsMutex.unlock()
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    nativeTtsMutex.withLock {
                        try {
                            offlineTts?.release()
                        } catch (_: Exception) {}
                        offlineTts = null
                    }
                }
            }
        } catch (_: Exception) {}
        try {
            ttsScope.coroutineContext.cancelChildren()
            preSynthesisScope.coroutineContext.cancelChildren()
        } catch (_: Exception) {}
    }


    fun calculatePiperAndPlaybackParams(
        nominalSampleRate: Int,
        targetSpeed: Float,
        targetPitch: Float
    ): Pair<Float, Float> {
        val clampedSpeed = targetSpeed.coerceIn(0.5f, 3.5f)

        // Limita la velocidad enviada a Piper VITS estrictamente a 0.75f..1.35f
        // Evita que el predictor de duraciones colapse a 0 frames produciendo audio vacío
        val safePiperSpeed = clampedSpeed.coerceIn(0.75f, 1.35f)

        // El resto de la velocidad (> 1.35x o < 0.75x) se aplica vía PlaybackParams por hardware sin efecto ardilla
        val hardwareSpeedMultiplier = (clampedSpeed / safePiperSpeed).coerceIn(0.5f, 2.0f)

        return safePiperSpeed to hardwareSpeedMultiplier
    }

    private fun applyPlaybackParams(track: AudioTrack, hardwareSpeedMultiplier: Float, pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = PlaybackParams()
                    .allowDefaults()
                    .setSpeed(hardwareSpeedMultiplier.coerceIn(0.5f, 2.0f))
                    .setPitch(pitch.coerceIn(0.7f, 1.4f))
                track.playbackParams = params
            } catch (e: Exception) {
                Timber.w(e, "Error al configurar PlaybackParams en AudioTrack")
            }
        }
    }

    @Synchronized
    private fun getOrCreateSherpaTrack(sampleRate: Int): AudioTrack {
        val existing = persistentSherpaTrack
        if (existing != null && persistentTrackSampleRate == sampleRate && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }

        runCatching {
            existing?.stop()
            existing?.release()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        persistentSherpaTrack = track
        persistentTrackSampleRate = sampleRate
        activeAudioTrack = track
        return track
    }

    fun splitIntoStreamingClauses(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= 65) return listOf(trimmed)

        val clauses = mutableListOf<String>()
        var remaining = trimmed

        while (remaining.length > 65) {
            var cutIndex = -1
            for (i in 30..minOf(65, remaining.length - 1)) {
                val c = remaining[i]
                if (c == ',' || c == ';' || c == '—' || c == ':') {
                    cutIndex = i + 1
                    break
                }
            }
            if (cutIndex == -1) {
                val spaceIndex = remaining.indexOf(' ', startIndex = 45)
                if (spaceIndex != -1 && spaceIndex <= 75) {
                    cutIndex = spaceIndex + 1
                }
            }
            if (cutIndex == -1) {
                val spaceIndex = remaining.indexOf(' ', startIndex = 35)
                if (spaceIndex != -1) {
                    cutIndex = spaceIndex + 1
                }
            }

            if (cutIndex != -1 && cutIndex < remaining.length) {
                val part = remaining.substring(0, cutIndex).trim()
                if (part.isNotEmpty()) clauses.add(part)
                remaining = remaining.substring(cutIndex).trim()
            } else {
                break
            }
        }
        if (remaining.isNotEmpty()) {
            clauses.add(remaining.trim())
        }
        return if (clauses.isEmpty()) listOf(trimmed) else clauses.filter { it.isNotBlank() }
    }

    private fun playPcmSamplesBlocking(samples: FloatArray, sampleRate: Int): Boolean {
        if (samples.isEmpty() || sampleRate <= 0) return false
        android.util.Log.e("WOODS_SHERPA_DIAG", "playPcmSamplesBlocking: INICIO ${samples.size} muestras @ $sampleRate Hz")
        val track = getOrCreateSherpaTrack(sampleRate)

        // Aplicar pitch/speed solo dentro de runCatching para que NUNCA aborte el audio en MediaTek
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val userPitch = _currentPitch.value.coerceIn(0.75f, 1.35f)
            if (kotlin.math.abs(userPitch - 1.0f) > 0.03f) {
                runCatching {
                    track.playbackParams = PlaybackParams()
                        .allowDefaults()
                        .setPitch(userPitch)
                        .setSpeed(1.0f)
                }
            }
        }

        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            runCatching { track.play() }
        }

        val chunkSize = 2048
        val shortBuf = ShortArray(chunkSize)
        var offset = 0
        while (offset < samples.size && !userRequestedStop) {
            val count = minOf(chunkSize, samples.size - offset)
            for (i in 0 until count) {
                val clamped = samples[offset + i].coerceIn(-1.0f, 1.0f)
                shortBuf[i] = (clamped * 32767.0f).toInt().toShort()
            }
            val written = track.write(shortBuf, 0, count)
            if (written < 0) break
            offset += count
        }
        android.util.Log.e("WOODS_SHERPA_DIAG", "playPcmSamplesBlocking: FIN offset=$offset / ${samples.size} (userRequestedStop=$userRequestedStop)")
        return offset > 0
    }

    fun setRate(rate: Float) { 
        val oldRate = baseRate
        baseRate = rate.coerceIn(0.5f, 3.5f)
        if (isReady) tts?.setSpeechRate(baseRate)
        if (oldRate != baseRate) {
            clearPreSynthesisCache()
            activeAudioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    val (_, hardwareSpeed) = calculatePiperAndPlaybackParams(track.sampleRate, baseRate, basePitch)
                    applyPlaybackParams(track, hardwareSpeed, basePitch)
                }
            }
        }
    }
    
    fun setPitch(pitch: Float) { 
        val oldPitch = basePitch
        basePitch = pitch.coerceIn(0.7f, 1.4f)
        _currentPitch.value = basePitch
        if (isReady) tts?.setPitch(basePitch)
        if (oldPitch != basePitch) {
            clearPreSynthesisCache()
            activeAudioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    val (_, hardwareSpeed) = calculatePiperAndPlaybackParams(track.sampleRate, baseRate, basePitch)
                    applyPlaybackParams(track, hardwareSpeed, basePitch)
                }
            }
        }
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

    suspend fun resetSherpa() = nativeTtsMutex.withLock {
        try {
            offlineTts?.release()
        } catch (e: Exception) {
            Timber.w(e, "Error releasing OfflineTts")
        }
        offlineTts = null
        loadedVoiceId = null
    }

    suspend fun switchSherpaVoice(option: SherpaVoiceOption): Boolean {
        stop()
        sherpaModelManager.selectVoiceOption(option)
        resetSherpa()
        return if (sherpaModelManager.isModelReady()) {
            initSherpaIfNeeded()
        } else {
            false
        }
    }

    suspend fun initSherpaIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (offlineTts != null && loadedVoiceId == sherpaModelManager.selectedVoice.value.id) return@withContext true
        nativeTtsMutex.withLock {
            ensureSherpaLoadedLocked()
        }
    }

    private fun ensureSherpaLoadedLocked(): Boolean {
        if (offlineTts != null && loadedVoiceId == sherpaModelManager.selectedVoice.value.id) return true
        if (offlineTts != null) {
            try {
                offlineTts?.release()
            } catch (_: Exception) {}
            offlineTts = null
            loadedVoiceId = null
        }
        return try {
            Timber.d("Initializing Sherpa ONNX...")
            _isLoadingModel.value = true
            ensureNativeLibLoaded()

            // Si el modelo seleccionado no está listo en disco, auto-fallback al primer modelo instalado
            if (!sherpaModelManager.isModelReady()) {
                val installed = sherpaModelManager.getInstalledModels().firstOrNull()
                if (installed != null) {
                    Timber.w("Selected model not ready. Auto-fallback to installed model: ${installed.id}")
                    sherpaModelManager.selectVoiceOption(installed)
                }
            }

            if (sherpaModelManager.isModelReady()) {
                Timber.d("Loading Sherpa ONNX from disk: ${sherpaModelManager.modelDir.absolutePath}")
                offlineTts = sherpaModelManager.createOfflineTtsInstance()
                if (offlineTts != null) {
                    loadedVoiceId = sherpaModelManager.selectedVoice.value.id
                    Timber.i("Sherpa ONNX initialized successfully from disk: $loadedVoiceId")
                    return true
                }
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
                        numThreads = 2,
                        debug = false
                    )
                )
                offlineTts = com.k2fsa.sherpa.onnx.OfflineTts(null, config)
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

    /**
     * Genera audio crudo con Sherpa ONNX sin reproducirlo.
     * Devuelve (muestras Float32, sampleRate) o null si falla.
     * Usado por el motor de estudio "Woods Studio Voice HD" para procesar DSP.
     *
     * Protegido estrictamente con [nativeTtsMutex] para impedir ejecuciones
     * concurrentes en el motor C++ JNI nativo.
     */
    suspend fun generateSherpaAudioRaw(text: String, speed: Float): Pair<FloatArray, Int>? =
        withContext(Dispatchers.IO) {
            nativeTtsMutex.withLock {
                if (offlineTts == null || loadedVoiceId != sherpaModelManager.selectedVoice.value.id) {
                    if (!ensureSherpaLoadedLocked()) return@withLock null
                }
                val tts = offlineTts ?: return@withLock null
                try {
                    android.util.Log.e("WOODS_SHERPA_DIAG", "generateSherpaAudioRaw: INICIO '$text' speed=$speed (tid=${Thread.currentThread().id})")
                    val result = tts.generate(
                        text = text,
                        sid = sherpaModelManager.selectedVoice.value.speakerId,
                        speed = speed
                    )
                    android.util.Log.e("WOODS_SHERPA_DIAG", "generateSherpaAudioRaw: FIN '$text' -> samples=${result.samples.size}, rate=${result.sampleRate}")
                    if (result.samples.isNotEmpty()) {
                        result.samples to result.sampleRate
                    } else {
                        Timber.w("Sherpa tts.generate returned empty samples for: $text")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WOODS_SHERPA_DIAG", "generateSherpaAudioRaw: EXCEPCION para '$text'", e)
                    null
                }
            }
        }

    /**
     * Sintetiza texto con el motor de estudio "Woods Studio Voice HD".
     * Síntesis directa de oración completa (sin fragmentar ni descartar muestras).
     *
     * @return Par de (muestras Float 32-bit, sampleRate) o null si falla la generación.
     */
    suspend fun synthesizeExpressiveStudioAudio(
        text: String,
        baseSpeed: Float = baseRate,
        basePitch: Float = this.basePitch
    ): Pair<FloatArray, Int>? {
        val cleanText = SpanishAudiobookNormalizer.normalizeForSpeech(text)
            .replace(":", ",")
            .trim()
        if (cleanText.isBlank() || !cleanText.any { it.isLetterOrDigit() }) {
            return null
        }

        // Si offlineTts es null o no coincide con la voz seleccionada, inicializar síncronamente con nativeTtsMutex
        if (offlineTts == null || loadedVoiceId != sherpaModelManager.selectedVoice.value.id) {
            nativeTtsMutex.withLock {
                ensureSherpaLoadedLocked()
            }
        }

        val safePiperSpeed = baseSpeed.coerceIn(0.75f, 1.35f)
        val rawPair = generateSherpaAudioRaw(cleanText, safePiperSpeed) ?: return null
        val rawSamples = rawPair.first
        val sampleRate = rawPair.second
        if (rawSamples.isEmpty() || sampleRate <= 0) return null

        // Aplicar DSP con fallback seguro a rawSamples si hubiera cualquier excepción:
        // JAMÁS descartar rawSamples cuando ya tiene audio válido (> 0 muestras).
        val masteredSamples = runCatching {
            StudioAudioDspProcessor.masterAudioSegment(
                rawSamples = rawSamples,
                sampleRate = sampleRate,
                profile = StudioAcousticsProfile.STUDIO_WARM,
                role = VoiceActRole.NARRATOR,
                gainMultiplier = 1.0f,
                postPauseMs = 240,
                singleVoiceRoleShift = false
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: rawSamples

        return masteredSamples to sampleRate
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
                Timber.i("AudioTrack sampleRate mismatch (${currentTrack.sampleRate} != $sampleRate) or uninitialized, recreating")
                try {
                    if (currentTrack.state == AudioTrack.STATE_INITIALIZED) {
                        currentTrack.stop()
                        currentTrack.flush()
                    }
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
        hardwareSpeed: Float = 1.0f,
        pitch: Float = 1.0f,
        text: String = "",
        charOffset: Int = 0,
        onRangeUpdate: ((IntRange) -> Unit)? = null
    ) = withContext(Dispatchers.Default) {
        val currentTrack = activeAudioTrack
        val effectiveSpeed = if (hardwareSpeed > 0.05f) hardwareSpeed else 1.0f
        if (currentTrack != null && currentTrack.sampleRate != sampleRate && currentTrack.state == AudioTrack.STATE_INITIALIZED) {
            val remainingFrames = totalFramesWritten.get() - currentTrack.playbackHeadPosition
            if (remainingFrames > 0 && sampleRate > 0) {
                val remainingMs = ((remainingFrames.toFloat() / sampleRate) / effectiveSpeed * 1000L).toLong().coerceIn(0L, 2000L)
                delay(remainingMs)
            }
        }

        val track = getOrCreateAudioTrack(sampleRate) ?: return@withContext

        try {
            // 1. Wait for any previously written audio to finish playing before changing the playback rate
            val startWaitTime = System.currentTimeMillis()
            val remainingFrames = totalFramesWritten.get() - track.playbackHeadPosition
            val remainingMs = if (remainingFrames > 0 && sampleRate > 0) {
                ((remainingFrames.toFloat() / sampleRate) / effectiveSpeed * 1000L).toLong()
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

            // 2. Configurar PlaybackParams por hardware sin distorsión de ardilla
            applyPlaybackParams(track, hardwareSpeed, pitch)

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

                // 5. Esperar a que el AudioTrack termine de reproducir físicamente todas las muestras
                val targetEndFrame = totalFramesWritten.get()
                val expectedDurationMs = if (sampleRate > 0) {
                    ((samples.size.toFloat() / sampleRate) / effectiveSpeed * 1000L).toLong()
                } else {
                    ((samples.size.toFloat() / 22050) * 1000L).toLong()
                }
                val waitStartTime = System.currentTimeMillis()
                val maxWaitMs = expectedDurationMs + 800L

                while (!isSpeakingCancelled && activeAudioTrack == track && track.playbackHeadPosition < targetEndFrame && currentCoroutineContext().isActive) {
                    if (System.currentTimeMillis() - waitStartTime > maxWaitMs) {
                        break
                    }
                    delay(25L)
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

        val maxClauseChars = 180
        val safeClauses = sherpaModelManager.splitIntoSafeClauses(processedText, maxChars = maxClauseChars)

        val items = mutableListOf<PlaybackItem>()
        var currentSearchIndex = 0
        for (clause in safeClauses) {
            val foundIndex = processedText.indexOf(clause, currentSearchIndex)
            val offset = if (foundIndex != -1) {
                currentSearchIndex = foundIndex + clause.length
                foundIndex
            } else currentSearchIndex

            val trimmed = clause.trim()
            val isExclamation = trimmed.endsWith("!") || trimmed.endsWith("¡")
            val isQuestion = trimmed.endsWith("?") || trimmed.endsWith("¿")
            val isDialogue = trimmed.startsWith("—") || trimmed.startsWith("\"") || trimmed.startsWith("“") || trimmed.startsWith("«")
            val chunkType = if (isDialogue) ChunkType.DIALOGUE else ChunkType.NARRATOR
            val pauseMs = if (trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")) 250L else 90L

            items.add(
                PlaybackItem(
                    text = clause,
                    type = chunkType,
                    pauseMs = pauseMs,
                    isExclamation = isExclamation,
                    isQuestion = isQuestion,
                    charOffset = offset
                )
            )
        }
        if (items.isEmpty() && processedText.isNotBlank()) {
            items.add(PlaybackItem(text = processedText, type = ChunkType.NARRATOR, pauseMs = 0L, charOffset = 0))
        }

        class GeneratedAudioItem(
            val samples: FloatArray?,
            val nominalSampleRate: Int,
            val hardwareSpeed: Float,
            val pitch: Float,
            val pauseMs: Long,
            val text: String,
            val charOffset: Int
        )

        val audioChannel = Channel<GeneratedAudioItem>(capacity = 1)
        var dialogueCount = 0

        val producerJob = launch(Dispatchers.Default) {
            try {
                for (item in items) {
                    if (!isActive) break

                    if (item.type == ChunkType.PAUSE || item.text.isBlank()) {
                        audioChannel.send(GeneratedAudioItem(null, 0, 1.0f, 1.0f, item.pauseMs, item.text, item.charOffset))
                        continue
                    }

                    val rolePitchModifier = when {
                        item.type == ChunkType.DIALOGUE && item.isExclamation -> 1.08f
                        item.type == ChunkType.DIALOGUE && item.isQuestion -> 1.06f
                        item.type == ChunkType.DIALOGUE -> 1.04f
                        item.isExclamation -> 1.04f
                        else -> 1.0f
                    }
                    val currentPitchFactor = (basePitch * rolePitchModifier).coerceIn(0.7f, 1.4f)

                    val speedMultiplier = when {
                        item.type == ChunkType.DIALOGUE && item.isExclamation -> 1.02f
                        item.type == ChunkType.DIALOGUE -> 0.96f
                        else -> 1.0f
                    }
                    val targetSpeed = (baseRate * speedMultiplier).coerceIn(0.5f, 3.5f)
                    val (safePiperSpeed, hardwareSpeedMultiplier) = calculatePiperAndPlaybackParams(22050, targetSpeed, currentPitchFactor)

                    val generatedAudio = runCatching {
                        nativeTtsMutex.withLock {
                            if (offlineTts == null || loadedVoiceId != sherpaModelManager.selectedVoice.value.id) {
                                ensureSherpaLoadedLocked()
                            }
                            offlineTts?.generate(
                                text = item.text,
                                sid = sherpaModelManager.selectedVoice.value.speakerId,
                                speed = safePiperSpeed
                            )
                        }
                    }.onFailure { e ->
                        android.util.Log.e(TAG, "Sherpa generate failed for: ${item.text}", e)
                    }.getOrNull()

                    if (generatedAudio != null && generatedAudio.samples.isNotEmpty()) {
                        audioChannel.send(
                            GeneratedAudioItem(
                                samples = generatedAudio.samples,
                                nominalSampleRate = generatedAudio.sampleRate,
                                hardwareSpeed = hardwareSpeedMultiplier,
                                pitch = currentPitchFactor,
                                pauseMs = item.pauseMs,
                                text = item.text,
                                charOffset = item.charOffset
                            )
                        )
                    } else {
                        audioChannel.send(GeneratedAudioItem(null, 0, 1.0f, 1.0f, item.pauseMs, item.text, item.charOffset))
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
                        hardwareSpeed = audioItem.hardwareSpeed,
                        pitch = audioItem.pitch,
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
            if (!isSynthesizingOrPlayingSherpa || userRequestedStop) {
                abandonAudioFocus()
            }
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
        onRangeUpdate: ((blockId: String, range: IntRange) -> Unit)? = null,
        onChapterFinished: (suspend () -> Unit)? = null
    ) {
        if (units.isEmpty() || startIndex !in units.indices) return

        userRequestedStop = false
        isNarratingSequence = true
        _isPlaying.value = true
        acquireAudioLocks()
        clearPreSynthesisCache()

        var completedNormally = true
        try {
            val useSherpa = try {
                userPreferencesRepository.userPreferencesFlow.first().useSherpaTts
            } catch (_: Exception) {
                false
            } || (sherpaModelManager.isModelReady() && !isReady)

            if (useSherpa && initSherpaIfNeeded()) {
                requestAudioFocus()
                for (i in startIndex until units.size) {
                    if (!ttsScope.isActive || isSpeakingCancelled || userRequestedStop) {
                        completedNormally = false
                        break
                    }

                    val unit = units[i]
                    val trimmedText = unit.textToSpeak.trim()
                    // Si la oración individual está vacía o solo contiene caracteres no pronunciables (ej. *** o ---),
                    // NO llamar a pause() ni detener la reproducción: saltar automáticamente a currentIndex + 1 y continuar
                    if (trimmedText.isEmpty() || trimmedText.all { !it.isLetterOrDigit() }) {
                        Timber.d("Saltando unidad vacía o decorativa $i ('$trimmedText') sin detener la reproducción")
                        continue
                    }

                    _currentNarrationUnitIndex.value = i
                    onUnitStarted?.invoke(unit)

                    if (unit.isAnnouncement) {
                        delay(200L)
                    }

                    // Síntesis y reproducción secuencial directa sin hilos paralelos
                    try {
                        isSynthesizingOrPlayingSherpa = true
                        val audio = synthesizeExpressiveStudioAudio(unit.textToSpeak, baseRate, basePitch)
                        if (audio != null && audio.first.isNotEmpty()) {
                            playPcmSamplesBlocking(audio.first, audio.second)
                        } else {
                            Timber.w("Audio vacío generado para unidad $i; continuando al siguiente sin pausar.")
                        }
                    } catch (unitErr: Exception) {
                        Timber.e(unitErr, "Excepción en unidad $i; continuando al siguiente sin pausar.")
                    } finally {
                        isSynthesizingOrPlayingSherpa = false
                    }

                    if (unit.isAnnouncement) {
                        delay(300L)
                    }
                }
            } else {
                // Modo Sistema Android nativo
                for (i in startIndex until units.size) {
                    if (!ttsScope.isActive || isSpeakingCancelled) {
                        completedNormally = false
                        break
                    }

                    val unit = units[i]
                    val trimmedText = unit.textToSpeak.trim()
                    if (trimmedText.isEmpty() || trimmedText.all { !it.isLetterOrDigit() }) {
                        continue
                    }

                    _currentNarrationUnitIndex.value = i
                    onUnitStarted?.invoke(unit)

                    if (unit.isAnnouncement) {
                        delay(200L)
                    }

                    try {
                        speak(unit.textToSpeak) { range ->
                            onRangeUpdate?.invoke(unit.blockId, range)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error en narración nativa $i, continuando.")
                    }

                    if (unit.isAnnouncement) {
                        delay(300L)
                    }
                }
            }

            if (completedNormally && !isSpeakingCancelled && ttsScope.isActive) {
                acquireAudioLocks()
                onChapterFinished?.invoke()
                if (smartSleepTimerManager.consumeEndOfChapterStopIfNeeded()) {
                    isNarratingSequence = false
                    _isPlaying.value = false
                    _currentNarrationUnitIndex.value = -1
                    releaseAudioLocks()
                } else {
                    onQueueCompletedNaturally?.invoke()
                }
            }
        } finally {
            clearPreSynthesisCache()
            if (!completedNormally || isSpeakingCancelled || !ttsScope.isActive) {
                isNarratingSequence = false
                _isPlaying.value = false
                _currentNarrationUnitIndex.value = -1
                releaseAudioLocks()
            }
        }
    }
}

