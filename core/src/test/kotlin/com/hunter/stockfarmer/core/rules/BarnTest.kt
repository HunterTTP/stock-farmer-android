package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BarnTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val barns = listOf("barn1" to "sheep", "goatBarn" to "goat", "pigBarn" to "pig")

    private fun engine(building: String): GameEngine {
        val engine =
            GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = building
        return engine
    }

    @Test
    fun `the sheep goat and pig barns are three buildings unlocked in order`() {
        val buildings = barns.map { Building.named(it.first)!! }
        assertEquals(listOf("Sheep Barn", "Goat Barn", "Pig Barn"), buildings.map { it.name })
        assertEquals(buildings.map { it.unlockLevel }.sorted(), buildings.map { it.unlockLevel })
        assertEquals(3, buildings.map { it.unlockLevel }.toSet().size)
        assertTrue(buildings.all { it.placementLimit == 1 && it.width == 4 && it.height == 5 })
        assertEquals(3, buildings.map { it.assetName }.toSet().size)
    }

    @Test
    fun `each barn keeps only its own animal`() {
        for ((index, barn) in barns.withIndex()) {
            val engine = engine(barn.first)
            val origin = GridPoint(10, 10 + 8 * index)
            assertTrue(engine.build(origin, now).any { it is GameEvent.Built })
            engine.updateHerds(now.advancedBy(60 * 60 * 10.0), SeededRandom(5))
            val kinds =
                engine.state.herds.values
                    .flatMap { it.animals }
                    .map { it.species.id }
                    .toSet()
            assertEquals(setOf(barn.second), kinds)
        }
    }

    @Test
    fun `a second barn of the same kind is refused`() {
        val engine = engine("goatBarn")
        engine.build(GridPoint(10, 10), now)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Goat Barn", 1))),
            engine.build(GridPoint(10, 30), now),
        )
    }
}
