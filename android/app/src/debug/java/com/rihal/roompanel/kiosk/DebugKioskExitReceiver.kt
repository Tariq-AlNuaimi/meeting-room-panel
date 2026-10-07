package com.rihal.roompanel.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only (src/debug): leave kiosk mode from a connected computer.
 *   adb shell am broadcast -n com.rihal.roompanel/.kiosk.DebugKioskExitReceiver -a com.rihal.roompanel.EXIT_KIOSK
 * Add `--ez relinquish true` to also drop Device Owner so the app can be uninstalled.
 */
class DebugKioskExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        KioskPolicy(context).release(relinquishOwnership = intent.getBooleanExtra("relinquish", false))
    }

    private companion object {
        const val ACTION = "com.rihal.roompanel.EXIT_KIOSK"
    }
}
