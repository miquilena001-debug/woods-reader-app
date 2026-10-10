package com.example.woodsreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.annotation.OptIn as AndroidOptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.example.woodsreader.MainActivity
import com.example.woodsreader.R
import com.google.common.collect.ImmutableList

/**
 * Proveedor de notificación TTS con el estilo visual de Woods Reader.
 *
 * Características:
 * ─ Canal dedicado "Woods Reader · Narración" (IMPORTANCE_LOW = sin sonido/vibración).
 * ─ Color de acento verde HiddenForest #4CAF72 tiñe el icono pequeño.
 * ─ Portada del libro como icono grande.
 * ─ Acciones en la notificación: [⏮ Anterior] [⏯ Play/Pausa] [⏭ Siguiente].
 * ─ Pantalla de bloqueo: VISIBILITY_PUBLIC (muestra contenido completo).
 * ─ Compatible con Media3 1.4.1 — sin dependencias en androidx.media legacy.
 */
@AndroidOptIn(UnstableApi::class)
class TtsMediaNotificationProvider(
    private val context: android.content.Context
) : MediaNotification.Provider {

    companion object {
        const val CHANNEL_ID = "woods_tts_playback"
        const val NOTIFICATION_ID = 7001
    }

    init {
        createNotificationChannel()
    }

    // ── Canal de notificación ─────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tts_notification_channel_name),
            // IMPORTANCE_LOW → sin sonido ni vibración (ideal para notificación de medios persistente)
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.tts_notification_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            manager.createNotificationChannel(this)
        }
    }

    // ── MediaNotification.Provider ────────────────────────────────────────────
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player   = mediaSession.player
        val metadata = player.mediaMetadata
        val isPlaying = player.isPlaying

        // ── PendingIntent → toca la notificación → abre la app ───────────────
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ── Portada del libro ─────────────────────────────────────────────────
        val artworkBitmap = metadata.artworkData?.let { bytes ->
            try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
        }

        // ── Acción [⏮ Anterior] ───────────────────────────────────────────────
        val prevAction = actionFactory.createCustomActionFromCustomCommandButton(
            mediaSession,
            CommandButton.Builder()
                .setDisplayName(context.getString(R.string.tts_action_prev))
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_back)
                .setSessionCommand(SessionCommand(ReaderTtsService.CMD_SKIP_PREV, Bundle.EMPTY))
                .build()
        )

        // ── Acción [⏯ Play/Pausa] ─────────────────────────────────────────────
        val playPauseIcon = if (isPlaying) {
            IconCompat.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_pause)
        } else {
            IconCompat.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_play)
        }
        val playPauseLabel = if (isPlaying) {
            context.getString(R.string.tts_action_pause)
        } else {
            context.getString(R.string.tts_action_play)
        }
        val playPauseAction = actionFactory.createMediaAction(
            mediaSession,
            playPauseIcon,
            playPauseLabel,
            Player.COMMAND_PLAY_PAUSE
        )

        // ── Acción [⏭ Siguiente] ──────────────────────────────────────────────
        val nextAction = actionFactory.createCustomActionFromCustomCommandButton(
            mediaSession,
            CommandButton.Builder()
                .setDisplayName(context.getString(R.string.tts_action_next))
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_forward)
                .setSessionCommand(SessionCommand(ReaderTtsService.CMD_SKIP_NEXT, Bundle.EMPTY))
                .build()
        )

        // ── Construir notificación ────────────────────────────────────────────
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Contenido principal
            .setContentTitle(
                metadata.title?.ifBlank { null }
                    ?: context.getString(R.string.tts_notification_title)
            )
            .setContentText(
                metadata.artist?.ifBlank { null } ?: metadata.albumTitle
            )
            .setSubText(context.getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            // Icono pequeño monocromo (requerido por Android para evitar el cuadrado blanco)
            .setSmallIcon(R.drawable.ic_notification)
            // Portada como icono grande
            .apply { artworkBitmap?.let { setLargeIcon(it) } }
            // Acento verde HiddenForest — tiñe el icono pequeño y el fondo en algunas ROMs
            .setColor(context.getColor(R.color.woods_accent))
            .setColorized(true)
            // Las 3 acciones de control
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            // MediaStyle: conecta con la MediaSession y muestra botones en la vista compacta
            .setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(false)
            )
            // Comportamiento de la notificación
            .setOngoing(isPlaying)    // No dismissible mientras reproduce
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)   // Pantalla de bloqueo completa
            .setPriority(NotificationCompat.PRIORITY_LOW)          // Sin sonido/vibración
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()

        return MediaNotification(NOTIFICATION_ID, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false  // Media3 maneja las acciones vía TtsSessionCallback
}
