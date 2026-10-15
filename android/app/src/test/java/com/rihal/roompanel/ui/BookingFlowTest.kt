package com.rihal.roompanel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rihal.roompanel.domain.IssueCategory
import com.rihal.roompanel.ui.TestStates.at
import com.rihal.roompanel.ui.TestStates.config
import com.rihal.roompanel.ui.TestStates.private
import com.rihal.roompanel.ui.TestStates.review
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The fix for "tapped, nothing happened": every tap reaches the app, and every outcome shows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class BookingFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private var shown by mutableStateOf(TestStates.state(emptyList()))

    private data class Booking(val minutes: Int, val start: Instant?, val preset: Int?)

    @Test
    fun bookNowIsTwoTapsWithTheFirstTitlePreselected() {
        val booked = mutableListOf<Booking>()
        compose.setContent {
            // Free until 11:00, so 15, 30 and 60 minutes all fit.
            PanelTheme { PanelScreen(TestStates.state(listOf(private)), PanelActions(onBook = { m, s, p -> booked += Booking(m, s, p) })) }
        }
        compose.onNodeWithText("Book now").performClick()
        compose.onNodeWithText("30 min").performClick()
        assertEquals(listOf(Booking(30, null, 0)), booked)
    }

    @Test
    fun aChosenTitleAndALaterSlotAreSent() {
        val booked = mutableListOf<Booking>()
        compose.setContent {
            PanelTheme { PanelScreen(TestStates.state(listOf(review, private)), PanelActions(onBook = { m, s, p -> booked += Booking(m, s, p) })) }
        }
        // 09:00 now; review 09:25-10:25; private 11:00-11:30; next slot 10:30 (rounded up to the quarter).
        compose.onNodeWithText("Free 10:30 – 11:00").performClick()
        compose.onNodeWithText("Book from 10:30").assertIsDisplayed()
        compose.onNodeWithText("Interview").performClick()
        compose.onNodeWithText("30 min").performClick()
        assertEquals(listOf(Booking(30, at(90), 2)), booked)
    }

    @Test
    fun outOfServiceShowsTheMessageAndNoBooking() {
        compose.setContent {
            PanelTheme { PanelScreen(TestStates.state(listOf(review), config = config.copy(outOfService = true, outOfServiceMessage = "Projector repair"))) }
        }
        compose.onNodeWithText("Out of service").assertIsDisplayed()
        compose.onNodeWithText("Projector repair").assertIsDisplayed()
        compose.onNodeWithText("Book now").assertDoesNotExist()
        compose.onNodeWithText("Book").assertDoesNotExist()
    }

    @Test
    fun bookingTurnedOffHidesEveryBookButton() {
        compose.setContent { PanelTheme { PanelScreen(TestStates.state(listOf(review), config = config.copy(allowTabletBooking = false))) } }
        compose.onNodeWithText("Book now").assertDoesNotExist()
        compose.onNodeWithText("Book").assertDoesNotExist()
    }

    @Test
    fun inFlightSuccessAndErrorAreAllVisible() {
        compose.setContent { PanelTheme { PanelScreen(shown) } }
        for ((fb, text) in listOf(
            ActionFeedback(inFlight = true) to "One moment…",
            ActionFeedback(success = ActionResult.Booked(at(0), at(30), later = false)) to "Booked until 09:30",
            ActionFeedback(error = "conflict") to "Someone just booked the room for that time. Pick a shorter booking.",
            ActionFeedback(error = "out_of_service") to "This room is out of service.",
            ActionFeedback(error = "something_new") to "Something went wrong. Try again.",
        )) {
            shown = TestStates.state(listOf(review), feedback = fb)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun reportAProblemIsOneTapPerCategory() {
        val reported = mutableListOf<IssueCategory>()
        compose.setContent { PanelTheme { PanelScreen(TestStates.state(listOf(review)), PanelActions(onReport = { reported += it })) } }
        compose.onNodeWithText("Report a problem").performClick()
        compose.onNodeWithText("Needs cleaning").performClick()
        assertEquals(listOf(IssueCategory.CLEANING), reported)
    }

    @Test
    fun guestWifiQrShowsOnlyWhileIdle() {
        shown = TestStates.state(listOf(review))
        compose.setContent { PanelTheme { PanelScreen(shown) } }
        compose.onNodeWithText("Scan to join Rihal-Guest").assertIsDisplayed()
        shown = TestStates.state(listOf(review.copy(start = at(-10), end = at(30))))
        compose.onNodeWithText("Scan to join Rihal-Guest").assertDoesNotExist()
    }
}
