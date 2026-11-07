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
import java.net.SocketTimeoutException

class SsdpDiscovery(private val context: Context) {
    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        /** Search targets that LG webOS TVs answer to. The first one is webOS-specific. */
        private val SEARCH_TARGETS = listOf(
            "urn:lge-com:service:webos-second-screen:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        )
    }

    suspend fun discoverDevices(timeoutMs: Int = 4000): List<TvDevice> = withContext(Dispatchers.IO) {
        val foundDevices = linkedMapOf<String, TvDevice>()
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("webos_ssdp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName(SSDP_ADDRESS)

                // UDP is lossy: send every query twice.
                repeat(2) {
                    for (st in SEARCH_TARGETS) {
                        val query = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $st\r\n\r\n"
                        val bytes = query.toByteArray()
                        socket.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT))
                    }
                }

                val buffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val response = String(packet.data, 0, packet.length)
                        val host = packet.address.hostAddress ?: continue
                        if (isLgWebOs(response) && host !in foundDevices) {
                            foundDevices[host] = TvDevice(
                                ip = host,
                                port = 3001, // WebOsClient falls back to 3000 automatically
                                name = "LG webOS TV ($host)",
                                uuid = parseUuid(response)
                            )
                        }
                    } catch (_: SocketTimeoutException) {
                        // keep listening until the overall timeout – TVs may answer late (MX: 2)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP discovery error: ${e.message}")
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock.release()
            } catch (_: Exception) {}
        }

        // Fallback when multicast is blocked by the router: probe a few common addresses.
        if (foundDevices.isEmpty()) {
            probeCommonAddresses()?.let { foundDevices[it.ip] = it }
        }

        foundDevices.values.toList()
    }

    /**
     * The old filter accepted anything containing "tv" or "lg", which matched routers,
     * Chromecasts, etc. webOS TVs identify themselves with "WebOS" in SERVER or the
     * lge-com second-screen service type.
     */
    private fun isLgWebOs(response: String): Boolean {
        val lower = response.lowercase()
        return lower.contains("webos") || lower.contains("lge-com") || lower.contains("lg electronics")
    }

    /** Extracts the device UDN from the USN header, e.g. `USN: uuid:abcd-1234::urn:...`. */
    internal fun parseUuid(response: String): String? =
        Regex("""(?im)^USN:\s*uuid:([^:\s]+)""").find(response)?.groupValues?.get(1)?.lowercase()

    private fun probeCommonAddresses(): TvDevice? {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifi?.dhcpInfo?.ipAddress ?: return null
            if (ipInt == 0) return null

            val prefix = "${ipInt and 0xff}.${(ipInt shr 8) and 0xff}.${(ipInt shr 16) and 0xff}."
            // Skip .1 (almost always the router).
            val testIps = listOf("${prefix}100", "${prefix}101", "${prefix}105", "${prefix}150", "${prefix}200")
            for (ip in testIps) {
                if (isPortOpen(ip, 3001, 250)) return TvDevice(ip = ip, port = 3001, name = "LG webOS TV ($ip)")
                if (isPortOpen(ip, 3000, 250)) return TvDevice(ip = ip, port = 3000, name = "LG webOS TV ($ip)")
            }
        } catch (_: Exception) {}
        return null
    }

    fun isPortOpen(ip: String, port: Int, timeout: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeout); true }
    } catch (_: Exception) {
        false
    }
}
