package com.example.woodsreader.core.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class SherpaVoiceOption(
    val id: String,
    val displayName: String,
    val accent: String,
    val dirName: String,
    val onnxFileName: String,
    val downloadUrl: String,
    val minBytes: Long = 40_000_000L,
    val isKokoro: Boolean = false,
    val voicesBinFileName: String = "voices.bin",
    val speakerId: Int = 0
) {
    KOKORO_DORA_ES(
        id = "kokoro-es-dora",
        displayName = "✨ Kokoro Dora (Ultra-Realista Femenina)",
        accent = "Español · Kokoro-82M Studio",
        dirName = "kokoro-multi-lang-v1_0",
        onnxFileName = "model.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2",
        minBytes = 250_000_000L,
        isKokoro = true,
        voicesBinFileName = "voices.bin",
        speakerId = 28 // ef_dora en kokoro-multi-lang-v1_0
    ),
    KOKORO_ALEX_ES(
        id = "kokoro-es-alex",
        displayName = "✨ Kokoro Alex (Ultra-Realista Masculina)",
        accent = "Español · Kokoro-82M Studio",
        dirName = "kokoro-multi-lang-v1_0",
        onnxFileName = "model.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2",
        minBytes = 250_000_000L,
        isKokoro = true,
        voicesBinFileName = "voices.bin",
        speakerId = 29 // em_alex en kokoro-multi-lang-v1_0
    ),
    CLAUDE_MX(
        id = "es_MX-claude-high",
        displayName = "Claudia (Voz Femenina Alta Definición)",
        accent = "Español (México / Latam)",
        dirName = "vits-piper-es_MX-claude-high",
        onnxFileName = "es_MX-claude-high.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high.tar.bz2",
        minBytes = 50_000_000L
    ),
    ALD_MX(
        id = "es_MX-ald-medium",
        displayName = "Alejandro (Latino Neutro)",
        accent = "Español (México / Latam)",
        dirName = "vits-piper-es_MX-ald-medium",
        onnxFileName = "es_MX-ald-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-ald-medium.tar.bz2",
        minBytes = 45_000_000L
    ),
    DAVEFX_ES(
        id = "es_ES-davefx-medium",
        displayName = "David (Narrador Clásico)",
        accent = "Español (España)",
        dirName = "vits-piper-es_ES-davefx-medium",
        onnxFileName = "es_ES-davefx-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2",
        minBytes = 50_000_000L
    ),
    CARLFM_ES(
        id = "es_ES-carlfm-x_low",
        displayName = "Carlos (Ultraligero y Rápido)",
        accent = "Español (España)",
        dirName = "vits-piper-es_ES-carlfm-x_low",
        onnxFileName = "es_ES-carlfm-x_low.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-carlfm-x_low.tar.bz2",
        minBytes = 15_000_000L
    );

    companion object {
        fun fromId(id: String?): SherpaVoiceOption =
            entries.find { it.id == id } ?: DAVEFX_ES
    }
}

sealed interface SherpaDownloadState {
    data object Idle : SherpaDownloadState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : SherpaDownloadState
    data object Extracting : SherpaDownloadState
    data object Ready : SherpaDownloadState
    data class Error(val message: String) : SherpaDownloadState
}

