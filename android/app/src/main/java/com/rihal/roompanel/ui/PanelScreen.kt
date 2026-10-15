package com.rihal.roompanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rihal.roompanel.R
import com.rihal.roompanel.data.SyncProblem
import com.rihal.roompanel.domain.AttendeeDisplay
import com.rihal.roompanel.domain.IssueCategory
import com.rihal.roompanel.domain.Meeting
import com.rihal.roompanel.domain.PanelConfig
import com.rihal.roompanel.domain.RoomStatus
import com.rihal.roompanel.ui.qr.QrCode
import java.time.Instant

internal val MinTouch = 64.dp

/** Everything the door can ask the app to do. Defaults let previews and tests pass only what they use. */
class PanelActions(
    val onBook: (minutes: Int, start: Instant?, presetIndex: Int?) -> Unit = { _, _, _ -> },
    val onExtend: (meetingId: String, minutes: Int) -> Unit = { _, _ -> },
    val onEnd: (String) -> Unit = {},
    val onCheckIn: (String) -> Unit = {},
    val onReport: (IssueCategory) -> Unit = {},
    val onOpenFreeRooms: () -> Unit = {},
    val onCloseFreeRooms: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
)

/** An open booking sheet: from now, or from a later free slot. */
internal data class BookingRequest(val start: Instant?, val lengths: List<Int>)

/**
 * Design A ("Split"): room status on the start side, today and tomorrow on the end side.
 * No keyboard anywhere: every choice is a button. Sheets are drawn inside this screen
 * (not system dialogs), so they behave the same in kiosk lock-task mode.
 */
@Composable
fun PanelScreen(
    state: PanelUiState,
    actions: PanelActions = PanelActions(),
    freeRooms: FreeRoomsState = FreeRoomsState.Closed,
) {
    val branding = state.config.branding
    PanelLocale(shownLocale(branding.language, state.now), state.config.zone, branding.clock24h) {
        var booking by remember { mutableStateOf<BookingRequest?>(null) }
        var reporting by remember { mutableStateOf(false) }
        // A sheet for a choice the room no longer allows (it became busy, went stale) closes itself.
        LaunchedEffect(state.bookingAllowed) { if (!state.bookingAllowed) booking = null }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxSize()) {
                StatusPane(
                    state = state,
                    actions = actions,
                    onBookNow = { booking = BookingRequest(null, state.bookOptions) },
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                )
                AgendaPane(
                    state = state,
                    onBookSlot = { slot ->
                        booking = BookingRequest(
                            start = if (slot.startsNow) null else slot.start,
                            lengths = com.rihal.roompanel.domain.AgendaPlanner.lengthsFor(slot, state.now, state.config),
                        )
                    },
                    onFindRoom = actions.onOpenFreeRooms,
                    onReport = { reporting = true },
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                )
            }
            booking?.let { request ->
                BookingSheet(
                    state = state,
                    request = request,
                    onBook = { minutes, preset ->
                        booking = null
                        actions.onBook(minutes, request.start, preset)
                    },
                    onClose = { booking = null },
                )
            }
            if (reporting) {
                ReportSheet(
                    onPick = { reporting = false; actions.onReport(it) },
                    onClose = { reporting = false },
                )
            }
            if (freeRooms != FreeRoomsState.Closed) FreeRoomsSheet(freeRooms, actions.onCloseFreeRooms)
        }
    }
}

@Composable
private fun StatusPane(state: PanelUiState, actions: PanelActions, onBookNow: () -> Unit, modifier: Modifier) {
    val config = state.config
    val status = state.status
    val color = statusColor(state)
    Box(modifier.background(color)) {
        // A background image sits under a dark scrim and the status tint, so white text keeps its contrast.
        state.images.background?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.7f)))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
            RoomHeader(state)
            Spacer(Modifier.height(12.dp))
            Feedback(state, actions.onDismissFeedback)
            Text(
                stringResource(statusLabel(state)),
                color = Color.White,
                fontSize = 64.sp,
                lineHeight = 68.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            StatusLine(state)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDetails(state)
                }
                SideCode(state)
            }
            Messages(state)
            Spacer(Modifier.height(12.dp))
            StatusButtons(state, actions, onBookNow)
        }
    }
}

