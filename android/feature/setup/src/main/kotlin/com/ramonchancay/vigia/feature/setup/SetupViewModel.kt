package com.ramonchancay.vigia.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramonchancay.vigia.core.server.ServerAddresses
import com.ramonchancay.vigia.core.server.security.ServerTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
    private val addresses: ServerAddresses
) : ViewModel() {
    val uiState: StateFlow<SetupUiState> = combine(
        tokenStore.token.onStart { tokenStore.currentToken() }.filter { it.isNotEmpty() },
        flow { emit(addresses.baseUrls()) }
    ) { token, urls -> SetupUiState.Ready(token = token, baseUrls = urls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

    private val doneEvents = Channel<Unit>(Channel.BUFFERED)

    /** One-shot: the owner finished setup and the screen should leave. */
    val done: Flow<Unit> = doneEvents.receiveAsFlow()

    fun onAction(action: SetupAction) {
        viewModelScope.launch {
            when (action) {
                SetupAction.RotateToken -> tokenStore.rotate()

                SetupAction.Done -> {
                    tokenStore.markSetupCompleted()
                    doneEvents.send(Unit)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
