package com.ramonchancay.vigia.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.device.KioskController
import com.ramonchancay.vigia.core.server.ServerAddresses
import com.ramonchancay.vigia.core.server.security.ServerTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val tokenStore: ServerTokenStore,
    private val addresses: ServerAddresses,
    private val kiosk: KioskController,
    private val secrets: SecretStore
) : ViewModel() {
    /** Kiosk facts that are not observable (system settings, lock task); refreshed on [SetupAction.Refresh]. */
    private val kioskFacts = MutableStateFlow(KioskFacts(kiosk.state(), pinRequired = false, wrongPin = false))

    val uiState: StateFlow<SetupUiState> = combine(
        tokenStore.token.onStart { tokenStore.currentToken() }.filter { it.isNotEmpty() },
        flow { emit(addresses.baseUrls()) },
        kioskFacts
    ) { token, urls, facts ->
        SetupUiState.Ready(token, urls, facts.state, facts.pinRequired, facts.wrongPin)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

    private val events = Channel<SetupEvent>(Channel.BUFFERED)

    val effects: Flow<SetupEvent> = events.receiveAsFlow()

    init {
        onAction(SetupAction.Refresh)
    }

    fun onAction(action: SetupAction) {
        viewModelScope.launch {
            when (action) {
                SetupAction.RotateToken -> tokenStore.rotate()

                SetupAction.Done -> {
                    tokenStore.markSetupCompleted()
                    events.send(SetupEvent.Done)
                }

                SetupAction.Refresh -> refresh(wrongPin = false)

                is SetupAction.ExitKiosk -> if (pinMatches(action.pin)) {
                    events.send(SetupEvent.StopLockTask)
                    refresh(wrongPin = false)
                } else {
                    refresh(wrongPin = true)
                }

                is SetupAction.ReleaseDeviceOwner -> if (pinMatches(action.pin)) {
                    events.send(SetupEvent.StopLockTask)
                    kiosk.releaseDeviceOwner()
                    refresh(wrongPin = false)
                } else {
                    refresh(wrongPin = true)
                }
            }
        }
    }

    private suspend fun refresh(wrongPin: Boolean) {
        kioskFacts.value =
            KioskFacts(
                kiosk.state(),
                pinRequired = secrets.get(KioskController.PIN_SECRET) != null,
                wrongPin = wrongPin
            )
    }

    /** No PIN set means none is required; comparison is constant-time. */
    private suspend fun pinMatches(candidate: String): Boolean {
        val pin = secrets.get(KioskController.PIN_SECRET) ?: return true
        return MessageDigest.isEqual(pin.encodeToByteArray(), candidate.trim().encodeToByteArray())
    }

    private data class KioskFacts(
        val state: com.ramonchancay.vigia.core.device.KioskState,
        val pinRequired: Boolean,
        val wrongPin: Boolean
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
