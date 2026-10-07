package com.rihal.roompanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rihal.roompanel.data.AgendaRepository
import com.rihal.roompanel.data.DemoAgendaRepository
import com.rihal.roompanel.data.DeviceCredentials
import com.rihal.roompanel.data.RemoteAgendaRepository
import com.rihal.roompanel.data.api.BackendClient
import com.rihal.roompanel.kiosk.AppUpdater
import com.rihal.roompanel.kiosk.KioskPolicy
import com.rihal.roompanel.ui.PanelScreen
import com.rihal.roompanel.ui.PanelTheme
import com.rihal.roompanel.ui.PanelViewModel
import com.rihal.roompanel.ui.pairing.PairingScreen
import com.rihal.roompanel.ui.pairing.PairingViewModel

class MainActivity : ComponentActivity() {

    private val kiosk by lazy { KioskPolicy(this) }
    private val credentials by lazy { DeviceCredentials(this) }

    /** `-PbackendUrl=demo` builds a self-contained demo (no backend, sample meetings). */
    private val demoMode get() = BuildConfig.BACKEND_URL == "demo"

    private fun client() = BackendClient(BuildConfig.BACKEND_URL, BuildConfig.VERSION_NAME) { credentials.token() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        startUpdateChecks()

        setContent {
            PanelTheme {
                var paired by remember { mutableStateOf(demoMode || credentials.token() != null) }
                // Bumped on every pair/unpair, so each session gets fresh ViewModels (and a fresh code).
                var session by remember { mutableIntStateOf(0) }
                if (paired) {
                    PanelRoute(
                        session = session,
                        repository = { if (demoMode) DemoAgendaRepository(getString(R.string.demo_room_name)) else RemoteAgendaRepository(client()) },
                        onUnpaired = {
                            // Revoked in the portal, or its room deleted: forget the token and pair again.
                            credentials.clear()
                            session++
                            paired = false
                        },
                    )
                } else {
                    PairingRoute(session = session, onPaired = { token, room ->
                        credentials.save(token, room)
                        session++
                        paired = true
                    })
                }
            }
        }
    }

    @Composable
    private fun PanelRoute(session: Int, repository: () -> AgendaRepository, onUnpaired: () -> Unit) {
        val vm: PanelViewModel = viewModel(key = "panel-$session", factory = factory { PanelViewModel(repository()) })
        val state by vm.state.collectAsStateWithLifecycle()
        val unpaired by vm.unpaired.collectAsStateWithLifecycle()
        LaunchedEffect(unpaired) { if (unpaired) onUnpaired() }

        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
            }
            return
        }
        LaunchedEffect(current.brightness) {
            window.attributes = window.attributes.apply { screenBrightness = current.brightness }
        }
        PanelScreen(
            state = current,
            onBook = vm::bookNow,
            onExtend = vm::extend,
            onEnd = vm::endEarly,
            onCheckIn = vm::checkIn,
            onDismissError = vm::dismissError,
        )
    }

    @Composable
    private fun PairingRoute(session: Int, onPaired: (String, String) -> Unit) {
        val vm: PairingViewModel = viewModel(
            key = "pairing-$session",
            factory = factory { PairingViewModel(client(), onPaired) },
        )
        val state by vm.state.collectAsStateWithLifecycle()
        PairingScreen(state, BuildConfig.BACKEND_URL.toUri().host ?: BuildConfig.BACKEND_URL)
    }

    /** Paired Device Owner tablets check for a newer APK shortly after start, then every 6 hours. */
    private fun startUpdateChecks() {
        if (demoMode) return
        lifecycleScope.launch {
            delay(UPDATE_FIRST_CHECK_MILLIS)
            while (true) {
                if (credentials.token() != null) {
                    AppUpdater(this@MainActivity, client(), BuildConfig.VERSION_CODE.toLong()).checkAndInstall()
                }
                delay(UPDATE_INTERVAL_MILLIS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kiosk.enter(this)
    }

    /** Full-screen even when not Device Owner; [KioskPolicy] adds the real lock-down when it is. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

    private companion object {
        const val UPDATE_FIRST_CHECK_MILLIS = 2 * 60_000L
        const val UPDATE_INTERVAL_MILLIS = 6 * 3_600_000L
    }
}
