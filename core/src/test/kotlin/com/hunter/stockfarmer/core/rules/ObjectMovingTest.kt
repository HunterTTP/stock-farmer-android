package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.TreeSize
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ObjectMovingTest {
    private val now = Instant.ofEpochSecond(60_000_000)
    private val spot = GridPoint(20, 20)

    private fun engine(
        decoration: String? = null,
        at: GridPoint = spot,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = 10_000.0, xp = lateGameXP))
        if (decoration != null) {
            engine.state.putDecoration(at, PlacedDecoration(Decoration.named(decoration)!!.id, placedAt = now))
        }
        return engine
    }

    @Test
    fun `a decoration is picked up where it stands and moved by arrows or dragging for nothing`() {
        val engine = engine("scarecrow")
        val money = engine.state.money
        engine.beginMovingDecoration(spot)
        assertTrue(engine.isPlacing && engine.objectPlacement?.origin == spot)
        engine.nudgePlacement(1, 2)
        assertEquals(GridPoint(21, 22), engine.objectPlacement?.origin)
        engine.movePlacement(GridPoint(30, 30))
        assertNull(engine.placementRejection(now))
        assertEquals(listOf<GameEvent>(GameEvent.Moved(GridPoint(30, 30))), engine.confirmPlacement(now))
        assertTrue(
            !engine.isPlacing && engine.state.decorations.keys
                .toList() == listOf(GridPoint(30, 30)),
        )
        assertEquals(money, engine.state.money)
    }

    @Test
    fun `a moved sprinkler sprays and waters again and may stand on water but a scarecrow may not`() {
        val sprinkler = engine("sprinkler")
        sprinkler.state.water[GridPoint(30, 30)] = WaterStyle.deep
        sprinkler.beginMovingDecoration(spot)
        sprinkler.movePlacement(GridPoint(30, 30))
        assertNull(sprinkler.placementRejection(now))
        val later = now.advancedBy(500.0)
        sprinkler.confirmPlacement(later)
        val moved = sprinkler.state.decorations[GridPoint(30, 30)]
        assertEquals(later, moved?.placedAt)
        assertEquals(later.advancedBy(Decoration.SPRAY_DURATION), moved?.wateringStartsAt)

        val scarecrow = engine("scarecrow")
        scarecrow.state.water[GridPoint(30, 30)] = WaterStyle.deep
        scarecrow.beginMovingDecoration(spot)
        scarecrow.movePlacement(GridPoint(30, 30))
        assertEquals(Rejection.NotEnoughSpace, scarecrow.placementRejection(now))
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)), scarecrow.confirmPlacement(now))
        assertTrue(
            scarecrow.state.decorations.keys
                .toList() == listOf(spot) && scarecrow.isPlacing,
        )
    }

    @Test
    fun `a bigger decoration stays inside the map and may overlap its own spot`() {
        val engine = engine("well", spot)
        engine.beginMovingDecoration(GridPoint(21, 21))
        assertEquals(spot, engine.objectPlacement?.origin)
        engine.nudgePlacement(1, 0)
        assertNull(engine.placementRejection(now))
        engine.movePlacement(GridPoint(100, 100))
        assertEquals(
            GridPoint(GameState.GRID_SIZE - 2, GameState.GRID_SIZE - 2),
            engine.objectPlacement?.origin,
        )
    }

    @Test
    fun `cancelling leaves the decoration where it was`() {
        val engine = engine("scarecrow")
        engine.beginMovingDecoration(spot)
        engine.nudgePlacement(3, 0)
        engine.cancelPlacement()
        assertTrue(
            !engine.isPlacing && engine.state.decorations.keys
                .toList() == listOf(spot),
        )
    }

    @Test
    fun `naturally generated trees cannot be moved or destroyed by holding`() {
        val engine = engine()
        engine.state.putTree(spot, PlacedTree(Tree.starter, TreeColor.green, now))
        val before = engine.state.copy()
        engine.beginMovingTree(spot)
        assertFalse(engine.isPlacing)
        assertTrue(engine.destroyPlacedObject(now).isEmpty())
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `personally planted trees remain movable after growth and saving`() {
        var engine = engine()
        engine.select(TreeSpecies.pine)
        assertFalse(engine.useTool(spot, now).containsRejection)
        engine.growTrees(now.advancedBy(Tree.growthInterval * 3))
        engine = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
        val tree = engine.state.tree(spot)!!
        engine.beginMovingTree(spot)
        assertEquals(tree.first, engine.objectPlacement?.from)
    }

    @Test
    fun `tree origin flags round trip and natural growth keeps its origin`() {
        for (natural in listOf(true, false)) {
            val engine = engine()
            engine.state.putTree(spot, PlacedTree(Tree.starter, TreeColor.green, now, natural))
            engine.growTrees(now.advancedBy(Tree.growthInterval * 3))
            val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
            assertEquals(
                natural,
                loaded.trees.values
                    .first()
                    .isNaturallyGenerated,
            )
            assertEquals(
                TreeSize.huge,
                loaded.trees.values
                    .first()
                    .tree.size,
            )
            val reloaded = GameEngine(loaded)
            reloaded.beginMovingTree(spot)
            assertEquals(!natural, reloaded.isPlacing)
        }
    }

    @Test
    fun `a tree is moved with its growth`() {
        val engine = engine()
        val species = TreeSpecies.pine
        engine.state.putTree(spot, PlacedTree(species.smallest, species.colors[0], now, false))
        engine.beginMovingTree(spot)
        engine.nudgePlacement(0, 5)
        val target = engine.objectPlacement!!.origin
        assertEquals(listOf<GameEvent>(GameEvent.Moved(target)), engine.confirmPlacement(now))
        assertTrue(engine.state.trees[spot] == null && engine.state.trees[target]?.grewAt == now)
    }

    @Test
    fun `destroying while moving gives eighty percent for a decoration and nothing for a tree`() {
        val engine = engine("scarecrow")
        val money = engine.state.money
        engine.beginMovingDecoration(spot)
        assertEquals(listOf<GameEvent>(GameEvent.Undecorated(spot, 120.0)), engine.destroyPlacedObject(now))
        assertTrue(!engine.isPlacing && engine.state.decorations.isEmpty() && engine.state.money == money + 120)

        val species = TreeSpecies.pine
        engine.state.putTree(spot, PlacedTree(species.smallest, species.colors[0], now, false))
        engine.beginMovingTree(spot)
        assertEquals(listOf<GameEvent>(GameEvent.Undecorated(spot, 0.0)), engine.destroyPlacedObject(now))
        assertTrue(!engine.isPlacing && engine.state.trees.isEmpty() && engine.state.money == money + 120)
    }

    @Test
    fun `a cosmetic trunk can be destroyed while moving without losing stored items`() {
        val engine = engine("trunk")
        engine.state.storage = mutableMapOf("wheat" to BACKPACK_CAPACITY + 5)
        engine.beginMovingDecoration(spot)
        val events = engine.destroyPlacedObject(now)
        assertTrue(!events.containsRejection && !engine.isPlacing && engine.state.decorations.isEmpty())
        assertEquals(mapOf("wheat" to BACKPACK_CAPACITY + 5), engine.state.storage)
    }
}
