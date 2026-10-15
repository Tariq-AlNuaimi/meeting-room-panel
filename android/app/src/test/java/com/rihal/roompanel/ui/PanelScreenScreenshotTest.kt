package com.rihal.roompanel.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.rihal.roompanel.domain.Branding
import com.rihal.roompanel.domain.FreeRoom
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelLanguage
import com.rihal.roompanel.ui.TestStates.at
import com.rihal.roompanel.ui.TestStates.config
import com.rihal.roompanel.ui.TestStates.private
import com.rihal.roompanel.ui.TestStates.review
import com.rihal.roompanel.ui.TestStates.tomorrow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the panel (design A) on a 10" landscape tablet in each state.
 * Record: ./gradlew recordRoborazziDebug · Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class PanelScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: PanelUiState, name: String, freeRooms: FreeRoomsState = FreeRoomsState.Closed, then: () -> Unit = {}) {
        compose.setContent { PanelTheme { PanelScreen(state, freeRooms = freeRooms) } }
        then()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val day = listOf(review, private, tomorrow)
    private val busyDay = listOf(review.copy(start = at(-20), end = at(40)), private, tomorrow)

    @Test
    fun free() = render(TestStates.state(day), "free")

    @Test
    fun startingSoon() = render(TestStates.state(listOf(review.copy(start = at(5), end = at(65)), private, tomorrow)), "starting_soon")

    @Test
    fun busy() = render(TestStates.state(busyDay), "busy")

    @Test
    fun busyPanelBookingEndingSoon() = render(
        TestStates.state(listOf(Meeting("mine", "Client call", null, at(-26), at(4), bookedFromPanel = true, checkedIn = true), review, tomorrow)),
        "busy_ending_soon",
    )

    @Test
    fun busyTeamsWithReleaseCountdown() = render(
        TestStates.state(
            listOf(
                review.copy(
                    start = at(-5), end = at(55),
                    joinUrl = "https://teams.microsoft.com/l/meetup-join/19%3ameeting_demo%40thread.v2/0",
                    releaseAt = at(5),
                ),
                private,
            ),
        ),
        "busy_teams_release",
    )

    @Test
    fun staleOffline() = render(TestStates.state(busyDay, stale = true), "stale_offline")

    @Test
    fun outOfService() = render(
        TestStates.state(day, config = config.copy(outOfService = true, outOfServiceMessage = "Projector being repaired. Please use the Majlis.")),
        "out_of_service",
    )

    @Test
    fun brandedNoWifi() = render(
        TestStates.state(day, config = config.copy(guestWifi = null, branding = config.branding.copy(primaryColor = 0xFF5B2A86, clock24h = false))),
        "branded_12h",
    )

    @Test
    fun bookingSheet() = render(TestStates.state(day), "booking_sheet") {
        compose.onNodeWithText("Book now").performClick()
        compose.onNodeWithText("Client call").performClick()
    }

    @Test
    fun bookingSheetLaterSlot() = render(TestStates.state(day), "booking_sheet_later") {
        compose.onNodeWithText("Free 11:30 – 18:00").performClick()
    }

    @Test
    fun booked() = render(
        TestStates.state(day, feedback = ActionFeedback(success = ActionResult.Booked(at(0), at(30), later = false))),
        "booked_confirmation",
    )

    @Test
    fun bookingFailed() = render(TestStates.state(day, feedback = ActionFeedback(error = "conflict")), "booking_failed")

    @Test
    fun reportSheet() = render(TestStates.state(day), "report_sheet") {
        compose.onNodeWithText("Report a problem").performClick()
    }

    @Test
    fun freeRooms() = render(
        TestStates.state(busyDay),
        "free_rooms",
        FreeRoomsState.Loaded(listOf(FreeRoom("Majlis", "Ground floor", 12, at(90)), FreeRoom("Focus Room", "2nd floor", 4, null))),
    )

    @Test
    fun pairing() {
        compose.setContent {
            PanelTheme { com.rihal.roompanel.ui.pairing.PairingScreen(com.rihal.roompanel.ui.pairing.PairingState.ShowingCode("K7QM-3XRP"), "meeting-room-backend-cyan.vercel.app") }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/pairing.png")
    }

    @Test
    fun emptyDay() = render(TestStates.state(emptyList(), config = config.copy(guestWifi = null, branding = Branding())), "empty_day")

    @Test
    fun busyArabicRtl() = render(
        TestStates.state(busyDay, config = config.copy(branding = config.branding.copy(language = PanelLanguage.AR))),
        "busy_ar",
    )

    @Test
    fun freeArabicRtl() = render(
        TestStates.state(day, config = config.copy(branding = config.branding.copy(language = PanelLanguage.AR))),
        "free_ar",
    )
}

/** The same screens at the smallest size a 10" tablet may report, to prove nothing is cut off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-hdpi")
class CompactScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: PanelUiState, name: String) {
        compose.setContent { PanelTheme { PanelScreen(state) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/compact_$name.png")
    }

    @Test
    fun free() = render(TestStates.state(listOf(review, private, tomorrow)), "free")

    @Test
    fun busy() = render(TestStates.state(listOf(review.copy(start = at(-20), end = at(40)), private, tomorrow)), "busy")

    @Test
    fun bookingSheet() {
        compose.setContent { PanelTheme { PanelScreen(TestStates.state(listOf(review, private, tomorrow))) } }
        compose.onNodeWithText("Book now").performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/compact_booking_sheet.png")
    }
}
