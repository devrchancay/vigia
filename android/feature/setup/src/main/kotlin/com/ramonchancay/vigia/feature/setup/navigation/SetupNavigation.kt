package com.ramonchancay.vigia.feature.setup.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ramonchancay.vigia.feature.setup.SetupRoute
import kotlinx.serialization.Serializable

@Serializable
data object SetupDestination

fun NavGraphBuilder.setupScreen(onDone: () -> Unit) {
    composable<SetupDestination> { SetupRoute(onDone = onDone) }
}
