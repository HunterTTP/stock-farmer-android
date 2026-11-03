package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random

class SprinklerSpraysTest {
    private val sprinkler = Decoration.named(Decoration.SPRINKLER_ID)!!
    private val origin = GridPoint(10, 10)

    /** A minute boundary of the shared schedule, a little in. */
    private val minute = Instant.ofEpochSecond(60L * 1_000_000 + 1)
    private val sprays = SprinklerSprays(Random(5))

    private fun at(seconds: Double): Instant = minute.plusMillis((seconds * 1000).toLong())

    private fun standing(placedAt: Instant? = null) =
        mapOf(origin to PlacedDecoration.of(sprinkler, placedAt = placedAt))

    private fun SprinklerSprays.only() = sprinklers.single()

    private fun Sprinkler.sprayingAt(seconds: Double) = sprays.any { !it.isOver(SprinklerSprays.seconds(at(seconds))) }

    @Test
    fun `a sprinkler just set down sprays at once`() {
        sprays.sync(standing(placedAt = minute), minute)
        sprays.tick(minute, isRaining = false, runsConstantly = false)
        val spray = sprays.only().sprays.first()
        assertEquals((5 * 5 - 1) * SprinklerSprays.DROPS_PER_TILE, spray.count)
        assertEquals(Decoration.SPRAY_DURATION, spray.duration)
        assertTrue(sprays.only().sprayingAt(0.0))
    }

    @Test
    fun `a sprinkler already standing waits for the next shared spray`() {
        sprays.sync(standing(placedAt = minute.minusSeconds(3600)), minute)
        sprays.tick(minute, isRaining = false, runsConstantly = false)
        assertFalse(sprays.only().sprayingAt(0.0))
        val nextMinute = minute.plusSeconds(60)
        sprays.tick(nextMinute, isRaining = false, runsConstantly = false)
        assertTrue(sprays.only().sprays.any { !it.isOver(SprinklerSprays.seconds(nextMinute)) })
    }

    @Test
    fun `a spray ends after twelve seconds and does not repeat until the next shared one`() {
        sprays.sync(standing(placedAt = minute), minute)
        sprays.tick(minute, false, false)
        sprays.tick(at(30.0), false, false)
        assertFalse(sprays.only().sprayingAt(30.0))
        assertEquals(0, sprays.only().sprays.count { it.startedAt > SprinklerSprays.seconds(at(1.0)) })
    }

    @Test
    fun `rain skips the spray and it is not made up when the rain stops`() {
        sprays.sync(standing(placedAt = minute.minusSeconds(3600)), minute.minusSeconds(120))
        val nextMinute = minute.plusSeconds(60)
        sprays.tick(nextMinute, isRaining = true, runsConstantly = false)
        assertFalse(sprays.only().sprays.any { !it.isOver(SprinklerSprays.seconds(nextMinute)) })
        sprays.tick(nextMinute.plusSeconds(5), isRaining = false, runsConstantly = false)
        assertFalse(sprays.only().sprays.any { !it.isOver(SprinklerSprays.seconds(nextMinute.plusSeconds(5))) })
    }

    @Test
    fun `a fountain sprays all the time, rain or not, before the last mist has gone`() {
        sprays.sync(standing(placedAt = minute.minusSeconds(3600)), minute)
        sprays.tick(minute, isRaining = true, runsConstantly = true)
        val first = sprays.only().sprays[0]
        assertEquals(SprinklerSprays.FOUNTAIN_DURATION, first.duration)
        assertEquals(24 * SprinklerSprays.FOUNTAIN_DROPS_PER_TILE, first.count)
        sprays.tick(at(2.0), isRaining = true, runsConstantly = true)
        assertEquals(0.0, sprays.only().sprays[1].startedAt)
        sprays.tick(at(2.5), isRaining = true, runsConstantly = true)
        assertEquals(SprinklerSprays.seconds(at(2.5)), sprays.only().sprays[1].startedAt)
        assertTrue(sprays.only().sprayingAt(3.9))
    }

