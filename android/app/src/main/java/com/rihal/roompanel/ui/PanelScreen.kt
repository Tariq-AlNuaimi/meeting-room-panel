package com.rihal.roompanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rihal.roompanel.R
import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.RoomStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val MinTouch = 64.dp

@Composable
fun PanelScreen(
    state: PanelUiState,
    onBook: (Int) -> Unit,
    onExtend: (String) -> Unit,
    onEnd: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var showBooking by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusPane(
            state = state,
            onBookClick = { showBooking = true },
            onExtend = onExtend,
            onEnd = onEnd,
            onCheckIn = onCheckIn,
            modifier = Modifier.weight(0.62f).fillMaxHeight(),
        )
        AgendaPane(state, Modifier.weight(0.38f).fillMaxHeight())
    }

    if (showBooking) {
        BookNowDialog(
            options = state.bookOptions,
            onPick = { minutes -> showBooking = false; onBook(minutes) },
            onDismiss = { showBooking = false },
        )
    }
    state.lastError?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.action_failed)) },
            text = { Text(message) },
        )
    }
}

@Composable
private fun StatusPane(
    state: PanelUiState,
    onBookClick: () -> Unit,
    onExtend: (String) -> Unit,
    onEnd: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    modifier: Modifier,
) {
    val status = state.status
    Column(
        modifier.background(StatusColors.of(status)).padding(48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                state.config.roomName,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 28.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(
                    when (status) {
                        is RoomStatus.Free -> R.string.status_free
                        is RoomStatus.StartingSoon -> R.string.status_starting_soon
                        is RoomStatus.Busy -> R.string.status_busy
                    },
                ),
                color = Color.White,
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        when (status) {
            is RoomStatus.Busy -> CurrentMeeting(status.current, state.now, state.config)
            is RoomStatus.StartingSoon -> NextUp(status.next, state.now, state.config)
            is RoomStatus.Free -> status.next?.let { NextUp(it, state.now, state.config) }
                ?: WhiteText(stringResource(R.string.free_rest_of_day), 28.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val enabled = !state.actionInFlight
            when (status) {
                is RoomStatus.Busy -> {
                    val current = status.current
                    if (!current.checkedIn) PanelButton(stringResource(R.string.check_in), enabled) { onCheckIn(current.id) }
                    if (state.canExtendCurrent) {
                        PanelOutlinedButton(stringResource(R.string.extend_minutes, state.config.extendStepMinutes), enabled) { onExtend(current.id) }
                    }
                    if (current.bookedFromPanel) PanelOutlinedButton(stringResource(R.string.end_now), enabled) { onEnd(current.id) }
                }
                is RoomStatus.StartingSoon -> {
                    val next = status.next
                    if (!next.checkedIn) PanelButton(stringResource(R.string.check_in), enabled) { onCheckIn(next.id) }
                    if (state.bookOptions.isNotEmpty()) PanelOutlinedButton(stringResource(R.string.book_now), enabled, onBookClick)
                }
                is RoomStatus.Free -> if (state.bookOptions.isNotEmpty()) PanelButton(stringResource(R.string.book_now), enabled, onBookClick)
            }
        }
    }
}

@Composable
private fun CurrentMeeting(meeting: Meeting, now: Instant, config: PanelConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteText(meetingTitle(meeting), 40.sp, FontWeight.SemiBold)
        if (!meeting.isPrivate) meeting.organizer?.let { WhiteText(it, 24.sp) }
        WhiteText(
            "${timeRange(meeting)} · " + stringResource(R.string.minutes_left, minutesBetween(now, meeting.end)),
            24.sp,
        )
        Attendees(meeting, config.attendeeDisplay)
    }
}

@Composable
private fun NextUp(meeting: Meeting, now: Instant, config: PanelConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteText(stringResource(R.string.next_in_minutes, minutesBetween(now, meeting.start)), 24.sp)
        WhiteText(meetingTitle(meeting), 36.sp, FontWeight.SemiBold)
        WhiteText(timeRange(meeting), 24.sp)
        Attendees(meeting, config.attendeeDisplay)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attendees(meeting: Meeting, display: AttendeeDisplay) {
    if (meeting.isPrivate || meeting.attendees.isEmpty()) return
    when (display) {
        AttendeeDisplay.OFF -> Unit
        AttendeeDisplay.COUNT -> WhiteText(
            pluralStringResource(R.plurals.attendee_count, meeting.attendees.size, meeting.attendees.size),
            22.sp,
        )
        AttendeeDisplay.NAMES -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            meeting.attendees.forEach { name ->
                Text(
                    name,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AgendaPane(state: PanelUiState, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(32.dp)) {
        Text(
            stringResource(R.string.today),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            formatTime(state.now),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (state.upcoming.isEmpty()) {
            Text(
                stringResource(R.string.no_more_meetings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.upcoming, key = Meeting::id) { meeting ->
                    val ongoing = meeting.isOngoingAt(state.now)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (ongoing) StatusColors.Busy.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(16.dp),
                    ) {
                        Text(timeRange(meeting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            meetingTitle(meeting),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookNowDialog(options: List<Int>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_now)) },
        text = {
            if (options.isEmpty()) {
                Text(stringResource(R.string.no_time_available))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    options.forEach { minutes ->
                        Button(onClick = { onPick(minutes) }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.minutes_short, minutes), fontSize = 22.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PanelButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF121417)),
        modifier = Modifier.heightIn(min = MinTouch),
    ) { Text(label, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
}

@Composable
private fun PanelOutlinedButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        modifier = Modifier.heightIn(min = MinTouch),
    ) { Text(label, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
}

@Composable
private fun WhiteText(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal) {
    Text(text, color = Color.White, fontSize = size, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun meetingTitle(meeting: Meeting): String = when {
    meeting.isPrivate -> stringResource(R.string.private_meeting)
    meeting.bookedFromPanel && meeting.subject == null -> stringResource(R.string.adhoc_meeting)
    else -> meeting.subject ?: stringResource(R.string.untitled_meeting)
}

/** Follows the current UI locale, so a language switch re-renders times correctly. */
@Composable
private fun formatTime(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

@Composable
private fun timeRange(meeting: Meeting) = "${formatTime(meeting.start)} – ${formatTime(meeting.end)}"

/** Rounded up so "0 min left" never shows while a meeting is still running. */
private fun minutesBetween(from: Instant, to: Instant): Long {
    val seconds = Duration.between(from, to).seconds.coerceAtLeast(0)
    return (seconds + 59) / 60
}
