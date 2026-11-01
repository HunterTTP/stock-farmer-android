package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AnimalMotionTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val point = GridPoint(10, 10)

    @Test
    fun `facing accepts cardinal moves and rejects zero and diagonal moves`() {
        assertEquals(Facing.RIGHT, Facing.from(GridPoint(0, 2)))
        assertEquals(Facing.LEFT, Facing.from(GridPoint(0, -1)))
        assertEquals(Facing.UP, Facing.from(GridPoint(-1, 0)))
        assertEquals(Facing.DOWN, Facing.from(GridPoint(1, 0)))
        assertNull(Facing.from(GridPoint(0, 0)))
        assertNull(Facing.from(GridPoint(1, 1)))
    }

    @Test
    fun `a game step interpolates for half a second and stops at its tile`() {
        val motion = AnimalMotion(point, Facing.DOWN, now)
        val target = GridPoint(10, 11)
        assertEquals(10f, motion.update(target, now).col)
        val halfway = motion.update(target, now.plusMillis(250))
        assertEquals(10.5f, halfway.col)
        assertEquals(Facing.RIGHT, halfway.facing)
        assertTrue(halfway.walking)
        val arrived = motion.update(target, now.plusMillis(500))
        assertEquals(11f, arrived.col)
        assertEquals(0.0, arrived.elapsed)
        assertFalse(arrived.walking)
    }

    @Test
    fun `vertical moves retain the last horizontal mirror for cats`() {
        val motion = AnimalMotion(point, Facing.RIGHT, now)
        val left = GridPoint(10, 9)
        assertTrue(motion.update(left, now).mirrored)
        val up = motion.update(GridPoint(9, 9), now.plusSeconds(1))
        assertEquals(Facing.UP, up.facing)
        assertTrue(up.mirrored)
        assertFalse(motion.update(GridPoint(9, 10), now.plusSeconds(2)).mirrored)
    }

    @Test
    fun `relocations snap and backward time resets interpolation`() {
        val motion = AnimalMotion(point, Facing.RIGHT, now)
        val stranded = motion.update(GridPoint(20, 30), now)
        assertEquals(20f, stranded.row)
        assertEquals(30f, stranded.col)
        assertFalse(stranded.walking)
        motion.update(GridPoint(20, 31), now.plusSeconds(1))
        val rewind = motion.update(point, now.minusSeconds(1))
        assertEquals(10f, rewind.row)
        assertEquals(10f, rewind.col)
        assertFalse(rewind.walking)
    }

    @Test
    fun `fish interpolate over one point eight seconds`() {
        val motion = AnimalMotion(point, Facing.LEFT, now)
        val target = GridPoint(9, 10)
        motion.update(target, now, 1.8)
        assertEquals(9.5f, motion.update(target, now.plusMillis(900), 1.8).row)
        assertFalse(motion.update(target, now.plusMillis(1800), 1.8).walking)
    }

    @Test
    fun `animation switches from walking back to idle and accepts a changed look`() {
        val cat = AnimalSprites("Cats", "grey_cat", AnimalSheet(32, 9), AnimalKind.CAT, 100f)
        val animation = AnimalAnimation(42)
        val walking = AnimalPose(0f, 0f, Facing.RIGHT, false, true, 0.49)
        assertEquals(SheetFrame(8, 3), animation.frame(cat, walking))
        val idle = walking.copy(walking = false, elapsed = 0.0)
        assertEquals(SheetFrame(1, 0), animation.frame(cat, idle))
        val hen = AnimalSprites("Chickens", "Chicken_White_48x48", AnimalSheet(48, 4), AnimalKind.CHICKEN, 52f)
        assertEquals(SheetFrame(0, 0), animation.frame(hen, idle))
    }
}
