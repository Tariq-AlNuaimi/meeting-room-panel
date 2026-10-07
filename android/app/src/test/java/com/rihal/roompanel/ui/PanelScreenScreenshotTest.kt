package com.rihal.roompanel.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * Renders the panel on a 10" landscape tablet in each room state.
 * Record: ./gradlew recordRoborazziDebug · Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class PanelScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-07T09:00:00Z")
    private val config = PanelConfig(roomName = "Board Room", attendeeDisplay = AttendeeDisplay.NAMES)

    private fun at(minutes: Long) = now.plus(Duration.ofMinutes(minutes))

    private val review = Meeting(
        "review", "Product review", "Omar Al-Harthy", at(25), at(85),
        attendees = listOf("Omar Al-Harthy", "Tariq Al-Naaimi", "Layla Al-Rawahi", "Khalid Al-Lawati"),
        attendeeCount = 4,
    )
    private val private = Meeting("private", null, null, at(120), at(150), isPrivate = true)

    private fun render(meetings: List<Meeting>, name: String, stale: Boolean = false) {
        val status = RoomStatusCalculator.compute(meetings, now, config.startingSoonWindow)
        val state = PanelUiState(
            config = config,
            now = now,
            status = status,
            upcoming = meetings.filter { it.end.isAfter(now) }.sortedBy { it.start },
            bookOptions = BookingRules.bookNowOptions(meetings, now, config),
            canExtendCurrent = false,
            actionInFlight = false,
            lastError = null,
            stale = stale,
            problem = if (stale) com.rihal.roompanel.data.SyncProblem.OFFLINE else null,
        )
        compose.setContent {
            PanelTheme { PanelScreen(state, onBook = {}, onExtend = {}, onEnd = {}, onCheckIn = {}, onDismissError = {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun free() = render(listOf(review, private), "free")

    @Test
    fun startingSoon() = render(listOf(review.copy(start = at(5), end = at(65)), private), "starting_soon")

    @Test
    fun busy() = render(listOf(review.copy(start = at(-20), end = at(40)), private), "busy")

    @Test
    fun staleOffline() = render(listOf(review.copy(start = at(-20), end = at(40)), private), "stale_offline", stale = true)

    @Test
    fun pairing() {
        compose.setContent {
            PanelTheme { com.rihal.roompanel.ui.pairing.PairingScreen(com.rihal.roompanel.ui.pairing.PairingState.ShowingCode("K7QM-3XRP"), "meeting-room-backend-cyan.vercel.app") }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/pairing.png")
    }

    @Test
    fun emptyDay() = render(emptyList(), "empty_day")

    @Test
    @Config(qualifiers = "+ar")
    fun busyArabicRtl() = render(listOf(review.copy(start = at(-20), end = at(40)), private), "busy_ar")
}
