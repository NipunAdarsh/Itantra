package com.example.itantra.integration

import android.content.Context
import android.util.Log
import com.example.itantra.model.NetworkMessage
import com.example.itantra.network.P2PClient
import com.example.itantra.network.P2PServer
import com.example.itantra.system.AlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Integration layer that bridges the networking stack ([P2PServer], [P2PClient]),
 * the emergency alert system ([AlertManager]), and the UI layer.
 *
 * ## Responsibilities
 * - Manages the lifecycle of [P2PServer] and [P2PClient].
 * - On receiving a [NetworkMessage] where [NetworkMessage.isAlert] is `true`,
 *   triggers [AlertManager.triggerEmergencyOverride] and then dispatches the
 *   message to the UI thread.
 * - Provides a high-level [sendTextMessage] function that constructs a
 *   [NetworkMessage], manages the [P2PClient] connection state, and sends.
 * - Offers a [cleanup] method for deterministic teardown during application
 *   lifecycle events (e.g. `Activity.onDestroy()`).
 *
 * ## Thread safety
 * All network I/O is delegated to [P2PClient] and [P2PServer], which handle
 * their own synchronisation internally. The [scope] uses a [SupervisorJob]
 * so that a failure in one child coroutine (e.g. a single failed send) does not
 * cancel sibling operations.
 */
class NetworkManager(
    private val context: Context,
    private val onMessageReceived: (NetworkMessage) -> Unit
) {
    private val TAG = "NetworkManager"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = P2PClient()

    private val server = P2PServer(
        port = 8888,
        onMessageReceived = { message ->
            // This callback is invoked on Dispatchers.Default by P2PServer.
            scope.launch {
                handleIncomingMessage(message)
            }
        }
    )

    // -------------------------------------------------------------------------
    // Server lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the P2P server to listen for incoming messages.
     * Idempotent – safe to call multiple times.
     */
    fun startServer() {
        server.start()
        Log.d(TAG, "P2P server started on port 8888")
    }

    /**
     * Stops the P2P server and releases the listening port.
     * Safe to call even if the server is not running.
     */
    fun stopServer() {
        server.stop()
        Log.d(TAG, "P2P server stopped")
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    /**
     * Constructs a [NetworkMessage] and sends it to [targetIp] via [P2PClient].
     *
     * If the client is not already connected to [targetIp], a connection is
     * established automatically. Subsequent calls to the same [targetIp] reuse
     * the persistent connection.
     *
     * @param targetIp  Destination IPv4/IPv6 address.
     * @param text      The message body.
     * @param isAlert   If `true`, the receiver will trigger an emergency override.
     * @param lang      Language code (default `"en"`).
     */
    fun sendTextMessage(
        targetIp: String,
        text: String,
        isAlert: Boolean,
        lang: String = "en"
    ) {
        scope.launch {
            try {
                // Ensure connected (idempotent if already connected to the same host).
                val connectResult = client.connect(targetIp)
                if (connectResult.isFailure) {
                    Log.e(TAG, "Failed to connect to $targetIp", connectResult.exceptionOrNull())
                    return@launch
                }

                val message = NetworkMessage(
                    msgId = UUID.randomUUID().toString(),
                    text = text,
                    lang = lang,
                    isAlert = isAlert,
                    timestamp = System.currentTimeMillis()
                )

                val sendResult = client.sendMessage(message)
                if (sendResult.isFailure) {
                    Log.e(TAG, "Failed to send message to $targetIp", sendResult.exceptionOrNull())
                } else {
                    Log.d(TAG, "Sent to $targetIp: ${message.text}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendTextMessage", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Incoming message handling
    // -------------------------------------------------------------------------

    /**
     * Processes an incoming [NetworkMessage]:
     * 1. If [NetworkMessage.isAlert] is `true`, triggers [AlertManager.triggerEmergencyOverride].
     * 2. Dispatches the message to the UI thread via [onMessageReceived].
     */
    private suspend fun handleIncomingMessage(message: NetworkMessage) {
        if (message.isAlert) {
            AlertManager.triggerEmergencyOverride(context)
        }

        // Dispatch to the UI thread so the callback can safely update Compose state.
        withContext(Dispatchers.Main) {
            onMessageReceived(message)
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Performs a complete teardown: disconnects the client, stops the server,
     * and cancels any in-flight coroutines.
     *
     * Call this from `Activity.onDestroy()` or equivalent lifecycle hook.
     */
    fun cleanup() {
        scope.launch {
            try {
                client.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting client", e)
            }
        }
        server.stop()
        Log.d(TAG, "NetworkManager cleaned up")
    }
}
