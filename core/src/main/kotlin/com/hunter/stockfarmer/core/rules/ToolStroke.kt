package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TimelapseSettings
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** One drag of a tool over the farm, from the moment the tool becomes active until the finger lifts. */
class ToolStroke {
    private var hasReportedProblem = false

    /**
     * Uses the tool on each tile in order. Tiles that can't take it are passed over quietly, and only the first real
     * problem of the stroke, such as running out of money or hitting a limit, is reported.
     */
    fun use(
        engine: GameEngine,
        tiles: List<GridPoint>,
        now: Instant,
    ): List<GameEvent> =
        tiles.flatMap { engine.useTool(it, now) }.filter { event ->
            if (event !is GameEvent.Rejected) return@filter true
            if (event.rejection.isTileSpecific || hasReportedProblem) return@filter false
            hasReportedProblem = true
            true
        }

    /** The finger lifted, so the axe's next meeting with a tree is a new pass. */
    fun end(engine: GameEngine) = engine.liftTool()
}

/** Observes only active farm time. Reset after loading, resuming, or editing settings to avoid catch-up captures. */
class TimelapseSchedule {
    private class Previous(
        val day: Int,
        val minute: Double,
        val enabled: Boolean,
        val captureMinute: Int,
    )

    private var previous: Previous? = null

    fun observe(
        day: Int,
        minute: Double,
        settings: TimelapseSettings,
        active: Boolean,
    ): Boolean {
        if (!active) {
            previous = null
            return false
        }
        val before = previous
        previous = Previous(day, minute, settings.isEnabled, settings.captureMinute)
        if (!settings.isEnabled || settings.lastCapturedDay == day || before == null ||
            !before.enabled || before.captureMinute != settings.captureMinute
        ) {
            return false
        }
        val target = settings.captureMinute.toDouble()
        if (day > before.day) return minute >= target
        return day == before.day && before.minute < target && minute >= target
    }
}

fun GameEngine.prepareTimelapse() {
    if (state.timelapse.historyID == null) state.timelapse = state.timelapse.copy(historyID = UUID.randomUUID())
}

fun GameEngine.setTimelapseEnabled(enabled: Boolean) {
    state.timelapse = state.timelapse.copy(isEnabled = enabled)
}

fun GameEngine.setTimelapseMinute(minute: Int) {
    state.timelapse = state.timelapse.copy(captureMinute = min(max(minute, 0), 1439))
}

fun GameEngine.setTimelapseCrossfade(enabled: Boolean) {
    state.timelapse = state.timelapse.copy(crossfades = enabled)
}

fun GameEngine.recordTimelapseCapture(day: Int) {
    state.timelapse = state.timelapse.copy(lastCapturedDay = day)
}
