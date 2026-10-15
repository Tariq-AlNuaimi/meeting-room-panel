package com.rihal.roompanel.domain

import java.net.URI

/**
 * A portal address the tablet may talk to: `https://host[:port]` with nothing else (no path,
 * query, fragment or user info), returned in canonical form, or null. The device token is sent
 * there, so anything looser is refused. Mirrors `serverAddressSchema` in the backend.
 */
fun normalizeServerUrl(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH } ?: return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (!"https".equals(uri.scheme, ignoreCase = true)) return null
    if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
    val path = uri.rawPath.orEmpty()
    if (path.isNotEmpty() && path != "/") return null
    val host = uri.host?.lowercase() ?: return null
    val port = if (uri.port == -1 || uri.port == HTTPS_PORT) "" else ":${uri.port}"
    return "https://$host$port"
}

private const val MAX_LENGTH = 300
private const val HTTPS_PORT = 443
