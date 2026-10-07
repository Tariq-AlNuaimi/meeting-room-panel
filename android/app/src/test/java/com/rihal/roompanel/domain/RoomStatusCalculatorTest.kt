package com.rihal.roompanel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RoomStatusCalculatorTest {

    private val nine = Instant.parse("2026-10-07T09:00:00Z")
    private val soon = Duration.ofMinutes(10)

    private fun meeting(id: String, startMin: Long, endMin: Long) =
        Meeting(id, "M$id", "Org", nine.plusSeconds(startMin * 60), nine.plusSeconds(endMin * 60))

    @Test
    fun `empty calendar is free with nothing next`() {
        assertEquals(RoomStatus.Free(null), RoomStatusCalculator.compute(emptyList(), nine, soon))
    }

    @Test
    fun `ongoing meeting is busy and reports the following one`() {
        val a = meeting("a", -10, 30)
        val b = meeting("b", 30, 60)
        assertEquals(RoomStatus.Busy(a, b), RoomStatusCalculator.compute(listOf(b, a), nine, soon))
    }

    @Test
    fun `meeting ending exactly now no longer counts as busy`() {
        val ended = meeting("a", -30, 0)
        assertEquals(RoomStatus.Free(null), RoomStatusCalculator.compute(listOf(ended), nine, soon))
    }

    @Test
    fun `meeting starting exactly now is busy`() {
        val a = meeting("a", 0, 30)
        assertTrue(RoomStatusCalculator.compute(listOf(a), nine, soon) is RoomStatus.Busy)
    }

    @Test
    fun `next meeting inside the window is starting soon`() {
        val a = meeting("a", 10, 40)
        assertEquals(RoomStatus.StartingSoon(a), RoomStatusCalculator.compute(listOf(a), nine, soon))
    }

    @Test
    fun `next meeting outside the window is free`() {
        val a = meeting("a", 11, 40)
        assertEquals(RoomStatus.Free(a), RoomStatusCalculator.compute(listOf(a), nine, soon))
    }

    @Test
    fun `double booking picks the earliest-ending meeting as current`() {
        val long = meeting("long", -30, 60)
        val short = meeting("short", -5, 20)
        val status = RoomStatusCalculator.compute(listOf(long, short), nine, soon) as RoomStatus.Busy
        assertEquals(short, status.current)
    }
}
