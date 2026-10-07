package com.rihal.roompanel.data

import com.rihal.roompanel.data.api.AgendaDto
import com.rihal.roompanel.data.api.ApiException
import com.rihal.roompanel.data.api.BackendClient
import com.rihal.roompanel.data.api.isOffline
import com.rihal.roompanel.data.api.toConfig
import com.rihal.roompanel.data.api.toMeeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import java.time.Clock

/** Agenda from the backend. Keeps the last good agenda through failures so the door never goes blank. */
class RemoteAgendaRepository(
    private val client: BackendClient,
    private val clock: Clock = Clock.systemUTC(),
) : AgendaRepository {

    private val mutable = MutableStateFlow(AgendaState())
    override val state: StateFlow<AgendaState> = mutable.asStateFlow()

    override suspend fun refresh() {
        try {
            val agenda = client.get("/api/device/agenda", AgendaDto.serializer())
            mutable.value = AgendaState(
                config = agenda.room.toConfig(),
                meetings = agenda.meetings.map { it.toMeeting() },
                lastSyncedAt = clock.instant(),
                problem = null,
            )
        } catch (e: Exception) {
            val problem = classify(e) ?: throw e
            mutable.update { it.copy(problem = problem) }
        }
    }

    override suspend fun bookNow(minutes: Int) = action("/api/device/book", """{"minutes":$minutes}""")
    override suspend fun extend(meetingId: String, minutes: Int) =
        action(BackendClient.eventPath(meetingId, "extend"), """{"minutes":$minutes}""")
    override suspend fun endEarly(meetingId: String) = action(BackendClient.eventPath(meetingId, "end"), "{}")
    override suspend fun checkIn(meetingId: String) = action(BackendClient.eventPath(meetingId, "checkin"), "{}")

    /** POST, then refresh so the door shows the result immediately. */
    private suspend fun action(path: String, body: String): Result<Unit> {
        val result = try {
            client.post(path, body, JsonElement.serializer())
            Result.success(Unit)
        } catch (e: ApiException) {
            if (e.status == 401) mutable.update { it.copy(problem = SyncProblem.UNPAIRED) }
            Result.failure(PanelActionException(e.code, e.message ?: "Request failed"))
        } catch (e: Exception) {
            if (!e.isOffline()) throw e
            Result.failure(PanelActionException("offline", e.message ?: "Offline"))
        }
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
}
