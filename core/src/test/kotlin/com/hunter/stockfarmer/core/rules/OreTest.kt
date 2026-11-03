package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.OreKind
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.Rock
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class OreTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val point = GridPoint(15, 15)
    private val products =
        mapOf(
            OreKind.copper to "copperBar",
            OreKind.silver to "silverBar",
            OreKind.gold to "goldBar",
            OreKind.ruby to "polishedRuby",
            OreKind.sapphire to "polishedSapphire",
        )

    @Test
    fun `natural veins mine with the pickaxe and keep their ore through saving`() {
        for (size in RockSize.oreSizes) {
            var engine = GameEngine(stateWith(logs = 0))
            engine.state.rocks[point] = size
            engine = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
            engine.select(HarvestTool.pickaxe)
            repeat(size.hitsToBreak) {
                engine.useTool(point, now)
                engine.liftTool()
            }
            val ore = size.ore!!
            assertEquals(mapOf(StorageItem.Ore(ore).id to 6), engine.state.storage)
            assertTrue(engine.state.rocks.isEmpty())
            val recipe = Recipe.named(products.getValue(ore))!!
            assertTrue(recipe.input == StorageItem.Ore(ore) && recipe.inputCount == 6)
            assertEquals(72.0, recipe.output.sellValue)
        }
    }

    @Test
    fun `ore veins are not for sale`() {
        assertNull(Decoration.named("oreVein"))
        assertEquals(listOf(RockSize.small, RockSize.medium, RockSize.big), Decoration.named("rock")?.rockSizes)
    }

    @Test
    fun `new worlds have few veins on bare ground without replacing building stones`() {
        val random = SeededRandom(314)
        val kinds = mutableSetOf<OreKind>()
        repeat(30) {
            val world = newGame(random, now)
            val veins = world.rocks.filter { it.value.ore != null }
            assertTrue(veins.size in 3..5)
            assertTrue(world.hasOreVeins)
            assertTrue(world.rocks.values.count { it.ore == null } in Rock.onANewMap)
            for ((tile, size) in veins) {
                assertTrue(GameState.isClearOfStart(tile))
                assertTrue(world.water[tile] == null && world.tree(tile) == null)
                assertTrue(world.building(tile) == null && tile !in world.farmland)
                size.ore?.let { kinds += it }
            }
        }
        assertEquals(OreKind.entries.toSet(), kinds)
    }

    @Test
    fun `old worlds receive veins only once and keep purchased veins`() {
        val state = stateWith(logs = 0)
        state.rocks[GridPoint(10, 10)] = RockSize.sapphire
        var engine = GameEngine(state)
        assertFalse(engine.state.hasOreVeins)
        val random = SeededRandom(42)
        engine.scatterOreVeinsIfNeeded(random)
        assertEquals(RockSize.sapphire, engine.state.rocks[GridPoint(10, 10)])
        assertTrue(
            engine.state.rocks.values
                .count { it.ore != null } in 3..5,
        )
        val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(sameState(restored, engine.state) && restored.hasOreVeins)
        engine = GameEngine(restored)
        val before = engine.state.rocks.toMap()
        engine.scatterOreVeinsIfNeeded(random)
        assertEquals(before, engine.state.rocks)
    }
}
