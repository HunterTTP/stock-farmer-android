package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

class FishingContestTest {
    private val bounds = FishingContest.Bounds(40.0, 120.0, 350.0, 680.0)
    private val step = 1.0 / 60

    private fun contest(
        rarity: Rarity,
        seed: Long = 1,
    ) = FishingContest(rarity, bounds, using = SeededRandom(seed))

    @Test
    fun `rarer fish swim quicker and take longer to land`() {
        val contests = Rarity.entries.map { contest(it) }
        for ((easier, harder) in contests.zipWithNext()) {
            assertTrue(easier.speed < harder.speed)
            assertTrue(easier.fillSeconds < harder.fillSeconds)
        }
    }

    @Test
    fun `rarer fish change their minds more often`() {
        val turns =
            Rarity.entries.map { rarity ->
                val contest = contest(rarity, 3)
                val generator = SeededRandom(4)
                repeat(60 * 60) { contest.advance(step, null, generator) }
                contest.turns
            }
        assertTrue(turns.zipWithNext().all { (a, b) -> a < b }, turns.toString())
    }

    @Test
    fun `the fuller the bar the faster and more restless the fish`() {
        val generator = SeededRandom(11)
        val empty = contest(Rarity.uncommon)
        val nearlyFull = contest(Rarity.uncommon)
        assertTrue(empty.fight == 1.0 && empty.currentSpeed == empty.speed)
        nearlyFull.fill(0.95)
        assertTrue(nearlyFull.fight > 1.9 && nearlyFull.currentSpeed > 1.9 * nearlyFull.speed)
        repeat(60 * 60) {
            empty.advance(step, null, generator)
            nearlyFull.fill(0.95)
            nearlyFull.advance(step, null, generator)
        }
        assertTrue(nearlyFull.turns > (empty.turns * 1.6).toInt())
    }

    @Test
    fun `the meter drains slowly with the rod away or released`() {
        for (rod in listOf(FishingContest.Position(-1_000.0, -1_000.0), null)) {
            val generator = SeededRandom(5)
            val contest = contest(Rarity.uncommon)
            contest.fill(0.5)
            assertFalse(contest.advance(1.0, rod, generator))
            assertTrue(abs(contest.progress - 0.46) < 0.000_001)
        }
    }

    @Test
    fun `an empty meter never fails and can be filled again`() {
        val generator = SeededRandom(6)
        val contest = contest(Rarity.uncommon)
        contest.fill(0.1)
        assertFalse(contest.advance(60.0, null, generator))
        assertEquals(0.0, contest.progress)
        for (i in 0 until 600) {
            if (contest.advance(step, contest.fish, generator)) break
        }
        assertEquals(1.0, contest.progress)
        assertTrue(contest.advance(60.0, null, generator))
        assertEquals(1.0, contest.progress)
    }

    @Test
    fun `holding the rod on the fish fills the bar in the time its kind takes`() {
        for (rarity in Rarity.entries) {
            val generator = SeededRandom(6)
            val contest = contest(rarity)
            var elapsed = 0.0
            var landed = false
            while (!landed && elapsed < 60) {
                landed = contest.advance(step, contest.fish, generator)
                elapsed += step
            }
            assertTrue(landed)
            assertTrue(abs(elapsed - contest.fillSeconds) < 0.1, "rarity $rarity: $elapsed")
        }
    }

    @Test
    fun `a rod outside the reach fills nothing and a fish cannot be lost`() {
        val generator = SeededRandom(7)
        val contest = contest(Rarity.epic)
        repeat(60 * 120) {
            val away = FishingContest.Position(contest.fish.x + FishingContest.REACH + 5, contest.fish.y)
            assertFalse(contest.advance(step, away, generator))
        }
        assertEquals(0.0, contest.progress)
    }

    @Test
    fun `the fish stays within the bounds whatever its kind`() {
        for (rarity in Rarity.entries) {
            val generator = SeededRandom(8)
            val contest = contest(rarity, 2)
            repeat(60 * 120) {
                contest.advance(step, null, generator)
                assertTrue(contest.fish.x in bounds.minX..bounds.maxX && contest.fish.y in bounds.minY..bounds.maxY)
                assertTrue(contest.heading in -PI..PI)
            }
        }
    }

    @Test
    fun `the same seeds play the same contest`() {
        fun run(): List<Any> {
            val generator = SeededRandom(9)
            val contest = contest(Rarity.epic)
            repeat(600) { contest.advance(step, contest.fish, generator) }
            return listOf(contest.fish, contest.heading, contest.progress, contest.turns)
        }
        assertEquals(run(), run())
    }
}
