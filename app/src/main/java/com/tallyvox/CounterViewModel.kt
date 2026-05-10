package com.tallyvox

import android.app.Application
import android.content.Context
import android.content.Intent
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

    private val prefs = application.getSharedPreferences("tallyvox", Context.MODE_PRIVATE)

    private val _counters = MutableStateFlow(
        Counters(
            primary = prefs.getInt("primary", 0),
            secondary = prefs.getInt("secondary", 0),
            interval = prefs.getInt("interval", 100),
            isVoiceMode = prefs.getBoolean("voice_mode", false)
        )
    )
    val counters: StateFlow<Counters> = _counters.asStateFlow()

    private val _voiceListening = MutableStateFlow(false)
    val voiceListening: StateFlow<Boolean> = _voiceListening.asStateFlow()

    private val _voiceHeard = MutableStateFlow(false)
    val voiceHeard: StateFlow<Boolean> = _voiceHeard.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        if (_counters.value.isVoiceMode && hasMicPermission()) {
            startListening()
        }
    }

    private fun hasMicPermission(): Boolean {
        return application.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun saveState() {
        prefs.edit().apply {
            putInt("primary", _counters.value.primary)
            putInt("secondary", _counters.value.secondary)
            putInt("interval", _counters.value.interval)
            putBoolean("voice_mode", _counters.value.isVoiceMode)
            apply()
        }
    }

    private fun notifyChange() {
        CounterService.updateNotification(
            _counters.value.primary,
            _counters.value.secondary,
            _counters.value.interval,
            application
        )
    }

    // Called when notification action updates count directly
    fun syncFromService(primary: Int, secondary: Int, interval: Int) {
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
            if (hasMicPermission()) startListening() else stopListening()
        } else {
            stopListening()
        }
    }

    fun startListening() {
        if (!hasMicPermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(application)) return
        _voiceListening.value = true
        startSpeechRecognition()
    }

    fun stopListening() {
        _voiceListening.value = false
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun startSpeechRecognition() {
        if (!hasMicPermission()) {
            _voiceListening.value = false
            return
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
            // ignore
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)
        } catch (_: Exception) {
            _voiceListening.value = false
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
                val restartable = error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                )
                if (restartable && _voiceListening.value) {
                    mainHandler.postDelayed({ startSpeechRecognition() }, 500)
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    triggerVoiceHeard()
                }
                if (_voiceListening.value) {
                    mainHandler.postDelayed({ startSpeechRecognition() }, 300)
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            _voiceListening.value = false
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
        } catch (_: Exception) {
            // ignore
        }
    }

    override fun onCleared() {
        speechRecognizer?.destroy()
        super.onCleared()
    }
}
