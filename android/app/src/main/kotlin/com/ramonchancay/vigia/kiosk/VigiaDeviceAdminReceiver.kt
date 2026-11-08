package com.ramonchancay.vigia.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin component that makes Vigía device owner:
 * `adb shell dpm set-device-owner com.ramonchancay.vigia/.kiosk.VigiaDeviceAdminReceiver` (see docs/KIOSK.md).
 * Policies are applied by KioskController when the panel starts.
 */
class VigiaDeviceAdminReceiver : DeviceAdminReceiver()
