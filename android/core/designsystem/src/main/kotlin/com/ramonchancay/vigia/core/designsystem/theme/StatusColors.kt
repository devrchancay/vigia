package com.ramonchancay.vigia.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Traffic-light colors for source status. Fixed across palettes so green/amber/red always mean the same thing
 * on the panel; chosen to read on both the dark and the light background.
 */
object StatusColors {
    val Ok = Color(0xFF3FBF7F)
    val Warn = Color(0xFFF2A93B)
    val Error = Color(0xFFE5484D)
    val Unknown = Color(0xFF8A8F98)
}
