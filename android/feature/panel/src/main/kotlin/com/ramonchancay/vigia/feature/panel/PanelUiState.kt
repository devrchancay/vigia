package com.ramonchancay.vigia.feature.panel

import com.ramonchancay.vigia.core.model.PanelConfig

sealed interface PanelUiState {
    data object Loading : PanelUiState

    data class Ready(val config: PanelConfig) : PanelUiState

    data object Error : PanelUiState
}

sealed interface PanelAction {
    data object Retry : PanelAction
}
