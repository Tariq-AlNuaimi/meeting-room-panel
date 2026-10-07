package com.rihal.roompanel.data.api

import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.WorkingHours
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Wire format of `GET /api/device/agenda` (meeting-room-backend, `{ data: ... }` envelope). */
@Serializable
data class Envelope<T>(val data: T? = null, val error: String? = null, val code: String? = null)

@Serializable
data class AgendaDto(val serverTime: String, val room: RoomDto, val meetings: List<MeetingDto>)

@Serializable
data class RoomDto(
    val id: String,
    val name: String,
    val timeZone: String,
    val workDays: String,
    val workStart: String,
    val workEnd: String,
    val checkInWindowMinutes: Int,
    val maxAdHocMinutes: Int,
    val attendeeDisplay: String,
)

@Serializable
data class MeetingDto(
    val id: String,
    val subject: String? = null,
    val organizer: String? = null,
    val start: String,
    val end: String,
    val isPrivate: Boolean = false,
    val attendees: List<String> = emptyList(),
    val attendeeCount: Int = 0,
    val joinUrl: String? = null,
    val bookedFromPanel: Boolean = false,
    val checkedIn: Boolean = false,
)

@Serializable
data class PairStartDto(val code: String, val pollToken: String, val expiresAt: String)

/** `status` is pending | expired | consumed | paired; token fields only when paired. */
@Serializable
data class PairPollDto(
    val status: String,
    val deviceToken: String? = null,
    val deviceId: String? = null,
    val roomName: String? = null,
    val expiresAt: String? = null,
)

val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun RoomDto.toConfig(): PanelConfig = PanelConfig(
    roomName = name,
    attendeeDisplay = runCatching { AttendeeDisplay.valueOf(attendeeDisplay) }.getOrDefault(AttendeeDisplay.COUNT),
    maxAdHocMinutes = maxAdHocMinutes,
    checkInWindowMinutes = checkInWindowMinutes,
    workingHours = runCatching {
        WorkingHours(
            days = workDays.split(",").map { DayOfWeek.of(it.trim().toInt()) }.toSet(),
            start = LocalTime.parse(workStart),
            end = LocalTime.parse(workEnd),
        )
    }.getOrDefault(WorkingHours()),
)

fun MeetingDto.toMeeting(): Meeting = Meeting(
    id = id,
    subject = subject,
    organizer = organizer,
    start = Instant.parse(start),
    end = Instant.parse(end),
    isPrivate = isPrivate,
    attendees = attendees,
    attendeeCount = maxOf(attendeeCount, attendees.size),
    joinUrl = joinUrl,
    checkedIn = checkedIn,
    bookedFromPanel = bookedFromPanel,
)
