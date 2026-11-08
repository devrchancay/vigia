package com.ramonchancay.vigia.feature.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramonchancay.vigia.core.common.result.Result
import com.ramonchancay.vigia.core.common.result.asResult
import com.ramonchancay.vigia.core.data.config.PanelConfigRepository
import com.ramonchancay.vigia.core.data.events.EventRepository
import com.ramonchancay.vigia.core.data.status.StatusRepository
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.device.DeviceStatusRepository
import com.ramonchancay.vigia.core.model.StoredEvent
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import com.ramonchancay.vigia.feature.panel.data.ClockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@HiltViewModel
class PanelViewModel @Inject constructor(
    configRepository: PanelConfigRepository,
    clockRepository: ClockRepository,
    deviceStatusRepository: DeviceStatusRepository,
    statusRepository: StatusRepository,
    eventRepository: EventRepository
) : ViewModel() {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The layout. Changes only when the config changes, so the grid does not recompose every second. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PanelUiState> = retries
        .onSubscription { emit(Unit) }
        .flatMapLatest { configRepository.config.asResult() }
        .map { result ->
            when (result) {
                Result.Loading -> PanelUiState.Loading

                is Result.Success -> PanelUiState.Ready(result.data)

                is Result.Error -> {
                    Timber.e(result.exception, "Panel config failed to load")
                    PanelUiState.Error
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PanelUiState.Loading)

    /** Current time, kept apart from [uiState] so only time-based widgets recompose on each tick. */
    val now: StateFlow<Instant?> = clockRepository.ticks()
        .catch { Timber.e(it, "Panel clock failed") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Battery, memory and Wi-Fi; polled only while the panel is visible. */
    val device: StateFlow<DeviceStatus?> = deviceStatusRepository.status()
        .catch { Timber.e(it, "Device status failed") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Latest uptime check per URL, written by the pollers in the always-on service. */
    val uptime: StateFlow<Map<String, UptimeStatus>> = statusRepository.uptime
        .catch { Timber.e(it, "Uptime status failed") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    /** Latest GitHub Actions run per source key. */
    val workflows: StateFlow<Map<String, WorkflowRunStatus>> = statusRepository.workflows
        .catch { Timber.e(it, "Workflow status failed") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    /** Pushed events, newest first; widgets drop expired ones with the panel clock. */
    val events: StateFlow<List<StoredEvent>> = eventRepository.events
        .catch { Timber.e(it, "Events failed") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun onAction(action: PanelAction) {
        when (action) {
            PanelAction.Retry -> retries.tryEmit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
