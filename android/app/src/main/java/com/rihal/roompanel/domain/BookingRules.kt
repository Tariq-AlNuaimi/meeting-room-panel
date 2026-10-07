package com.rihal.roompanel.domain

import java.time.Duration
import java.time.Instant

/**
 * What the panel may offer. The backend re-checks every action against live free/busy;
 * these rules only decide which buttons to show.
 */
object BookingRules {

    /** Minutes until the next meeting starts, or null if the rest of the day is free. */
    fun minutesUntilNext(meetings: List<Meeting>, now: Instant): Long? =
        meetings.filter { !it.start.isBefore(now) }
            .minByOrNull { it.start }
            ?.let { Duration.between(now, it.start).toMinutes() }

    /** Book-now durations that fit before the next meeting and under the ad-hoc cap. Empty when busy. */
    fun bookNowOptions(meetings: List<Meeting>, now: Instant, config: PanelConfig): List<Int> {
        if (meetings.any { it.isOngoingAt(now) }) return emptyList()
        val limit = minOf(minutesUntilNext(meetings, now) ?: Long.MAX_VALUE, config.maxAdHocMinutes.toLong())
        return config.bookingOptionsMinutes.filter { it <= limit }
    }

    /** Whether [meeting] can be extended by [minutes] without colliding with the next booking. */
    fun canExtend(meeting: Meeting, meetings: List<Meeting>, minutes: Int): Boolean {
        if (!meeting.bookedFromPanel) return false
        val newEnd = meeting.end.plus(Duration.ofMinutes(minutes.toLong()))
        return meetings.none { it.id != meeting.id && it.start.isBefore(newEnd) && it.end.isAfter(meeting.end) }
    }
}
