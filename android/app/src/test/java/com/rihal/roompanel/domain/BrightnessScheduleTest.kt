package com.rihal.roompanel.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BrightnessScheduleTest {

    private val muscat = ZoneId.of("Asia/Muscat")
    private val hours = WorkingHours()

    // 2026-10-07 is a Wednesday; 2026-10-09 is a Friday.
    private fun at(date: String, time: String) = ZonedDateTime.of(
        java.time.LocalDate.parse(date), java.time.LocalTime.parse(time), muscat,
    )

    private val meeting = Meeting(
        "m", "Late call", "Org",
        at("2026-10-07", "20:00").toInstant(), at("2026-10-07", "21:00").toInstant(),
    )

    @Test
    fun `full brightness during working hours`() {
        assertEquals(BrightnessSchedule.FULL, BrightnessSchedule.brightnessFor(at("2026-10-07", "10:00"), RoomStatus.Free(null), hours))
    }

    @Test
    fun `dimmed in the evening when free`() {
        assertEquals(BrightnessSchedule.DIMMED, BrightnessSchedule.brightnessFor(at("2026-10-07", "19:00"), RoomStatus.Free(null), hours))
    }

    @Test
    fun `working hours end is exclusive`() {
        assertEquals(BrightnessSchedule.DIMMED, BrightnessSchedule.brightnessFor(at("2026-10-07", "18:00"), RoomStatus.Free(null), hours))
    }

    @Test
    fun `dimmed on the weekend`() {
        assertEquals(BrightnessSchedule.DIMMED, BrightnessSchedule.brightnessFor(at("2026-10-09", "10:00"), RoomStatus.Free(null), hours))
    }

    @Test
    fun `full brightness for an evening meeting`() {
        assertEquals(BrightnessSchedule.FULL, BrightnessSchedule.brightnessFor(at("2026-10-07", "20:30"), RoomStatus.Busy(meeting, null), hours))
    }

    @Test
    fun `full brightness when an evening meeting is starting soon`() {
        assertEquals(BrightnessSchedule.FULL, BrightnessSchedule.brightnessFor(at("2026-10-07", "19:55"), RoomStatus.StartingSoon(meeting), hours))
    }
}
