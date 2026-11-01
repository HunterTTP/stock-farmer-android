package com.hunter.stockfarmer.ui.timelapse

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.persistence.TimelapseStore
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.screens.Figure
import com.hunter.stockfarmer.ui.screens.ScreenCard
import com.hunter.stockfarmer.ui.screens.ScreenLayout
import com.hunter.stockfarmer.ui.screens.rememberScreenState
import kotlinx.coroutines.launch
import java.util.Locale

/** Provide the same recorder that the active-play coroutine ticks so viewing suspends capture. */
val LocalTimelapseRecorder = staticCompositionLocalOf<TimelapseRecorder?> { null }

/** The fallback supports a standalone history screen; the host should provide its active recorder. */
@Composable
fun rememberTimelapseRecorder(session: GameSession): TimelapseRecorder {
    val provided = LocalTimelapseRecorder.current
    val directory =
        LocalContext.current.applicationContext.filesDir
            .toPath()
            .resolve("Timelapse")
    return remember(session, provided, directory) {
        provided ?: TimelapseRecorder(session, TimelapseStore(directory))
    }
}

@Composable
fun TimelapseScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val recorder = rememberTimelapseRecorder(session)
    val recording by recorder.state.collectAsState()
    val settings by rememberScreenState(session) { session.read { it.timelapse } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmsClear by remember(recorder) { mutableStateOf(false) }
    var viewing by remember(recorder, settings.historyID) { mutableStateOf(false) }
    val archive = recording.archive.takeIf { recording.farm == settings.historyID }
    val count = archive?.frames?.size ?: 0

    LaunchedEffect(recorder, settings.historyID) { recorder.refresh() }
    ScreenLayout("Timelapse", session, onClose) {
        ScreenCard {
            Figure("Screenshots", count.toString())
            Figure("Saved images", Formatter.formatFileSize(context, archive?.totalBytes ?: 0))
        }
        ScreenCard {
            Text("Daily capture time", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeMenu("Hour", settings.captureMinute / 60, 24, ::hourText) {
                    recorder.setMinute(it * 60 + session.read { state -> state.timelapse.captureMinute % 60 })
                }
                TimeMenu("Minute", settings.captureMinute % 60, 60, { String.format(Locale.US, "%02d", it) }) {
                    recorder.setMinute(session.read { state -> state.timelapse.captureMinute / 60 * 60 } + it)
                }
            }
            Text(
                "One whole-farm screenshot each in-game day while you play. Images stay in the app until you clear them.",
            )
            if (count == 0) {
                val time = DayNightCycle.clockText(settings.captureMinute.toDouble())
                Text(
                    "Your first screenshot will be taken when the farm clock next crosses $time " +
                        "while Timelapse is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = { viewing = true },
            enabled = count > 0 && !recording.isClearing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Play Timelapse") }
        OutlinedButton(
            onClick = { confirmsClear = true },
            enabled = (count > 0 || recording.error != null) && !recording.isClearing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear Timelapse") }
        if (recording.isClearing) Text("Clearing screenshots…")
        recording.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { recorder.refresh() } }) { Text("Retry") }
        }
        ScreenCard {
            TimelapseSwitch("Crossfade Transitions", settings.crossfades, recorder::setCrossfades)
        }
        ScreenCard {
            TimelapseSwitch("Enable Timelapse", settings.isEnabled, recorder::setEnabled)
        }
    }
    if (confirmsClear) {
        AlertDialog(
            onDismissRequest = { confirmsClear = false },
            title = { Text("Clear Timelapse?") },
            text = {
                Text(
                    "Delete all timelapse screenshots for this farm? Your capture time and recording settings stay the same.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmsClear = false
                    scope.launch { recorder.clear() }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmsClear = false }) { Text("Cancel") } },
        )
    }
    val farm = settings.historyID
    if (viewing && archive != null && farm != null) {
        TimelapseViewer(recorder, archive, farm, settings.crossfades) { viewing = false }
    }
}

private fun hourText(hour: Int): String = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"

@Composable
private fun TimeMenu(
    label: String,
    value: Int,
    count: Int,
    text: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${text(value)}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            repeat(count) { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
private fun TimelapseSwitch(
    title: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f))
        Switch(enabled, onCheckedChange = onChange)
    }
}
