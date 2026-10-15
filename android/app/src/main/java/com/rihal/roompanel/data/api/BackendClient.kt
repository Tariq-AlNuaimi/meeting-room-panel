package com.rihal.roompanel.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A request the backend answered with an error. [code] is the backend's stable machine code. */
class ApiException(val status: Int, val code: String?, message: String) : Exception(message)

/**
 * Minimal JSON-over-HTTPS client for the backend's `/api/device/...` routes. No Microsoft
 * credentials ever pass through here: the device token is the only secret, sent as a bearer.
 */
class BackendClient(
    private val baseUrl: String,
    private val appVersion: String,
    private val tokenProvider: () -> String?,
) {
    suspend fun <T> get(path: String, serializer: KSerializer<T>): T = call("GET", path, null, serializer)

    suspend fun <T> post(path: String, body: String?, serializer: KSerializer<T>): T = call("POST", path, body, serializer)

    /** Raw bytes (branding images), at most [maxBytes]. */
    suspend fun getBytes(path: String, maxBytes: Int = 2 * 1024 * 1024): ByteArray = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("X-App-Version", appVersion)
            tokenProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            val status = conn.responseCode
            if (status !in 200..299) throw ApiException(status, null, "HTTP $status")
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw ApiException(status, "too_large", "Image too large")
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun <T> call(method: String, path: String, body: String?, serializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-App-Version", appVersion)
                tokenProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                body?.let { conn.outputStream.use { os -> os.write(it.toByteArray()) } }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val envelope = runCatching { ApiJson.decodeFromString(Envelope.serializer(serializer), text) }.getOrNull()
                if (status !in 200..299 || envelope?.data == null) {
                    throw ApiException(status, envelope?.code, envelope?.error ?: "HTTP $status")
                }
                envelope.data
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        fun eventPath(eventId: String, action: String) =
            "/api/device/events/${URLEncoder.encode(eventId, "UTF-8").replace("+", "%20")}/$action"
    }
}

/** Network down, DNS failure, timeout: anything that isn't an answer from the backend. */
fun Throwable.isOffline(): Boolean = this is IOException