    @Test
    fun `drops fly from a tip of the sprinkler into their tile within the spray`() {
        sprays.sync(standing(placedAt = minute), minute)
        sprays.tick(minute, false, false)
        val spray = sprays.only().sprays.first()
        val tile = 100f
        for (drop in 0 until spray.count) {
            assertTrue(spray.delay[drop] >= 0f)
            assertTrue(spray.delay[drop] + spray.flight[drop] <= Decoration.SPRAY_DURATION.toFloat() + 0.001f)
            assertTrue(spray.flight[drop] in 0.9f..1.1001f)
            val reachX = spray.startX[drop] + spray.endX[drop]
            assertTrue(kotlin.math.abs(reachX) <= 2.6f * tile)
            assertEquals(spray.startX[drop], spray.x(drop, 0f), 0.0001f)
            assertEquals(reachX, spray.x(drop, 1f), 0.0001f)
            assertEquals(spray.startY[drop] + spray.endY[drop], spray.y(drop, 1f), 0.001f)
        }
        assertEquals(0f, spray.alpha(0, 0f))
        assertEquals(1f, spray.alpha(0, 0.5f))
        assertEquals(0f, spray.alpha(0, spray.flight[0]), 0.0001f)
    }

    @Test
    fun `the mist fades in, holds at its strength and fades out`() {
        sprays.sync(standing(placedAt = minute), minute)
        sprays.tick(minute, false, false)
        val spray = sprays.only().sprays.first()
        val start = spray.startedAt
        assertEquals(0f, spray.mistAlpha(start))
        assertEquals(0.15f, spray.mistAlpha(start + 0.75), 0.0001f)
        assertEquals(0.3f, spray.mistAlpha(start + 6), 0.0001f)
        assertEquals(0f, spray.mistAlpha(start + 12), 0.0001f)
    }

    @Test
    fun `a sprinkler taken away stops spraying and one set down again sprays at once`() {
        sprays.sync(standing(placedAt = minute.minusSeconds(3600)), minute)
        val before = sprays.only()
        sprays.sync(emptyMap(), minute)
        assertTrue(sprays.sprinklers.isEmpty())
        val moved = minute.plusSeconds(10)
        sprays.sync(standing(placedAt = moved), moved)
        assertNotSame(before, sprays.only())
        sprays.tick(moved, false, false)
        assertTrue(sprays.only().sprayingAt(10.0))
    }

    @Test
    fun `a sprinkler picked up and put down in place keeps its picture and sprays at the next tick`() {
        sprays.sync(standing(placedAt = minute.minusSeconds(3600)), minute)
        val before = sprays.only()
        sprays.sync(standing(placedAt = minute.minusSeconds(1)), minute)
        assertSame(before, sprays.only())
        sprays.tick(minute, false, false)
        assertTrue(sprays.only().sprayingAt(0.0))
    }

    @Test
    fun `unchanged decorations are not walked again`() {
        val decorations = standing(placedAt = minute.minusSeconds(3600))
        sprays.sync(decorations, minute)
        val list = sprays.sprinklers.toList()
        sprays.sync(decorations, minute)
        assertEquals(list, sprays.sprinklers.toList())
    }

    @Test
    fun `other decorations are ignored and a big sprinkler throws for its wider reach`() {
        val big = Decoration.named(Decoration.BIG_SPRINKLER_ID)!!
        val well = Decoration.named("well")!!
        sprays.sync(
            mapOf(
                origin to PlacedDecoration.of(big, placedAt = minute),
                GridPoint(3, 3) to PlacedDecoration.of(well),
            ),
            minute,
        )
        assertEquals(1, sprays.sprinklers.size)
        sprays.tick(minute, false, false)
        assertEquals(
            (7 * 7 - 1) * SprinklerSprays.DROPS_PER_TILE,
            sprays
                .only()
                .sprays
                .first()
                .count,
        )
    }
}
