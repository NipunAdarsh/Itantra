package com.example.itantra

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*

class NetworkManager(
    private val onTextReceived: (String) -> Unit
) {
    private val TCP_PORT = 8888
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var peerIp: String? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        startServer()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("NetworkManager", "Close server socket error", e)
        }
        scope.cancel()
    }

    fun setPeerIp(ip: String) {
        peerIp = ip
        Log.d("NetworkManager", "Peer discovered: $ip")
    }

    fun getPeerIp(): String? = peerIp

    fun sendText(text: String, onComplete: ((Boolean) -> Unit)? = null) {
        val targetIp = peerIp
        if (targetIp == null) {
            Log.w("NetworkManager", "sendText failed: no peer IP set")
            onComplete?.invoke(false)
            return
        }
        scope.launch {
            var retries = 3
            while (retries > 0) {
                try {
                    val socket = Socket()
                    socket.connect(
                        InetSocketAddress(targetIp, TCP_PORT), 3000
                    )
                    val writer = PrintWriter(
                        OutputStreamWriter(socket.getOutputStream()),
                        true
                    )
                    writer.println(text)
                    socket.close()
                    Log.d("NetworkManager", "Sent text successfully to $targetIp: $text")
                    onComplete?.invoke(true)
                    return@launch
                } catch (e: Exception) {
                    retries--
                    Log.e("NetworkManager", "Send failed, retries left: $retries", e)
                    if (retries > 0) delay(500)
                }
            }
            onComplete?.invoke(false)
        }
    }

    fun sendText(targetIp: String, text: String, onComplete: ((Boolean) -> Unit)? = null) {
        setPeerIp(targetIp)
        sendText(text, onComplete)
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                Log.d("NetworkManager", "Server listening on $TCP_PORT")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        scope.launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("NetworkManager", "Accept error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkManager", "Server error", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(
                InputStreamReader(socket.getInputStream())
            )
            val text = reader.readLine()
            if (!text.isNullOrBlank()) {
                Log.d("NetworkManager", "Received text: $text")
                onTextReceived(text)
            }
            socket.close()
        } catch (e: Exception) {
            Log.e("NetworkManager", "Client handler error", e)
        }
    }
}
