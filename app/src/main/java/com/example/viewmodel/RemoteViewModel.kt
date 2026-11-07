package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TvPreferencesRepository
import com.example.model.ConnectionStatus
import com.example.model.Direction
import com.example.model.RemoteMode
import com.example.model.RemoteUiState
import com.example.model.TvDevice
import com.example.model.isYouTubeOnScreen
import com.example.network.SsdpDiscovery
import com.example.network.WebOsClient
import com.example.network.WebOsEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteViewModel(application: Application) : AndroidViewModel(application), WebOsEventListener {

    companion object {
        private const val AUTO_RECONNECT_FAST_MS = 3_000L
        private const val AUTO_RECONNECT_SLOW_MS = 10_000L
        /** Fast polling for the first ~minute without the TV, then slower. */
        private const val FAST_PROBES = 20
        private const val PROBE_TIMEOUT_MS = 700
        private const val SSDP_EVERY_N_PROBES = 10
        private const val MAX_AUTO_AUTH_FAILURES = 2
        /** After switching the TV off from the app, don't reconnect while it powers down. */
        private const val POWER_OFF_GRACE_MS = 20_000L
    }

    private val repository = TvPreferencesRepository(application)
    private val ssdpDiscovery = SsdpDiscovery(application)
    private val webOsClient = WebOsClient(viewModelScope, this)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _uiState = MutableStateFlow(
        RemoteUiState(
            savedDevices = repository.getSavedDevices(),
            recentUrls = repository.getRecentUrls(),
            hapticEnabled = repository.isHapticEnabled()
        )
    )
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    // ---------------------------------------------------------------------------------------
    // Persistent connection
    //
    // Once a TV has been paired (we hold its client-key) the app keeps itself connected to it
    // while it is on screen: when the TV is off, a cheap TCP probe runs every few seconds and
    // as soon as the TV answers we register again with the saved key (no prompt on the TV).
    // If the router gave the TV a new IP, it is found again via SSDP by its UUID.
    // ---------------------------------------------------------------------------------------

    private var autoReconnectJob: Job? = null
    private var inForeground = false
    /** The user pressed "Disconnect": stay offline until they pick a TV again. */
    private var userDisconnected = false
    /** True while a connection attempt the user started themselves is running. */
    private var userAttemptPending = false
    /** Consecutive auto-attempts that reached the TV but failed to register (e.g. key revoked). */
    private var autoAuthFailures = 0
    private var tickInFlight = false
    private var suppressAutoUntil = 0L
    private var failedProbes = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        // Quietly connect to the last paired TV; if it's off we just wait for it.
        rememberedDevice()?.takeIf { !it.clientKey.isNullOrBlank() }?.let { startAutoConnect(it) }
    }

    /** The TV we should stay connected to: the last one used, else any paired one. */
    private fun rememberedDevice(): TvDevice? {
        val saved = repository.getSavedDevices()
        val lastIp = repository.getLastConnectedIp()
        return saved.find { it.ip == lastIp } ?: saved.firstOrNull { !it.clientKey.isNullOrBlank() }
    }

    /** Called from Activity.onStart. */
    fun onAppForeground() {
        inForeground = true
        failedProbes = 0
        registerNetworkCallback()
        reconnectIfNeeded()
        startAutoReconnectLoop()
    }

    /** Called from Activity.onStop: no polling in the background, it would only drain the battery. */
    fun onAppBackground() {
        inForeground = false
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        unregisterNetworkCallback()
    }

    private fun startAutoReconnectLoop() {
        if (autoReconnectJob?.isActive == true) return
        autoReconnectJob = viewModelScope.launch {
            while (isActive) {
                autoReconnectTick()
                delay(if (failedProbes < FAST_PROBES) AUTO_RECONNECT_FAST_MS else AUTO_RECONNECT_SLOW_MS)
            }
        }
    }

    /** Whether the supervisor should try to (re)connect to the remembered TV right now. */
    internal fun shouldAutoReconnect(now: Long = System.currentTimeMillis()): Boolean {
        if (!inForeground || userDisconnected) return false
        // The connection dialog is open: the user is managing the connection by hand.
        if (_uiState.value.showConnectionDialog) return false
        if (autoAuthFailures >= MAX_AUTO_AUTH_FAILURES || now < suppressAutoUntil) return false
        if (webOsClient.isConnected) return false
        return when (_uiState.value.connectionStatus) {
            is ConnectionStatus.Connecting, is ConnectionStatus.PromptingOnTv,
            is ConnectionStatus.Scanning, is ConnectionStatus.Connected -> false
            // Only a paired TV: an unpaired one would show the "Allow" prompt over and over.
            else -> !rememberedDevice()?.clientKey.isNullOrBlank()
        }
    }

    private suspend fun autoReconnectTick() {
        if (tickInFlight || !shouldAutoReconnect()) return
        val device = rememberedDevice() ?: return
        tickInFlight = true
        try {
            val reachable = withContext(Dispatchers.IO) { isTvReachable(device.ip, device.port) }
            // Things may have changed during the probe (user connected manually, app went away).
            if (!shouldAutoReconnect()) return
            if (reachable) {
                failedProbes = 0
                startAutoConnect(device)
                return
            }
            failedProbes++
            setWaiting(device)
            // Every ~30 s of silence, check whether the TV came back under a different IP.
            if (failedProbes % SSDP_EVERY_N_PROBES == 0 && scanJob?.isActive != true) {
                val moved = findMovedDevice(device)
                if (moved != null && shouldAutoReconnect()) startAutoConnect(moved)
            }
        } finally {
            tickInFlight = false
        }
    }

    private fun setWaiting(device: TvDevice) {
        _uiState.update {
            when (it.connectionStatus) {
                is ConnectionStatus.Disconnected, is ConnectionStatus.Error, is ConnectionStatus.WaitingForTv ->
                    it.copy(connectionStatus = ConnectionStatus.WaitingForTv(device.name), activeDevice = device)
                else -> it
            }
        }
    }

    private fun startAutoConnect(device: TvDevice) {
        userAttemptPending = false
        _uiState.update { it.copy(activeDevice = device, connectionStatus = ConnectionStatus.Connecting(device.ip)) }
        webOsClient.connect(device)
    }

    private fun isTvReachable(ip: String, port: Int): Boolean {
        val first = if (port == 3000) 3000 else 3001
        val second = if (first == 3001) 3000 else 3001
        return ssdpDiscovery.isPortOpen(ip, first, PROBE_TIMEOUT_MS) ||
            ssdpDiscovery.isPortOpen(ip, second, PROBE_TIMEOUT_MS)
    }

    /** Looks for the remembered TV (by UUID) at a new address and moves its saved entry there. */
    private suspend fun findMovedDevice(device: TvDevice): TvDevice? {
        val uuid = device.uuid ?: return null
        val found = ssdpDiscovery.discoverDevices(timeoutMs = 3000)
            .firstOrNull { it.uuid == uuid && it.ip != device.ip } ?: return null
        val moved = device.copy(ip = found.ip)
        repository.removeDevice(device.ip)
        repository.saveDevice(moved)
        repository.setLastConnectedIp(moved.ip)
        _uiState.update { it.copy(savedDevices = repository.getSavedDevices()) }
        return moved
    }

    /** Learns the UUID of a TV that was added by IP, so a later IP change can be followed. */
    private fun learnUuidIfMissing(ip: String) {
        if (repository.getSavedDevices().find { it.ip == ip }?.uuid != null) return
        viewModelScope.launch {
            val uuid = ssdpDiscovery.discoverDevices(timeoutMs = 3000).firstOrNull { it.ip == ip }?.uuid
                ?: return@launch
            val current = repository.getSavedDevices().find { it.ip == ip } ?: return@launch
            repository.saveDevice(current.copy(uuid = uuid))
            _uiState.update { it.copy(savedDevices = repository.getSavedDevices()) }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Back on a network (e.g. Wi-Fi after wake-up): try now, not at the next tick.
                viewModelScope.launch {
                    failedProbes = 0
                    autoReconnectTick()
                }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {}
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try { cm?.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }

    fun triggerHaptic(strong: Boolean = false) {
        if (!_uiState.value.hapticEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (strong) {
                    VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createOneShot(18, 120)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(if (strong) 35 else 18)
            }
        } catch (_: Exception) {}
    }

    fun setRemoteMode(mode: RemoteMode) {
        triggerHaptic()
        _uiState.update { it.copy(remoteMode = mode) }
        if (mode == RemoteMode.TOUCHPAD) {
            webOsClient.ensurePointerSocket()
        }
    }

    fun startScan() {
        scanJob?.cancel()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.Scanning("Searching local network for LG webOS TVs..."),
                showConnectionDialog = true
            )
        }
        scanJob = viewModelScope.launch {
            val devices = ssdpDiscovery.discoverDevices()
            _uiState.update {
                it.copy(
                    discoveredDevices = devices,
                    connectionStatus = if (devices.isEmpty()) {
                        ConnectionStatus.Disconnected
                    } else {
                        it.connectionStatus
                    }
                )
            }
        }
    }

    fun connectToTv(device: TvDevice, forceFreshPairing: Boolean = false) {
        triggerHaptic(true)
        userDisconnected = false
        userAttemptPending = true
        autoAuthFailures = 0
        suppressAutoUntil = 0L
        // A TV picked from the scan results carries no key: reuse the saved pairing for it,
        // otherwise every pick would overwrite the key and ask for "Allow" on the TV again.
        val saved = repository.getSavedDevices().find { it.ip == device.ip }
        val merged = if (saved != null) {
            device.copy(
                clientKey = device.clientKey ?: saved.clientKey,
                uuid = device.uuid ?: saved.uuid,
                mac = device.mac ?: saved.mac
            )
        } else device
        val target = if (forceFreshPairing) merged.copy(clientKey = null) else merged
        _uiState.update {
            it.copy(
                activeDevice = target,
                showConnectionDialog = false,
                // Set synchronously so a second trigger (e.g. onStart) doesn't restart the attempt.
                connectionStatus = ConnectionStatus.Connecting(target.ip)
            )
        }
        repository.setLastConnectedIp(target.ip)
        if (forceFreshPairing) {
            repository.saveClientKey(target.ip, "")
        }
        repository.saveDevice(target)
        webOsClient.connect(target, forceFreshPairing)
    }

    fun connectManual(ip: String, port: Int = 3001, forceFreshPairing: Boolean = false) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) return
        // Reuse the saved entry (and its client-key) if this TV was paired before, so a manual
        // connect doesn't trigger a new "Allow" prompt every time.
        val saved = repository.getSavedDevices().find { it.ip == cleanIp }
        val device = saved?.copy(port = port)
            ?: TvDevice(ip = cleanIp, port = port, name = "LG webOS TV ($cleanIp)")
        connectToTv(device, forceFreshPairing)
    }

    /**
     * Called when the app returns to the foreground. Phones drop Wi-Fi sockets while asleep,
     * so reconnect to the last TV if we are not connected.
     */
    fun reconnectIfNeeded() {
        val state = _uiState.value
        if (webOsClient.isConnected) return
        when (state.connectionStatus) {
            is ConnectionStatus.Connecting, is ConnectionStatus.PromptingOnTv, is ConnectionStatus.Scanning -> return
            else -> Unit
        }
        if (userDisconnected || System.currentTimeMillis() < suppressAutoUntil) return
        val device = rememberedDevice() ?: return
        if (device.clientKey.isNullOrBlank()) return
        startAutoConnect(device)
    }

    fun clearKeysAndPairFresh(ip: String) {
        val device = _uiState.value.savedDevices.find { it.ip == ip }
            ?: TvDevice(ip = ip.trim(), port = 3001, name = "LG webOS TV ($ip)")
        connectToTv(device, forceFreshPairing = true)
    }

    fun disconnect() {
        triggerHaptic()
        userDisconnected = true
        userAttemptPending = false
        webOsClient.disconnect()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.Disconnected,
                activeDevice = null
            )
        }
    }

    // Remote Actions
    fun sendDirection(direction: Direction) {
        triggerHaptic()
        val name = when (direction) {
            Direction.UP -> "UP"
            Direction.DOWN -> "DOWN"
            Direction.LEFT -> "LEFT"
            Direction.RIGHT -> "RIGHT"
        }
        webOsClient.sendButton(name)
    }

    fun sendOk() {
        triggerHaptic(true)
        webOsClient.sendButton("ENTER")
    }

    fun sendBack() {
        triggerHaptic()
        webOsClient.sendButton("BACK")
    }

    fun sendHome() {
        triggerHaptic(true)
        webOsClient.sendButton("HOME")
    }

    // webOS has separate PLAY and PAUSE keys; alternate between them for a single toggle key.
    // The first press pauses, since the key is usually pressed while something is playing.
    private var assumePlaying = true

    fun togglePlayPause() {
        triggerHaptic()
        webOsClient.sendButton(if (assumePlaying) "PAUSE" else "PLAY")
        assumePlaying = !assumePlaying
    }

    private var pointerAccumulatorX = 0f
    private var pointerAccumulatorY = 0f

    // Touchpad & Pointer
    fun sendPointerMove(dx: Float, dy: Float) {
        pointerAccumulatorX += dx * 1.5f
        pointerAccumulatorY += dy * 1.5f
        val moveX = pointerAccumulatorX.toInt()
        val moveY = pointerAccumulatorY.toInt()
        if (moveX != 0 || moveY != 0) {
            pointerAccumulatorX -= moveX
            pointerAccumulatorY -= moveY
            webOsClient.sendPointerMove(moveX, moveY)
        }
    }

    fun sendPointerClick() {
        triggerHaptic(true)
        webOsClient.sendPointerClick()
    }

    fun sendScroll(dy: Float) {
        webOsClient.sendScroll(dy.toInt())
    }

    // Volume & Audio Controls
    fun volumeUp() {
        triggerHaptic()
        webOsClient.volumeUp()
    }

    fun volumeDown() {
        triggerHaptic()
        webOsClient.volumeDown()
    }

    fun toggleMute() {
        triggerHaptic(true)
        // Previously always sent mute=true, so the TV could never be unmuted from the app.
        webOsClient.toggleMute()
    }

    // Web Browser Navigation & Keyboard Features
    fun openUrl(url: String) {
        triggerHaptic(true)
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = if (formatted.contains(".") && !formatted.contains(" ")) {
                "https://$formatted"
            } else {
                "https://www.google.com/search?q=${java.net.URLEncoder.encode(formatted, "UTF-8")}"
            }
        }

        repository.addRecentUrl(formatted)
        _uiState.update { it.copy(recentUrls = repository.getRecentUrls(), currentUrlInput = "") }

        webOsClient.openUrl(formatted)
    }

    fun launchBrowser() {
        triggerHaptic(true)
        webOsClient.launchBrowser()
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        triggerHaptic()
        webOsClient.sendText(text)
        _uiState.update { it.copy(currentTextInput = "") }
    }

    fun sendEnter() {
        triggerHaptic(true)
        webOsClient.sendEnter()
    }

    fun sendBackspace() {
        triggerHaptic()
        webOsClient.sendBackspace()
    }

    // Text we believe is currently in the focused TV input field (typed from this sheet).
    private var mirroredTvText = ""

    /**
     * Live-mirrors the phone text field into the focused TV input field (e.g. YouTube search):
     * the TV cursor sits at the end, so delete back to the common prefix and insert the rest.
     */
    private fun mirrorTextToTv(newText: String) {
        val old = mirroredTvText
        var prefix = old.commonPrefixWith(newText).length
        // Never split a surrogate pair (emoji) between the kept and the re-sent part.
        if (prefix > 0 && newText[prefix - 1].isHighSurrogate()) prefix--
        webOsClient.deleteCharacters(old.length - prefix)
        val added = newText.substring(prefix)
        if (added.isNotEmpty()) webOsClient.sendText(added)
        mirroredTvText = newText
    }

    /** Submits the mirrored text on the TV (Enter in the search field) and starts a fresh entry. */
    fun confirmTextInput() {
        triggerHaptic(true)
        val state = _uiState.value
        val text = state.currentTextInput
        if (state.isYouTubeOnScreen && state.tvTextFieldFocused != true) {
            // YouTube's search box doesn't accept remote text: open its search results directly.
            if (text.isNotBlank()) webOsClient.launchYouTubeSearch(text.trim(), state.foregroundAppId!!)
            mirroredTvText = ""
            _uiState.update { it.copy(currentTextInput = "", showKeyboardSheet = false) }
            return
        }
        mirrorTextToTv(text)
        webOsClient.sendEnter()
        mirroredTvText = ""
        _uiState.update { it.copy(currentTextInput = "") }
    }

    /** Clears both the phone field and the text it typed into the TV field. */
    fun clearTextInput() {
        triggerHaptic()
        mirrorTextToTv("")
        _uiState.update { it.copy(currentTextInput = "") }
    }

    fun textInputBackspace() {
        triggerHaptic()
        val current = _uiState.value.currentTextInput
        if (current.isEmpty()) {
            // Nothing typed from the phone: delete whatever is already in the TV field.
            webOsClient.sendBackspace()
        } else {
            updateTextInput(current.dropLast(if (current.length > 1 && current[current.length - 2].isHighSurrogate()) 2 else 1))
        }
    }

    fun pageUp() {
        triggerHaptic()
        // Emulate the remote's CH+ key (the foreground app decides what it does – the
        // browser pages up). ssap://tv/channelUp only changes the live-TV tuner channel.
        webOsClient.sendButton("CHANNELUP")
    }

    fun pageDown() {
        triggerHaptic()
        webOsClient.sendButton("CHANNELDOWN")
    }

    fun refreshPage() {
        triggerHaptic()
        // Sending RED color button or Enter in address bar refreshes webOS browser
        webOsClient.sendButton("RED")
    }

    fun powerOff() {
        triggerHaptic(true)
        suppressAutoUntil = System.currentTimeMillis() + POWER_OFF_GRACE_MS
        failedProbes = 0
        webOsClient.turnOff()
        _uiState.update { it.copy(showPowerDialog = false) }
    }

    // UI Dialog & Input Helpers
    fun updateUrlInput(input: String) {
        _uiState.update { it.copy(currentUrlInput = input) }
    }

    fun updateTextInput(input: String) {
        _uiState.update { it.copy(currentTextInput = input) }
        mirrorTextToTv(input)
    }

    fun setShowConnectionDialog(show: Boolean) {
        _uiState.update { it.copy(showConnectionDialog = show) }
    }

    fun setShowKeyboardSheet(show: Boolean) {
        // Each opening starts a new entry; we can't know what the TV field holds by now.
        mirroredTvText = ""
        _uiState.update { it.copy(showKeyboardSheet = show, currentTextInput = "") }
    }

    fun setShowPowerDialog(show: Boolean) {
        _uiState.update { it.copy(showPowerDialog = show) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        repository.setHapticEnabled(enabled)
        _uiState.update { it.copy(hapticEnabled = enabled) }
    }

    // WebOsEventListener Callbacks
    override fun onConnecting(ip: String) {
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.Connecting(ip)) }
    }

    override fun onPromptingUserOnTv() {
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.PromptingOnTv()) }
    }

    override fun onConnected(device: TvDevice, clientKey: String) {
        // Persist the key *and* the port that actually worked, so the next connect goes
        // straight to the right port instead of failing over every time.
        val existing = repository.getSavedDevices().find { it.ip == device.ip }
        repository.saveDevice(
            (existing ?: device).copy(port = device.port, clientKey = clientKey.ifBlank { null })
        )
        repository.setLastConnectedIp(device.ip)
        userAttemptPending = false
        autoAuthFailures = 0
        failedProbes = 0
        learnUuidIfMissing(device.ip)
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.Connected(device),
                activeDevice = device,
                savedDevices = repository.getSavedDevices()
            )
        }
    }

    override fun onConnectionFailed(error: String) {
        if (!userAttemptPending) {
            // A background attempt failed: keep quietly waiting for the TV instead of showing
            // a troubleshooting error. Network failures (TV still booting) are retried forever;
            // registration failures only twice, so a revoked key doesn't keep popping up the
            // pairing prompt on the TV.
            if (!isNetworkFailure(error)) autoAuthFailures++
            val device = _uiState.value.activeDevice ?: rememberedDevice()
            if (device != null && autoAuthFailures < MAX_AUTO_AUTH_FAILURES) {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.WaitingForTv(device.name)) }
                return
            }
        }
        userAttemptPending = false
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.Error(error)) }
    }

    private fun isNetworkFailure(error: String) = error.startsWith("Could not connect")

    override fun onDisconnected() {
        // The TV went away (switched off / Wi-Fi lost). Unless the user disconnected on
        // purpose, show that we're waiting for it; the supervisor reconnects when it's back.
        val device = _uiState.value.activeDevice ?: rememberedDevice()
        val status = if (!userDisconnected && device != null && !device.clientKey.isNullOrBlank()) {
            ConnectionStatus.WaitingForTv(device.name)
        } else ConnectionStatus.Disconnected
        _uiState.update { it.copy(connectionStatus = status, tvTextFieldFocused = null, foregroundAppId = null) }
    }

    override fun onForegroundAppChanged(appId: String) {
        _uiState.update { it.copy(foregroundAppId = appId.ifBlank { null }) }
    }

    override fun onTvTextFieldFocusChanged(focused: Boolean) {
        val wasFocused = _uiState.value.tvTextFieldFocused == true
        _uiState.update { it.copy(tvTextFieldFocused = focused) }
        if (focused && !wasFocused) {
            // A (new) field just got focus: it doesn't contain what we typed before, so
            // push whatever is already in the phone field into it.
            mirroredTvText = ""
            val pending = _uiState.value.currentTextInput
            if (pending.isNotEmpty()) mirrorTextToTv(pending)
        }
    }

    override fun onVolumeChanged(volume: Int, isMuted: Boolean) {
        _uiState.update { it.copy(isMuted = isMuted) }
    }

    override fun onCleared() {
        onAppBackground()
        webOsClient.shutdown()
        super.onCleared()
    }
}
