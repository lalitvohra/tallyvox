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
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import com.tallyvox.ui.theme.DarkDivider
import com.tallyvox.ui.theme.DarkError
import com.tallyvox.ui.theme.DarkGold
import com.tallyvox.ui.theme.DarkSuccess
import com.tallyvox.ui.theme.LightDivider
import com.tallyvox.ui.theme.LightError
import com.tallyvox.ui.theme.LightGold
import com.tallyvox.ui.theme.LightSuccess
import kotlinx.coroutines.delay

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
    isVoiceHeard: Boolean,
    primaryCount: Int = 0,
    secondaryCount: Int = 0,
    interval: Int = 100,
    onMinusInterval: () -> Unit = {},
    onPlusInterval: () -> Unit = {},
    onResetPrimary: () -> Unit = {},
    onResetAll: () -> Unit = {}
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
    val goldColor = if (isDark) DarkGold else LightGold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, if (isDark) Color(0xFF343230) else Color(0xFFd4d1ca), RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── HERO COUNTER — takes most space ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // COUNT label
                Text(
                    text = "COUNT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = textMuted.copy(alpha = 0.7f)
                )
                // Primary count — BIG
                Text(
                    text = primaryCount.toString(),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor,
                    lineHeight = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                // INTERVAL label
                Text(
                    text = "INTERVAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = textMuted.copy(alpha = 0.7f)
                )
                // Secondary @ interval — large
                Text(
                    text = "$secondaryCount @ $interval",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = goldColor
                )
            }
        }

        // Divider
        HorizontalDivider(
            color = if (isDark) DarkDivider else LightDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // ── State 1: No Phrase ──────────────────────────────────────────────
        if (voiceUiState == VoiceUiState.NO_PHRASE) {
            CompactNoPhraseState(
                primaryColor = primaryColor,
                textColor = textColor,
                textMuted = textMuted,
                hasMicPermission = hasMicPermission,
                onRequestMicPermission = onRequestMicPermission,
                onRecord = onStartRecording
            )
        }

        // ── State 2: Recording ───────────────────────────────────────────
        else if (isRecording) {
            CompactRecordingState(
                amplitude = recordingAmplitude,
                isRecording = isRecording,
                errorColor = errorColor,
                primaryColor = primaryColor,
                textMuted = textMuted,
                onStop = onStopRecording
            )
        }

        // ── State 3: Phrase Saved ───────────────────────────────────────────
        if (voiceUiState == VoiceUiState.PHRASE_IDLE || voiceUiState == VoiceUiState.PHRASE_LISTENING) {
            CompactPhraseIdleState(
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

        // Control buttons row: INTERVAL +/- | RESET | RESET ALL
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // INTERVAL - button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariant)
                    .clickable { onMinusInterval() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "−", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textMuted)
            }
            // INTERVAL + button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariant)
                    .clickable { onPlusInterval() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textMuted)
            }
            // RESET button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariant)
                    .clickable { onResetPrimary() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↺ RESET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textMuted)
            }
            // RESET ALL button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariant)
                    .clickable { onResetAll() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⟳ ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textMuted)
            }
        }
    }
}

// ─── Compact NoPhrase State ─────────────────────────────────────────────────

@Composable
private fun CompactNoPhraseState(
    primaryColor: Color,
    textColor: Color,
    textMuted: Color,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onRecord: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Small hint text
        Text(
            text = "Record a phrase to enable voice counting",
            fontSize = 12.sp,
            color = textMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Compact RECORD button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primaryColor)
                .clickable {
                    if (!hasMicPermission) {
                        try { onRequestMicPermission() } catch (_: Exception) {}
                    } else {
                        onRecord()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎙 RECORD PHRASE",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// ─── Compact Recording State ─────────────────────────────────────────────────

@Composable
private fun CompactRecordingState(
    amplitude: Float,
    isRecording: Boolean,
    errorColor: Color,
    primaryColor: Color,
    textMuted: Color,
    onStop: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(10) }
    LaunchedEffect(isRecording) {
        secondsLeft = 10
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onStop()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pulsing mic icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(errorColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎙", fontSize = 22.sp)
            }
            Column {
                Text(
                    text = "Recording…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = errorColor
                )
                Text(
                    text = "Auto-stop in ${secondsLeft}s",
                    fontSize = 12.sp,
                    color = textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Waveform
        WaveformBars(
            amplitude = amplitude,
            barColor = errorColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // STOP button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(errorColor)
                .clickable { onStop() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⏹ STOP",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// ─── Compact Phrase Idle State ──────────────────────────────────────────────

@Composable
private fun CompactPhraseIdleState(
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
    val infiniteTransition = rememberInfiniteTransition(label = "listenPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
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
        // Phrase line + status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(
                        if (isListening) Modifier.scale(pulseScale) else Modifier
                    )
                    .clip(CircleShape)
                    .background(
                        when {
                            isVoiceHeard -> successColor.copy(alpha = 0.3f)
                            isListening -> successColor.copy(alpha = 0.2f)
                            else -> primaryColor.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isVoiceHeard -> "✓"
                        isListening -> "🎙"
                        else -> "✓"
                    },
                    fontSize = 20.sp,
                    color = when {
                        isVoiceHeard -> successColor
                        isListening -> Color.Unspecified
                        else -> successColor
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"$savedPhrase\"",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isListening) successColor else textColor,
                    maxLines = 1
                )
                Text(
                    text = when {
                        isVoiceHeard -> "Heard!"
                        isListening -> "Listening…"
                        else -> "Tap START to activate"
                    },
                    fontSize = 11.sp,
                    color = when {
                        isVoiceHeard -> successColor
                        isListening -> textMuted
                        else -> textMuted
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // START / STOP button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isListening) errorColor else successColor)
                .clickable { if (isListening) onStopListening() else onStartListening() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isListening) "⏹ STOP LISTENING" else "🔴 START LISTENING",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Delete button — compact text link
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, errorColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑 Delete & Re-record",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = errorColor.copy(alpha = 0.8f)
            )
        }
    }
}

// ─── Waveform Bars ──────────────────────────────────────────────────────────

@Composable
private fun WaveformBars(
    amplitude: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 40
    val animatedAmps = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }

    LaunchedEffect(amplitude) {
        animatedAmps.removeAt(0)
        animatedAmps.add(maxOf(0.1f, minOf(1f, amplitude)))
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w / barCount
        val spacing = barWidth * 0.3f
        val maxBarH = h * 0.9f

        animatedAmps.forEachIndexed { i, amp ->
            val barH = maxOf(4.dp.toPx(), amp * maxBarH)
            val x = i * barWidth + spacing / 2
            val y = (h - barH) / 2
            drawRoundRect(
                color = barColor.copy(alpha = 0.6f + amp * 0.4f),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth - spacing, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

// ─── Phrase Confirm Dialog ──────────────────────────────────────────────────

@Composable
fun PhraseConfirmDialog(
    initialPhrase: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReRecord: () -> Unit,
    isDark: Boolean
) {
    var phraseText by remember { mutableStateOf(initialPhrase) }
    val textColor = if (isDark) Color(0xFFe8e7e3) else Color(0xFF1a1815)
    val surfaceColor = if (isDark) Color(0xFF171614) else Color(0xFFf9f8f5)
    val primaryColor = if (isDark) Color(0xFF4f98a3) else Color(0xFF01696f)

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

// ─── Delete Phrase Dialog ───────────────────────────────────────────────────

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
