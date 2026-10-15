package com.rihal.roompanel.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProvisioningTest {

    @Test
    fun provisioningModeIsFullyManaged() {
        val activity = Robolectric.buildActivity(ProvisioningModeActivity::class.java).setup().get()
        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        assertEquals(
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
            shadow.resultIntent.getIntExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, -1),
        )
        assertTrue(activity.isFinishing)
    }

    @Test
    fun policyComplianceSucceedsAndFinishes() {
        // Not Device Owner under Robolectric, so configure() is a no-op; setup must still continue.
        val activity = Robolectric.buildActivity(PolicyComplianceActivity::class.java).setup().get()
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun setupRemembersThePortalFromTheQrCode() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val intent = android.content.Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
            android.os.PersistableBundle().apply { putString(SERVER_URL_EXTRA, "https://Rooms.Example.com/") },
        )
        Robolectric.buildActivity(PolicyComplianceActivity::class.java, intent).setup()
        assertEquals("https://rooms.example.com", com.rihal.roompanel.data.ServerAddress(ctx).current())
    }

    @Test
    fun anUnsafeQrAddressIsIgnored() {
        val intent = android.content.Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
            android.os.PersistableBundle().apply { putString(SERVER_URL_EXTRA, "http://evil.example.com") },
        )
        assertEquals(null, provisionedServerUrl(intent))
        assertEquals(null, provisionedServerUrl(null))
    }
}
