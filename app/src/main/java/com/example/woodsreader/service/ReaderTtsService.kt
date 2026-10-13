package com.example.woodsreader.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.OptIn as AndroidOptIn
import androidx.core.graphics.scale
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.woodsreader.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover

/**
 * Servicio TTS integrado con Media3 MediaSessionService.
 *
 * ¿¿¿Por quééé funciona con los Soundcore Space A40?
 * ─ Los auriculares Bluetooth envían comandos AVRCP (play/pause, skip next/prev)
 *   al sistema Android, que los convierte en eventos de MediaSession.
 * ─ ReaderTtsService expone una MediaSession con el Player real de Readium.
 * ─ El Callback intercepta los eventos y los enruta a los métodos TTS correctos.
 *
 * ¿¿¿Por quééé funciona en la pantalla de bloqueo y notificaciones?
 * ─ exported="true" + intent-filter en el Manifest → Android descubre el servicio.
 * ─ Media3 genera automáticamente la notificación de medios persistente.
 * ─ setCustomLayout() define los botones visibles en la notificación.
 *
 * NOTAS DE ARQUITECTURA:
 * - TtsNavigatorFactory constructor es internal en Readium 3.0.3.
 *   Se usa la free function TtsNavigatorFactory(application, publication) del mismo paquete.
 * - @AndroidOptIn (androidx.annotation.OptIn) es el mecanismo correcto para Media3 1.4.x.
 */
@OptIn(ExperimentalReadiumApi::class)
@AndroidEntryPoint
class ReaderTtsService : MediaSessionService() {

    @Inject
    lateinit var ttsManager: com.example.woodsreader.core.tts.TtsManager

    @Inject
    lateinit var userPreferencesRepository: com.example.woodsreader.data.datastore.UserPreferencesRepository

    // ── Comandos personalizados expuestos a los auriculares / notificación ────
    companion object {
        const val CMD_SKIP_PREV = "com.example.woodsreader.tts.SKIP_PREV"
        const val CMD_SKIP_NEXT = "com.example.woodsreader.tts.SKIP_NEXT"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var _isEngineWarmed = false
    private var dummyTts: android.speech.tts.TextToSpeech? = null

    // Usamos el tipo genérico completo compatible con SherpaTtsEngineProvider
    private val _ttsNavigator = MutableStateFlow<TtsNavigator<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error, AndroidTtsEngine.Voice>?>(null)

    private val _playbackState = MutableStateFlow<TtsNavigator.Playback?>(null)
    val playbackState = _playbackState.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator = _currentLocator.asStateFlow()

    private var mediaSession: MediaSession? = null
    val binder = TtsBinder()

    private var currentSpeed = 1.0f
    private var currentPreferences = AndroidTtsPreferences()

    @Volatile private var pendingPlayOnReady: Boolean = false
    @Volatile private var pendingLocatorOnReady: Locator? = null

    fun isNavigatorInitialized(): Boolean = _ttsNavigator.value != null

    // ── Binder ────────────────────────────────────────────────────────────────
    inner class TtsBinder : Binder() {
        fun getService(): ReaderTtsService = this@ReaderTtsService
    }

    // ── MediaSession.Callback ─────────────────────────────────────────────────
    /**
     * Recibe comandos de:
     * - Auriculares Bluetooth (AVRCP NEXT / PREV / PLAY / PAUSE)  ← Soundcore Space A40
     * - Pantalla de bloqueo (botones de medios del sistema)
     * - Notificación de sistema
     *
     * En Media3 1.4.x los comandos de skip se interceptan via onCustomCommand;
     * los CommandButton con SessionCommand son la vía recomendada para prev/next.
     */
    @AndroidOptIn(UnstableApi::class)
    private inner class TtsSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Declarar comandos personalizados al conectar cada controlador (auriculares, panel, etc.)
            val customCommands = listOf(
                SessionCommand(CMD_SKIP_PREV, Bundle.EMPTY),
                SessionCommand(CMD_SKIP_NEXT, Bundle.EMPTY)
            )
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .apply { customCommands.forEach { add(it) } }
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(buildCommandButtons())
                .build()
        }

        // Comandos personalizados: prev/next enviados por notificación o auriculares
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                CMD_SKIP_PREV -> {
                    goPrevious()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_SKIP_NEXT -> {
                    goNext()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
        }
    }

