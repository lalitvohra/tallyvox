package com.tallyvox

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.tallyvox.service.CounterService

/**
 * TallyVox Accessibility Service
 *
 * Intercepts volume key events on the lock screen and routes them to the counter.
 * User must enable this service manually in:
 *   Settings → Accessibility → TallyVox → Enable
 *
 * This is the ONLY way to make volume buttons work on Samsung lock screen
 * without Good Lock / root.
 */
class TallyVoxAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        // Key event interception is configured via accessibility_service_config.xml
        // flagRequestFilterKeyEvents enables volume key capture on lock screen
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we handle key events instead
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        // Only handle volume buttons
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    // Increment counter
                    try {
                        val ctx = applicationContext
                        CounterService.increment(ctx)
                        // Also wake screen briefly so user sees the change
                        wakeScreen(ctx)
                    } catch (_: Exception) {}
                    return true // Consume: don't pass to system
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // Decrement counter
                    try {
                        val ctx = applicationContext
                        CounterService.decrement(ctx)
                        wakeScreen(ctx)
                    } catch (_: Exception) {}
                    return true // Consume: don't pass to system
                }
            }
        }

        // Pass all other events to the system
        return false
    }

    private fun wakeScreen(ctx: android.content.Context) {
        try {
            val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "TallyVox::A11yScreenOn"
            )
            wl.acquire(5000L) // Keep screen on 5 seconds so user sees count change
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        // Required stub
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
