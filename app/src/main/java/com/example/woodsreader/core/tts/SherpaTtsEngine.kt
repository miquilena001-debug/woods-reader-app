package com.example.woodsreader.core.tts

import android.content.Context
import kotlinx.coroutines.*
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
    private val delegate: AndroidTtsEngine,
    private val ttsManager: TtsManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null

    @Volatile private var isSherpa: Boolean = false

    // Listener que Readium inyecta en nuestro engine para recibir eventos
    private var engineListener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null

    init {
        scope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                isSherpa = prefs.useSherpaTts
            }
        }

        // Reenviar eventos del delegado Android nativo al listener de Readium
        // Solo cuando Sherpa está desactivado
        delegate.setListener(object : TtsEngine.Listener<AndroidTtsEngine.Error> {
            override fun onStart(requestId: TtsEngine.RequestId) {
                if (!isSherpa) engineListener?.onStart(requestId)
            }

            override fun onRange(requestId: TtsEngine.RequestId, range: IntRange) {
                if (!isSherpa) engineListener?.onRange(requestId, range)
            }

            override fun onInterrupted(requestId: TtsEngine.RequestId) {
                if (!isSherpa) engineListener?.onInterrupted(requestId)
            }

            override fun onFlushed(requestId: TtsEngine.RequestId) {
                if (!isSherpa) engineListener?.onFlushed(requestId)
            }

            override fun onDone(requestId: TtsEngine.RequestId) {
                if (!isSherpa) engineListener?.onDone(requestId)
            }

            override fun onError(requestId: TtsEngine.RequestId, error: AndroidTtsEngine.Error) {
                if (!isSherpa) engineListener?.onError(requestId, error)
            }
        })
    }

    // ── TtsEngine interface ────────────────────────────────────────────────────

    override val settings: StateFlow<AndroidTtsSettings> = delegate.settings

    override val voices: Set<AndroidTtsEngine.Voice> = delegate.voices

    override fun submitPreferences(preferences: AndroidTtsPreferences) {
        delegate.submitPreferences(preferences)
    }

    /** Readium llama aquí para inyectar su listener de eventos de síntesis. */
    override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
        engineListener = listener
    }

    /**
     * Sintetiza [text] con el motor activo:
     * - Sherpa ONNX si [useSherpaTts] es true
     * - Android nativo en caso contrario
     *
     * [language] es nullable según la firma de Readium 3.0.3.
     */
    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        if (isSherpa) {
            speakWithSherpa(requestId, text)
        } else {
            // El delegate nativo usará su propio listener (configurado en init)
            delegate.speak(requestId, text, language)
        }
    }

    private fun speakWithSherpa(requestId: TtsEngine.RequestId, text: String) {
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.Default) {
            engineListener?.onStart(requestId)
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                ttsManager.setRate(prefs.ttsSpeed)
                ttsManager.setPitch(prefs.ttsPitch)
                ttsManager.speak(text) { range ->
                    engineListener?.onRange(requestId, range)
                }
                engineListener?.onDone(requestId)
            } catch (e: CancellationException) {
                engineListener?.onInterrupted(requestId)
                throw e
            } catch (_: Exception) {
                // Fallback silencioso: indicar done para que Readium continúe al siguiente
                engineListener?.onDone(requestId)
            }
        }
    }

    override fun stop() {
        activeJob?.cancel()
        activeJob = null
        delegate.stop()
        ttsManager.stop()
    }

    override fun close() {
        activeJob?.cancel()
        scope.cancel()
        delegate.close()
    }
}
