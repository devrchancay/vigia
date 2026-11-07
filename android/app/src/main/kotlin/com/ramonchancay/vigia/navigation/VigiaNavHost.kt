package com.ramonchancay.vigia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ramonchancay.vigia.feature.panel.navigation.PanelDestination
import com.ramonchancay.vigia.feature.panel.navigation.panelScreen

@Composable
fun VigiaNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = PanelDestination,
        modifier = modifier
    ) {
        panelScreen()
    }
}
