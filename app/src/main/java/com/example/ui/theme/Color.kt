package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------
// "Silver & graphite" remote palette
// Light brushed-aluminium background, dark graphite keys, green accent for status/power.
// ---------------------------------------------------------------------------------------

// Screen background (vertical gradient, top -> bottom)
val BgTop = Color(0xFFE4E6EA)
val BgBottom = Color(0xFFB4B8BE)

// Graphite key faces (vertical gradient, top -> bottom)
val KeyTop = Color(0xFF494A50)
val KeyBottom = Color(0xFF2A2B2F)
val KeyTopPressed = Color(0xFF55565D)
val KeyBottomPressed = Color(0xFF34353A)
val KeyRim = Color(0x1FFFFFFF)          // thin highlight around a key
val KeyIcon = Color(0xFFF2F3F5)
val KeyIconMuted = Color(0xFFA9ACB3)

// Trackpad (inner disc is darker on top, lighter at the bottom: looks recessed)
val PadTop = Color(0xFF242528)
val PadBottom = Color(0xFF3D3E43)
val PadEdge = Color(0xFF111214)

// Accents
val StatusGreen = Color(0xFF34B356)
val PowerRed = Color(0xFFD9433F)
val PowerRedGlow = Color(0x33D9433F)
val AmberYellow = Color(0xFFE0A800)
val DeepViolet = Color(0xFF6B4FD8)

// Ink used for primary actions/icons on light surfaces (dialogs, sheets, browser card)
val AccentInk = Color(0xFF2B2C30)
val AccentInkDim = Color(0xFF5B5E66)

// Light surfaces used by dialogs, sheets and the browser card
val AppBackground = Color(0xFFDDE0E4)
val SurfaceBase = Color(0xFFF2F3F5)
val SurfaceContainer = Color(0xFFF4F5F7)
val SurfaceContainerHigh = Color(0xFFE5E7EB)
val SurfaceBorder = Color(0xFFCDD0D6)

// Text
val TextPrimary = Color(0xFF1C1D20)
val TextSecondary = Color(0xFF50545B)
val TextMuted = Color(0xFF767A82)
