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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tallyvox.MainActivity
import com.tallyvox.R

class CounterService : Service() {

    companion object {
        const val CHANNEL_ID = "tallyvox_counter"
        const val NOTIF_ID = 1001
        const val ACTION_INCREMENT = "com.tallyvox.INCREMENT"
        const val ACTION_DECREMENT = "com.tallyvox.DECREMENT"
        const val ACTION_SHOW = "com.tallyvox.SHOW"

        var currentPrimary = 0
        var currentSecondary = 0
        var currentInterval = 100

        // Called from activity to update notification
        fun updateNotification(
            primary: Int,
            secondary: Int,
            interval: Int,
            context: android.content.Context
        ) {
            currentPrimary = primary
            currentSecondary = secondary
            currentInterval = interval
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(context))
        }

        fun buildNotification(ctx: android.content.Context): Notification {
            val mainIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", ACTION_SHOW)
            }
            val mainPending = PendingIntent.getActivity(
                ctx, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // +1 button
            val incIntent = Intent(ctx, CounterService::class.java).apply { action = ACTION_INCREMENT }
            val incPending = PendingIntent.getService(
                ctx, 1, incIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // -1 button
            val decIntent = Intent(ctx, CounterService::class.java).apply { action = ACTION_DECREMENT }
            val decPending = PendingIntent.getService(
                ctx, 2, decIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle("TallyVox  —  $currentPrimary")
                .setContentText("Interval: $currentSecondary @$currentInterval")
                .setSmallIcon(R.drawable.ic_counter)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(mainPending)
                .addAction(R.drawable.ic_plus, "+1", incPending)
                .addAction(R.drawable.ic_minus, "−1", decPending)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }
    }

    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INCREMENT -> {
                currentPrimary++
                updateNotificationUI()
                haptic(50)
            }
            ACTION_DECREMENT -> {
                if (currentPrimary > 0) {
                    currentPrimary--
                    updateNotificationUI()
                    haptic(50)
                }
            }
            ACTION_SHOW -> {
                // Just bring to front — handled by activity
            }
            else -> {
                // Normal start — show notification
                startForeground(NOTIF_ID, buildNotification(this))
            }
        }
        return START_STICKY
    }

    private fun updateNotificationUI() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(this))
        // Also notify MainActivity to update its count
        sendCountBroadcast()
    }

    private fun sendCountBroadcast() {
        val broadcast = Intent("com.tallyvox.COUNT_UPDATE").apply {
            putExtra("primary", currentPrimary)
            putExtra("secondary", currentSecondary)
            putExtra("interval", currentInterval)
            setPackage(packageName)
        }
        sendBroadcast(broadcast)
    }

    private fun haptic(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "TallyVox Counter",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "TallyVox counter — shows current count and quick actions"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
