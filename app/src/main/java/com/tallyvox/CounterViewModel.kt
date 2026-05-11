package com.tallyvox

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tallyvox.service.CounterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CounterViewModel(private val application: Application) : AndroidViewModel(application) {

    companion object {
        const val PREFS_NAME = "tallyvox_prefs"
    }

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Load initial state from SharedPreferences (same file CounterService uses)
    private val initial = prefs.getInt("primary", 0)

    private val _counters = MutableStateFlow(
        Counters(
            primary = initial,
            secondary = prefs.getInt("secondary", 0),
            interval = prefs.getInt("interval", 100),
            isVoiceMode = prefs.getBoolean("voice_mode", false)
        )
    )
    val counters: StateFlow<Counters> = _counters.asStateFlow()

    private val _voiceListening = MutableStateFlow(false)
    val voiceListening: StateFlow<Boolean> = _voiceListening.asStateFlow()

    // Mic permission state — exposed for Composable to read
    private val _micPermissionGranted = MutableStateFlow(false)
    val micPermissionGranted: StateFlow<Boolean> = _micPermissionGranted.asStateFlow()

    private val _voiceHeard = MutableStateFlow(false)
    val voiceHeard: StateFlow<Boolean> = _voiceHeard.asStateFlow()

    // Voice Mode screen state
    private val _voiceUiState = MutableStateFlow(com.tallyvox.ui.VoiceUiState.NO_PHRASE)
    val voiceUiState: StateFlow<com.tallyvox.ui.VoiceUiState> = _voiceUiState.asStateFlow()

    private val _savedPhraseText = MutableStateFlow("")
    val savedPhraseText: StateFlow<String> = _savedPhraseText.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _showPhraseConfirmDialog = MutableStateFlow(false)
    val showPhraseConfirmDialog: StateFlow<Boolean> = _showPhraseConfirmDialog.asStateFlow()

    // True while confirmation dialog is showing after stopping recording.
    // Keeps the UI in RECORDING state until user Save/Cancel/ReRecord.
    private val _phraseConfirmPending = MutableStateFlow(false)
    val phraseConfirmPending: StateFlow<Boolean> = _phraseConfirmPending.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var voiceRestartBlocked = false

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Broadcast receiver for CounterService broadcasts
    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.tallyvox.VOICE_LISTENING" -> {
                    _voiceListening.value = intent.getBooleanExtra("listening", false)
                    updateVoiceUiState()
                }
                "com.tallyvox.VOICE_HEARD" -> {
                    _voiceHeard.value = true
                    increment()
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(600)
                        _voiceHeard.value = false
                    }
                }
                "com.tallyvox.VOICE_AMPLITUDE" -> {
                    val amp = intent.getIntExtra("amplitude", 0)
                    _recordingAmplitude.value = amp.toFloat()
                }
            }
        }
    }

    init {
        // Load phrase state from CounterService companion
        CounterService::class.java.getDeclaredField("savedPhraseText").apply { isAccessible = true }
        _savedPhraseText.value = prefs.getString(CounterService.KEY_SAVED_PHRASE, "") ?: ""
        val phraseRecorded = prefs.getBoolean(CounterService.KEY_PHRASE_RECORDED, false)

        _voiceUiState.value = if (phraseRecorded) com.tallyvox.ui.VoiceUiState.PHRASE_IDLE else com.tallyvox.ui.VoiceUiState.NO_PHRASE

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction("com.tallyvox.VOICE_LISTENING")
            addAction("com.tallyvox.VOICE_HEARD")
            addAction("com.tallyvox.VOICE_AMPLITUDE")
        }
        try {
            @Suppress("UNCHECKED_CAST")
            application.registerReceiver(voiceReceiver, filter, 0)
        } catch (_: Exception) {}

        if (_counters.value.isVoiceMode && hasMicPermission()) {
            startListening()
        }
    }

    // Override hasMicPermission to use our tracked state (more reliable for Composable)
    private fun hasMicPermission(): Boolean {
        return _micPermissionGranted.value ||
            application.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Called by MainActivity when permission result arrives
    fun onMicPermissionResult(granted: Boolean) {
        _micPermissionGranted.value = granted
        if (granted) {
            startListening()
        }
    }


    private fun updateVoiceUiState() {
        val listening = _voiceListening.value
        val phraseRecorded = _savedPhraseText.value.isNotBlank()

        // While confirmation dialog is showing, keep UI in RECORDING state
        // (avoids PHRASE_IDLE flicker before dialog appears on stop)
        _voiceUiState.value = when {
            _phraseConfirmPending.value -> com.tallyvox.ui.VoiceUiState.RECORDING
            !phraseRecorded -> com.tallyvox.ui.VoiceUiState.NO_PHRASE
            _isRecording.value -> com.tallyvox.ui.VoiceUiState.RECORDING
            listening -> com.tallyvox.ui.VoiceUiState.PHRASE_LISTENING
            else -> com.tallyvox.ui.VoiceUiState.PHRASE_IDLE
        }
    }

    // Save to SharedPreferences (same file CounterService and CounterWidget use)
    private fun saveState() {
        prefs.edit().apply {
            putInt("primary", _counters.value.primary)
            putInt("secondary", _counters.value.secondary)
            putInt("interval", _counters.value.interval)
            putBoolean("voice_mode", _counters.value.isVoiceMode)
            apply()
        }
    }

    // Sync ViewModel → Service companion vars + update notification
    private fun notifyChange() {
        CounterService.currentPrimary = _counters.value.primary
        CounterService.currentSecondary = _counters.value.secondary
        CounterService.currentInterval = _counters.value.interval
        CounterService.updateNotification(application)
        CounterWidget.updateAllWidgets(application)
    }

    // Called when notification action updates count — keep ViewModel in sync
    fun syncFromService(primary: Int, secondary: Int, interval: Int) {
        CounterService.currentPrimary = primary
        CounterService.currentSecondary = secondary
        CounterService.currentInterval = interval
        _counters.value = Counters(primary, secondary, interval, _counters.value.isVoiceMode)
        saveState()
    }

    fun increment() {
        val c = _counters.value
        val newPrimary = c.primary + 1
        val newSecondary = if (newPrimary > 0 && c.interval > 0 && newPrimary % c.interval == 0) {
            c.secondary + 1
        } else {
            c.secondary
        }
        _counters.value = c.copy(primary = newPrimary, secondary = newSecondary)
        if (newPrimary > 0 && c.interval > 0 && newPrimary % c.interval == 0) {
            haptic(200)
        } else {
            haptic(50)
        }
        saveState()
        notifyChange()
    }

    fun decrement() {
        val c = _counters.value
        if (c.primary <= 0) return
        val newPrimary = c.primary - 1
        val newSecondary = if (c.primary > 0 && c.interval > 0 && c.primary % c.interval == 0) {
            maxOf(0, c.secondary - 1)
        } else {
            c.secondary
        }
        _counters.value = c.copy(primary = newPrimary, secondary = newSecondary)
        haptic(50)
        saveState()
        notifyChange()
    }

    fun resetPrimary() {
        _counters.value = _counters.value.copy(primary = 0, secondary = 0)
        haptic(100)
        saveState()
        notifyChange()
    }

    fun resetSecondary() {
        _counters.value = _counters.value.copy(secondary = 0)
        saveState()
        notifyChange()
    }

    fun setInterval(n: Int) {
        if (n in 1..99999) {
            _counters.value = _counters.value.copy(interval = n)
            saveState()
            notifyChange()
        }
    }

    fun toggleVoiceMode() {
        val newMode = !_counters.value.isVoiceMode
        _counters.value = _counters.value.copy(isVoiceMode = newMode)
        saveState()
        if (newMode) {
            // Switching INTO voice mode — stop old listening, let VoiceModeScreen handle its own flow
            stopListening()
        } else {
            // Switching OUT of voice mode — stop everything
            stopListening()
        }
    }

    fun startListening() {
        if (!hasMicPermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(application)) return
        voiceRestartBlocked = false
        _voiceListening.value = true
        startSpeechRecognition()
        updateVoiceUiState()
    }

    fun stopListening() {
        voiceRestartBlocked = true
        _voiceListening.value = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        updateVoiceUiState()
    }

    private fun startSpeechRecognition() {
        if (voiceRestartBlocked || !_voiceListening.value) return
        if (!hasMicPermission()) {
            _voiceListening.value = false
            updateVoiceUiState()
            return
        }

        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            _voiceListening.value = false
            updateVoiceUiState()
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)
        } catch (_: Exception) {
            _voiceListening.value = false
            updateVoiceUiState()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (voiceRestartBlocked || !_voiceListening.value) return
                val restartable = error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                )
                if (restartable) {
                    mainHandler.postDelayed({
                        if (!voiceRestartBlocked && _voiceListening.value) {
                            startSpeechRecognition()
                        }
                    }, 1000)
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                if (voiceRestartBlocked || !_voiceListening.value) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    triggerVoiceHeard()
                }
                mainHandler.postDelayed({
                    if (!voiceRestartBlocked && _voiceListening.value) {
                        startSpeechRecognition()
                    }
                }, 800)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            _voiceListening.value = false
            updateVoiceUiState()
        }
    }

    private fun triggerVoiceHeard() {
        increment()
        _voiceHeard.value = true
        haptic(30)
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _voiceHeard.value = false
        }
    }

    // ─── Voice Mode Screen Methods ─────────────────────────────────────────

    fun onStartRecording() {
        if (!hasMicPermission()) {
            android.widget.Toast.makeText(application, "Mic permission needed!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        _isRecording.value = true
        // Set placeholder so phraseRecorded=true → after stop, voiceUiState=PHRASE_IDLE → dialog shows
        _savedPhraseText.value = " "
        updateVoiceUiState()
        // Stop the speech recognizer loop — it runs independently of MediaRecorder
        // and would otherwise keep matching the OLD saved phrase during recording
        try {
            val stopIntent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_STOP_LISTENING"
            }
            application.startService(stopIntent)
        } catch (_: Exception) {}
        android.widget.Toast.makeText(application, "Recording started!", android.widget.Toast.LENGTH_SHORT).show()
        // Tell CounterService to start recording
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_START_RECORDING"
            }
            application.startService(intent)
        } catch (_: Exception) {}
    }

    fun onStopRecording(): Boolean {
        // Idempotent: if already showing confirm dialog or not recording, skip
        if (!_isRecording.value || _phraseConfirmPending.value) return false
        android.util.Log.e("TallyVox", "onStopRecording called, isRecording=${_isRecording.value}")
        android.widget.Toast.makeText(application, "Stopping recording…", android.widget.Toast.LENGTH_SHORT).show()
        _isRecording.value = false
        // Do NOT call updateVoiceUiState() here — dialog must appear while
        // UI is still in RECORDING state (spec requirement). Keep _phraseConfirmPending
        // to block state transition until user resolves the dialog.
        var recordingOk = false
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_STOP_RECORDING"
            }
            application.startService(intent)
            android.util.Log.e("TallyVox", "onStopRecording: service called ok")
            recordingOk = true
        } catch (e: Exception) {
            android.util.Log.e("TallyVox", "onStopRecording: service error ${e.message}")
            recordingOk = false
        }
        if (recordingOk) {
            _phraseConfirmPending.value = true
            _showPhraseConfirmDialog.value = true
            android.util.Log.e("TallyVox", "onStopRecording: dialog should show now")
        }
        return recordingOk
    }

    fun onSavePhrase(phrase: String) {
        _phraseConfirmPending.value = false
        _savedPhraseText.value = phrase
        prefs.edit().putString(CounterService.KEY_SAVED_PHRASE, phrase).apply()
        prefs.edit().putBoolean(CounterService.KEY_PHRASE_RECORDED, true).apply()
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_SAVE_PHRASE"
                putExtra("phrase_text", phrase)
            }
            application.startService(intent)
        } catch (_: Exception) {}
        updateVoiceUiState()  // transitions to PHRASE_IDLE or PHRASE_LISTENING
        // Restart the listening loop so the new phrase is active immediately
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_START_LISTENING"
            }
            application.startService(intent)
        } catch (_: Exception) {}
    }

    fun onReRecord() {
        _isRecording.value = false
        _savedPhraseText.value = ""
        updateVoiceUiState()
        onStartRecording()
    }

    fun onStartListening() {
        if (!hasMicPermission()) return
        _voiceListening.value = true
        updateVoiceUiState()
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_START_LISTENING"
            }
            application.startService(intent)
        } catch (_: Exception) {}
    }

    fun onStopListening() {
        _voiceListening.value = false
        updateVoiceUiState()
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_STOP_LISTENING"
            }
            application.startService(intent)
        } catch (_: Exception) {}
    }

    fun onDeletePhrase() {
        _showDeleteConfirmDialog.value = true
    }

    fun dismissPhraseConfirmDialog() {
        _showPhraseConfirmDialog.value = false
    }

    // Called when user taps outside dialog or presses back — cancels the recording
    fun onPhraseDialogCancel() {
        _phraseConfirmPending.value = false
        _savedPhraseText.value = ""
        _showPhraseConfirmDialog.value = false
        _isRecording.value = false
        updateVoiceUiState()
    }

    fun confirmDeletePhrase() {
        _phraseConfirmPending.value = false
        _showDeleteConfirmDialog.value = false
        _savedPhraseText.value = ""
        _voiceListening.value = false
        _isRecording.value = false
        updateVoiceUiState()
        try {
            val intent = Intent(application, CounterService::class.java).apply {
                action = "com.tallyvox.ACTION_DELETE_PHRASE"
            }
            application.startService(intent)
        } catch (_: Exception) {}
    }

    fun dismissDeleteDialog() {
        _showDeleteConfirmDialog.value = false
    }

    private fun haptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        stopListening()
        try {
            application.unregisterReceiver(voiceReceiver)
        } catch (_: Exception) {}
        super.onCleared()
    }
}
