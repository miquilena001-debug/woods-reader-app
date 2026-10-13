package com.example.woodsreader.core.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
        const val MODEL_DIR_NAME = "vits-piper-es_ES-davefx-medium"
        const val ONNX_FILE_NAME = "es_ES-davefx-medium.onnx"
        const val TOKENS_FILE_NAME = "tokens.txt"
        const val ESPEAK_DIR_NAME = "espeak-ng-data"

        // Official release archive with all model weights, tokens and espeak dictionary
        private const val MODEL_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2"
        private const val MIN_ONNX_BYTES = 50_000_000L
    }

    private val baseDir = File(context.filesDir, "sherpa-onnx")
    val modelDir = File(baseDir, MODEL_DIR_NAME)
    val onnxFile = File(modelDir, ONNX_FILE_NAME)
    val tokensFile = File(modelDir, TOKENS_FILE_NAME)
    val espeakDir = File(modelDir, ESPEAK_DIR_NAME)

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

    fun isModelReady(): Boolean {
        return onnxFile.exists() &&
                onnxFile.length() > MIN_ONNX_BYTES &&
                tokensFile.exists() &&
                tokensFile.length() > 0 &&
                espeakDir.exists() &&
                espeakDir.isDirectory &&
                (espeakDir.list()?.isNotEmpty() == true)
    }

    suspend fun downloadAndExtractModel(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = SherpaDownloadState.Ready
            return@withContext Result.success(Unit)
        }

        val tempArchive = File(context.cacheDir, "sherpa_model_temp.tar.bz2")
        try {
            Timber.i("Iniciando descarga del modelo Sherpa ONNX desde $MODEL_DOWNLOAD_URL")
            _downloadState.value = SherpaDownloadState.Downloading(0f, 0L, 0L)

            val request = Request.Builder()
                .url(MODEL_DOWNLOAD_URL)
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
            val canonicalBasePath = baseDir.canonicalPath

            FileInputStream(tempArchive).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    BZip2CompressorInputStream(bis).use { bz2 ->
                        TarArchiveInputStream(bz2).use { tar ->
                            var entry = tar.nextEntry
                            while (entry != null) {
                                val destFile = File(baseDir, entry.name)
                                if (!destFile.canonicalPath.startsWith(canonicalBasePath)) {
                                    throw SecurityException("Ruta no segura detectada en tar: ${entry.name}")
                                }

                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    destFile.parentFile?.mkdirs()
                                    destFile.outputStream().use { fos ->
                                        tar.copyTo(fos)
                                    }
                                }
                                entry = tar.nextEntry
                            }
                        }
                    }
                }
            }

            tempArchive.delete()

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
            tempArchive.delete()
            val message = e.localizedMessage ?: "Error de descarga"
            _downloadState.value = SherpaDownloadState.Error(message)
            Result.failure(e)
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

    fun getModelSizeBytes(): Long {
        if (!modelDir.exists()) return 0L
        return modelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
