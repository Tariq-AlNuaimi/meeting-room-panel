package com.rihal.roompanel.ui

import com.rihal.roompanel.data.AgendaState
import com.rihal.roompanel.data.SyncProblem
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
    private val config = PanelConfig(roomName = "Board Room")

    @Test
    fun `no screen state until the first sync brings the room settings`() {
        assertNull(panelUiState(AgendaState(), now, false, null, muscat))
    }

    @Test
    fun `fresh agenda offers booking`() {
        val s = panelUiState(AgendaState(config, emptyList(), now.minusSeconds(20)), now, false, null, muscat)!!
        assertFalse(s.stale)
        assertEquals(listOf(15, 30, 60), s.bookOptions)
    }

    @Test
    fun `stale agenda shows the banner and offers no booking`() {
        val s = panelUiState(AgendaState(config, emptyList(), now.minusSeconds(120), SyncProblem.OFFLINE), now, false, null, muscat)!!
        assertTrue(s.stale)
        assertTrue(s.bookOptions.isEmpty())
        assertEquals(SyncProblem.OFFLINE, s.problem)
    }
}
