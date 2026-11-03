package com.hunter.stockfarmer.farm

import kotlin.math.abs

/** What the farm does with its touches. Pinch positions are the first two fingers down, in screen pixels. */
internal interface FarmGestureListener {
    fun touchDown(
        x: Float,
        y: Float,
        isFirst: Boolean,
    )

    fun tap(
        x: Float,
        y: Float,
    )

    fun longPress(
        x: Float,
        y: Float,
    )

    fun pan(
        x: Float,
        y: Float,
        deltaX: Float,
        deltaY: Float,
    )

    fun panStop()

    fun pinch(
        initial1: FingerPoint,
        initial2: FingerPoint,
        current1: FingerPoint,
        current2: FingerPoint,
    )

    fun pinchStop()
}

internal data class FingerPoint(
    val x: Float,
    val y: Float,
)

/**
 * Tells a tap, hold, pan and pinch apart from the fingers genuinely down on the farm.
 * It counts the fingers itself instead of asking libGDX, so a stale second pointer, or a lone finger that Android
 * numbered 1, can never turn a one-finger drag into a zoom: a pinch needs two fingers this class saw go down.
 */
internal class FarmGestureRecognizer(
    private val listener: FarmGestureListener,
    private val tapRadius: Float = TAP_RADIUS,
    private val longPressMillis: Long = LONG_PRESS_MILLIS,
) {
    private class Finger(
        var x: Float,
        var y: Float,
    )

    private val fingers = LinkedHashMap<Int, Finger>()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var inTapSquare = true
    private var longPressPending = false
    private var longPressFired = false
    private var panning = false
    private var pinching = false
    private var initial1 = FingerPoint(0f, 0f)
    private var initial2 = FingerPoint(0f, 0f)

    fun touchDown(
        id: Int,
        x: Float,
        y: Float,
        now: Long,
    ) {
        // A second down for a finger already counted means its up was lost.
        if (fingers.remove(id) != null && fingers.isEmpty()) endGesture()
        val isFirst = fingers.isEmpty()
        fingers[id] = Finger(x, y)
        listener.touchDown(x, y, isFirst)
        if (isFirst) {
            downX = x
            downY = y
            downTime = now
            inTapSquare = true
            longPressPending = true
            longPressFired = false
            panning = false
            pinching = false
        } else if (!pinching) {
            startPinch()
        }
    }

    fun touchDragged(
        id: Int,
        x: Float,
        y: Float,
    ) {
        val finger = fingers[id] ?: return
        val deltaX = x - finger.x
        val deltaY = y - finger.y
        finger.x = x
        finger.y = y
        if (pinching) {
            val pair = pinchPair()
            if (id == pair.first || id == pair.second) {
                listener.pinch(initial1, initial2, point(pair.first), point(pair.second))
            }
            return
        }
        if (inTapSquare && (abs(x - downX) >= tapRadius || abs(y - downY) >= tapRadius)) {
            inTapSquare = false
            longPressPending = false
        }
        if (!inTapSquare) {
            panning = true
            listener.pan(x, y, deltaX, deltaY)
        }
    }

    fun touchUp(
        id: Int,
        x: Float,
        y: Float,
    ) {
        if (fingers.isEmpty() || id !in fingers) return
        val pair = pinchPair()
        fingers.remove(id)
        if (pinching) {
            if (id != pair.first && id != pair.second) return
            pinching = false
            listener.pinchStop()
            when {
                fingers.size >= 2 -> {
                    startPinch()
                }

                fingers.size == 1 -> {
                    inTapSquare = false
                    panning = true
                }
            }
        }
        if (fingers.isEmpty()) {
            val wasPanning = panning
            val isTap = inTapSquare && !longPressFired && !wasPanning
            endGesture()
            if (wasPanning) {
                listener.panStop()
            } else if (isTap) {
                listener.tap(x, y)
            }
        }
    }

    /** The system took the touches away (cancel, pause, a menu opening): forget every finger without a tap. */
    fun cancel() {
        if (fingers.isEmpty()) return
        val wasPinching = pinching
        val wasPanning = panning
        fingers.clear()
        endGesture()
        if (wasPinching) {
            listener.pinchStop()
        } else if (wasPanning) {
            listener.panStop()
        }
    }

    /** Fires a hold once a single finger has stayed put long enough. */
    fun update(now: Long) {
        if (!longPressPending || fingers.size != 1 || now - downTime < longPressMillis) return
        longPressPending = false
        longPressFired = true
        val finger = fingers.values.first()
        listener.longPress(finger.x, finger.y)
    }

    private fun startPinch() {
        pinching = true
        inTapSquare = false
        panning = false
        longPressPending = false
        val pair = pinchPair()
        initial1 = point(pair.first)
        initial2 = point(pair.second)
    }

    private fun endGesture() {
        inTapSquare = true
        longPressPending = false
        longPressFired = false
        panning = false
        pinching = false
    }

    private fun pinchPair(): Pair<Int, Int> {
        val ids = fingers.keys.iterator()
        val first = if (ids.hasNext()) ids.next() else -1
        return first to if (ids.hasNext()) ids.next() else -1
    }

    private fun point(id: Int): FingerPoint = fingers[id]?.let { FingerPoint(it.x, it.y) } ?: FingerPoint(0f, 0f)

    companion object {
        const val TAP_RADIUS = 20f
        const val LONG_PRESS_MILLIS = 350L
    }
}
