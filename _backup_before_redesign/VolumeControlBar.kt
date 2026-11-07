package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.AccentInk
import com.example.ui.theme.PowerRed
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun VolumeControlBar(
    modifier: Modifier = Modifier,
    isMuted: Boolean,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onToggleMute: () -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Volume Pill (Vol-, speaker icon, Vol+)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceContainer)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(28.dp))
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Equal weights keep - and + the same size, with the icon exactly centered
                IconButton(
                    onClick = onVolumeDown,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("volume_down")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Volume Down",
                        tint = TextPrimary
                    )
                }

                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (isMuted) PowerRed else AccentInk,
                    modifier = Modifier.size(18.dp)
                )

                IconButton(
                    onClick = onVolumeUp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("volume_up")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Volume Up",
                        tint = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Mute Quick Button
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isMuted) PowerRed.copy(alpha = 0.2f) else SurfaceContainer)
                .border(
                    1.dp,
                    if (isMuted) PowerRed else SurfaceBorder,
                    CircleShape
                )
                .testTag("volume_mute")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) PowerRed else TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Web Page / Channel Navigation Pill (Page Up, Page Down)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceContainer)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(28.dp))
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Equal weights keep down and up the same size, with the label exactly centered
                IconButton(
                    onClick = onPageDown,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("page_down")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Page Down (Scroll Web Page)",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = "CH",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onPageUp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("page_up")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Page Up (Scroll Web Page)",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}
