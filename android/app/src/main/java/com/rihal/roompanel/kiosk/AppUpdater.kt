package com.rihal.roompanel.kiosk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.rihal.roompanel.data.api.AppUpdateDto
import com.rihal.roompanel.data.api.AppUpdateEnvelopeDto
import com.rihal.roompanel.data.api.BackendClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Pure rules, unit-tested: install only a strictly newer version from an https URL with a well-formed hash. */
object UpdatePolicy {
    fun shouldInstall(currentVersionCode: Long, update: AppUpdateDto?): Boolean =
        update != null &&
            update.versionCode > currentVersionCode &&
            update.url.startsWith("https://") &&
            Regex("^[0-9a-f]{64}$").matches(update.sha256.lowercase())

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Silent self-update for Device Owner tablets: ask the backend which APK to run, download it,
 * verify its SHA-256, then install through PackageInstaller (no prompt as Device Owner).
 * Android itself refuses an APK signed with a different key than the installed app.
 * The app is restarted afterwards by [PackageReplacedReceiver].
 */
class AppUpdater(
    private val context: Context,
    private val client: BackendClient,
    private val currentVersionCode: Long,
) {
    /** Returns true if an install was started. Never throws for expected failures. */
    suspend fun checkAndInstall(): Boolean = withContext(Dispatchers.IO) {
        if (!KioskPolicy(context).isDeviceOwner) return@withContext false
        val update = runCatching { client.get("/api/device/app-update", AppUpdateEnvelopeDto.serializer()).update }
            .getOrNull()
        if (update == null || !UpdatePolicy.shouldInstall(currentVersionCode, update)) return@withContext false

        val apk = File(context.cacheDir, "update.apk")
        try {
            download(update.url, apk)
            val actual = apk.inputStream().use(UpdatePolicy::sha256Hex)
            if (actual != update.sha256.lowercase()) return@withContext false
            install(apk)
            true
        } catch (e: java.io.IOException) {
            false
        } finally {
            if (apk.exists()) apk.delete()
        }
    }

    private fun download(url: String, target: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
    }
}