    // ── CommandButtons para la notificación ──────────────────────────────────
    /**
     * Define los 3 botones en la notificación de medios y pantalla de bloqueo:
     *   [⏮ Anterior]  [⏯ Play/Pausa]  [⏭ Siguiente]
     *
     * Play/Pausa usa COMMAND_PLAY_PAUSE (nativo del Player de Readium).
     * Anterior/Siguiente usan SessionCommand personalizados para llamar a
     * skipToNextUtterance() / skipToPreviousUtterance() del TtsNavigator.
     *
     * ICON_SKIP_BACK / ICON_SKIP_FORWARD son constantes de CommandButton
     * disponibles desde Media3 1.3.x.
     */
    @AndroidOptIn(UnstableApi::class)
    private fun buildCommandButtons(): List<CommandButton> {
        return listOf(
            // Botón: Enunciado Anterior — SessionCommand personalizado → goPrevious()
            CommandButton.Builder()
                .setDisplayName("Anterior")
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_back)
                .setSessionCommand(SessionCommand(CMD_SKIP_PREV, Bundle.EMPTY))
                .build(),

            // Botón: Play/Pausa — gestionado nativamente por el Player de Readium
            // En Media3 1.4.x no hay ICON_PLAY_PAUSE; el sistema usa el estado del Player
            // para alternar automáticamente entre los iconos de play y pausa.
            CommandButton.Builder()
                .setDisplayName("Reproducir / Pausar")
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_play)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .build(),

