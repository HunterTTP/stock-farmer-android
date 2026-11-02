package com.hunter.stockfarmer.ui.hud

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolCircleGestureTest {
    private val pending = mutableListOf<Pair<Long, () -> Unit>>()
    private val log = mutableListOf<String>()
    private var tool =
        ToolCircleTool(activationDelayMillis = 40, activatesImmediately = false, isChopping = false, reach = 16f)
    private var tileShows = true

    private val gesture =
        ToolCircleGesture(
            scale = { 1f },
            tool = { tool },
            showsTile = { tileShows },
            schedule = { delay, action ->
                val entry = delay to action
                pending.add(entry)
                return@ToolCircleGesture { pending.remove(entry) }
            },
            onTap = { log.add("tap") },
            onMove = { to, phase -> log.add("move $phase ${to.x},${to.y}") },
            onDrag = { from, to -> log.add("drag ${from.x},${from.y} ${to.x},${to.y}") },
            onHold = { log.add("hold") },
            onDragEnded = { log.add("ended") },
        )

    private val start = Offset(100f, 100f)

    @Test
    fun `a touch within the slop is a tap`() {
        assertFalse(gesture.change(start, Offset(104f, 104f)))
        gesture.end()
        assertEquals(listOf("tap"), log)
    }

    @Test
    fun `a drag past the slop never taps and ends once`() {
        assertTrue(gesture.change(start, Offset(120f, 100f)))
        gesture.end()
        assertEquals("ended", log.last())
        assertFalse(log.contains("tap"))
    }

    @Test
    fun `the tool wakes after one button width and the pause`() {
        gesture.change(start, Offset(120f, 100f))
        assertFalse(gesture.isActive)
        gesture.change(start, Offset(190f, 100f))
        assertFalse(gesture.isActive)
        assertEquals(listOf(40L, ToolCircleMetrics.EXPLAIN_DELAY_MILLIS), pending.map { it.first })
        pending.first { it.first == 40L }.second()
        assertTrue(gesture.isActive)
        assertTrue(log.any { it.startsWith("drag") })
    }

    @Test
    fun `a tool with no pause wakes the moment the distance is reached`() {
        tool = tool.copy(activationDelayMillis = null)
        gesture.change(start, Offset(120f, 100f))
        assertFalse(gesture.isActive)
        gesture.change(start, Offset(177f, 100f))
        assertFalse(gesture.isActive)
        gesture.change(start, Offset(178f, 100f))
        assertTrue(gesture.isActive)
        assertTrue(pending.none { it.first != ToolCircleMetrics.EXPLAIN_DELAY_MILLIS })
    }

    @Test
    fun `the distance is one button width scaled by the HUD`() {
        val scaled =
            ToolCircleGesture(
                scale = { 2f },
                tool = { tool.copy(activationDelayMillis = null) },
                showsTile = { true },
                schedule = { _, _ -> {} },
                onTap = {},
                onMove = { _, _ -> },
                onDrag = { _, _ -> },
                onHold = {},
                onDragEnded = {},
            )
        scaled.change(start, Offset(120f, 100f))
        scaled.change(start, Offset(255f, 100f))
        assertFalse(scaled.isActive)
        scaled.change(start, Offset(256f, 100f))
        assertTrue(scaled.isActive)
    }

    @Test
    fun `the scythe and bucket skip the distance`() {
        tool = tool.copy(activationDelayMillis = null, activatesImmediately = true)
        gesture.change(start, Offset(120f, 100f))
        assertTrue(gesture.isActive)
    }

    @Test
    fun `a finger that rests asks the game to explain`() {
        tool = tool.copy(activationDelayMillis = null, activatesImmediately = true)
        gesture.change(start, Offset(120f, 100f))
        pending.first { it.first == ToolCircleMetrics.EXPLAIN_DELAY_MILLIS }.second()
        assertEquals("hold", log.last())
    }

    @Test
    fun `cancelling ends the drag without a tap`() {
        gesture.change(start, Offset(120f, 100f))
        gesture.cancel()
        assertEquals("ended", log.last())
        assertFalse(log.contains("tap"))
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `cancelling a pending activation clears timers and permits a fresh tap`() {
        gesture.change(start, Offset(190f, 100f))
        assertEquals(2, pending.size)
        gesture.cancel()
        assertTrue(pending.isEmpty())
        assertFalse(gesture.isActive)
        assertFalse(gesture.isDragging)
        gesture.cancel()
        assertEquals(1, log.count { it == "ended" })
        gesture.end()
        assertEquals("tap", log.last())
    }

    @Test
    fun `tools aim up and right of the finger where a tile shows, the axe at the finger`() {
        val finger = Offset(100f, 100f)
        assertEquals(Offset(116f, 84f), aimedTarget(finger, tool, 1f, showsTile = { true }))
        assertEquals(Offset(132f, 68f), aimedTarget(finger, tool, 2f, showsTile = { true }))
        assertEquals(finger, aimedTarget(finger, tool, 1f, showsTile = { false }))
        assertEquals(finger, aimedTarget(finger, tool.copy(isChopping = true), 1f, showsTile = { true }))
    }
}
