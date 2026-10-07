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
