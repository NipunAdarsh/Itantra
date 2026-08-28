package com.example.itantra

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.itantra.ui.P2pState
import com.example.itantra.ui.TranscriptMessage
import com.example.itantra.ui.WalkieTalkieScreen
import com.example.itantra.ui.theme.ITantraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {

    // ── Engine & Networking ───────────────────────────────────────────────────
    private lateinit var sherpaEngine     : SherpaOnnxEngine
    private lateinit var networkManager   : NetworkManager
    private lateinit var discoveryManager : DiscoveryManager

    // ── UI State ─────────────────────────────────────────────────────────────
    private val transcriptState       = mutableStateListOf<TranscriptMessage>()
    private val isListeningState      = mutableStateOf(false)
    private val isTransmittingState   = mutableStateOf(false)
    private val isEmergencyState      = mutableStateOf(false)
    private val p2pState              = mutableStateOf<P2pState>(P2pState.Searching)
    private val selectedLanguageState = mutableStateOf(AppLanguage.ENGLISH)

    private var messageIdCounter = 0L
    private fun nextId() = ++messageIdCounter

    private fun currentTimestamp(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Runtime Permissions Request (Audio + WiFi/Location for Discovery) ─
        val permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* results handled; mic & discovery start gracefully */ }
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // ── Full Duplex Network Manager ──────────────────────────────────────
        networkManager = NetworkManager { receivedText ->
            runOnUiThread {
                transcriptState.add(
                    0,
                    TranscriptMessage(
                        id          = nextId(),
                        text        = receivedText,
                        direction   = TranscriptMessage.Direction.RECEIVED,
                        isEmergency = receivedText.startsWith("[ALERT]"),
                        timestamp   = currentTimestamp()
                    )
                )
                // Play received audio on both devices when not holding mic
                if (!isListeningState.value) {
                    sherpaEngine.synthesizeAndPlay(receivedText)
                }
            }
        }

        // ── UDP Broadcast Auto-Discovery Manager ──────────────────────────────
        discoveryManager = DiscoveryManager(this) { peerIp ->
            runOnUiThread {
                p2pState.value = P2pState.Connected(peerIp)
                networkManager.setPeerIp(peerIp)
            }
        }

        // ── Neural Voice Engine (STT -> Network Send -> TTS) ─────────────────
        sherpaEngine = SherpaOnnxEngine(this) { sttText ->
            runOnUiThread {
                val finalMessage = if (isEmergencyState.value) "[ALERT]$sttText" else sttText
                transcriptState.add(
                    0,
                    TranscriptMessage(
                        id          = nextId(),
                        text        = finalMessage,
                        direction   = TranscriptMessage.Direction.SENT,
                        isEmergency = isEmergencyState.value,
                        timestamp   = currentTimestamp()
                    )
                )
                lifecycleScope.launch {
                    isTransmittingState.value = true
                    networkManager.sendText(finalMessage)
                    delay(500) // Visual feedback duration
                    isTransmittingState.value = false
                }
            }
        }

        // ── Compose UI ───────────────────────────────────────────────────────
        setContent {
            ITantraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val isListening      by isListeningState
                    val isTransmitting   by isTransmittingState
                    val isEmergency      by isEmergencyState
                    val p2pConnection    by p2pState
                    val selectedLanguage by selectedLanguageState

                    WalkieTalkieScreen(
                        isListening          = isListening,
                        isTransmitting       = isTransmitting,
                        p2pConnectionState   = p2pConnection,
                        selectedLanguage     = selectedLanguage,
                        transcriptList       = transcriptState,
                        isEmergencyAlert     = isEmergency,
                        onPushToTalkPressed  = {
                            isListeningState.value = true
                            sherpaEngine.startListening()
                        },
                        onPushToTalkReleased = {
                            isListeningState.value = false
                            sherpaEngine.stopListening()
                        },
                        onVoiceChange        = { lang ->
                            selectedLanguageState.value = lang
                            sherpaEngine.switchLanguage(lang)
                        },
                        onEmergencyToggle    = { enabled ->
                            isEmergencyState.value = enabled
                        }
                    )
                }
            }
        }

        // Start background networking & peer discovery
        networkManager.start()
        discoveryManager.start()
        p2pState.value = P2pState.Searching
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stop()
        networkManager.stop()
        sherpaEngine.stopListening()
    }
}
