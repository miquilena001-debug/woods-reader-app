package com.example.woodsreader.core.translation.calibre.inplace

import android.content.Context
import com.example.woodsreader.core.translation.calibre.EpubTranslationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class EpubInPlaceTranslator @Inject constructor() {

    suspend fun translate(
        sourceFile: File,
        outputFile: File,
        translator: TextTranslator,
        sourceLang: String = "en",
        targetLang: String = "es",
        cacheDir: File,
        onProgress: (EpubTranslationProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return@withContext Result.failure(
                IllegalArgumentException("El archivo original no existe o no es accesible: ${sourceFile.absolutePath}")
            )
        }

        val tempWorkingDir = File(cacheDir, "epub_tx_${System.currentTimeMillis()}").apply { mkdirs() }
        val modifiedFiles = mutableMapOf<String, File>()

        try {
            onProgress(EpubTranslationProgress("Analizando estructura del libro...", 0, 0, 0.05f))

            ZipFile(sourceFile).use { zip ->
                val pkg = EpubPackageParser.parse(zip)
                val totalChapters = pkg.readingOrderZipPaths.size
                if (totalChapters == 0) {
                    throw IllegalArgumentException("El archivo EPUB no contiene capítulos de lectura válidos.")
                }

                // 1. Traducir OPF (metadatos de idioma y título)
                val opfEntry = zip.getEntry(pkg.opfPath)
                if (opfEntry != null) {
                    val rawOpf = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val translatedOpf = translateOpf(rawOpf, targetLang)
                    val opfFile = File(tempWorkingDir, "content.opf").apply {
                        writeText(translatedOpf, Charsets.UTF_8)
                    }
                    modifiedFiles[pkg.opfPath] = opfFile
                }

                // 2. Traducir TOC (NCX y/o NAV)
                if (pkg.ncxZipPath != null) {
                    val ncxEntry = zip.getEntry(pkg.ncxZipPath)
                    if (ncxEntry != null) {
                        val rawNcx = zip.getInputStream(ncxEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val translatedNcx = translateNcx(rawNcx, translator, sourceLang, targetLang)
                        val ncxFile = File(tempWorkingDir, "toc.ncx").apply {
                            writeText(translatedNcx, Charsets.UTF_8)
                        }
                        modifiedFiles[pkg.ncxZipPath] = ncxFile
                    }
                }

                if (pkg.navZipPath != null) {
                    val navEntry = zip.getEntry(pkg.navZipPath)
                    if (navEntry != null) {
                        val rawNav = zip.getInputStream(navEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val translatedNav = translateNav(rawNav, translator, sourceLang, targetLang)
                        val navFile = File(tempWorkingDir, "nav.xhtml").apply {
                            writeText(translatedNav, Charsets.UTF_8)
                        }
                        modifiedFiles[pkg.navZipPath] = navFile
                    }
                }

                // 3. Traducir capítulos en orden de lectura
                for ((idx, zipPath) in pkg.readingOrderZipPaths.withIndex()) {
                    coroutineContext.ensureActive()
                    val chapterNum = idx + 1
                    onProgress(
                        EpubTranslationProgress(
                            currentStep = "Traduciendo capítulo $chapterNum de $totalChapters",
                            currentChapter = chapterNum,
                            totalChapters = totalChapters,
                            progressFraction = 0.05f + 0.90f * (idx.toFloat() / totalChapters)
                        )
                    )

                    val chapterEntry = zip.getEntry(zipPath)
                        ?: zip.entries().asSequence().firstOrNull { it.name.equals(zipPath, ignoreCase = true) }
                    if (chapterEntry != null) {
                        val rawXhtml = zip.getInputStream(chapterEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val translatedXhtml = XhtmlTranslatorDoc.translate(
                            xhtmlContent = rawXhtml,
                            translator = translator,
                            sourceLang = sourceLang,
                            targetLang = targetLang
                        )
                        val chFile = File(tempWorkingDir, "ch_${idx}.xhtml").apply {
                            writeText(translatedXhtml, Charsets.UTF_8)
                        }
                        modifiedFiles[chapterEntry.name.replace('\\', '/')] = chFile
                    }
                }

                // 4. Ensamblar EPUB In-Place conservando imágenes, estilos y fuentes
                coroutineContext.ensureActive()
                onProgress(
                    EpubTranslationProgress(
                        currentStep = "Reconstruyendo archivo EPUB con diseño original...",
                        currentChapter = totalChapters,
                        totalChapters = totalChapters,
                        progressFraction = 0.96f
                    )
                )

                EpubZipWriter.writeInPlaceFiles(
                    sourceZipFile = zip,
                    outputFile = outputFile,
                    modifiedFiles = modifiedFiles
                )
            }

            // 5. Verificación final de integridad
            ZipFile(outputFile).use { verifyZip ->
                EpubPackageParser.parse(verifyZip)
            }

            onProgress(
                EpubTranslationProgress(
                    currentStep = "¡Traducción completada con éxito!",
                    currentChapter = 1,
                    totalChapters = 1,
                    progressFraction = 1.0f
                )
            )

            Result.success(outputFile)
        } catch (e: Throwable) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            tempWorkingDir.deleteRecursively()
        }
    }

    fun translateOpf(opfXml: String, targetLang: String): String {
        val doc = Jsoup.parse(opfXml, "", Parser.xmlParser())
        doc.outputSettings()
            .prettyPrint(false)
            .syntax(Document.OutputSettings.Syntax.xml)
            .charset(Charsets.UTF_8)

        val metadataEl = doc.selectFirst("metadata")

        // 1. Idioma
        val langEl = doc.selectFirst("metadata > dc|language, metadata > language")
        if (langEl != null) {
            langEl.text(targetLang)
        } else {
            metadataEl?.appendElement("dc:language")?.text(targetLang)
        }

        // 2. Título (agregar sufijo de idioma si no lo tiene)
        val titleEl = doc.selectFirst("metadata > dc|title, metadata > title")
        if (titleEl != null) {
            val originalTitle = titleEl.text().trim()
            val suffix = " (${targetLang.uppercase()})"
            if (!originalTitle.endsWith(suffix, ignoreCase = true)) {
                titleEl.text("$originalTitle$suffix")
            }
        }

        return doc.outerHtml()
    }

    suspend fun translateNcx(
        ncxXml: String,
        translator: TextTranslator,
        sourceLang: String,
        targetLang: String
    ): String {
        val doc = Jsoup.parse(ncxXml, "", Parser.xmlParser())
        doc.outputSettings()
            .prettyPrint(false)
            .syntax(Document.OutputSettings.Syntax.xml)
            .charset(Charsets.UTF_8)

        val textNodes = doc.select("navLabel > text")
        val validIndices = mutableListOf<Int>()
        val textsToTranslate = mutableListOf<String>()

        for ((idx, el) in textNodes.withIndex()) {
            val text = el.text().trim()
            if (text.isNotBlank()) {
                validIndices.add(idx)
                textsToTranslate.add(text)
            }
        }

        if (textsToTranslate.isNotEmpty()) {
            val translatedBatch = translator.translateBatch(textsToTranslate, sourceLang, targetLang)
            for (k in validIndices.indices) {
                textNodes[validIndices[k]].text(translatedBatch[k])
            }
        }

        return doc.outerHtml()
    }

    suspend fun translateNav(
        navXhtml: String,
        translator: TextTranslator,
        sourceLang: String,
        targetLang: String
    ): String {
        val doc = Jsoup.parse(navXhtml, "", Parser.xmlParser())
        doc.outputSettings()
            .prettyPrint(false)
            .syntax(Document.OutputSettings.Syntax.xml)
            .charset(Charsets.UTF_8)

        val links = doc.select("nav[epub:type=toc] ol li a, nav ol li a, nav a")
        val validIndices = mutableListOf<Int>()
        val textsToTranslate = mutableListOf<String>()

        for ((idx, el) in links.withIndex()) {
            val text = el.text().trim()
            if (text.isNotBlank()) {
                validIndices.add(idx)
                textsToTranslate.add(text)
            }
        }

        if (textsToTranslate.isNotEmpty()) {
            val translatedBatch = translator.translateBatch(textsToTranslate, sourceLang, targetLang)
            for (k in validIndices.indices) {
                links[validIndices[k]].text(translatedBatch[k])
            }
        }

        return doc.outerHtml()
    }
}
