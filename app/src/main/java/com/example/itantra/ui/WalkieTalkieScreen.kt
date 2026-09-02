package com.example.itantra.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.itantra.AppLanguage
import com.example.itantra.ui.TranscriptMessage.Direction
import com.example.itantra.ui.components.*
import com.example.itantra.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// WalkieTalkieScreen — Neural Tactical Interface (Auto-Discovery + Full Duplex)
//
// Design:  Stitch Project 13078437623162040590
// HIG:     Apple Design Standards (Haptics, Safe Areas, Inclusive Color)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WalkieTalkieScreen(
    // ── State ────────────────────────────────────────────────────────────────
    isListening          : Boolean,
    isTransmitting       : Boolean,
    p2pConnectionState   : P2pState,
    selectedLanguage     : AppLanguage,
    transcriptList       : List<TranscriptMessage>,
    isEmergencyAlert     : Boolean,
    operationalMode      : com.example.itantra.OperationalMode = com.example.itantra.OperationalMode.WALKIE_TALKIE,
    isVadSpeaking        : Boolean = false,
    // ── Callbacks ────────────────────────────────────────────────────────────
    onPushToTalkPressed  : () -> Unit,
    onPushToTalkReleased : () -> Unit,
    onVoiceChange         : (AppLanguage) -> Unit,
    onEmergencyToggle     : (Boolean) -> Unit,
    onOperationalModeChange: (com.example.itantra.OperationalMode) -> Unit = {},
    modifier              : Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val haptic    = LocalHapticFeedback.current

    // HIG Fix: Emergency Alert modal confirmation
    var pendingEmergencyState by remember { mutableStateOf<Boolean?>(null) }
    var showEmergencyConfirm  by remember { mutableStateOf(false) }

    LaunchedEffect(transcriptList.size) {
        if (transcriptList.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // ── Emergency confirmation dialog ─────────────────────────────────────────
    if (showEmergencyConfirm) {
        AlertDialog(
            onDismissRequest = {
                showEmergencyConfirm = false
                pendingEmergencyState = null
            },
            containerColor     = SurfaceContainer,
            titleContentColor  = OnSurface,
            textContentColor   = OnSurfaceVariant,
            title = {
                Text(
                    text  = if (pendingEmergencyState == true) "Enable Emergency Alert?"
                            else "Disable Emergency Alert?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text  = if (pendingEmergencyState == true)
                        "All transmissions will be flagged as emergency broadcasts. Only use this in a genuine emergency."
                    else
                        "Emergency broadcast mode will be deactivated.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingEmergencyState?.let { onEmergencyToggle(it) }
                        showEmergencyConfirm  = false
                        pendingEmergencyState = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingEmergencyState == true) AlertRed else Primary,
                        contentColor   = if (pendingEmergencyState == true) Color.White else OnPrimary
                    )
                ) {
                    Text(if (pendingEmergencyState == true) "Enable" else "Disable")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmergencyConfirm  = false
                        pendingEmergencyState = null
                    }
                ) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }

    // ── Main Screen Container ─────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            // Subtle ambient radial neon glow at top (Design depth layer)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.04f),
                            Background.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.5f, 0f)
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Sticky Header (Auto-Discovery status shown in P2pStatusPill) ───
            NeuralHeader(
                p2pState    = p2pConnectionState,
                isEmergency = isEmergencyAlert
            )

            // ── Scrollable Content ────────────────────────────────────────────
            LazyColumn(
                state          = listState,
                modifier       = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start  = 20.dp,
                    end    = 20.dp,
                    top    = 16.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ── Voice / Language Selector ─────────────────────────────────
                item {
                    VoiceSelectorCard(
                        selectedLanguage   = selectedLanguage,
                        allLanguages       = AppLanguage.values(),
                        displayLabel       = { "${it.flag} ${it.label}" },
                        itemLabel          = { "${it.flag} ${it.label}" },
                        onLanguageSelected = onVoiceChange
                    )
                }

                // ── Operational Mode Toggle Card ─────────────────────────────
                item {
                    OperationalModeCard(
                        currentMode  = operationalMode,
                        onModeChange = onOperationalModeChange
                    )
                }

                // ── Emergency Toggle Card (with Confirmation Guard) ───────────
                item {
                    EmergencyToggleCard(
                        isEmergency = isEmergencyAlert,
                        onToggle    = { requested ->
                            pendingEmergencyState = requested
                            showEmergencyConfirm  = true
                        }
                    )
                }

                // ── Section: MODE CENTREPIECE ─────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(
                        label = if (operationalMode == com.example.itantra.OperationalMode.WALKIE_TALKIE)
                            "PUSH TO TALK"
                        else
                            "PHONE MODE (AUTO-VAD)"
                    )
                }

                // ── Centrepiece: Neural PTT Button OR Phone Mode Visualizer ───
                item {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (operationalMode == com.example.itantra.OperationalMode.WALKIE_TALKIE) {
                            NeuralPTTButton(
                                isListening    = isListening,
                                isTransmitting = isTransmitting,
                                isEmergency    = isEmergencyAlert,
                                modifier       = Modifier.pointerInteropFilter { event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onPushToTalkPressed()
                                            true
                                        }
                                        MotionEvent.ACTION_UP,
                                        MotionEvent.ACTION_CANCEL -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onPushToTalkReleased()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            )
                        } else {
                            PhoneModeVisualizer(
                                isSpeechDetected = isVadSpeaking,
                                isTransmitting   = isTransmitting,
                                isEmergency      = isEmergencyAlert
                            )
                        }
                    }
                }

                // ── Section: TRANSCRIPT ───────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(
                        color     = CardBorder,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(bottom = 12.dp)
                    )
                    SectionHeader(
                        label = "TRANSCRIPT",
                        count = transcriptList.size.takeIf { it > 0 }
                    )
                }

                // ── Empty State ───────────────────────────────────────────────
                if (transcriptList.isEmpty()) {
                    item { EmptyTranscriptState() }
                }

                // ── Transcript Feed Cards (Two-way communications) ────────────
                items(
                    items = transcriptList,
                    key   = { it.id }
                ) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { -it / 4 },
                            animationSpec  = tween(300, easing = FastOutSlowInEasing)
                        )
                    ) {
                        TranscriptCard(
                            text        = msg.text,
                            direction   = msg.direction,
                            isEmergency = msg.isEmergency,
                            timestamp   = msg.timestamp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews — matching 4 key application states
// ─────────────────────────────────────────────────────────────────────────────

private val mockTranscripts = listOf(
    TranscriptMessage(1L, "Unit Alpha reporting position. All clear.",
        Direction.SENT, false, "14:32"),
    TranscriptMessage(2L, "Copy that. Moving to sector seven.",
        Direction.RECEIVED, false, "14:31"),
    TranscriptMessage(3L, "[ALERT] Requesting immediate evacuation support!",
        Direction.SENT, true, "14:30"),
    TranscriptMessage(4L, "On our way. ETA five minutes.",
        Direction.RECEIVED, false, "14:29"),
)

@Preview(name = "⬛ Connected — Idle", showSystemUi = true, showBackground = true, backgroundColor = 0xFF11131B)
@Composable
private fun Preview_Connected() {
    ITantraTheme {
        WalkieTalkieScreen(
            isListening          = false,
            isTransmitting       = false,
            p2pConnectionState   = P2pState.Connected("192.168.1.42"),
            selectedLanguage     = AppLanguage.ENGLISH,
            transcriptList       = mockTranscripts,
            isEmergencyAlert     = false,
            onPushToTalkPressed  = {},
            onPushToTalkReleased = {},
            onVoiceChange        = {},
            onEmergencyToggle    = {}
        )
    }
}

@Preview(name = "🟢 Listening — Active", showSystemUi = true, showBackground = true, backgroundColor = 0xFF11131B)
@Composable
private fun Preview_Listening() {
    ITantraTheme {
        WalkieTalkieScreen(
            isListening          = true,
            isTransmitting       = false,
            p2pConnectionState   = P2pState.Connected("192.168.1.42"),
            selectedLanguage     = AppLanguage.HINDI,
            transcriptList       = mockTranscripts,
            isEmergencyAlert     = false,
            onPushToTalkPressed  = {},
            onPushToTalkReleased = {},
            onVoiceChange        = {},
            onEmergencyToggle    = {}
        )
    }
}

@Preview(name = "🔴 Emergency — Active", showSystemUi = true, showBackground = true, backgroundColor = 0xFF11131B)
@Composable
private fun Preview_Emergency() {
    ITantraTheme {
        WalkieTalkieScreen(
            isListening          = true,
            isTransmitting       = false,
            p2pConnectionState   = P2pState.Connected("192.168.1.7"),
            selectedLanguage     = AppLanguage.ENGLISH,
            transcriptList       = mockTranscripts,
            isEmergencyAlert     = true,
            onPushToTalkPressed  = {},
            onPushToTalkReleased = {},
            onVoiceChange        = {},
            onEmergencyToggle    = {}
        )
    }
}

@Preview(name = "🟡 Searching — Empty", showSystemUi = true, showBackground = true, backgroundColor = 0xFF11131B)
@Composable
private fun Preview_Searching() {
    ITantraTheme {
        WalkieTalkieScreen(
            isListening          = false,
            isTransmitting       = false,
            p2pConnectionState   = P2pState.Searching,
            selectedLanguage     = AppLanguage.ENGLISH,
            transcriptList       = emptyList(),
            isEmergencyAlert     = false,
            onPushToTalkPressed  = {},
            onPushToTalkReleased = {},
            onVoiceChange        = {},
            onEmergencyToggle    = {}
        )
    }
}
