package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.TvDevice
import org.json.JSONArray
import org.json.JSONObject

class TvPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("webos_remote_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAVED_DEVICES = "saved_devices"
        private const val KEY_LAST_CONNECTED_IP = "last_connected_ip"
        private const val KEY_RECENT_URLS = "recent_urls"
        private const val KEY_HAPTIC = "haptic_enabled"
    }

    fun getSavedDevices(): List<TvDevice> {
        val jsonStr = prefs.getString(KEY_SAVED_DEVICES, null) ?: return emptyList()
        val list = mutableListOf<TvDevice>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TvDevice(
                        ip = obj.getString("ip"),
                        port = obj.optInt("port", 3001),
                        name = obj.optString("name", "LG webOS TV"),
                        clientKey = if (obj.has("clientKey") && !obj.isNull("clientKey")) obj.getString("clientKey") else null,
                        mac = if (obj.has("mac") && !obj.isNull("mac")) obj.getString("mac") else null,
                        uuid = if (obj.has("uuid") && !obj.isNull("uuid")) obj.getString("uuid") else null,
                        isLastConnected = obj.optBoolean("isLastConnected", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveDevice(device: TvDevice) {
        val current = getSavedDevices().toMutableList()
        val index = current.indexOfFirst { it.ip == device.ip }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        persistDevices(current)
    }

    fun saveClientKey(ip: String, clientKey: String) {
        val current = getSavedDevices().toMutableList()
        val index = current.indexOfFirst { it.ip == ip }
        if (index >= 0) {
            current[index] = current[index].copy(clientKey = clientKey)
            persistDevices(current)
        } else {
            val newDevice = TvDevice(ip = ip, clientKey = clientKey)
            current.add(newDevice)
            persistDevices(current)
        }
    }

    fun removeDevice(ip: String) {
        val current = getSavedDevices().filterNot { it.ip == ip }
        persistDevices(current)
    }

    private fun persistDevices(devices: List<TvDevice>) {
        val jsonArray = JSONArray()
        for (d in devices) {
            val obj = JSONObject()
            obj.put("ip", d.ip)
            obj.put("port", d.port)
            obj.put("name", d.name)
            obj.put("clientKey", d.clientKey)
            obj.put("mac", d.mac)
            obj.put("uuid", d.uuid)
            obj.put("isLastConnected", d.isLastConnected)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_DEVICES, jsonArray.toString()).apply()
    }

    fun getLastConnectedIp(): String? = prefs.getString(KEY_LAST_CONNECTED_IP, null)

    fun setLastConnectedIp(ip: String) {
        prefs.edit().putString(KEY_LAST_CONNECTED_IP, ip).apply()
    }

    fun getRecentUrls(): List<String> {
        val jsonStr = prefs.getString(KEY_RECENT_URLS, null) ?: return listOf(
            "https://www.google.com",
            "https://www.youtube.com",
            "https://www.reddit.com",
            "https://www.wikipedia.org"
        )
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun addRecentUrl(url: String) {
        val current = getRecentUrls().toMutableList()
        current.remove(url)
        current.add(0, url)
        if (current.size > 15) {
            current.removeAt(current.size - 1)
        }
        val arr = JSONArray(current)
        prefs.edit().putString(KEY_RECENT_URLS, arr.toString()).apply()
    }

    fun isHapticEnabled(): Boolean = prefs.getBoolean(KEY_HAPTIC, true)

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }
}
