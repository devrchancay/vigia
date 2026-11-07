package com.ramonchancay.vigia.feature.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramonchancay.vigia.core.common.result.Result
import com.ramonchancay.vigia.core.common.result.asResult
import com.ramonchancay.vigia.feature.panel.data.ClockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@HiltViewModel
class PanelViewModel @Inject constructor(clockRepository: ClockRepository) : ViewModel() {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PanelUiState> = retries
        .onSubscription { emit(Unit) }
        .flatMapLatest { clockRepository.ticks().asResult() }
        .map { result ->
            when (result) {
                Result.Loading -> PanelUiState.Loading

                is Result.Success -> PanelUiState.Ready(result.data)

                is Result.Error -> {
                    Timber.e(result.exception, "Panel clock failed")
                    PanelUiState.Error
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PanelUiState.Loading
        )

    fun onAction(action: PanelAction) {
        when (action) {
            PanelAction.Retry -> retries.tryEmit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
