package com.rihal.roompanel.domain

import java.time.Duration
import java.time.Instant

sealed interface RoomStatus {
    /** Free now. [next] is null when nothing else is booked today. */
    data class Free(val next: Meeting?) : RoomStatus

    /** Free now, but [next] starts within the starting-soon window. */
    data class StartingSoon(val next: Meeting) : RoomStatus

    data class Busy(val current: Meeting, val next: Meeting?) : RoomStatus
}

object RoomStatusCalculator {

    fun compute(meetings: List<Meeting>, now: Instant, startingSoonWindow: Duration): RoomStatus {
        val upcoming = meetings.filter { it.end.isAfter(now) }.sortedBy { it.start }
        // Overlapping bookings can exist (double-booked room); the earliest-ending ongoing one is "current".
        val current = upcoming.filter { it.isOngoingAt(now) }.minByOrNull { it.end }
        if (current != null) {
            val next = upcoming.firstOrNull { it !== current && !it.start.isBefore(current.end) }
            return RoomStatus.Busy(current, next)
        }
        val next = upcoming.firstOrNull() ?: return RoomStatus.Free(null)
        return if (Duration.between(now, next.start) <= startingSoonWindow) {
            RoomStatus.StartingSoon(next)
        } else {
            RoomStatus.Free(next)
        }
    }
}
