package com.hunter.stockfarmer.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

@Composable
fun PerformanceScreen(
    session: GameSession,
    monitor: PerformanceMonitor,
    onClose: () -> Unit,
    recordingContext: () -> String,
    onShare: ((String) -> Unit)? = null,
) {
    val state by rememberPolledState(monitor) { monitor.snapshot }
    val context = LocalContext.current
    DisposableEffect(monitor) {
        monitor.setVisible(true)
        onDispose { monitor.setVisible(false) }
    }
    ScreenLayout("Performance", session, onClose) {
        PerformanceLiveStats(state)
        Column(
            Modifier
                .fillMaxWidth()
                .glassCard(16.dp, tint = if (state.isRecording) Theme.spend else null)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isRecording) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.isActive) "Recording" else "Recording paused",
                        style = HudType.headline,
                        color = Theme.spend,
                    )
                    Text(
                        "${number(state.elapsed)} s",
                        style = HudType.headline,
                        color = Theme.gold,
                    )
                }
                Text(
                    "Play as usual, then return here to stop and see the report. Inactive time is excluded.",
                    style = HudType.subheadline,
                    color = Theme.textSecondary,
                )
                PillButton(
                    title = "Stop Recording",
                    onClick = monitor::stop,
                    role = PillRole.destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
                PillButton(
                    title = "Return to Farm",
                    onClick = onClose,
                    role = PillRole.neutral,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Find slow work", style = HudType.headline, color = Theme.textPrimary)
                Text(
                    "Record while planting, harvesting or exploring. Stop here to compare frame pacing " +
                        "and time spent in each system.",
                    style = HudType.subheadline,
                    color = Theme.textSecondary,
                )
                PillButton(
                    title = if (state.report == null) "Record" else "New Recording",
                    onClick = {
                        monitor.start(recordingContext())
                        onClose()
                    },
                    role = PillRole.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Recording opens the farm. Includes time in menus. The latest report is kept until " +
                        "replaced or the app closes.",
                    style = HudType.caption,
                    color = Theme.textSecondary,
                )
            }
        }
        if (!state.isRecording) {
            state.report?.let { report ->
                PerformanceRecordingReport(report) {
                    if (onShare != null) {
                        onShare(report.text)
                    } else {
                        val send =
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.text)
                        context.startActivity(Intent.createChooser(send, "Share performance report"))
                    }
                }
            }
        }
        Text(
            PerformanceReport.measurementNotes,
            style = HudType.caption,
            color = Theme.textSecondary,
        )
    }
}

@Composable
private fun PerformanceLiveStats(state: PerformanceState) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Live", style = HudType.headline, color = Theme.textPrimary)
            Text(
                "Target ${state.targetFPS.toInt()} FPS",
                style = HudType.caption,
                color = Theme.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                PerformanceMetric("FPS", number(state.liveFPS), "Farm frame cadence")
            }
            Column(Modifier.weight(1f)) {
                PerformanceMetric("Frame time", milliseconds(state.liveFrameMS), "Average interval")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                PerformanceMetric("Scene update", milliseconds(state.liveUpdateMS), "Average CPU work")
            }
            Column(Modifier.weight(1f)) {
                PerformanceMetric("Memory", state.memoryMB?.let { "${number(it)} MiB" } ?: "—", "App footprint")
            }
        }
        PerformanceRow("Sprites drawn", ScreenFormat.number(state.spriteCount.toLong()))
        PerformanceRow("Thermal state", state.thermal)
        if (state.liveFPS == null) {
            Text(
                "Waiting for farm frames. The scene may be paused while a menu covers it.",
                style = HudType.caption,
                color = Theme.textSecondary,
            )
        }
    }
}

@Composable
private fun PerformanceMetric(
    title: String,
    value: String,
    detail: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(10.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = HudType.caption, color = Theme.textSecondary)
        Text(
            value,
            style = HudType.title3.copy(fontWeight = FontWeight.SemiBold),
            color = Theme.textPrimary,
        )
        Text(detail, style = HudType.caption, color = Theme.textSecondary)
    }
}

