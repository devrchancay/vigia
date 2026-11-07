package com.ramonchancay.vigia.feature.panel

import java.time.Instant

sealed interface PanelUiState {
    data object Loading : PanelUiState

    data class Ready(val now: Instant) : PanelUiState

    data object Error : PanelUiState
}

sealed interface PanelAction {
    data object Retry : PanelAction
}
