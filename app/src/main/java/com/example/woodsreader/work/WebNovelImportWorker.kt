package com.example.woodsreader.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.woodsreader.R
import com.example.woodsreader.core.storage.WoodsFolderStructure
import com.example.woodsreader.core.web2epub.novel.EpubAssembler
import com.example.woodsreader.core.web2epub.novel.ImageDownloader
import com.example.woodsreader.core.web2epub.novel.ImportStage
import com.example.woodsreader.core.web2epub.novel.ImportStore
import com.example.woodsreader.core.web2epub.novel.NovelImporter
import com.example.woodsreader.core.web2epub.novel.OkHttpPageFetcher
import com.example.woodsreader.core.web2epub.novel.SiteProfileLoader
import com.example.woodsreader.core.web2epub.novel.SiteProfileRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File

@HiltWorker
class WebNovelImportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val woodsFolderStructure: WoodsFolderStructure
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_URL = "key_novel_url"
        const val KEY_START_INDEX = "key_start_index"
        const val KEY_END_INDEX = "key_end_index"
        const val KEY_FORCE_REVERSE = "key_force_reverse"
        const val KEY_OUTPUT_EPUB_PATH = "key_output_epub_path"
        const val KEY_ERROR_MESSAGE = "key_error_message"

        private const val CHANNEL_ID = "web_novel_import_channel"
        private const val NOTIFICATION_ID = 4004
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val novelUrl = inputData.getString(KEY_URL) ?: return Result.failure()
        val startIndex = inputData.getInt(KEY_START_INDEX, 0)
        val endIndex = if (inputData.keyValueMap.containsKey(KEY_END_INDEX)) {
            inputData.getInt(KEY_END_INDEX, 0)
        } else null
        val forceReverse = if (inputData.keyValueMap.containsKey(KEY_FORCE_REVERSE)) {
            inputData.getBoolean(KEY_FORCE_REVERSE, false)
        } else null

        createNotificationChannel()
        setForeground(createForegroundInfo(context.getString(R.string.novel_import_preparing), 0, 0))

        val outputDir = woodsFolderStructure.epubsDir
        val baseStoreDir = File(context.cacheDir, "web2epub").apply { if (!exists()) mkdirs() }

        val adapterRegistry = SiteProfileLoader(context).loadRegistry()
        val fetcher = OkHttpPageFetcher(okHttpClient)
        val imageDownloader = ImageDownloader(okHttpClient)
        val store = ImportStore(baseStoreDir)
        val assembler = EpubAssembler()

        val importer = NovelImporter(
            fetcher = fetcher,
            adapterRegistry = adapterRegistry,
            store = store,
            imageDownloader = imageDownloader,
            assembler = assembler
        )

        var finalError: String? = null

        // Escuchar progreso para actualizar la notificación de Foreground Service
        val progressJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            importer.progressFlow.collectLatest { progress ->
                when (progress.stage) {
                    ImportStage.FETCHING_INDEX -> {
                        setForeground(createForegroundInfo(context.getString(R.string.novel_import_fetching_index), 0, 0))
                    }
                    ImportStage.FETCHING_CHAPTERS -> {
                        val text = "${progress.currentChapter}/${progress.totalChapters}: ${progress.currentChapterTitle}"
                        setForeground(createForegroundInfo(text, progress.currentChapter, progress.totalChapters))
                    }
                    ImportStage.DOWNLOADING_IMAGES -> {
                        val text = context.getString(R.string.novel_import_downloading_images, progress.currentChapter, progress.totalChapters)
                        setForeground(createForegroundInfo(text, progress.currentChapter, progress.totalChapters))
                    }
                    ImportStage.ASSEMBLING_EPUB -> {
                        setForeground(createForegroundInfo(context.getString(R.string.novel_import_assembling), 100, 100))
                    }
                    ImportStage.ERROR -> {
                        finalError = progress.error
                    }
                    ImportStage.PAUSED_CHALLENGE -> {
                        finalError = context.getString(R.string.novel_import_challenge_required)
                    }
                    else -> Unit
                }
            }
        }

        return try {
            val epubFile = importer.importNovel(
                novelUrl = novelUrl,
                outputDirectory = outputDir,
                startIndex = startIndex,
                endIndex = endIndex,
                forceReverse = forceReverse
            )
            progressJob.cancel()

            if (epubFile != null && epubFile.exists()) {
                Timber.d("WebNovelImportWorker completó con éxito: ${epubFile.absolutePath}")
                Result.success(workDataOf(KEY_OUTPUT_EPUB_PATH to epubFile.absolutePath))
            } else {
                Timber.w("WebNovelImportWorker falló: $finalError")
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to (finalError ?: "Error desconocido al importar")))
            }
        } catch (e: Exception) {
            progressJob.cancel()
            Timber.e(e, "WebNovelImportWorker error crítico")
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Excepción al procesar")))
        }
    }

    private fun createForegroundInfo(content: String, progress: Int, max: Int): ForegroundInfo {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.novel_import_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)

        if (max > 0) {
            builder.setProgress(max, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()

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
                context.getString(R.string.novel_import_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
