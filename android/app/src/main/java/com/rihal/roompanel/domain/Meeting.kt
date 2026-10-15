package com.rihal.roompanel.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

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
    /** When the room will release this meeting if nobody checks in (server rule); null = never. */
    val releaseAt: Instant? = null,
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
    /** The "+15" / "+30" buttons while a panel booking runs. */
    val extendOptionsMinutes: List<Int> = listOf(15, 30),
    val workingHours: WorkingHours = WorkingHours(),
    /** Minutes before a meeting starts that "Check in" appears. */
    val checkInWindowMinutes: Int = 10,
    /** The room's time zone (from the portal); booking slots are computed in it. */
    val zone: ZoneId = ZoneId.systemDefault(),
    val floor: String = "",
    val capacity: Int? = null,
    val equipment: Set<Equipment> = emptySet(),
    /** Portal switch "Allow employee booking on tablet". */
    val allowTabletBooking: Boolean = true,
    /** Maintenance mode: shows [outOfServiceMessage], no booking. */
    val outOfService: Boolean = false,
    val outOfServiceMessage: String = "",
    val branding: Branding = Branding(),
    /** Shown as a join QR on the idle screen; null when the portal has it off or expired. */
    val guestWifi: GuestWifi? = null,
) {
    /** Booking from this tablet is possible at all (the server enforces the same rules). */
    val canBook: Boolean get() = allowTabletBooking && !outOfService
}

/** Fixed codes from the portal; the tablet shows translated labels. */
enum class Equipment { SCREEN, VIDEO, WHITEBOARD, PHONE, ACCESSIBLE }

enum class PanelLanguage { EN, AR, BOTH }

/** Everything the portal's "Branding & display" page controls. */
data class Branding(
    val orgName: String = "",
    /** ARGB; null = the app's default. The portal only saves colours white text reads on (>= 4.5:1). */
    val primaryColor: Long? = null,
    /** Server-relative image paths carrying a version, so a change is a new URL. */
    val logoPath: String? = null,
    val backgroundPath: String? = null,
    val welcomeMessage: String = "",
    val visitorNotice: String = "",
    val language: PanelLanguage = PanelLanguage.EN,
    val clock24h: Boolean = true,
    /** Meeting titles to tap when booking; the first is preselected. Empty = the default title. */
    val titlePresets: List<String> = emptyList(),
    val dimOutsideHours: Boolean = true,
)

/** Guest network for the idle-screen QR code (sent by the server only while it should show). */
data class GuestWifi(val ssid: String, val password: String) {
    /** The de-facto standard Wi-Fi QR payload (as Android and iOS cameras read it). */
    fun qrPayload(): String = "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};;"

    private fun escape(s: String) = s.replace(Regex("""([\\;,:"])"""), """\\$1""")

    /** Never print the password, e.g. in a crash log. */
    override fun toString() = "GuestWifi(ssid=$ssid)"
}

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

/** Another room that is free now, for "Find a free room". */
data class FreeRoom(val name: String, val floor: String, val capacity: Int?, val freeUntil: Instant?)

/** "Report a problem" categories; the same fixed list as the portal. */
enum class IssueCategory { AV, VIDEO_CALL, CLEANING, SUPPLIES, TEMPERATURE, OTHER }
