package com.example.itantra

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import com.example.itantra.ui.BluetoothPeerUi
import com.example.itantra.ui.P2pState
import com.example.itantra.ui.TransportType
import com.example.itantra.ui.TranscriptMessage
import com.example.itantra.ui.WalkieTalkieScreen
import com.example.itantra.ui.theme.ITantraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {

    // ── Engine & Networking ───────────────────────────────────────────────────
    private lateinit var sherpaEngine     : SherpaOnnxEngine
    private lateinit var ttsManager       : TtsManager
    private lateinit var networkManager   : NetworkManager
    private lateinit var discoveryManager : DiscoveryManager
    private lateinit var bluetoothManager : BluetoothTransportManager

    // ── UI State ─────────────────────────────────────────────────────────────
    private val transcriptState        = mutableStateListOf<TranscriptMessage>()
    private val isListeningState       = mutableStateOf(false)
    private val isTransmittingState    = mutableStateOf(false)
    private val isEmergencyState       = mutableStateOf(false)
    private val p2pState               = mutableStateOf<P2pState>(P2pState.Searching)
    private val selectedLanguageState  = mutableStateOf(AppLanguage.ENGLISH)
    private val operationalModeState   = mutableStateOf(OperationalMode.WALKIE_TALKIE)
    private val isVadSpeakingState     = mutableStateOf(false)
    private val transportTypeState     = mutableStateOf(TransportType.WIFI)
    private val bluetoothPeerLabelState = mutableStateOf<String?>(null)
    private val pairedBtDevicesState    = mutableStateListOf<BluetoothPeerUi>()
    private val discoveredBtDevicesState = mutableStateListOf<BluetoothPeerUi>()
    private val isScanningBtState        = mutableStateOf(false)

    private var messageIdCounter = 0L
    private fun nextId() = ++messageIdCounter

    private fun currentTimestamp(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    /** Shared inbound-message handling for both the Wi-Fi and Bluetooth transports. */
    private fun handleIncomingText(rawReceivedText: String) {
        var cleanText = rawReceivedText
        var targetLang = selectedLanguageState.value

        // 1. Extract [LANG:xx] protocol header if present
        val langMatch = Regex("^\\[LANG:([a-z]{2})\\]").find(cleanText)
        if (langMatch != null) {
            val code = langMatch.groupValues[1]
            AppLanguage.values().find { it.code == code }?.let {
                targetLang = it
            }
            cleanText = cleanText.removeRange(langMatch.range)
        } else {
            targetLang = SherpaOnnxEngine.detectScript(cleanText, selectedLanguageState.value)
        }

        val isEmergency = cleanText.startsWith("[ALERT]")

        transcriptState.add(
            0,
            TranscriptMessage(
                id          = nextId(),
                text        = cleanText,
                direction   = TranscriptMessage.Direction.RECEIVED,
                isEmergency = isEmergency,
                timestamp   = currentTimestamp()
            )
        )
        // Play received audio on peer device when not holding mic in PTT mode
        if (!isListeningState.value) {
            ttsManager.speak(cleanText, targetLang)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Runtime Permissions Request (Audio + WiFi/Location for Discovery) ─
        val permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* mic & discovery start gracefully */ }
        val runtimePermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runtimePermissions += Manifest.permission.BLUETOOTH_CONNECT
            runtimePermissions += Manifest.permission.BLUETOOTH_SCAN
        }
        permissionsLauncher.launch(runtimePermissions.toTypedArray())

        // ── Hybrid TTS Manager (Piper for EN/HI + Android Native for 8 Indic) ─
        ttsManager = TtsManager(
            context = this,
            onVoiceUnavailable = { lang ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Offline voice for ${lang.label} missing. Spoken via fallback.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            speakPiper = { text, lang, isAlert ->
                sherpaEngine.synthesizeAndPlayPiper(text, lang, isAlert)
            }
        )

        // ── Full Duplex Network Manager (Wi-Fi transport) ────────────────────
        networkManager = NetworkManager { rawReceivedText ->
            runOnUiThread { handleIncomingText(rawReceivedText) }
        }

        // ── UDP Broadcast Auto-Discovery Manager ──────────────────────────────
        discoveryManager = DiscoveryManager(this) { peerIp ->
            runOnUiThread {
                networkManager.setPeerIp(peerIp)
                if (transportTypeState.value == TransportType.WIFI) {
                    p2pState.value = P2pState.Connected(peerIp)
                }
            }
        }

        // ── Bluetooth Classic (RFCOMM) Transport — offline fallback link ──────
        bluetoothManager = BluetoothTransportManager(
            context = this,
            onTextReceived = { rawReceivedText ->
                runOnUiThread { handleIncomingText(rawReceivedText) }
            },
            onConnectionStateChanged = { connected, peerLabel ->
                runOnUiThread {
                    bluetoothPeerLabelState.value = if (connected) peerLabel else null
                    if (transportTypeState.value == TransportType.BLUETOOTH) {
                        p2pState.value = if (connected)
                            P2pState.Connected(peerLabel ?: "peer")
                        else
                            P2pState.Disconnected
                    }
                }
            },
            onDeviceDiscovered = { peer ->
                runOnUiThread {
                    val ui = BluetoothPeerUi(peer.name, peer.address, peer.isPaired)
                    if (discoveredBtDevicesState.none { it.address == ui.address }) {
                        discoveredBtDevicesState.add(ui)
                    }
                }
            },
            onDiscoveryFinished = {
                runOnUiThread { isScanningBtState.value = false }
            }
        )

        // ── Neural Voice Engine (STT -> Network Send -> TTS) ─────────────────
        sherpaEngine = SherpaOnnxEngine(
            context = this,
            onTextReady = { sttText ->
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
                        val networkPayload = "[LANG:${selectedLanguageState.value.code}]$finalMessage"
                        when (transportTypeState.value) {
                            TransportType.WIFI      -> networkManager.sendText(networkPayload)
                            TransportType.BLUETOOTH -> bluetoothManager.sendText(networkPayload)
                        }
                        delay(500) // Visual feedback duration
                        isTransmittingState.value = false
                    }
                }
            },
            onVadSpeechStateChanged = { isSpeaking ->
                runOnUiThread {
                    isVadSpeakingState.value = isSpeaking
                }
            }
        )

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
                    val currentMode      by operationalModeState
                    val isVadSpeaking    by isVadSpeakingState
                    val currentTransport by transportTypeState
                    val btPeerLabel      by bluetoothPeerLabelState
                    val isScanningBt     by isScanningBtState

                    WalkieTalkieScreen(
                        isListening             = isListening,
                        isTransmitting          = isTransmitting,
                        p2pConnectionState      = p2pConnection,
                        selectedLanguage        = selectedLanguage,
                        transcriptList          = transcriptState,
                        isEmergencyAlert        = isEmergency,
                        operationalMode         = currentMode,
                        isVadSpeaking           = isVadSpeaking,
                        transportType           = currentTransport,
                        bluetoothPeerLabel      = btPeerLabel,
                        pairedBluetoothDevices  = pairedBtDevicesState,
                        discoveredBluetoothDevices = discoveredBtDevicesState,
                        isScanningBluetooth     = isScanningBt,
                        onTransportChange       = { newTransport ->
                            transportTypeState.value = newTransport
                            when (newTransport) {
                                TransportType.WIFI -> {
                                    p2pState.value = networkManager.getPeerIp()
                                        ?.let { P2pState.Connected(it) }
                                        ?: P2pState.Searching
                                }
                                TransportType.BLUETOOTH -> {
                                    bluetoothManager.start()
                                    p2pState.value = bluetoothPeerLabelState.value
                                        ?.let { P2pState.Connected(it) }
                                        ?: P2pState.Disconnected
                                }
                            }
                        },
                        onScanBluetoothDevices  = {
                            discoveredBtDevicesState.clear()
                            pairedBtDevicesState.clear()
                            pairedBtDevicesState.addAll(
                                bluetoothManager.pairedDevices()
                                    .map { BluetoothPeerUi(it.name, it.address, it.isPaired) }
                            )
                            isScanningBtState.value = true
                            bluetoothManager.startDiscovery()
                        },
                        onBluetoothDeviceSelected = { device ->
                            bluetoothManager.stopDiscovery()
                            isScanningBtState.value = false
                            bluetoothManager.connectToDevice(device.address)
                        },
                        onPushToTalkPressed     = {
                            isListeningState.value = true
                            sherpaEngine.startListening()
                        },
                        onPushToTalkReleased    = {
                            isListeningState.value = false
                            sherpaEngine.stopListening()
                        },
                        onVoiceChange           = { lang ->
                            selectedLanguageState.value = lang
                            sherpaEngine.switchLanguage(lang)
                        },
                        onEmergencyToggle       = { enabled ->
                            isEmergencyState.value = enabled
                        },
                        onOperationalModeChange = { newMode ->
                            operationalModeState.value = newMode
                            sherpaEngine.setOperationalMode(newMode)
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
        bluetoothManager.stop()
        ttsManager.release()
        sherpaEngine.release()
    }
}
