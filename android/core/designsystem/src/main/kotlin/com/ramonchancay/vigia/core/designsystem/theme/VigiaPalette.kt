package com.ramonchancay.vigia.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Ember" palette: warm amber accents on near-black, inspired by the Fósil status UI.
internal val Ember = Color(0xFFFF8A3D)
internal val EmberDark = Color(0xFFB8541A)
internal val Teal = Color(0xFF3DD6C6)
internal val Ink = Color(0xFF0E0F12)
internal val InkRaised = Color(0xFF1A1C21)
internal val Paper = Color(0xFFFBF8F5)
internal val PaperRaised = Color(0xFFF1ECE6)

internal val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    secondary = Teal,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFECE7E1),
    surface = Ink,
    onSurface = Color(0xFFECE7E1),
    surfaceContainer = InkRaised,
    onSurfaceVariant = Color(0xFFA7A29C)
)

internal val LightColors = lightColorScheme(
    primary = EmberDark,
    onPrimary = Color.White,
    secondary = Color(0xFF00796E),
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainer = PaperRaised,
    onSurfaceVariant = Color(0xFF5C5751)
)
