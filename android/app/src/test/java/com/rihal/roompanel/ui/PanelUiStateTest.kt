package com.rihal.roompanel.ui

import com.rihal.roompanel.data.AgendaState
import com.rihal.roompanel.data.SyncProblem
import com.rihal.roompanel.domain.Branding
import com.rihal.roompanel.domain.BrightnessSchedule
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PanelUiStateTest {

    private val now = Instant.parse("2026-10-07T06:00:00Z") // 10:00 in Muscat, a Wednesday
    private val muscat = ZoneId.of("Asia/Muscat")
    private val config = PanelConfig(roomName = "Board Room", zone = muscat)
    private val idle = ActionFeedback()

    @Test
    fun `no screen state until the first sync brings the room settings`() {
        assertNull(panelUiState(AgendaState(), now, idle, muscat))
    }

    @Test
    fun `fresh agenda offers booking`() {
        val s = panelUiState(AgendaState(config, emptyList(), now.minusSeconds(20)), now, idle, muscat)!!
        assertFalse(s.stale)
        assertEquals(listOf(15, 30, 60), s.bookOptions)
        assertTrue(s.bookingAllowed)
    }

    @Test
    fun `stale agenda shows the banner and offers no booking`() {
        val s = panelUiState(AgendaState(config, emptyList(), now.minusSeconds(120), SyncProblem.OFFLINE), now, idle, muscat)!!
        assertTrue(s.stale)
        assertTrue(s.bookOptions.isEmpty())
        assertFalse(s.bookingAllowed)
        assertEquals(SyncProblem.OFFLINE, s.problem)
    }

    @Test
    fun `booking switched off in the portal, or out of service, offers no booking`() {
        for (c in listOf(config.copy(allowTabletBooking = false), config.copy(outOfService = true))) {
            val s = panelUiState(AgendaState(c, emptyList(), now), now, idle, muscat)!!
            assertTrue(s.bookOptions.isEmpty())
            assertFalse(s.bookingAllowed)
            assertTrue(s.agenda.filterIsInstance<com.rihal.roompanel.domain.AgendaEntry.Free>().none { it.bookable })
        }
    }

    @Test
    fun `the portal's booking lengths are what the door offers`() {
        val s = panelUiState(AgendaState(config.copy(bookingOptionsMinutes = listOf(30, 45)), emptyList(), now), now, idle, muscat)!!
        assertEquals(listOf(30, 45), s.bookOptions)
    }

    @Test
    fun `a running panel booking offers +15 and +30 when they fit, and warns in its last 5 minutes`() {
        val mine = Meeting("m", null, null, now.minusSeconds(25 * 60), now.plusSeconds(4 * 60), bookedFromPanel = true)
        val next = Meeting("n", "Next", "X", now.plusSeconds(24 * 60), now.plusSeconds(60 * 60))
        val s = panelUiState(AgendaState(config, listOf(mine, next), now), now, idle, muscat)!!
        assertEquals(listOf(15), s.extendOptions) // +30 would run into the next meeting
        assertEquals(4L, s.endingInMinutes)
    }

    @Test
    fun `dimming can be switched off in the portal`() {
        val night = Instant.parse("2026-10-07T20:00:00Z") // midnight in Muscat
        val on = panelUiState(AgendaState(config, emptyList(), night), night, idle, muscat)!!
        val off = panelUiState(AgendaState(config.copy(branding = Branding(dimOutsideHours = false)), emptyList(), night), night, idle, muscat)!!
        assertEquals(BrightnessSchedule.DIMMED, on.brightness)
        assertEquals(BrightnessSchedule.FULL, off.brightness)
    }
}
