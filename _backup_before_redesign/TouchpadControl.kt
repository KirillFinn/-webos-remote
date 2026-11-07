package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun TouchpadControl(
    modifier: Modifier = Modifier,
    onPointerMove: (dx: Float, dy: Float) -> Unit,
    onPointerClick: () -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Full-Width Seamless Touchpad Box (Scroll strip completely removed per user request)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceContainerHigh,
                            Color(0xFF101520)
                        )
                    )
                )
                .border(1.5.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDrag = Offset.Zero
                        var isDragging = false
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                // Finger lifted
                                if (!isDragging) {
                                    onPointerClick()
                                }
                                break
                            }

                            val dragDelta = change.positionChange()
                            totalDrag += dragDelta

                            if (!isDragging && totalDrag.getDistance() > touchSlop) {
                                isDragging = true
                            }

                            if (isDragging && (dragDelta.x != 0f || dragDelta.y != 0f)) {
                                change.consume()
                                onPointerMove(dragDelta.x, dragDelta.y)
                            }
                        }
                    }
                }
                .testTag("touchpad_surface"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ElectricCyan.copy(alpha = 0.12f))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Swipe to steer pointer, tap to click",
                        tint = ElectricCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Magic Pointer Touchpad",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Glide finger to move cursor • Tap to click",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action bar: Back, Direct Click Button, Home
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceContainer)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp).testTag("touchpad_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            FilledTonalButton(
                onClick = onPointerClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = ElectricCyan.copy(alpha = 0.2f),
                    contentColor = ElectricCyan
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("touchpad_click_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AdsClick,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("CLICK", fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onHomeClick,
                modifier = Modifier.size(48.dp).testTag("touchpad_home")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = ElectricCyan
                )
            }
        }
    }
}
