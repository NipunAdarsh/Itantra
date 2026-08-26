package com.example.itantra

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class NetworkManager(
    private val onTextReceived: (String) -> Unit
) {
    private val TAG = "NetworkManager"
    private val port = 8888
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startServer() {
        if (isServerRunning) return
        isServerRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server started on port $port")
                while (isServerRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isServerRunning) {
                    Log.e(TAG, "Server error", e)
                }
            } finally {
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing server socket", e)
                }
                isServerRunning = false
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val text = reader.readLine()
            if (text != null) {
                Log.d(TAG, "Received: $text")
                onTextReceived(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }

    fun stopServer() {
        isServerRunning = false
        scope.launch {
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server", e)
            }
        }
    }

    fun sendText(ip: String, text: String) {
        scope.launch {
            try {
                val socket = Socket(ip, port)
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(text)
                socket.close()
                Log.d(TAG, "Sent to $ip: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending text to $ip", e)
            }
        }
    }
}
