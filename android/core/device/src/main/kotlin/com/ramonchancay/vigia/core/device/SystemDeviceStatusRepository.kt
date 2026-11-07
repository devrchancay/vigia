package com.ramonchancay.vigia.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.getSystemService
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SystemDeviceStatusRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : DeviceStatusRepository {
    private val activityManager = context.getSystemService<ActivityManager>()
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    override fun status(intervalMillis: Long): Flow<DeviceStatus> = flow {
        while (true) {
            emit(DeviceStatus(battery = readBattery(), memory = readMemory(), wifi = readWifi()))
            delay(intervalMillis)
        }
    }.flowOn(ioDispatcher)

    private fun readBattery(): BatteryStatus? {
        // Sticky broadcast: registering a null receiver returns the last value without listening.
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return DeviceReadings.battery(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        )
    }

    private fun readMemory(): MemoryStatus {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)
        return MemoryStatus(availableBytes = info.availMem, totalBytes = info.totalMem)
    }

    private fun readWifi(): WifiStatus {
        val manager = connectivityManager ?: return WifiStatus.Disconnected
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return WifiStatus.Disconnected
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return WifiStatus.Disconnected
        val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        } else {
            legacyRssi()
        }
        return WifiStatus(isConnected = true, signalLevel = rssi?.let(DeviceReadings::signalLevel))
    }

    // Android 8–9 has no NetworkCapabilities.signalStrength; WifiManager.connectionInfo is the only source there.
    @Suppress("DEPRECATION")
    private fun legacyRssi(): Int? = context.applicationContext.getSystemService<WifiManager>()?.connectionInfo?.rssi
}
