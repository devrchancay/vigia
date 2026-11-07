package com.ramonchancay.vigia.core.device

import android.os.BatteryManager

/** Pure conversions from raw platform values, kept apart from Android APIs so they are unit-testable. */
internal object DeviceReadings {
    /** From ACTION_BATTERY_CHANGED extras. Temperature arrives in tenths of a degree Celsius. */
    fun battery(level: Int, scale: Int, temperatureTenths: Int, status: Int): BatteryStatus? {
        if (level < 0 || scale <= 0) return null
        return BatteryStatus(
            levelPercent = (level * 100 / scale).coerceIn(0, 100),
            temperatureCelsius = temperatureTenths / 10f,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        )
    }

    /** Buckets an RSSI in dBm into 0..4, the same thresholds Android's status bar uses. */
    fun signalLevel(rssiDbm: Int): Int = when {
        rssiDbm >= -55 -> 4
        rssiDbm >= -66 -> 3
        rssiDbm >= -77 -> 2
        rssiDbm >= -88 -> 1
        else -> 0
    }
}
