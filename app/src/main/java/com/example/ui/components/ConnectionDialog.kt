package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.TvDevice
import com.example.ui.theme.AccentInk
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.PowerRed
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ConnectionDialog(
    connectionStatus: ConnectionStatus,
    savedDevices: List<TvDevice>,
    discoveredDevices: List<TvDevice>,
    onConnectDevice: (TvDevice) -> Unit,
    onConnectManual: (ip: String, port: Int, forceFreshPairing: Boolean) -> Unit,
    onClearKeysAndPairFresh: (ip: String) -> Unit,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    activeDeviceIp: String? = null
) {
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("3001") }
    var forceFreshPairing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainer,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = AccentInk,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect LG webOS TV",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Connection Status Notice
                when (connectionStatus) {
                    is ConnectionStatus.Connecting -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainerHigh)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentInk,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Connecting to TV...",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = connectionStatus.ip,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is ConnectionStatus.PromptingOnTv -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentInk.copy(alpha = 0.15f))
                                .border(1.dp, AccentInk, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = AccentInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Pairing Prompt on TV",
                                        color = AccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Please grab your TV remote and click 'Allow' or 'Yes' on your TV screen to authorize this connection.",
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is ConnectionStatus.Connected -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StatusGreen.copy(alpha = 0.15f))
                                .border(1.dp, StatusGreen, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = connectionStatus.device.name,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${connectionStatus.device.ip}:${connectionStatus.device.port}",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            TextButton(onClick = onDisconnect) {
                                Text("Disconnect", color = PowerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is ConnectionStatus.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PowerRed.copy(alpha = 0.12f))
                                .border(1.dp, PowerRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = connectionStatus.message,
                                    color = PowerRed,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            // Re-pair the TV that just failed, not whichever device is listed first.
                                            val targetIp = manualIp.ifBlank {
                                                activeDeviceIp
                                                    ?: discoveredDevices.firstOrNull()?.ip
                                                    ?: savedDevices.firstOrNull()?.ip
                                                    ?: ""
                                            }
                                            if (targetIp.isNotBlank()) {
                                                onClearKeysAndPairFresh(targetIp)
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = SurfaceContainerHigh,
                                            contentColor = AccentInk
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Clear Key & Pair Fresh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is ConnectionStatus.WaitingForTv -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainerHigh)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = AccentInk,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = connectionStatus.deviceName,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Paired. Connects automatically when the TV is switched on.",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                            TextButton(onClick = onDisconnect) {
                                Text("Stop", color = PowerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    else -> {}
                }

                // Discovered / Saved Devices Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DISCOVERED ON WI-FI",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    IconButton(onClick = onScan, modifier = Modifier.size(28.dp).testTag("scan_wifi_btn")) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan", tint = AccentInk, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val allDevices = (discoveredDevices + savedDevices).distinctBy { it.ip }

                if (allDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No TV discovered automatically.\nEnter IP manually below.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(115.dp)) {
                        items(allDevices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerHigh)
                                    .clickable {
                                        manualIp = device.ip
                                        onConnectDevice(device)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = AccentInk,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = device.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "${device.ip} (Auto 3001/3000)", color = TextMuted, fontSize = 10.sp)
                                    }
                                }
                                Text("Connect", color = AccentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Manual IP input
                Text(
                    text = "MANUAL IP CONNECTION",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        placeholder = { Text("e.g. 192.168.3.30", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentInk,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceContainerHigh,
                            unfocusedContainerColor = SurfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(50.dp).testTag("manual_ip_input")
                    )

                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                onConnectManual(manualIp, manualPort.toIntOrNull() ?: 3001, forceFreshPairing)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentInk,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp).testTag("manual_connect_button")
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { forceFreshPairing = !forceFreshPairing }
                ) {
                    Checkbox(
                        checked = forceFreshPairing,
                        onCheckedChange = { forceFreshPairing = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentInk)
                    )
                    Text(
                        text = "Pair as new device (Request prompt on TV)",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Important TV setup hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentInk,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LG TV Setting: Ensure 'LG Connect Apps' or 'TV On With Mobile' is turned ON in Settings > General > Devices > External Devices.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AccentInk, fontWeight = FontWeight.Bold)
            }
        }
    )
}
