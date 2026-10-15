package com.rihal.roompanel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.rihal.roompanel.domain.Meeting
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * A full day plus tomorrow must scroll in the agenda pane, at the smallest landscape size a
 * 10" tablet reports: the last row is reachable by swiping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-hdpi")
class AgendaScrollTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longDayScrollsToTomorrowsLastMeeting() {
        val now = TestStates.now
        val today = (0 until 10).map { i ->
            val start = now.plus(Duration.ofMinutes(30L + i * 45))
            Meeting("m$i", "Meeting number $i", "Org", start, start.plus(Duration.ofMinutes(30)))
        }
        val last = Meeting("last", "Tomorrow's last meeting", "Org", TestStates.at(24 * 60 + 400), TestStates.at(24 * 60 + 430))
        val state = TestStates.state(today + last)
        compose.setContent { PanelTheme { PanelScreen(state) } }
        val list = compose.onNode(hasScrollToIndexAction())
        list.assertExists("the agenda must be a scrollable list")
        repeat(12) { list.performTouchInput { swipeUp() } }
        compose.onNodeWithText("Tomorrow's last meeting").assertIsDisplayed()
    }
}
