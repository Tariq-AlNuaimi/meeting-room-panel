package com.rihal.roompanel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BookingRulesTest {

    private val nine = Instant.parse("2026-10-07T09:00:00Z")
    private val config = PanelConfig(roomName = "Board Room", maxAdHocMinutes = 120)

    private fun meeting(id: String, startMin: Long, endMin: Long, fromPanel: Boolean = false) =
        Meeting(id, null, null, nine.plusSeconds(startMin * 60), nine.plusSeconds(endMin * 60), bookedFromPanel = fromPanel)

    @Test
    fun `all options offered when the day is free`() {
        assertEquals(listOf(15, 30, 60), BookingRules.bookNowOptions(emptyList(), nine, config))
    }

    @Test
    fun `options are capped by the next meeting`() {
        val next = meeting("n", 40, 60)
        assertEquals(listOf(15, 30), BookingRules.bookNowOptions(listOf(next), nine, config))
    }

    @Test
    fun `option that ends exactly when the next meeting starts is allowed`() {
        val next = meeting("n", 30, 60)
        assertEquals(listOf(15, 30), BookingRules.bookNowOptions(listOf(next), nine, config))
    }

    @Test
    fun `options are capped by the ad-hoc maximum`() {
        assertEquals(listOf(15, 30), BookingRules.bookNowOptions(emptyList(), nine, config.copy(maxAdHocMinutes = 45)))
    }

    @Test
    fun `no booking while a meeting is ongoing`() {
        assertEquals(emptyList<Int>(), BookingRules.bookNowOptions(listOf(meeting("c", -5, 25)), nine, config))
    }

    @Test
    fun `panel booking can extend into free time`() {
        val current = meeting("c", -15, 15, fromPanel = true)
        val next = meeting("n", 30, 60)
        assertTrue(BookingRules.canExtend(current, listOf(current, next), 15))
    }

    @Test
    fun `extension that collides with the next meeting is refused`() {
        val current = meeting("c", -15, 15, fromPanel = true)
        val next = meeting("n", 20, 60)
        assertFalse(BookingRules.canExtend(current, listOf(current, next), 15))
    }

    @Test
    fun `meetings booked in Outlook cannot be extended from the panel`() {
        val current = meeting("c", -15, 15, fromPanel = false)
        assertFalse(BookingRules.canExtend(current, listOf(current), 15))
    }
}
