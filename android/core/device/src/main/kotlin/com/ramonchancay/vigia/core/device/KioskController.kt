package com.ramonchancay.vigia.core.device

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** What the setup screen shows about kiosk mode. */
data class KioskState(val isDeviceOwner: Boolean, val isDefaultHome: Boolean, val isLockTaskActive: Boolean)

/**
 * Kiosk mode (docs/SPEC.md, M6). Without device owner the app still starts on boot and can be the default
 * launcher; as device owner it also pins itself (lock task), becomes the persistent HOME, hides the status bar
 * and keeps the screen on while plugged in, so OEM skins such as MIUI cannot kill or leave it.
 */
@Singleton
class KioskController @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val policy = context.getSystemService<DevicePolicyManager>()
    private val activities = context.getSystemService<ActivityManager>()
    private val admin = ComponentName(context, ADMIN_RECEIVER)

    val isDeviceOwner: Boolean get() = policy?.isDeviceOwnerApp(context.packageName) == true

    fun state(): KioskState = KioskState(
        isDeviceOwner = isDeviceOwner,
        isDefaultHome = isDefaultHome(),
        isLockTaskActive = activities?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    )

    /** Applies the device-owner policies; a no-op otherwise. Safe to call on every launch. */
    fun applyDeviceOwnerPolicies(panelActivity: ComponentName) {
        val dpm = policy ?: return
        if (!isDeviceOwner) return
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, panelActivity)
            dpm.setStatusBarDisabled(admin, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dpm.setKeyguardDisabled(admin, true)
            val pluggedIn = BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, pluggedIn.toString())
        }.onFailure { Timber.e(it, "Applying kiosk policies failed") }
    }

    /**
     * Gives the phone back: clears the policies and the device owner. After this the app is a normal app
     * again; making it device owner needs adb (see docs/KIOSK.md).
     */
    fun releaseDeviceOwner() {
        val dpm = policy ?: return
        if (!isDeviceOwner) return
        runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            dpm.setStatusBarDisabled(admin, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dpm.setKeyguardDisabled(admin, false)
            dpm.setLockTaskPackages(admin, emptyArray())
            @Suppress("DEPRECATION") // The only way for an owner to step down; replacements need a new owner.
            dpm.clearDeviceOwnerApp(context.packageName)
        }.onFailure { Timber.e(it, "Releasing device owner failed") }
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    companion object {
        /** Fully qualified so it matches the adb command in docs/KIOSK.md for every build type. */
        const val ADMIN_RECEIVER = "com.ramonchancay.vigia.kiosk.VigiaDeviceAdminReceiver"

        /** Secret holding the PIN that must be typed on the device to leave kiosk mode. */
        const val PIN_SECRET = "kiosk-pin"
    }
}
