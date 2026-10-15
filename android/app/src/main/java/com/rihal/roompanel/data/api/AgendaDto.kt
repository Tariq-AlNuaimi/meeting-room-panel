package com.rihal.roompanel.data.api

import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.Branding
import com.rihal.roompanel.domain.Equipment
import com.rihal.roompanel.domain.FreeRoom
import com.rihal.roompanel.domain.GuestWifi
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.PanelLanguage
import com.rihal.roompanel.domain.WorkingHours
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Wire format of `GET /api/device/agenda` (meeting-room-backend, `{ data: ... }` envelope). */
@Serializable
data class Envelope<T>(val data: T? = null, val error: String? = null, val code: String? = null)

@Serializable
data class AgendaDto(
    val serverTime: String,
    val room: RoomDto,
    val meetings: List<MeetingDto>,
    /** Portal "Branding & display" (backend 0.4+); absent from older backends. */
    val panel: PanelDto? = null,
    /** Only while the portal shows guest Wi-Fi on this room. */
    val guestWifi: GuestWifiDto? = null,
    /** Set while the portal moves to a new address (backend docs/self-hosting.md). */
    val move: MoveDto? = null,
)

@Serializable
data class MoveDto(val url: String)

@Serializable
data class ImageRefDto(val url: String, val version: String)

@Serializable
data class PanelDto(
    val orgName: String = "",
    val primaryColor: String? = null,
    val welcomeMessage: String = "",
    val visitorNotice: String = "",
    val language: String = "EN",
    val clock24h: Boolean = true,
    val showOrganizer: Boolean = true,
    val bookingLengths: List<Int> = listOf(15, 30, 60),
    val titlePresets: List<String> = emptyList(),
    val dimOutsideHours: Boolean = true,
    val logo: ImageRefDto? = null,
    val background: ImageRefDto? = null,
)

@Serializable
data class GuestWifiDto(val ssid: String, val password: String, val expiresAt: String? = null) {
    override fun toString() = "GuestWifiDto(ssid=$ssid)"
}

/** `GET /api/device/free-rooms`. */
@Serializable
data class FreeRoomsDto(val rooms: List<FreeRoomDto>)

@Serializable
data class FreeRoomDto(
    val id: String,
    val name: String,
    val floor: String = "",
    val capacity: Int? = null,
    val freeUntil: String? = null,
)

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
    val floor: String = "",
    val capacity: Int? = null,
    val equipment: List<String> = emptyList(),
    val allowTabletBooking: Boolean = true,
    val outOfService: Boolean = false,
    val outOfServiceMessage: String = "",
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
    val releaseAt: String? = null,
)

@Serializable
data class PairStartDto(val code: String, val pollToken: String, val expiresAt: String)

/** `status` is pending | expired | consumed | paired; token fields only when paired. */
/** `GET /api/device/app-update`: the APK this tablet should run, or null. */
@Serializable
data class AppUpdateDto(val versionCode: Int, val url: String, val sha256: String)

@Serializable
data class AppUpdateEnvelopeDto(val update: AppUpdateDto? = null)

@Serializable
data class PairPollDto(
    val status: String,
    val deviceToken: String? = null,
    val deviceId: String? = null,
    val roomName: String? = null,
    val expiresAt: String? = null,
)

val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun RoomDto.toConfig(panel: PanelDto? = null, wifi: GuestWifiDto? = null): PanelConfig = PanelConfig(
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
    zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault()),
    floor = floor,
    capacity = capacity,
    equipment = equipment.mapNotNull { code -> runCatching { Equipment.valueOf(code) }.getOrNull() }.toSet(),
    allowTabletBooking = allowTabletBooking,
    outOfService = outOfService,
    outOfServiceMessage = outOfServiceMessage,
    bookingOptionsMinutes = panel?.bookingLengths?.filter { it > 0 }?.takeIf { it.isNotEmpty() } ?: listOf(15, 30, 60),
    branding = panel?.toBranding() ?: Branding(),
    guestWifi = wifi?.takeIf { it.ssid.isNotBlank() }?.let { GuestWifi(it.ssid, it.password) },
)

/** "#RRGGBB" to opaque ARGB, or null. */
fun parseHexColor(hex: String?): Long? =
    hex?.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }?.let { 0xFF000000L or it.substring(1).toLong(16) }

fun PanelDto.toBranding() = Branding(
    orgName = orgName,
    primaryColor = parseHexColor(primaryColor),
    logoPath = logo?.url,
    backgroundPath = background?.url,
    welcomeMessage = welcomeMessage,
    visitorNotice = visitorNotice,
    language = runCatching { PanelLanguage.valueOf(language) }.getOrDefault(PanelLanguage.EN),
    clock24h = clock24h,
    titlePresets = titlePresets.filter { it.isNotBlank() },
    dimOutsideHours = dimOutsideHours,
)

fun FreeRoomDto.toFreeRoom() = FreeRoom(
    name = name,
    floor = floor,
    capacity = capacity,
    freeUntil = freeUntil?.let { runCatching { Instant.parse(it) }.getOrNull() },
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
    releaseAt = releaseAt?.let(Instant::parse),
)
