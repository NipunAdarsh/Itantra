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

/** Which link layer is currently carrying transmissions */
enum class TransportType { WIFI, BLUETOOTH }

/** A Bluetooth peer surfaced to the device picker (paired or freshly discovered) */
@Stable
data class BluetoothPeerUi(
    val name    : String,
    val address : String,
    val isPaired: Boolean
)

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
