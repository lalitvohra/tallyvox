package com.tallyvox

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.tallyvox.service.CounterService

class CounterWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_INCREMENT = "com.tallyvox.widget.ACTION_INCREMENT"
        const val ACTION_DECREMENT = "com.tallyvox.widget.ACTION_DECREMENT"
        const val PREFS_NAME = "tallyvox_prefs"

        fun updateAllWidgets(ctx: Context) {
            val intent = Intent(ctx, CounterWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(ctx)
                .getAppWidgetIds(ComponentName(ctx, CounterWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            ctx.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        ctx: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(ctx, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        ctx: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(ctx.packageName, R.layout.widget_counter)

        // Read current count from SharedPreferences (persisted by CounterService)
        val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val primary = prefs.getInt("primary", 0)
        views.setTextViewText(R.id.widget_count, primary.toString())

        // Plus button → broadcast to CounterService
        val incIntent = Intent(ctx, CounterWidget::class.java).apply { action = ACTION_INCREMENT }
        val incPending = PendingIntent.getBroadcast(
            ctx, 0, incIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_plus, incPending)

        // Minus button → broadcast to CounterService
        val decIntent = Intent(ctx, CounterWidget::class.java).apply { action = ACTION_DECREMENT }
        val decPending = PendingIntent.getBroadcast(
            ctx, 1, decIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_minus, decPending)

        // Tap on counter number → open app
        val openIntent = Intent(ctx, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            ctx, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_count, openPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)

        when (intent.action) {
            ACTION_INCREMENT -> {
                // Increment in service
                CounterService.increment(ctx)
                // Update widget display
                updateAllWidgets(ctx)
            }
            ACTION_DECREMENT -> {
                CounterService.decrement(ctx)
                updateAllWidgets(ctx)
            }
        }
    }
}
