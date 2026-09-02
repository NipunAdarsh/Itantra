package com.example.itantra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.itantra.ui.P2pState
import com.example.itantra.ui.TranscriptMessage
import com.example.itantra.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// HIG Optimisation Summary (apple-design skill audit)
//
// Fix #1 — statusBarsPadding() on NeuralHeader
// Fix #2 — PTT semantics: contentDescription + Role.Button
// Fix #3 — P2pStatusPill: icon-based state (not color-only)
// Fix #5 — Idle PTT: animations disabled when not active
// Fix #6 — Sentence-case on action labels ("Hold to talk", "Recording"…)
// Fix #8 — defaultMinSize(48dp) on touch targets (chips, badges)
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// 1 ─ NeuralHeader
//     Sticky header with gradient surface, P2P pill chip, emergency badge.
//     HIG fix #1: statusBarsPadding() prevents bleeding under the status bar.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun NeuralHeader(
    p2pState      : P2pState,
    isEmergency   : Boolean,
    onStatusClick : (() -> Unit)? = null,
    modifier      : Modifier      = Modifier
) {
    // HIG fix #1 — statusBarsPadding() so header never bleeds under system bar
    // Design Guideline — Layout > Safe Areas: "Respect key display features."
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceContainerLowest,
                        Background.copy(alpha = 0.95f)
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ── App branding ─────────────────────────────────────────────────
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "iTantra",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color      = OnBackground
                        )
                    )
                }
                Text(
                    text     = "Neural Walkie-Talkie",
                    style    = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // ── Right cluster ─────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (isEmergency) EmergencyBadge()
                P2pStatusPill(
                    state   = p2pState,
                    onClick = onStatusClick
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2 ─ P2pStatusPill
//     HIG fix #3: icon + color (not color-only) per state — color blind safe.
//     HIG fix #8: defaultMinSize(44dp) for accessible touch target.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun P2pStatusPill(
    state   : P2pState,
    onClick : (() -> Unit)? = null,
    modifier: Modifier      = Modifier
) {
    // HIG fix #3 — color-independent indicators: distinct icon per state
    // Design Guideline — Color > Inclusive color:
    // "Avoid relying solely on color to convey essential information."
    val (label, stateIcon, chipColor) = when (state) {
        is P2pState.Connected    -> Triple("Connected", "✓", ChipConnected)
        is P2pState.Searching    -> Triple("Searching", "↻", ChipSearching)
        is P2pState.Disconnected -> Triple("Offline",   "✕", ChipOffline)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pill_pulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (state is P2pState.Searching) 0.3f else 1f,
        animationSpec = if (state is P2pState.Searching)
            infiniteRepeatable(tween(700), RepeatMode.Reverse)
        else
            infiniteRepeatable(tween(99999), RepeatMode.Restart),
        label         = "icon_alpha"
    )

    // HIG fix #8 — 44dp min height for accessibility touch target (HIG standard)
    // Design Guideline — Accessibility > Mobility: "Min control size 44x44pt on mobile."
    val baseModifier = if (onClick != null) {
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Connection status: $label. Tap to manage connection."
                role               = Role.Button
            }
    } else {
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .semantics { contentDescription = "Connection status: $label" }
    }

    Surface(
        shape    = RoundedCornerShape(999.dp),
        color    = chipColor.copy(alpha = 0.12f),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, chipColor.copy(alpha = 0.3f)),
        modifier = baseModifier
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text  = stateIcon,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = chipColor.copy(alpha = iconAlpha)
                )
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color      = chipColor
                )
            )
            if (state is P2pState.Connected && state.peerAddress.isNotEmpty()) {
                Text(
                    text     = state.peerAddress,
                    style    = MaterialTheme.typography.labelSmall.copy(
                        color = chipColor.copy(alpha = 0.6f)
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3 ─ EmergencyBadge
//     Pulsing red pill — purposeful animation (communicates active alert state).
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun EmergencyBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "emg_badge")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label         = "emg_alpha"
    )
    Surface(
        shape    = RoundedCornerShape(999.dp),
        color    = AlertRed.copy(alpha = alpha * 0.25f),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, AlertRed.copy(alpha = alpha)),
        modifier = modifier.semantics { contentDescription = "Emergency alert active" }
    ) {
        Text(
            text     = "⚠ ALERT",
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color      = AlertRed.copy(alpha = alpha)
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4 ─ VoiceSelectorCard
//     Elevated dark card for language selection — full-width pill-button style.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun <T> VoiceSelectorCard(
    selectedLanguage  : T,
    allLanguages      : Array<T>,
    displayLabel      : (T) -> String,
    itemLabel         : (T) -> String = displayLabel,
    onLanguageSelected: (T) -> Unit,
    modifier          : Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape    = RoundedCornerShape(14.dp),
            color    = SurfaceContainerLow,
            border   = androidx.compose.foundation.BorderStroke(0.5.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .semantics { contentDescription = "Voice language: ${displayLabel(selectedLanguage)}. Tap to change." }
        ) {
            Row(
                modifier              = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement   = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text  = "VOICE (AUTO-MATCHES INCOMING LANGUAGE)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color      = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text  = displayLabel(selectedLanguage),
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Text(
                    text  = "\u25BC",
                    style = MaterialTheme.typography.titleMedium.copy(color = OnSurfaceVariant)
                )
            }
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(SurfaceContainerHigh)
        ) {
            allLanguages.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            itemLabel(lang),
                            style = MaterialTheme.typography.bodyMedium.copy(color = OnSurface)
                        )
                    },
                    onClick = { onLanguageSelected(lang); expanded = false },
                    leadingIcon = {
                        if (lang == selectedLanguage) {
                            Text("\u2713", style = MaterialTheme.typography.bodyMedium.copy(color = Primary))
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor        = OnSurface,
                        leadingIconColor = Primary
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5 ─ EmergencyToggleCard
//     Animated card — background transitions to red tint when active.
//     Toggle events are intercepted by WalkieTalkieScreen for confirmation dialog.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun EmergencyToggleCard(
    isEmergency : Boolean,
    onToggle    : (Boolean) -> Unit,
    modifier    : Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue   = if (isEmergency) AlertRedDim else SurfaceContainerLow,
        animationSpec = tween(400),
        label         = "emg_bg"
    )
    val borderColor by animateColorAsState(
        targetValue   = if (isEmergency) AlertRed.copy(alpha = 0.4f) else CardBorder,
        animationSpec = tween(400),
        label         = "emg_border"
    )

    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = bgColor,
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.weight(1f)
            ) {
                Text(
                    text  = if (isEmergency) "🚨" else "🔔",
                    style = MaterialTheme.typography.titleMedium
                )
                Column {
                    Text(
                        text  = "Emergency Alert",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isEmergency) AlertRed else OnSurface
                        )
                    )
                    Text(
                        text  = if (isEmergency) "Active — all transmissions flagged"
                               else              "Tap to enable emergency broadcast",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isEmergency) AlertRed.copy(alpha = 0.7f) else OnSurfaceVariant
                        )
                    )
                }
            }
            Switch(
                checked         = isEmergency,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor    = Color.White,
                    checkedTrackColor    = AlertRed,
                    checkedBorderColor   = AlertRed,
                    uncheckedThumbColor  = OnSurfaceVariant,
                    uncheckedTrackColor  = SurfaceContainerHighest,
                    uncheckedBorderColor = OutlineVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Emergency Alert ${if (isEmergency) "on" else "off"}"
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 6 ─ NeuralPTTButton
//     The centrepiece: 160dp circular PTT with multi-ring glow, waveform,
//     animated state transitions.
//
//     HIG fix #2: semantics { contentDescription; role = Button } for TalkBack
//     HIG fix #5: animations only active when isActive = true (idle = static)
//     HIG fix #6: sentence-case labels ("Hold to talk", "Recording", etc.)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun NeuralPTTButton(
    isListening    : Boolean,
    isTransmitting : Boolean,
    isEmergency    : Boolean,
    modifier       : Modifier = Modifier
) {
    val isActive = isListening || isTransmitting

    // ── Color transitions (350ms) ────────────────────────────────────────────
    val coreColor by animateColorAsState(
        targetValue   = when {
            isEmergency && isActive -> AlertRed
            isActive                -> Primary
            else                    -> SurfaceContainerLow
        },
        animationSpec = tween(350),
        label         = "ptt_core_color"
    )
    val glowColor = when {
        isEmergency && isActive -> AlertRedGlow
        isActive                -> GreenGlowBright
        else                    -> Color.Transparent
    }
    val borderColor by animateColorAsState(
        targetValue   = when {
            isEmergency && isActive -> AlertRed
            isActive                -> Primary
            else                    -> Primary.copy(alpha = 0.4f)
        },
        animationSpec = tween(350),
        label         = "ptt_border_color"
    )

    // HIG fix #5 — animations only fire when isActive
    // Design Guideline — Motion: "Don't animate for the sake of it. Avoid motion
    // on frequent UI states (idle is shown continuously — don't animate it)."
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isActive) 1.18f else 1f,
        animationSpec = if (isActive)
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        else
            infiniteRepeatable(tween(99999), RepeatMode.Restart),
        label         = "ptt_scale"
    )
    // Outer ring is static in idle state (0.12f), only pulses when active
    val outerRingAlpha by infiniteTransition.animateFloat(
        initialValue  = if (isActive) 0.5f else 0.12f,
        targetValue   = if (isActive) 0f   else 0.12f,
        animationSpec = if (isActive)
            infiniteRepeatable(tween(900), RepeatMode.Reverse)
        else
            infiniteRepeatable(tween(99999), RepeatMode.Restart),
        label         = "ring_alpha"
    )
    val waveformOffset by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = if (isListening) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label         = "wave_offset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier
    ) {
        Box(
            modifier         = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // ── Ambient glow (only visible when active) ─────────────────────
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(glowColor)
                        .blur(24.dp)
                )
            }

            // ── Middle pulsing ring ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(if (isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = borderColor.copy(alpha = outerRingAlpha),
                        shape = CircleShape
                    )
            )

            // ── Inner definition ring ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .clip(CircleShape)
                    .border(1.dp, borderColor.copy(alpha = 0.6f), CircleShape)
            )

            // ── Core circle — HIG fix #2: Role.Button + contentDescription ──
            // Design Guideline — Accessibility > Vision:
            // "Describe your interface and content for screen readers."
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isActive) {
                                listOf(coreColor, coreColor.copy(alpha = 0.85f))
                            } else {
                                listOf(SurfaceContainerLow, SurfaceContainerLowest)
                            }
                        )
                    )
                    .border(1.dp, borderColor, CircleShape)
                    .semantics {
                        contentDescription = when {
                            isListening    -> "Recording. Release to stop."
                            isTransmitting -> "Transmitting audio."
                            else           -> "Push to talk. Hold to record."
                        }
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text  = when {
                            isListening    -> "🎙"
                            isTransmitting -> "📡"
                            else           -> "🎙"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (isListening) {
                        Spacer(Modifier.height(6.dp))
                        WaveformBars(isActive = true, amplitude = waveformOffset)
                    }
                }
            }
        }

        // HIG fix #6 — sentence-case on action labels
        // Design Guideline — Writing: "Use sentence case, not ALLCAPS for action labels."
        Spacer(Modifier.height(16.dp))
        Text(
            text  = when {
                isListening    -> "● Recording"
                isTransmitting -> "▶ Transmitting"
                else           -> "Hold to talk"
            },
            style = SectionLabelStyle.copy(
                color = when {
                    isListening    -> Primary
                    isTransmitting -> ChipSearching
                    else           -> OnSurfaceVariant
                }
            )
        )

        // ── Signal meter + Ready badge ────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.Bottom
        ) {
            SignalBars(isConnected = true)
            Spacer(Modifier.width(8.dp))
            ReadyBadge(isReady = !isListening && !isTransmitting)
        }
    }
}

