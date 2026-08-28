package com.example.itantra.ui

import androidx.compose.runtime.Stable

// ── Domain models (pure data, no logic) ─────────────────────────────────────

/** Peer-to-peer connection state communicated down to the UI layer */
@Stable
sealed class P2pState {
    /** Successfully connected to a peer */
    data class Connected(val peerAddress: String) : P2pState()
    /** Scanning / attempting to discover a peer */
    object Searching : P2pState()
    /** No active connection */
    object Disconnected : P2pState()
}

/** A single entry in the live transcript feed */
@Stable
data class TranscriptMessage(
    val id         : Long,
    val text       : String,
    val direction  : Direction,
    val isEmergency: Boolean    = false,
    val timestamp  : String     = ""          // e.g. "14:32"
) {
    enum class Direction { SENT, RECEIVED }
}
