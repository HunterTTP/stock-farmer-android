package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.isSoilWateringChanging
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** iOS `waterWithSprinklers`: a sprinkler waters its land tile by tile through its first spray. */
class SoilWateringTest {
    private val now = Instant.ofEpochSecond(70_000_000)
    private val origin = GridPoint(20, 20)
    private val reach = (18..22).flatMap { row -> (18..22).map { GridPoint(row, it) } }.filter { it != origin }
    private val engine =
        GameEngine(
            GameState().apply {
                farmland = reach.toMutableSet()
                putDecoration(
                    origin,
                    PlacedDecoration.of(
                        Decoration.named(Decoration.SPRINKLER_ID)!!,
                        wateringStartsAt = now.advancedBy(Decoration.SPRAY_DURATION),
                        placedAt = now,
                    ),
                )
            },
        )
    private val session = GameSession(engine)

    private fun watered() =
        engine.state.decorations
            .getValue(origin)
            .wateredTiles

    @Test
    fun `a new sprinkler waters more of its land as its first spray goes on`() {
        assertTrue(session.waterWithSprinklers(now.advancedBy(5.0)))
        val partway = watered().size
        assertTrue(partway > 0)
        assertTrue(session.waterWithSprinklers(now.advancedBy(9.0)))
        assertTrue(watered().size > partway)
        assertTrue(engine.isSoilWateringChanging)
    }

    @Test
    fun `at the end of the spray the land stays watered and nothing is changing`() {
        assertTrue(session.waterWithSprinklers(now.advancedBy(Decoration.SPRAY_DURATION)))
        assertFalse(engine.isSoilWateringChanging)
        assertFalse(session.waterWithSprinklers(now.advancedBy(Decoration.SPRAY_DURATION + 1)))
    }

    @Test
    fun `a farm with no soil changing is left alone`() {
        val calm = GameSession(GameEngine(GameState()))
        assertFalse(calm.waterWithSprinklers(now))
    }

    @Test
    fun `a sprinkler destroyed lets its old land dry out`() {
        session.waterWithSprinklers(now.advancedBy(Decoration.SPRAY_DURATION))
        engine.state.removeDecoration(origin)
        engine.state.lingeringWater[reach.first()] = now.advancedBy(20.0)
        assertTrue(engine.isSoilWateringChanging)
        assertTrue(session.waterWithSprinklers(now.advancedBy(21.0)))
        assertFalse(engine.isSoilWateringChanging)
    }

    @Test
    fun `a quest finished while the land changes is turned in`() {
        engine.state.putBuilding(GridPoint(5, 5), "marketStand", null)
        engine.state.marketQuests["marketStand"] =
            listOf(MarketQuest(UUID(0, 9), listOf(QuestObjective(QuestGoal.PlantTrees, 1, progress = 1)), 50))
        session.waterWithSprinklers(now.advancedBy(6.0))
        assertEquals(1, engine.state.completedMarketQuests)
    }
}
