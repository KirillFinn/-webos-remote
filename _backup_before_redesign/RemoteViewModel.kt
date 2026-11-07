package com.example.viewmodel

import android.app.Application
import android.content.Context
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
import com.example.network.SsdpDiscovery
import com.example.network.WebOsClient
import com.example.network.WebOsEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemoteViewModel(application: Application) : AndroidViewModel(application), WebOsEventListener {

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

    init {
        // Auto-connect to last connected device
        val saved = repository.getSavedDevices()
        val lastIp = repository.getLastConnectedIp()
        val lastDevice = saved.find { it.ip == lastIp } ?: saved.firstOrNull()

        if (lastDevice != null) {
            connectToTv(lastDevice)
        }
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
        val target = if (forceFreshPairing) device.copy(clientKey = null) else device
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
        val saved = repository.getSavedDevices()
        val lastIp = repository.getLastConnectedIp() ?: return
        val device = saved.find { it.ip == lastIp } ?: return
        _uiState.update { it.copy(activeDevice = device, connectionStatus = ConnectionStatus.Connecting(device.ip)) }
        webOsClient.connect(device)
    }

    fun clearKeysAndPairFresh(ip: String) {
        val device = _uiState.value.savedDevices.find { it.ip == ip }
            ?: TvDevice(ip = ip.trim(), port = 3001, name = "LG webOS TV ($ip)")
        connectToTv(device, forceFreshPairing = true)
    }

    fun disconnect() {
        triggerHaptic()
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

    fun sendMenu() {
        triggerHaptic()
        webOsClient.sendButton("MENU")
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
        mirrorTextToTv(_uiState.value.currentTextInput)
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
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.Connected(device),
                activeDevice = device,
                savedDevices = repository.getSavedDevices()
            )
        }
    }

    override fun onConnectionFailed(error: String) {
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.Error(error)) }
    }

    override fun onDisconnected() {
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnected) }
    }

    override fun onVolumeChanged(volume: Int, isMuted: Boolean) {
        _uiState.update { it.copy(isMuted = isMuted) }
    }

    override fun onCleared() {
        webOsClient.shutdown()
        super.onCleared()
    }
}
