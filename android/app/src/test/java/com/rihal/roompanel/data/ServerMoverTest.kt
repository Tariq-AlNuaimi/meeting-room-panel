package com.rihal.roompanel.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ServerMoverTest {

    private class Store(var url: String) : ServerAddressStore {
        override fun current() = url
        override fun save(url: String) {
            this.url = url
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private val old = "https://old.example.com"
    private val new = "https://new.example.com"

    @Test
    fun movesOnlyWhenTheNewPortalKnowsTheTablet() = runTest {
        val store = Store(old)
        val pinged = mutableListOf<String>()
        var accept = false
        val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
        val mover = ServerMover(store, ping = { pinged += it; accept }, clock = clock)

        assertFalse(mover.consider(new))
        assertEquals(old, store.url)

        // Within the retry window: no second ping.
        clock.now = clock.now.plusSeconds(60)
        assertFalse(mover.consider(new))
        assertEquals(1, pinged.size)

        // After it, the new portal has the data: switch.
        accept = true
        clock.now = clock.now.plus(ServerMover.RETRY)
        assertTrue(mover.consider(new))
        assertEquals(new, store.url)
        assertEquals(listOf(new, new), pinged)

        // Already there: nothing to do.
        assertFalse(mover.consider("$new/"))
        assertEquals(2, pinged.size)
    }

    @Test
    fun ignoresMissingOrUnsafeAddresses() = runTest {
        val store = Store(old)
        val mover = ServerMover(store, ping = { error("must not ping") })
        listOf(null, "http://new.example.com", "https://new.example.com/x", old).forEach {
            assertFalse(mover.consider(it))
        }
        assertEquals(old, store.url)
    }

    @Test
    fun aFailingPingNeverThrows() = runTest {
        val store = Store(old)
        val mover = ServerMover(store, ping = { throw java.io.IOException("down") })
        assertFalse(mover.consider(new))
        assertEquals(old, store.url)
    }
}
