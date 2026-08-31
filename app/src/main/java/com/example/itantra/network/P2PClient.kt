package com.example.itantra.network

import com.example.itantra.model.NetworkMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Stateful P2P client that maintains a **persistent TCP connection** to a single
 * remote [com.example.itantra.network.P2PServer].
 *
 * Motivation: opening/closing a TCP socket per phrase wastes a full three-way
 * handshake (~1–3 RTTs) on every transmission, drains CPU (TLS-less but still
 * syscall-heavy) and prevents the OS Wi-Fi radio from sleeping between bursts.
 * Keeping one connection alive eliminates this entirely.
 *
 * ## Lifecycle
 * ```kotlin
 * val client = P2PClient()
 * client.connect("192.168.1.42")         // once, when the session starts
 *
 * client.sendMessage(msg)                // as many times as needed
 * client.sendMessage(msg2)
 *
 * client.disconnect()                    // when the session ends
 * ```
 *
 * ## Thread safety
 * [sendMessage] serialises concurrent callers through a [Mutex] so the underlying
 * [BufferedWriter] is never written from two coroutines simultaneously.
 *
 * ## Reconnection
 * If the socket is found to be closed or broken at send time, [sendMessage]
 * attempts a **single silent reconnect** before returning [Result.failure].
 * This handles transient Wi-Fi drops without requiring the caller to call
 * [connect] again.
 *
 * ## Uninitialized-IP guard
 * If [sendMessage] is called before [connect], the client returns
 * [Result.failure] with an [IllegalStateException] rather than attempting
 * a connection to an empty host string (which would throw [java.net.UnknownHostException]).
 */
class P2PClient {

    /** Maximum time (ms) allowed for the TCP three-way handshake. */
    private val connectionTimeoutMs = 5_000

    // Mutable connection state – all access goes through [writeMutex].
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var lastIp: String = ""
    private var lastPort: Int = 8888

    /** Serialises all writes (and reconnect attempts) so the writer is never shared. */
    private val writeMutex = Mutex()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Opens a persistent TCP connection to [targetIp]:[port].
     *
     * Idempotent: if a healthy connection already exists to the same host, this
     * is a no-op. If the target changes or the old socket is dead, it is replaced.
     *
     * @param targetIp Destination IPv4/IPv6 address.
     * @param port     Destination port (default 8888).
     * @return [Result.success] when connected, [Result.failure] on [IOException].
     */
    suspend fun connect(
        targetIp: String,
        port: Int = 8888
    ): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            // Reuse existing healthy connection to the same host.
            if (socket?.isConnected == true && !socket!!.isClosed &&
                lastIp == targetIp && lastPort == port
            ) return@withContext Result.success(Unit)

            closeSilently()
            openConnection(targetIp, port)
        }
    }

    /**
     * Sends [message] over the persistent connection.
     *
     * If the underlying socket is found to be closed, a single silent reconnect
     * is attempted before giving up.  All I/O is confined to [Dispatchers.IO].
     *
     * **Edge-case guard**: if [connect] was never called (i.e. [lastIp] is empty),
     * this method returns [Result.failure] with an [IllegalStateException] instead
     * of attempting a connection to an empty host string.
     *
     * @return [Result.success] on a clean write+flush, [Result.failure] otherwise.
     */
    suspend fun sendMessage(message: NetworkMessage): Result<Unit> =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                // Guard: reject if connect() was never called.
                if (lastIp.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Cannot send message: Target IP is not initialized. Call connect() first."
                        )
                    )
                }

                // Attempt reconnect if the socket is no longer healthy.
                if (socket == null || socket!!.isClosed || !socket!!.isConnected) {
                    val reconnect = openConnection(lastIp, lastPort)
                    if (reconnect.isFailure) return@withContext reconnect
                }

                try {
                    val w = writer ?: return@withContext Result.failure(
                        IOException("Not connected – call connect() first.")
                    )
                    // Wire format: compact JSON + newline delimiter (readLine() on server side).
                    w.write(message.toJson())
                    w.newLine()
                    w.flush()
                    Result.success(Unit)
                } catch (e: IOException) {
                    // Mark connection dead so the next call triggers reconnect.
                    closeSilently()
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    /**
     * Flushes any buffered data and closes the underlying socket.
     * Safe to call from any thread. Safe to call even if not connected.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                writer?.flush()
            } catch (_: Exception) {}
            closeSilently()
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a new [Socket], connects it, and wires up the [BufferedWriter].
     * Must be called **inside** [writeMutex].
     */
    private fun openConnection(targetIp: String, port: Int): Result<Unit> {
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(targetIp, port), connectionTimeoutMs)
            s.tcpNoDelay = true          // disable Nagle – we flush explicitly after each message
            socket = s
            writer = BufferedWriter(
                OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
            )
            lastIp = targetIp
            lastPort = port
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Closes the socket (and therefore all associated streams) without throwing.
     * Must be called **inside** [writeMutex] or from [disconnect].
     */
    private fun closeSilently() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
    }
}
