package com.rihal.roompanel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rihal.roompanel.data.AgendaRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
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
    val lastError: String?,
    /** Window brightness, 0..1, from [BrightnessSchedule]. */
    val brightness: Float = BrightnessSchedule.FULL,
)

class PanelViewModel(
    private val repository: AgendaRepository,
    private val config: PanelConfig,
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

    val state: StateFlow<PanelUiState> =
        combine(repository.meetings, ticker, actionInFlight, lastError) { meetings, now, busy, error ->
            val status = RoomStatusCalculator.compute(meetings, now, config.startingSoonWindow)
            PanelUiState(
                config = config,
                now = now,
                status = status,
                upcoming = meetings.filter { it.end.isAfter(now) }.sortedBy { it.start },
                bookOptions = BookingRules.bookNowOptions(meetings, now, config),
                canExtendCurrent = status is RoomStatus.Busy &&
                    BookingRules.canExtend(status.current, meetings, config.extendStepMinutes),
                actionInFlight = busy,
                lastError = error,
                brightness = BrightnessSchedule.brightnessFor(now.atZone(clock.zone), status, config.workingHours),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    init {
        viewModelScope.launch {
            while (true) {
                repository.refresh()
                delay(REFRESH_MILLIS)
            }
        }
    }

    fun bookNow(minutes: Int) = run { repository.bookNow(minutes).map { } }
    fun extend(meetingId: String) = run { repository.extend(meetingId, config.extendStepMinutes) }
    fun endEarly(meetingId: String) = run { repository.endEarly(meetingId) }
    fun checkIn(meetingId: String) = run { repository.checkIn(meetingId) }
    fun dismissError() { lastError.value = null }

    private fun run(action: suspend () -> Result<Unit>) {
        if (actionInFlight.value) return
        viewModelScope.launch {
            actionInFlight.value = true
            action().onFailure { lastError.value = it.message ?: it.javaClass.simpleName }
            actionInFlight.value = false
        }
    }

    private fun initialState(): PanelUiState {
        val now = clock.instant()
        val meetings = repository.meetings.value
        return PanelUiState(
            config = config,
            now = now,
            status = RoomStatusCalculator.compute(meetings, now, config.startingSoonWindow),
            upcoming = meetings.filter { it.end.isAfter(now) },
            bookOptions = BookingRules.bookNowOptions(meetings, now, config),
            canExtendCurrent = false,
            actionInFlight = false,
            lastError = null,
        )
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
        const val REFRESH_MILLIS = 30_000L
    }
}
