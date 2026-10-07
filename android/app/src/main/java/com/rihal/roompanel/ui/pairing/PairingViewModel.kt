package com.rihal.roompanel.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rihal.roompanel.data.api.BackendClient
import com.rihal.roompanel.data.api.PairPollDto
import com.rihal.roompanel.data.api.PairStartDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairingState {
    data object Connecting : PairingState
    data class ShowingCode(val code: String) : PairingState
    data object CantReachServer : PairingState
}

/**
 * Shows a pairing code and polls until an admin approves it in the portal. A code that
 * expires is silently replaced; a network failure retries. [onPaired] runs once with the
 * device token, which is then never shown or logged.
 */
class PairingViewModel(
    private val client: BackendClient,
    private val onPaired: (token: String, roomName: String) -> Unit,
) : ViewModel() {

    private val mutable = MutableStateFlow<PairingState>(PairingState.Connecting)
    val state: StateFlow<PairingState> = mutable.asStateFlow()

    init {
        viewModelScope.launch { loop() }
    }

    private suspend fun loop() {
        while (true) {
            val start = try {
                client.post("/api/device/pair/start", "{}", PairStartDto.serializer())
            } catch (e: Exception) {
                mutable.value = PairingState.CantReachServer
                delay(RETRY_MILLIS)
                continue
            }
            mutable.value = PairingState.ShowingCode(start.code)
            if (pollUntilDone(start.pollToken)) return
        }
    }

    /** True when paired; false when the code expired and a new one is needed. */
    private suspend fun pollUntilDone(pollToken: String): Boolean {
        while (true) {
            delay(POLL_MILLIS)
            val poll = try {
                client.post("/api/device/pair/poll", """{"pollToken":"$pollToken"}""", PairPollDto.serializer())
            } catch (e: Exception) {
                continue // transient: keep the code on screen and try again
            }
            when (poll.status) {
                "paired" -> {
                    val token = poll.deviceToken ?: return false
                    onPaired(token, poll.roomName.orEmpty())
                    return true
                }
                "pending" -> Unit
                else -> return false // expired or consumed: start over with a fresh code
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 3_000L
        const val RETRY_MILLIS = 5_000L
    }
}
