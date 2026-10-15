package com.rihal.roompanel.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** One row of the agenda list: a day heading, a booking, or a free gap someone can tap to book. */
sealed interface AgendaEntry {
    val key: String

    data class Day(val date: LocalDate, val isToday: Boolean) : AgendaEntry {
        override val key get() = "day-$date"
    }

    data class Booked(val meeting: Meeting) : AgendaEntry {
        override val key get() = "m-${meeting.id}"
    }

    /** A gap of at least [AgendaPlanner.MIN_SLOT] inside working hours. [startsNow] = book from now, not later. */
    data class Free(val start: Instant, val end: Instant, val startsNow: Boolean, val bookable: Boolean) : AgendaEntry {
        override val key get() = "free-${start.epochSecond}"
        val minutes: Long get() = Duration.between(start, end).toMinutes()
    }
}

/**
 * Today's and tomorrow's agenda as a list: meetings that have not ended, with the free gaps
 * between them (inside working hours, on work days). A later gap starts on the next quarter
 * hour, which is what the server accepts. The server re-checks every booking.
 */
object AgendaPlanner {
    val MIN_SLOT: Duration = Duration.ofMinutes(15)
    private const val QUARTER_SECONDS = 15 * 60L

    fun plan(meetings: List<Meeting>, now: Instant, config: PanelConfig): List<AgendaEntry> {
        val zone = config.zone
        val today = now.atZone(zone).toLocalDate()
        val upcoming = meetings.filter { it.end.isAfter(now) }.sortedBy { it.start }
        return listOf(today, today.plusDays(1)).flatMap { date ->
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val dayMeetings = upcoming.filter { it.start.isBefore(dayEnd) && !it.start.isBefore(dayStart) ||
                (date == today && it.isOngoingAt(now)) }
            val free = freeGaps(date, dayMeetings, now, config)
            val rows = (dayMeetings.map { AgendaEntry.Booked(it) to it.start } + free.map { it to it.start })
                .sortedBy { it.second }
                .map { it.first }
            listOf(AgendaEntry.Day(date, date == today)) + rows
        }
    }

    private fun freeGaps(date: LocalDate, dayMeetings: List<Meeting>, now: Instant, config: PanelConfig): List<AgendaEntry.Free> {
        val hours = config.workingHours
        if (date.dayOfWeek !in hours.days) return emptyList()
        val open = ZonedDateTime.of(date, hours.start, config.zone).toInstant()
        val close = ZonedDateTime.of(date, hours.end, config.zone).toInstant()
        var cursor = maxOf(open, now)
        val gaps = mutableListOf<AgendaEntry.Free>()
        fun addGap(until: Instant) {
            val startsNow = !cursor.isAfter(now)
            val start = if (startsNow) cursor else roundUpToQuarter(cursor)
            val end = minOf(until, close)
            if (Duration.between(start, end) >= MIN_SLOT) {
                gaps += AgendaEntry.Free(start, end, startsNow, config.canBook)
            }
        }
        for (m in dayMeetings.sortedBy { it.start }) {
            if (m.start.isAfter(cursor)) addGap(m.start)
            if (m.end.isAfter(cursor)) cursor = m.end
        }
        if (cursor.isBefore(close)) addGap(close)
        return gaps
    }

    fun roundUpToQuarter(t: Instant): Instant {
        val s = t.epochSecond
        val rem = s % QUARTER_SECONDS
        return if (rem == 0L && t.nano == 0) t else Instant.ofEpochSecond(s - rem + QUARTER_SECONDS)
    }

    /** Lengths offered for [slot]: the portal's lengths that fit the gap and the room's maximum. */
    fun lengthsFor(slot: AgendaEntry.Free, now: Instant, config: PanelConfig): List<Int> {
        val from = if (slot.startsNow) now else slot.start
        val room = Duration.between(from, slot.end).toMinutes()
        return config.bookingOptionsMinutes.filter { it <= room && it <= config.maxAdHocMinutes }
    }

    /** Minutes left in [current] when it ends within [warnWithin]; else null (the end-of-meeting countdown). */
    fun endingSoon(current: Meeting?, now: Instant, warnWithin: Duration = Duration.ofMinutes(5)): Long? {
        current ?: return null
        val left = Duration.between(now, current.end)
        if (left.isNegative || left > warnWithin) return null
        return (left.seconds + 59) / 60
    }
}
