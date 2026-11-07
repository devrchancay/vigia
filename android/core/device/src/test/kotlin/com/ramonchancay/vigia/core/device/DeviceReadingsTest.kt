package com.ramonchancay.vigia.core.device

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceReadingsTest {
    @Test
    fun `battery converts level, tenths of a degree and charging status`() {
        val battery = DeviceReadings.battery(
            level = 40,
            scale = 50,
            temperatureTenths = 315,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )

        assertEquals(BatteryStatus(levelPercent = 80, temperatureCelsius = 31.5f, isCharging = true), battery)
    }

    @Test
    fun `a full battery counts as charging and discharging does not`() {
        assertTrue(DeviceReadings.battery(100, 100, 300, BatteryManager.BATTERY_STATUS_FULL)!!.isCharging)
        assertFalse(DeviceReadings.battery(50, 100, 300, BatteryManager.BATTERY_STATUS_DISCHARGING)!!.isCharging)
    }

    @Test
    fun `battery is unknown without level or scale`() {
        assertNull(DeviceReadings.battery(level = -1, scale = 100, temperatureTenths = 0, status = 0))
        assertNull(DeviceReadings.battery(level = 50, scale = 0, temperatureTenths = 0, status = 0))
    }

    @Test
    fun `battery is hot from 40 degrees`() {
        assertFalse(BatteryStatus(50, 39.9f, isCharging = true).isHot)
        assertTrue(BatteryStatus(50, 40f, isCharging = true).isHot)
    }

    @Test
    fun `signal level buckets rssi`() {
        assertEquals(
            listOf(4, 3, 2, 1, 0),
            listOf(-50, -60, -70, -85, -95).map(DeviceReadings::signalLevel)
        )
    }

    @Test
    fun `used memory never goes negative`() {
        assertEquals(0L, MemoryStatus(availableBytes = 10, totalBytes = 5).usedBytes)
        assertEquals(3L, MemoryStatus(availableBytes = 2, totalBytes = 5).usedBytes)
    }
}
