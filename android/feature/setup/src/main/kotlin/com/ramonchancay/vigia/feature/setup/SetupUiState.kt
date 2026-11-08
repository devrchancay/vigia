package com.ramonchancay.vigia.feature.setup

import com.ramonchancay.vigia.core.device.KioskState

sealed interface SetupUiState {
    data object Loading : SetupUiState

    /**
     * [baseUrls] is Tailscale first; empty when the phone has no network. [pinRequired] is false when no
     * `kiosk-pin` secret is set, in which case leaving kiosk mode needs no PIN.
     */
    data class Ready(
        val token: String,
        val baseUrls: List<String>,
        val kiosk: KioskState = KioskState(isDeviceOwner = false, isDefaultHome = false, isLockTaskActive = false),
        val pinRequired: Boolean = false,
        val wrongPin: Boolean = false
    ) : SetupUiState {
        val pairingUri: String? get() = baseUrls.firstOrNull()?.let { PairingQr.uri(it, token) }
    }
}

sealed interface SetupAction {
    data object RotateToken : SetupAction

    data object Done : SetupAction

    /** Re-reads kiosk state (the owner may have changed the home app in system settings). */
    data object Refresh : SetupAction

    data class ExitKiosk(val pin: String) : SetupAction

    data class ReleaseDeviceOwner(val pin: String) : SetupAction
}

/** One-shot effects the screen performs with its Activity. */
sealed interface SetupEvent {
    data object Done : SetupEvent

    data object StopLockTask : SetupEvent
}
