package com.rihal.roompanel.ui

import com.rihal.roompanel.data.AgendaState
import com.rihal.roompanel.data.SyncProblem
import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.Branding
import com.rihal.roompanel.domain.Equipment
import com.rihal.roompanel.domain.GuestWifi
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.WorkingHours
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Shared fixtures for the UI tests: 09:00 UTC on Wednesday 7 Oct 2026, a room open 08:00-18:00 every day. */
object TestStates {
    val now: Instant = Instant.parse("2026-10-07T09:00:00Z")
    val utc: ZoneId = ZoneId.of("UTC")

    fun at(minutes: Long): Instant = now.plus(Duration.ofMinutes(minutes))

    val config = PanelConfig(
        roomName = "Board Room",
        attendeeDisplay = AttendeeDisplay.NAMES,
        zone = utc,
        workingHours = WorkingHours(DayOfWeek.entries.toSet(), LocalTime.of(8, 0), LocalTime.of(18, 0)),
        floor = "2nd floor",
        capacity = 8,
        equipment = setOf(Equipment.SCREEN, Equipment.VIDEO),
        branding = Branding(
            orgName = "Rihal",
            welcomeMessage = "Welcome to Rihal",
            visitorNotice = "Guests, please sign in at reception.",
            titlePresets = listOf("Quick meeting", "Client call", "Interview", "Team huddle"),
        ),
        guestWifi = GuestWifi("Rihal-Guest", "not-a-real-password"),
    )

    val review = Meeting(
        "review", "Product review", "Omar Al-Harthy", at(25), at(85),
        attendees = listOf("Omar Al-Harthy", "Tariq Al-Naaimi", "Layla Al-Rawahi", "Khalid Al-Lawati"),
        attendeeCount = 4,
    )
    val private = Meeting("private", null, null, at(120), at(150), isPrivate = true)
    val tomorrow = Meeting("tomorrow", "Sprint planning", "Layla Al-Rawahi", at(24 * 60 + 60), at(24 * 60 + 120))

    fun state(
        meetings: List<Meeting>,
        config: PanelConfig = this.config,
        stale: Boolean = false,
        feedback: ActionFeedback = ActionFeedback(),
        now: Instant = this.now,
    ): PanelUiState = panelUiState(
        AgendaState(
            config = config,
            meetings = meetings,
            lastSyncedAt = if (stale) now.minusSeconds(600) else now,
            problem = if (stale) SyncProblem.OFFLINE else null,
        ),
        now,
        feedback,
        utc,
    )!!
}
