package com.rihal.roompanel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class AgendaPlannerTest {

    private val muscat = ZoneId.of("Asia/Muscat")
    // Wednesday 7 Oct 2026, 10:07 in Muscat. Work week Sun-Thu 08:00-17:00, so tomorrow (Fri) is closed.
    private val now = Instant.parse("2026-10-07T06:07:00Z")
    private val config = PanelConfig(
        roomName = "Board Room",
        zone = muscat,
        workingHours = WorkingHours(
            setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
        ),
    )

    private fun local(hhmm: String, day: Int = 7) = java.time.LocalDateTime.parse("2026-10-%02dT$hhmm".format(day)).atZone(muscat).toInstant()
    private fun m(id: String, from: String, to: String, day: Int = 7) = Meeting(id, id, null, local(from, day), local(to, day))

    @Test
    fun `free now, a later gap on the quarter hour, and tomorrow's open day`() {
        val plan = AgendaPlanner.plan(listOf(m("a", "11:00", "12:10"), m("b", "13:00", "16:55")), now, config)
        val all = plan.filterIsInstance<AgendaEntry.Free>()
        val free = all.filter { it.start.atZone(muscat).dayOfMonth == 7 }
        assertEquals(2, free.size)
        assertTrue(free[0].startsNow)
        assertEquals(now, free[0].start)
        assertEquals(local("11:00"), free[0].end)
        assertEquals(local("12:15"), free[1].start) // 12:10 rounded up
        assertEquals(local("13:00"), free[1].end)
        // 16:55-17:00 is under 15 minutes: no slot. Tomorrow (Thursday) is one free working day.
        val days = plan.filterIsInstance<AgendaEntry.Day>()
        assertEquals(listOf(true, false), days.map { it.isToday })
        assertEquals(listOf(local("08:00", 8) to local("17:00", 8)), (all - free.toSet()).map { it.start to it.end })
    }

    @Test
    fun `a closed day gets a heading and no slots`() {
        val thursday = Instant.parse("2026-10-08T06:07:00Z")
        val plan = AgendaPlanner.plan(emptyList(), thursday, config)
        assertTrue(plan.last() is AgendaEntry.Day) // Friday: heading only
        assertTrue(plan.filterIsInstance<AgendaEntry.Free>().none { it.start.atZone(muscat).dayOfMonth == 9 })
    }

    @Test
    fun `tomorrow on a work day gets its own slots`() {
        val tuesday = Instant.parse("2026-10-06T06:07:00Z") // tomorrow = Wednesday 7th
        val plan = AgendaPlanner.plan(listOf(m("x", "09:00", "10:00", day = 7)), tuesday, config)
        val tomorrowFree = plan.filterIsInstance<AgendaEntry.Free>().filter { it.start.atZone(muscat).dayOfMonth == 7 }
        assertEquals(listOf(local("08:00"), local("10:00")), tomorrowFree.map { it.start })
    }

    @Test
    fun `slots are not bookable when the room is out of service or booking is off`() {
        for (c in listOf(config.copy(outOfService = true), config.copy(allowTabletBooking = false))) {
            assertTrue(AgendaPlanner.plan(emptyList(), now, c).filterIsInstance<AgendaEntry.Free>().none { it.bookable })
        }
    }

    @Test
    fun `lengths fit the gap and the room maximum`() {
        val slot = AgendaEntry.Free(local("12:15"), local("13:00"), startsNow = false, bookable = true)
        assertEquals(listOf(15, 30), AgendaPlanner.lengthsFor(slot, now, config))
        val nowSlot = AgendaEntry.Free(now, local("10:40"), startsNow = true, bookable = true)
        assertEquals(listOf(15, 30), AgendaPlanner.lengthsFor(nowSlot, now, config))
        assertEquals(listOf(15), AgendaPlanner.lengthsFor(slot, now, config.copy(maxAdHocMinutes = 20)))
    }

    @Test
    fun `end warning only in the last five minutes`() {
        val meeting = Meeting("m", null, null, now.minusSeconds(3600), now.plus(Duration.ofSeconds(4 * 60 + 10)))
        assertEquals(5L, AgendaPlanner.endingSoon(meeting, now))
        assertNull(AgendaPlanner.endingSoon(meeting.copy(end = now.plusSeconds(600)), now))
        assertNull(AgendaPlanner.endingSoon(null, now))
    }

    @Test
    fun `rounding up to the quarter hour`() {
        assertEquals(Instant.parse("2026-10-07T06:15:00Z"), AgendaPlanner.roundUpToQuarter(Instant.parse("2026-10-07T06:07:00Z")))
        assertEquals(Instant.parse("2026-10-07T06:15:00Z"), AgendaPlanner.roundUpToQuarter(Instant.parse("2026-10-07T06:15:00Z")))
    }

    @Test
    fun `guest Wi-Fi QR escapes special characters and never prints the password`() {
        val wifi = GuestWifi("Rihal;Guest", "p\\a:ss,\"w")
        assertEquals("""WIFI:T:WPA;S:Rihal\;Guest;P:p\\a\:ss\,\"w;;""", wifi.qrPayload())
        assertTrue(!wifi.toString().contains("a:ss"))
    }
}
