package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.RemoteMode
import com.example.ui.components.ConnectionDialog
import com.example.ui.components.DPadControl
import com.example.ui.components.TouchpadControl
import com.example.ui.components.TvSimulatorCard
import com.example.ui.components.VirtualKeyboardSheet
import com.example.ui.components.VolumeControlBar
import com.example.ui.components.WebBrowserBar
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PowerRed
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMainScreen(
    viewModel: RemoteViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val keyboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        containerColor = ObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // TV Status Pill (Clickable to open connection dialog)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                        .clickable { viewModel.setShowConnectionDialog(true) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("connection_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (uiState.connectionStatus) {
                                    is ConnectionStatus.Connected -> NeonGreen
                                    is ConnectionStatus.Connecting,
                                    is ConnectionStatus.PromptingOnTv,
                                    is ConnectionStatus.Scanning -> ElectricCyan
                                    else -> if (uiState.isSimulationMode) NeonGreen else PowerRed
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (val s = uiState.connectionStatus) {
                            is ConnectionStatus.Connected -> s.device.name
                            is ConnectionStatus.Connecting -> "Connecting..."
                            is ConnectionStatus.PromptingOnTv -> "Pairing on TV..."
                            is ConnectionStatus.Scanning -> "Searching..."
                            else -> if (uiState.isSimulationMode) "LG OLED (Demo TV)" else "Disconnected"
                        },
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (uiState.connectionStatus is ConnectionStatus.Connected) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Connection Settings",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Power & Quick Keyboard Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setShowKeyboardSheet(true) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainer)
                            .border(1.dp, SurfaceBorder, CircleShape)
                            .testTag("header_keyboard_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Open Keyboard",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.setShowPowerDialog(true) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainer)
                            .border(1.dp, PowerRed.copy(alpha = 0.5f), CircleShape)
                            .testTag("header_power_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = PowerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // TV Live Screen Simulator Preview Card
            TvSimulatorCard(
                simulatedState = uiState.simulatedTvState,
                isSimulationMode = uiState.isSimulationMode,
                onToggleSimulation = { viewModel.toggleSimulationMode(it) },
                onOpenConnectionDialog = { viewModel.setShowConnectionDialog(true) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Remote Controller Mode Tabs (D-Pad vs Touchpad Magic Remote)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceContainer)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (uiState.remoteMode == RemoteMode.DPAD) ElectricCyan.copy(alpha = 0.2f) else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (uiState.remoteMode == RemoteMode.DPAD) ElectricCyan else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setRemoteMode(RemoteMode.DPAD) }
                            .padding(vertical = 8.dp)
                            .testTag("tab_dpad"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = if (uiState.remoteMode == RemoteMode.DPAD) ElectricCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "D-PAD CONTROL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.remoteMode == RemoteMode.DPAD) TextPrimary else TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (uiState.remoteMode == RemoteMode.TOUCHPAD) ElectricCyan.copy(alpha = 0.2f) else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (uiState.remoteMode == RemoteMode.TOUCHPAD) ElectricCyan else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setRemoteMode(RemoteMode.TOUCHPAD) }
                            .padding(vertical = 8.dp)
                            .testTag("tab_touchpad"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = if (uiState.remoteMode == RemoteMode.TOUCHPAD) ElectricCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TOUCHPAD POINTER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.remoteMode == RemoteMode.TOUCHPAD) TextPrimary else TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Controller Pane (D-Pad or Touchpad)
            if (uiState.remoteMode == RemoteMode.DPAD) {
                DPadControl(
                    onDirectionClick = { viewModel.sendDirection(it) },
                    onOkClick = { viewModel.sendOk() },
                    onBackClick = { viewModel.sendBack() },
                    onHomeClick = { viewModel.sendHome() },
                    onMenuClick = { viewModel.sendMenu() }
                )
            } else {
                TouchpadControl(
                    onPointerMove = { dx, dy -> viewModel.sendPointerMove(dx, dy) },
                    onPointerClick = { viewModel.sendPointerClick() },
                    onBackClick = { viewModel.sendBack() },
                    onHomeClick = { viewModel.sendHome() }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Volume & Page Up/Down Bar
            VolumeControlBar(
                volume = uiState.simulatedTvState.volume,
                isMuted = uiState.simulatedTvState.isMuted,
                onVolumeUp = { viewModel.volumeUp() },
                onVolumeDown = { viewModel.volumeDown() },
                onToggleMute = { viewModel.toggleMute() },
                onPageUp = { viewModel.pageUp() },
                onPageDown = { viewModel.pageDown() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Web Browser Navigation Bar (URL, Search, Bookmarks, and Shortcuts)
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

            Spacer(modifier = Modifier.height(24.dp))
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
                onDismiss = { viewModel.setShowConnectionDialog(false) }
            )
        }

        // Virtual Keyboard Modal Bottom Sheet
        if (uiState.showKeyboardSheet) {
            VirtualKeyboardSheet(
                textInput = uiState.currentTextInput,
                onTextInputChange = { viewModel.updateTextInput(it) },
                onSendText = { viewModel.sendText(it) },
                onSendEnter = { viewModel.sendEnter() },
                onSendBackspace = { viewModel.sendBackspace() },
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
                    Text("Power Control", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Are you sure you want to turn off the TV? You will need to turn it back on manually or via Magic Remote.",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.powerOff() },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerRed),
                        modifier = Modifier.testTag("confirm_power_off")
                    ) {
                        Text("Turn Off TV", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowPowerDialog(false) }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}
