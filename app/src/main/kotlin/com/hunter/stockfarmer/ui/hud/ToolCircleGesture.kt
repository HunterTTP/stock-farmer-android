package com.hunter.stockfarmer.ui.hud

import androidx.compose.ui.geometry.Offset

enum class ToolDragPhase { Inactive, Began, Changed }

/** Sizes and timings of the tool circle, in dp before the HUD scale. */
object ToolCircleMetrics {
    const val DIAMETER = 78f

    /** Movement under this many points still counts as a tap. */
    const val TAP_SLOP = 8f

    /** How far the finger may drift while it counts as holding still. */
    const val HOLD_SLOP = 8f
    const val DRAGGED_OPACITY = 0.35f
    const val DRAGGED_SCALE = 1.1f
    const val EXPLAIN_DELAY_MILLIS = 350L
    const val ICON_PADDING = 16f
    const val DEFAULT_REACH = 16f
    const val SHADOW_RADIUS = 4f
    const val SHADOW_ALPHA = 0.25f
}

/** What the gesture needs to know about the tool in hand, read afresh at each touch event. */
data class ToolCircleTool(
    val activationDelayMillis: Long?,
    val activatesImmediately: Boolean,
    val isChopping: Boolean,
    /** How far up and to the right of the circle's center the tool works, in points before the HUD scale. */
    val reach: Float,
)

/** Where the tool works for a finger: the axe at the finger, others up and to the right where a tile shows, else at the finger. */
fun aimedTarget(
    finger: Offset,
    tool: ToolCircleTool,
    scale: Float,
    showsTile: (Offset) -> Boolean,
): Offset {
    if (tool.isChopping) return finger
    val shift = tool.reach * scale
    val aimed = Offset(finger.x + shift, finger.y - shift)
    return if (showsTile(aimed)) aimed else finger
}

/**
 * The tool circle's touch handling: a touch that stays within the tap slop is a tap; otherwise
 * the circle follows the finger, the tool wakes after the finger has gone one button width (and, for tools with a pause, rested),
 * and a finger that rests for a while asks the game to explain itself. Timers are injected so the logic is plain Kotlin.
 */
class ToolCircleGesture(
    private val scale: () -> Float,
    private val tool: () -> ToolCircleTool,
    private val showsTile: (Offset) -> Boolean,
    private val schedule: (delayMillis: Long, action: () -> Unit) -> () -> Unit,
    private val onTap: () -> Unit,
    private val onMove: (to: Offset, phase: ToolDragPhase) -> Unit,
    private val onDrag: (from: Offset, to: Offset) -> Unit,
    private val onHold: () -> Unit,
    private val onDragEnded: () -> Unit,
) {
    var lastLocation: Offset? = null
        private set
    var isActive = false
        private set
    private var dragOrigin: Offset? = null
    private var holdAnchor: Offset? = null
    private var hasReachedDistance = false
    private var cancelActivation: (() -> Unit)? = null
    private var cancelHold: (() -> Unit)? = null

    val isDragging: Boolean get() = lastLocation != null

    private fun target(finger: Offset) = aimedTarget(finger, tool(), scale(), showsTile)

    /** The finger moved to [location] on a touch that began at [start]; true once the circle is being dragged. */
    fun change(
        start: Offset,
        location: Offset,
    ): Boolean {
        if (lastLocation == null) {
            if ((location - start).getDistance() <= ToolCircleMetrics.TAP_SLOP) return false
            lastLocation = start
            dragOrigin = start
        }
        val wasActive = isActive
        val current = tool()
        val origin = dragOrigin
        val anchor = holdAnchor
        if (!hasReachedDistance && origin != null &&
            (current.activatesImmediately || (location - origin).getDistance() >= ToolCircleMetrics.DIAMETER * scale())
        ) {
            hasReachedDistance = true
            if (current.activationDelayMillis != null) armActivation(location) else activate(null)
            armExplanation(location)
        } else if (hasReachedDistance && anchor != null &&
            (location - anchor).getDistance() > ToolCircleMetrics.HOLD_SLOP
        ) {
            armActivation(location)
            armExplanation(location)
        }
        onMove(
            target(location),
            if (isActive) (if (wasActive) ToolDragPhase.Changed else ToolDragPhase.Began) else ToolDragPhase.Inactive,
        )
        lastLocation?.let { previous ->
            if (isActive) onDrag(target(if (!wasActive) location else previous), target(location))
        }
        lastLocation = location
        return true
    }

    private fun armActivation(location: Offset) {
        val delay = tool().activationDelayMillis
        if (!hasReachedDistance || isActive || delay == null) return
        cancelActivation?.invoke()
        cancelActivation = schedule(delay) { activate(location) }
    }

    private fun activate(rest: Offset?) {
        if (!hasReachedDistance || isActive) return
        isActive = true
        cancelActivation?.invoke()
        cancelActivation = null
        if (rest != null) {
            onMove(target(rest), ToolDragPhase.Began)
            onDrag(target(rest), target(rest))
            armExplanation(rest)
        }
    }

    private fun armExplanation(location: Offset) {
        holdAnchor = location
        cancelHold?.invoke()
        cancelHold = schedule(ToolCircleMetrics.EXPLAIN_DELAY_MILLIS) { if (isActive) onHold() }
    }

    /** The finger lifted: a drag ends, anything shorter was a tap. */
    fun end() {
        stopTimers()
        if (isDragging) onDragEnded() else onTap()
        reset()
    }

    /** The touch was interrupted: the drag ends without a tap. */
    fun cancel() {
        stopTimers()
        if (isDragging) onDragEnded()
        reset()
    }

    private fun stopTimers() {
        cancelActivation?.invoke()
        cancelHold?.invoke()
        cancelActivation = null
        cancelHold = null
    }

    private fun reset() {
        lastLocation = null
        dragOrigin = null
        holdAnchor = null
        hasReachedDistance = false
        isActive = false
    }
}
