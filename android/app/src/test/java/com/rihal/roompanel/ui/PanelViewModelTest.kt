package com.rihal.roompanel.ui

import com.rihal.roompanel.data.AgendaRepository
import com.rihal.roompanel.data.AgendaState
import com.rihal.roompanel.data.PanelActionException
import com.rihal.roompanel.domain.FreeRoom
import com.rihal.roompanel.domain.IssueCategory
import com.rihal.roompanel.domain.PanelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Booking outcomes in the view model: nothing may escape, everything must show. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PanelViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-07T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(var book: suspend () -> Result<Unit>) : AgendaRepository {
        val calls = mutableListOf<Triple<Int, Instant?, Int?>>()
        override val state: StateFlow<AgendaState> =
            MutableStateFlow(AgendaState(PanelConfig("Board Room"), emptyList(), Instant.parse("2026-10-07T09:00:00Z")))
        override suspend fun refresh() {}
        override suspend fun bookNow(minutes: Int, start: Instant?, presetIndex: Int?): Result<Unit> {
            calls += Triple(minutes, start, presetIndex)
            return book()
        }
        override suspend fun extend(meetingId: String, minutes: Int) = Result.success(Unit)
        override suspend fun endEarly(meetingId: String) = Result.success(Unit)
        override suspend fun checkIn(meetingId: String) = Result.success(Unit)
        override suspend fun freeRooms() = Result.success(emptyList<FreeRoom>())
        override suspend fun reportIssue(category: IssueCategory) = Result.success(Unit)
        override suspend fun image(path: String): ByteArray? = null
    }

    private fun feedbackOf(vm: PanelViewModel): ActionFeedback {
        // Read the private flow through the public state; collect it once so it is computed.
        return vm.state.value?.feedback ?: error("no state")
    }

    @Test
    fun `an unexpected crash inside a booking becomes a visible error, not an app restart`() = runTest(dispatcher) {
        val repo = FakeRepo { throw IllegalStateException("boom") }
        val vm = PanelViewModel(repo, clock, { null }, autoRefresh = false)
        val job = backgroundScope.launchCollect(vm)
        vm.book(30)
        runCurrent()
        assertEquals("unknown", feedbackOf(vm).error)
        assertEquals(listOf(Triple(30, null as Instant?, null as Int?)), repo.calls)
        job.cancel()
    }

    @Test
    fun `a refused booking names the reason, and a success says until when`() = runTest(dispatcher) {
        val repo = FakeRepo { Result.failure(PanelActionException("conflict", "taken")) }
        val vm = PanelViewModel(repo, clock, { null }, autoRefresh = false)
        val job = backgroundScope.launchCollect(vm)
        vm.book(30)
        runCurrent()
        assertEquals("conflict", feedbackOf(vm).error)

        repo.book = { Result.success(Unit) }
        vm.book(15, now.plusSeconds(3600), 1)
        runCurrent()
        val ok = feedbackOf(vm).success as ActionResult.Booked
        assertEquals(now.plusSeconds(3600 + 15 * 60), ok.until)
        assertTrue(ok.later)
        assertEquals(Triple(15, now.plusSeconds(3600), 1), repo.calls.last())

        advanceTimeBy(7_000)
        runCurrent()
        assertNull("the confirmation clears itself", feedbackOf(vm).success)
        job.cancel()
    }

    @Test
    fun `a second tap while booking is ignored, not sent twice`() = runTest(dispatcher) {
        val repo = FakeRepo { kotlinx.coroutines.delay(1_000); Result.success(Unit) }
        val vm = PanelViewModel(repo, clock, { null }, autoRefresh = false)
        val job = backgroundScope.launchCollect(vm)
        vm.book(30)
        runCurrent()
        assertTrue(feedbackOf(vm).inFlight)
        vm.book(30)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, repo.calls.size)
        job.cancel()
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchCollect(vm: PanelViewModel) =
    launch { vm.state.collect {} }
