package com.tallyvox.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tallyvox.CounterViewModel
import com.tallyvox.ui.theme.DarkError
import com.tallyvox.ui.theme.DarkGold
import com.tallyvox.ui.PhraseConfirmDialog
import com.tallyvox.ui.DeletePhraseDialog
import com.tallyvox.ui.theme.DarkPrimary
import com.tallyvox.ui.theme.DarkSuccess
import com.tallyvox.ui.theme.LightError
import com.tallyvox.ui.theme.LightGold
import com.tallyvox.ui.theme.LightPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CounterScreen(
    viewModel: CounterViewModel,
    onRequestMicPermission: () -> Unit
) {
    val counters by viewModel.counters.collectAsState()
    val voiceListening by viewModel.voiceListening.collectAsState()
    val voiceHeard by viewModel.voiceHeard.collectAsState()
    val micPermissionGranted by viewModel.micPermissionGranted.collectAsState()

    // Banner: show on first voice mode entry, reset on re-entry
    var showBannerOnEntry by remember { mutableStateOf(true) }
    var wasInVoiceMode by remember { mutableStateOf(false) }
    // Track how many times we've entered voice mode — used as Crossfade key
    var voiceModeEntryCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Track re-entry to voice mode for banner + force fresh VoiceModeScreen composition
    LaunchedEffect(counters.isVoiceMode) {
        if (counters.isVoiceMode) {
            voiceModeEntryCount++
            // Reset voiceUiState to NO_PHRASE on every voice mode entry
            // This fixes stale cached Compose state from previous voice mode visit
            viewModel.resetVoiceUiState()
            if (wasInVoiceMode) {
                showBannerOnEntry = true
            }
        }
        wasInVoiceMode = counters.isVoiceMode
        if (counters.isVoiceMode) {
            showBannerOnEntry = false
        }
    }
    val voiceUiState by viewModel.voiceUiState.collectAsState()
    val savedPhraseText by viewModel.savedPhraseText.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()
    val isDark = isSystemInDarkTheme()

    val primaryColor = if (isDark) DarkPrimary else LightPrimary
    val successColor = if (isDark) DarkSuccess else Color(0xFF437a22)
    val errorColor = if (isDark) DarkError else LightError
    val goldColor = if (isDark) DarkGold else LightGold

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showResetPrimaryDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    val showPhraseConfirmDialog by viewModel.showPhraseConfirmDialog.collectAsState()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsState()

    var lastTapTime by remember { mutableLongStateOf(0L) }
    val DEBOUNCE_MS = 300L

    var isPlusLongPressing by remember { mutableStateOf(false) }
    var isMinusLongPressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Voice heard pulse animation
    val heardScale = if (voiceHeard) 1.08f else 1f
    val heardAlpha = if (voiceHeard) 0.85f else 0f

    // Voice pulse via alternating LaunchedEffect
    var pulsePhase by remember { mutableStateOf(false) }
    LaunchedEffect(pulsePhase) {
        delay(750)
        pulsePhase = !pulsePhase
    }
    val voicePulseScale = if (pulsePhase) 1.15f else 1f
    val voicePulseAlpha = if (pulsePhase) 1f else 0.7f

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onBackground
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App title + mode toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TallyVox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )

                // Mode toggle pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val manualActive = !counters.isVoiceMode
                    val voiceActive = counters.isVoiceMode

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (manualActive) primaryColor else Color.Transparent)
                            .clickable {
                                if (counters.isVoiceMode) viewModel.toggleVoiceMode()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MANUAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (manualActive) Color.White else textMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (voiceActive) errorColor else Color.Transparent)
                            .clickable {
                                if (!counters.isVoiceMode) {
                                    viewModel.toggleVoiceMode()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VOICE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (voiceActive) Color.White else textMuted
                        )
                    }
                }
            }


            // `key()` forces Compose to FULLY dispose and remount VoiceModeScreen on every
            // voice mode entry — fixes stale cached composition bug where old UI showed
            if (counters.isVoiceMode) {
                key(voiceModeEntryCount) {
                    VoiceModeScreen(
                        voiceUiState = voiceUiState,
                        savedPhrase = savedPhraseText,
                        isListening = voiceListening,
                        isDark = isDark,
                        isRecording = isRecording,
                        onStartRecording = { viewModel.onStartRecording() },
                        onStopRecording = { viewModel.onStopRecording() },
                        onSavePhrase = { viewModel.onSavePhrase(it) },
                        onReRecord = { viewModel.onReRecord() },
                        onStartListening = { viewModel.onStartListening() },
                        onStopListening = { viewModel.onStopListening() },
                        onDeletePhrase = { viewModel.onDeletePhrase() },
                        recordingAmplitude = recordingAmplitude,
                        showBanner = showBannerOnEntry,
                        hasMicPermission = micPermissionGranted,
                        onRequestMicPermission = onRequestMicPermission,
                        isVoiceHeard = voiceHeard,
                        primaryCount = counters.primary,
                        secondaryCount = counters.secondary,
                        interval = counters.interval,
                        onMinusInterval = { viewModel.decrementInterval() },
                        onPlusInterval = { viewModel.incrementInterval() },
                        onResetPrimary = { viewModel.resetPrimary() },
                        onResetAll = { viewModel.resetPrimary() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Counter display area — always shown (weight=1f fills remaining space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(surfaceColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PRIMARY COUNT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = primaryColor.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = counters.primary.toString(),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryColor,
                        lineHeight = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Interval sub-counter row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "INTERVAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = textMuted.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = counters.secondary.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = goldColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "@${counters.interval}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = goldColor.copy(alpha = 1f)
                        )
                    }
                }

                // Voice heard indicator overlay
                if (voiceHeard) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(heardAlpha)
                            .background(primaryColor.copy(alpha = 0.15f))
                            .scale(heardScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Heard!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice listening indicator
            if (voiceListening) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF1a0d2e) else Color(0xFFf5e6f5))
                        .border(1.dp, if (isDark) Color(0xFF4a1a5e) else Color(0xFFddaadd), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .scale(voicePulseScale)
                            .alpha(voicePulseAlpha)
                            .clip(CircleShape)
                            .background(errorColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎙", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Listening for phrase…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Button row: MINUS | PLUS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus button
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .height(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (counters.primary > 0) errorColor.copy(alpha = 0.15f) else surfaceVariant)
                        .then(
                            if (counters.primary > 0) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime < DEBOUNCE_MS) return@detectTapGestures
                                            lastTapTime = now
                                            isMinusLongPressing = true
                                            tryAwaitRelease()
                                            isMinusLongPressing = false
                                        }
                                    )
                                }
                            } else Modifier
                        )
                        .clickable(enabled = counters.primary > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < DEBOUNCE_MS) return@clickable
                            lastTapTime = now
                            viewModel.decrement()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "−",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (counters.primary > 0) errorColor else textMuted.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "MINUS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = if (counters.primary > 0) errorColor.copy(alpha = 0.7f) else textMuted.copy(alpha = 0.3f)
                        )
                    }
                }

                // Plus button
                Box(
                    modifier = Modifier
                        .weight(0.70f)
                        .height(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(successColor.copy(alpha = 0.15f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < DEBOUNCE_MS) return@detectTapGestures
                                    lastTapTime = now
                                    isPlusLongPressing = true
                                    tryAwaitRelease()
                                    isPlusLongPressing = false
                                }
                            )
                        }
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < DEBOUNCE_MS) return@clickable
                            lastTapTime = now
                            viewModel.increment()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = successColor
                        )
                        Text(
                            text = "PLUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = successColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: INTERVAL | RESET | RESET ALL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Interval button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceVariant)
                        .clickable { showIntervalDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙ INTERVAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted
                    )
                }

                // Reset Primary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceVariant)
                        .clickable { showResetPrimaryDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↺ RESET",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted
                    )
                }

                // Reset Both
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(errorColor.copy(alpha = 0.1f))
                        .clickable { showResetAllDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠ RESET ALL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = errorColor.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Volume key hint
            Text(
                text = "Vol+ = +1   Vol− = −1",
                fontSize = 11.sp,
                color = textMuted.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Voice mode dialogs
    if (showPhraseConfirmDialog) {
        PhraseConfirmDialog(
            initialPhrase = savedPhraseText,
            onDismiss = { viewModel.onPhraseDialogCancel() },
            onSave = { phrase ->
                viewModel.onSavePhrase(phrase)
                viewModel.dismissPhraseConfirmDialog()
            },
            onReRecord = {
                viewModel.onReRecord()
                viewModel.dismissPhraseConfirmDialog()
            },
            isDark = isDark
        )
    }

    if (showDeleteConfirmDialog) {
        DeletePhraseDialog(
            onDismiss = { viewModel.dismissDeleteDialog() },
            onConfirm = {
                viewModel.confirmDeletePhrase()
            },
            isDark = isDark
        )
    }

    // Counter dialogs
    if (showIntervalDialog) {
        IntervalDialog(
            currentInterval = counters.interval,
            onDismiss = { showIntervalDialog = false },
            onConfirm = { newInterval ->
                viewModel.setInterval(newInterval)
                showIntervalDialog = false
            }
        )
    }

    if (showResetPrimaryDialog) {
        ConfirmDialog(
            title = "Reset Primary Counter?",
            message = "This will reset the main counter to 0. The interval setting will be preserved.",
            confirmText = "Reset",
            confirmColor = errorColor,
            onDismiss = { showResetPrimaryDialog = false },
            onConfirm = {
                viewModel.resetPrimary()
                showResetPrimaryDialog = false
            }
        )
    }

    if (showResetAllDialog) {
        ConfirmDialog(
            title = "Reset ALL Counters?",
            message = "This will reset both the primary and interval counters to 0. The interval setting will be preserved.",
            confirmText = "Reset All",
            confirmColor = errorColor,
            onDismiss = { showResetAllDialog = false },
            onConfirm = {
                viewModel.resetPrimary()
                viewModel.resetSecondary()
                showResetAllDialog = false
            }
        )
    }
}

@Composable
fun IntervalDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentInterval.toString()) }
    val isDark = isSystemInDarkTheme()
    val goldColor = if (isDark) DarkGold else LightGold

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set Interval",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sub-counter increments every N primary counts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.length <= 5)) {
                            textValue = newValue
                        }
                    },
                    label = { Text("Interval (1–99999)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = goldColor,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val n = textValue.toIntOrNull()
                            if (n != null && n in 1..99999) {
                                onConfirm(n)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