            // Botón: Siguiente Enunciado — SessionCommand personalizado → goNext()
            CommandButton.Builder()
                .setDisplayName("Siguiente")
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_forward)
                .setSessionCommand(SessionCommand(CMD_SKIP_NEXT, Bundle.EMPTY))
                .build()
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        // Registrar el proveedor ANTES de que Media3 emita cualquier notificación.
        // Si se configura tarde hay un parpadeo donde aparece la notificación genérica.
        setMediaNotificationProvider(TtsMediaNotificationProvider(this))

        serviceScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                currentSpeed = prefs.ttsSpeed
                currentPreferences = currentPreferences.copy(
                    speed = prefs.ttsSpeed.toDouble().coerceIn(0.25, 4.0),
                    pitch = prefs.ttsPitch.toDouble().coerceIn(0.5, 2.0),
                    language = prefs.ttsLanguage?.let { org.readium.r2.shared.util.Language(it) }
                )
                ttsManager.setRate(prefs.ttsSpeed)
                ttsManager.setPitch(prefs.ttsPitch)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _ttsNavigator.value?.let { navigator ->
                        try {
                            navigator.submitPreferences(currentPreferences)
                        } catch (_: Exception) {}
                    }
                    mediaSession?.player?.setPlaybackSpeed(currentSpeed)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onBind(intent: Intent?): IBinder {
        // Media3 MediaSessionService needs its own binder for system/bluetooth connections.
        // Our EpubFormatManager binds with a plain Intent (no action) and needs TtsBinder.
        val media3Binder = super.onBind(intent)
        return if (intent?.action == SERVICE_INTERFACE) media3Binder ?: binder else binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // El usuario elimina la app del gestor de tareas: limpiar notificación y parar.
        pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        _ttsNavigator.value?.close()
        mediaSession?.run { release(); mediaSession = null }
        dummyTts?.stop(); dummyTts?.shutdown(); dummyTts = null
        serviceScope.coroutineContext.cancelChildren()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Engine warm-up ────────────────────────────────────────────────────────
    fun preWarmEngine() {
        if (_isEngineWarmed) return
        serviceScope.launch(Dispatchers.Main) {
            dummyTts = android.speech.tts.TextToSpeech(applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) _isEngineWarmed = true
            }
        }
    }

    // ── Preferencias TTS ─────────────────────────────────────────────────────
    fun updateSettings(speed: Float, pitch: Float, languageCode: String?) {
        currentSpeed = speed
        currentPreferences = currentPreferences.copy(
            speed = speed.toDouble().coerceIn(0.25, 4.0),
            pitch = pitch.toDouble().coerceIn(0.5, 2.0),
            language = languageCode?.let { org.readium.r2.shared.util.Language(it) }
        )
        ttsManager.setRate(speed)
        ttsManager.setPitch(pitch)
        serviceScope.launch(Dispatchers.Main) {
            _ttsNavigator.value?.let { navigator ->
                try {
                    navigator.submitPreferences(currentPreferences)
                } catch (_: Exception) { }
            }
            mediaSession?.player?.setPlaybackSpeed(speed)
        }
    }

    // ── Inicialización del Navigator ─────────────────────────────────────────
    fun initNavigator(pub: Publication, initialLocator: Locator? = null) {
        preWarmEngine()
        updateMediaMetadata(pub)

        serviceScope.launch(Dispatchers.Main) {
            try {
                _ttsNavigator.value?.close()
                _ttsNavigator.value = null
            } catch (e: Exception) {
                android.util.Log.w("ReaderTtsService", "Error closing old TTS navigator", e)
            }

            val engineProvider = com.example.woodsreader.core.tts.SherpaTtsEngineProvider(
                context = applicationContext,
                ttsManager = ttsManager,
                userPreferencesRepository = userPreferencesRepository
            )

            val factory = try {
                TtsNavigatorFactory(
                    application = application,
                    publication = pub,
                    ttsEngineProvider = engineProvider
                )
            } catch (t: Throwable) {
                android.util.Log.e("ReaderTtsService", "Failed to create TtsNavigatorFactory with SherpaTtsEngineProvider", t)
                try {
                    TtsNavigatorFactory(
                        application = application,
                        publication = pub
                    )
                } catch (t2: Throwable) {
                    android.util.Log.e("ReaderTtsService", "Failed to create default TtsNavigatorFactory", t2)
                    null
                }
            }

            val navigatorResult = try {
                factory?.createNavigator(
                    listener = object : TtsNavigator.Listener {
                        override fun onStopRequested() { pause() }
                    },
                    initialLocator = initialLocator,
                    initialPreferences = currentPreferences
                )
            } catch (t: Throwable) {
                android.util.Log.e("ReaderTtsService", "factory.createNavigator threw exception", t)
                null
            }

            val navigator = navigatorResult?.getOrNull()
            if (navigator == null && factory != null) {
                android.util.Log.e("ReaderTtsService", "createNavigator failed: $navigatorResult")
            }

            _ttsNavigator.value = navigator
            setupMediaSession(navigator)
            observeNavigator(navigator)
            try {
                navigator?.submitPreferences(currentPreferences)
            } catch (_: Exception) {}
            mediaSession?.player?.setPlaybackSpeed(currentSpeed)

            pendingLocatorOnReady?.let { loc ->
                navigator?.go(loc)
                pendingLocatorOnReady = null
            }
            if (pendingPlayOnReady) {
                navigator?.play()
                pendingPlayOnReady = false
            }
        }
    }

    // ── Metadata (portada + título en notificación) ───────────────────────────
    private fun updateMediaMetadata(pub: Publication) {
        serviceScope.launch(Dispatchers.Default) {
            val title  = pub.metadata.title ?: ""
            val author = pub.metadata.authors.joinToString { it.name }

            val coverBytes: ByteArray? = try {
                pub.cover()?.let { bmp ->
                    val scaled = bmp.scale(400, 600, true)
                    val stream = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
                    stream.toByteArray()
                }
            } catch (_: Exception) { null }

            serviceScope.launch(Dispatchers.Main) {
                val metadata = MediaMetadata.Builder()
                    .setTitle(title).setArtist(author).setAlbumTitle(title)
                    .apply { coverBytes?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
                    .build()

                mediaSession?.player?.let { player ->
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setMediaId("woods_tts_item")
                            .setMediaMetadata(metadata)
                            .build()
                    )
                }
            }
        }
    }

    // ── MediaSession setup ────────────────────────────────────────────────────
    @AndroidOptIn(UnstableApi::class)
    private fun setupMediaSession(navigator: TtsNavigator<*, *, *, *>?) {
        if (navigator == null) return
        mediaSession?.let {
            removeSession(it)
            it.release()
            mediaSession = null
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // navigator.asMedia3Player() conecta el Player de Readium con Media3.
        // Esto hace que Play/Pause funcionen directamente desde la notificación
        // y que los auriculares A40 envíen sus comandos AVRCP aquí.
        mediaSession = MediaSession.Builder(this, navigator.asMedia3Player())
            .setSessionActivity(pendingIntent)
            .setCallback(TtsSessionCallback())
            .setCustomLayout(buildCommandButtons())
            .build()

        // IMPORTANTE: agregar la sesión al servicio para que Media3 maneje
        // automáticamente startForeground() y la notificación de medios.
        mediaSession?.let { addSession(it) }
    }

    // ── Observer del navigator ────────────────────────────────────────────────
    private fun observeNavigator(navigator: TtsNavigator<*, *, *, *>?) {
        navigator ?: return
        serviceScope.launch { navigator.playback.collectLatest { _playbackState.value = it } }
        serviceScope.launch {
            navigator.location.collectLatest { loc ->
                _currentLocator.value = loc.utteranceLocator
            }
        }
    }

    // ── Controles públicos (llamados desde ReaderViewModel / EpubFormatManager) ─
    fun play() {
        val nav = _ttsNavigator.value
        if (nav != null) {
            nav.play()
        } else {
            android.util.Log.d("ReaderTtsService", "play() queued: waiting for navigator initialization")
            pendingPlayOnReady = true
        }
    }

    fun pause() {
        pendingPlayOnReady = false
        _ttsNavigator.value?.pause()
    }

    fun stop() {
        pendingPlayOnReady = false
        _ttsNavigator.value?.pause()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
    }

    fun go(locator: Locator) {
        val nav = _ttsNavigator.value
        if (nav != null) {
            nav.go(locator)
        } else {
            android.util.Log.d("ReaderTtsService", "go() queued: locator = $locator")
            pendingLocatorOnReady = locator
        }
    }

    fun goNext()     { _ttsNavigator.value?.skipToNextUtterance() }
    fun goPrevious() { _ttsNavigator.value?.skipToPreviousUtterance() }

    fun speakNarrationUnits(
        units: List<com.example.woodsreader.core.pipeline.TtsNarrationUnit>,
        startIndex: Int = 0,
        onUnitStarted: ((com.example.woodsreader.core.pipeline.TtsNarrationUnit) -> Unit)? = null,
        onRangeUpdate: ((blockId: String, range: IntRange) -> Unit)? = null
    ) {
        serviceScope.launch {
            ttsManager.speakNarrationUnits(units, startIndex, onUnitStarted, onRangeUpdate)
        }
    }
}
