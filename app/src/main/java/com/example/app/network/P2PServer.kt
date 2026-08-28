package com.example.app.network

import com.example.app.model.NetworkMessage
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * A non-blocking P2P server that listens on [port] for incoming [NetworkMessage] payloads.
 *
 * Uses Kotlin Coroutines (Dispatchers.IO) to avoid blocking the calling thread.
 * Each accepted client connection is handled in its own child coroutine so the accept
 * loop is never stalled by slow reads.
 *
 * Thread-safety notes:
 * - [serverSocket] is written only inside the [serverJob] coroutine and nulled in [stop].
 *   Because [stop] can be called from any thread, access to [serverSocket] is guarded by
 *   [synchronized] on `this`.
 * - [onMessageReceived] is invoked on [Dispatchers.Default] to keep it off the IO pool and
 *   to give callers a predictable non-IO context for lightweight processing.
 *
 * Lifecycle:
 * ```
 * val server = P2PServer(onMessageReceived = { msg -> /* handle */ })
 * server.start()
 * // …
 * server.stop()
 * ```
 */
class P2PServer(
    private val port: Int = 8888,
    private val onMessageReceived: (NetworkMessage) -> Unit
) {
    // SupervisorJob so an individual client-handler failure never kills the accept loop.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    // -------------------------------------------------------------------------
    // Public lifecycle API
    // -------------------------------------------------------------------------

    /**
     * Opens a [ServerSocket] on [port] and starts accepting connections.
     * Idempotent – calling [start] while already running is a no-op.
     */
    fun start() {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            openServerSocket()?.let { ss ->
                acceptLoop(ss)
            }
        }
    }

    /**
     * Stops the server, cancels all in-flight coroutines, and releases the port.
     * Safe to call from any thread, and safe to call multiple times.
     */
    fun stop() {
        serverJob?.cancel()
        serverJob = null

        val ss = synchronized(this) {
            val s = serverSocket
            serverSocket = null
            s
        }
        try {
            ss?.close()
        } catch (_: Exception) {
            // Intentionally swallowed – we are tearing down.
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Creates and configures the [ServerSocket].
     * Returns `null` on failure so [start] has nothing to loop over.
     */
    private fun openServerSocket(): ServerSocket? {
        return try {
            ServerSocket(port).also { ss ->
                ss.reuseAddress = true
                synchronized(this) { serverSocket = ss }
            }
        } catch (e: Exception) {
            // Port already in use or other I/O error – server cannot start.
            null
        }
    }

    /**
     * Continuously accepts client connections until the coroutine is cancelled
     * or the socket is closed externally by [stop].
     */
    private suspend fun acceptLoop(ss: ServerSocket) {
        while (isActive) {
            try {
                val clientSocket: Socket = ss.accept()   // blocks on IO dispatcher
                handleClientConnection(clientSocket)
            } catch (_: SocketException) {
                // ServerSocket closed (stop() was called) — exit gracefully.
                break
            } catch (_: Exception) {
                // Transient accept error; log here if a logger is wired in.
            }
        }
    }

    /**
     * Reads a single newline-delimited JSON payload from [socket], parses it, and
     * dispatches the result to [onMessageReceived] on [Dispatchers.Default].
     *
     * The socket is closed (via `use`) regardless of success or failure.
     */
    private fun handleClientConnection(socket: Socket) {
        scope.launch {
            try {
                socket.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val payload = reader.readLine() ?: return@use
                    val message = NetworkMessage.fromJson(payload)
                    // Dispatch to Default so callers are not on the IO thread pool.
                    withContext(Dispatchers.Default) {
                        onMessageReceived(message)
                    }
                }
            } catch (_: Exception) {
                // Parsing or read error for this specific client; server keeps running.
            }
        }
    }
}
