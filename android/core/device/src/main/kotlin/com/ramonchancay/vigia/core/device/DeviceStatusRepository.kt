package com.ramonchancay.vigia.core.device

import kotlinx.coroutines.flow.Flow

interface DeviceStatusRepository {
    /** Emits the current device status immediately and then every [intervalMillis]. */
    fun status(intervalMillis: Long = DEFAULT_INTERVAL_MILLIS): Flow<DeviceStatus>

    companion object {
        /** Battery, memory and Wi-Fi change slowly; polling keeps the cost flat on low-end phones. */
        const val DEFAULT_INTERVAL_MILLIS = 10_000L
    }
}
