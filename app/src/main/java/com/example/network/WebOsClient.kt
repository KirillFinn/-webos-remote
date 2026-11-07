package com.example.network

import android.util.Log
import com.example.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
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
    /** A text field on the TV gained (true) or lost (false) keyboard focus. */
    fun onTvTextFieldFocusChanged(focused: Boolean) {}
    /** The app in the foreground on the TV changed, e.g. "youtube.leanback.v4". */
    fun onForegroundAppChanged(appId: String) {}
}

data class DiagnosticResult(
    val ip: String,
    val port3001Open: Boolean,
    val port3000Open: Boolean,
    val tlsHandshakeSuccess: Boolean,
    val recommendation: String
)

/**
 * Client for the LG webOS "SSAP" second-screen protocol.
 *
 * Connection lifecycle:
 *  1. open main WebSocket (wss://ip:3001, falling back to ws://ip:3000 or vice versa)
 *  2. send `hello` + `register` (with stored client-key if we have one)
 *  3. TV answers `response {pairingType: PROMPT}` (user must press Allow) and then
 *     `registered {client-key}`; with a valid key the `registered` comes immediately
 *  4. only after `registered`: subscribe to volume/mute and open the pointer-input socket
 *
 * Every connection attempt gets a generation number. Callbacks from sockets belonging to an
 * older generation are ignored, so a late `onClosed` from a previous socket can never tear
 * down the connection that replaced it.
 *
 * All [WebOsEventListener] callbacks are delivered on the main thread.
 */
