package com.hunter.stockfarmer.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.Theme

@Composable
fun PerformanceScreen(
    session: GameSession,
    monitor: PerformanceMonitor,
    onClose: () -> Unit,
    recordingContext: () -> String,
    onShare: ((String) -> Unit)? = null,
) {
    val state by rememberScreenState(monitor) { monitor.snapshot }
    val context = LocalContext.current
    DisposableEffect(monitor) {
        monitor.setVisible(true)
        onDispose { monitor.setVisible(false) }
    }
    ScreenLayout("Performance", session, onClose) {
        PerformanceLiveStats(state)
        ScreenCard {
            if (state.isRecording) {
                Figure(
                    if (state.isActive) "Recording" else "Recording paused",
                    "${number(state.elapsed)} s",
                    Theme.gold,
                )
                Text("Play as usual, then return here to stop and see the report. Inactive time is excluded.")
                Button(onClick = monitor::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop Recording") }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Return to Farm") }
            } else {
                Text("Find slow work", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Record while planting, harvesting or exploring. Stop here to compare frame pacing " +
                        "and time spent in each system.",
                )
                Button(onClick = {
                    monitor.start(recordingContext())
                    onClose()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.report == null) "Record" else "New Recording")
                }
                Text(
                    "Recording opens the farm. Includes time in menus. The latest report is kept until " +
                        "replaced or the app closes.",
                    style = MaterialTheme.typography.bodySmall,
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
        Text(PerformanceReport.measurementNotes, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PerformanceLiveStats(state: PerformanceState) {
    ScreenCard {
        Figure("Live", "Target ${state.targetFPS.toInt()} FPS")
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
        Figure("Farm nodes", ScreenFormat.number(state.nodeCount.toLong()))
        Figure("Thermal state", state.thermal)
        if (state.liveFPS == null) {
            Text(
                "Waiting for farm frames. The scene may be paused while a menu covers it.",
                style = MaterialTheme.typography.bodySmall,
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
    ScreenCard {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PerformanceRecordingReport(
    report: PerformanceReport,
    onShare: () -> Unit,
) {
    ScreenCard {
        Text("Recording Report", style = MaterialTheme.typography.titleMedium)
        Text(
            "${number(
                report.activeSeconds,
            )} active seconds · ${ScreenFormat.number(report.frames.durations.count)} frames",
        )
        TextButton(onClick = onShare) { Text("Share performance report") }
        Text(report.insight)
    }
    ScreenCard {
        val durations = report.frames.durations
        Text("Frame Pacing", style = MaterialTheme.typography.titleMedium)
        Figure("Average FPS", number(report.frames.fps))
        Figure("p50 / median", milliseconds(durations.percentile(0.5)))
        Figure("p95", milliseconds(durations.percentile(0.95)))
        Figure("p99", milliseconds(durations.percentile(0.99)))
        Figure("Worst frame gap", milliseconds(durations.maximumMilliseconds))
        Figure(
            "Slow frames (>${number(1500 / report.frames.targetFPS)} ms)",
            ScreenFormat.number(report.frames.slowFrames),
        )
        Figure("Hitches (>50 ms)", ScreenFormat.number(report.frames.hitches))
        Figure("Sampled peak memory", report.peakMemoryMB?.let { "${number(it)} MiB" } ?: "—")
        Figure("Peak farm nodes", ScreenFormat.number(report.peakNodes.toLong()))
        Text(
            "Percentiles show the interval that 50%, 95% or 99% of frames stayed within. Lower is better.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    ScreenCard {
        Text("Time by System", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sorted by total time. Expand a system for call timings. Parent timings include their subsystems.",
            style = MaterialTheme.typography.bodySmall,
        )
        report.rankedWork.forEach { (work, samples) ->
            var expanded by remember(report, work) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) { Text("${if (expanded) "▾" else "▸"} ${work.title}") }
            Text(
                "${milliseconds(samples.totalMilliseconds)} total · ${milliseconds(samples.maximumMilliseconds)} worst",
            )
            if (expanded) {
                Figure("Calls", ScreenFormat.number(samples.count))
                Figure("Average", milliseconds(samples.averageMilliseconds))
                Figure("p95 · 95% at or below", milliseconds(samples.percentile(0.95)))
                Figure("Worst call", milliseconds(samples.maximumMilliseconds))
            }
        }
    }
    if (report.hitches.isNotEmpty()) {
        ScreenCard {
            Text("Longest Frame Gaps", style = MaterialTheme.typography.titleMedium)
            Text(
                "The eight longest gaps over 50 ms, with the largest nearby subsystem call. " +
                    "These are clues, not proof of a cause.",
                style = MaterialTheme.typography.bodySmall,
            )
            report.hitches.forEach {
                Text("${milliseconds(it.milliseconds)} at ${number(it.elapsed)} s")
                Text(it.nearbyWork, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun number(value: Double?): String = value?.let(PerformanceReport::number) ?: "—"

private fun milliseconds(value: Double?): String = value?.let(PerformanceReport::ms) ?: "—"
