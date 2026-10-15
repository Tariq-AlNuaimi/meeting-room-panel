package com.rihal.roompanel.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rihal.roompanel.data.AgendaRepository
import com.rihal.roompanel.data.AgendaState
import com.rihal.roompanel.data.PanelActionException
import com.rihal.roompanel.data.SyncProblem
import com.rihal.roompanel.domain.AgendaEntry
import com.rihal.roompanel.domain.AgendaPlanner
import com.rihal.roompanel.domain.BookingRules
import com.rihal.roompanel.domain.BrightnessSchedule
import com.rihal.roompanel.domain.FreeRoom
import com.rihal.roompanel.domain.IssueCategory
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.RoomStatus
import com.rihal.roompanel.domain.RoomStatusCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/** What the last action did, shown as a short confirmation so a tap never looks ignored. */
sealed interface ActionResult {
    data class Booked(val start: Instant, val until: Instant, val later: Boolean) : ActionResult
    data class Extended(val until: Instant) : ActionResult
    data object Ended : ActionResult
    data object CheckedIn : ActionResult
    data object Reported : ActionResult
}

/** Short-lived feedback around actions: in flight, the last success, or the last error code. */
data class ActionFeedback(
    val inFlight: Boolean = false,
    val success: ActionResult? = null,
    /** Backend error code of the last failed action ("conflict", "offline", ...). */
    val error: String? = null,
)

data class PanelImages(val logo: ImageBitmap? = null, val background: ImageBitmap? = null)

data class PanelUiState(
    val config: PanelConfig,
    val now: Instant,
    val status: RoomStatus,
    /** Today and tomorrow: day headings, meetings that haven't ended, and free slots. */
    val agenda: List<AgendaEntry>,
    /** Book-now lengths that fit right now; empty when busy, stale, or booking is off. */
    val bookOptions: List<Int>,
    /** "+15" / "+30" that fit, for the running panel booking. */
    val extendOptions: List<Int> = emptyList(),
    /** Minutes left when the current meeting ends within 5 minutes (the end warning). */
    val endingInMinutes: Long? = null,
    val feedback: ActionFeedback = ActionFeedback(),
    /** Window brightness, 0..1, from [BrightnessSchedule]. */
    val brightness: Float = BrightnessSchedule.FULL,
    /** The agenda on screen may be out of date: show the banner and disable actions. */
    val stale: Boolean = false,
    val problem: SyncProblem? = null,
    val images: PanelImages = PanelImages(),
) {
    val actionInFlight: Boolean get() = feedback.inFlight
    val bookingAllowed: Boolean get() = config.canBook && !stale
}

/** Pure mapping from repository state to what the screen shows. Null until the first sync. */
fun panelUiState(
    agenda: AgendaState,
    now: Instant,
    feedback: ActionFeedback,
    zone: ZoneId,
    images: PanelImages = PanelImages(),
): PanelUiState? {
    val config = agenda.config ?: return null
    val meetings = agenda.meetings
    val status = RoomStatusCalculator.compute(meetings, now, config.startingSoonWindow)
    val stale = agenda.lastSyncedAt == null || Duration.between(agenda.lastSyncedAt, now) > STALE_AFTER
    val current = (status as? RoomStatus.Busy)?.current
    val brightness = if (config.branding.dimOutsideHours) {
        BrightnessSchedule.brightnessFor(now.atZone(zone), status, config.workingHours)
    } else {
        BrightnessSchedule.FULL
    }
    return PanelUiState(
        config = config,
        now = now,
        status = status,
        agenda = AgendaPlanner.plan(meetings, now, config),
        // Never offer a booking on top of an agenda we can't vouch for.
        bookOptions = if (stale) emptyList() else BookingRules.bookNowOptions(meetings, now, config),
        extendOptions = if (stale || current == null) emptyList() else BookingRules.extendOptions(current, meetings, config),
        endingInMinutes = AgendaPlanner.endingSoon(current, now),
        feedback = feedback,
        brightness = brightness,
        stale = stale,
        problem = agenda.problem,
        images = images,
    )
}

/** Two missed polls and a margin: past this, the door says "may be out of date". */
private val STALE_AFTER: Duration = Duration.ofSeconds(90)

sealed interface FreeRoomsState {
    data object Closed : FreeRoomsState
    data object Loading : FreeRoomsState
    data class Loaded(val rooms: List<FreeRoom>) : FreeRoomsState
    data object Failed : FreeRoomsState
}

