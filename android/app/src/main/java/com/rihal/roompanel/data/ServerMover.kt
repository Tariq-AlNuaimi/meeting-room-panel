package com.rihal.roompanel.data

import com.rihal.roompanel.domain.normalizeServerUrl
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Follows the portal's "move tablets to a new address" (backend docs/self-hosting.md). The
 * tablet switches only after the new portal answers [ping] for its own token, which proves the
 * database was copied there; until then it stays where it is and asks again after [RETRY].
 */
class ServerMover(
    private val address: ServerAddressStore,
    private val ping: suspend (String) -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var lastFailure: Pair<String, Instant>? = null

    /** True when the tablet has just moved to [target]. Never throws, except cancellation. */
    suspend fun consider(target: String?): Boolean {
        val url = normalizeServerUrl(target) ?: return false
        if (url == normalizeServerUrl(address.current())) return false
        lastFailure?.let { (failed, at) ->
            if (failed == url && Duration.between(at, clock.instant()) < RETRY) return false
        }
        val accepted = try {
            ping(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (accepted) {
            address.save(url)
            lastFailure = null
        } else {
            lastFailure = url to clock.instant()
        }
        return accepted
    }

    companion object {
        val RETRY: Duration = Duration.ofMinutes(10)
    }
}
