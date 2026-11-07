package com.example.model

data class TvDevice(
    val ip: String,
    val port: Int = 3001,
    val name: String = "LG webOS TV",
    val clientKey: String? = null,
    val mac: String? = null,
    /** UPnP UDN from SSDP; identifies the TV even after its IP address changes. */
    val uuid: String? = null,
    val isLastConnected: Boolean = false
)

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    data class Scanning(val message: String = "Scanning local network for LG webOS TVs...") : ConnectionStatus()
    data class Connecting(val ip: String) : ConnectionStatus()
    data class PromptingOnTv(val message: String = "Please click 'Allow' on your LG TV screen...") : ConnectionStatus()
    data class Connected(val device: TvDevice) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
    /** A paired TV is remembered but not reachable (switched off); the app reconnects as soon as it is. */
    data class WaitingForTv(val deviceName: String) : ConnectionStatus()
}

enum class RemoteMode {
    DPAD,
    TOUCHPAD
}

enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

data class WebShortcut(
    val id: String,
    val title: String,
    val url: String,
    val category: String = "Web"
)

data class RemoteUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val activeDevice: TvDevice? = null,
    val savedDevices: List<TvDevice> = emptyList(),
    val discoveredDevices: List<TvDevice> = emptyList(),
    // Reported by the TV's volume subscription
    val isMuted: Boolean = false,
    val remoteMode: RemoteMode = RemoteMode.DPAD,
    val recentUrls: List<String> = emptyList(),
    val shortcuts: List<WebShortcut> = defaultShortcuts,
    val currentUrlInput: String = "",
    val currentTextInput: String = "",
    val showConnectionDialog: Boolean = false,
    val showKeyboardSheet: Boolean = false,
    /** Whether a text field on the TV currently has keyboard focus (null = not reported yet). */
    val tvTextFieldFocused: Boolean? = null,
    /** App currently on the TV screen, if the TV reported it. */
    val foregroundAppId: String? = null,
    val showPowerDialog: Boolean = false,
    val hapticEnabled: Boolean = true
)

val defaultShortcuts = listOf(
    WebShortcut("google", "Google", "https://www.google.com", "Search"),
    WebShortcut("youtube", "YouTube", "https://www.youtube.com", "Video"),
    WebShortcut("reddit", "Reddit", "https://www.reddit.com", "Community"),
    WebShortcut("wikipedia", "Wikipedia", "https://www.wikipedia.org", "Reference"),
    WebShortcut("netflix", "Netflix", "https://www.netflix.com", "Streaming"),
    WebShortcut("twitch", "Twitch", "https://www.twitch.tv", "Live"),
    WebShortcut("github", "GitHub", "https://www.github.com", "Tech"),
    WebShortcut("news", "BBC News", "https://www.bbc.com/news", "News")
)

/** YouTube's own keyboard ignores remote text input, so searches go through a deep link instead. */
val RemoteUiState.isYouTubeOnScreen: Boolean
    get() = foregroundAppId?.startsWith("youtube", ignoreCase = true) == true
