package com.hunter.stockfarmer.ui.timelapse

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hunter.stockfarmer.core.persistence.TimelapseStore
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.screens.Figure
import com.hunter.stockfarmer.ui.screens.ScreenCard
import com.hunter.stockfarmer.ui.screens.ScreenLayout
import com.hunter.stockfarmer.ui.screens.rememberScreenState
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard
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
            Text("Daily capture time", color = Theme.textPrimary, style = HudType.headline)
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
                color = Theme.textSecondary,
                style = HudType.subheadline,
            )
            if (count == 0) {
                val time = DayNightCycle.clockText(settings.captureMinute.toDouble())
                Text(
                    "Your first screenshot will be taken when the farm clock next crosses $time " +
                        "while Timelapse is enabled.",
                    color = Theme.textSecondary,
                    style = HudType.caption,
                )
            }
        }
        PillButton(
            title = "Play Timelapse",
            role = PillRole.accent,
            enabled = count > 0 && !recording.isClearing,
            onClick = { viewing = true },
        )
        PillButton(
            title = "Clear Timelapse",
            role = PillRole.destructive,
            enabled = (count > 0 || recording.error != null) && !recording.isClearing,
            onClick = { confirmsClear = true },
        )
        if (recording.isClearing) {
            Text(
                "Clearing screenshots…",
                color = Theme.textSecondary,
                style = HudType.subheadline,
            )
        }
        recording.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = Theme.spend, style = HudType.subheadline)
                PillButton(
                    title = "Retry",
                    role = PillRole.accent,
                    modifier = Modifier.fillMaxWidth(0.4f),
                    onClick = { scope.launch { recorder.refresh() } },
                )
            }
        }
        ScreenCard {
            TimelapseSwitch("Crossfade Transitions", settings.crossfades, recorder::setCrossfades)
        }
        ScreenCard {
            TimelapseSwitch("Enable Timelapse", settings.isEnabled, recorder::setEnabled)
        }
    }
    if (confirmsClear) {
        Dialog(onDismissRequest = { confirmsClear = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard(18.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Clear Timelapse?",
                    color = Theme.textPrimary,
                    style = HudType.headline,
                )
                Text(
                    "Delete all timelapse screenshots for this farm? Your capture time and recording settings stay the same.",
                    color = Theme.textSecondary,
                    style = HudType.subheadline,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PillButton(
                        title = "Cancel",
                        role = PillRole.neutral,
                        modifier = Modifier.weight(1f),
                        onClick = { confirmsClear = false },
                    )
                    PillButton(
                        title = "Clear",
                        role = PillRole.destructive,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmsClear = false
                            scope.launch { recorder.clear() }
                        },
                    )
                }
            }
        }
    }
    val farm = settings.historyID
    if (viewing && archive != null && farm != null) {
        TimelapseViewer(session, recorder, archive, farm, settings.crossfades) { viewing = false }
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
        Box(
            Modifier
                .glassCard(12.dp)
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$label: ${text(value)}",
                color = Theme.accent,
                style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp).background(Color(0.19f, 0.19f, 0.19f)),
        ) {
            repeat(count) { option ->
                DropdownMenuItem(
                    text = { Text(text(option), color = Theme.textPrimary, style = HudType.subheadline) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            color = Theme.textPrimary,
            style = HudType.body,
        )
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Theme.onAccent,
                    checkedTrackColor = Theme.accent,
                    uncheckedThumbColor = Theme.textSecondary,
                    uncheckedTrackColor = Theme.glassFill,
                ),
        )
    }
}
