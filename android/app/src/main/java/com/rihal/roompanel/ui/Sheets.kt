package com.rihal.roompanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rihal.roompanel.R
import com.rihal.roompanel.domain.IssueCategory
import java.time.Duration

private val Ink = Color(0xFF14171A)
private val Muted = Color(0xFF4A5560)
private val Line = Color(0xFFC9D0D6)

/** A white card over a dark scrim, inside the app window (no system dialog), tap outside to close. */
@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                // Swallow taps on the card itself so they don't close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.close), color = Ink, fontSize = 20.sp)
                }
            }
            content()
        }
    }
}

/**
 * Book now or a later slot: pick a title (the first preset is preselected) and tap a length.
 * Tapping a length books at once: two taps from "Book now", and no typing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookingSheet(
    state: PanelUiState,
    request: BookingRequest,
    onBook: (minutes: Int, presetIndex: Int?) -> Unit,
    onClose: () -> Unit,
) {
    val fmt = LocalTimeFormat.current
    val presets = state.config.branding.titlePresets
    var picked by remember { mutableIntStateOf(0) }
    val from = request.start ?: state.now
    val title = if (request.start == null) stringResource(R.string.book_room, state.config.roomName)
    else stringResource(R.string.book_from, fmt.time(request.start))
    Sheet(title, onClose) {
        if (presets.isNotEmpty()) {
            Text(stringResource(R.string.pick_title), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                presets.forEachIndexed { i, preset ->
                    val selected = i == picked
                    val shape = RoundedCornerShape(50)
                    Text(
                        preset,
                        color = if (selected) Color.White else Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .heightIn(min = MinTouch)
                            .clip(shape)
                            .background(if (selected) brandColor(state.config) else Color.White)
                            .border(2.dp, if (selected) brandColor(state.config) else Line, shape)
                            .clickable(role = Role.RadioButton) { picked = i }
                            .semantics { this.selected = selected }
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                    )
                }
            }
        }
        Text(stringResource(R.string.how_long), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (request.lengths.isEmpty()) {
            Text(stringResource(R.string.no_time_available), color = Muted, fontSize = 20.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                request.lengths.forEach { minutes ->
                    Button(
                        onClick = { onBook(minutes, presets.takeIf { it.isNotEmpty() }?.let { picked }) },
                        enabled = !state.actionInFlight,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusColors.Free, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.heightIn(min = 84.dp).widthIn(min = 150.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.minutes_short, minutes), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.until_time, fmt.time(from.plus(Duration.ofMinutes(minutes.toLong())))),
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Report a problem": six fixed categories, one tap, no typing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportSheet(onPick: (IssueCategory) -> Unit, onClose: () -> Unit) {
    Sheet(stringResource(R.string.report_title), onClose) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 3) {
            IssueCategory.entries.forEach { c ->
                OutlinedButton(
                    onClick = { onPick(c) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 84.dp),
                ) {
                    Text(stringResource(issueLabel(c)), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
        }
    }
}

internal fun issueLabel(c: IssueCategory) = when (c) {
    IssueCategory.AV -> R.string.issue_AV
    IssueCategory.VIDEO_CALL -> R.string.issue_VIDEO_CALL
    IssueCategory.CLEANING -> R.string.issue_CLEANING
    IssueCategory.SUPPLIES -> R.string.issue_SUPPLIES
    IssueCategory.TEMPERATURE -> R.string.issue_TEMPERATURE
    IssueCategory.OTHER -> R.string.issue_OTHER
}

/** "Find a free room": other rooms free right now, with where they are and until when. */
@Composable
internal fun FreeRoomsSheet(state: FreeRoomsState, onClose: () -> Unit) {
    val fmt = LocalTimeFormat.current
    Sheet(stringResource(R.string.free_rooms_title), onClose) {
        when (state) {
            FreeRoomsState.Closed -> Unit
            FreeRoomsState.Loading -> Text(stringResource(R.string.working), color = Muted, fontSize = 20.sp)
            FreeRoomsState.Failed -> Text(stringResource(R.string.free_rooms_failed), color = Muted, fontSize = 20.sp)
            is FreeRoomsState.Loaded -> if (state.rooms.isEmpty()) {
                Text(stringResource(R.string.free_rooms_none), color = Muted, fontSize = 20.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.rooms.forEach { room ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF4F6F8))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(room.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                val facts = listOfNotNull(
                                    room.floor.takeIf { it.isNotBlank() },
                                    room.capacity?.let { pluralStringResource(R.plurals.seats, it, it) },
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = Muted, fontSize = 18.sp)
                            }
                            Text(
                                room.freeUntil?.let { stringResource(R.string.free_until, fmt.time(it)) }
                                    ?: stringResource(R.string.free_for_rest_of_day_short),
                                color = StatusColors.Free,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
