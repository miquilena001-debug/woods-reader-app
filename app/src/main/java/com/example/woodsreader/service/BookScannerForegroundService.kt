package com.example.woodsreader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BookScannerForegroundService : Service() {

    @Inject
    lateinit var scanner: BookScannerService

    companion object {
        private const val CHANNEL_ID = "book_scanner_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_SCAN = "com.example.woodsreader.START_SCAN"
        const val ACTION_STOP_SCAN = "com.example.woodsreader.STOP_SCAN"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                startForegroundService()
                scanner.scan()
                scanner.startWatching()
            }
            ACTION_STOP_SCAN -> {
                scanner.stopWatching()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // intent null (reinicio por START_STICKY) o acción desconocida: parar limpiamente
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // START_NOT_STICKY: no reiniciar automáticamente si Android mata el servicio.
        // El usuario puede iniciar un nuevo scan desde la UI manualmente.
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Buscando libros")
            .setContentText("Escaneando el dispositivo en busca de libros nuevos...")
            .setSmallIcon(android.R.drawable.stat_sys_download) // Fallback icon
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Book Scanner Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación constante mientras se buscan audiolibros y epubs"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scanner.stopWatching()
        super.onDestroy()
    }
}
