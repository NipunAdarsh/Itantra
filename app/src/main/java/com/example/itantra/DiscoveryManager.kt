package com.example.itantra

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class DiscoveryManager(
    private val context: Context,
    private val onPeerDiscovered: (String) -> Unit
) {
    private val DISCOVERY_PORT = 9999
    private val BROADCAST_MESSAGE = "ITANTRA_DEVICE"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("iTantraMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e("Discovery", "Failed to acquire multicast lock", e)
        }
        startBroadcasting()
        startListening()
    }

    fun stop() {
        isRunning = false
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.e("Discovery", "Failed to release multicast lock", e)
        }
        scope.cancel()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Discovery", "getLocalIpAddress error", e)
        }
        return "127.0.0.1"
    }

    private fun getBroadcastAddress(): InetAddress {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
                val quads = ByteArray(4)
                for (k in 0..3) quads[k] = (broadcast shr k * 8 and 0xff).toByte()
                return InetAddress.getByAddress(quads)
            }
        } catch (e: Exception) {
            Log.e("Discovery", "getBroadcastAddress error", e)
        }
        return InetAddress.getByName("255.255.255.255")
    }

    private fun startBroadcasting() {
        scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                while (isRunning) {
                    try {
                        val myIp = getLocalIpAddress()
                        if (myIp != "127.0.0.1" && myIp.isNotBlank()) {
                            val message = "$BROADCAST_MESSAGE:$myIp"
                            val data = message.toByteArray()
                            val packet = DatagramPacket(
                                data, data.size,
                                getBroadcastAddress(),
                                DISCOVERY_PORT
                            )
                            socket.send(packet)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("Discovery", "Broadcast error", e)
                        }
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.e("Discovery", "Broadcast socket error", e)
            } finally {
                socket?.close()
            }
        }
    }

    private fun startListening() {
        scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
                val buffer = ByteArray(256)
                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        if (message.startsWith(BROADCAST_MESSAGE)) {
                            val parts = message.split(":")
                            if (parts.size >= 2) {
                                val peerIp = parts[1]
                                val myIp = getLocalIpAddress()
                                if (peerIp.isNotBlank() && peerIp != myIp && peerIp != "127.0.0.1") {
                                    onPeerDiscovered(peerIp)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("Discovery", "Listen error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Discovery", "Listen socket error", e)
            } finally {
                socket?.close()
            }
        }
    }
}
