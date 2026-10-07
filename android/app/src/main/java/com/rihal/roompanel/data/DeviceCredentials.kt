package com.rihal.roompanel.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The tablet's one secret: the device token from pairing. Encrypted with a non-exportable
 * AES-GCM key in the Android Keystore, stored in app-private prefs (excluded from backup and
 * device transfer). If the key is ever unusable the token reads as absent and the tablet
 * simply shows the pairing screen again.
 */
class DeviceCredentials(context: Context) {

    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    fun roomName(): String? = prefs.getString(KEY_ROOM, null)

    fun save(token: String, roomName: String) {
        prefs.edit { putString(KEY_TOKEN, encrypt(token)).putString(KEY_ROOM, roomName) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "room-panel-device-token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_TOKEN = "token"
        const val KEY_ROOM = "room"
    }
}
