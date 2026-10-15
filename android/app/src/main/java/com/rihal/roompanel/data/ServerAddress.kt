package com.rihal.roompanel.data

import android.content.Context
import androidx.core.content.edit
import com.rihal.roompanel.BuildConfig
import com.rihal.roompanel.domain.normalizeServerUrl

/** Where the portal is. Read on every request, so a move takes effect at once. */
interface ServerAddressStore {
    fun current(): String
    fun save(url: String)
}

/**
 * The portal address, set by the QR code at setup or by a verified move, else the address
 * the APK was built with. Not a secret; app-private prefs, excluded from backup like the token.
 */
class ServerAddress(context: Context) : ServerAddressStore {

    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    override fun current(): String = normalizeServerUrl(prefs.getString(KEY_URL, null)) ?: BuildConfig.BACKEND_URL

    /** Ignores anything that isn't a clean https address. */
    override fun save(url: String) {
        val clean = normalizeServerUrl(url) ?: return
        prefs.edit { putString(KEY_URL, clean) }
    }

    private companion object {
        const val KEY_URL = "url"
    }
}
