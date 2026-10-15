package com.rihal.roompanel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun acceptsCleanHttpsAddresses() {
        assertEquals("https://rooms.example.com", normalizeServerUrl("https://rooms.example.com"))
        assertEquals("https://rooms.example.com", normalizeServerUrl("  HTTPS://Rooms.Example.com/ "))
        assertEquals("https://rooms.example.com", normalizeServerUrl("https://rooms.example.com:443"))
        assertEquals("https://10.0.0.5:8443", normalizeServerUrl("https://10.0.0.5:8443"))
    }

    @Test
    fun refusesAnythingElse() {
        listOf(
            null,
            "",
            "rooms.example.com",
            "http://rooms.example.com",
            "https://rooms.example.com/portal",
            "https://rooms.example.com/?x=1",
            "https://rooms.example.com/#a",
            "https://user:pw@rooms.example.com",
            "javascript:alert(1)",
            "https://",
        ).forEach { assertNull(it, normalizeServerUrl(it)) }
    }
}
