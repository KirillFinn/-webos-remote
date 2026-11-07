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
import com.example.model.SimulatedTvState
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
            isSimulationMode = repository.isSimulationMode(),
            recentUrls = repository.getRecentUrls(),
            hapticEnabled = repository.isHapticEnabled()
        )
    )
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Auto-connect to last connected device or initialize simulation if enabled
        val saved = repository.getSavedDevices()
        val lastIp = repository.getLastConnectedIp()
        val lastDevice = saved.find { it.ip == lastIp } ?: saved.firstOrNull()

        if (lastDevice != null && !repository.isSimulationMode()) {
            connectToTv(lastDevice)
        } else if (repository.isSimulationMode() || saved.isEmpty()) {
            // Default to friendly simulation so the user can test the remote immediately
            _uiState.update {
                it.copy(
                    isSimulationMode = true,
                    connectionStatus = ConnectionStatus.Connected(
                        TvDevice(ip = "192.168.1.55", name = "LG OLED C3 (Virtual)")
                    )
                )
            }
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
        if (mode == RemoteMode.TOUCHPAD && !_uiState.value.isSimulationMode) {
            webOsClient.ensurePointerSocket()
        }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        triggerHaptic(true)
        repository.setSimulationMode(enabled)
        if (enabled) {
            webOsClient.disconnect()
            _uiState.update {
                it.copy(
                    isSimulationMode = true,
                    connectionStatus = ConnectionStatus.Connected(
                        TvDevice(ip = "192.168.1.55", name = "LG OLED C3 (Virtual)")
                    )
                )
            }
        } else {
            val saved = repository.getSavedDevices()
            val lastDevice = saved.firstOrNull()
            if (lastDevice != null) {
                _uiState.update { it.copy(isSimulationMode = false) }
                connectToTv(lastDevice)
            } else {
                _uiState.update {
                    it.copy(
                        isSimulationMode = false,
                        connectionStatus = ConnectionStatus.Disconnected
                    )
                }
                startScan()
            }
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
                isSimulationMode = false,
                activeDevice = target,
                showConnectionDialog = false
            )
        }
        repository.setSimulationMode(false)
        repository.setLastConnectedIp(target.ip)
        if (forceFreshPairing) {
            repository.saveClientKey(target.ip, "")
        }
        repository.saveDevice(target)
        webOsClient.connect(target, forceFreshPairing)
    }

    fun connectManual(ip: String, port: Int = 3001, forceFreshPairing: Boolean = false) {
        val device = TvDevice(ip = ip.trim(), port = port, name = "LG webOS TV ($ip)")
        connectToTv(device, forceFreshPairing)
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

        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val delta = 0.08f
                val newX = when (direction) {
                    Direction.LEFT -> (tv.cursorX - delta).coerceIn(0.05f, 0.95f)
                    Direction.RIGHT -> (tv.cursorX + delta).coerceIn(0.05f, 0.95f)
                    else -> tv.cursorX
                }
                val newY = when (direction) {
                    Direction.UP -> (tv.cursorY - delta).coerceIn(0.05f, 0.95f)
                    Direction.DOWN -> (tv.cursorY + delta).coerceIn(0.05f, 0.95f)
                    else -> tv.cursorY
                }
                val scroll = when (direction) {
                    Direction.UP -> (tv.webPageScrollOffset - 150).coerceAtLeast(0)
                    Direction.DOWN -> tv.webPageScrollOffset + 150
                    else -> tv.webPageScrollOffset
                }
                state.copy(
                    simulatedTvState = tv.copy(
                        cursorX = newX,
                        cursorY = newY,
                        webPageScrollOffset = scroll,
                        lastAction = "D-Pad $name"
                    )
                )
            }
        } else {
            webOsClient.sendButton(name)
        }
    }

    fun sendOk() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Selected / Clicked",
                        lastToast = "Clicked item at pointer"
                    )
                )
            }
        } else {
            webOsClient.sendButton("ENTER")
        }
    }

    fun sendBack() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Back",
                        lastToast = "Navigated Back"
                    )
                )
            }
        } else {
            webOsClient.sendButton("BACK")
        }
    }

    fun sendHome() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        currentApp = "webOS Home",
                        isBrowserActive = false,
                        lastAction = "Home Screen",
                        lastToast = "LG webOS Home Menu"
                    )
                )
            }
        } else {
            webOsClient.sendButton("HOME")
        }
    }

    fun sendMenu() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Menu / Quick Settings"
                    )
                )
            }
        } else {
            webOsClient.sendButton("MENU")
        }
    }

    private var pointerAccumulatorX = 0f
    private var pointerAccumulatorY = 0f

    // Touchpad & Pointer
    fun sendPointerMove(dx: Float, dy: Float) {
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val sensitivity = 0.0025f
                val newX = (tv.cursorX + dx * sensitivity).coerceIn(0.02f, 0.98f)
                val newY = (tv.cursorY + dy * sensitivity).coerceIn(0.02f, 0.98f)
                state.copy(
                    simulatedTvState = tv.copy(
                        cursorX = newX,
                        cursorY = newY,
                        lastAction = "Magic Pointer Moved"
                    )
                )
            }
        } else {
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
    }

    fun sendPointerClick() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Pointer Click",
                        lastToast = "Clicked link on page"
                    )
                )
            }
        } else {
            webOsClient.sendPointerClick()
        }
    }

    fun sendScroll(dy: Float) {
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val newScroll = (tv.webPageScrollOffset + (dy * 3).toInt()).coerceAtLeast(0)
                state.copy(
                    simulatedTvState = tv.copy(
                        webPageScrollOffset = newScroll,
                        lastAction = if (dy > 0) "Scrolled Down" else "Scrolled Up"
                    )
                )
            }
        } else {
            webOsClient.sendScroll(dy.toInt())
        }
    }

    // Volume & Audio Controls
    fun volumeUp() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val newVol = (tv.volume + 1).coerceAtMost(100)
                state.copy(
                    simulatedTvState = tv.copy(
                        volume = newVol,
                        isMuted = false,
                        lastAction = "Volume Up: $newVol",
                        lastToast = "Volume: $newVol"
                    )
                )
            }
        } else {
            webOsClient.volumeUp()
        }
    }

    fun volumeDown() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val newVol = (tv.volume - 1).coerceAtLeast(0)
                state.copy(
                    simulatedTvState = tv.copy(
                        volume = newVol,
                        lastAction = "Volume Down: $newVol",
                        lastToast = "Volume: $newVol"
                    )
                )
            }
        } else {
            webOsClient.volumeDown()
        }
    }

    fun toggleMute() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val newMute = !tv.isMuted
                state.copy(
                    simulatedTvState = tv.copy(
                        isMuted = newMute,
                        lastAction = if (newMute) "Muted" else "Unmuted",
                        lastToast = if (newMute) "Audio Muted" else "Volume: ${tv.volume}"
                    )
                )
            }
        } else {
            webOsClient.setMute(true)
        }
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

        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        currentApp = "Web Browser",
                        currentUrl = formatted,
                        isBrowserActive = true,
                        webPageScrollOffset = 0,
                        lastAction = "Loaded $formatted",
                        lastToast = "Opening: $formatted"
                    )
                )
            }
        } else {
            webOsClient.openUrl(formatted)
        }
    }

    fun launchBrowser() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        currentApp = "Web Browser",
                        isBrowserActive = true,
                        lastAction = "Opened Web Browser",
                        lastToast = "LG webOS Browser launched"
                    )
                )
            }
        } else {
            webOsClient.launchBrowser()
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Typed: \"$text\"",
                        lastToast = "Input: \"$text\""
                    ),
                    currentTextInput = ""
                )
            }
        } else {
            webOsClient.sendText(text)
            _uiState.update { it.copy(currentTextInput = "") }
        }
    }

    fun sendEnter() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Pressed Enter / Submit"
                    )
                )
            }
        } else {
            webOsClient.sendEnter()
        }
    }

    fun sendBackspace() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        lastAction = "Pressed Backspace"
                    )
                )
            }
        } else {
            webOsClient.sendBackspace()
        }
    }

    fun pageUp() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val newScroll = (tv.webPageScrollOffset - 400).coerceAtLeast(0)
                state.copy(
                    simulatedTvState = tv.copy(
                        webPageScrollOffset = newScroll,
                        lastAction = "Page Up (Scroll)"
                    )
                )
            }
        } else {
            webOsClient.channelUp()
        }
    }

    fun pageDown() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val tv = state.simulatedTvState
                val newScroll = tv.webPageScrollOffset + 400
                state.copy(
                    simulatedTvState = tv.copy(
                        webPageScrollOffset = newScroll,
                        lastAction = "Page Down (Scroll)"
                    )
                )
            }
        } else {
            webOsClient.channelDown()
        }
    }

    fun refreshPage() {
        triggerHaptic()
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    simulatedTvState = state.simulatedTvState.copy(
                        webPageScrollOffset = 0,
                        lastAction = "Refreshed Page",
                        lastToast = "Reloading web page..."
                    )
                )
            }
        } else {
            // Sending RED color button or Enter in address bar refreshes webOS browser
            webOsClient.sendButton("RED")
        }
    }

    fun powerOff() {
        triggerHaptic(true)
        if (_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                val powered = !state.simulatedTvState.isPoweredOn
                state.copy(
                    showPowerDialog = false,
                    simulatedTvState = state.simulatedTvState.copy(
                        isPoweredOn = powered,
                        lastAction = if (powered) "TV Powered ON" else "TV Powered OFF",
                        lastToast = if (powered) "Power ON" else "Powering OFF..."
                    )
                )
            }
        } else {
            webOsClient.turnOff()
            _uiState.update { it.copy(showPowerDialog = false) }
        }
    }

    // UI Dialog & Input Helpers
    fun updateUrlInput(input: String) {
        _uiState.update { it.copy(currentUrlInput = input) }
    }

    fun updateTextInput(input: String) {
        _uiState.update { it.copy(currentTextInput = input) }
    }

    fun setShowConnectionDialog(show: Boolean) {
        _uiState.update { it.copy(showConnectionDialog = show) }
    }

    fun setShowKeyboardSheet(show: Boolean) {
        _uiState.update { it.copy(showKeyboardSheet = show) }
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
        repository.saveClientKey(device.ip, clientKey)
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
        _uiState.update { state ->
            state.copy(
                simulatedTvState = state.simulatedTvState.copy(
                    volume = volume,
                    isMuted = isMuted
                )
            )
        }
    }

    override fun onToastReceived(message: String) {
        _uiState.update { state ->
            state.copy(
                simulatedTvState = state.simulatedTvState.copy(
                    lastToast = message
                )
            )
        }
    }
}
