package com.ramonchancay.vigia.feature.panel.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ramonchancay.vigia.feature.panel.PanelRoute
import kotlinx.serialization.Serializable

@Serializable
data object PanelDestination

/** [onOpenSetup] runs on a long press anywhere on the panel (the way back to the setup screen). */
fun NavGraphBuilder.panelScreen(onOpenSetup: () -> Unit) {
    composable<PanelDestination> { PanelRoute(onOpenSetup = onOpenSetup) }
}
