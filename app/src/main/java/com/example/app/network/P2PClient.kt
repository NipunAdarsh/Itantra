package com.example.app.network

import com.example.app.model.NetworkMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Stateless P2P client for sending a [NetworkMessage] to a remote [P2PServer].
 *
 * Each call to [sendMessage] opens a fresh TCP connection, writes the serialized
 * JSON line, flushes, and tears the connection down cleanly.  All I/O is run on
 * [Dispatchers.IO] so the function is safe to call from any coroutine context.
 *
 * Usage (inside a coroutine or suspend fun):
 * ```kotlin
 * val result = P2PClient.sendMessage(
 *     targetIp = "192.168.1.42",
 *     message  = myNetworkMessage
 * )
 * result.onFailure { e -> Log.e("P2PClient", "Send failed", e) }
 * ```
 */
object P2PClient {

    /** Maximum time (ms) to wait while establishing the TCP connection. */
    private const val CONNECTION_TIMEOUT_MS = 5_000

    /**
     * Serializes [message] to JSON and transmits it to [targetIp]:[port].
     *
     * @param targetIp IPv4 (or IPv6) address of the target [P2PServer].
     * @param port     Destination port; defaults to 8888.
     * @param message  The [NetworkMessage] to serialize and send.
     * @return [Result.success] on a clean write+flush, or [Result.failure]
     *         wrapping the [IOException] (or any other exception) on error.
     */
    suspend fun sendMessage(
        targetIp: String,
        port: Int = 8888,
        message: NetworkMessage
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // socket.use {} guarantees close() even if an exception is thrown.
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetIp, port), CONNECTION_TIMEOUT_MS)

                // Wrap the output stream in a BufferedWriter for efficient writes.
                val writer = BufferedWriter(
                    OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                )
                // The server uses readLine(), so the message MUST end with '\n'.
                writer.write(message.toJson())
                writer.newLine()
                writer.flush()
                // writer does NOT own the socket's stream lifecycle; closing the
                // socket (via use {}) is sufficient to release OS resources.
            }
            Result.success(Unit)
        } catch (e: IOException) {
            // Network-level failure (host unreachable, connection refused, timeout…)
            Result.failure(e)
        } catch (e: Exception) {
            // JSON serialisation error or other unexpected failure.
            Result.failure(e)
        }
    }
}
