package com.rihal.roompanel.data

import com.rihal.roompanel.data.api.AgendaDto
import com.rihal.roompanel.data.api.ApiJson
import com.rihal.roompanel.data.api.BackendClient
import com.rihal.roompanel.data.api.Envelope
import com.rihal.roompanel.data.api.PairPollDto
import com.rihal.roompanel.data.api.toConfig
import com.rihal.roompanel.data.api.toMeeting
import com.rihal.roompanel.domain.AttendeeDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Wire shapes as meeting-room-backend actually returns them (captured from a local run, 2026-10-07). */
class AgendaDtoTest {

    private val agendaJson = """
        {"data":{"serverTime":"2026-10-07T05:00:00.000Z",
          "room":{"id":"r1","name":"Board Room","timeZone":"Asia/Muscat","workDays":"7,1,2,3,4","workStart":"07:00",
                  "workEnd":"18:00","checkInWindowMinutes":10,"maxAdHocMinutes":120,"attendeeDisplay":"COUNT"},
          "meetings":[
            {"id":"AAMk1","subject":"Product review","organizer":"Omar","start":"2026-10-07T05:00:00.000Z",
             "end":"2026-10-07T06:00:00.000Z","isPrivate":false,"attendees":[],"attendeeCount":4,
             "joinUrl":"https://teams.microsoft.com/l/x","bookedFromPanel":false,"checkedIn":true},
            {"id":"AAMk2","subject":null,"organizer":null,"start":"2026-10-07T07:00:00.000Z","end":"2026-10-07T07:30:00.000Z",
             "isPrivate":true,"attendees":[],"attendeeCount":0,"joinUrl":null,"bookedFromPanel":false,"checkedIn":false,
             "someFutureField":123}
          ]}}
    """.trimIndent()

    @Test
    fun `agenda envelope maps to room config and meetings`() {
        val agenda = ApiJson.decodeFromString(Envelope.serializer(AgendaDto.serializer()), agendaJson).data!!
        val config = agenda.room.toConfig()
        assertEquals("Board Room", config.roomName)
        assertEquals(AttendeeDisplay.COUNT, config.attendeeDisplay)
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), config.workingHours.days)
        assertEquals(LocalTime.of(18, 0), config.workingHours.end)
        assertEquals(10, config.checkInWindowMinutes)

        val (review, private) = agenda.meetings.map { it.toMeeting() }
        assertEquals(Instant.parse("2026-10-07T05:00:00Z"), review.start)
        assertEquals(4, review.attendeeCount)
        assertTrue(review.attendees.isEmpty())
        assertTrue(review.checkedIn)
        assertTrue(private.isPrivate)
        assertNull(private.subject)
    }

    @Test
    fun `backend error envelope carries the machine code`() {
        val json = """{"error":"Microsoft 365 is not connected (MS_* environment variables are not set)","code":"calendar_not_configured"}"""
        val env = ApiJson.decodeFromString(Envelope.serializer(AgendaDto.serializer()), json)
        assertNull(env.data)
        assertEquals("calendar_not_configured", env.code)
    }

    @Test
    fun `pairing poll responses parse in every state`() {
        fun poll(json: String) = ApiJson.decodeFromString(Envelope.serializer(PairPollDto.serializer()), json).data!!
        assertEquals("pending", poll("""{"data":{"status":"pending","expiresAt":"2026-10-07T19:41:55.499Z"}}""").status)
        assertEquals("consumed", poll("""{"data":{"status":"consumed"}}""").status)
        val paired = poll("""{"data":{"status":"paired","deviceToken":"t","deviceId":"d","roomName":"Board Room"}}""")
        assertEquals("t", paired.deviceToken)
        assertEquals("Board Room", paired.roomName)
    }

    @Test
    fun `event ids are path-encoded (Graph ids contain slash, plus and equals)`() {
        assertEquals("/api/device/events/AAMk%2Fab%2Bc%3D%3D/end", BackendClient.eventPath("AAMk/ab+c==", "end"))
    }
}

/** Backend 0.4 adds the portal's settings to the agenda; older backends simply omit them. */
class AgendaDto04Test {
    private val json = """
        {"data":{"serverTime":"2026-10-15T06:00:00.000Z",
          "room":{"id":"r1","name":"Board Room","timeZone":"Asia/Muscat","workDays":"7,1,2,3,4","workStart":"07:00",
                  "workEnd":"18:00","checkInWindowMinutes":10,"maxAdHocMinutes":120,"autoReleaseMinutes":10,"attendeeDisplay":"COUNT",
                  "floor":"2nd floor","capacity":8,"equipment":["SCREEN","VIDEO","LASER"],"allowTabletBooking":false,
                  "outOfService":true,"outOfServiceMessage":"Projector repair","showGuestWifi":true},
          "panel":{"orgName":"Rihal","primaryColor":"#17663F","welcomeMessage":"Welcome","visitorNotice":"Sign in",
                   "language":"BOTH","clock24h":false,"showOrganizer":true,"bookingLengths":[30,60],
                   "titlePresets":["Client call",""],"dimOutsideHours":false,
                   "logo":{"url":"/api/device/branding/logo?v=abc","version":"abc"},"background":null},
          "guestWifi":{"ssid":"Rihal-Guest","password":"secret-pass","expiresAt":null},
          "meetings":[]}}
    """.trimIndent()

    @Test
    fun `portal settings map onto the panel config`() {
        val dto = com.rihal.roompanel.data.api.ApiJson.decodeFromString(
            com.rihal.roompanel.data.api.Envelope.serializer(com.rihal.roompanel.data.api.AgendaDto.serializer()), json,
        ).data!!
        val c = dto.room.toConfig(dto.panel, dto.guestWifi)
        assertEquals("2nd floor", c.floor)
        assertEquals(8, c.capacity)
        assertEquals(setOf(com.rihal.roompanel.domain.Equipment.SCREEN, com.rihal.roompanel.domain.Equipment.VIDEO), c.equipment)
        assertEquals(false, c.allowTabletBooking)
        assertEquals(true, c.outOfService)
        assertEquals(false, c.canBook)
        assertEquals(listOf(30, 60), c.bookingOptionsMinutes)
        assertEquals(0xFF17663FL, c.branding.primaryColor)
        assertEquals(com.rihal.roompanel.domain.PanelLanguage.BOTH, c.branding.language)
        assertEquals(listOf("Client call"), c.branding.titlePresets)
        assertEquals("/api/device/branding/logo?v=abc", c.branding.logoPath)
        assertEquals("Rihal-Guest", c.guestWifi?.ssid)
        assertEquals(java.time.ZoneId.of("Asia/Muscat"), c.zone)
    }
}
