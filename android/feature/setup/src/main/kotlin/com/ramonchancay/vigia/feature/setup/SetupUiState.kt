package com.ramonchancay.vigia.feature.setup

sealed interface SetupUiState {
    data object Loading : SetupUiState

    /** [baseUrls] is Tailscale first; empty when the phone has no network. */
    data class Ready(val token: String, val baseUrls: List<String>) : SetupUiState {
        val pairingUri: String? get() = baseUrls.firstOrNull()?.let { PairingQr.uri(it, token) }
    }
}

sealed interface SetupAction {
    data object RotateToken : SetupAction

    data object Done : SetupAction
}
