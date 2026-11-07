package com.ramonchancay.vigia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ramonchancay.vigia.feature.panel.navigation.PanelDestination
import com.ramonchancay.vigia.feature.panel.navigation.panelScreen
import com.ramonchancay.vigia.feature.setup.navigation.SetupDestination
import com.ramonchancay.vigia.feature.setup.navigation.setupScreen

@Composable
fun VigiaNavHost(startDestination: Any, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        panelScreen(onOpenSetup = { navController.navigate(SetupDestination) { launchSingleTop = true } })
        setupScreen(
            onDone = {
                // The panel is the only screen left after setup; back from it exits the app.
                navController.navigate(PanelDestination) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        )
    }
}
