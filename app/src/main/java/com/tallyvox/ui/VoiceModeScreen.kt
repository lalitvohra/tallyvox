package com.tallyvox.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tallyvox.ui.theme.DarkError
import com.tallyvox.ui.theme.DarkSuccess
import com.tallyvox.ui.theme.LightError
import com.tallyvox.ui.theme.LightSuccess
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import java.io.File
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

// ─── Voice Mode UI States ────────────────────────────────────────────────────

enum class VoiceUiState {
    NO_PHRASE,       // State 1: nothing saved yet
    RECORDING,        // State 2: actively recording
    PHRASE_IDLE,     // State 3a: saved, not listening
    PHRASE_LISTENING // State 3b: saved, actively listening
}

// ─── Voice Mode Screen ─────────────────────────────────────────────────────

@Composable
fun VoiceModeScreen(
    voiceUiState: VoiceUiState,
    savedPhrase: String,
    isListening: Boolean,
    isDark: Boolean,
    isRecording: Boolean,
    hasMicPermission: Boolean,
    showBanner: Boolean,
    onRequestMicPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSavePhrase: (String) -> Unit,
    onReRecord: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onDeletePhrase: () -> Unit,
    recordingAmplitude: Float,
    isVoiceHeard: Boolean
) {
    val context = LocalContext.current



    val primaryColor = if (isDark) Color(0xFF4f98a3) else Color(0xFF01696f)
    val successColor = if (isDark) DarkSuccess else LightSuccess
    val errorColor = if (isDark) DarkError else LightError
    val surfaceColor = if (isDark) Color(0xFF171614) else Color(0xFFf9f8f5)
    val surfaceVariant = if (isDark) Color(0xFF1d1c1a) else Color(0xFFfbfbf9)
    val textColor = if (isDark) Color(0xFFe8e7e3) else Color(0xFF1a1815)
    val textMuted = if (isDark) Color(0xFF888785) else Color(0xFF6b6a66)
    val bgColor = if (isDark) Color(0xFF111110) else Color(0xFFf7f6f2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, if (isDark) Color(0xFF343230) else Color(0xFFd4d1ca), RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── State 1: No Phrase ──────────────────────────────────────────────
        if (voiceUiState == VoiceUiState.NO_PHRASE) {
            NoPhraseState(
                primaryColor = primaryColor,
                textColor = textColor,
                textMuted = textMuted,
                showBanner = showBanner,
                hasMicPermission = hasMicPermission,
                onRequestMicPermission = onRequestMicPermission,
                onRecord = onStartRecording
            )
        }

        // ── State 2: Recording (directly controlled by isRecording flag) ──
        if (isRecording) {
            RecordingState(
                amplitude = recordingAmplitude,
                isRecording = isRecording,
                errorColor = errorColor,
                primaryColor = primaryColor,
                textColor = textColor,
                textMuted = textMuted,
                onStop = onStopRecording
            )
        }

        // ── State 3: Phrase Saved ───────────────────────────────────────────
        if (voiceUiState == VoiceUiState.PHRASE_IDLE || voiceUiState == VoiceUiState.PHRASE_LISTENING) {
            PhraseIdleState(
                voiceUiState = voiceUiState,
                savedPhrase = savedPhrase,
                isListening = isListening,
                isVoiceHeard = isVoiceHeard,
                successColor = successColor,
                errorColor = errorColor,
                textColor = textColor,
                textMuted = textMuted,
                primaryColor = primaryColor,
                onStartListening = onStartListening,
                onStopListening = onStopListening,
                onDelete = onDeletePhrase
            )
        }
    }
}

// ─── State 1: No Phrase ──────────────────────────────────────────────────────

