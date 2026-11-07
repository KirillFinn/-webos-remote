package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RemoteColorScheme = lightColorScheme(
    primary = AccentInk,
    onPrimary = Color.White,
    primaryContainer = SurfaceContainerHigh,
    onPrimaryContainer = TextPrimary,
    secondary = StatusGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF1E2),
    onSecondaryContainer = Color(0xFF0E3A1B),
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = SurfaceBorder,
    error = PowerRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RemoteColorScheme,
        typography = Typography,
        content = content
    )
}