class WebOsClient(
    private val scope: CoroutineScope,
    private val listener: WebOsEventListener
) {
    companion object {
        private const val TAG = "WebOsClient"
        private const val SECURE_PORT = 3001
        private const val PLAIN_PORT = 3000

        /** How long to wait for any answer to the register request. */
        private const val REGISTER_RESPONSE_TIMEOUT_MS = 8_000L
        /** How long the user has to press "Allow" on the TV. */
        private const val PAIRING_PROMPT_TIMEOUT_MS = 60_000L
        const val YOUTUBE_APP_ID = "youtube.leanback.v4"
        private const val MAX_AUTO_RECONNECTS = 3
        private const val MAX_QUEUED_POINTER_MESSAGES = 16

        /**
         * Permission set used by current open-source webOS clients (aiowebostv / Home Assistant).
         * Notably includes CONTROL_POWER (turnOff) and WRITE_NOTIFICATION_TOAST (createToast),
         * which the previous manifest was missing, and READ_NETWORK_STATE (not "..._STATUS").
         */
        private val PERMISSIONS = listOf(
            "APP_TO_APP", "CLOSE", "CONTROL_AUDIO", "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_MEDIA_RECORDING",
            "CONTROL_INPUT_TEXT", "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER",
            "CONTROL_TV_SCREEN", "LAUNCH", "LAUNCH_WEBAPP", "READ_APP_STATUS", "READ_COUNTRY_INFO",
            "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_INSTALLED_APPS", "READ_LGE_SDX",
            "READ_LGE_TV_INPUT_EVENTS", "READ_NETWORK_STATE", "READ_NOTIFICATIONS", "READ_POWER_STATE",
            "READ_RUNNING_APPS", "READ_SETTINGS", "READ_TV_CHANNEL_LIST", "READ_TV_CURRENT_TIME",
            "READ_UPDATE_INFO", "SEARCH", "TEST_OPEN", "TEST_PROTECTED", "TEST_SECURE",
            "UPDATE_FROM_REMOTE_APP", "WRITE_NOTIFICATION_ALERT", "WRITE_NOTIFICATION_TOAST",
            "WRITE_SETTINGS"
        )
    }

    private enum class PointerState { NONE, REQUESTED, OPEN, FAILED }

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val okHttpClient = createOkHttpClient()

    private val lock = Any()
    private val generation = AtomicInteger(0)
    private val reqIdCounter = AtomicInteger(100)

    // All mutable connection state below is guarded by [lock].
    private var mainWebSocket: WebSocket? = null
    private var pointerWebSocket: WebSocket? = null
    private var pointerState = PointerState.NONE
    private val pendingPointerMessages = ArrayDeque<String>()
    private var currentDevice: TvDevice? = null
    private var registerId: String? = null
    private var isRegistered = false
    private var sentClientKey = false
    private var retriedWithoutKey = false
    private var reconnectAttempts = 0
    private var userRequestedClose = false
    private var registrationTimeoutJob: Job? = null
    private var reconnectJob: Job? = null

    /** Response handlers keyed by request id. Subscriptions stay registered. */
    private val responseHandlers = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val errorHandlers = ConcurrentHashMap<String, (String) -> Unit>()

    @Volatile private var lastVolume: Int = -1
    @Volatile private var lastMuted: Boolean? = null

    val isConnected: Boolean
        get() = synchronized(lock) { isRegistered && mainWebSocket != null }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    private fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // WebSockets: no read timeout
            .pingInterval(15, TimeUnit.SECONDS)      // detect dead sockets (Wi-Fi drop, TV standby)
            .retryOnConnectionFailure(false)         // we do our own retry/fallback

        return try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())

            // webOS TVs use self-signed certs and, on older models, older TLS setups.
            // Be maximally permissive: this connection never leaves the local network.
            val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                .allEnabledTlsVersions()
                .allEnabledCipherSuites()
                .build()

            builder
                .sslSocketFactory(sslContext.socketFactory, trustAllManager)
                .hostnameVerifier { _, _ -> true }
                .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create trust-all SSL client, falling back to default", e)
            builder.build()
        }
    }

    private fun notify(block: WebOsEventListener.() -> Unit) {
        scope.launch(Dispatchers.Main) { listener.block() }
    }

    // ---------------------------------------------------------------------------------------
    // Diagnostics
    // ---------------------------------------------------------------------------------------

    suspend fun runDiagnostics(ip: String): DiagnosticResult = withContext(Dispatchers.IO) {
        val port3001 = isPortOpen(ip, SECURE_PORT, 1500)
        val port3000 = isPortOpen(ip, PLAIN_PORT, 1500)
        var tlsOk = false

        if (port3001) {
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
                val raw = Socket().apply { connect(InetSocketAddress(ip, SECURE_PORT), 1500) }
                (sslContext.socketFactory.createSocket(raw, ip, SECURE_PORT, true) as SSLSocket).use { s ->
                    s.soTimeout = 2000
                    s.startHandshake()
                    tlsOk = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "TLS diagnostic failed: ${e.message}")
            }
        }

        val recommendation = when {
            port3001 && tlsOk -> "Port 3001 (Secure WSS) is active. Ready to pair."
            port3001 -> "Port 3001 is open, but the TLS handshake failed."
            port3000 -> "Port 3000 (Standard WS) is open. Connecting via standard mode."
            else -> "Neither port 3001 nor 3000 responded at $ip. Make sure the TV is ON and " +
                "'LG Connect Apps' / 'TV On With Mobile' is enabled."
        }
        DiagnosticResult(ip, port3001, port3000, tlsOk, recommendation)
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }

    // ---------------------------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------------------------

    fun connect(device: TvDevice, forceFreshPairing: Boolean = false) {
        val target = if (forceFreshPairing) device.copy(clientKey = null) else device
        synchronized(lock) {
            closeSocketsLocked()
            currentDevice = target
            retriedWithoutKey = false
            reconnectAttempts = 0
            userRequestedClose = false
        }
        startAttempt(target, useSecure = target.port != PLAIN_PORT, isFallback = false)
    }

    /** User-initiated disconnect: no callbacks, no auto-reconnect. */
    fun disconnect() {
        synchronized(lock) {
            userRequestedClose = true
            closeSocketsLocked()
        }
    }

    /** Must be called when the owner (ViewModel) is destroyed. */
    fun shutdown() {
        disconnect()
        okHttpClient.dispatcher.executorService.shutdown()
    }

    /** Closes both sockets and invalidates all callbacks from them. Caller holds [lock]. */
    private fun closeSocketsLocked() {
        generation.incrementAndGet()
        registrationTimeoutJob?.cancel(); registrationTimeoutJob = null
        reconnectJob?.cancel(); reconnectJob = null
        try { pointerWebSocket?.close(1000, null) } catch (_: Exception) {}
        try { mainWebSocket?.close(1000, null) } catch (_: Exception) {}
        pointerWebSocket = null
        mainWebSocket = null
        pointerState = PointerState.NONE
        pendingPointerMessages.clear()
        isRegistered = false
        registerId = null
        responseHandlers.clear()
        errorHandlers.clear()
    }

    private fun startAttempt(device: TvDevice, useSecure: Boolean, isFallback: Boolean) {
        val port = if (useSecure) SECURE_PORT else PLAIN_PORT
        val url = "${if (useSecure) "wss" else "ws"}://${device.ip}:$port"
        val gen: Int
        synchronized(lock) {
            closeSocketsLocked()
            gen = generation.get()
        }

        Log.d(TAG, "Connecting to $url (fallback=$isFallback, gen=$gen)")
        notify { onConnecting("Connecting to ${device.ip} via ${if (useSecure) "port 3001 (WSS)" else "port 3000 (WS)"}…") }

        // No custom Origin header: webOS resets connections with unexpected Origins.
        val request = Request.Builder().url(url).build()
        val ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(gen, webSocket)) return
                Log.d(TAG, "Main socket open on $url")
                val clientKey = synchronized(lock) {
                    currentDevice = device.copy(port = port)
                    currentDevice?.clientKey
                }
                sendHelloAndRegister(gen, clientKey)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(gen, webSocket)) return
                handleIncomingMessage(gen, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(gen, webSocket)) return
                Log.w(TAG, "Main socket failure on $url: ${t.message}")
                val wasRegistered = synchronized(lock) { isRegistered }
                if (wasRegistered) {
                    handleConnectionLost("Connection lost: ${t.message ?: t.javaClass.simpleName}")
                } else if (!isFallback) {
                    // Never got registered on this port – try the other one.
                    scope.launch(Dispatchers.IO) {
                        delay(300)
                        if (generation.get() == gen) startAttempt(device, !useSecure, isFallback = true)
                    }
                } else {
                    synchronized(lock) { closeSocketsLocked() }
                    scope.launch(Dispatchers.IO) {
                        val diag = runDiagnostics(device.ip)
                        val msg = buildDetailedErrorMessage(device.ip, t, diag)
                        notify { onConnectionFailed(msg) }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(gen, webSocket)) return
                Log.d(TAG, "Main socket closed by TV: $code / $reason")
                handleConnectionLost(null)
            }
        })
        synchronized(lock) {
            if (generation.get() == gen) mainWebSocket = ws else ws.cancel()
        }
    }

    private fun isCurrent(gen: Int, ws: WebSocket): Boolean = synchronized(lock) {
        // mainWebSocket may still be null for a few microseconds right after newWebSocket();
        // the generation check alone is sufficient in that window.
        generation.get() == gen && (mainWebSocket == null || mainWebSocket === ws)
    }

    /** The TV went away after a successful registration (Wi-Fi drop, TV off, standby). */
    private fun handleConnectionLost(reason: String?) {
        val device: TvDevice?
        val attempt: Int
        synchronized(lock) {
            val wasRegistered = isRegistered
            closeSocketsLocked()
            if (userRequestedClose || !wasRegistered || reconnectAttempts >= MAX_AUTO_RECONNECTS) {
                device = null
                attempt = -1
            } else {
                reconnectAttempts++
                device = currentDevice
                attempt = reconnectAttempts
            }
        }

        if (device == null || attempt < 0) {
            notify { onDisconnected() }
            return
        }

        Log.d(TAG, "Auto-reconnect attempt $attempt ($reason)")
        notify { onConnecting("Connection lost – reconnecting (attempt $attempt/$MAX_AUTO_RECONNECTS)…") }
        val job = scope.launch(Dispatchers.IO) {
            delay(1000L shl (attempt - 1))  // 1s, 2s, 4s
            startAttempt(device, useSecure = device.port != PLAIN_PORT, isFallback = false)
        }
        synchronized(lock) { reconnectJob = job }
    }

    private fun buildDetailedErrorMessage(ip: String, t: Throwable, diag: DiagnosticResult): String =
        buildString {
            append("Could not connect to TV at $ip")
            t.message?.takeIf { it.isNotBlank() && it != "null" }?.let { append(" ($it)") }
            append(".\n\nNetwork status:\n")
            append("• Port 3001 (Secure WSS): ").append(if (diag.port3001Open) "OPEN" else "CLOSED").append('\n')
            append("• Port 3000 (Standard WS): ").append(if (diag.port3000Open) "OPEN" else "CLOSED").append('\n')
            append("\nTroubleshooting:\n")
            if (!diag.port3001Open && !diag.port3000Open) {
                append("1. Make sure the TV is ON (not in standby) – enable 'Quick Start+' so it stays reachable.\n")
                append("2. On the TV enable 'LG Connect Apps' / 'TV On With Mobile'\n")
                append("   (Settings > General > Devices > External Devices).\n")
                append("3. Phone and TV must be on the same network (no guest Wi-Fi / AP isolation).\n")
                append("4. Give the TV a fixed IP (DHCP reservation) in your router so it doesn't change.")
            } else {
                append("1. Ensure 'LG Connect Apps' is ON on the TV.\n")
                append("2. Tap 'Clear Key & Pair Fresh' if this TV was reset or previously rejected the pairing.")
            }
        }

    // ---------------------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------------------

    private fun sendHelloAndRegister(gen: Int, clientKey: String?) {
        val id = "register_${reqIdCounter.incrementAndGet()}"
        val hasKey = !clientKey.isNullOrBlank()

        val payload = JSONObject().apply {
            put("forcePairing", false)
            put("pairingType", "PROMPT")
            if (hasKey) put("client-key", clientKey)
            put("manifest", JSONObject().apply {
                put("manifestVersion", 1)
                put("appVersion", "1.1")
                put("permissions", JSONArray(PERMISSIONS))
            })
        }
        val register = JSONObject().apply {
            put("id", id)
            put("type", "register")
            put("payload", payload)
        }
        val hello = JSONObject().apply {
            put("id", "hello")
            put("type", "hello")
            put("payload", JSONObject())
        }

        val ws = synchronized(lock) {
            if (generation.get() != gen) return
            registerId = id
            sentClientKey = hasKey
            mainWebSocket
        } ?: return

        ws.send(hello.toString())
        val sent = ws.send(register.toString())
        Log.d(TAG, "Sent register: $sent (hasClientKey=$hasKey)")

        // Only show "accept on TV" when a prompt is actually expected; the TV's
        // `pairingType: PROMPT` response will switch the UI to the prompt state.
        notify { onConnecting(if (hasKey) "Authenticating with saved key…" else "Requesting pairing…") }
        armRegistrationTimeout(gen, REGISTER_RESPONSE_TIMEOUT_MS, "The TV did not answer the pairing request.")
    }

    private fun armRegistrationTimeout(gen: Int, timeoutMs: Long, message: String) {
        val job = scope.launch(Dispatchers.IO) {
            delay(timeoutMs)
            val timedOut = synchronized(lock) {
                if (generation.get() == gen && !isRegistered) { closeSocketsLocked(); true } else false
            }
            if (timedOut) notify { onConnectionFailed(message) }
        }
        synchronized(lock) {
            registrationTimeoutJob?.cancel()
            registrationTimeoutJob = job
        }
    }

    private fun handleIncomingMessage(gen: Int, text: String) {
        val json = try { JSONObject(text) } catch (e: Exception) {
            Log.e(TAG, "Unparseable message: $text", e); return
        }
        val type = json.optString("type")
        val id = json.optString("id")
        val payload = json.optJSONObject("payload") ?: JSONObject()
        val regId = synchronized(lock) { registerId }

        when {
            type == "hello" -> Log.d(TAG, "Hello from TV: ${payload.optString("deviceOS")} ${payload.optString("deviceOSReleaseVersion")}")

            id == regId && type == "response" -> {
                if (payload.optString("pairingType").equals("PROMPT", ignoreCase = true)) {
                    Log.d(TAG, "TV is showing the pairing prompt")
                    notify { onPromptingUserOnTv() }
                    armRegistrationTimeout(gen, PAIRING_PROMPT_TIMEOUT_MS,
                        "Pairing timed out. Press 'Allow' on the TV within 60 seconds and try again.")
                }
            }

            id == regId && type == "registered" -> onRegistered(gen, payload.optString("client-key"))

            id == regId && type == "error" -> onRegistrationError(gen, json.optString("error"))

            type == "error" -> {
                val error = json.optString("error")
                Log.w(TAG, "Request $id failed: $error")
                errorHandlers.remove(id)?.invoke(error)
                responseHandlers.remove(id)
            }

            else -> {
                if (payload.has("returnValue") && !payload.optBoolean("returnValue", true)) {
                    Log.w(TAG, "Request $id returned failure: ${payload.optString("errorText")}")
                }
                responseHandlers[id]?.invoke(payload)
            }
        }
    }

    private fun onRegistered(gen: Int, newKey: String) {
        val device: TvDevice
        synchronized(lock) {
            if (generation.get() != gen) return
            registrationTimeoutJob?.cancel(); registrationTimeoutJob = null
            isRegistered = true
            reconnectAttempts = 0
            val key = newKey.ifBlank { currentDevice?.clientKey.orEmpty() }
            device = (currentDevice ?: return).copy(clientKey = key)
            currentDevice = device
        }
        Log.d(TAG, "Registered with ${device.ip}:${device.port}")
        notify { onConnected(device, device.clientKey.orEmpty()) }
        subscribeToAudioStatus()
        subscribeToRemoteKeyboard()
        subscribeToForegroundApp()
        requestPointerInputSocket()
    }

    private fun onRegistrationError(gen: Int, error: String) {
        Log.w(TAG, "Registration error: $error")
        val retryDevice = synchronized(lock) {
            if (generation.get() != gen) return
            val rejectedByUser = error.contains("reject", ignoreCase = true) ||
                error.contains("cancel", ignoreCase = true)
            if (sentClientKey && !retriedWithoutKey && !rejectedByUser) {
                // Stored key is no longer valid (TV reset / key revoked): pair again once.
                retriedWithoutKey = true
                currentDevice?.copy(clientKey = null)?.also { currentDevice = it }
            } else null
        }

        if (retryDevice != null) {
            Log.d(TAG, "Saved client-key rejected – retrying with a fresh pairing prompt")
            startAttempt(retryDevice, useSecure = retryDevice.port != PLAIN_PORT, isFallback = true)
        } else {
            synchronized(lock) { closeSocketsLocked() }
            val msg = if (error.contains("reject", true) || error.contains("cancel", true))
                "Pairing was declined on the TV. Try again and press 'Allow'."
            else "The TV refused the connection: $error"
            notify { onConnectionFailed(msg) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Requests
    // ---------------------------------------------------------------------------------------

    private fun sendRequest(
        uri: String,
        payload: JSONObject = JSONObject(),
        type: String = "request",
        onError: ((String) -> Unit)? = null,
        onResponse: ((JSONObject) -> Unit)? = null
    ): Boolean {
        val ws = synchronized(lock) { if (isRegistered) mainWebSocket else null }
        if (ws == null) {
            Log.w(TAG, "Not connected – dropped $uri")
            return false
        }
        val id = "${if (type == "subscribe") "sub" else "req"}_${reqIdCounter.incrementAndGet()}"
        if (onResponse != null) {
            responseHandlers[id] = if (type == "subscribe") onResponse else { p ->
                responseHandlers.remove(id); errorHandlers.remove(id); onResponse(p)
            }
        }
        if (onError != null) errorHandlers[id] = onError
        val msg = JSONObject().apply {
            put("id", id)
            put("type", type)
            put("uri", uri)
            put("payload", payload)
        }
        return ws.send(msg.toString())
    }

    private fun subscribeToAudioStatus() {
        // Volume: older firmware returns {volume, muted}; webOS 5+ nests in {volumeStatus:{volume, muteStatus}}.
        sendRequest("ssap://audio/getVolume", type = "subscribe") { p -> handleAudioPayload(p) }
        // Mute status: {mute: Boolean, volume: Int}
        sendRequest("ssap://audio/getStatus", type = "subscribe") { p -> handleAudioPayload(p) }
    }

    /**
     * Registers this client as the TV's remote keyboard. Without this subscription webOS
     * silently ignores ime/insertText, deleteCharacters and sendEnterKey – which is why
     * nothing reached YouTube's search box. The TV pushes `currentWidget` whenever a text
     * field gains or loses focus.
     */
    private fun subscribeToRemoteKeyboard() {
        sendRequest(
            "ssap://com.webos.service.ime/registerRemoteKeyboard",
            type = "subscribe",
            onError = { err -> Log.w(TAG, "registerRemoteKeyboard refused: $err") }
        ) { p ->
            val widget = p.optJSONObject("currentWidget") ?: return@sendRequest
            val focused = widget.optBoolean("focus", false)
            Log.d(TAG, "TV text field focus=$focused (${widget.optString("contentType")})")
            notify { onTvTextFieldFocusChanged(focused) }
        }
    }

    private fun subscribeToForegroundApp() {
        sendRequest(
            "ssap://com.webos.applicationManager/getForegroundAppInfo",
            type = "subscribe",
            onError = { err -> Log.w(TAG, "getForegroundAppInfo refused: $err") }
        ) { p ->
            val appId = p.optString("appId")
            if (p.has("appId")) {
                Log.d(TAG, "Foreground app: $appId")
                notify { onForegroundAppChanged(appId) }
            }
        }
    }

    private fun handleAudioPayload(p: JSONObject) {
        val src = p.optJSONObject("volumeStatus") ?: p
        val volume = src.optInt("volume", lastVolume)
        val muted = when {
            src.has("muteStatus") -> src.optBoolean("muteStatus")
            src.has("muted") -> src.optBoolean("muted")
            p.has("mute") -> p.optBoolean("mute")
            else -> lastMuted ?: false
        }
        if (volume == lastVolume && muted == lastMuted) return
        lastVolume = volume
        lastMuted = muted
        if (volume >= 0) notify { onVolumeChanged(volume, muted) }
    }

    // ---------------------------------------------------------------------------------------
    // Pointer / input socket (buttons, mouse, scroll)
    // ---------------------------------------------------------------------------------------

    fun ensurePointerSocket() = requestPointerInputSocket()

    private fun requestPointerInputSocket() {
        val gen = synchronized(lock) {
            if (!isRegistered || pointerState == PointerState.REQUESTED || pointerState == PointerState.OPEN) return
            pointerState = PointerState.REQUESTED
            generation.get()
        }
        val sent = sendRequest(
            "ssap://com.webos.service.networkinput/getPointerInputSocket",
            onError = { err ->
                Log.w(TAG, "Pointer socket request refused: $err")
                synchronized(lock) { if (generation.get() == gen) pointerState = PointerState.FAILED }
            }
        ) { p ->
            val path = p.optString("socketPath")
            if (path.isNotBlank()) connectPointerSocket(gen, path)
            else synchronized(lock) { if (generation.get() == gen) pointerState = PointerState.FAILED }
        }
        if (!sent) synchronized(lock) { pointerState = PointerState.NONE }
    }

    private fun connectPointerSocket(gen: Int, socketPath: String) {
        val securePort = synchronized(lock) { currentDevice?.port == SECURE_PORT }
        // Some firmware hands out a ws://…:3000 path even when only 3001 is usable.
        val url = if (securePort && socketPath.startsWith("ws://")) {
            socketPath.replaceFirst("ws://", "wss://").replaceFirst(":$PLAIN_PORT/", ":$SECURE_PORT/")
        } else socketPath

        Log.d(TAG, "Connecting pointer socket: $url")
        val ws = okHttpClient.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val queued = synchronized(lock) {
                    if (generation.get() != gen) { webSocket.close(1000, null); return }
                    pointerWebSocket = webSocket
                    pointerState = PointerState.OPEN
                    pendingPointerMessages.toList().also { pendingPointerMessages.clear() }
                }
                Log.d(TAG, "Pointer socket open, flushing ${queued.size} queued message(s)")
                queued.forEach { webSocket.send(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Pointer socket failed: ${t.message}")
                resetPointer(gen, webSocket)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Pointer socket closed: $code / $reason")
                resetPointer(gen, webSocket)
            }
        })
        synchronized(lock) { if (generation.get() != gen) ws.cancel() }
    }

    private fun resetPointer(gen: Int, ws: WebSocket) {
        synchronized(lock) {
            if (generation.get() != gen) return
            if (pointerWebSocket === ws || pointerWebSocket == null) {
                pointerWebSocket = null
                pointerState = PointerState.NONE  // will be re-requested on next use
            }
        }
    }

    /**
     * Sends a message on the pointer socket. If the socket isn't open yet, [queueIfNotReady]
     * messages are held and flushed once it opens (button presses); transient ones (moves)
     * are dropped. Returns false if the pointer socket is unavailable on this TV.
     */
    private fun sendPointer(message: String, queueIfNotReady: Boolean): Boolean {
        val (ws, state) = synchronized(lock) {
            val s = pointerState
            if (s != PointerState.OPEN && queueIfNotReady && s != PointerState.FAILED) {
                if (pendingPointerMessages.size >= MAX_QUEUED_POINTER_MESSAGES) pendingPointerMessages.removeFirst()
                pendingPointerMessages.addLast(message)
            }
            pointerWebSocket to s
        }
        return when (state) {
            PointerState.OPEN -> ws?.send(message) ?: false
            PointerState.FAILED -> false
            else -> { requestPointerInputSocket(); true }
        }
    }

    /** Remote-control key, e.g. UP, DOWN, LEFT, RIGHT, ENTER, BACK, HOME, MENU, CHANNELUP, RED. */
    fun sendButton(buttonName: String) {
        if (sendPointer("type:button\nname:$buttonName\n\n", queueIfNotReady = true)) return
        // Pointer input is not available on this TV/firmware: use SSAP equivalents where they exist.
        Log.w(TAG, "Pointer socket unavailable, SSAP fallback for $buttonName")
        when (buttonName) {
            "ENTER" -> sendRequest("ssap://com.webos.service.ime/sendEnterKey")
            "HOME" -> sendRequest("ssap://system.launcher/launch", JSONObject().put("id", "com.webos.app.home"))
            "VOLUMEUP" -> volumeUp()
            "VOLUMEDOWN" -> volumeDown()
            "CHANNELUP" -> channelUp()
            "CHANNELDOWN" -> channelDown()
            "PLAY" -> playMedia()
            "PAUSE" -> pauseMedia()
        }
    }

    fun sendPointerMove(dx: Int, dy: Int) {
        sendPointer("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n", queueIfNotReady = false)
    }

    fun sendPointerClick() {
        if (!sendPointer("type:click\n\n", queueIfNotReady = true)) {
            sendRequest("ssap://com.webos.service.ime/sendEnterKey")
        }
    }

    fun sendScroll(dy: Int) {
        sendPointer("type:scroll\ndx:0\ndy:$dy\n\n", queueIfNotReady = false)
    }

    // ---------------------------------------------------------------------------------------
    // SSAP commands
    // ---------------------------------------------------------------------------------------

    fun volumeUp() { sendRequest("ssap://audio/volumeUp") }

    fun volumeDown() { sendRequest("ssap://audio/volumeDown") }

    fun setMute(mute: Boolean) { sendRequest("ssap://audio/setMute", JSONObject().put("mute", mute)) }

    /** Toggles mute based on the TV's reported state; falls back to the MUTE key if unknown. */
    fun toggleMute() {
        val muted = lastMuted
        if (muted != null) setMute(!muted) else sendButton("MUTE")
    }

    fun openUrl(url: String) {
        // Standard "open URL" call; if the firmware refuses it, launch the browser app explicitly.
        sendRequest("ssap://system.launcher/open", JSONObject().put("target", url), onError = {
            sendRequest(
                "ssap://system.launcher/launch",
                JSONObject().put("id", "com.webos.app.browser").put("target", url)
            )
        })
    }

    fun launchBrowser() {
        sendRequest("ssap://system.launcher/launch", JSONObject().put("id", "com.webos.app.browser"))
    }

    fun sendText(text: String) {
        sendRequest(
            "ssap://com.webos.service.ime/insertText",
            JSONObject().put("text", text).put("replace", 0),
            onError = { err -> Log.w(TAG, "insertText failed: $err") }
        )
    }

    /**
     * Opens YouTube's search results for [query]. YouTube on webOS draws its own on-screen
     * keyboard and never hands its search box to the TV's input service, so text typed via
     * ime/insertText can't reach it. Its manifest instead accepts an in-app search
     * deep link of the form `q=<keywords>` in `contentTarget`.
     */
    fun launchYouTubeSearch(query: String, appId: String = YOUTUBE_APP_ID) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        sendRequest(
            "ssap://system.launcher/launch",
            JSONObject()
                .put("id", appId)
                .put("params", JSONObject().put("contentTarget", "q=$encoded")),
            onError = { err ->
                Log.w(TAG, "YouTube search launch failed: $err")
                sendToast("Couldn't start the YouTube search")
            }
        )
    }

    fun sendEnter() {
        // IME enter only – also sending a pointer ENTER produced a double submit.
        sendRequest("ssap://com.webos.service.ime/sendEnterKey")
    }

    fun sendBackspace() {
        deleteCharacters(1)
    }

    /** Deletes [count] characters before the cursor of the focused TV input field. */
    fun deleteCharacters(count: Int) {
        if (count <= 0) return
        // IME delete only – the previous extra pointer BACK key navigated away from the page.
        sendRequest("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", count))
    }

    fun sendToast(message: String) {
        sendRequest("ssap://system.notifications/createToast", JSONObject().put("message", message))
    }

    fun turnOff() {
        synchronized(lock) { userRequestedClose = true }  // don't auto-reconnect to a TV we just switched off
        sendRequest("ssap://system/turnOff")
    }

    fun playMedia() { sendRequest("ssap://media.controls/play") }

    fun pauseMedia() { sendRequest("ssap://media.controls/pause") }

    fun channelUp() { sendRequest("ssap://tv/channelUp") }

    fun channelDown() { sendRequest("ssap://tv/channelDown") }
}