@Composable
private fun NoPhraseState(
    primaryColor: Color,
    textColor: Color,
    textMuted: Color,
    showBanner: Boolean,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onRecord: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        // Grey mic icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(textMuted.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎙", fontSize = 36.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No voice phrase saved",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Record a phrase to enable voice counting",
            fontSize = 13.sp,
            color = textMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // RECORD button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryColor)
                .clickable {
                    if (!hasMicPermission) {
                        try {
                            onRequestMicPermission()
                        } catch (_: Exception) {
                            // Permission permanently denied — user needs app settings
                        }
                    } else {
                        onRecord()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎙 RECORD PHRASE",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// ─── State 2: Recording ────────────────────────────────────────────────────

@Composable
private fun RecordingState(
    amplitude: Float,
    isRecording: Boolean,
    errorColor: Color,
    primaryColor: Color,
    textColor: Color,
    textMuted: Color,
    onStop: () -> Unit
) {
    // Auto-stop countdown: 10 seconds max recording
    var secondsLeft by remember { mutableIntStateOf(10) }
    LaunchedEffect(isRecording) {
        secondsLeft = 10
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onStop()
    }

    // Pulsing red animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pulsing mic with red ring
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing outer ring
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(errorColor.copy(alpha = 0.15f))
            )
            // Mic icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(errorColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎙", fontSize = 30.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Recording… Speak your phrase now",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = errorColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Countdown timer — auto-stops at 0
        Text(
            text = "Auto-stop in ${secondsLeft}s  |  Tap ⏹ to stop now",
            fontSize = 12.sp,
            color = textMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Waveform bars
        WaveformBars(
            amplitude = amplitude,
            barColor = errorColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // STOP button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(errorColor)
                .clickable { onStop() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⏹ STOP RECORDING",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// ─── State 3: Phrase Saved ──────────────────────────────────────────────────

@Composable
private fun PhraseIdleState(
    voiceUiState: VoiceUiState,
    savedPhrase: String,
    isListening: Boolean,
    isVoiceHeard: Boolean,
    successColor: Color,
    errorColor: Color,
    textColor: Color,
    textMuted: Color,
    primaryColor: Color,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onDelete: () -> Unit
) {
    // Pulsing green ring when listening
    val infiniteTransition = rememberInfiniteTransition(label = "listenPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listenPulseScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Green checkmark or pulsing mic
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(successColor.copy(alpha = 0.15f))
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) successColor.copy(alpha = 0.2f)
                        else successColor.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isListening) "🎙" else "✓",
                    fontSize = if (isListening) 30.sp else 36.sp,
                    color = if (isListening) Color.Unspecified else successColor
                )
            }

            // "Heard!" overlay
            if (isVoiceHeard) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(successColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✓", fontSize = 36.sp, color = successColor, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isListening) {
            Text(
                text = "Listening for:",
                fontSize = 13.sp,
                color = textMuted
            )
            Text(
                text = "\"$savedPhrase\"",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = successColor,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Voice phrase saved!",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Say your phrase to add 1 to the counter",
                fontSize = 13.sp,
                color = textMuted,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // START / STOP LISTENING button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isListening) errorColor else successColor)
                .clickable { if (isListening) onStopListening() else onStartListening() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isListening) "⏹ STOP LISTENING" else "🔴 START LISTENING",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DELETE button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, errorColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑 DELETE PHRASE & RE-RECORD",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = errorColor
            )
        }
    }
}

// ─── Waveform Bars ───────────────────────────────────────────────────────────

@Composable
private fun WaveformBars(
    amplitude: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 24
    val normalizedAmp = (amplitude / 32767f).coerceIn(0f, 1f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { i ->
            val fraction = i.toFloat() / (barCount - 1)
            val distFromCenter = kotlin.math.abs(fraction - 0.5f) * 2f
            val baseHeight = (1f - distFromCenter * 0.6f)
            val randomFactor = listOf(0.5f, 0.7f, 0.9f, 1.1f, 0.8f, 1.0f, 0.6f, 0.85f)[i % 8]
            val barHeight = (baseHeight * normalizedAmp * randomFactor).coerceIn(0.08f, 1f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
    }
}

// ─── Phrase Confirm Dialog ─────────────────────────────────────────────────

@Composable
fun PhraseConfirmDialog(
    initialPhrase: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReRecord: () -> Unit,
    isDark: Boolean
) {
    var phraseText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialPhrase) }
    val textColor = if (isDark) Color(0xFFe8e7e3) else Color(0xFF1a1815)
    val surfaceColor = if (isDark) Color(0xFF171614) else Color(0xFFf9f8f5)
    val primaryColor = if (isDark) Color(0xFF4f98a3) else Color(0xFF01696f)
    val goldColor = if (isDark) Color(0xFFe8af34) else Color(0xFFc08800)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Confirm your phrase",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Type what you just said — this is what we'll listen for",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFF888785) else Color(0xFF6b6a66),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = phraseText,
                    onValueChange = { phraseText = it },
                    placeholder = { Text("e.g. next count") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onReRecord
                    ) {
                        Text("Re-record", color = if (isDark) Color(0xFF888785) else Color(0xFF6b6a66))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (phraseText.isNotBlank()) onSave(phraseText.trim())
                        },
                        enabled = phraseText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            disabledContainerColor = primaryColor.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Delete Confirm Dialog ──────────────────────────────────────────────────

@Composable
fun DeletePhraseDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isDark: Boolean
) {
    val textColor = if (isDark) Color(0xFFe8e7e3) else Color(0xFF1a1815)
    val surfaceColor = if (isDark) Color(0xFF171614) else Color(0xFFf9f8f5)
    val errorColor = if (isDark) DarkError else LightError

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = surfaceColor,
        title = {
            Text("Delete voice phrase?", fontWeight = FontWeight.Bold, color = textColor)
        },
        text = {
            Text(
                "Your recorded audio and saved phrase will be permanently deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFF888785) else Color(0xFF6b6a66)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = errorColor)
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = if (isDark) Color(0xFF888785) else Color(0xFF6b6a66))
            }
        }
    )
}
