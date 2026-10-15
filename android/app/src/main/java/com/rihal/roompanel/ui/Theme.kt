package com.rihal.roompanel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rihal.roompanel.domain.RoomStatus

/** Status colours: every one holds >= 4.5:1 contrast with white text. */
object StatusColors {
    val Free = Color(0xFF1B7A3A)
    val StartingSoon = Color(0xFF8F5300)
    val Busy = Color(0xFFB3261E)
    /** Maintenance: slate grey, 7.4:1 with white. */
    val OutOfService = Color(0xFF4A5560)
    /** Default accent until the portal sets a brand colour (6.6:1 with white). */
    val DefaultBrand = Color(0xFF0B5CAD)
    /** The end-of-meeting warning: dark amber, 5.9:1 with white. */
    val Warning = Color(0xFF8F5300)

    fun of(status: RoomStatus): Color = when (status) {
        is RoomStatus.Free -> Free
        is RoomStatus.StartingSoon -> StartingSoon
        is RoomStatus.Busy -> Busy
    }
}

private val PanelColors = darkColorScheme(
    background = Color(0xFF121417),
    surface = Color(0xFF1C1F24),
    surfaceVariant = Color(0xFF262A30),
    onBackground = Color(0xFFF1F3F5),
    onSurface = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFFB8BEC6),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF121417),
)

@Composable
fun PanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PanelColors, content = content)
}
