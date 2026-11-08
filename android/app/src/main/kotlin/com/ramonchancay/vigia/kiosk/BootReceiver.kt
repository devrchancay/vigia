package com.ramonchancay.vigia.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ramonchancay.vigia.core.server.VigiaServerService

/**
 * Starts the server (and its pollers) after a reboot or an app update, so the API answers even before the panel
 * is on screen. The panel itself comes up as the HOME app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED) VigiaServerService.start(context)
    }

    private companion object {
        val HANDLED = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
