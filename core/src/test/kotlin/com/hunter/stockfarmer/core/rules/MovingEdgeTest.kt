package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MovingEdgeTest {
    private val now = Instant.ofEpochSecond(50_000_000)
    private val backEdgeNow = Instant.ofEpochSecond(80_000_000)

    private fun carriedTreeEngine(): GameEngine {
        val origin = GridPoint(10, 10)
        val state = GameState()
        state.putTree(
            origin,
            PlacedTree(Tree.starter, TreeColor.green, now.advancedBy(-Tree.growthInterval + 1), false),
        )
        val engine = GameEngine(state)
        engine.beginMovingTree(origin)
        engine.movePlacement(GridPoint(35, 35))
        return engine
    }

    @Test
    fun `growth while carried never duplicates a tree`() {
        for (action in listOf("confirm", "cancel", "destroy")) {
            val engine = carriedTreeEngine()
            val storage = engine.state.storage.toMap()
            val money = engine.state.money
            val later = now.advancedBy(2.0)
            assertTrue(!engine.growTrees(later))
            when (action) {
                "confirm" -> {
                    assertEquals(listOf<GameEvent>(GameEvent.Moved(GridPoint(35, 35))), engine.confirmPlacement(later))
                }

                "cancel" -> {
                    engine.cancelPlacement()
                }

                else -> {
                    assertEquals(
                        listOf<GameEvent>(GameEvent.Undecorated(GridPoint(10, 10), 0.0)),
                        engine.destroyPlacedObject(later),
                    )
                }
            }
            engine.growTrees(later)
            assertEquals(if (action == "destroy") 0 else 1, engine.state.trees.size, action)
            if (action != "destroy") {
                assertEquals(
                    Tree.starter.next,
                    engine.state.trees.values
                        .first()
                        .tree,
                )
            }
            assertEquals(storage, engine.state.storage)
            assertEquals(money, engine.state.money)
        }
    }

    @Test
    fun `moving a fruit tree cannot overwrite an overlapping neighbor`() {
        val apple = Crop.named("apple")!!
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP))
        engine.select(apple)
        for (row in listOf(10, 9)) {
            engine.beginPlacingFruitTree(GridPoint(row, 10))
            assertEquals(
                listOf<GameEvent>(GameEvent.Planted(GridPoint(row - 1, 10), apple.seedPrice)),
                engine.confirmPlacement(now),
            )
        }
        val before = engine.state.copy()
        engine.beginMovingFruitTree(GridPoint(10, 10))
        engine.movePlacement(GridPoint(8, 10))
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedGrass)), engine.confirmPlacement(now))
        assertTrue(sameState(before, engine.state))
        engine.cancelPlacement()
        assertEquals(2, engine.placedOnMap(ShopItem.Seed(apple.id)))
    }

    @Test
    fun `a tall building can be moved with only its bottom row on the map`() {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP))
        val silo = Building.named("silo")!!
        engine.state.putBuilding(GridPoint(20, 20), silo.id)
        engine.beginMoving(engine.building(GridPoint(24, 20))!!)
        engine.movePlacement(GridPoint(-50, 20))
        val top = GridPoint(1 - silo.height, 20)
        assertEquals(top, engine.buildingPlacement?.origin)
        assertNull(engine.placementRejection(backEdgeNow))
        assertEquals(listOf<GameEvent>(GameEvent.Moved(top)), engine.confirmPlacement(backEdgeNow))
        assertTrue(engine.state.buildings[top] == silo.id && engine.state.buildings[GridPoint(20, 20)] == null)
        val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.buildings, restored.buildings)
    }

    @Test
    fun `the bottom row still has to stay on the map and be free`() {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP))
        val silo = Building.named("silo")!!
        engine.state.putBuilding(GridPoint(20, 20), silo.id)
        engine.beginMoving(engine.building(GridPoint(24, 20))!!)
        engine.movePlacement(GridPoint(500, 500))
        assertEquals(
            GridPoint(GameState.GRID_SIZE - silo.height, GameState.GRID_SIZE - silo.width),
            engine.buildingPlacement?.origin,
        )
        engine.state.water[GridPoint(0, 20)] = WaterStyle.deep
        engine.movePlacement(GridPoint(-50, 20))
        assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(backEdgeNow))
    }

    @Test
    fun `the tractor stays wholly inside the map`() {
        val tractor = Building.named("tractor1")!!
        assertEquals(0, tractor.clampedOrigin(GridPoint(-9, 3)).row)
    }

    @Test
    fun `a decoration can be carried to the back edge too`() {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP))
        val well = Decoration.named("well")!!
        engine.state.putDecoration(GridPoint(20, 20), PlacedDecoration(well.id))
        engine.beginMovingDecoration(GridPoint(21, 20))
        engine.movePlacement(GridPoint(-5, 20))
        val top = GridPoint(1 - well.height, 20)
        assertEquals(top, engine.objectPlacement?.origin)
        assertNull(engine.placementRejection(backEdgeNow))
        assertEquals(listOf<GameEvent>(GameEvent.Moved(top)), engine.confirmPlacement(backEdgeNow))
    }
}