private fun statusColor(state: PanelUiState): Color = when {
    state.config.outOfService -> StatusColors.OutOfService
    else -> StatusColors.of(state.status)
}

private fun statusLabel(state: PanelUiState): Int = when {
    state.config.outOfService -> R.string.status_out_of_service
    else -> when (state.status) {
        is RoomStatus.Free -> R.string.status_free
        is RoomStatus.StartingSoon -> R.string.status_starting_soon
        is RoomStatus.Busy -> R.string.status_busy
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomHeader(state: PanelUiState) {
    val config = state.config
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        state.images.logo?.let {
            Image(
                it,
                contentDescription = stringResource(R.string.logo),
                modifier = Modifier
                    .heightIn(max = 44.dp)
                    .width(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(4.dp),
            )
        }
        Column {
            Text(
                config.roomName,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val facts = buildList {
                if (config.floor.isNotBlank()) add(config.floor)
                config.capacity?.let { add(pluralStringResource(R.plurals.seats, it, it)) }
                config.equipment.sortedBy { it.ordinal }.forEach { add(stringResource(equipmentLabel(it))) }
            }
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun equipmentLabel(e: com.rihal.roompanel.domain.Equipment) = when (e) {
    com.rihal.roompanel.domain.Equipment.SCREEN -> R.string.equipment_SCREEN
    com.rihal.roompanel.domain.Equipment.VIDEO -> R.string.equipment_VIDEO
    com.rihal.roompanel.domain.Equipment.WHITEBOARD -> R.string.equipment_WHITEBOARD
    com.rihal.roompanel.domain.Equipment.PHONE -> R.string.equipment_PHONE
    com.rihal.roompanel.domain.Equipment.ACCESSIBLE -> R.string.equipment_ACCESSIBLE
}

/** In flight, done, or failed (named), or the stale warning: never silence after a tap. */
@Composable
private fun Feedback(state: PanelUiState, onDismiss: () -> Unit) {
    val fmt = LocalTimeFormat.current
    val fb = state.feedback
    val text: String? = when {
        fb.inFlight -> stringResource(R.string.working)
        fb.success != null -> when (val s = fb.success) {
            is ActionResult.Booked ->
                if (s.later) stringResource(R.string.booked_later, fmt.time(s.start), fmt.time(s.until))
                else stringResource(R.string.booked_until, fmt.time(s.until))
            is ActionResult.Extended -> stringResource(R.string.extended_until, fmt.time(s.until))
            ActionResult.Ended -> stringResource(R.string.ended_done)
            ActionResult.CheckedIn -> stringResource(R.string.checked_in_done)
            ActionResult.Reported -> stringResource(R.string.reported_done)
        }
        fb.error != null -> stringResource(errorText(fb.error))
        state.stale -> stringResource(staleText(state.problem))
        else -> null
    }
    text ?: return
    val isError = fb.error != null
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.3f))
            .padding(start = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp).weight(1f, fill = false))
        if (fb.success != null || fb.error != null) {
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.dismiss), color = Color.White, fontSize = 18.sp)
            }
        } else {
            Spacer(Modifier.width(18.dp))
        }
    }
}

internal fun errorText(code: String): Int = when (code) {
    "conflict" -> R.string.error_conflict
    "offline" -> R.string.error_offline
    "not_panel_booking" -> R.string.error_not_panel_booking
    "ended", "not_found" -> R.string.error_meeting_gone
    "booking_disabled" -> R.string.error_booking_disabled
    "out_of_service" -> R.string.error_out_of_service
    "outside_hours" -> R.string.error_outside_hours
    "outside_window" -> R.string.error_outside_window
    "in_past", "not_aligned" -> R.string.error_in_past
    "too_long", "invalid_duration" -> R.string.error_too_long
    else -> R.string.error_generic
}

