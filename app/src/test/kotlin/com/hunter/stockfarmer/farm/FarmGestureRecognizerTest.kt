package com.hunter.stockfarmer.farm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** One finger pans and taps; only two fingers genuinely down on the farm pinch. */
internal class FarmGestureRecognizerTest {
    private val events = mutableListOf<String>()
    private val recognizer =
        FarmGestureRecognizer(
            object : FarmGestureListener {
                override fun touchDown(
                    x: Float,
                    y: Float,
                    isFirst: Boolean,
                ) {
                    events += "down$isFirst"
                }

                override fun tap(
                    x: Float,
                    y: Float,
                ) {
                    events += "tap"
                }

                override fun longPress(
                    x: Float,
                    y: Float,
                ) {
                    events += "hold"
                }

                override fun pan(
                    x: Float,
                    y: Float,
                    deltaX: Float,
                    deltaY: Float,
                ) {
                    events += "pan"
                }

                override fun panStop() {
                    events += "panStop"
                }

                override fun pinch(
                    initial1: FingerPoint,
                    initial2: FingerPoint,
                    current1: FingerPoint,
                    current2: FingerPoint,
                ) {
                    events += "pinch"
                }

                override fun pinchStop() {
                    events += "pinchStop"
                }
            },
        )

    private fun dragLone(id: Int) {
        recognizer.touchDown(id, 100f, 100f, 0)
        recognizer.touchDragged(id, 150f, 100f)
        recognizer.touchUp(id, 150f, 100f)
    }

    @Test
    fun `a lone finger pans`() {
        dragLone(0)
        assertEquals(listOf("downtrue", "pan", "panStop"), events)
    }

    @Test
    fun `a lone finger numbered one pans instead of pinching`() {
        dragLone(1)
        assertEquals(listOf("downtrue", "pan", "panStop"), events)
    }

    @Test
    fun `a pointer that never went up is forgotten by a cancel so the next finger pans`() {
        recognizer.touchDown(1, 10f, 10f, 0)
        recognizer.touchDown(0, 300f, 300f, 0)
        recognizer.cancel()
        events.clear()
        dragLone(0)
        assertEquals(listOf("downtrue", "pan", "panStop"), events)
    }

    @Test
    fun `a repeated down for a counted pointer replaces the lost one instead of pinching`() {
        recognizer.touchDown(0, 10f, 10f, 0)
        events.clear()
        dragLone(0)
        assertEquals(listOf("downtrue", "pan", "panStop"), events)
    }

    @Test
    fun `two fingers pinch and the one left pans`() {
        recognizer.touchDown(0, 100f, 100f, 0)
        recognizer.touchDown(1, 200f, 100f, 0)
        recognizer.touchDragged(1, 260f, 100f)
        recognizer.touchUp(1, 260f, 100f)
        recognizer.touchDragged(0, 120f, 100f)
        recognizer.touchUp(0, 120f, 100f)
        assertEquals(listOf("downtrue", "downfalse", "pinch", "pinchStop", "pan", "panStop"), events)
    }

    @Test
    fun `a short touch taps and a still touch holds without tapping`() {
        recognizer.touchDown(0, 100f, 100f, 0)
        recognizer.touchUp(0, 101f, 100f)
        recognizer.touchDown(0, 100f, 100f, 1_000)
        recognizer.update(1_400)
        recognizer.touchUp(0, 100f, 100f)
        assertEquals(listOf("downtrue", "tap", "downtrue", "hold"), events)
    }

    @Test
    fun `an up for an unknown pointer is ignored`() {
        recognizer.touchUp(1, 0f, 0f)
        assertEquals(emptyList<String>(), events)
        assertEquals(0, recognizer.fingerCount)
    }
}
