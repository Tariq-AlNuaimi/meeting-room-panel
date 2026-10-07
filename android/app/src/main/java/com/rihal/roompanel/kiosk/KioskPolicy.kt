package com.rihal.roompanel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import com.rihal.roompanel.MainActivity

/**
 * Locks the tablet to this app. Every call is a no-op unless the app is Device Owner,
 * so a normally installed build (development, demos) behaves like an ordinary app.
 */
class KioskPolicy(context: Context) {

    private val app = context.applicationContext
    private val dpm = app.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(app, PanelDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(app.packageName)

    /** Idempotent: safe to call on every resume. */
    fun enter(activity: Activity) {
        if (!isDeviceOwner) return
        dpm.setLockTaskPackages(admin, arrayOf(app.packageName))
        // No home/recents/notifications/global-actions while pinned; power button still turns the screen off.
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        // Become the home screen, so the panel is what boots and what any crash returns to.
        dpm.addPersistentPreferredActivity(
            admin,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            ComponentName(app, MainActivity::class.java),
        )
        dpm.setKeyguardDisabled(admin, true)
        dpm.setStatusBarDisabled(admin, true)
        dpm.setGlobalSetting(
            admin,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            (BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString(),
        )
        val am = app.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) activity.startLockTask()
    }

    /**
     * Undo [enter]: unpins the app and restores the normal launcher, keyguard and status bar.
     * With [relinquishOwnership] the app also gives up Device Owner, after which it can be uninstalled.
     */
    fun release(relinquishOwnership: Boolean = false) {
        if (!isDeviceOwner) return
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.clearPackagePersistentPreferredActivities(admin, app.packageName)
        dpm.setKeyguardDisabled(admin, false)
        dpm.setStatusBarDisabled(admin, false)
        dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
        if (relinquishOwnership) dpm.clearDeviceOwnerApp(app.packageName)
    }
}
