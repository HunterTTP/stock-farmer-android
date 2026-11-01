package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.feedback.PerformanceDurations
import com.hunter.stockfarmer.core.feedback.PerformanceFrames
import java.time.Instant
import java.util.Locale

@Suppress("ktlint:standard:enum-entry-name-case")
enum class PerformanceWork(
    val title: String,
    val isContainer: Boolean = false,
) {
    scene("Scene update (includes subsystems)", true),
    actions("Scene actions & physics"),
    structure("Farm structure sync"),
    crops("Crop visuals & growth"),
    animals("Animal & fish visuals"),
    animalRules("Animal production & movement"),
    tractor("Tractor rules & visuals"),
    shadows("Shadows"),
    night("Night lighting & midnight wave"),
    wildlife("Fireflies & butterflies"),
    weather("Season, weather & decorations"),
    precipitation("Rain & snow scene"),
    fishing("Fishing contest rules"),
    watering("Sprinkler watering rules"),
    tick("One-second game tick (includes rules)", true),
    workshops("Workshops & building status"),
    tap("Farm taps (includes state & feedback)", true),
    drag("Tool strokes (includes state & feedback)", true),
    preview("Tool & placement previews"),
    commit("State sync & feedback"),
    saveQueue("Save snapshot & enqueue"),
    saveFlush("Save flush / waiting for disk"),
}

data class PerformanceHitch(
    val elapsed: Double,
    val milliseconds: Double,
    val nearbyWork: String,
)

data class PerformanceReport(
    val startedAt: Instant,
    val environment: String,
    val frames: PerformanceFrames,
    val activeSeconds: Double = 0.0,
    val work: Map<PerformanceWork, PerformanceDurations> = emptyMap(),
    val peakMemoryMB: Double? = null,
    val peakNodes: Int = 0,
    val hitches: List<PerformanceHitch> = emptyList(),
    val thermalStates: Set<String> = emptySet(),
) {
    val rankedWork: List<Pair<PerformanceWork, PerformanceDurations>> get() =
        work.toList().sortedByDescending { it.second.totalMilliseconds }

    val insight: String get() {
        if (frames.durations.count == 0L) {
            return "No farm frames captured. Return to the farm and play before stopping."
        }
        val busiest =
            rankedWork.firstOrNull { !it.first.isContainer }
                ?: return "No system timings captured yet. A longer recording will give a more useful comparison."
        return "${busiest.first.title} used the most measured subsystem time. " +
            "Its worst call took ${ms(busiest.second.maximumMilliseconds)}. " +
            "Compare this with slow frames below; timing alone does not prove the cause."
    }

    val text: String get() =
        buildList {
            add("Stock Farmer — Performance recording")
            add(startedAt.toString())
            add(environment)
            add("Active duration: ${number(activeSeconds)} s; includes menus, excludes inactive time.")
            add("Target: ${frames.targetFPS.toInt()} FPS; measured farm cadence: ${number(frames.fps)} FPS")
            add(
                "Frames: ${frames.durations.count}; slow (>1.5× budget): ${frames.slowFrames}; " +
                    "hitches (>50 ms): ${frames.hitches}",
            )
            add(
                "Frame ms: p50 ${ms(frames.durations.percentile(0.5))}, " +
                    "p95 ${ms(frames.durations.percentile(0.95))}, p99 ${ms(frames.durations.percentile(0.99))}, " +
                    "worst ${ms(frames.durations.maximumMilliseconds)}",
            )
            add(
                "Sampled peak memory: ${peakMemoryMB?.let { number(it) + " MiB" } ?: "unavailable"}; " +
                    "peak farm nodes: $peakNodes",
            )
            add("Thermal states: ${thermalStates.sorted().joinToString(", ")}")
            add("")
            add(insight)
            add("")
            add("System | Calls | Total ms | Average ms | p95 ms | Worst ms")
            rankedWork.forEach { (work, samples) ->
                add(
                    "${work.title} | ${samples.count} | ${number(samples.totalMilliseconds)} | " +
                        "${number(samples.averageMilliseconds)} | ${number(samples.percentile(0.95))} | " +
                        number(samples.maximumMilliseconds),
                )
            }
            add("\nLongest frame gaps (nearby work is correlation, not attribution)")
            hitches.forEach { add("At ${number(it.elapsed)} s: ${ms(it.milliseconds)}; ${it.nearbyWork}") }
            add("\n$measurementNotes")
        }.joinToString("\n")

    companion object {
        fun ms(value: Double): String = String.format(Locale.US, "%.2f ms", value)

        fun number(value: Double): String = String.format(Locale.US, "%.1f", value)

        val measurementNotes =
            "FPS measures intervals between farm scene updates, not GPU presentation. " +
                "System timings are elapsed work on the instrumented thread, include nested calls, " +
                "and must not be added together. Rendering, background save writes and uninstrumented work " +
                "are not separately timed. Percentiles round up to 0.25 ms; values above 250 ms use the observed " +
                "maximum. Memory (Android proportional set size) and farm node counts are sampled once a second " +
                "when the host supplies them. Recording adds some overhead. Reports stay in memory until the app " +
                "closes; Share exports a copy."
    }
}
