package com.rihal.roompanel.data

import com.rihal.roompanel.domain.Meeting
import kotlinx.coroutines.flow.StateFlow

/**
 * The panel's only data source. Phase 2 implements this against the backend's `/api/device/...`
 * routes; the tablet never talks to Microsoft Graph directly (see docs/PLAN.md).
 */
interface AgendaRepository {
    /** Today's meetings for this room. */
    val meetings: StateFlow<List<Meeting>>

    suspend fun refresh(): Result<Unit>
    suspend fun bookNow(minutes: Int): Result<Meeting>
    suspend fun extend(meetingId: String, minutes: Int): Result<Unit>
    suspend fun endEarly(meetingId: String): Result<Unit>
    suspend fun checkIn(meetingId: String): Result<Unit>
}
