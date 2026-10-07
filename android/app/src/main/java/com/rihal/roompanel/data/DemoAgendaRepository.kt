package com.rihal.roompanel.data

import com.rihal.roompanel.domain.Meeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** In-memory agenda for running the panel before the backend exists. Seeded relative to launch time. */
class DemoAgendaRepository(private val clock: Clock = Clock.systemDefaultZone()) : AgendaRepository {

    private val state = MutableStateFlow(seed(clock.instant().truncatedTo(ChronoUnit.MINUTES)))
    override val meetings: StateFlow<List<Meeting>> = state.asStateFlow()

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun bookNow(minutes: Int): Result<Meeting> {
        val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        val meeting = Meeting(
            id = UUID.randomUUID().toString(),
            subject = null,
            organizer = null,
            start = now,
            end = now.plus(Duration.ofMinutes(minutes.toLong())),
            checkedIn = true,
            bookedFromPanel = true,
        )
        state.update { (it + meeting).sortedBy(Meeting::start) }
        return Result.success(meeting)
    }

    override suspend fun extend(meetingId: String, minutes: Int): Result<Unit> = edit(meetingId) {
        it.copy(end = it.end.plus(Duration.ofMinutes(minutes.toLong())))
    }

    override suspend fun endEarly(meetingId: String): Result<Unit> {
        val now = clock.instant()
        state.update { list ->
            list.mapNotNull { m ->
                when {
                    m.id != meetingId -> m
                    now.isAfter(m.start) -> m.copy(end = now)
                    else -> null
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun checkIn(meetingId: String): Result<Unit> = edit(meetingId) { it.copy(checkedIn = true) }

    private fun edit(id: String, change: (Meeting) -> Meeting): Result<Unit> {
        state.update { list -> list.map { if (it.id == id) change(it) else it } }
        return Result.success(Unit)
    }

    private companion object {
        fun seed(now: Instant): List<Meeting> {
            fun at(minutes: Long) = now.plus(Duration.ofMinutes(minutes))
            return listOf(
                Meeting("demo-1", "Weekly sync", "Aisha Al-Balushi", at(-90), at(-60),
                    attendees = listOf("Aisha Al-Balushi", "Omar Al-Harthy", "Sara Al-Hinai")),
                Meeting("demo-2", "Product review", "Omar Al-Harthy", at(25), at(85),
                    attendees = listOf("Omar Al-Harthy", "Tariq Al-Naaimi", "Layla Al-Rawahi", "Khalid Al-Lawati"),
                    joinUrl = "https://teams.microsoft.com/l/meetup-join/demo"),
                Meeting("demo-3", null, null, at(120), at(150), isPrivate = true),
                Meeting("demo-4", "Client call", "Layla Al-Rawahi", at(180), at(210),
                    attendees = listOf("Layla Al-Rawahi", "External guest")),
            )
        }
    }
}
