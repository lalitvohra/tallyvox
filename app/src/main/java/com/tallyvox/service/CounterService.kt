package com.tallyvox.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tallyvox.MainActivity
import com.tallyvox.R
import java.io.File

class CounterService : Service() {

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "tallyvox_counter"
        const val PREFS_NAME = "tallyvox_prefs"
        const val PHRASE_FILE = "voice_phrase.3gp"
        const val KEY_SAVED_PHRASE = "saved_phrase_text"
        const val KEY_PHRASE_RECORDED = "phraseRecorded"

        var isRunning = false

        var currentPrimary = 0
        var currentSecondary = 0
        var currentInterval = 100

        // Voice mode state (persisted via SharedPreferences)
        var phraseRecorded = false
            private set
        var savedPhraseText = ""
            private set

        fun createNotificationChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val counterChannel = NotificationChannel(
                    CHANNEL_ID,
                    "TallyVox Counter",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "TallyVox counter notification"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(counterChannel)
            }
        }

        fun updateNotification(ctx: Context) {
            try {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification(ctx))
            } catch (e: Exception) {
                android.util.Log.e("TallyVox", "updateNotification failed", e)
            }
        }

        fun increment(ctx: Context) {
            currentPrimary++
            persistCount(ctx)
            updateNotification(ctx)
            sendCountBroadcast(ctx)
            wakeScreen(ctx)
        }

        fun decrement(ctx: Context) {
            if (currentPrimary > 0) {
                currentPrimary--
                persistCount(ctx)
                updateNotification(ctx)
                sendCountBroadcast(ctx)
                wakeScreen(ctx)
            }
        }

        // Keep screen on for 1 minute when counter changes (widget tap or volume button)
        private fun wakeScreen(ctx: Context) {
            try {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                    "TallyVox::ScreenOn"
                )
                wl.acquire(60_000L)
                android.util.Log.d("TallyVox", "Screen wake lock acquired for 60s")
            } catch (e: Exception) {
                android.util.Log.e("TallyVox", "wakeScreen failed: ${e.message}", e)
            }
        }

        private fun persistCount(ctx: Context) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("primary", currentPrimary)
                .putInt("secondary", currentSecondary)
                .putInt("interval", currentInterval)
                .apply()
        }

        private fun sendCountBroadcast(ctx: Context) {
            try {
                val i = Intent("com.tallyvox.COUNT_UPDATE").apply {
                    putExtra("primary", currentPrimary)
                    putExtra("secondary", currentSecondary)
                    putExtra("interval", currentInterval)
                    setPackage(ctx.packageName)
                }
                ctx.sendBroadcast(i)
            } catch (_: Exception) {}
        }

        fun buildNotification(ctx: Context): android.app.Notification {
            createNotificationChannel(ctx)

            val contentIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                ctx, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val incIntent = Intent(ctx, CounterService::class.java).apply { action = "com.tallyvox.ACTION_INCREMENT" }
            val incPendingIntent = PendingIntent.getService(
                ctx, 1, incIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val decIntent = Intent(ctx, CounterService::class.java).apply { action = "com.tallyvox.ACTION_DECREMENT" }
            val decPendingIntent = PendingIntent.getService(
                ctx, 2, decIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val subText = if (currentInterval > 0) "Interval: $currentSecondary" else "Tap to open"

            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle("TallyVox  $currentPrimary")
                .setContentText(subText)
                .setSmallIcon(R.drawable.ic_mic)
                .setColor(0xFF4f98a3.toInt())
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .addAction(R.drawable.ic_plus, "＋", incPendingIntent)
                .addAction(R.drawable.ic_minus, "−", decPendingIntent)
                .build()
        }
    }

    // ─── Voice Recording ────────────────────────────────────────────────────

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    private val amplitudeHandler = Handler(Looper.getMainLooper())
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val amp = try { mediaRecorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                sendAmplitudeBroadcast(amp)
                amplitudeHandler.postDelayed(this, 100)
            }
        }
    }

    fun startRecording(): Boolean {
        if (isRecording) return false
        val filePath = filesDir.absolutePath + "/" + PHRASE_FILE
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(filePath)
                prepare()
                start()
            }
            isRecording = true
            amplitudeHandler.post(amplitudeRunnable)
            // Auto-stop after 10 seconds — broadcast to notify ViewModel
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.postDelayed({
                android.util.Log.d("TallyVox", "Auto-stop triggered at 10s")
                stopRecording()
                // Notify ViewModel that recording auto-stopped
                sendBroadcast(Intent("com.tallyvox.ACTION_AUTO_STOP_RECORDING"))
            }, 10000)
            android.util.Log.d("TallyVox", "Recording started: $filePath")
            return true
        } catch (e: Exception) {
            android.util.Log.e("TallyVox", "startRecording failed", e)
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            isRecording = false
            android.widget.Toast.makeText(this, "Recording error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            return false
        }
    }

    fun stopRecording(): Boolean {
        if (!isRecording) return false
        amplitudeHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)  // Cancel auto-stop
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            android.util.Log.e("TallyVox", "stopRecording failed", e)
        }
        mediaRecorder = null
        isRecording = false
        // Verify file
        val file = File(filesDir, PHRASE_FILE)
        return file.exists() && file.length() > 0
    }

    fun getRecordingAmplitude(): Int {
        return try { mediaRecorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
    }

    private fun sendAmplitudeBroadcast(amp: Int) {
        try {
            val i = Intent("com.tallyvox.VOICE_AMPLITUDE").apply {
                putExtra("amplitude", amp)
                setPackage(packageName)
            }
            sendBroadcast(i)
        } catch (_: Exception) {}
    }

    // ─── Voice Listening Loop ───────────────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningActive = false
    private var cooldownActive = false
    private var voiceRestartBlocked = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cooldownRunnable = Runnable {
        cooldownActive = false
    }

    fun startListeningLoop() {
        if (isListeningActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.util.Log.w("TallyVox", "Speech recognizer not available")
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        voiceRestartBlocked = false
        isListeningActive = true
        sendListeningBroadcast(true)
        startSpeechRecognitionLoop()
    }

    fun stopListeningLoop() {
        voiceRestartBlocked = true
        isListeningActive = false
        isRestartingLoop = false
        mainHandler.removeCallbacksAndMessages(null)
        cooldownActive = false
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        sendListeningBroadcast(false)
    }

    private var isRestartingLoop = false

    private fun startSpeechRecognitionLoop() {
        if (voiceRestartBlocked || !isListeningActive || isRestartingLoop) return
        isRestartingLoop = true

        // Destroy old recognizer cleanly
        val oldRecognizer = speechRecognizer
        speechRecognizer = null
        try { oldRecognizer?.stopListening() } catch (_: Exception) {}
        try { oldRecognizer?.destroy() } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) {
            isListeningActive = false
            isRestartingLoop = false
            sendListeningBroadcast(false)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (voiceRestartBlocked || !isListeningActive) {
                    isRestartingLoop = false
                    return
                }
                isRestartingLoop = false  // Reset so restart can proceed
                val restartable = error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                )
                val delay = if (restartable) 1000L else 2000L
                mainHandler.postDelayed({
                    if (!voiceRestartBlocked && isListeningActive && !isRestartingLoop) {
                        startSpeechRecognitionLoop()
                    }
                }, delay)
            }

            override fun onResults(results: android.os.Bundle?) {
                if (voiceRestartBlocked || !isListeningActive) {
                    isRestartingLoop = false
                    return
                }
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0] ?: ""
                    checkPhraseMatch(spoken)
                }
                isRestartingLoop = false  // Allow restart
                mainHandler.postDelayed({
                    if (!voiceRestartBlocked && isListeningActive && !isRestartingLoop) {
                        startSpeechRecognitionLoop()
                    }
                }, 500)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                if (voiceRestartBlocked || !isListeningActive) return
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0) ?: ""
                if (partial.isNotBlank()) {
                    checkPhraseMatch(partial)
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
            isRestartingLoop = false
        } catch (_: Exception) {
            isListeningActive = false
            isRestartingLoop = false
            sendListeningBroadcast(false)
        }
    }

    private fun checkPhraseMatch(spoken: String) {
        if (cooldownActive) return
        if (savedPhraseText.isBlank()) return

        val normSpoken = spoken.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")
        val normSaved = savedPhraseText.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")

        // Skip if no phrase saved (e.g., during re-record placeholder)
        if (normSaved.isBlank()) return

        android.util.Log.d("TallyVox", "phrase match check: spoken='$normSpoken' saved='$normSaved'")

        if (normSpoken.contains(normSaved)) {
            onPhraseDetected()
        }
    }

    private fun onPhraseDetected() {
        if (cooldownActive) return
        cooldownActive = true
        mainHandler.removeCallbacks(cooldownRunnable)
        mainHandler.postDelayed(cooldownRunnable, 1000)

        currentPrimary++
        persistCount(this)
        updateNotification(this)
        sendCountBroadcast(this)
        sendHeardBroadcast()

        // Haptic feedback
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (_: Exception) {}

        android.util.Log.d("TallyVox", "Phrase detected! Count incremented to $currentPrimary")
    }

    // ─── Phrase Persistence ────────────────────────────────────────────────

    fun savePhrase(text: String) {
        filesDir.resolve(PHRASE_FILE).takeIf { it.exists() && it.length() > 0 }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(KEY_SAVED_PHRASE, text)
            putBoolean(KEY_PHRASE_RECORDED, true)
            apply()
        }
        savedPhraseText = text
        phraseRecorded = true
    }

    fun loadPhraseState() {
        val p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        savedPhraseText = p.getString(KEY_SAVED_PHRASE, "") ?: ""
        phraseRecorded = p.getBoolean(KEY_PHRASE_RECORDED, false)
    }

    fun deletePhrase() {
        try {
            File(filesDir, PHRASE_FILE).delete()
        } catch (_: Exception) {}
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            remove(KEY_SAVED_PHRASE)
            putBoolean(KEY_PHRASE_RECORDED, false)
            apply()
        }
        savedPhraseText = ""
        phraseRecorded = false
    }

    // ─── Broadcasts ─────────────────────────────────────────────────────────

    private fun sendListeningBroadcast(listening: Boolean) {
        try {
            val i = Intent("com.tallyvox.VOICE_LISTENING").apply {
                putExtra("listening", listening)
                setPackage(packageName)
            }
            sendBroadcast(i)
        } catch (_: Exception) {}
    }

    private fun sendHeardBroadcast() {
        try {
            val i = Intent("com.tallyvox.VOICE_HEARD").apply {
                setPackage(packageName)
            }
            sendBroadcast(i)
        } catch (_: Exception) {}
    }

    // ─── Service Lifecycle ─────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)

        val p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentPrimary = p.getInt("primary", 0)
        currentSecondary = p.getInt("secondary", 0)
        currentInterval = p.getInt("interval", 100)
        loadPhraseState()

        val wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TallyVox::WakeLock").apply {
                acquire(10 * 60 * 60 * 1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true

        val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        try {
            when (intent?.action) {
                "com.tallyvox.ACTION_INCREMENT" -> increment(this)
                "com.tallyvox.ACTION_DECREMENT" -> decrement(this)
                "com.tallyvox.ACTION_START_RECORDING" -> {
                    if (!isRecording) startRecording()
                }
                "com.tallyvox.ACTION_STOP_RECORDING" -> {
                    stopRecording()
                }
                "com.tallyvox.ACTION_SAVE_PHRASE" -> {
                    val text = intent.getStringExtra("phrase_text") ?: ""
                    savePhrase(text)
                }
                "com.tallyvox.ACTION_DELETE_PHRASE" -> {
                    deletePhrase()
                    stopListeningLoop()
                }
                "com.tallyvox.ACTION_START_LISTENING" -> startListeningLoop()
                "com.tallyvox.ACTION_STOP_LISTENING" -> stopListeningLoop()
            }
            if (hasNotifPerm) {
                startForeground(NOTIF_ID, buildNotification(this))
            }
        } catch (e: Exception) {
            android.util.Log.e("TallyVox", "onStartCommand failed", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
