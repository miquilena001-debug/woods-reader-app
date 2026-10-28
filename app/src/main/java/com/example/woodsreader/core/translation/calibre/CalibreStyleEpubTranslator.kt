package com.example.woodsreader.core.translation.calibre

import android.content.Context
import com.example.woodsreader.core.translation.calibre.inplace.EpubInPlaceTranslator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EpubTranslationProgress(
    val currentStep: String,
    val currentChapter: Int,
    val totalChapters: Int,
    val progressFraction: Float
)

/**
 * Motor de traducción completa de EPUBs estilo Calibre (Ebook Translator Plugin):
 * 100% In-Place, sin APIs de pago ni claves. Conserva portadas, imágenes, fuentes,
 * estilos CSS, maquetación y jerarquía de capítulos del EPUB original.
 */
@Singleton
class CalibreStyleEpubTranslator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleFreeNetworkTranslator: GoogleFreeNetworkTranslator,
    private val epubInPlaceTranslator: EpubInPlaceTranslator
) {
    private val _isTranslatingBook = MutableStateFlow(false)
    val isTranslatingBook: StateFlow<Boolean> = _isTranslatingBook.asStateFlow()

    private val _progressState = MutableStateFlow(EpubTranslationProgress("Iniciando...", 0, 0, 0f))
    val progressState: StateFlow<EpubTranslationProgress> = _progressState.asStateFlow()

    suspend fun translateEntireEpub(
        sourceEpubFile: File,
        sourceLanguageCode: String = "en",
        targetLanguageCode: String = "es"
    ): Result<File> = withContext(Dispatchers.IO) {
        val actualFile = when {
            sourceEpubFile.path.startsWith("file://") -> File(sourceEpubFile.path.removePrefix("file://"))
            sourceEpubFile.path.startsWith("file:") -> File(sourceEpubFile.path.removePrefix("file:"))
            else -> sourceEpubFile
        }
        if (!actualFile.exists() || !actualFile.canRead()) {
            return@withContext Result.failure(
                IllegalArgumentException("El archivo EPUB no existe o no se puede leer: ${actualFile.absolutePath}")
            )
        }
        if (!_isTranslatingBook.compareAndSet(false, true)) {
            return@withContext Result.failure(IllegalStateException("Ya hay una traducción de libro en curso"))
        }

        val outputDir = File(context.filesDir, "translated_epubs").apply { mkdirs() }
        val baseName = actualFile.nameWithoutExtension
        val translatedFile = File(outputDir, "${baseName}_${targetLanguageCode.uppercase()}.epub")

        try {
            _progressState.value = EpubTranslationProgress("Iniciando motor Calibre In-Place...", 0, 0, 0.05f)

            epubInPlaceTranslator.translate(
                sourceFile = actualFile,
                outputFile = translatedFile,
                translator = googleFreeNetworkTranslator,
                sourceLang = sourceLanguageCode,
                targetLang = targetLanguageCode,
                cacheDir = context.cacheDir,
                onProgress = { progress ->
                    _progressState.value = progress
                }
            )
        } finally {
            _isTranslatingBook.value = false
        }
    }
}
