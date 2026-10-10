package com.example.woodsreader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.service.BookScannerService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.util.Log

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scannerService: BookScannerService,
    private val preferencesRepo: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "library_sync_channel"
    private val syncNotificationId = 1001
    private val resultNotificationId = 1002

    override suspend fun doWork(): Result {
        // 1. Verificar permisos
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Asumimos que si llegamos aquí es porque el usuario otorgó los permisos básicos
            true 
        }

        if (!hasPermission) {
            Log.d("LibrarySyncWorker", "No storage permission. Skipping sync.")
            return Result.failure()
        }

        showProgressNotification("Buscando libros nuevos…")

        try {
            val prefs = preferencesRepo.userPreferencesFlow.first()

            // Phase 1: Quick Scan — discovers new books fast
            val stats = scannerService.quickScan(prefs.customScanPaths)

            preferencesRepo.saveScanStats(
                lastScanTime  = System.currentTimeMillis(),
                filesScanned  = stats.scannedCount,
                newBooksFound = stats.newBooksCount
            )

            if (stats.newBooksCount > 0) {
                showResultNotification(stats.newBooksCount)
            }

            // Phase 2: Deep Enrich — fills in covers and metadata (background, non-blocking)
            showProgressNotification("Enriqueciendo metadatos…")
            scannerService.deepEnrich()

            return Result.success()
        } catch (e: Exception) {
            Log.e("LibrarySyncWorker", "Error in background sync", e)
            return Result.retry()
        } finally {
            notificationManager.cancel(syncNotificationId)
        }
    }

    private fun showProgressNotification(message: String) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Woods Reader")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(syncNotificationId, notification)
    }

    private fun showResultNotification(newBooks: Int) {
        createNotificationChannel()
        val text = if (newBooks == 1) "Se encontró 1 nuevo libro" else "Se encontraron $newBooks nuevos libros"
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Biblioteca actualizada")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(resultNotificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sincronización de Biblioteca"
            val descriptionText = "Notificaciones sobre el escaneo de libros en segundo plano"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
