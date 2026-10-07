package com.rihal.roompanel.domain

import java.time.Duration
import java.time.Instant

/** One booking on the room's calendar, already normalised by the backend. */
data class Meeting(
    val id: String,
    val subject: String?,
    val organizer: String?,
    val start: Instant,
    val end: Instant,
    /** Private meetings never show subject, organiser or attendees on the door. */
    val isPrivate: Boolean = false,
    val attendees: List<String> = emptyList(),
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

/** Per-room behaviour, served by the backend's /api/device/config. */
data class PanelConfig(
    val roomName: String,
    val attendeeDisplay: AttendeeDisplay = AttendeeDisplay.COUNT,
    /** Amber "starting soon" window before the next meeting. */
    val startingSoonWindow: Duration = Duration.ofMinutes(10),
    val bookingOptionsMinutes: List<Int> = listOf(15, 30, 60),
    val maxAdHocMinutes: Int = 120,
    val extendStepMinutes: Int = 15,
)
