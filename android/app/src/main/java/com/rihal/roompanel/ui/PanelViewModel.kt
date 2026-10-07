package com.rihal.roompanel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rihal.roompanel.data.AgendaRepository
import com.rihal.roompanel.data.AgendaState
import com.rihal.roompanel.data.PanelActionException
import com.rihal.roompanel.data.SyncProblem
import com.rihal.roompanel.domain.BookingRules
import com.rihal.roompanel.domain.BrightnessSchedule
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.RoomStatus
import com.rihal.roompanel.domain.RoomStatusCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class PanelUiState(
    val config: PanelConfig,
    val now: Instant,
    val status: RoomStatus,
    /** Meetings that have not ended yet, in start order. */
    val upcoming: List<Meeting>,
    val bookOptions: List<Int>,
    val canExtendCurrent: Boolean,
    val actionInFlight: Boolean,
    /** Backend error code of the last failed action ("conflict", "offline", ...), or null. */
    val lastError: String?,
    /** Window brightness, 0..1, from [BrightnessSchedule]. */
    val brightness: Float = BrightnessSchedule.FULL,
    /** The agenda on screen may be out of date: show the banner and disable actions. */
    val stale: Boolean = false,
    val problem: SyncProblem? = null,
)

/** Pure mapping from repository state to what the screen shows. Null until the first sync. */
fun panelUiState(
    agenda: AgendaState,
    now: Instant,
    actionInFlight: Boolean,
    lastError: String?,
    zone: java.time.ZoneId,
): PanelUiState? {
    val config = agenda.config ?: return null
    val meetings = agenda.meetings
    val status = RoomStatusCalculator.compute(meetings, now, config.startingSoonWindow)
    val stale = agenda.lastSyncedAt == null || Duration.between(agenda.lastSyncedAt, now) > STALE_AFTER
    return PanelUiState(
        config = config,
        now = now,
        status = status,
        upcoming = meetings.filter { it.end.isAfter(now) }.sortedBy { it.start },
        // Never offer a booking on top of an agenda we can't vouch for.
        bookOptions = if (stale) emptyList() else BookingRules.bookNowOptions(meetings, now, config),
        canExtendCurrent = !stale && status is RoomStatus.Busy &&
            BookingRules.canExtend(status.current, meetings, config.extendStepMinutes),
        actionInFlight = actionInFlight,
        lastError = lastError,
        brightness = BrightnessSchedule.brightnessFor(now.atZone(zone), status, config.workingHours),
        stale = stale,
        problem = agenda.problem,
    )
}

/** Two missed polls and a margin: past this, the door says "may be out of date". */
private val STALE_AFTER: Duration = Duration.ofSeconds(90)

class PanelViewModel(
    private val repository: AgendaRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }
    private val actionInFlight = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)

    /** Null while the first agenda is loading. */
    val state: StateFlow<PanelUiState?> =
        combine(repository.state, ticker, actionInFlight, lastError) { agenda, now, busy, error ->
            panelUiState(agenda, now, busy, error, clock.zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once the backend has rejected this tablet's token (revoked, or its room deleted). */
    val unpaired: StateFlow<Boolean> = repository.state
        .map { it.problem == SyncProblem.UNPAIRED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            while (true) {
                repository.refresh()
                delay(REFRESH_MILLIS)
            }
        }
    }

    fun bookNow(minutes: Int) = run { repository.bookNow(minutes) }
    fun extend(meetingId: String) = run {
        repository.extend(meetingId, repository.state.value.config?.extendStepMinutes ?: 15)
    }
    fun endEarly(meetingId: String) = run { repository.endEarly(meetingId) }
    fun checkIn(meetingId: String) = run { repository.checkIn(meetingId) }
    fun dismissError() { lastError.value = null }

    private fun run(action: suspend () -> Result<Unit>) {
        if (actionInFlight.value) return
        viewModelScope.launch {
            actionInFlight.value = true
            action().onFailure { lastError.value = (it as? PanelActionException)?.code ?: "unknown" }
            actionInFlight.value = false
        }
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
        const val REFRESH_MILLIS = 30_000L
    }
}