class PanelViewModel(
    private val repository: AgendaRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val decodeImage: (ByteArray) -> ImageBitmap? = ::decodeBitmap,
    /** Tests switch the 30 s poll off: an endless timer loop never lets a test scheduler go idle. */
    autoRefresh: Boolean = true,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }
    private val feedback = MutableStateFlow(ActionFeedback())
    private val images = MutableStateFlow(PanelImages())
    private var clearFeedback: Job? = null

    /** Null while the first agenda is loading. */
    val state: StateFlow<PanelUiState?> =
        combine(repository.state, ticker, feedback, images) { agenda, now, fb, img ->
            panelUiState(agenda, now, fb, clock.zone, img)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once the backend has rejected this tablet's token (revoked, or its room deleted). */
    val unpaired: StateFlow<Boolean> = repository.state
        .map { it.problem == SyncProblem.UNPAIRED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val freeRoomsMutable = MutableStateFlow<FreeRoomsState>(FreeRoomsState.Closed)
    val freeRooms: StateFlow<FreeRoomsState> = freeRoomsMutable.asStateFlow()

    init {
        if (autoRefresh) viewModelScope.launch {
            while (true) {
                guarded("refresh") { repository.refresh() }
                delay(REFRESH_MILLIS)
            }
        }
        // Branding images: fetched when their (versioned) path changes, kept otherwise.
        viewModelScope.launch {
            repository.state
                .map { it.config?.branding?.let { b -> b.logoPath to b.backgroundPath } }
                .distinctUntilChanged()
                .collect { paths ->
                    guarded("images") {
                        images.value = PanelImages(
                            logo = paths?.first?.let { p -> repository.image(p)?.let(decodeImage) },
                            background = paths?.second?.let { p -> repository.image(p)?.let(decodeImage) },
                        )
                    }
                }
        }
    }

    /** Book from now ([start] null) or a later slot; [presetIndex] picks the title. */
    fun book(minutes: Int, start: Instant? = null, presetIndex: Int? = null) = run(
        action = { repository.bookNow(minutes, start, presetIndex) },
        success = {
            val from = start ?: clock.instant()
            ActionResult.Booked(from, from.plus(Duration.ofMinutes(minutes.toLong())), later = start != null)
        },
    )

    fun extend(meetingId: String, minutes: Int) = run(
        action = { repository.extend(meetingId, minutes) },
        success = {
            val end = repository.state.value.meetings.firstOrNull { it.id == meetingId }?.end ?: clock.instant()
            ActionResult.Extended(end)
        },
    )

    fun endEarly(meetingId: String) = run({ repository.endEarly(meetingId) }) { ActionResult.Ended }
    fun checkIn(meetingId: String) = run({ repository.checkIn(meetingId) }) { ActionResult.CheckedIn }
    fun reportIssue(category: IssueCategory) = run({ repository.reportIssue(category) }) { ActionResult.Reported }
    fun dismissFeedback() = feedback.update { it.copy(success = null, error = null) }

    fun openFreeRooms() {
        freeRoomsMutable.value = FreeRoomsState.Loading
        viewModelScope.launch {
            val result = try {
                repository.freeRooms()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (freeRoomsMutable.value == FreeRoomsState.Closed) return@launch
            freeRoomsMutable.value = result.fold({ FreeRoomsState.Loaded(it) }, { FreeRoomsState.Failed })
        }
    }

    fun closeFreeRooms() {
        freeRoomsMutable.value = FreeRoomsState.Closed
    }

    /**
     * Every outcome is visible: in flight, then a confirmation or a named error. Nothing thrown
     * here may escape: an uncaught exception would restart the kiosk app, which on the door looks
     * exactly like "I tapped and nothing happened".
     */
    private fun run(action: suspend () -> Result<Unit>, success: () -> ActionResult) {
        if (feedback.value.inFlight) return
        clearFeedback?.cancel()
        feedback.value = ActionFeedback(inFlight = true)
        viewModelScope.launch {
            val result = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "action crashed: ${e.javaClass.simpleName}")
                Result.failure(e)
            }
            feedback.value = result.fold(
                onSuccess = { ActionFeedback(success = success()) },
                onFailure = { ActionFeedback(error = (it as? PanelActionException)?.code ?: "unknown") },
            )
            clearFeedback = viewModelScope.launch {
                delay(if (result.isSuccess) SUCCESS_MILLIS else ERROR_MILLIS)
                dismissFeedback()
            }
        }
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "RoomPanel"
        const val TICK_MILLIS = 15_000L
        const val REFRESH_MILLIS = 30_000L
        const val SUCCESS_MILLIS = 6_000L
        const val ERROR_MILLIS = 12_000L
    }
}

private fun decodeBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
