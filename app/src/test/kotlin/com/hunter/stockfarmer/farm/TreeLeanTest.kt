package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.pow

internal class TreeLeanTest {
    private class SeededRandom(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
    }

    private val start = Instant.ofEpochSecond(1_000)
    private val width = GameState.GRID_SIZE * FarmRenderer.TILE
    private val tree = GridPoint(10, 10)

    private fun lean() = TreeLean(SeededRandom(7))

    @Test
    fun `a tree leans for 1_3 seconds then straightens over 1_5 more`() {
        assertEquals(0f, TreeLean.swing(0.0))
        assertEquals(0.5f, TreeLean.swing(0.65), 1e-6f)
        assertEquals(1f, TreeLean.swing(1.3), 1e-6f)
        assertEquals(0.5f, TreeLean.swing(2.05), 1e-6f)
        assertEquals(0f, TreeLean.swing(2.8))
        assertEquals(0f, TreeLean.swing(5.0))
    }

    @Test
    fun `the higher the strip the further its edge moves with the foot staying put`() {
        assertEquals(0f, TreeLean.rowShift(0.06f, 0))
        assertEquals(0.06f * 0.5f.pow(2.5f), TreeLean.rowShift(0.06f, 2), 1e-6f)
        assertEquals(0.06f, TreeLean.rowShift(0.06f, TreeLean.STRIPS), 1e-6f)
        assertEquals(-0.04f, TreeLean.rowShift(-0.04f, TreeLean.STRIPS), 1e-6f)
    }

    @Test
    fun `a tree stands straight until a gust has been dealt out to it`() {
        val lean = lean()
        lean.gust(true, start)
        assertTrue(lean.isPending)
        assertEquals(0f, lean.tipBend(tree, start.plusMillis(1_300)))
    }

    @Test
    fun `a gust is dealt out once`() {
        val lean = lean()
        lean.gust(true, start)
        lean.reach(mapOf(tree to 0f))
        assertFalse(lean.isPending)
    }

    @Test
    fun `a lone tree is always reached and bends its tip 3 to 7_5 percent of its width the way the gust blows`() {
        for (pushesRight in listOf(true, false)) {
            val lean = lean()
            lean.gust(pushesRight, start)
            lean.reach(mapOf(tree to 0f))
            // A rightward gust is on the left edge at once; a leftward one takes the whole sweep to get there.
            val arrives = if (pushesRight) 0 else 1_500
            val peak = lean.tipBend(tree, start.plusMillis(arrives + 1_300L))
            assertTrue(abs(peak) in 0.03f..0.075f, "$peak")
            assertEquals(pushesRight, peak > 0)
            assertEquals(0f, lean.tipBend(tree, start.plusMillis(arrives + 2_800L)))
        }
    }

    @Test
    fun `a gust sweeps across the map the way it blows`() {
        val lean = lean()
        lean.gust(true, start)
        lean.reach(mapOf(tree to width))
        assertEquals(0f, lean.tipBend(tree, start.plusMillis(1_400)))
        assertEquals(0f, lean.tipBend(tree, start.plusMillis(1_500)))
        assertTrue(lean.tipBend(tree, start.plusMillis(2_800)) > 0f)
    }

    @Test
    fun `a gust reaches a quarter to 6 tenths of the trees`() {
        val trees = (0 until 20).associate { GridPoint(it, 0) to 0f }
        repeat(20) { gustNumber ->
            val lean = TreeLean(SeededRandom(gustNumber.toLong()))
            lean.gust(true, start)
            lean.reach(trees)
            val bent = trees.keys.count { lean.tipBend(it, start.plusMillis(1_300)) != 0f }
            assertTrue(bent in 5..12, "$bent")
        }
    }
}
