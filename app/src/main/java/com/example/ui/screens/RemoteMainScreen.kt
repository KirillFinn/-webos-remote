package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.RemoteMode
import com.example.model.RemoteUiState
import com.example.model.isYouTubeOnScreen
import com.example.ui.components.ConnectionDialog
import com.example.ui.components.IconKey
import com.example.ui.components.KeyHeight
import com.example.ui.components.NavigationPad
import com.example.ui.components.RemoteKey
import com.example.ui.components.RockerKey
import com.example.ui.components.TrackpadControl
import com.example.ui.components.VirtualKeyboardSheet
import com.example.ui.components.WebBrowserBar
import com.example.ui.theme.AccentInk
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.BgBottom
import com.example.ui.theme.BgTop
import com.example.ui.theme.KeyBottom
import com.example.ui.theme.KeyIcon
import com.example.ui.theme.KeyIconMuted
import com.example.ui.theme.KeyTop
import com.example.ui.theme.PowerRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.RemoteViewModel

private val KeyGap = 14.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMainScreen(
    viewModel: RemoteViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(
                uiState = uiState,
                onOpenConnection = { viewModel.setShowConnectionDialog(true) },
                onPower = { viewModel.setShowPowerDialog(true) },
                onToggleHaptics = { viewModel.setHapticEnabled(!uiState.hapticEnabled) }
            )

            // The remote body: same width for the trackpad and the key grid
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .widthIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))

                if (uiState.remoteMode == RemoteMode.TOUCHPAD) {
                    TrackpadControl(
                        onPointerMove = { dx, dy -> viewModel.sendPointerMove(dx, dy) },
                        onPointerClick = { viewModel.sendPointerClick() },
                        onScroll = { dy -> viewModel.sendScroll(dy) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    NavigationPad(
                        onDirection = { viewModel.sendDirection(it) },
                        onSelect = { viewModel.sendOk() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(18.dp))

                TrackpadModeToggle(
                    mode = uiState.remoteMode,
                    onModeChange = { viewModel.setRemoteMode(it) }
                )

                Spacer(Modifier.height(22.dp))

                KeyGrid(uiState = uiState, viewModel = viewModel)
            }

            Spacer(Modifier.height(32.dp))

            WebBrowserBar(
                urlInput = uiState.currentUrlInput,
                onUrlInputChange = { viewModel.updateUrlInput(it) },
                onOpenUrl = { viewModel.openUrl(it) },
                onLaunchBrowser = { viewModel.launchBrowser() },
                onRefreshPage = { viewModel.refreshPage() },
                onOpenKeyboardSheet = { viewModel.setShowKeyboardSheet(true) },
                shortcuts = uiState.shortcuts,
                recentUrls = uiState.recentUrls
            )

            Spacer(Modifier.height(24.dp))
        }

        // Connection Dialog Modal
        if (uiState.showConnectionDialog) {
            ConnectionDialog(
                connectionStatus = uiState.connectionStatus,
                savedDevices = uiState.savedDevices,
                discoveredDevices = uiState.discoveredDevices,
                onConnectDevice = { viewModel.connectToTv(it) },
                onConnectManual = { ip, port, fresh -> viewModel.connectManual(ip, port, fresh) },
                onClearKeysAndPairFresh = { viewModel.clearKeysAndPairFresh(it) },
                onScan = { viewModel.startScan() },
                onDisconnect = { viewModel.disconnect() },
                onDismiss = { viewModel.setShowConnectionDialog(false) },
                activeDeviceIp = uiState.activeDevice?.ip
            )
        }

        // Virtual Keyboard Modal Bottom Sheet
        if (uiState.showKeyboardSheet) {
            VirtualKeyboardSheet(
                textInput = uiState.currentTextInput,
                tvFieldFocused = uiState.tvTextFieldFocused,
                youTubeSearchMode = uiState.isYouTubeOnScreen && uiState.tvTextFieldFocused != true,
                onTextInputChange = { viewModel.updateTextInput(it) },
                onConfirm = { viewModel.confirmTextInput() },
                onClear = { viewModel.clearTextInput() },
                onBackspace = { viewModel.textInputBackspace() },
                onPageUp = { viewModel.pageUp() },
                onPageDown = { viewModel.pageDown() },
                onDismiss = { viewModel.setShowKeyboardSheet(false) },
                sheetState = keyboardSheetState
            )
        }

        // Power Off Dialog
        if (uiState.showPowerDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowPowerDialog(false) },
                containerColor = SurfaceContainer,
                title = {
                    Text("Turn off TV?", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "You will need to turn it back on with the TV's own remote or power button.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.powerOff() },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerRed),
                        modifier = Modifier.testTag("confirm_power_off")
                    ) {
                        Text("Turn Off", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowPowerDialog(false) }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Top bar: settings · device picker · power
// ---------------------------------------------------------------------------------------

@Composable
private fun TopBar(
    uiState: RemoteUiState,
    onOpenConnection: () -> Unit,
    onPower: () -> Unit,
    onToggleHaptics: () -> Unit
) {
    val status = uiState.connectionStatus
    val isConnected = status is ConnectionStatus.Connected
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Settings (left)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.testTag("settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = AccentInk,
                    modifier = Modifier.size(26.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("TV connection") },
                    leadingIcon = { Icon(Icons.Rounded.Cast, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenConnection() }
                )
                DropdownMenuItem(
                    text = { Text("Haptic feedback") },
                    trailingIcon = {
                        Checkbox(
                            checked = uiState.hapticEnabled,
                            onCheckedChange = { onToggleHaptics() },
                            colors = CheckboxDefaults.colors(checkedColor = AccentInk)
                        )
                    },
                    onClick = { onToggleHaptics() }
                )
            }
        }

        // Device picker (center)
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 200.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenConnection)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("connection_pill"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            is ConnectionStatus.Connected -> StatusGreen
                            is ConnectionStatus.Connecting,
                            is ConnectionStatus.PromptingOnTv,
                            is ConnectionStatus.Scanning,
                            is ConnectionStatus.WaitingForTv -> AmberYellow
                            else -> PowerRed
                        }
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = when (status) {
                    is ConnectionStatus.Connected -> status.device.name
                    is ConnectionStatus.Connecting -> "Connecting…"
                    is ConnectionStatus.PromptingOnTv -> "Accept on TV…"
                    is ConnectionStatus.Scanning -> "Searching…"
                    is ConnectionStatus.WaitingForTv -> "Waiting for TV…"
                    else -> "Not connected"
                },
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = "Choose TV",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Power (right)
        RemoteKey(
            onClick = onPower,
            contentDescription = "Power",
            shape = CircleShape,
            testTag = "header_power_btn",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(50.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = if (isConnected) StatusGreen else KeyIconMuted,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Keys / Trackpad toggle under the pad
// ---------------------------------------------------------------------------------------

@Composable
private fun TrackpadModeToggle(
    mode: RemoteMode,
    onModeChange: (RemoteMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.07f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeSegment("Keys", mode == RemoteMode.DPAD, "tab_dpad") { onModeChange(RemoteMode.DPAD) }
        ModeSegment("Trackpad", mode == RemoteMode.TOUCHPAD, "tab_touchpad") { onModeChange(RemoteMode.TOUCHPAD) }
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .then(
                if (selected) Modifier.background(Brush.verticalGradient(listOf(KeyTop, KeyBottom)))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) KeyIcon else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------------------------------------------------------------------------------------
// Key grid
//   Back       | Home
//   Play/Pause | VOL +
//   Keyboard   | VOL −
//   Mute       | CH ∨ ∧
// ---------------------------------------------------------------------------------------

@Composable
private fun KeyGrid(uiState: RemoteUiState, viewModel: RemoteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            IconKey(
                icon = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "Back",
                onClick = { viewModel.sendBack() },
                iconSize = 22.dp,
                testTag = "key_back",
                modifier = Modifier.weight(1f).height(KeyHeight)
            )
            IconKey(
                icon = Icons.Outlined.Tv,
                contentDescription = "Home",
                onClick = { viewModel.sendHome() },
                testTag = "key_home",
                modifier = Modifier.weight(1f).height(KeyHeight)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KeyGap)
            ) {
                RemoteKey(
                    onClick = { viewModel.togglePlayPause() },
                    contentDescription = "Play or pause",
                    testTag = "key_play_pause",
                    modifier = Modifier.fillMaxWidth().height(KeyHeight)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = KeyIcon, modifier = Modifier.size(22.dp))
                        Icon(Icons.Filled.Pause, contentDescription = null, tint = KeyIcon, modifier = Modifier.size(22.dp))
                    }
                }
                IconKey(
                    icon = Icons.Outlined.Keyboard,
                    contentDescription = "Keyboard",
                    onClick = { viewModel.setShowKeyboardSheet(true) },
                    testTag = "key_keyboard",
                    modifier = Modifier.fillMaxWidth().height(KeyHeight)
                )
            }
            RockerKey(
                firstIcon = Icons.Rounded.Add,
                firstDescription = "Volume up",
                onFirst = { viewModel.volumeUp() },
                secondIcon = Icons.Rounded.Remove,
                secondDescription = "Volume down",
                onSecond = { viewModel.volumeDown() },
                firstTestTag = "volume_up",
                secondTestTag = "volume_down",
                modifier = Modifier
                    .weight(1f)
                    .height(KeyHeight * 2 + KeyGap)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            IconKey(
                icon = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeMute,
                contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                onClick = { viewModel.toggleMute() },
                tint = if (uiState.isMuted) PowerRed else KeyIcon,
                testTag = "volume_mute",
                modifier = Modifier.weight(1f).height(KeyHeight)
            )
            RockerKey(
                firstIcon = Icons.Rounded.KeyboardArrowDown,
                firstDescription = "Channel / page down",
                onFirst = { viewModel.pageDown() },
                secondIcon = Icons.Rounded.KeyboardArrowUp,
                secondDescription = "Channel / page up",
                onSecond = { viewModel.pageUp() },
                vertical = false,
                label = "CH",
                firstTestTag = "page_down",
                secondTestTag = "page_up",
                modifier = Modifier.weight(1f).height(KeyHeight)
            )
        }
    }
}
