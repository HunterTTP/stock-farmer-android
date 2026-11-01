package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.CareerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeasonTest {
    @Test
    fun `the farm starts in spring and each season lasts thirty days`() {
        assertEquals(30, Season.DAYS_PER_SEASON)
        assertTrue(listOf(1, 15, 30).all { Season.of(it) == Season.spring })
        assertTrue(listOf(31, 45, 60).all { Season.of(it) == Season.fall })
        assertTrue(listOf(61, 75, 90).all { Season.of(it) == Season.winter })
    }

    @Test
    fun `the seasons repeat after winter`() {
        assertEquals(Season.spring, Season.of(91))
        assertEquals(Season.fall, Season.of(121))
        assertEquals(Season.spring, Season.of(181))
    }

    @Test
    fun `a day before the first counts as spring`() {
        assertEquals(Season.spring, Season.of(0))
        assertEquals(Season.spring, Season.of(-5))
    }

    @Test fun `seasons and sightings are remembered`() {
        val engine = GameEngine()
        assertTrue(engine.hasSeenEnvironment(CareerRecord.environmentID(Season.spring)))
        assertFalse(engine.hasSeenEnvironment(CareerRecord.environmentID(Season.winter)))
        engine.setSeason(Season.winter)
        engine.setSeason(Season.fall)
        assertTrue(engine.hasSeenEnvironment(CareerRecord.environmentID(Season.winter)))
        engine.noteSeenEnvironment(CareerRecord.environmentID(Butterflies.Wings.Shifting))
        assertTrue(engine.hasSeenEnvironment("butterfly:shifting"))
        assertFalse(engine.hasSeenEnvironment("butterfly:rare"))
    }
}
