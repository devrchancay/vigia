package com.ramonchancay.vigia.core.designsystem.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * POST_NOTIFICATIONS state for Android 13+. Below API 33 the permission does not exist and is always granted.
 * Needed by the foreground service notification (milestone M2); the setup screen is the intended caller.
 */
@Stable
class NotificationPermissionState internal constructor(initiallyGranted: Boolean) {
    var isGranted by mutableStateOf(initiallyGranted)
        internal set

    internal var launcher: ManagedActivityResultLauncher<String, Boolean>? = null

    /** Shows the system dialog if the permission is not granted yet; no-op otherwise. */
    fun request() {
        if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun rememberNotificationPermissionState(onResult: (granted: Boolean) -> Unit = {}): NotificationPermissionState {
    val context = LocalContext.current
    val state = remember { NotificationPermissionState(context.hasNotificationPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.isGranted = granted
        onResult(granted)
    }
    SideEffect { state.launcher = launcher }
    return state
}

fun Context.hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED
