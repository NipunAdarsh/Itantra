package com.example.itantra

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.integration.PipelineIntegrator
import com.example.app.system.PermissionHandler
import com.example.itantra.ui.theme.ITantraTheme

/**
 * Application entry-point.
 *
 * ## Integration overview
 *
 * | Old wiring                  | New wiring                                   |
 * |-----------------------------|----------------------------------------------|
 * | `NetworkManager.sendText`   | `pipelineIntegrator.sendPayload`             |
 * | `NetworkManager.startServer`| `pipelineIntegrator.startReceiver`           |
 * | `permissionLauncher` (audio)| `permissionLauncher` (all required perms)    |
 * | Inline `[ALERT]` prefix     | `isAlert = true` flag on `NetworkMessage`    |
 *
 * ## Lifecycle contract
 * - [pipelineIntegrator] is created in [onCreate] and destroyed in [onDestroy].
 * - The server listener is started once inside `onCreate` and remains alive for
 *   the full activity lifetime regardless of SENDER/RECEIVER mode switches —
 *   matching the original behaviour where the server was always available to
 *   accept emergency overrides from any peer.
 *
 * ## Existing code preserved verbatim
 * - [SherpaOnnxEngine] initialisation and PTT gesture handler.
 * - Compose UI layout (mode toggle, IP field, PTT button, transcript list).
 * - [onDestroy] teardown pattern.
 */
class MainActivity : ComponentActivity() {

    // -------------------------------------------------------------------------
    // Engines
    // -------------------------------------------------------------------------

    private lateinit var sherpaEngine: SherpaOnnxEngine

    /**
     * Central integration controller that owns [P2PServer], [P2PClient], and
     * routes inbound emergency messages to [AlertManager].
     *
     * Replaces the old [NetworkManager] field.
     */
    private lateinit var pipelineIntegrator: PipelineIntegrator

    // -------------------------------------------------------------------------
    // Compose state
    // -------------------------------------------------------------------------

    /** Prepended-to so the newest entry always appears at the top of the list. */
    private val transcript      = mutableStateListOf<String>()
    private val modeState       = mutableStateOf("SENDER")
    private val targetIpState   = mutableStateOf("")
    private val isEmergencyState = mutableStateOf(false)

    // -------------------------------------------------------------------------
    // Permission launcher
    // -------------------------------------------------------------------------

    /**
     * Requests all permissions returned by [PermissionHandler.getRequiredPermissions]
     * in a single system dialog.
     *
     * The result map contains `permission → granted` entries.  If any critical
     * permission (e.g. [android.Manifest.permission.RECORD_AUDIO]) was denied,
     * you can show a rationale UI here.  For now the pipeline starts regardless
     * so the app remains functional for sending even if microphone is denied.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // results: Map<String, Boolean>  — permission → isGranted
        // Extend here to show a Snackbar/Dialog if any critical perm was denied.
        val allGranted = results.values.all { it }
        if (!allGranted) {
            // Optional: inform the user that certain features will be degraded.
            // e.g. RECORD_AUDIO denied → STT unavailable; location denied → Wi-Fi scan limited.
        }
    }

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Step 1: Permission check ----------------------------------------
        // Request all required runtime permissions upfront using PermissionHandler
        // so the app always has what it needs before starting the pipeline.
        if (!PermissionHandler.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionHandler.getRequiredPermissions())
        }

        // --- Step 2: Initialise PipelineIntegrator ---------------------------
        // Pass applicationContext to prevent the integrator from holding a
        // reference to this Activity beyond its lifecycle.
        pipelineIntegrator = PipelineIntegrator(applicationContext)

        // --- Step 3: Start the receive server --------------------------------
        // The listener stays alive for the full activity lifetime.
        // onUIUpdate is dispatched to Dispatchers.Main by PipelineIntegrator,
        // so mutating Compose snapshot state here is safe.
        pipelineIntegrator.startReceiver { message ->
            // Prepend received message to transcript (newest first).
            transcript.add(0, "Rcvd [${message.lang}]: ${message.text}")

            // If we are in RECEIVER mode, speak the incoming text.
            if (modeState.value == "RECEIVER") {
                sherpaEngine.synthesizeAndPlay(message.text)
            }
        }

        // --- Step 4: Initialise SherpaOnnxEngine (unchanged) ----------------
        sherpaEngine = SherpaOnnxEngine(this) { sttText ->
            runOnUiThread {
                val ip       = targetIpState.value
                val isAlert  = isEmergencyState.value

                // Prepend sent message to transcript.
                val label = if (isAlert) "[SOS] $sttText" else sttText
                transcript.add(0, "Sent: $label")

                // Route through PipelineIntegrator instead of NetworkManager.
                if (modeState.value == "SENDER" && ip.isNotEmpty()) {
                    pipelineIntegrator.sendPayload(
                        targetIp = ip,
                        text     = sttText,
                        isAlert  = isAlert
                    )
                }
            }
        }

        // --- Step 5: Inflate Compose UI (unchanged) --------------------------
        setContent {
            ITantraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    WalkieTalkieScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Gracefully stops the P2PServer, disconnects the P2PClient, and
        // cancels all coroutines owned by PipelineIntegrator.
        pipelineIntegrator.shutdown()
        sherpaEngine.stopListening()
    }

    // -------------------------------------------------------------------------
    // Compose UI  (structure unchanged from Nipun's original)
    // -------------------------------------------------------------------------

    @ExperimentalComposeUiApi
    @Composable
    fun WalkieTalkieScreen() {
        var mode        by modeState
        var targetIp    by targetIpState
        var isEmergency by isEmergencyState

        // NOTE: Server is always running (started in onCreate).
        // The RECEIVER mode only controls whether incoming audio is synthesized.

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Mode toggle ------------------------------------------------
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { mode = "SENDER" },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (mode == "SENDER")
                            MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) { Text("SENDER") }

                Button(
                    onClick = { mode = "RECEIVER" },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (mode == "RECEIVER")
                            MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) { Text("RECEIVER") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- IP input / status hint ------------------------------------
            if (mode == "SENDER") {
                TextField(
                    value         = targetIp,
                    onValueChange = { targetIp = it },
                    label         = { Text("Receiver IP Address") },
                    modifier      = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text     = "Listening for incoming messages…",
                    fontSize = 14.sp,
                    color    = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Emergency toggle + SOS send button ------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EMERGENCY ALERT")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked         = isEmergency,
                    onCheckedChange = { isEmergency = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Emergency SOS Button: sends a pre-canned alert message immediately
            // without requiring PTT, so the user can trigger it one-handed.
            Button(
                onClick = {
                    val ip = targetIp
                    if (ip.isNotEmpty()) {
                        transcript.add(0, "Sent: [SOS] EMERGENCY SOS")
                        pipelineIntegrator.sendPayload(
                            targetIp = ip,
                            text     = "EMERGENCY SOS",
                            isAlert  = true
                        )
                    }
                },
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                enabled = targetIp.isNotEmpty()
            ) {
                Text("🚨  SEND SOS", color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- PTT button (unchanged from Nipun's original) --------------
            Button(
                onClick  = {},
                modifier = Modifier
                    .size(180.dp)
                    .pointerInteropFilter {
                        when (it.action) {
                            MotionEvent.ACTION_DOWN -> {
                                sherpaEngine.startListening()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                sherpaEngine.stopListening()
                                true
                            }
                            else -> false
                        }
                    },
                shape  = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("PTT", fontSize = 24.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ---- Transcript (unchanged from Nipun's original) --------------
            Text("Transcript", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transcript) { line ->
                    Text(
                        text     = line,
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
