package com.rihal.roompanel.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rihal.roompanel.MainActivity

/** Device Owner entry point. Provisioning: docs/tablet-setup.md. */
class PanelDeviceAdminReceiver : DeviceAdminReceiver() {

    /** End of QR setup on Android 9, which has no [PolicyComplianceActivity] step: lock down and open the panel. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        KioskPolicy(context).configure()
        // Android 10+ finishes setup through PolicyComplianceActivity and then opens the home
        // screen, which is the panel; launching from here would land mid-wizard.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
