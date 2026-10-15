package com.rihal.roompanel.data

import android.util.Log
import com.rihal.roompanel.data.api.AgendaDto
import com.rihal.roompanel.data.api.ApiException
import com.rihal.roompanel.data.api.BackendClient
import com.rihal.roompanel.data.api.FreeRoomsDto
import com.rihal.roompanel.data.api.isOffline
import com.rihal.roompanel.data.api.toConfig
import com.rihal.roompanel.data.api.toFreeRoom
import com.rihal.roompanel.data.api.toMeeting
import com.rihal.roompanel.domain.FreeRoom
import com.rihal.roompanel.domain.IssueCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** Agenda from the backend. Keeps the last good agenda through failures so the door never goes blank. */
class RemoteAgendaRepository(
    private val client: BackendClient,
    private val clock: Clock = Clock.systemUTC(),
    private val mover: ServerMover? = null,
) : AgendaRepository {

    private val mutable = MutableStateFlow(AgendaState())
    override val state: StateFlow<AgendaState> = mutable.asStateFlow()

    /**
     * Never throws (except cancellation): a bad answer must not crash the kiosk app, which would
     * look like "nothing happened" on the door. Unknown failures are logged and shown as stale.
     */
    override suspend fun refresh() {
        try {
            // days=2: today and tomorrow, for the agenda list's free slots (backend 0.4+).
            val agenda = client.get("/api/device/agenda?days=2", AgendaDto.serializer())
            mutable.value = AgendaState(
                config = agenda.room.toConfig(agenda.panel, agenda.guestWifi),
                meetings = agenda.meetings.mapNotNull { dto -> runCatching { dto.toMeeting() }.getOrNull() },
                lastSyncedAt = clock.instant(),
                problem = null,
            )
            // The portal is moving: switch once the new address knows this tablet (never throws).
            mover?.consider(agenda.move?.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = classify(e) ?: SyncProblem.CALENDAR_UNAVAILABLE.also { Log.w(TAG, "agenda failed: ${e.javaClass.simpleName}") }
            mutable.update { it.copy(problem = problem) }
        }
    }

    override suspend fun bookNow(minutes: Int, start: Instant?, presetIndex: Int?) = action(
        "/api/device/book",
        buildJsonObject {
            put("minutes", minutes)
            start?.let { put("start", it.toString()) }
            presetIndex?.let { put("presetIndex", it) }
        }.toString(),
    )
    override suspend fun extend(meetingId: String, minutes: Int) =
        action(BackendClient.eventPath(meetingId, "extend"), """{"minutes":$minutes}""")
    override suspend fun endEarly(meetingId: String) = action(BackendClient.eventPath(meetingId, "end"), "{}")
    override suspend fun checkIn(meetingId: String) = action(BackendClient.eventPath(meetingId, "checkin"), "{}")

    override suspend fun freeRooms(): Result<List<FreeRoom>> = attempt {
        client.get("/api/device/free-rooms", FreeRoomsDto.serializer()).rooms.map { it.toFreeRoom() }
    }

    override suspend fun reportIssue(category: IssueCategory): Result<Unit> = attempt {
        client.post("/api/device/issues", """{"category":"${category.name}"}""", JsonElement.serializer())
        Unit
    }

    override suspend fun image(path: String): ByteArray? = try {
        client.getBytes(path)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "image failed: ${e.javaClass.simpleName}")
        null
    }

    /** Any failure becomes a Result with a code the screen can name; nothing escapes to crash the app. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.status == 401) mutable.update { it.copy(problem = SyncProblem.UNPAIRED) }
        Result.failure(PanelActionException(e.code, e.message ?: "Request failed"))
    } catch (e: Exception) {
        Log.w(TAG, "action failed: ${e.javaClass.simpleName}")
        Result.failure(PanelActionException(if (e.isOffline()) "offline" else "unknown", e.message ?: "Failed"))
    }
    /** POST, then refresh so the door shows the result immediately. */
    private suspend fun action(path: String, body: String): Result<Unit> {
        val result = attempt { client.post(path, body, JsonElement.serializer()); Unit }
        refresh()
        return result
    }

    private fun classify(e: Exception): SyncProblem? = when {
        e.isOffline() -> SyncProblem.OFFLINE
        e is ApiException && e.status == 401 -> SyncProblem.UNPAIRED
        e is ApiException && e.code == "calendar_not_configured" -> SyncProblem.CALENDAR_NOT_CONFIGURED
        e is ApiException -> SyncProblem.CALENDAR_UNAVAILABLE
        else -> null
    }

    private companion object {
        const val TAG = "RoomPanel"
    }
}
