package com.rihal.roompanel.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** One booking on the room's calendar, already normalised by the backend. */
data class Meeting(
    val id: String,
    val subject: String?,
    val organizer: String?,
    val start: Instant,
    val end: Instant,
    /** Private meetings never show subject, organiser or attendees on the door. */
    val isPrivate: Boolean = false,
    /** Names, only when the room shows names (the server applies the room's privacy setting). */
    val attendees: List<String> = emptyList(),
    /** How many attendees, when the room shows a count or names. */
    val attendeeCount: Int = attendees.size,
    /** Teams join link, shown as a QR code when present. */
    val joinUrl: String? = null,
    val checkedIn: Boolean = false,
    /** True when the panel created it (ad-hoc "book now"); only these may be extended or shortened in place. */
    val bookedFromPanel: Boolean = false,
) {
    init {
        require(end.isAfter(start)) { "Meeting $id must end after it starts" }
    }

    val duration: Duration get() = Duration.between(start, end)

    fun isOngoingAt(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
}

enum class AttendeeDisplay { OFF, COUNT, NAMES }

/** Per-room behaviour, served with the agenda by the backend's `/api/device/agenda`. */
data class PanelConfig(
    val roomName: String,
    val attendeeDisplay: AttendeeDisplay = AttendeeDisplay.COUNT,
    /** Amber "starting soon" window before the next meeting. */
    val startingSoonWindow: Duration = Duration.ofMinutes(10),
    val bookingOptionsMinutes: List<Int> = listOf(15, 30, 60),
    val maxAdHocMinutes: Int = 120,
    val extendStepMinutes: Int = 15,
    val workingHours: WorkingHours = WorkingHours(),
    /** Minutes before a meeting starts that "Check in" appears. */
    val checkInWindowMinutes: Int = 10,
)

/** When the office is open. Outside these hours the panel dims unless the room is in use. */
data class WorkingHours(
    /** Oman work week by default. */
    val days: Set<DayOfWeek> = setOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    ),
    val start: LocalTime = LocalTime.of(7, 0),
    val end: LocalTime = LocalTime.of(18, 0),
) {
    init {
        require(end.isAfter(start)) { "Working hours must end after they start" }
    }
}
