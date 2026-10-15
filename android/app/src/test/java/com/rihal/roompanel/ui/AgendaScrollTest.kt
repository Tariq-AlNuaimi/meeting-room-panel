package com.rihal.roompanel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.BookingRules
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.RoomStatusCalculator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/** A full day must scroll in the agenda pane: the last meeting is reachable by swiping. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class AgendaScrollTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longDayScrollsToTheLastMeeting() {
        val now = Instant.parse("2026-10-15T05:00:00Z")
        val meetings = (0 until 14).map { i ->
            val start = now.plus(Duration.ofMinutes(30L + i * 45))
            Meeting("m$i", "Meeting number $i", "Org", start, start.plus(Duration.ofMinutes(30)))
        }
        val config = PanelConfig(roomName = "Innovation Hub", attendeeDisplay = AttendeeDisplay.COUNT)
        val state = PanelUiState(
            config = config, now = now,
            status = RoomStatusCalculator.compute(meetings, now, config.startingSoonWindow),
            upcoming = meetings, bookOptions = BookingRules.bookNowOptions(meetings, now, config),
            canExtendCurrent = false, actionInFlight = false, lastError = null, stale = false, problem = null,
        )
        compose.setContent { PanelTheme { PanelScreen(state, {}, {}, {}, {}, {}) } }
        val list = compose.onNode(hasScrollToIndexAction())
        list.assertExists("the agenda must be a scrollable list")
        repeat(8) { list.performTouchInput { swipeUp() } }
        compose.onNodeWithText("Meeting number 13").assertIsDisplayed()
    }
}
