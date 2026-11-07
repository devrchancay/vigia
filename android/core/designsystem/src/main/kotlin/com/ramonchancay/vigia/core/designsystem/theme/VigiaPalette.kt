package com.ramonchancay.vigia.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Neutrals shared by every palette: near-black ink for dark mode, warm paper for light mode.
private val Ink = Color(0xFF0E0F12)
private val InkRaised = Color(0xFF1A1C21)
private val InkText = Color(0xFFECE7E1)
private val InkMuted = Color(0xFFA7A29C)
private val Paper = Color(0xFFFBF8F5)
private val PaperRaised = Color(0xFFF1ECE6)
private val PaperMuted = Color(0xFF5C5751)

/**
 * Named palettes a config can pick (`theme.palette`). Each one only changes the accents, so every palette keeps
 * the same contrast on the panel. Inspired by the Fósil status UI.
 */
enum class VigiaPalette(
    val configName: String,
    private val darkAccent: Color,
    private val darkSecondary: Color,
    private val lightAccent: Color,
    private val lightSecondary: Color
) {
    Ember("ember", Color(0xFFFF8A3D), Color(0xFF3DD6C6), Color(0xFFB8541A), Color(0xFF00796E)),
    Teal("teal", Color(0xFF3DD6C6), Color(0xFFFFB86B), Color(0xFF00796E), Color(0xFFB8541A)),
    Indigo("indigo", Color(0xFF8C9EFF), Color(0xFFFF8A9A), Color(0xFF3949AB), Color(0xFFC2185B));

    internal fun colorScheme(dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = darkAccent,
            onPrimary = Ink,
            secondary = darkSecondary,
            onSecondary = Ink,
            background = Ink,
            onBackground = InkText,
            surface = Ink,
            onSurface = InkText,
            surfaceContainer = InkRaised,
            onSurfaceVariant = InkMuted
        )
    } else {
        lightColorScheme(
            primary = lightAccent,
            onPrimary = Color.White,
            secondary = lightSecondary,
            onSecondary = Color.White,
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceContainer = PaperRaised,
            onSurfaceVariant = PaperMuted
        )
    }

    companion object {
        val names: Set<String> = entries.map { it.configName }.toSet()

        /** Falls back to [Ember]; configs are validated, so an unknown name only reaches here from old data. */
        fun fromConfigName(name: String): VigiaPalette = entries.firstOrNull { it.configName == name } ?: Ember
    }
}
