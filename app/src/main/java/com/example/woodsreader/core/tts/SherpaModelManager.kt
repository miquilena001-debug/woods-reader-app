package com.example.woodsreader.core.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val speakerId: Int = 0,
    val languageCode: String = "es"
) {
    // 🇪🇸 Español (es)
    CLAUDE_MX(
        id = "es_MX-claude-high",
        displayName = "Claudia (Estudio Latam HD)",
        accent = "Español (México / Latam) · Femenina Cálida",
        dirName = "vits-piper-es_MX-claude-high",
        onnxFileName = "es_MX-claude-high.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high.tar.bz2",
        minBytes = 50_000_000L,
        languageCode = "es"
    ),
    ALD_MX(
        id = "es_MX-ald-medium",
        displayName = "Alejandro (Narrador Latam)",
        accent = "Español (México / Latam) · Masculina Firme",
        dirName = "vits-piper-es_MX-ald-medium",
        onnxFileName = "es_MX-ald-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-ald-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "es"
    ),
    DAVEFX_ES(
        id = "es_ES-davefx-medium",
        displayName = "David (Estudio España)",
        accent = "Español (España) · Masculina Documental",
        dirName = "vits-piper-es_ES-davefx-medium",
        onnxFileName = "es_ES-davefx-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "es"
    ),
    CARLFM_ES(
        id = "es_ES-carlfm-x_low",
        displayName = "Carlos (Ultra-Rápido)",
        accent = "Español (España) · Masculina Ligera",
        dirName = "vits-piper-es_ES-carlfm-x_low",
        onnxFileName = "es_ES-carlfm-x_low.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-carlfm-x_low.tar.bz2",
        minBytes = 15_000_000L,
        languageCode = "es"
    ),

    // 🇺🇸🇬🇧 Inglés (en)
    LESSAC_US(
        id = "en_US-lessac-medium",
        displayName = "Lessac (Audiobook US HD)",
        accent = "English (US) · Femenina Expresiva",
        dirName = "vits-piper-en_US-lessac-medium",
        onnxFileName = "en_US-lessac-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "en"
    ),
    RYAN_US(
        id = "en_US-ryan-medium",
        displayName = "Ryan (Narrator US)",
        accent = "English (US) · Masculina Profunda",
        dirName = "vits-piper-en_US-ryan-medium",
        onnxFileName = "en_US-ryan-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "en"
    ),
    AMY_US(
        id = "en_US-amy-medium",
        displayName = "Amy (Storyteller US)",
        accent = "English (US) · Femenina Suave",
        dirName = "vits-piper-en_US-amy-medium",
        onnxFileName = "en_US-amy-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "en"
    ),
    ALAN_GB(
        id = "en_GB-alan-medium",
        displayName = "Alan (British Studio)",
        accent = "English (UK) · Masculina Británica",
        dirName = "vits-piper-en_GB-alan-medium",
        onnxFileName = "en_GB-alan-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "en"
    ),

    // 🇧🇷 Portugués (pt)
    FABER_BR(
        id = "pt_BR-faber-medium",
        displayName = "Faber (Estúdio Brasil)",
        accent = "Português (Brasil) · Masculina Natural",
        dirName = "vits-piper-pt_BR-faber-medium",
        onnxFileName = "pt_BR-faber-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-faber-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "pt"
    ),

    // 🇫🇷 Francés (fr)
    SIWIS_FR(
        id = "fr_FR-siwis-medium",
        displayName = "Siwis (Studio France)",
        accent = "Français (France) · Femenina Clara",
        dirName = "vits-piper-fr_FR-siwis-medium",
        onnxFileName = "fr_FR-siwis-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-siwis-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "fr"
    ),

    // 🇩🇪 Alemán (de)
    THORSTEN_DE(
        id = "de_DE-thorsten-medium",
        displayName = "Thorsten (Studio Deutschland)",
        accent = "Deutsch (Deutschland) · Masculina Cálida",
        dirName = "vits-piper-de_DE-thorsten-medium",
        onnxFileName = "de_DE-thorsten-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten-medium.tar.bz2",
        minBytes = 45_000_000L,
        languageCode = "de"
    ),

    // 🇮🇹 Italiano (it)
    RICCARDO_IT(
        id = "it_IT-riccardo-x_low",
        displayName = "Riccardo (Studio Italia)",
        accent = "Italiano (Italia) · Masculina Fluida",
        dirName = "vits-piper-it_IT-riccardo-x_low",
        onnxFileName = "it_IT-riccardo-x_low.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-riccardo-x_low.tar.bz2",
        minBytes = 15_000_000L,
        languageCode = "it"
    );

    val language: SupportedTtsLanguage get() = SupportedTtsLanguage.fromCode(languageCode)

    companion object {
        val AVAILABLE_MODELS: List<SherpaVoiceOption> = entries

        fun fromId(id: String?): SherpaVoiceOption =
            entries.find { it.id == id } ?: ALD_MX
    }
}

