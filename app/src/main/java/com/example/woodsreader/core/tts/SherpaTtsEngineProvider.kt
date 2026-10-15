package com.example.woodsreader.core.tts

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Try
import com.example.woodsreader.data.datastore.UserPreferencesRepository

/**
 * Proveedor TTS personalizado que inyecta [SherpaTtsEngine] en el [TtsNavigatorFactory] de Readium.
 *
 * Firma exacta de TtsEngineProvider en Readium 3.0.3:
 *   TtsEngineProvider<S, P, E, F, V>  (5 tipos: Settings, Preferences, PreferencesEditor, Error, Voice)
 *   createEngine retorna Try<TtsEngine<S,P,F,V>, Error>
 */
@OptIn(ExperimentalReadiumApi::class)
class SherpaTtsEngineProvider(
    private val context: Context,
    private val ttsManager: TtsManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : TtsEngineProvider<
        AndroidTtsSettings,
        AndroidTtsPreferences,
        PreferencesEditor<AndroidTtsPreferences>,
        AndroidTtsEngine.Error,
        AndroidTtsEngine.Voice> {

    private val delegate = AndroidTtsEngineProvider(context)

    override fun createEmptyPreferences(): AndroidTtsPreferences =
        delegate.createEmptyPreferences()

    override fun getPlaybackParameters(settings: AndroidTtsSettings): PlaybackParameters =
        delegate.getPlaybackParameters(settings)

    override fun updatePlaybackParameters(
        previousPreferences: AndroidTtsPreferences,
        playbackParameters: PlaybackParameters
    ): AndroidTtsPreferences =
        delegate.updatePlaybackParameters(previousPreferences, playbackParameters)

    override fun mapEngineError(error: AndroidTtsEngine.Error): PlaybackException =
        delegate.mapEngineError(error)

    override fun createPreferencesEditor(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences
    ): PreferencesEditor<AndroidTtsPreferences> =
        delegate.createPreferencesEditor(publication, initialPreferences)

    /**
     * Crea el engine Sherpa que envuelve al AndroidTtsEngine nativo.
     * Retorna Try.success con el engine, o Try.failure si falla la inicialización.
     */
    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences
    ): Try<TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice>, Error> {
        val delegateResult = delegate.createEngine(publication, initialPreferences)

        return delegateResult.map { androidEngine ->
            SherpaTtsEngine(
                delegate = androidEngine,
                ttsManager = ttsManager,
                userPreferencesRepository = userPreferencesRepository
            )
        }
    }
}