private fun staleText(problem: SyncProblem?) = when (problem) {
    SyncProblem.CALENDAR_NOT_CONFIGURED -> R.string.stale_not_configured
    SyncProblem.CALENDAR_UNAVAILABLE -> R.string.stale_calendar
    else -> R.string.stale_offline
}

/** "Free until 13:00" / "Busy until 11:00": the line people read from across the corridor. */
@Composable
private fun StatusLine(state: PanelUiState) {
    val fmt = LocalTimeFormat.current
    val text = when {
        state.config.outOfService -> state.config.outOfServiceMessage.ifBlank { stringResource(R.string.out_of_service_default) }
        else -> when (val s = state.status) {
            is RoomStatus.Busy -> stringResource(R.string.busy_until, fmt.time(s.current.end))
            is RoomStatus.StartingSoon -> stringResource(R.string.free_until, fmt.time(s.next.start))
            is RoomStatus.Free -> s.next?.let { stringResource(R.string.free_until, fmt.time(it.start)) }
                ?: stringResource(R.string.free_rest_of_day)
        }
    }
    Text(text, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun StatusDetails(state: PanelUiState) {
    val fmt = LocalTimeFormat.current
    if (state.config.outOfService) return
    when (val s = state.status) {
        is RoomStatus.Busy -> {
            state.endingInMinutes?.let { EndingSoon(it) }
            WhiteText(meetingTitle(s.current), 30.sp, FontWeight.SemiBold)
            if (!s.current.isPrivate) s.current.organizer?.let { WhiteText(it, 20.sp) }
            WhiteText(fmt.range(s.current.start, s.current.end), 20.sp)
            ReleaseWarning(s.current)
            Attendees(s.current, state.config.attendeeDisplay)
            s.next?.let { NextPreview(it) }
        }
        is RoomStatus.StartingSoon -> {
            WhiteText(meetingTitle(s.next), 30.sp, FontWeight.SemiBold)
            if (!s.next.isPrivate) s.next.organizer?.let { WhiteText(it, 20.sp) }
            WhiteText(fmt.range(s.next.start, s.next.end), 20.sp)
            ReleaseWarning(s.next)
        }
        is RoomStatus.Free -> s.next?.let { NextPreview(it) }
    }
}

/** Next booking: time and organiser, or "Private meeting" (privacy is applied on the server too). */
@Composable
private fun NextPreview(next: Meeting) {
    val fmt = LocalTimeFormat.current
    val who = when {
        next.isPrivate -> stringResource(R.string.private_meeting)
        else -> next.organizer ?: meetingTitle(next)
    }
    WhiteText(stringResource(R.string.next_meeting, fmt.range(next.start, next.end), who), 20.sp)
}

@Composable
private fun EndingSoon(minutes: Long) {
    Text(
        stringResource(R.string.ends_in, minutes.toInt()),
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StatusColors.Warning)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** "Check in by 10:10 or the room is released": only when the room releases no-shows. */
@Composable
private fun ReleaseWarning(meeting: Meeting) {
    val at = meeting.releaseAt ?: return
    if (meeting.checkedIn) return
    WhiteText(stringResource(R.string.release_warning, LocalTimeFormat.current.time(at)), 20.sp, FontWeight.SemiBold)
}

/** Teams join QR for the meeting on screen, else the guest Wi-Fi QR while the room is idle. */
@Composable
private fun SideCode(state: PanelUiState) {
    val meeting = when (val s = state.status) {
        is RoomStatus.Busy -> s.current
        is RoomStatus.StartingSoon -> s.next
        is RoomStatus.Free -> null
    }
    val joinUrl = meeting?.joinUrl?.takeIf { !meeting.isPrivate && it.startsWith("https://") }
    val wifi = state.config.guestWifi
    when {
        joinUrl != null -> CodeCard(joinUrl, stringResource(R.string.scan_to_join))
        meeting == null && wifi != null && !state.config.outOfService ->
            CodeCard(wifi.qrPayload(), stringResource(R.string.guest_wifi_scan, wifi.ssid), stringResource(R.string.guest_wifi))
    }
}

@Composable
private fun CodeCard(payload: String, label: String, title: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(150.dp)) {
        title?.let { Text(it, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        QrCode(payload, 120.dp, label, Modifier.clip(RoundedCornerShape(8.dp)))
        Text(label, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Welcome message while free, and the visitor notice: both set in the portal. */
@Composable
private fun Messages(state: PanelUiState) {
    val b = state.config.branding
    if (state.status is RoomStatus.Free && b.welcomeMessage.isNotBlank()) WhiteText(b.welcomeMessage, 20.sp)
    if (b.visitorNotice.isNotBlank()) WhiteText(b.visitorNotice, 17.sp)
}

@Composable
private fun StatusButtons(state: PanelUiState, actions: PanelActions, onBookNow: () -> Unit) {
    val enabled = !state.actionInFlight
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.config.outOfService) return@Row
        when (val status = state.status) {
            is RoomStatus.Busy -> {
                val current = status.current
                if (!current.checkedIn && !state.stale) PanelButton(stringResource(R.string.check_in), enabled) { actions.onCheckIn(current.id) }
                state.extendOptions.forEach { m ->
                    PanelOutlinedButton(stringResource(R.string.extend_minutes, m), enabled) { actions.onExtend(current.id, m) }
                }
                if (current.bookedFromPanel && !state.stale) PanelOutlinedButton(stringResource(R.string.end_now), enabled) { actions.onEnd(current.id) }
            }
            is RoomStatus.StartingSoon -> {
                val next = status.next
                val windowOpen = !state.now.isBefore(next.start.minusSeconds(state.config.checkInWindowMinutes * 60L))
                if (!next.checkedIn && windowOpen && !state.stale) PanelButton(stringResource(R.string.check_in), enabled) { actions.onCheckIn(next.id) }
                if (state.bookOptions.isNotEmpty()) PanelOutlinedButton(stringResource(R.string.book_now), enabled, onBookNow)
            }
            is RoomStatus.Free -> if (state.bookOptions.isNotEmpty()) PanelButton(stringResource(R.string.book_now), enabled, onBookNow)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attendees(meeting: Meeting, display: AttendeeDisplay) {
    if (meeting.isPrivate || meeting.attendeeCount == 0) return
    when (display) {
        AttendeeDisplay.OFF -> Unit
        AttendeeDisplay.COUNT -> WhiteText(
            pluralStringResource(R.plurals.attendee_count, meeting.attendeeCount, meeting.attendeeCount),
            18.sp,
        )
        AttendeeDisplay.NAMES -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxLines = 1,
        ) {
            meeting.attendees.forEach { name ->
                Text(
                    name,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun PanelButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF121417)),
        modifier = Modifier.heightIn(min = MinTouch),
    ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp)) }
}

@Composable
internal fun PanelOutlinedButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        modifier = Modifier.heightIn(min = MinTouch),
    ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp)) }
}

@Composable
internal fun WhiteText(text: String, size: TextUnit, weight: FontWeight = FontWeight.Normal) {
    Text(text, color = Color.White, fontSize = size, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun meetingTitle(meeting: Meeting): String = when {
    meeting.isPrivate -> stringResource(R.string.private_meeting)
    meeting.bookedFromPanel && meeting.subject == null -> stringResource(R.string.adhoc_meeting)
    else -> meeting.subject ?: stringResource(R.string.untitled_meeting)
}

/** The brand colour from the portal (already contrast-checked there), else the default. */
internal fun brandColor(config: PanelConfig): Color =
    config.branding.primaryColor?.let { Color(it.toInt()) } ?: StatusColors.DefaultBrand
