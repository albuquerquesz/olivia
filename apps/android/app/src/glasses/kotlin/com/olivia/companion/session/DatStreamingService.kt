package com.olivia.companion.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.olivia.companion.R

/** Keeps the active DAT stream eligible to run while the creator is hands-free. */
class DatStreamingService : Service() {
    override fun onCreate() {
        super.onCreate()
        // Publish the foreground notification before the DAT session handshake. Android applies
        // a short deadline after startForegroundService(), including in instrumentation runs.
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Olivia ativa")
                .setContentText("Orientação pelos óculos em andamento")
                .setOngoing(true)
                .build(),
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Olivia", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Olivia ativa")
        .setContentText("Orientação pelos óculos em andamento")
        .setOngoing(true)
        .build()

    companion object {
        fun start(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, DatStreamingService::class.java),
        )

        fun stop(context: Context) = context.stopService(Intent(context, DatStreamingService::class.java))

        private const val CHANNEL_ID = "olivia_dat_stream"
        private const val NOTIFICATION_ID = 71
    }
}
