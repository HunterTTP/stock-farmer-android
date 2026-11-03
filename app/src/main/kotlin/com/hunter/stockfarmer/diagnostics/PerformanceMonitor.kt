package com.hunter.stockfarmer.diagnostics

import com.hunter.stockfarmer.core.feedback.PerformanceDurations
import com.hunter.stockfarmer.core.feedback.PerformanceFrames
import com.hunter.stockfarmer.diagnostics.PerformanceHitch
import com.hunter.stockfarmer.diagnostics.PerformanceReport
import com.hunter.stockfarmer.diagnostics.PerformanceWork
import java.time.Instant

data class PerformanceResources(
    val memoryMB: Double? = null,
    val spriteCount: Int = 0,
    val thermal: String = "Unknown",
)

data class PerformanceState(
    val targetFPS: Double,
    val isRecording: Boolean = false,
    val isActive: Boolean = true,
    val liveFPS: Double? = null,
    val liveFrameMS: Double? = null,
    val liveUpdateMS: Double? = null,
    val memoryMB: Double? = null,
    val spriteCount: Int = 0,
    val thermal: String = "Unknown",
    val elapsed: Double = 0.0,
    val report: PerformanceReport? = null,
)

/** Host-owned recorder shared by the renderer and UI. All clocks are monotonic seconds; snapshots are immutable. */
class PerformanceMonitor(
    targetFPS: Double = 60.0,
    private val clock: () -> Double = { System.nanoTime() / 1e9 },
    private val wallClock: () -> Instant = Instant::now,
) {
    private var state = PerformanceState(targetFPS.coerceAtLeast(1.0))
    private var visible = false
    private var window = PerformanceFrames(state.targetFPS)
    private var updates = PerformanceDurations()
    private var capture: PerformanceReport? = null
    private var activeSince: Double? = null
    private var completedSeconds = 0.0
    private var nearbyWork: Pair<PerformanceWork, Double>? = null
    private var lastFrame: Double? = null
    private var resources = PerformanceResources()

    val snapshot: PerformanceState get() = synchronized(this) { state }
    val isSampling: Boolean get() = synchronized(this) { enabled }
    private val enabled: Boolean get() = state.isActive && (visible || state.isRecording)

    @Synchronized
    fun setVisible(visible: Boolean) {
        this.visible = visible
        if (!enabled) resetWindow()
    }

    @Synchronized
    fun setActive(active: Boolean) {
        if (active == state.isActive) return
        activeSince?.let { completedSeconds += clock() - it }
        activeSince = if (active && state.isRecording) clock() else null
        state = state.copy(isActive = active)
        capture = capture?.let { it.copy(frames = it.frames.resetClock()) }
        resetWindow()
    }

    @Synchronized
    fun start(context: String) {
        if (state.isRecording) return
        capture = PerformanceReport(wallClock(), context, PerformanceFrames(state.targetFPS))
        completedSeconds = 0.0
        activeSince = if (state.isActive) clock() else null
        state = state.copy(isRecording = true, elapsed = 0.0)
        resetWindow()
        sample(resources)
    }

    @Synchronized
    fun stop() {
        if (!state.isRecording) return
        sample(resources)
        val elapsed = activeDuration()
        state = state.copy(isRecording = false, elapsed = elapsed, report = capture?.copy(activeSeconds = elapsed))
        capture = null
        activeSince = null
        if (!enabled) resetWindow()
    }

    @Synchronized
    fun begin(): Double? = if (enabled) clock() else null

    @Synchronized
    fun end(
        work: PerformanceWork,
        since: Double?,
    ) {
        if (!enabled || since == null) return
        val seconds = clock() - since
        if (!seconds.isFinite() || seconds < 0) return
        if (work == PerformanceWork.scene) updates = updates.add(seconds)
        val recording = capture ?: return
        val samples = (recording.work[work] ?: PerformanceDurations()).add(seconds)
        capture = recording.copy(work = recording.work + (work to samples))
        if (!work.isContainer && seconds > (nearbyWork?.second ?: 0.0)) nearbyWork = work to seconds
    }

    fun <T> measure(
        work: PerformanceWork,
        action: () -> T,
    ): T {
        val start = begin()
        try {
            return action()
        } finally {
            end(work, start)
        }
    }

    @Synchronized
    fun frame() {
        if (!enabled) return
        val now = clock()
        lastFrame = now
        window = window.observe(now).first
        capture?.let { recording ->
            val (frames, seconds) = recording.frames.observe(now)
            var hitches = recording.hitches
            if (seconds != null && seconds > 0.05) {
                val description =
                    nearbyWork?.let { "${it.first.title}: ${PerformanceReport.ms(it.second * 1000)}" }
                        ?: "No measured subsystem work nearby"
                val hitch = PerformanceHitch(activeDuration(), seconds * 1000, description)
                hitches = (hitches + hitch).sortedByDescending { it.milliseconds }.take(8)
            }
            capture = recording.copy(frames = frames, hitches = hitches)
        }
        nearbyWork = null
    }

    /** Called once a second by the foreground owner, even after the screen closes during a recording. */
    @Synchronized
    fun sample(resources: PerformanceResources = this.resources) {
        if (!enabled) return
        this.resources = resources
        state =
            state.copy(
                liveFPS = window.fps.takeIf { window.durations.count > 0 },
                liveFrameMS = window.durations.averageMilliseconds.takeIf { window.durations.count > 0 },
                liveUpdateMS = updates.averageMilliseconds.takeIf { updates.count > 0 },
                memoryMB = resources.memoryMB,
                spriteCount = resources.spriteCount,
                thermal = resources.thermal,
            )
        window = PerformanceFrames(state.targetFPS)
        lastFrame?.let { window = window.observe(it).first }
        updates = PerformanceDurations()
        capture?.let { recording ->
            val elapsed = activeDuration()
            state = state.copy(elapsed = elapsed)
            capture =
                recording.copy(
                    activeSeconds = elapsed,
                    peakMemoryMB =
                        resources.memoryMB?.let { maxOf(it, recording.peakMemoryMB ?: 0.0) }
                            ?: recording.peakMemoryMB,
                    peakSprites = maxOf(recording.peakSprites, resources.spriteCount),
                    thermalStates = recording.thermalStates + resources.thermal,
                )
        }
    }

    private fun activeDuration(): Double = completedSeconds + (activeSince?.let { clock() - it } ?: 0.0)

    private fun resetWindow() {
        window = PerformanceFrames(state.targetFPS)
        updates = PerformanceDurations()
        lastFrame = null
        nearbyWork = null
        state = state.copy(liveFPS = null, liveFrameMS = null, liveUpdateMS = null)
    }
}

/** Runs [action] once, timed under [work] when there is a monitor; a null result never makes it run again. */
fun <T> PerformanceMonitor?.measureOrRun(
    work: PerformanceWork,
    action: () -> T,
): T = if (this == null) action() else measure(work, action)
