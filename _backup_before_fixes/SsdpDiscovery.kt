package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class SsdpDiscovery(private val context: Context) {
    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
    }

    suspend fun discoverDevices(timeoutMs: Int = 4000): List<TvDevice> = withContext(Dispatchers.IO) {
        val foundDevices = mutableMapOf<String, TvDevice>()
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("webos_ssdp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            val ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

            val ssdpQueryAll = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"

            val socket = DatagramSocket()
            socket.soTimeout = 1500
            val group = InetAddress.getByName(SSDP_ADDRESS)

            val sendPacket1 = DatagramPacket(ssdpQuery.toByteArray(), ssdpQuery.length, group, SSDP_PORT)
            val sendPacket2 = DatagramPacket(ssdpQueryAll.toByteArray(), ssdpQueryAll.length, group, SSDP_PORT)

            socket.send(sendPacket1)
            socket.send(sendPacket2)

            val buffer = ByteArray(2048)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    val hostAddress = packet.address.hostAddress

                    if (hostAddress != null) {
                        val lower = response.lowercase()
                        if (lower.contains("lg") || lower.contains("webos") || lower.contains("tv") || lower.contains("3000") || lower.contains("3001")) {
                            // Default discovered webOS TVs to 3001 (WebOsClient will auto-fallback to 3000 if needed)
                            foundDevices[hostAddress] = TvDevice(
                                ip = hostAddress,
                                port = 3001,
                                name = "LG webOS TV ($hostAddress)"
                            )
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "SSDP Discovery error: ${e.message}")
        } finally {
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (_: Exception) {}
        }

        // Fast probe: check if standard gateway or host has open port 3001 or 3000
        probeLocalGateway()?.let {
            foundDevices[it.ip] = it
        }

        foundDevices.values.toList()
    }

    private fun probeLocalGateway(): TvDevice? {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifi?.dhcpInfo ?: return null
            val ipInt = dhcpInfo.ipAddress
            if (ipInt == 0) return null

            val b1 = ipInt and 0xff
            val b2 = (ipInt shr 8) and 0xff
            val b3 = (ipInt shr 16) and 0xff
            val prefix = "$b1.$b2.$b3."

            val testIps = listOf("${prefix}1", "${prefix}100", "${prefix}105", "${prefix}150", "${prefix}200")
            for (ip in testIps) {
                if (isPortOpen(ip, 3001, 200)) {
                    return TvDevice(ip = ip, port = 3001, name = "LG webOS TV ($ip)")
                } else if (isPortOpen(ip, 3000, 200)) {
                    return TvDevice(ip = ip, port = 3000, name = "LG webOS TV ($ip)")
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isPortOpen(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
