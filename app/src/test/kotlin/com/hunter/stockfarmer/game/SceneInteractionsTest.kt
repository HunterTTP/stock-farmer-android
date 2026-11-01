package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SceneInteractionsTest {
    private val origin = GridPoint(5, 5)
    private val now = Instant.ofEpochSecond(100_000)
    private val interactions = SceneInteractions()

    @Test
    fun `building door frames advance at 0 point 07s per frame up to 14 frames`() {
        interactions.interact(FarmSceneFeedback.Interaction(origin, building = true, now))
        assertEquals(0, interactions.door(origin, now.plusMillis(35)))
        assertEquals(1, interactions.door(origin, now.plusMillis(100)))
        assertEquals(7, interactions.door(origin, now.plusMillis(500)))
        assertEquals(13, interactions.door(origin, now.plusMillis(920)))
        // 14 * 0.07 = 0.98s expires
        assertNull(interactions.door(origin, now.plusMillis(990)))
    }

    @Test
    fun `well decoration advances with 16 frames and size 144 by 96`() {
        val well = PlacedDecoration("well")
        interactions.interact(FarmSceneFeedback.Interaction(origin, building = false, now))
        val motion = interactions.decoration(origin, well, Season.spring, now)
        assertNotNull(motion)
        assertEquals("well", motion!!.name)
        assertEquals(16, motion.count)
        assertEquals(0, motion.frame)
        assertEquals(144, motion.width)
        assertEquals(96, motion.height)

        // 16 * 0.06 = 0.96s expires
        assertNull(interactions.decoration(origin, well, Season.spring, now.plusMillis(960)))
    }

    @Test
    fun `chest decoration advances with 20 frames and size 96 by 144`() {
        val chest = PlacedDecoration("decorativeChest", savedSize = 2)
        interactions.interact(FarmSceneFeedback.Interaction(origin, building = false, now))
        val motion = interactions.decoration(origin, chest, Season.fall, now)
        assertNotNull(motion)
        assertEquals("chest2", motion!!.name)
        assertEquals(20, motion.count)
        assertEquals(0, motion.frame)
        assertEquals(96, motion.width)
        assertEquals(144, motion.height)
    }

    @Test
    fun `winter decoration uses rotation wobble instead of motion frames`() {
        val well = PlacedDecoration("well")
        interactions.interact(FarmSceneFeedback.Interaction(origin, building = false, now))
        assertNull(interactions.decoration(origin, well, Season.winter, now))

        val rot1 = interactions.winterRotation(origin, well, now.plusMillis(50))
        assertTrue(rot1 > 0f)

        val rot2 = interactions.winterRotation(origin, well, now.plusMillis(180))
        assertTrue(rot2 < 0f)

        val rot3 = interactions.winterRotation(origin, well, now.plusMillis(350))
        assertEquals(0f, rot3)
    }
}
