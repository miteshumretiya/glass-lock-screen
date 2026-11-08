package com.mitesh.glasslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Restarts the lock screen service after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Prefs(context).enabled && Settings.canDrawOverlays(context)) {
                    LockService.start(context)
                }
            }
        }
    }
}
