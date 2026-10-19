package com.example.woodsreader.core.tts

import android.content.Context
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class EdgeVoiceOption(
    val shortName: String,
    val displayName: String,
    val regionLabel: String
) {
    ALVARO_ES("es-ES-AlvaroNeural", "🌟 Álvaro Neural (Estudio Masculina)", "España · Calidad Humana 10/10"),
    ELVIRA_ES("es-ES-ElviraNeural", "🌟 Elvira Neural (Estudio Femenina)", "España · Calidad Humana 10/10"),
    DALIA_MX("es-MX-DaliaNeural", "🌟 Dalia Neural (Estudio Femenina)", "México / Latam · Calidad Humana 10/10"),
    JORGE_MX("es-MX-JorgeNeural", "🌟 Jorge Neural (Estudio Masculina)", "México / Latam · Calidad Humana 10/10"),
    SEBASTIAN_VE("es-VE-SebastianNeural", "🌟 Sebastián Neural (Estudio Masculina)", "Venezuela · Calidad Humana 10/10"),
    PAOLA_VE("es-VE-PaolaNeural", "🌟 Paola Neural (Estudio Femenina)", "Venezuela · Calidad Humana 10/10");

    companion object {
        fun fromShortName(name: String?): EdgeVoiceOption =
            entries.find { it.shortName == name } ?: ALVARO_ES
    }
}

@Singleton
class EdgeNeuralTtsClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val WIN_EPOCH_OFFSET = 11644473600L
    }

    private val prefs = context.getSharedPreferences("edge_neural_tts_prefs", Context.MODE_PRIVATE)
    private val cacheDir = File(context.cacheDir, "edge_tts_audio_cache").apply { mkdirs() }

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("use_edge_neural_tts", false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _selectedVoice = MutableStateFlow(
        EdgeVoiceOption.fromShortName(prefs.getString("selected_edge_voice", null))
    )
    val selectedVoice: StateFlow<EdgeVoiceOption> = _selectedVoice.asStateFlow()

    @Volatile
    private var activePlayer: MediaPlayer? = null

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<ByteArray?>>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("use_edge_neural_tts", enabled).apply()
        _isEnabled.value = enabled
        if (!enabled) {
            stopPlayback()
        }
    }

    fun setVoice(option: EdgeVoiceOption) {
        prefs.edit().putString("selected_edge_voice", option.shortName).apply()
        _selectedVoice.value = option
    }

    fun stopPlayback() {
        val player = activePlayer
        activePlayer = null
        if (player != null) {
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {}
            try {
                player.release()
            } catch (_: Exception) {}
        }
    }

    fun pausePlayback() {
        runCatching {
            activePlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
        }
    }

    fun resumePlayback() {
        runCatching {
            activePlayer?.let {
                it.start()
            }
        }
    }

    fun prefetchSentence(
        text: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || !_isEnabled.value) return
        prefetchScope.launch {
            runCatching {
                synthesizeToMp3Bytes(cleanText, _selectedVoice.value, speed, pitch)
            }
        }
    }

    private fun generateSecMsGec(): String {
        var ticks = (System.currentTimeMillis() / 1000L) + WIN_EPOCH_OFFSET
        ticks -= (ticks % 300L)
        val fileTime = ticks * 10_000_000L
        val strToHash = "$fileTime$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256").digest(strToHash.toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    suspend fun synthesizeToMp3Bytes(
        text: String,
        voice: EdgeVoiceOption = _selectedVoice.value,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@withContext null

        val speedPercent = ((speed.coerceIn(0.5f, 2.5f) - 1.0f) * 100f).toInt()
        val pitchHz = ((pitch.coerceIn(0.5f, 1.5f) - 1.0f) * 40f).toInt()
        val rateStr = if (speedPercent >= 0) "+${speedPercent}%" else "${speedPercent}%"
        val pitchStr = if (pitchHz >= 0) "+${pitchHz}Hz" else "${pitchHz}Hz"

        val cacheKey = "${voice.shortName}_${rateStr}_${pitchStr}_${cleanText.hashCode()}"
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        if (cachedFile.exists() && cachedFile.length() > 256) {
            return@withContext runCatching { cachedFile.readBytes() }.getOrNull()
        }

        // Si ya hay una solicitud en curso para esta clave (por prefetch o llamada previa), reusar el Deferred
        val deferred = inFlightRequests.computeIfAbsent(cacheKey) {
            prefetchScope.async(Dispatchers.IO) {
                try {
                    val bytes = fetchMp3ViaWebSocket(cleanText, voice.shortName, rateStr, pitchStr)
                    if (bytes != null && bytes.size > 256) {
                        runCatching {
                            cleanOldCacheIfNeeded()
                            cachedFile.writeBytes(bytes)
                        }
                    }
                    bytes
                } finally {
                    inFlightRequests.remove(cacheKey)
                }
            }
        }
        deferred.await()
    }

    suspend fun speakSentence(
        text: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val mp3Bytes = synthesizeToMp3Bytes(text, _selectedVoice.value, speed, pitch)
            ?: return@withContext false

        val tempPlayFile = File(context.cacheDir, "edge_play_active.mp3")
        runCatching { tempPlayFile.writeBytes(mp3Bytes) }.onFailure { return@withContext false }

        suspendCancellableCoroutine { cont ->
            stopPlayback()
            val player = MediaPlayer()
            activePlayer = player
            try {
                player.setDataSource(tempPlayFile.absolutePath)
                player.setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) {}
                    if (activePlayer === mp) activePlayer = null
                    if (cont.isActive) cont.resume(true)
                }
                player.setOnErrorListener { mp, _, _ ->
                    try { mp.release() } catch (_: Exception) {}
                    if (activePlayer === mp) activePlayer = null
                    if (cont.isActive) cont.resume(false)
                    true
                }
                player.prepare()
                player.start()
            } catch (_: Exception) {
                try { player.release() } catch (_: Exception) {}
                if (activePlayer === player) activePlayer = null
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation {
                stopPlayback()
            }
        }
    }

    private suspend fun fetchMp3ViaWebSocket(
        text: String,
        voiceName: String,
        rateStr: String,
        pitchStr: String
    ): ByteArray? = suspendCancellableCoroutine { cont ->
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = generateSecMsGec()
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=1-130.0.2849.68&ConnectionId=$connectionId"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .build()

        val audioStream = ByteArrayOutputStream()
        var completed = false

        val ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val configMsg = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
                webSocket.send(configMsg)

                val escapedText = text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")

                val ssmlMsg = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n" +
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='es-ES'>" +
                        "<voice name='$voiceName'><prosody pitch='$pitchStr' rate='$rateStr' volume='+0%'>$escapedText</prosody></voice></speak>"
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size > 2) {
                    val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val payloadOffset = 2 + headerLen
                    if (payloadOffset < data.size) {
                        val headerStr = String(data, 2, headerLen, Charsets.UTF_8)
                        if (headerStr.contains("Path:audio")) {
                            audioStream.write(data, payloadOffset, data.size - payloadOffset)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    completed = true
                    webSocket.close(1000, null)
                    if (cont.isActive) cont.resume(audioStream.toByteArray())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!completed && cont.isActive) {
                    cont.resume(null)
                }
            }
        })

        cont.invokeOnCancellation {
            ws.cancel()
        }
    }

    private fun cleanOldCacheIfNeeded() {
        val files = cacheDir.listFiles() ?: return
        if (files.size > 80) {
            files.sortedBy { it.lastModified() }.take(30).forEach { it.delete() }
        }
    }
}