typealias LocalVoiceModel = SherpaVoiceOption

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

    private val _selectedLanguageFilter = MutableStateFlow(SupportedTtsLanguage.SPANISH)
    val selectedLanguageFilter: StateFlow<SupportedTtsLanguage> = _selectedLanguageFilter.asStateFlow()

    fun setSelectedLanguageFilter(language: SupportedTtsLanguage) {
        _selectedLanguageFilter.value = language
    }

    private val _autoDetectBookLanguage = MutableStateFlow(
        prefs.getBoolean("auto_detect_book_language", true)
    )
    val autoDetectBookLanguage: StateFlow<Boolean> = _autoDetectBookLanguage.asStateFlow()

    fun setAutoDetectBookLanguage(enabled: Boolean) {
        _autoDetectBookLanguage.value = enabled
        prefs.edit().putBoolean("auto_detect_book_language", enabled).apply()
    }

    private val _downloadedModelIds = MutableStateFlow<Set<String>>(getCurrentlyDownloadedModelIds())
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds.asStateFlow()

    fun refreshDownloadedModels() {
        _downloadedModelIds.value = getCurrentlyDownloadedModelIds()
    }

    fun getCurrentlyDownloadedModelIds(): Set<String> {
        return SherpaVoiceOption.entries
            .filter { isVoiceDownloaded(it) }
            .map { it.id }
            .toSet()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val kokoroDir = File(baseDir, "kokoro-multi-lang-v1_0")
                if (kokoroDir.exists()) {
                    val deleted = kokoroDir.deleteRecursively()
                    Timber.i("Directorio de Kokoro-82M eliminado para liberar espacio: $deleted")
                }
                val kokoroArchive = File(baseDir, "kokoro-multi-lang-v1_0.tar.bz2")
                if (kokoroArchive.exists()) {
                    kokoroArchive.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al limpiar archivos residuales de Kokoro-82M")
            }
        }
    }

    fun isVoiceDownloaded(option: SherpaVoiceOption): Boolean {
        val dir = File(baseDir, option.dirName)
        val onnx = File(dir, option.onnxFileName)
        val tokens = File(dir, TOKENS_FILE_NAME)
        val espeak = File(dir, ESPEAK_DIR_NAME)
        return onnx.exists() &&
                onnx.length() > option.minBytes &&
                tokens.exists() &&
                tokens.length() > 0 &&
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
                numThreads = 2,
                debug = false
            )
        )
        return com.k2fsa.sherpa.onnx.OfflineTts(null, config)
    }

    fun splitIntoSafeClauses(text: String, maxChars: Int = 110): List<String> {
        val cleaned = text.replace(Regex("""\s+"""), " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= maxChars) return listOf(cleaned)

        val result = mutableListOf<String>()
        val primaryParts = cleaned.split(Regex("""(?<=[.!?;:…—])\s+|(?<=,)\s+"""))
        var current = StringBuilder()

        for (part in primaryParts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (current.isEmpty()) {
                if (trimmed.length <= maxChars) {
                    current.append(trimmed)
                } else {
                    // Cortar por palabras si una cláusula sin comas supera maxChars
                    val words = trimmed.split(" ")
                    for (w in words) {
                        if (current.length + w.length + 1 > maxChars && current.isNotEmpty()) {
                            result.add(current.toString().trim())
                            current = StringBuilder(w)
                        } else {
                            if (current.isNotEmpty()) current.append(" ")
                            current.append(w)
                        }
                    }
                }
            } else if (current.length + 1 + trimmed.length <= maxChars) {
                current.append(" ").append(trimmed)
            } else {
                result.add(current.toString().trim())
                current = StringBuilder(trimmed)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result.filter { it.isNotBlank() }
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
                    refreshDownloadedModels()
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
            refreshDownloadedModels()
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
            refreshDownloadedModels()
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

    fun deleteDownloadedModel(modelId: String): Boolean {
        val option = SherpaVoiceOption.fromId(modelId)
        return deleteVoice(option)
    }

    fun findBestVoiceForLanguage(language: SupportedTtsLanguage): SherpaVoiceOption? {
        return SherpaVoiceOption.entries
            .filter { it.languageCode == language.code && isVoiceDownloaded(it) }
            .firstOrNull()
    }

    fun getModelSizeBytes(): Long {
        if (!modelDir.exists()) return 0L
        return modelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    fun getVoiceSizeBytes(option: SherpaVoiceOption): Long {
        val dir = File(baseDir, option.dirName)
        if (!dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
