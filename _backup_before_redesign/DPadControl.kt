package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Direction
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun DPadControl(
    modifier: Modifier = Modifier,
    onDirectionClick: (Direction) -> Unit,
    onOkClick: () -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation Disc
        Box(
            modifier = Modifier
                .size(240.dp)
                .shadow(16.dp, CircleShape, spotColor = ElectricCyan.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SurfaceContainerHigh,
                            SurfaceContainer,
                            Color(0xFF10141D)
                        )
                    )
                )
                .border(2.dp, SurfaceBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Directional Buttons around perimeter
            // UP
            DirectionQuadrant(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                testTag = "dpad_up",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                onClick = { onDirectionClick(Direction.UP) }
            )

            // DOWN
            DirectionQuadrant(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                testTag = "dpad_down",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                onClick = { onDirectionClick(Direction.DOWN) }
            )

            // LEFT
            DirectionQuadrant(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Left",
                testTag = "dpad_left",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
                onClick = { onDirectionClick(Direction.LEFT) }
            )

            // RIGHT
            DirectionQuadrant(
                icon = Icons.Default.KeyboardArrowRight,
                contentDescription = "Right",
                testTag = "dpad_right",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                onClick = { onDirectionClick(Direction.RIGHT) }
            )

            // Center OK Button
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E2838),
                                Color(0xFF0F1520)
                            )
                        )
                    )
                    .border(2.dp, ElectricCyan.copy(alpha = 0.8f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = ElectricCyan),
                        onClick = onOkClick
                    )
                    .testTag("dpad_ok"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = ElectricCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Aux Bar: Back, Home, Menu
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceContainer.copy(alpha = 0.8f))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuxButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back",
                testTag = "dpad_back",
                onClick = onBackClick
            )

            AuxButton(
                icon = Icons.Default.Home,
                label = "Home",
                testTag = "dpad_home",
                tint = ElectricCyan,
                onClick = onHomeClick
            )

            AuxButton(
                icon = Icons.Default.Menu,
                label = "Menu",
                testTag = "dpad_menu",
                onClick = onMenuClick
            )
        }
    }
}

@Composable
private fun DirectionQuadrant(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = ElectricCyan),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun AuxButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
