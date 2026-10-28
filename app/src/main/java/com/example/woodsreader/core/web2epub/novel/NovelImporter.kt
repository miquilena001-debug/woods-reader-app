package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ImportStage {
    IDLE,
    FETCHING_INDEX,
    FETCHING_CHAPTERS,
    DOWNLOADING_IMAGES,
    ASSEMBLING_EPUB,
    COMPLETED,
    PAUSED_CHALLENGE,
    ERROR
}

data class ImportProgress(
    val stage: ImportStage = ImportStage.IDLE,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val currentChapterTitle: String = "",
    val error: String? = null,
    val outputFile: File? = null
)

/**
 * Orquestador principal de importación de novelas web a EPUB 3.
 * Sin dependencias directas de Android para permitir pruebas unitarias rápidas.
 */
class NovelImporter(
    private val fetcher: PageFetcher,
    private val profileRegistry: SiteProfileRegistry,
    private val store: ImportStore,
    private val imageDownloader: ImageDownloader? = null,
    private val assembler: EpubAssembler = EpubAssembler()
) {
    private val _progressFlow = MutableStateFlow(ImportProgress())
    val progressFlow: Flow<ImportProgress> = _progressFlow.asStateFlow()

    suspend fun importNovel(
        novelUrl: String,
        outputDirectory: File,
        startIndex: Int = 0,
        endIndex: Int? = null,
        forceReverse: Boolean? = null
    ): File? {
        val profile = profileRegistry.forUrl(novelUrl)
        val jobId = ImportStore.computeJobId(novelUrl)

        _progressFlow.value = ImportProgress(stage = ImportStage.FETCHING_INDEX)

        // 1. Obtener o restaurar índice de la novela
        val existingState = store.loadState(jobId)
        val indexLoader = NovelIndexLoader(fetcher, profileRegistry)
        val novelIndex = try {
            indexLoader.load(novelUrl)
        } catch (challenge: FetchFailure.Challenge) {
            _progressFlow.value = ImportProgress(stage = ImportStage.PAUSED_CHALLENGE)
            return null
        } catch (e: Exception) {
            if (existingState != null) {
                NovelIndex(
                    indexUrl = existingState.novelUrl,
                    title = existingState.title,
                    author = existingState.author,
                    description = existingState.description,
                    coverUrl = existingState.coverUrl,
                    language = "es",
                    chapters = existingState.chapters.map { ChapterRef(it.index, it.title, it.url) },
                    looksReversed = false,
                    profileId = profile.id,
                    warnings = emptyList()
                )
            } else {
                _progressFlow.value = ImportProgress(stage = ImportStage.ERROR, error = e.message ?: "Error al cargar índice")
                return null
            }
        }

        // Orden de capítulos
        val shouldReverse = forceReverse ?: profile.reverseToc ?: novelIndex.looksReversed
        val orderedChapters = if (shouldReverse && existingState == null) {
            novelIndex.chapters.reversed()
        } else {
            novelIndex.chapters
        }

        // Filtrar rango de capítulos a descargar
        val clampedStart = startIndex.coerceIn(0, orderedChapters.size)
        val clampedEnd = (endIndex ?: orderedChapters.size).coerceIn(clampedStart, orderedChapters.size)
        val targetChapters = orderedChapters.subList(clampedStart, clampedEnd)

        if (targetChapters.isEmpty()) {
            _progressFlow.value = ImportProgress(stage = ImportStage.ERROR, error = "No se encontraron capítulos para descargar.")
            return null
        }

        // 2. Gestionar portada
        var coverFileName: String? = existingState?.coverLocalPath
        if (coverFileName == null && !novelIndex.coverUrl.isNullOrBlank() && imageDownloader != null) {
            val downloadedCover = imageDownloader.downloadImage(novelIndex.coverUrl, referer = novelUrl)
            if (downloadedCover != null) {
                val ext = downloadedCover.zipPath.substringAfterLast(".")
                coverFileName = store.saveCover(jobId, ext, downloadedCover.bytes)
            }
        }

        // 3. Inicializar estado del trabajo
        val storedChapters = existingState?.chapters?.toMutableList() ?: mutableListOf()
        var jobState = ImportJobState(
            jobId = jobId,
            novelUrl = novelUrl,
            title = novelIndex.title,
            author = novelIndex.author ?: "Desconocido",
            description = novelIndex.description ?: "",
            coverUrl = novelIndex.coverUrl,
            coverLocalPath = coverFileName,
            totalChapters = targetChapters.size,
            status = JobStatus.IN_PROGRESS,
            chapters = storedChapters
        )
        store.saveState(jobState)

        // 4. Descargar y extraer capítulos pendientes
        val contentExtractor = ContentExtractor(profile)
        val downloadedImages = mutableListOf<EpubImageInput>()

        // Cargar portada si existe
        if (coverFileName != null) {
            val coverBytes = store.loadCover(jobId, coverFileName)
            if (coverBytes != null) {
                val sniffed = ImageSniffer.sniff(coverBytes)
                downloadedImages.add(
                    EpubImageInput(
                        relativeZipPath = "OEBPS/images/$coverFileName",
                        mimeType = sniffed?.mimeType ?: "image/jpeg",
                        bytes = coverBytes,
                        isCover = true
                    )
                )
            }
        }

        for ((idx, chRef) in targetChapters.withIndex()) {
            val chapterNumber = idx + 1
            val expectedFileName = "chapter_%04d.xhtml".format(chapterNumber)

            // Comprobar si ya fue descargado previamente
            val existingChapter = storedChapters.firstOrNull { it.url == chRef.url && it.status == ChapterStatus.OK.name }
            if (existingChapter != null && store.loadChapterContent(jobId, existingChapter.fileName) != null) {
                _progressFlow.value = ImportProgress(
                    stage = ImportStage.FETCHING_CHAPTERS,
                    currentChapter = chapterNumber,
                    totalChapters = targetChapters.size,
                    currentChapterTitle = chRef.title
                )
                continue
            }

            _progressFlow.value = ImportProgress(
                stage = ImportStage.FETCHING_CHAPTERS,
                currentChapter = chapterNumber,
                totalChapters = targetChapters.size,
                currentChapterTitle = chRef.title
            )

            val page = try {
                fetcher.get(chRef.url, referer = novelUrl)
            } catch (c: FetchFailure.Challenge) {
                jobState = jobState.copy(status = JobStatus.PAUSED_CHALLENGE)
                store.saveState(jobState)
                _progressFlow.value = ImportProgress(
                    stage = ImportStage.PAUSED_CHALLENGE,
                    currentChapter = chapterNumber,
                    totalChapters = targetChapters.size,
                    currentChapterTitle = chRef.title
                )
                return null
            } catch (_: Exception) {
                // Registrar intento fallido pero continuar
                storedChapters.add(
                    StoredChapterInfo(
                        index = chapterNumber,
                        title = chRef.title,
                        url = chRef.url,
                        fileName = expectedFileName,
                        wordCount = 0,
                        status = ChapterStatus.EMPTY.name
                    )
                )
                continue
            }

            val html = CharsetDetector.decode(page.bytes, page.contentType)
            val extracted = contentExtractor.extract(html, page.finalUrl, fallbackTitle = chRef.title)

            // Descargar imágenes del capítulo si hay
            if (imageDownloader != null && extracted.imageSources.isNotEmpty()) {
                _progressFlow.value = ImportProgress(
                    stage = ImportStage.DOWNLOADING_IMAGES,
                    currentChapter = chapterNumber,
                    totalChapters = targetChapters.size,
                    currentChapterTitle = chRef.title
                )
                for (imgUrl in extracted.imageSources) {
                    val downloaded = imageDownloader.downloadImage(imgUrl, referer = chRef.url)
                    if (downloaded != null) {
                        store.saveImage(jobId, downloaded.zipPath, downloaded.bytes)
                        downloadedImages.add(
                            EpubImageInput(
                                relativeZipPath = downloaded.zipPath,
                                mimeType = downloaded.mimeType,
                                bytes = downloaded.bytes
                            )
                        )
                    }
                }
            }

            // Guardar capítulo en disco
            store.saveChapterContent(jobId, expectedFileName, extracted.bodyXhtml)
            storedChapters.removeAll { it.url == chRef.url }
            storedChapters.add(
                StoredChapterInfo(
                    index = chapterNumber,
                    title = extracted.title.ifBlank { chRef.title },
                    url = chRef.url,
                    fileName = expectedFileName,
                    wordCount = extracted.wordCount,
                    status = extracted.status.name
                )
            )

            jobState = jobState.copy(chapters = storedChapters)
            store.saveState(jobState)
        }

        // 5. Ensamblar EPUB 3.0
        _progressFlow.value = ImportProgress(
            stage = ImportStage.ASSEMBLING_EPUB,
            currentChapter = targetChapters.size,
            totalChapters = targetChapters.size
        )

        val epubChapters = mutableListOf<EpubChapterInput>()
        val seenFileNames = mutableSetOf<String>()
        val sortedStored = storedChapters.sortedBy { it.index }
        for (info in sortedStored) {
            if (seenFileNames.add(info.fileName)) {
                val content = store.loadChapterContent(jobId, info.fileName) ?: "<p></p>"
                epubChapters.add(
                    EpubChapterInput(
                        title = info.title,
                        fileName = info.fileName,
                        contentXhtml = content
                    )
                )
            }
        }

        // Sanitizar nombre de archivo para el EPUB sin timestamps ni números innecesarios
        val safeNovelName = novelIndex.title
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { "Novela" }

        val targetEpubFile = File(outputDirectory, "$safeNovelName.epub")
        val metadata = EpubMetadata(
            title = novelIndex.title,
            author = novelIndex.author ?: "Desconocido",
            description = novelIndex.description ?: "",
            sourceUrl = novelUrl,
            language = novelIndex.language ?: "es"
        )

        assembler.assemble(
            metadata = metadata,
            chapters = epubChapters,
            images = downloadedImages.distinctBy { it.relativeZipPath },
            outputFile = targetEpubFile
        )

        jobState = jobState.copy(status = JobStatus.COMPLETED)
        store.saveState(jobState)

        _progressFlow.value = ImportProgress(
            stage = ImportStage.COMPLETED,
            currentChapter = targetChapters.size,
            totalChapters = targetChapters.size,
            outputFile = targetEpubFile
        )

        return targetEpubFile
    }
}
