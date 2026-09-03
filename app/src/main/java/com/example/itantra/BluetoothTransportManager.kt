package com.example.itantra

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/** A discovered or paired peer, as shown to the UI device picker. */
data class BtPeer(
    val name: String,
    val address: String,
    val isPaired: Boolean
)

/**
 * Bluetooth Classic (RFCOMM/SPP) transport — the offline fallback for locations
 * with no Wi-Fi AP or Wi-Fi Direct support. Mirrors NetworkManager's text-line
 * wire protocol so MainActivity can route through either transport interchangeably.
 *
 * Unlike the WiFi transport (which opens a fresh TCP connection per message),
 * RFCOMM keeps one persistent duplex socket open after connect — both sides
 * read and write on it directly.
 */
class BluetoothTransportManager(
    private val context: Context,
    private val onTextReceived: (String) -> Unit,
    private val onConnectionStateChanged: (connected: Boolean, peerLabel: String?) -> Unit,
    private val onDeviceDiscovered: (BtPeer) -> Unit,
    private val onDiscoveryFinished: () -> Unit = {}
) {
    companion object {
        private const val TAG = "BluetoothTransport"
        // Fixed app-specific SPP UUID — both ends must use the same value.
        private val ITANTRA_UUID: UUID = UUID.fromString("8ce255c0-223c-11eb-adc1-0242ac120002")
        private const val SERVICE_NAME = "iTantraSPP"
    }

    private val adapter: BluetoothAdapter? by lazy {
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var readJob: Job? = null

    @Volatile private var activeSocket: BluetoothSocket? = null
    @Volatile private var isRunning = false
    private var connectedPeerLabel: String? = null

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    val name = try { device.name } catch (e: SecurityException) { null }
                    onDeviceDiscovered(
                        BtPeer(
                            name      = name ?: "Unknown device",
                            address   = device.address,
                            isPaired  = device.bondState == BluetoothDevice.BOND_BONDED
                        )
                    )
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onDiscoveryFinished()
            }
        }
    }
    private var receiverRegistered = false

    fun isAvailable(): Boolean = adapter != null

    fun isEnabled(): Boolean = try { adapter?.isEnabled == true } catch (e: SecurityException) { false }

    /** Starts the RFCOMM accept loop so a peer can connect to us. Safe to call once permissions are granted. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        val bt = adapter ?: return
        val enabled = try { bt.isEnabled } catch (e: SecurityException) {
            Log.w(TAG, "start() denied — BLUETOOTH_CONNECT not granted yet")
            return
        }
        if (!enabled) return
        isRunning = true

        if (!receiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.applicationContext.registerReceiver(discoveryReceiver, filter)
                receiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "registerReceiver failed", e)
            }
        }

        acceptJob = scope.launch {
            while (isRunning) {
                try {
                    val serverSocket = bt.listenUsingRfcommWithServiceRecord(SERVICE_NAME, ITANTRA_UUID)
                    Log.d(TAG, "RFCOMM server listening")
                    val socket = serverSocket.accept() // blocks until a peer connects
                    serverSocket.close()
                    adoptConnectedSocket(socket, peerLabel = socket.remoteDevice?.name ?: socket.remoteDevice?.address)
                } catch (e: IOException) {
                    if (isRunning) Log.e(TAG, "Accept loop error", e)
                    delay(1000)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Accept loop permission denied", e)
                    isRunning = false
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            if (receiverRegistered) {
                context.applicationContext.unregisterReceiver(discoveryReceiver)
                receiverRegistered = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "unregisterReceiver failed", e)
        }
        stopDiscovery()
        acceptJob?.cancel()
        connectJob?.cancel()
        readJob?.cancel()
        closeActiveSocket()
        scope.coroutineContext.cancelChildren()
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BtPeer> {
        val bt = adapter ?: return emptyList()
        return try {
            bt.bondedDevices.map { BtPeer(it.name ?: it.address, it.address, isPaired = true) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val bt = adapter ?: return
        try {
            if (bt.isDiscovering) bt.cancelDiscovery()
            bt.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "startDiscovery denied", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            adapter?.let { if (it.isDiscovering) it.cancelDiscovery() }
        } catch (e: SecurityException) {
            // ignore — permission already gone (e.g. during teardown)
        }
    }

    /** Initiates an outbound RFCOMM connection to [address] (paired or freshly discovered). */
    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        val bt = adapter ?: return
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                stopDiscovery()
                val device: BluetoothDevice = bt.getRemoteDevice(address)
                val socket = device.createRfcommSocketToServiceRecord(ITANTRA_UUID)
                socket.connect() // blocking
                adoptConnectedSocket(socket, peerLabel = device.name ?: device.address)
            } catch (e: IOException) {
                Log.e(TAG, "connectToDevice failed for $address", e)
                onConnectionStateChanged(false, null)
            } catch (e: SecurityException) {
                Log.e(TAG, "connectToDevice permission denied", e)
                onConnectionStateChanged(false, null)
            }
        }
    }

    fun sendText(text: String, onComplete: ((Boolean) -> Unit)? = null) {
        val socket = activeSocket
        if (socket == null) {
            onComplete?.invoke(false)
            return
        }
        scope.launch {
            try {
                val out: OutputStream = socket.outputStream
                out.write((text + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                onComplete?.invoke(true)
            } catch (e: IOException) {
                Log.e(TAG, "sendText failed", e)
                closeActiveSocket()
                onConnectionStateChanged(false, null)
                onComplete?.invoke(false)
            }
        }
    }

    fun disconnect() {
        closeActiveSocket()
        onConnectionStateChanged(false, null)
    }

    private fun adoptConnectedSocket(socket: BluetoothSocket, peerLabel: String?) {
        closeActiveSocket()
        activeSocket = socket
        connectedPeerLabel = peerLabel
        onConnectionStateChanged(true, peerLabel)

        readJob?.cancel()
        readJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                while (isRunning && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) onTextReceived(line)
                }
            } catch (e: IOException) {
                Log.d(TAG, "Read loop ended: ${e.message}")
            } finally {
                if (activeSocket === socket) {
                    closeActiveSocket()
                    onConnectionStateChanged(false, null)
                }
            }
        }
    }

    private fun closeActiveSocket() {
        try {
            activeSocket?.close()
        } catch (e: IOException) {
            // ignore
        }
        activeSocket = null
        connectedPeerLabel = null
    }
}
