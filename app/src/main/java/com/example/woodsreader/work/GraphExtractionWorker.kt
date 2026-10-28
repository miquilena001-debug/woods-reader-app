package com.example.woodsreader.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.woodsreader.core.pipeline.EpubPackageSanitizer
import com.example.woodsreader.data.graph.GraphEntity
import com.example.woodsreader.data.graph.GraphMention
import com.example.woodsreader.data.graph.GraphRelation
import com.example.woodsreader.data.graph.GraphRepository
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.util.EntityHeuristics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import java.io.File

@HiltWorker
class GraphExtractionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val graphRepository: GraphRepository,
    private val bookRepository: BookRepository,
    private val publicationOpener: PublicationOpener,
    private val assetRetriever: AssetRetriever
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "GraphExtractionWorker"
        const val KEY_BOOK_ID = "bookId"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val CHANNEL_ID = "graph_extraction_channel"
        const val NOTIFICATION_ID = 2001
        const val RESULT_NOTIFICATION_ID = 2002
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private data class CandidateStats(
        var totalCount: Int = 0,
        var midSentenceCount: Int = 0,
        var firstChapter: Int = 0
    )

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: run {
            Log.e(TAG, "Falta parámetro bookId en inputData")
            return Result.failure()
        }

        val book = bookRepository.getBook(bookId) ?: run {
            Log.e(TAG, "No se encontró el libro con bookId=$bookId")
            return Result.failure()
        }

        createNotificationChannel()
        runCatching {
            setForeground(createForegroundInfo(0, "Iniciando análisis de ${book.title}..."))
        }

        // 1. Abrir publicación en modo seguro para streaming capítulo a capítulo
        val bookFile = File(book.filePath)
        val assetResult = if (bookFile.exists()) {
            assetRetriever.retrieve(bookFile)
        } else {
            val url = AbsoluteUrl(book.filePath)
            if (url != null) assetRetriever.retrieve(url) else null
        }

        val asset = assetResult?.getOrNull() ?: run {
            Log.e(TAG, "No se pudo recuperar el asset para ${book.title}")
            return Result.failure()
        }

        val sanitizedAsset = EpubPackageSanitizer.transformAsset(asset)
        val publication = publicationOpener.open(sanitizedAsset, allowUserInteraction = false).getOrNull() ?: run {
            Log.e(TAG, "No se pudo abrir la publicación para ${book.title}")
            return Result.failure()
        }

        try {
            val readingOrder = publication.readingOrder
            val totalChapters = readingOrder.size
            if (totalChapters == 0) {
                Log.w(TAG, "El libro no tiene capítulos en readingOrder")
                return Result.success()
            }

            Log.i(TAG, "Iniciando extracción de grafo para '${book.title}' ($totalChapters capítulos)...")

            // ════════════════════════════════════════════════════════════════
            // PASADA 1: Conteo y detección de candidatos
            // ════════════════════════════════════════════════════════════════
            val candidateMap = HashMap<String, CandidateStats>(1024)

            for ((chapterIdx, link) in readingOrder.withIndex()) {
                if (isStopped) {
                    Log.i(TAG, "Extracción detenida por el usuario/sistema")
                    return Result.retry()
                }

                val progress = (chapterIdx * 50) / totalChapters
                updateProgress(progress, chapterIdx + 1, totalChapters, 1, book.title, bookId)

                val resource = publication.get(link) ?: continue
                val rawHtml = try {
                    val bytes = resource.read().getOrNull() ?: continue
                    String(bytes, Charsets.UTF_8)
                } finally {
                    runCatching { resource.close() }
                }

                val plainText = EntityHeuristics.stripHtml(rawHtml)
                if (plainText.isEmpty()) continue

                val paragraphs = EntityHeuristics.splitIntoParagraphs(plainText)
                for (paragraph in paragraphs) {
                    val matches = EntityHeuristics.CANDIDATE_REGEX.findAll(paragraph)
                    for (match in matches) {
                        val candidate = match.value.trim()
                        if (EntityHeuristics.isStopword(candidate)) continue

                        val isMid = EntityHeuristics.isMidSentence(paragraph, match.range.first)
                        val stats = candidateMap.getOrPut(candidate) {
                            CandidateStats(firstChapter = chapterIdx)
                        }
                        stats.totalCount++
                        if (isMid) {
                            stats.midSentenceCount++
                        }
                    }
                }

                // Podar candidatos cada 25 capítulos para mantener tope de 3000
                if (chapterIdx % 25 == 0 && candidateMap.size > 3000) {
                    pruneCandidates(candidateMap, 3000)
                }
            }

            // Filtrado según reglas de FASE 8:
            // - Stopwords descartadas
            // - Nombres de 1 palabra con < 3 apariciones a mitad de frase descartados
            // - Cualquiera con < 3 apariciones totales descartado
            val acceptedCandidates = candidateMap.entries
                .filter { (name, stats) ->
                    val isSingleWord = !name.contains(' ')
                    val passesMid = !isSingleWord || stats.midSentenceCount >= 3
                    stats.totalCount >= 3 && passesMid && !EntityHeuristics.isStopword(name)
                }
                .sortedByDescending { it.value.totalCount }
                .take(3000)
                .associate { it.key to it.value }

            Log.i(TAG, "Pasada 1 completada: ${acceptedCandidates.size} entidades candidatas aceptadas")

            // Insertar o reutilizar entidades aceptadas en Room
            val nameToEntityId = HashMap<String, Long>(acceptedCandidates.size)
            for ((name, stats) in acceptedCandidates) {
                val existing = graphRepository.getEntityByName(bookId, name)
                if (existing != null) {
                    nameToEntityId[name] = existing.id
                } else {
                    val entity = GraphEntity(
                        bookId = bookId,
                        name = name,
                        type = EntityHeuristics.suggestType(name),
                        description = "",
                        firstChapterIndex = stats.firstChapter,
                        mentionCount = stats.totalCount,
                        confirmed = false,
                        ignored = false
                    )
                    val id = graphRepository.insertEntity(entity)
                    if (id > 0) {
                        nameToEntityId[name] = id
                    } else {
                        val retry = graphRepository.getEntityByName(bookId, name)
                        if (retry != null) nameToEntityId[name] = retry.id
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // PASADA 2: Co-ocurrencias (relaciones) y menciones
            // ════════════════════════════════════════════════════════════════
            val coOccurrenceMap = HashMap<Long, Int>()
            val relationFirstChapter = HashMap<Long, Int>()
            val mentionsBuffer = ArrayList<GraphMention>(128)

            for ((chapterIdx, link) in readingOrder.withIndex()) {
                if (isStopped) {
                    Log.i(TAG, "Extracción detenida en Pasada 2")
                    return Result.retry()
                }

                val progress = 50 + (chapterIdx * 50) / totalChapters
                updateProgress(progress, chapterIdx + 1, totalChapters, 2, book.title, bookId)

                val resource = publication.get(link) ?: continue
                val rawHtml = try {
                    val bytes = resource.read().getOrNull() ?: continue
                    String(bytes, Charsets.UTF_8)
                } finally {
                    runCatching { resource.close() }
                }

                val plainText = EntityHeuristics.stripHtml(rawHtml)
                if (plainText.isEmpty()) continue

                val paragraphs = EntityHeuristics.splitIntoParagraphs(plainText)
                val totalP = paragraphs.size.coerceAtLeast(1)
                val chapterMentionedEntities = HashSet<Long>()

                for ((pIdx, paragraph) in paragraphs.withIndex()) {
                    val progression = pIdx.toDouble() / totalP

                    val presentEntityIds = ArrayList<Long>()
                    val matches = EntityHeuristics.CANDIDATE_REGEX.findAll(paragraph)
                    for (match in matches) {
                        val candidate = match.value.trim()
                        val entityId = nameToEntityId[candidate] ?: continue
                        if (!presentEntityIds.contains(entityId)) {
                            presentEntityIds.add(entityId)
                        }

                        // Guardar 1 GraphMention por entidad por capítulo
                        if (chapterMentionedEntities.add(entityId)) {
                            val snippet = EntityHeuristics.extractSnippet(
                                text = paragraph,
                                matchStart = match.range.first,
                                matchLength = match.value.length,
                                targetRadius = 40
                            )
                            mentionsBuffer.add(
                                GraphMention(
                                    entityId = entityId,
                                    chapterIndex = chapterIdx,
                                    href = link.href.toString(),
                                    progression = progression,
                                    snippet = snippet
                                )
                            )
                        }
                    }

                    // Por párrafo: +1 de peso a cada par de entidades presentes
                    if (presentEntityIds.size >= 2) {
                        for (i in 0 until presentEntityIds.size) {
                            for (j in i + 1 until presentEntityIds.size) {
                                val idA = minOf(presentEntityIds[i], presentEntityIds[j])
                                val idB = maxOf(presentEntityIds[i], presentEntityIds[j])
                                val pairKey = idA * 1_000_000_000L + idB
                                val currentWeight = (coOccurrenceMap[pairKey] ?: 0) + 1
                                coOccurrenceMap[pairKey] = currentWeight
                                if (!relationFirstChapter.containsKey(pairKey)) {
                                    relationFirstChapter[pairKey] = chapterIdx
                                }
                            }
                        }
                    }
                }

                if (mentionsBuffer.size >= 200) {
                    graphRepository.insertMentions(mentionsBuffer.toList())
                    mentionsBuffer.clear()
                }
            }

            if (mentionsBuffer.isNotEmpty()) {
                graphRepository.insertMentions(mentionsBuffer.toList())
                mentionsBuffer.clear()
            }

            // Al final, upsertRelation solo si peso >= 3
            var savedRelations = 0
            for ((pairKey, weight) in coOccurrenceMap) {
                if (weight >= 3) {
                    val idA = pairKey / 1_000_000_000L
                    val idB = pairKey % 1_000_000_000L
                    val firstCh = relationFirstChapter[pairKey] ?: 0
                    graphRepository.upsertRelation(
                        GraphRelation(
                            bookId = bookId,
                            sourceId = idA,
                            targetId = idB,
                            label = "",
                            weight = weight,
                            manual = false,
                            firstChapterIndex = firstCh
                        )
                    )
                    savedRelations++
                }
            }

            Log.i(TAG, "Extracción de grafo completada exitosamente: ${acceptedCandidates.size} entidades, $savedRelations relaciones guardadas")

            showResultNotification(acceptedCandidates.size, savedRelations)
            return Result.success(
                workDataOf(
                    KEY_PROGRESS to 100,
                    KEY_STATUS to "Completado: ${acceptedCandidates.size} entidades y $savedRelations relaciones.",
                    KEY_BOOK_ID to bookId
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error durante la extracción de grafo", e)
            return Result.failure()
        } finally {
            runCatching { publication.close() }
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private suspend fun updateProgress(
        progress: Int,
        chapter: Int,
        totalChapters: Int,
        pass: Int,
        bookTitle: String,
        bookId: String
    ) {
        val status = "Capítulo $chapter/$totalChapters (Pasada $pass/2)"
        setProgress(
            workDataOf(
                KEY_PROGRESS to progress,
                KEY_STATUS to status,
                KEY_BOOK_ID to bookId
            )
        )

        runCatching {
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle("Analizando $bookTitle")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setProgress(100, progress, false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun pruneCandidates(map: HashMap<String, CandidateStats>, maxKeep: Int) {
        if (map.size <= maxKeep) return
        val topEntries = map.entries
            .sortedByDescending { it.value.totalCount }
            .take(maxKeep)
            .associate { it.key to it.value }
        map.clear()
        map.putAll(topEntries)
    }

    private fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Woods Reader - Grafo")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Extracción de Grafo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso del extractor de entidades y relaciones del libro"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showResultNotification(entitiesCount: Int, relationsCount: Int) {
        createNotificationChannel()
        val text = "Se identificaron $entitiesCount entidades y $relationsCount relaciones."
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Análisis de Grafo finalizado")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }
}
