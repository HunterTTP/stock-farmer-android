package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CareerRecordTest {
    private fun roundTrip(state: GameState) = GameStateJson.decode(GameStateJson.encode(state))

    @Test
    fun `a career survives save and load`() {
        val state = GameState()
        state.career.plantedCropIDs = mutableSetOf("wheat")
        state.career.noteCatch("carp", 4.5)
        state.career.addGrossIncome(12.34)
        state.career.sample(600.0, 40, 0)
        val loaded = roundTrip(state)
        assertTrue(sameState(state, loaded))
        assertEquals(1234, loaded.career.grossIncomeCents)
        assertEquals(setOf("wheat"), loaded.career.plantedCropIDs)
        assertEquals(4.5, loaded.career.heaviestCatches["carp"])
    }

    @Test
    fun `a heavier catch replaces a lighter one but not the other way around`() {
        val career = CareerRecord()
        career.noteCatch("carp", 3.0)
        career.noteCatch("carp", 5.0)
        career.noteCatch("carp", 4.0)
        assertEquals(5.0, career.heaviestCatches["carp"])
    }

    @Test
    fun `gross income counts only gains`() {
        val career = CareerRecord()
        career.addGrossIncome(10.0)
        career.addGrossIncome(-5.0)
        career.addGrossIncome(0.0)
        assertEquals(1000, career.grossIncomeCents)
    }

    @Test
    fun `the XP chart gains a point only after five minutes and some XP`() {
        val career = CareerRecord()
        career.sample(0.0, 0, 0)
        career.sample(120.0, 10, 0)
        assertEquals(1, career.xpHistory.size)
        career.sample(300.0, 0, 0)
        assertEquals(1, career.xpHistory.size)
        career.sample(300.0, 20, 0)
        assertEquals(XPSample(300.0, 20), career.xpHistory.last())
    }

    @Test
    fun `a long career thins its chart but keeps the first and last points`() {
        val career = CareerRecord()
        for (step in 0..CareerRecord.MAX_SAMPLES + 40) {
            career.sample(step * CareerRecord.SAMPLE_INTERVAL, step + 1, 0)
        }
        assertTrue(career.xpHistory.size <= CareerRecord.MAX_SAMPLES)
        assertEquals(1, career.xpHistory.first().xp)
        assertEquals(CareerRecord.MAX_SAMPLES + 41, career.xpHistory.last().xp)
    }

    @Test
    fun `a corrupt career is refused`() {
        val state = GameState()
        state.career.grossIncomeCents = -1
        val json = GameStateJson.encode(state)
        assertThrows(Exception::class.java) { GameStateJson.decode(json) }
    }
}
