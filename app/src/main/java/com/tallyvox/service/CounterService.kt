package com.tallyvox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tallyvox.MainActivity
import com.tallyvox.R

class CounterService : Service() {

    companion object {
        const val CHANNEL_ID = "tallyvox_counter"
        const val NOTIF_ID = 1001
        var currentCount: Int = 0
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentCount = intent?.getIntExtra("count", 0) ?: 0
        val countText = "Count: $currentCount"
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TallyVox")
            .setContentText(countText)
            .setSmallIcon(R.drawable.ic_counter)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Counter", NotificationManager.IMPORTANCE_LOW).apply {
            description = "TallyVox counter service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
