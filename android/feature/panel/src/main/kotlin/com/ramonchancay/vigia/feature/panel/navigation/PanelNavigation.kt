package com.ramonchancay.vigia.feature.panel.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ramonchancay.vigia.feature.panel.PanelRoute
import kotlinx.serialization.Serializable

@Serializable
data object PanelDestination

fun NavGraphBuilder.panelScreen() {
    composable<PanelDestination> { PanelRoute() }
}
