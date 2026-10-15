package com.rihal.roompanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rihal.roompanel.R
import com.rihal.roompanel.domain.AgendaEntry

/**
 * Today and tomorrow as one touch list: meetings and the free gaps between them. A free gap is
 * a big tappable row that opens the booking sheet. The list fills the remaining height and
 * scrolls, with a visible scrollbar so people can tell there is more below.
 */
@Composable
internal fun AgendaPane(
    state: PanelUiState,
    onBookSlot: (AgendaEntry.Free) -> Unit,
    onFindRoom: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier,
) {
    val fmt = LocalTimeFormat.current
    val brand = brandColor(state.config)
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().background(brand).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(fmt.time(state.now), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(14.dp))
            Text(
                fmt.day(state.now.atZone(state.config.zone).toLocalDate()),
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .scrollbar(listState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.heightIn(min = 4.dp)) }
            items(state.agenda, key = { it.key }) { entry ->
                when (entry) {
                    is AgendaEntry.Day -> DayHeading(entry, state)
                    is AgendaEntry.Booked -> BookedRow(entry, state)
                    is AgendaEntry.Free -> FreeRow(entry, state, onBookSlot)
                }
            }
            item { Spacer(Modifier.heightIn(min = 8.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FooterButton(stringResource(R.string.find_room), Modifier.weight(1f), onFindRoom)
            FooterButton(stringResource(R.string.report_problem), Modifier.weight(1f), onReport)
        }
    }
}

@Composable
private fun DayHeading(day: AgendaEntry.Day, state: PanelUiState) {
    val fmt = LocalTimeFormat.current
    val label = if (day.isToday) stringResource(R.string.today) else stringResource(R.string.tomorrow)
    val open = day.date.dayOfWeek in state.config.workingHours.days
    val hasRows = state.agenda.any { e ->
        (e is AgendaEntry.Booked && e.meeting.start.atZone(state.config.zone).toLocalDate() == day.date) ||
            (e is AgendaEntry.Free && e.start.atZone(state.config.zone).toLocalDate() == day.date) ||
            (day.isToday && e is AgendaEntry.Booked && e.meeting.isOngoingAt(state.now))
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            if (day.isToday) label else "$label · ${fmt.day(day.date)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (!hasRows) {
            Text(
                stringResource(if (open) R.string.no_meetings_day else R.string.day_closed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookedRow(entry: AgendaEntry.Booked, state: PanelUiState) {
    val fmt = LocalTimeFormat.current
    val m = entry.meeting
    val ongoing = m.isOngoingAt(state.now)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (ongoing) StatusColors.Busy.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(fmt.range(m.start, m.end), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            meetingTitle(m),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!m.isPrivate) m.organizer?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FreeRow(entry: AgendaEntry.Free, state: PanelUiState, onBook: (AgendaEntry.Free) -> Unit) {
    val fmt = LocalTimeFormat.current
    val bookable = entry.bookable && state.bookingAllowed && !state.actionInFlight &&
        com.rihal.roompanel.domain.AgendaPlanner.lengthsFor(entry, state.now, state.config).isNotEmpty()
    val label = if (entry.startsNow) stringResource(R.string.free_now, fmt.time(entry.end))
    else stringResource(R.string.free_slot, fmt.time(entry.start), fmt.time(entry.end))
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(shape)
            .border(2.dp, StatusColors.Free.copy(alpha = 0.8f), shape)
            .then(if (bookable) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.tap_to_book)) { onBook(entry) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (bookable) {
            Text(
                stringResource(R.string.tap_to_book),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(StatusColors.Free)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FooterButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouch)) {
        Text(label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A thin, always-visible scrollbar on the end edge while the list can scroll. */
private fun Modifier.scrollbar(state: androidx.compose.foundation.lazy.LazyListState): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty() || visible.size >= total) return@drawWithContent
    val fraction = visible.size.toFloat() / total
    val barHeight = (size.height * fraction).coerceAtLeast(40f)
    val top = (size.height - barHeight) * (state.firstVisibleItemIndex.toFloat() / (total - visible.size).coerceAtLeast(1))
    val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 2f else size.width - 8f
    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(x, top), Size(6f, barHeight), CornerRadius(3f, 3f))
}