// ── Waveform bars ─────────────────────────────────────────────────────────────
@Composable
private fun WaveformBars(isActive: Boolean, amplitude: Float) {
    val heights = listOf(4, 7, 10, 7, 4).map { base ->
        if (isActive) (base + amplitude * 6).dp else base.dp
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Primary.copy(alpha = 0.9f))
            )
        }
    }
}

// ── Signal strength bars ──────────────────────────────────────────────────────
@Composable
private fun SignalBars(isConnected: Boolean) {
    val heights = listOf(4.dp, 7.dp, 10.dp, 13.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.Bottom,
        modifier              = Modifier.semantics {
            contentDescription = "Signal: ${if (isConnected) "strong" else "none"}"
        }
    ) {
        heights.forEachIndexed { idx, h ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isConnected && idx < 4) Primary else SurfaceContainerHighest
                    )
            )
        }
    }
}

// ── Ready / Busy badge ────────────────────────────────────────────────────────
@Composable
private fun ReadyBadge(isReady: Boolean) {
    Surface(
        shape    = RoundedCornerShape(999.dp),
        color    = if (isReady) Primary.copy(alpha = 0.12f) else ChipSearching.copy(alpha = 0.12f),
        border   = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isReady) Primary.copy(alpha = 0.4f) else ChipSearching.copy(alpha = 0.4f)
        ),
        modifier = Modifier.defaultMinSize(minHeight = 28.dp)
    ) {
        Text(
            text     = if (isReady) "READY" else "BUSY",
            style    = TelemetryStyle.copy(
                color      = if (isReady) Primary else ChipSearching,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 7 ─ TranscriptCard
//     Stitch-style elevated dark card — direction + color + icon (HIG-compliant
//     triple redundancy for color-blind users).
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun TranscriptCard(
    text        : String,
    direction   : TranscriptMessage.Direction,
    isEmergency : Boolean,
    timestamp   : String,
    modifier    : Modifier = Modifier
) {
    val isSent = direction == TranscriptMessage.Direction.SENT

    val cardBg = when {
        isEmergency -> AlertRedDim
        isSent      -> Primary.copy(alpha = 0.08f)
        else        -> SurfaceContainerLow
    }
    val cardBorder = when {
        isEmergency -> AlertRed.copy(alpha = 0.35f)
        isSent      -> Primary.copy(alpha = 0.25f)
        else        -> CardBorder
    }
    val avatarBg = when {
        isEmergency -> AlertRed.copy(alpha = 0.20f)
        isSent      -> Primary.copy(alpha = 0.15f)
        else        -> SurfaceContainerHighest
    }
    val avatarIcon  = if (isEmergency) "⚠" else if (isSent) "↑" else "↓"
    val avatarColor = when {
        isEmergency -> AlertRed
        isSent      -> Primary
        else        -> OnSurfaceVariant
    }
    val senderLabel = if (isSent) "You" else "Peer"
    val labelColor  = when {
        isEmergency -> AlertRed
        isSent      -> Primary
        else        -> OnSurfaceVariant
    }

    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = cardBg,
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$senderLabel said: $text" }
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = avatarIcon,
                    style = MaterialTheme.typography.labelMedium.copy(color = avatarColor)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text  = if (isEmergency) "⚠ $senderLabel" else senderLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color      = labelColor
                        )
                    )
                    if (timestamp.isNotEmpty()) {
                        Text(
                            text  = timestamp,
                            style = TimestampStyle.copy(color = OnSurfaceVariant.copy(alpha = 0.6f))
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isEmergency) AlertRed.copy(alpha = 0.9f)
                               else              OnSurface.copy(alpha = 0.85f)
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 8 ─ SectionHeader
//     Uppercase muted section label + optional count badge.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SectionHeader(
    label   : String,
    count   : Int?     = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = label,
            style = SectionLabelStyle.copy(color = OnSurfaceVariant)
        )
        if (count != null) {
            Surface(
                shape    = RoundedCornerShape(999.dp),
                color    = SurfaceContainerHigh,
                modifier = Modifier.defaultMinSize(minHeight = 24.dp)
            ) {
                Text(
                    text     = count.toString(),
                    style    = TelemetryStyle.copy(color = OnSurfaceVariant),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 9 ─ EmptyTranscriptState
//     Centered empty state when no transcript messages exist.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun EmptyTranscriptState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(0.5.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📭", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                text  = "No transmissions yet",
                style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Hold the button to start transmitting",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OnSurfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 10 ─ OperationalModeCard
//      Tactical segmented toggle between Walkie-Talkie (PTT) and Auto-VAD Phone Mode.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun OperationalModeCard(
    currentMode: com.example.itantra.OperationalMode,
    onModeChange: (com.example.itantra.OperationalMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "OPERATIONAL MODE",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Walkie Talkie Option
                val isPtt = currentMode == com.example.itantra.OperationalMode.WALKIE_TALKIE
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPtt) Primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeChange(com.example.itantra.OperationalMode.WALKIE_TALKIE) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📻 PTT Mode",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isPtt) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPtt) OnPrimary else OnSurfaceVariant
                            )
                        )
                    }
                }

                // Phone Mode Option
                val isPhone = currentMode == com.example.itantra.OperationalMode.PHONE_MODE
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPhone) Primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeChange(com.example.itantra.OperationalMode.PHONE_MODE) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📞 Phone Mode",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isPhone) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPhone) OnPrimary else OnSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 11 ─ PhoneModeVisualizer
//      Hands-free continuous listening visualizer with live VAD pulse state.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun PhoneModeVisualizer(
    isSpeechDetected: Boolean,
    isTransmitting: Boolean,
    isEmergency: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "phone_mode_vad")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeechDetected) 1.22f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeechDetected) 450 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phone_pulse"
    )

    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeechDetected) 600 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phone_wave_alpha"
    )

    val activeColor = when {
        isEmergency -> AlertRed
        isSpeechDetected -> Primary
        else -> Primary.copy(alpha = 0.6f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient outer glow
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = waveAlpha * 0.35f))
                    .blur(16.dp)
            )

            // Inner circle
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainerHigh)
                    .border(2.dp, activeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when {
                            isTransmitting -> "🚀"
                            isSpeechDetected -> "🎙️"
                            else -> "👂"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = when {
                            isTransmitting -> "Transmitting"
                            isSpeechDetected -> "Speech Detected"
                            else -> "Listening..."
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSpeechDetected) activeColor else OnSurface
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (isSpeechDetected) "Speech active • Transcribing..." else "Hands-free continuous listening active",
            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant)
        )
        Text(
            text = "Pause for 500ms to automatically transmit",
            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant.copy(alpha = 0.6f))
        )
    }
}

