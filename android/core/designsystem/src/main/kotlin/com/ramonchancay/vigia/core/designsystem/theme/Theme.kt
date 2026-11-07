package com.ramonchancay.vigia.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme. The panel passes [darkTheme] and [palette] from its config; screens outside the panel follow the
 * system. Dynamic color (Android 12+) is opt-in because the palette is part of the panel's identity.
 */
@Composable
fun VigiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: VigiaPalette = VigiaPalette.Ember,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        palette.colorScheme(darkTheme)
    }
    MaterialTheme(colorScheme = colorScheme, typography = VigiaTypography, content = content)
}
