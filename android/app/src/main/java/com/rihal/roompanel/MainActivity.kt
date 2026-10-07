package com.rihal.roompanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rihal.roompanel.data.DemoAgendaRepository
import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.kiosk.KioskPolicy
import com.rihal.roompanel.ui.PanelScreen
import com.rihal.roompanel.ui.PanelTheme
import com.rihal.roompanel.ui.PanelViewModel

class MainActivity : ComponentActivity() {

    private val kiosk by lazy { KioskPolicy(this) }

    // Phase 2 swaps DemoAgendaRepository for the backend client and loads PanelConfig from /api/device/config.
    private val viewModel: PanelViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PanelViewModel(
                repository = DemoAgendaRepository(),
                config = PanelConfig(
                    roomName = getString(R.string.demo_room_name),
                    attendeeDisplay = AttendeeDisplay.NAMES,
                ),
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            PanelTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.brightness) {
                    window.attributes = window.attributes.apply { screenBrightness = state.brightness }
                }
                PanelScreen(
                    state = state,
                    onBook = viewModel::bookNow,
                    onExtend = viewModel::extend,
                    onEnd = viewModel::endEarly,
                    onCheckIn = viewModel::checkIn,
                    onDismissError = viewModel::dismissError,
                )
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
}
