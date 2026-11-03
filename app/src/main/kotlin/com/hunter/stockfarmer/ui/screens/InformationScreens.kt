package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.ChangelogDay
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudSearchField
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PrivacyScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val policy by rememberInformationAsset("Privacy.md")
    ScreenLayout("Privacy", session, onClose) {
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard(16.dp)
                    .padding(16.dp),
            ) {
                Text(
                    text =
                        policy?.getOrElse {
                            "Privacy information could not be opened. Please try reopening the app."
                        } ?: "Loading…",
                    style = HudType.body,
                    color = Theme.textPrimary,
                )
            }
        }
    }
}

@Composable
fun ChangelogScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val text by rememberInformationAsset("Changelog.md")
    val days = remember(text) { text?.mapCatching(ScreenInformation::changelog) }
    var state by remember(days) { mutableStateOf(ChangelogState(days?.getOrNull().orEmpty())) }
    ScreenLayout("Changelog", session, onClose) {
        when {
            days == null -> {
                Text("Loading…", style = HudType.subheadline, color = Theme.textSecondary)
            }

            days.isFailure -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glassCard(14.dp)
                        .padding(14.dp),
                ) {
                    Text(
                        "The changelog could not be opened. Please try reopening the app.",
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                }
            }

            else -> {
                ChangelogContent(state) { state = it }
            }
        }
    }
}

@Composable
private fun ChangelogContent(
    state: ChangelogState,
    onChange: (ChangelogState) -> Unit,
) {
    var showsCalendar by remember { mutableStateOf(false) }
    HudSearchField(state.query, { onChange(state.search(it)) }, Modifier.fillMaxWidth())
    PillButton(
        title = if (state.picked.isEmpty()) "Choose days" else "Choose days · Filtered",
        onClick = { showsCalendar = true },
        modifier = Modifier.fillMaxWidth(),
        role = if (state.picked.isEmpty()) PillRole.neutral else PillRole.accent,
    )
    if (state.shown.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassCard(14.dp)
                .padding(14.dp),
        ) {
            Text("Nothing matches.", style = HudType.subheadline, color = Theme.textSecondary)
        }
    }
    state.shown.forEach { day ->
        key(day.date) {
            val expanded = state.isExpanded(day.date)
            val title =
                remember(day.date) {
                    runCatching {
                        LocalDate
                            .parse(day.date)
                            .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
                    }.getOrDefault(day.date)
                }
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard(14.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChange(state.toggle(day.date)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        style = HudType.headline,
                        color = Theme.textPrimary,
                    )
                    Text(
                        if (expanded) "▾" else "▸",
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                }
                if (expanded) {
                    day.lines.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("•", style = HudType.subheadline, color = Theme.accent)
                            Text(
                                line,
                                Modifier.weight(1f),
                                style = HudType.subheadline,
                                color = Theme.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
    if (state.pageCount > 1) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                title = "Newer",
                onClick = { onChange(state.goToPage(state.page - 1)) },
                role = PillRole.neutral,
                enabled = state.page > 0,
                fillWidth = false,
            )
            Text(
                "Page ${state.page + 1} of ${state.pageCount}",
                style = HudType.subheadline,
                color = Theme.textSecondary,
            )
            PillButton(
                title = "Older",
                onClick = { onChange(state.goToPage(state.page + 1)) },
                role = PillRole.neutral,
                enabled = state.page < state.pageCount - 1,
                fillWidth = false,
            )
        }
    }
    if (showsCalendar) {
        ChangelogCalendar(state.days, state.picked, onDismiss = { showsCalendar = false }) {
            onChange(state.pick(it))
            showsCalendar = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangelogCalendar(
    days: List<ChangelogDay>,
    picked: Set<LocalDate>,
    onDismiss: () -> Unit,
    onPick: (Set<LocalDate>) -> Unit,
) {
    val dates = remember(days) { days.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() } }

    fun millis(date: LocalDate): Long = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val first = dates.minOrNull()?.let(::millis)
    val last = dates.maxOrNull()?.let(::millis)
    val calendar =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = picked.minOrNull()?.let(::millis),
            initialSelectedEndDateMillis = picked.maxOrNull()?.let(::millis),
            initialDisplayedMonthMillis = picked.minOrNull()?.let(::millis) ?: last,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        first != null && last != null && utcTimeMillis in first..last
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PillButton(
                title = "Done",
                onClick = {
                    onPick(
                        listOfNotNull(calendar.selectedStartDateMillis, calendar.selectedEndDateMillis)
                            .map { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }
                            .toSet(),
                    )
                },
                role = PillRole.accent,
            )
        },
        dismissButton = {
            PillButton(
                title = "Show All Days",
                onClick = { onPick(emptySet()) },
                role = PillRole.neutral,
            )
        },
    ) {
        Text(
            "Tap a day, or two days to see everything between them.",
            style = HudType.footnote,
            color = Theme.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        DateRangePicker(
            calendar,
            modifier = Modifier.heightIn(max = 500.dp),
            title = {
                Text(
                    "Choose Days",
                    style = HudType.headline,
                    color = Theme.textPrimary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
            },
        )
    }
}

@Composable
private fun rememberInformationAsset(name: String): State<Result<String>?> {
    val assets = LocalContext.current.assets
    return produceState<Result<String>?>(null, assets, name) {
        value =
            withContext(Dispatchers.IO) {
                runCatching { assets.open("information/$name").bufferedReader().use { it.readText() } }
            }
    }
}
