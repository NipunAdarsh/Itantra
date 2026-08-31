package com.example.itantra

import android.content.Context
import com.example.app.model.NetworkMessage
import com.example.app.network.P2PClient
import com.example.app.network.P2PServer
import com.example.app.system.AlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Central integration controller that wires the [P2PServer], [P2PClient], and
 * [AlertManager] background engines into the application's UI layer.
 *
 * ## Responsibilities
 * - Owns the lifecycle of both network endpoints ([P2PServer] and [P2PClient]).
 * - Routes inbound [NetworkMessage] objects to the UI thread via [Dispatchers.Main],
 *   and to [AlertManager] when [NetworkMessage.isAlert] is `true`.
 * - Constructs and dispatches outbound [NetworkMessage] payloads, including
 *   emergency SOS frames, using [Dispatchers.IO] so the UI is never blocked.
 *
 * ## Usage
 * ```kotlin
 * // In onCreate():
 * val integrator = PipelineIntegrator(applicationContext)
 * integrator.startReceiver { message -> transcript.add(message.text) }
 *
 * // On PTT release / SOS button:
 * integrator.sendPayload(targetIp = "192.168.1.42", text = "Hello", isAlert = false)
 * integrator.sendPayload(targetIp = "192.168.1.42", text = "EMERGENCY SOS", isAlert = true)
 *
 * // In onDestroy():
 * integrator.shutdown()
 * ```
 *
 * ## Coroutine scope
 * A [SupervisorJob] is used so that a failure in a single child coroutine (e.g.
 * a failed send) never cancels the accept-loop or other in-flight work.
 * [shutdown] cancels the scope entirely, which also stops the server.
 *
 * @param context Application or activity context.  The class retains it only for
 *                [AlertManager] calls; pass [Context.getApplicationContext] to
 *                prevent activity leaks if this object outlives the Activity.
 */
class PipelineIntegrator(private val context: Context) {

    // -------------------------------------------------------------------------
    // Owned engine instances
    // -------------------------------------------------------------------------

    /**
     * The P2P receive endpoint.  Created lazily inside [startReceiver] so that
     * the callback can be provided at the call-site rather than the constructor.
     */
    private lateinit var server: P2PServer

    /**
     * The P2P send endpoint.  Stateful – maintains a persistent TCP connection
     * to the last-used peer so that multiple [sendPayload] calls reuse the same
     * socket without a fresh three-way handshake each time.
     */
    private val client: P2PClient = P2PClient()

    // -------------------------------------------------------------------------
    // Coroutine infrastructure
    // -------------------------------------------------------------------------

    /**
     * Scope shared by all coroutines spawned inside this integrator.
     *
     * - [SupervisorJob]: individual child failures (e.g. a timed-out send) do
     *   not propagate upward and cancel the accept-loop or other siblings.
     * - [Dispatchers.Default]: a neutral starting dispatcher; each launch site
     *   switches to the appropriate dispatcher ([Dispatchers.IO] / [Dispatchers.Main])
     *   for its own work.
     */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts the P2P receive server on the default port (8888) and begins
     * processing inbound [NetworkMessage] objects.
     *
     * For each received message:
     * 1. If [NetworkMessage.isAlert] is `true`, [AlertManager.triggerEmergencyOverride]
     *    is called immediately on the callback thread (fast, non-blocking).
     * 2. The message is then dispatched to [Dispatchers.Main] so that
     *    [onUIUpdate] can safely mutate Compose state or RecyclerView adapters.
     *
     * Calling [startReceiver] while the server is already running is a no-op
     * (the underlying [P2PServer.start] call is idempotent).
     *
     * @param onUIUpdate Callback invoked on the **main thread** with each
     *                   inbound [NetworkMessage].  Update your UI state here.
     */
    fun startReceiver(onUIUpdate: (NetworkMessage) -> Unit) {
        server = P2PServer { message ->
            // Step 1 – emergency side-effect (runs on Dispatchers.Default per P2PServer contract).
            if (message.isAlert) {
                AlertManager.triggerEmergencyOverride(context)
            }

            // Step 2 – hand the message back to the main thread for UI mutation.
            scope.launch(Dispatchers.Main) {
                onUIUpdate(message)
            }
        }
        server.start()
    }

    /**
     * Constructs a [NetworkMessage] from the supplied parameters and sends it
     * to the target peer entirely on [Dispatchers.IO].
     *
     * The [P2PClient] maintains a persistent TCP connection; if the socket is
     * found dead it will attempt a single silent reconnect before reporting
     * failure.  Any send failure is silently logged here – callers that need
     * error feedback can extend this function to return the [Result].
     *
     * @param targetIp  IPv4/IPv6 address of the receiving peer.
     * @param text      Human-readable message body (e.g. STT transcript or "EMERGENCY SOS").
     * @param lang      BCP-47 language tag of [text]; defaults to `"en"`.
     * @param isAlert   `true` causes the receiver to trigger an emergency override.
     */
    fun sendPayload(
        targetIp: String,
        text: String,
        lang: String = "en",
        isAlert: Boolean
    ) {
        // Build the message on the calling thread – cheap, no I/O.
        val message = NetworkMessage(
            msgId     = UUID.randomUUID().toString(),
            text      = text,
            lang      = lang,
            isAlert   = isAlert,
            timestamp = System.currentTimeMillis()
        )

        // All network I/O on Dispatchers.IO; SupervisorJob means a failure here
        // does not cancel the parent scope or the server's accept loop.
        scope.launch(Dispatchers.IO) {
            // connect() is idempotent – reuses existing socket if healthy.
            val connectResult = client.connect(targetIp)
            if (connectResult.isFailure) {
                // TODO: surface to the user via a SnackBar / error callback if needed.
                return@launch
            }
            client.sendMessage(message)
            // Result is intentionally ignored here.  Wire in error handling if the
            // caller needs delivery confirmation (e.g. show a "!" badge in the transcript).
        }
    }

    /**
     * Gracefully stops the receive server and disconnects the send client.
     *
     * After this call the [PipelineIntegrator] instance must not be reused;
     * create a fresh instance if the pipeline needs to be restarted.
     *
     * Call this from [android.app.Activity.onDestroy] (or the equivalent
     * ViewModel `onCleared`) to release the port binding and all coroutines.
     */
    fun shutdown() {
        if (::server.isInitialized) {
            server.stop()
        }
        // Disconnect the persistent client socket gracefully.
        scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                client.disconnect()
            }
        }
        // Cancel the scope last – this cancels the disconnect coroutine only if
        // it hasn't launched yet, which is safe because disconnect() is best-effort.
        scope.cancel()
    }
}