@Singleton
class SherpaModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PREFS_NAME = "sherpa_model_prefs"
        const val KEY_SELECTED_VOICE_ID = "sherpa_selected_voice_id"

        const val MODEL_DIR_NAME = "vits-piper-es_ES-davefx-medium"
        const val ONNX_FILE_NAME = "es_ES-davefx-medium.onnx"
        const val TOKENS_FILE_NAME = "tokens.txt"
        const val ESPEAK_DIR_NAME = "espeak-ng-data"

        // Official release archive with all model weights, tokens and espeak dictionary
        private const val MODEL_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2"
        private const val MIN_ONNX_BYTES = 50_000_000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedVoice = MutableStateFlow(
        SherpaVoiceOption.fromId(prefs.getString(KEY_SELECTED_VOICE_ID, null))
    )
    val selectedVoice: StateFlow<SherpaVoiceOption> = _selectedVoice.asStateFlow()

    private val downloadMutex = Mutex()
    private val baseDir = File(context.filesDir, "sherpa-onnx")

    val modelDir: File
        get() = File(baseDir, _selectedVoice.value.dirName)
    val onnxFile: File
        get() = File(modelDir, _selectedVoice.value.onnxFileName)
    val tokensFile: File
        get() = File(modelDir, TOKENS_FILE_NAME)
    val espeakDir: File
        get() = File(modelDir, ESPEAK_DIR_NAME)

    private val _downloadState = MutableStateFlow<SherpaDownloadState>(
        if (isModelReady()) SherpaDownloadState.Ready else SherpaDownloadState.Idle
    )
    val downloadState: StateFlow<SherpaDownloadState> = _downloadState.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isVoiceDownloaded(option: SherpaVoiceOption): Boolean {
        val dir = File(baseDir, option.dirName)
        val onnx = File(dir, option.onnxFileName)
        val tokens = File(dir, TOKENS_FILE_NAME)
        val espeak = File(dir, ESPEAK_DIR_NAME)
        val voicesOk = if (option.isKokoro) File(dir, option.voicesBinFileName).exists() else true
        return onnx.exists() &&
                onnx.length() > option.minBytes &&
                tokens.exists() &&
                tokens.length() > 0 &&
                voicesOk &&
                espeak.exists() &&
                espeak.isDirectory &&
                (espeak.list()?.isNotEmpty() == true)
    }

    fun isModelReady(): Boolean {
        return isVoiceDownloaded(_selectedVoice.value)
    }

    fun createOfflineTtsInstance(): com.k2fsa.sherpa.onnx.OfflineTts? {
        val voice = _selectedVoice.value
        if (!isVoiceDownloaded(voice)) return null
        val dir = File(baseDir, voice.dirName)
        val onnxPath = File(dir, voice.onnxFileName).absolutePath
        val tokensPath = File(dir, TOKENS_FILE_NAME).absolutePath
        val espeakPath = File(dir, ESPEAK_DIR_NAME).absolutePath

        if (voice.isKokoro) {
            return try {
                val voicesPath = File(dir, voice.voicesBinFileName).absolutePath
                val kokoroClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig")
                val kokoroInstance = runCatching {
                    val inst = kokoroClass.getDeclaredConstructor().newInstance()
                    kokoroClass.getDeclaredField("model").apply { isAccessible = true }.set(inst, onnxPath)
                    kokoroClass.getDeclaredField("voices").apply { isAccessible = true }.set(inst, voicesPath)
                    kokoroClass.getDeclaredField("tokens").apply { isAccessible = true }.set(inst, tokensPath)
                    kokoroClass.getDeclaredField("dataDir").apply { isAccessible = true }.set(inst, espeakPath)
                    inst
                }.getOrElse {
                    val ctor = kokoroClass.constructors.first { it.parameterTypes.size == 7 }
                    ctor.newInstance(onnxPath, voicesPath, tokensPath, espeakPath, "", "", 1.0f)
                }
                runCatching {
                    kokoroClass.getDeclaredField("lang").apply { isAccessible = true; set(kokoroInstance, "es") }
                }

                val modelConfig = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
                    numThreads = 4,
                    debug = false
                )
                modelConfig.javaClass.getDeclaredField("kokoro").apply {
                    isAccessible = true
                    set(modelConfig, kokoroInstance)
                }
                val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(model = modelConfig)
                com.k2fsa.sherpa.onnx.OfflineTts(null, config)
            } catch (e: Exception) {
                Timber.e(e, "Tu versión de sherpa-onnx.aar no incluye soporte nativo para Kokoro")
                null
            }
        }

        val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
            model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
                vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                    model = onnxPath,
                    lexicon = "",
                    tokens = tokensPath,
                    dataDir = espeakPath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                ),
                numThreads = 4,
                debug = false
            )
        )
        return com.k2fsa.sherpa.onnx.OfflineTts(null, config)
    }

    fun selectVoiceOption(option: SherpaVoiceOption) {
        prefs.edit().putString(KEY_SELECTED_VOICE_ID, option.id).apply()
        _selectedVoice.value = option
        _downloadState.value = if (isModelReady()) SherpaDownloadState.Ready else SherpaDownloadState.Idle
    }

    suspend fun downloadAndExtractModel(): Result<Unit> = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (isModelReady()) {
                _downloadState.value = SherpaDownloadState.Ready
                return@withLock Result.success(Unit)
            }

            val currentVoice = _selectedVoice.value
            val tempArchive = File(context.cacheDir, "sherpa_model_temp_${currentVoice.id}_${System.currentTimeMillis()}.tar.bz2")
            try {
                Timber.i("Iniciando descarga del modelo Sherpa ONNX desde ${currentVoice.downloadUrl}")
                _downloadState.value = SherpaDownloadState.Downloading(0f, 0L, 0L)

                val request = Request.Builder()
                    .url(currentVoice.downloadUrl)
                    .header("User-Agent", "WoodsReader/1.0")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("Error HTTP en descarga: ${response.code} ${response.message}")
                }

                val body = response.body ?: throw IOException("Cuerpo de respuesta vacío")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                tempArchive.parentFile?.mkdirs()
                tempArchive.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var lastUpdateMs = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateMs > 150 || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                                lastUpdateMs = now
                                val progress = if (totalBytes > 0) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                _downloadState.value = SherpaDownloadState.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                        output.flush()
                    }
                }

                Timber.i("Descarga completada ($downloadedBytes bytes). Iniciando descompresión...")
                _downloadState.value = SherpaDownloadState.Extracting

                baseDir.mkdirs()
                val canonicalBaseDir = baseDir.canonicalFile.toPath()

                FileInputStream(tempArchive).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        BZip2CompressorInputStream(bis).use { bz2 ->
                            TarArchiveInputStream(bz2).use { tar ->
                                var entry = tar.nextEntry as? TarArchiveEntry
                                while (entry != null) {
                                    // Rechazar entradas simbólicas y enlaces rígidos del archivo tar
                                    if (entry.isSymbolicLink || entry.isLink) {
                                        throw SecurityException("Entrada no permitida en archivo tar (enlace simbólico o duro): ${entry.name}")
                                    }

                                    // 1. Normalización léxica y comparación estricta de componentes de ruta con Path.startsWith(Path)
                                    val targetPath = canonicalBaseDir.resolve(entry.name).normalize()
                                    if (!targetPath.startsWith(canonicalBaseDir)) {
                                        throw SecurityException("Ruta no segura detectada en tar (escape léxico): ${entry.name}")
                                    }

                                    // 2. Verificar que el destino no sea un enlace simbólico preexistente
                                    if (Files.isSymbolicLink(targetPath)) {
                                        throw SecurityException("El destino de extracción no puede ser un enlace simbólico: ${entry.name}")
                                    }

                                    val destFile = targetPath.toFile()

                                    if (entry.isDirectory) {
                                        destFile.mkdirs()
                                        val canonicalDir = destFile.canonicalFile.toPath()
                                        if (!canonicalDir.startsWith(canonicalBaseDir)) {
                                            throw SecurityException("Directorio escapa del límite seguro mediante enlaces: ${entry.name}")
                                        }
                                    } else {
                                        val parent = destFile.parentFile
                                        if (parent != null) {
                                            parent.mkdirs()
                                            val canonicalParent = parent.canonicalFile.toPath()
                                            if (!canonicalParent.startsWith(canonicalBaseDir)) {
                                                throw SecurityException("Directorio contenedor escapa mediante enlaces: ${entry.name}")
                                            }
                                        }

                                        destFile.outputStream().use { fos ->
                                            tar.copyTo(fos)
                                        }

                                        // Verificar el archivo resultante en el sistema de archivos
                                        val canonicalDest = destFile.canonicalFile.toPath()
                                        if (!canonicalDest.startsWith(canonicalBaseDir)) {
                                            destFile.delete()
                                            throw SecurityException("Archivo extraído escapa mediante enlaces: ${entry.name}")
                                        }
                                    }
                                    entry = tar.nextEntry as? TarArchiveEntry
                                }
                            }
                        }
                    }
                }

                if (isModelReady()) {
                    Timber.i("Modelo Sherpa ONNX extraído y verificado con éxito en ${modelDir.absolutePath}")
                    _downloadState.value = SherpaDownloadState.Ready
                    Result.success(Unit)
                } else {
                    val errorMsg = "Los archivos del modelo no se verificaron correctamente tras la descompresión"
                    Timber.e(errorMsg)
                    _downloadState.value = SherpaDownloadState.Error(errorMsg)
                    Result.failure(IllegalStateException(errorMsg))
                }
            } catch (e: Exception) {
                Timber.e(e, "Fallo al descargar o descomprimir el modelo Sherpa ONNX")
                val message = e.localizedMessage ?: "Error de descarga"
                _downloadState.value = SherpaDownloadState.Error(message)
                Result.failure(e)
            } finally {
                if (tempArchive.exists()) {
                    tempArchive.delete()
                }
            }
        }
    }

    fun deleteModel(): Boolean {
        return try {
            val deleted = modelDir.deleteRecursively()
            _downloadState.value = SherpaDownloadState.Idle
            Timber.i("Modelo Sherpa ONNX eliminado: $deleted")
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar modelo Sherpa ONNX")
            false
        }
    }

    fun deleteVoice(option: SherpaVoiceOption): Boolean {
        return try {
            val dir = File(baseDir, option.dirName)
            val deleted = dir.deleteRecursively()
            if (_selectedVoice.value == option) {
                _downloadState.value = SherpaDownloadState.Idle
            }
            Timber.i("Modelo Sherpa ONNX (${option.displayName}) eliminado: $deleted")
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar modelo Sherpa ONNX (${option.displayName})")
            false
        }
    }

    fun getModelSizeBytes(): Long {
        if (!modelDir.exists()) return 0L
        return modelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
