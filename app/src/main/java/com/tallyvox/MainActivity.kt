package com.tallyvox

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tallyvox.service.CounterService
import com.tallyvox.ui.CounterScreen
import com.tallyvox.ui.theme.TallyVoxTheme

class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission result — continue regardless
    }

    val viewModel: CounterViewModel by lazy {
        CounterViewModel(application)
    }

    // Receives count updates from CounterService (notification actions)
    private val countReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == "com.tallyvox.COUNT_UPDATE") {
                val primary = intent.getIntExtra("primary", -1)
                if (primary >= 0) {
                    viewModel.syncFromService(primary,
                        intent.getIntExtra("secondary", 0),
                        intent.getIntExtra("interval", 100)
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start and bind CounterService
        val serviceIntent = Intent(this, CounterService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // Notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Register broadcast to receive count updates from notification actions
        val filter = IntentFilter("com.tallyvox.COUNT_UPDATE")
        registerReceiver(countReceiver, filter, RECEIVER_NOT_EXPORTED)

        setContent {
            TallyVoxTheme {
                CounterScreen(
                    viewModel = viewModel,
                    onRequestMicPermission = {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Push current count to notification when app comes to foreground
        CounterService.updateNotification(
            viewModel.counters.value.primary,
            viewModel.counters.value.secondary,
            viewModel.counters.value.interval,
            this
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Keep notification running but release UI resources
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.increment()
                CounterService.updateNotification(
                    viewModel.counters.value.primary,
                    viewModel.counters.value.secondary,
                    viewModel.counters.value.interval,
                    this
                )
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.decrement()
                CounterService.updateNotification(
                    viewModel.counters.value.primary,
                    viewModel.counters.value.secondary,
                    viewModel.counters.value.interval,
                    this
                )
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(countReceiver)
        } catch (_: Exception) {}
    }
}
