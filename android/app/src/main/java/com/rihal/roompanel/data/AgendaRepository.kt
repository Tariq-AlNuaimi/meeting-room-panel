package com.rihal.roompanel.data

import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** Why the last sync failed; drives the stale banner and the unpaired flow. */
enum class SyncProblem {
    /** No answer from the backend (network down, timeout). */
    OFFLINE,
    /** The backend rejected this tablet's token: it was revoked or its room deleted. */
    UNPAIRED,
    /** The backend has no Microsoft 365 connection configured yet. */
    CALENDAR_NOT_CONFIGURED,
    /** Microsoft 365 answered with an error or is throttling. */
    CALENDAR_UNAVAILABLE,
}

data class AgendaState(
    /** Null until the first successful sync: the room's settings come from the server. */
    val config: PanelConfig? = null,
    val meetings: List<Meeting> = emptyList(),
    val lastSyncedAt: Instant? = null,
    val problem: SyncProblem? = null,
)

/** A panel action the backend refused, with its stable code (e.g. "conflict"). */
class PanelActionException(val code: String?, message: String) : Exception(message)

/**
 * The panel's only data source. The real implementation talks to the backend's
 * `/api/device/...` routes; the tablet never talks to Microsoft Graph directly.
 */
interface AgendaRepository {
    val state: StateFlow<AgendaState>

    suspend fun refresh()
    suspend fun bookNow(minutes: Int): Result<Unit>
    suspend fun extend(meetingId: String, minutes: Int): Result<Unit>
    suspend fun endEarly(meetingId: String): Result<Unit>
    suspend fun checkIn(meetingId: String): Result<Unit>
}
