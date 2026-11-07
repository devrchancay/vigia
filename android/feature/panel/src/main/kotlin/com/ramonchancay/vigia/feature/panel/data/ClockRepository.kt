package com.ramonchancay.vigia.feature.panel.data

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Source of the current time for the panel. Abstracted so the ViewModel can be tested with a fake clock. */
interface ClockRepository {
    /** Emits the current instant immediately and then at the start of every second. */
    fun ticks(): Flow<Instant>
}
