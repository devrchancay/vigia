package com.ramonchancay.vigia.core.device

/** Snapshot of the phone's own health, shown by the `device` widget. */
data class DeviceStatus(val battery: BatteryStatus?, val memory: MemoryStatus, val wifi: WifiStatus)

data class BatteryStatus(val levelPercent: Int, val temperatureCelsius: Float, val isCharging: Boolean) {
    /** Above this the battery is at risk when charging 24/7 (see docs/CONTEXT.md, battery risk). */
    val isHot: Boolean get() = temperatureCelsius >= HOT_CELSIUS

    companion object {
        const val HOT_CELSIUS = 40f
    }
}

data class MemoryStatus(val availableBytes: Long, val totalBytes: Long) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
}

/** [signalLevel] goes from 0 (worst) to [MAX_SIGNAL_LEVEL]; null when not connected to Wi-Fi. */
data class WifiStatus(val isConnected: Boolean, val signalLevel: Int?) {
    companion object {
        const val MAX_SIGNAL_LEVEL = 4
        val Disconnected = WifiStatus(isConnected = false, signalLevel = null)
    }
}
