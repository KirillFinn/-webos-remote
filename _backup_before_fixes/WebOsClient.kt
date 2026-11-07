package com.example.network

import android.util.Log
import com.example.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

interface WebOsEventListener {
    fun onConnecting(statusText: String)
    fun onPromptingUserOnTv()
    fun onConnected(device: TvDevice, clientKey: String)
    fun onConnectionFailed(error: String)
    fun onDisconnected()
    fun onVolumeChanged(volume: Int, isMuted: Boolean)
    fun onToastReceived(message: String)
}

data class DiagnosticResult(
    val ip: String,
    val port3001Open: Boolean,
    val port3000Open: Boolean,
    val tlsHandshakeSuccess: Boolean,
    val recommendation: String
)

class WebOsClient(
    private val scope: CoroutineScope,
    private val listener: WebOsEventListener
) {
    companion object {
        private const val TAG = "WebOsClient"
    }

    private val okHttpClient = createUnsafeOkHttpClient()

    private var mainWebSocket: WebSocket? = null
    private var pointerWebSocket: WebSocket? = null
    private val reqIdCounter = AtomicInteger(100)

    private var currentDevice: TvDevice? = null
    private var isPairingPromptShown = false

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                .allEnabledCipherSuites()
                .build()

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom SSL client, falling back to default", e)
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    suspend fun runDiagnostics(ip: String): DiagnosticResult = withContext(Dispatchers.IO) {
        var port3001 = false
        var port3000 = false
        var tlsOk = false

        // Test Port 3001
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, 3001), 1500)
                port3001 = true
            }
        } catch (_: Exception) {}

        // Test Port 3000
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, 3000), 1500)
                port3000 = true
            }
        } catch (_: Exception) {}

        // Test TLS on 3001 if port open
        if (port3001) {
            try {
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAll, SecureRandom())
                val sslSocket = sslContext.socketFactory.createSocket(ip, 3001) as SSLSocket
                sslSocket.soTimeout = 2000
                sslSocket.startHandshake()
                tlsOk = true
                sslSocket.close()
            } catch (_: Exception) {}
        }

        val recommendation = when {
            port3001 && tlsOk -> "Port 3001 (Secure WSS) is active. Ready to pair."
            port3001 && !tlsOk -> "Port 3001 is open, but TLS handshake failed. TV may be rejecting certificate."
            port3000 -> "Port 3000 (Standard WS) is open. Connecting via standard mode."
            else -> "Neither port 3001 nor 3000 responded at $ip. Make sure the TV is powered ON and 'LG Connect Apps' or 'TV On With Mobile' is turned ON in TV Settings."
        }

        DiagnosticResult(ip, port3001, port3000, tlsOk, recommendation)
    }

    fun connect(device: TvDevice, forceFreshPairing: Boolean = false) {
        disconnect()
        val targetDevice = if (forceFreshPairing) device.copy(clientKey = null) else device
        currentDevice = targetDevice
        isPairingPromptShown = false

        // Determine initial attempt port:
        // By default, try 3001 (secure) first, unless port 3000 was explicitly chosen.
        val trySecureFirst = (targetDevice.port != 3000)
        attemptConnection(targetDevice, useSecure = trySecureFirst, isFallback = false)
    }

    private fun attemptConnection(device: TvDevice, useSecure: Boolean, isFallback: Boolean) {
        val scheme = if (useSecure) "wss" else "ws"
        val port = if (useSecure) 3001 else 3000
        val wsUrl = "$scheme://${device.ip}:$port"
        Log.d(TAG, "Attempting connection to LG TV at $wsUrl (fallback=$isFallback)")

        val portLabel = if (useSecure) "Port 3001 (WSS)" else "Port 3000 (WS)"
        listener.onConnecting("Connecting to ${device.ip} via $portLabel...")

        // Notice: No custom Origin header here! LG webOS rejects arbitrary Origins with Connection Reset
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        mainWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Main WebSocket connected successfully on $wsUrl")
                currentDevice = device.copy(port = port)
                sendRegisterHandshake(device.clientKey)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure on $wsUrl: ${t.message}")
                disconnect()

                if (!isFallback) {
                    // Try the alternative port (3001 <-> 3000) with a fresh handshake
                    Log.d(TAG, "Retrying on fallback port ${if (useSecure) "3000 (ws)" else "3001 (wss)"}...")
                    scope.launch(Dispatchers.IO) {
                        delay(300)
                        attemptConnection(device, useSecure = !useSecure, isFallback = true)
                    }
                } else {
                    // Both ports failed. Let's run a quick diagnostic to provide clear, accurate guidance
                    scope.launch(Dispatchers.IO) {
                        val diag = runDiagnostics(device.ip)
                        val errorDetail = buildDetailedErrorMessage(device.ip, t, diag)
                        withContext(Dispatchers.Main) {
                            listener.onConnectionFailed(errorDetail)
                        }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                disconnect()
                listener.onDisconnected()
            }
        })
    }

    private fun buildDetailedErrorMessage(ip: String, t: Throwable, diag: DiagnosticResult): String {
        return buildString {
            append("Could not connect to TV at $ip")
            if (t.message != null && t.message != "null") {
                append(" (${t.message})")
            }
            append(".\n\n")

            append("Network status:\n")
            append("• Port 3001 (Secure WSS): ").append(if (diag.port3001Open) "OPEN" else "CLOSED").append("\n")
            append("• Port 3000 (Standard WS): ").append(if (diag.port3000Open) "OPEN" else "CLOSED").append("\n")

            if (!diag.port3001Open && !diag.port3000Open) {
                append("\nTroubleshooting:\n")
                append("1. Verify the TV is powered ON and awake (Quick Start+ recommended).\n")
                append("2. On your TV, turn ON 'LG Connect Apps' or 'Mobile TV On':\n")
                append("   Settings > General > Devices > External Devices > LG Connect Apps\n")
                append("3. Confirm phone and TV are on the same Wi-Fi network.")
            } else {
                append("\nTroubleshooting:\n")
                append("1. On TV, go to Settings > General > Devices > External Devices and ensure 'LG Connect Apps' is ON.\n")
                append("2. Tap 'Clear Key & Pair Fresh' below if this TV previously rejected a pairing key.")
            }
        }
    }

    fun disconnect() {
        try {
            mainWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        mainWebSocket = null

        try {
            pointerWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        pointerWebSocket = null
    }

    private fun sendRegisterHandshake(clientKey: String?) {
        val registerId = "register_${reqIdCounter.incrementAndGet()}"
        val json = JSONObject()
        json.put("id", registerId)
        json.put("type", "register")

        val payload = JSONObject()
        payload.put("forcePairing", false)
        payload.put("pairingType", "PROMPT")

        if (!clientKey.isNullOrBlank()) {
            payload.put("client-key", clientKey)
        }

        // Clean unsigned manifest according to Connect-SDK specification
        val permissions = JSONArray().apply {
            put("LAUNCH")
            put("LAUNCH_WEBAPP")
            put("APP_TO_APP")
            put("CLOSE")
            put("TEST_OPEN")
            put("TEST_PROTECTED")
            put("CONTROL_AUDIO")
            put("CONTROL_DISPLAY")
            put("CONTROL_INPUT_JOYSTICK")
            put("CONTROL_INPUT_MEDIA_RECORDING")
            put("CONTROL_INPUT_MEDIA_PLAYBACK")
            put("CONTROL_INPUT_TV")
            put("READ_APP_STATUS")
            put("READ_CURRENT_CHANNEL")
            put("READ_INPUT_DEVICE_LIST")
            put("READ_NETWORK_STATUS")
            put("READ_RUNNING_APPS")
            put("READ_TV_CHANNEL_LIST")
            put("WRITE_NOTIFICATION_TO_APP")
            put("READ_COUNTRY_INFO")
            put("CONTROL_INPUT_TEXT")
            put("CONTROL_MOUSE_AND_KEYBOARD")
        }

        val manifest = JSONObject()
        manifest.put("manifestVersion", 1)
        manifest.put("permissions", permissions)

        payload.put("manifest", manifest)
        json.put("payload", payload)

        val sent = mainWebSocket?.send(json.toString()) ?: false
        Log.d(TAG, "Sent register request: $sent (hasClientKey=${!clientKey.isNullOrBlank()})")

        // Prompt user on TV if no key or pairing requested
        listener.onPromptingUserOnTv()
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            val id = json.optString("id")

            if (type == "response" || type == "registered") {
                val payload = json.optJSONObject("payload")
                if (payload != null) {
                    val pairingType = payload.optString("pairingType")
                    if (pairingType.equals("PROMPT", ignoreCase = true)) {
                        listener.onPromptingUserOnTv()
                    }

                    val clientKey = payload.optString("client-key")
                    if (clientKey.isNotBlank() && currentDevice != null) {
                        Log.d(TAG, "Registered successfully with clientKey!")
                        val updated = currentDevice!!.copy(clientKey = clientKey)
                        currentDevice = updated
                        listener.onConnected(updated, clientKey)
                        requestPointerInputSocket()
                        subscribeToVolumeStatus()
                    } else if (currentDevice != null) {
                        Log.d(TAG, "Registered with existing key!")
                        listener.onConnected(currentDevice!!, currentDevice?.clientKey ?: "")
                        requestPointerInputSocket()
                        subscribeToVolumeStatus()
                    }
                }
            }

            val payload = json.optJSONObject("payload")
            if (id == "pointer_input_req" || (payload != null && payload.has("socketPath"))) {
                val socketPath = payload?.optString("socketPath")
                if (!socketPath.isNullOrBlank()) {
                    Log.d(TAG, "Received pointer socketPath: $socketPath")
                    connectPointerSocket(socketPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming message: $text", e)
        }
    }

    fun requestPointerInputSocket() {
        val req = JSONObject()
        req.put("id", "pointer_input_req")
        req.put("type", "request")
        req.put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
        mainWebSocket?.send(req.toString())
        Log.d(TAG, "Requested pointer input socket via ssap://com.webos.service.networkinput/getPointerInputSocket")
    }

    fun ensurePointerSocket() {
        if (pointerWebSocket == null && mainWebSocket != null) {
            requestPointerInputSocket()
        }
    }

    private fun connectPointerSocket(socketPath: String) {
        val normalizedPath = if (socketPath.startsWith("ws://") && currentDevice?.port == 3001) {
            socketPath.replaceFirst("ws://", "wss://")
        } else {
            socketPath
        }

        Log.d(TAG, "Connecting to Pointer WebSocket at: $normalizedPath")
        val request = Request.Builder().url(normalizedPath).build()
        pointerWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Pointer WebSocket connected successfully and ready for mouse input!")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Pointer WebSocket failed: ${t.message}")
                pointerWebSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Pointer WebSocket closed: $code / $reason")
                pointerWebSocket = null
            }
        })
    }

    private fun subscribeToVolumeStatus() {
        sendRequest("ssap://audio/getVolume", JSONObject())
    }

    fun sendButton(buttonName: String) {
        val sentPointer = pointerWebSocket?.send("type:button\nname:$buttonName\n\n") ?: false
        if (!sentPointer) {
            when (buttonName) {
                "UP" -> sendRequest("ssap://com.webos.service.ime/sendEnterKey", JSONObject())
                "DOWN" -> sendRequest("ssap://com.webos.service.ime/sendEnterKey", JSONObject())
                "ENTER" -> sendRequest("ssap://com.webos.service.ime/sendEnterKey", JSONObject())
                "BACK" -> sendRequest("ssap://system/exitApp", JSONObject())
                "HOME" -> sendRequest("ssap://system.launcher/open", JSONObject())
            }
            requestPointerInputSocket()
        }
    }

    fun sendPointerMove(dx: Int, dy: Int) {
        if (pointerWebSocket == null) {
            requestPointerInputSocket()
        }
        val packet = "type:move\ndx:$dx\ndy:$dy\ndown:0\n\n"
        pointerWebSocket?.send(packet)
    }

    fun sendPointerClick() {
        if (pointerWebSocket == null) {
            requestPointerInputSocket()
        }
        val sent = pointerWebSocket?.send("type:click\n\n") ?: false
        if (!sent) {
            sendButton("ENTER")
        }
    }

    fun sendScroll(dy: Int) {
        pointerWebSocket?.send("type:scroll\ndx:0\ndy:$dy\n\n")
    }

    fun volumeUp() {
        sendRequest("ssap://audio/volumeUp", JSONObject())
    }

    fun volumeDown() {
        sendRequest("ssap://audio/volumeDown", JSONObject())
    }

    fun setMute(mute: Boolean) {
        val payload = JSONObject().apply { put("mute", mute) }
        sendRequest("ssap://audio/setMute", payload)
    }

    fun openUrl(url: String) {
        val payload = JSONObject().apply { put("target", url) }
        sendRequest("ssap://system.launcher/open", payload)
    }

    fun launchBrowser() {
        val payload = JSONObject().apply { put("id", "com.webos.app.browser") }
        sendRequest("ssap://system.launcher/launch", payload)
    }

    fun sendText(text: String) {
        val payload = JSONObject().apply { put("text", text) }
        sendRequest("ssap://com.webos.service.ime/insertText", payload)
    }

    fun sendEnter() {
        sendRequest("ssap://com.webos.service.ime/sendEnterKey", JSONObject())
        pointerWebSocket?.send("type:button\nname:ENTER\n\n")
    }

    fun sendBackspace() {
        val payload = JSONObject().apply { put("count", 1) }
        sendRequest("ssap://com.webos.service.ime/deleteCharacters", payload)
        pointerWebSocket?.send("type:button\nname:BACK\n\n")
    }

    fun sendToast(message: String) {
        val payload = JSONObject().apply { put("message", message) }
        sendRequest("ssap://system.notifications/createToast", payload)
    }

    fun turnOff() {
        sendRequest("ssap://system/turnOff", JSONObject())
    }

    fun playMedia() {
        sendRequest("ssap://media.controls/play", JSONObject())
    }

    fun pauseMedia() {
        sendRequest("ssap://media.controls/pause", JSONObject())
    }

    fun channelUp() {
        sendRequest("ssap://tv/channelUp", JSONObject())
    }

    fun channelDown() {
        sendRequest("ssap://tv/channelDown", JSONObject())
    }

    private fun sendRequest(uri: String, payload: JSONObject) {
        val id = "req_${reqIdCounter.incrementAndGet()}"
        val msg = JSONObject().apply {
            put("id", id)
            put("type", "request")
            put("uri", uri)
            put("payload", payload)
        }
        mainWebSocket?.send(msg.toString())
    }
}
