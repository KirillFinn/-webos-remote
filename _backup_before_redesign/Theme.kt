package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RemotePrimary,
    onPrimary = RemoteOnPrimary,
    primaryContainer = RemotePrimaryContainer,
    onPrimaryContainer = RemoteOnPrimaryContainer,
    secondary = RemoteSecondary,
    onSecondary = RemoteOnSecondary,
    secondaryContainer = RemoteSecondaryContainer,
    onSecondaryContainer = RemoteOnSecondaryContainer,
    background = RemoteBackground,
    onBackground = RemoteOnBackground,
    surface = RemoteSurface,
    onSurface = RemoteOnSurface,
    surfaceVariant = RemoteSurfaceVariant,
    onSurfaceVariant = RemoteOnSurfaceVariant,
    error = PowerRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
