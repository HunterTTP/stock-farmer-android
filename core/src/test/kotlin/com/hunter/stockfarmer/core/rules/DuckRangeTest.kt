package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.max

class DuckRangeTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val center = GridPoint(20, 20)

    private fun water(
        rows: IntRange,
        cols: IntRange,
        engine: GameEngine,
    ) {
        for (row in rows) for (col in cols) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
    }

    @Test
    fun `a duck swims anywhere the water joins up to even far from where it was born into another pond`() {
        val engine = GameEngine()
        water(19..21, 19..21, engine)
        water(19..21, 49..49, engine)
        water(20..20, 22..48, engine)
        engine.setTimeOfDay(720.0, now)
        val herdRandom = SeededRandom(1)
        engine.updateHerds(now, herdRandom)
        engine.updateHerds(now.advancedBy(600.0), herdRandom)
        val duck = engine.animals(center).first()
        val ref = AnimalRef.Livestock(center, duck.id)
        val generator = SeededRandom(3)
        var farthest = 0
        repeat(20_000) {
            val direction = engine.wanderDirection(ref, now, generator) ?: return@repeat
            val spot = engine.move(ref, direction) ?: return@repeat
            farthest = max(farthest, spot.col)
            assertNotNull(engine.state.water[spot])
        }
        assertTrue(farthest >= 49)
    }
}