@Composable
private fun PerformanceRecordingReport(
    report: PerformanceReport,
    onShare: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recording Report", style = HudType.headline, color = Theme.textPrimary)
                Text(
                    "${number(report.activeSeconds)} active seconds · " +
                        "${ScreenFormat.number(report.frames.durations.count)} frames",
                    style = HudType.caption,
                    color = Theme.textSecondary,
                )
            }
        }
        Text(report.insight, style = HudType.subheadline, color = Theme.textPrimary)
        PillButton(
            title = "Share performance report",
            onClick = onShare,
            role = PillRole.accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val durations = report.frames.durations
        Text("Frame Pacing", style = HudType.headline, color = Theme.textPrimary)
        PerformanceRow("Average FPS", number(report.frames.fps))
        PerformanceRow("p50 / median", milliseconds(durations.percentile(0.5)))
        PerformanceRow("p95", milliseconds(durations.percentile(0.95)))
        PerformanceRow("p99", milliseconds(durations.percentile(0.99)))
        PerformanceRow("Worst frame gap", milliseconds(durations.maximumMilliseconds))
        PerformanceRow(
            "Slow frames (>${number(1500 / report.frames.targetFPS)} ms)",
            ScreenFormat.number(report.frames.slowFrames),
        )
        PerformanceRow("Hitches (>50 ms)", ScreenFormat.number(report.frames.hitches))
        PerformanceRow("Sampled peak memory", report.peakMemoryMB?.let { "${number(it)} MiB" } ?: "—")
        PerformanceRow("Peak sprites", ScreenFormat.number(report.peakSprites.toLong()))
        Text(
            "Percentiles show the interval that 50%, 95% or 99% of frames stayed within. Lower is better.",
            style = HudType.caption,
            color = Theme.textSecondary,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Time by System", style = HudType.headline, color = Theme.textPrimary)
        Text(
            "Sorted by total time. Expand a system for call timings. Parent timings include their subsystems.",
            style = HudType.caption,
            color = Theme.textSecondary,
        )
        report.rankedWork.forEach { (work, samples) ->
            var expanded by remember(report, work) { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(work.title, style = HudType.subheadline, color = Theme.accent)
                    Text(if (expanded) "▾" else "▸", style = HudType.subheadline, color = Theme.textSecondary)
                }
                Text(
                    "${milliseconds(samples.totalMilliseconds)} total · " +
                        "${milliseconds(samples.maximumMilliseconds)} worst",
                    style = HudType.caption,
                    color = Theme.textSecondary,
                )
            }
            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PerformanceRow("Calls", ScreenFormat.number(samples.count))
                    PerformanceRow("Average", milliseconds(samples.averageMilliseconds))
                    PerformanceRow("p95 · 95% at or below", milliseconds(samples.percentile(0.95)))
                    PerformanceRow("Worst call", milliseconds(samples.maximumMilliseconds))
                }
            }
        }
    }
    if (report.hitches.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassCard(16.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Longest Frame Gaps", style = HudType.headline, color = Theme.textPrimary)
            Text(
                "The eight longest gaps over 50 ms, with the largest nearby subsystem call. " +
                    "These are clues, not proof of a cause.",
                style = HudType.caption,
                color = Theme.textSecondary,
            )
            report.hitches.forEach {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${milliseconds(it.milliseconds)} at ${number(it.elapsed)} s",
                        style = HudType.subheadline,
                        color = Theme.textPrimary,
                    )
                    Text(it.nearbyWork, style = HudType.caption, color = Theme.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun PerformanceRow(
    title: String,
    value: String,
    valueColor: Color = Theme.textPrimary,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = HudType.subheadline, color = Theme.textSecondary)
        Text(value, style = HudType.subheadline, color = valueColor)
    }
}

private fun number(value: Double?): String = value?.let(PerformanceReport::number) ?: "—"

private fun milliseconds(value: Double?): String = value?.let(PerformanceReport::ms) ?: "—"
