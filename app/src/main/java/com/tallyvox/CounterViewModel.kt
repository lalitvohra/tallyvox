package com.tallyvox

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CounterViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("tallyvox", Context.MODE_PRIVATE)

    private val _counters = MutableStateFlow(
        Counters(
            primary = prefs.getInt("primary", 0),
            secondary = prefs.getInt("secondary", 0),
            interval = prefs.getInt("interval", 100),
            isVoiceMode = false
        )
    )
    val counters: StateFlow<Counters> = _counters.asStateFlow()

    private val _voiceListening = MutableStateFlow(false)
    val voiceListening: StateFlow<Boolean> = _voiceListening.asStateFlow()

    private val _voiceHeard = MutableStateFlow(false)
    val voiceHeard: StateFlow<Boolean> = _voiceHeard.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        loadState()
    }

    private fun loadState() {
        _counters.value = Counters(
            primary = prefs.getInt("primary", 0),
            secondary = prefs.getInt("secondary", 0),
            interval = prefs.getInt("interval", 100),
            isVoiceMode = prefs.getBoolean("voice_mode", false)
        )
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
    }

    fun decrement() {
        val c = _counters.value
        if (c.primary <= 0) return
        val newPrimary = c.primary - 1
        val prevSecondary = if (c.primary > 0 && c.interval > 0 && c.primary % c.interval == 0 && newPrimary > 0) {
            maxOf(0, c.secondary - 1)
        } else {
            c.secondary
        }
        _counters.value = c.copy(primary = newPrimary, secondary = prevSecondary)
        haptic(50)
        saveState()
    }

    fun resetPrimary() {
        _counters.value = _counters.value.copy(primary = 0, secondary = 0)
        haptic(100)
        saveState()
    }

    fun resetSecondary() {
        _counters.value = _counters.value.copy(secondary = 0)
        saveState()
    }

    fun setInterval(n: Int) {
        if (n in 1..99999) {
            _counters.value = _counters.value.copy(interval = n)
            saveState()
        }
    }

    fun toggleVoiceMode() {
        val newMode = !_counters.value.isVoiceMode
        _counters.value = _counters.value.copy(isVoiceMode = newMode)
        saveState()
        if (newMode) startListening() else stopListening()
    }

    fun startListening() {
        _voiceListening.value = true
        startSpeechRecognition()
    }

    fun stopListening() {
        _voiceListening.value = false
        speechRecognizer?.stopListening()
    }

    private fun startSpeechRecognition() {
        val ctx = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (_voiceListening.value) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (_voiceListening.value) startSpeechRecognition()
                        }, 500)
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        triggerVoiceHeard()
                    }
                    if (_voiceListening.value) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (_voiceListening.value) startSpeechRecognition()
                        }, 300)
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            startListening(intent)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    override fun onCleared() {
        speechRecognizer?.destroy()
        super.onCleared()
    }
}
