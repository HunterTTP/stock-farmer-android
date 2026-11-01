package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.ChangelogDay
import com.hunter.stockfarmer.game.GameSession
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
            Text(
                policy?.getOrElse {
                    "Privacy information could not be opened. Please try reopening the app."
                } ?: "Loading…",
            )
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
                Text("Loading…")
            }

            days.isFailure -> {
                ScreenCard {
                    Text("The changelog could not be opened. Please try reopening the app.")
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
    OutlinedTextField(
        value = state.query,
        onValueChange = { onChange(state.search(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search") },
        singleLine = true,
        trailingIcon = {
            if (state.query.isNotEmpty()) TextButton(onClick = { onChange(state.search("")) }) { Text("Clear search") }
        },
    )
    TextButton(onClick = { showsCalendar = true }) {
        Text(if (state.picked.isEmpty()) "Choose days" else "Choose days · Filtered")
    }
    if (state.shown.isEmpty()) ScreenCard { Text("Nothing matches.") }
    state.shown.forEach { day ->
        key(day.date) {
            ScreenCard {
                val expanded = state.isExpanded(day.date)
                val title =
                    remember(day.date) {
                        runCatching {
                            LocalDate
                                .parse(day.date)
                                .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
                        }.getOrDefault(day.date)
                    }
                TextButton(onClick = { onChange(state.toggle(day.date)) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(if (expanded) "▾" else "▸")
                    }
                }
                if (expanded) day.lines.forEach { Text("• $it") }
            }
        }
    }
    if (state.pageCount > 1) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { onChange(state.goToPage(state.page - 1)) }, enabled = state.page > 0) { Text("Newer") }
            Text("Page ${state.page + 1} of ${state.pageCount}")
            Button(onClick = { onChange(state.goToPage(state.page + 1)) }, enabled = state.page < state.pageCount - 1) {
                Text("Older")
            }
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
            TextButton(onClick = {
                onPick(
                    listOfNotNull(calendar.selectedStartDateMillis, calendar.selectedEndDateMillis)
                        .map { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }
                        .toSet(),
                )
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = { onPick(emptySet()) }) { Text("Show All Days") } },
    ) {
        Text("Tap a day, or two days to see everything between them.")
        DateRangePicker(calendar, modifier = Modifier.heightIn(max = 500.dp), title = { Text("Choose Days") })
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
