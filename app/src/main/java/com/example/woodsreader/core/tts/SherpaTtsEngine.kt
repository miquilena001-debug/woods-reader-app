package com.example.woodsreader.core.tts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import timber.log.Timber

typealias SherpaOnnxTtsEngine = SherpaTtsEngine

/**
 * Motor TTS personalizado que envuelve [AndroidTtsEngine] y redirige la síntesis
 * a [TtsManager] (Sherpa ONNX o nativo) según la preferencia del usuario.
 *
 * Firma exacta de Readium 3.0.3:
 *   TtsEngine<S, P, E, V>  donde E = AndroidTtsEngine.Error, V = AndroidTtsEngine.Voice
 *   TtsEngine.Listener<E>  (solo 1 tipo genérico)
 *   speak(requestId, text, language?)  → language es nullable
 *   setListener(listener: Listener<E>?)
 */
@OptIn(ExperimentalReadiumApi::class)
class SherpaTtsEngine(
    private val delegate: TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice>,
    private val ttsManager: TtsManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    context: Context,
    val pronunciationLexiconManager: PronunciationLexiconManager? = null
) : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null
    private val nativeTtsMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile private var isSherpa: Boolean = false
    @Volatile private var currentRequestUsesSherpa: Boolean = false

    // Listener que Readium inyecta en nuestro engine para recibir eventos
    private var engineListener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null

    // ── Woods Studio Voice HD: Delegación al TtsManager centralizado ───────────
    val acousticsProfile: StateFlow<StudioAcousticsProfile> = ttsManager.acousticsProfile
    val expressiveProsodyEnabled: StateFlow<Boolean> = ttsManager.expressiveProsodyEnabled
    val dramatizedDialogueEnabled: StateFlow<Boolean> = ttsManager.dramatizedDialogueEnabled
    val secondaryDialogueVoiceId: StateFlow<String?> = ttsManager.secondaryDialogueVoiceId

    val selectedLanguageFilter: StateFlow<SupportedTtsLanguage> = ttsManager.selectedLanguageFilter
    val autoDetectBookLanguage: StateFlow<Boolean> = ttsManager.autoDetectBookLanguage
    val downloadedModelIds: StateFlow<Set<String>> = ttsManager.downloadedModelIds

    // ── Paisajes Sonoros Procedurales en Vivo y Temporizador Inteligente ────
    val ambientSoundscapePlayer: AmbientSoundscapePlayer = ttsManager.ambientSoundscapePlayer
    val ambientSoundscapeEngine: AmbientSoundscapeEngine = ttsManager.ambientSoundscapeEngine
    val soundscapeMode: StateFlow<SoundscapeMode> = ambientSoundscapePlayer.mode
    val soundscapeIntensity: StateFlow<Float> = ambientSoundscapePlayer.volume
    val smartSleepTimerManager: SmartSleepTimerManager = ttsManager.smartSleepTimerManager

    fun setAcousticsProfile(profile: StudioAcousticsProfile) = ttsManager.setAcousticsProfile(profile)
    fun setExpressiveProsodyEnabled(enabled: Boolean) = ttsManager.setExpressiveProsodyEnabled(enabled)
    fun setDramatizedDialogueEnabled(enabled: Boolean) = ttsManager.setDramatizedDialogueEnabled(enabled)
    fun setSecondaryDialogueVoiceId(voiceId: String?) = ttsManager.setSecondaryDialogueVoiceId(voiceId)

    fun setSelectedLanguageFilter(language: SupportedTtsLanguage) = ttsManager.setSelectedLanguageFilter(language)
    fun setAutoDetectBookLanguage(enabled: Boolean) = ttsManager.setAutoDetectBookLanguage(enabled)
    fun deleteDownloadedModel(modelId: String): Boolean = ttsManager.deleteDownloadedModel(modelId)

    fun setSoundscapeMode(mode: SoundscapeMode) = ambientSoundscapePlayer.setMode(mode)
    fun setSoundscapeIntensity(intensity: Float) = ambientSoundscapePlayer.setVolume(intensity)

    init {
        scope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                isSherpa = prefs.useSherpaTts
            }
        }

        // Reenviar eventos del delegado Android nativo al listener de Readium
        // Solo cuando la solicitud activa está usando el delegado nativo
        delegate.setListener(object : TtsEngine.Listener<AndroidTtsEngine.Error> {
            override fun onStart(requestId: TtsEngine.RequestId) {
                if (!currentRequestUsesSherpa) engineListener?.onStart(requestId)
            }

            override fun onRange(requestId: TtsEngine.RequestId, range: IntRange) {
                if (!currentRequestUsesSherpa) engineListener?.onRange(requestId, range)
            }

            override fun onInterrupted(requestId: TtsEngine.RequestId) {
                if (!currentRequestUsesSherpa) engineListener?.onInterrupted(requestId)
            }

            override fun onFlushed(requestId: TtsEngine.RequestId) {
                if (!currentRequestUsesSherpa) engineListener?.onFlushed(requestId)
            }

            override fun onDone(requestId: TtsEngine.RequestId) {
                if (!currentRequestUsesSherpa) engineListener?.onDone(requestId)
            }

            override fun onError(requestId: TtsEngine.RequestId, error: AndroidTtsEngine.Error) {
                if (!currentRequestUsesSherpa) engineListener?.onError(requestId, error)
            }
        })
    }

    // ── TtsEngine interface ────────────────────────────────────────────────────

    override val settings: StateFlow<AndroidTtsSettings> = delegate.settings

    override val voices: Set<AndroidTtsEngine.Voice> = delegate.voices

    override fun submitPreferences(preferences: AndroidTtsPreferences) {
        delegate.submitPreferences(preferences)
        preferences.speed?.let { ttsManager.setRate(it.toFloat()) }
        preferences.pitch?.let { ttsManager.setPitch(it.toFloat()) }
    }

    /** Readium llama aquí para inyectar su listener de eventos de síntesis. */
    override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
        engineListener = listener
    }

    /**
     * Sintetiza [text] con el motor activo:
     * - Sherpa ONNX si [useSherpaTts] es true y hay modelos listos
     * - Fallback al delegado Android nativo si Sherpa no está disponible o falla
     *
     * [language] es nullable según la firma de Readium 3.0.3.
     */
    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        val canUseSherpa = isSherpa && ttsManager.isSherpaReady()
        if (canUseSherpa) {
            currentRequestUsesSherpa = true
            speakWithSherpa(requestId, text, language)
        } else {
            currentRequestUsesSherpa = false
            delegate.speak(requestId, text, language)
        }
    }

    private fun speakWithSherpa(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.Default) {
            engineListener?.onStart(requestId)
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                ttsManager.setRate(prefs.ttsSpeed)
                ttsManager.setPitch(prefs.ttsPitch)
                val playedSuccessfully = ttsManager.speak(text) { range ->
                    engineListener?.onRange(requestId, range)
                }
                if (playedSuccessfully) {
                    engineListener?.onDone(requestId)
                } else {
                    Timber.w("ttsManager.speak devolvió false para request $requestId. Fallback al delegado Android TTS nativo.")
                    currentRequestUsesSherpa = false
                    withContext(Dispatchers.Main) {
                        delegate.speak(requestId, text, language)
                    }
                }
            } catch (e: CancellationException) {
                engineListener?.onInterrupted(requestId)
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Sherpa synthesis failed for request $requestId, falling back to Android TTS delegate")
                currentRequestUsesSherpa = false
                withContext(Dispatchers.Main) {
                    delegate.speak(requestId, text, language)
                }
            }
        }
    }

    /**
     * Sintetiza texto con el motor de estudio "Woods Studio Voice HD".
     *
     * 1. Analiza la oración con [AudiobookProsodyEngine] para obtener [ProsodySegment]s.
     * 2. Para cada segmento, genera audio crudo con Sherpa ONNX aplicando la velocidad prosódica.
     * 3. Pasa cada segmento por [StudioAudioDspProcessor.masterAudioSegment] con el perfil acústico activo.
     * 4. Concatena todos los segmentos en un único [FloatArray] continuo.
     *
     * @return Par de (muestras Float 32-bit, sampleRate) o null si falla la generación.
     */
    suspend fun synthesizeExpressiveStudioAudio(
        text: String,
        baseSpeed: Float,
        basePitch: Float = 1.0f
    ): Pair<FloatArray, Int>? = nativeTtsMutex.withLock {
        ttsManager.synthesizeExpressiveStudioAudio(text, baseSpeed, basePitch)
    }

    override fun stop() {
        activeJob?.cancel()
        activeJob = null
        currentRequestUsesSherpa = false
        delegate.stop()
        ttsManager.stop()
    }

    override fun close() {
        activeJob?.cancel()
        currentRequestUsesSherpa = false
        scope.cancel()
        delegate.close()
    }
}
