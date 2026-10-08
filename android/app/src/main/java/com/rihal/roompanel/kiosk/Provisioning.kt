package com.rihal.roompanel.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

/*
 * QR-code setup. A factory-reset tablet's setup wizard (tap the welcome screen 6 times) scans
 * the QR code from the admin portal, downloads this APK, makes it Device Owner and then asks
 * the two activities below what to do. Android 12+ refuses QR provisioning without them.
 * Both are guarded by BIND_DEVICE_ADMIN in the manifest, so only the system can start them.
 */

/** Answers "which mode?" with a fully managed device, the only mode a door panel uses. */
@RequiresApi(Build.VERSION_CODES.Q)
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK, provisioningModeResult())
        finish()
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun provisioningModeResult(): Intent =
    Intent().putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE)

/**
 * Runs once at the end of setup, now as Device Owner: applies the kiosk lock-down so the
 * wizard finishes straight into the panel (it is already the home screen).
 */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KioskPolicy(this).configure()
        setResult(RESULT_OK)
        finish()
    }
}
