package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LandscapeTest {
    private val now = Instant.ofEpochSecond(3_000_000)
    private val start = GridPoint(24, 24)
    private val outside = GridPoint(10, 10)
    private val spot = GridPoint(30, 30)

    private fun engine(tool: LandscapeTool): GameEngine {
        val state = stateWith(money = 1_000.0, mode = PlayMode.landscape)
        state.selectedLandscape = tool
        return GameEngine(state)
    }

    private fun placed(point: GridPoint) = listOf<GameEvent>(GameEvent.Placed(point))

    private fun rejected(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))

    @Test
    fun `farmland is free and placed up to the level limit`() {
        val engine = engine(LandscapeTool.farmland)
        assertEquals(4, engine.state.farmlandLimit)
        assertEquals(rejected(Rejection.FarmlandLimit(4, 4)), engine.tap(outside, now))
        engine.state.xp = LevelRules.xpToReach(2)
        assertEquals(8, engine.state.farmlandLimit)
        assertEquals(placed(outside), engine.tap(outside, now))
        assertTrue(outside in engine.state.farmland)
        assertEquals(1_000.0, engine.state.money)
    }

    @Test
    fun `erasing farmland frees room under the limit`() {
        val engine = engine(LandscapeTool.grass)
        engine.tap(start, now)
        assertFalse(start in engine.state.farmland)
        engine.select(LandscapeTool.farmland)
        assertEquals(placed(outside), engine.tap(outside, now))
    }

    @Test
    fun `grass cannot clear tiles under crops`() {
        val engine = engine(LandscapeTool.grass)
        engine.state.plots[start] = Plot("wheat", now)
        assertEquals(rejected(Rejection.NotFinishedGrowing("Wheat", 30.0)), engine.tap(start, now))
        assertEquals(rejected(Rejection.NothingHere), engine.tap(outside, now))
    }

    @Test
    fun `water replaces farmland and frees its room`() {
        val engine = engine(LandscapeTool.water)
        engine.tap(start, now)
        assertEquals(WaterStyle.deep, engine.state.water[start])
        assertFalse(start in engine.state.farmland)
        engine.select(LandscapeTool.farmland)
        assertEquals(placed(outside), engine.tap(outside, now))
        assertEquals(rejected(Rejection.FarmlandLimit(4, 4)), engine.tap(start, now))
        engine.tap(outside, now)
        engine.select(LandscapeTool.grass)
        engine.tap(outside, now)
        engine.select(LandscapeTool.farmland)
        assertEquals(placed(start), engine.tap(start, now))
        assertTrue(start in engine.state.farmland)
        assertNull(engine.state.water[start])
    }

    @Test
    fun `farmland over water works like farmland over grass`() {
        val overWater = engine(LandscapeTool.water)
        overWater.state.xp = LevelRules.xpToReach(3)
        val waterTile = GridPoint(12, 12)
        val grassTile = GridPoint(12, 13)
        overWater.tap(waterTile, now)
        overWater.select(LandscapeTool.farmland)
        assertEquals(placed(waterTile), overWater.tap(waterTile, now))
        assertEquals(placed(grassTile), overWater.tap(grassTile, now))
        assertTrue(overWater.state.farmland.containsAll(listOf(waterTile, grassTile)))
        assertNull(overWater.state.water[waterTile])
    }

    @Test
    fun `grass tiles are what is left over farmland water and buildings`() {
        val state = GameState()
        assertEquals(2_500 - 4, state.grassTileCount)
        state.water[GridPoint(1, 1)] = WaterStyle.deep
        state.putBuilding(GridPoint(10, 10), "barn1")
        assertEquals(2_500 - 4 - 1 - 20, state.grassTileCount)
    }

    @Test
    fun `water is free at zero money and unlimited`() {
        val engine = engine(LandscapeTool.water)
        engine.state.money = 0.0
        assertEquals(0.0, engine.price(com.hunter.stockfarmer.core.model.ShopItem.Water))
        for (column in 0 until 5) {
            val point = GridPoint(10, column)
            assertEquals(placed(point), engine.tap(point, now))
        }
        assertEquals(5, engine.state.water.size)
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `paths cost two dollars a tile and grass is free`() {
        val engine = engine(LandscapeTool.groundCover(GroundCover.dirtPath))
        assertEquals(
            2.0,
            engine.price(
                com.hunter.stockfarmer.core.model.ShopItem
                    .GroundCoverItem(GroundCover.cobblestonePath),
            ),
        )
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(outside, 2.0)), engine.tap(outside, now))
        assertEquals(998.0, engine.state.money)
        engine.select(LandscapeTool.grass)
        assertEquals(placed(outside), engine.tap(outside, now))
        assertEquals(998.0, engine.state.money)
    }

    @Test
    fun `terrain that cannot be paid for is refused and leaves the tile alone`() {
        for (cover in GroundCover.entries) {
            val engine = engine(LandscapeTool.groundCover(cover))
            engine.state.money = 0.0
            assertEquals(rejected(Rejection.NeedMoney(cover.cost)), engine.tap(outside, now))
            assertTrue(engine.state.water[outside] == null && engine.state.groundCover[outside] == null)
        }
    }

    @Test
    fun `erasing water just removes it`() {
        val engine = engine(LandscapeTool.water)
        engine.tap(outside, now)
        engine.select(LandscapeTool.grass)
        assertEquals(placed(outside), engine.tap(outside, now))
        assertNull(engine.state.water[outside])
    }

    @Test
    fun `water is placed in the selected color`() {
        val engine = engine(LandscapeTool.water)
        engine.select(WaterStyle.deep)
        engine.tap(outside, now)
        engine.select(WaterStyle.shallow)
        val next = GridPoint(10, 11)
        engine.tap(next, now)
        assertEquals(WaterStyle.deep, engine.state.water[outside])
        assertEquals(WaterStyle.shallow, engine.state.water[next])
    }

    @Test
    fun `water over fertilized farmland takes the fertilizer with the farmland`() {
        val engine = engine(LandscapeTool.water)
        engine.state.farmland.add(outside)
        engine.state.fertilized.add(outside)
        assertEquals(placed(outside), engine.tap(outside, now))
        assertFalse(outside in engine.state.farmland)
        assertTrue(engine.state.fertilized.isEmpty())
    }

    @Test
    fun `choosing a color selects water and recoloring works`() {
        val engine = engine(LandscapeTool.farmland)
        engine.state.money = 0.0
        engine.select(WaterStyle.deep)
        assertEquals(LandscapeTool.water, engine.state.selectedLandscape)
        engine.tap(outside, now)
        engine.select(WaterStyle.shallow)
        assertEquals(placed(outside), engine.tap(outside, now))
        assertEquals(WaterStyle.shallow, engine.state.water[outside])
        assertEquals(0.0, engine.state.money)
        assertEquals(rejected(Rejection.AlreadyPlaced), engine.tap(outside, now))
    }

    @Test
    fun `farmland next to water is saturated one tile out`() {
        assertEquals(1, FarmlandRules.WATER_REACH)
        val saturated = FarmlandRules.saturatedTiles(GameState.startingFarmland, setOf(GridPoint(24, 23)))
        // (24, 24) and (25, 24) are 1 column away; (24, 25) and (25, 25) are 2 away.
        assertEquals(setOf(GridPoint(24, 24), GridPoint(25, 24)), saturated)
    }

    @Test
    fun `reach includes diagonals and ends at the first tile`() {
        val farmland = setOf(GridPoint(21, 21), GridPoint(21, 22), GridPoint(22, 21))
        assertEquals(setOf(GridPoint(21, 21)), FarmlandRules.saturatedTiles(farmland, setOf(GridPoint(20, 20))))
    }

    @Test
    fun `saturated soil grows faster`() {
        val state = GameState()
        state.water[GridPoint(24, 23)] = WaterStyle.medium
        val conditions = GrowthConditions(state)
        assertEquals(1.05, GrowthConditions.SATURATION_MULTIPLIER)
        assertEquals(GrowthConditions.SATURATION_MULTIPLIER, conditions.speedMultiplier(start))
        assertEquals(1.0, conditions.speedMultiplier(GridPoint(24, 25)))
    }

    /** The tile a structure takes up, which is its bottom row: the room above is only where its picture rises. */
    private fun groundTile(structure: String): GridPoint {
        val height = Building.named(structure)?.height ?: Decoration.named(structure)?.height ?: 1
        return GridPoint(spot.row + height - 1, spot.col)
    }

    private fun engineBeneath(
        structure: String,
        tool: LandscapeTool,
    ): GameEngine {
        val engine = engine(tool)
        engine.state.money = 1_000.0
        engine.state.xp = LevelRules.xpToReach(LevelRules.maxLevel)
        val decoration = Decoration.named(structure)
        if (decoration != null) {
            engine.state.putDecoration(spot, PlacedDecoration.of(decoration, 0))
        } else {
            engine.place(spot, structure)
        }
        return engine
    }

    @Test
    fun `paths and grass can go beneath a building and a decoration without moving them`() {
        for (structure in listOf("barn1", "scarecrow")) {
            val tile = groundTile(structure)
            val engine = engineBeneath(structure, LandscapeTool.groundCover(GroundCover.cobblestonePath))
            assertEquals(
                listOf<GameEvent>(GameEvent.Decorated(tile, GroundCover.cobblestonePath.cost)),
                engine.tap(tile, now),
            )
            assertEquals(GroundCover.cobblestonePath, engine.state.groundCover[tile])
            assertEquals(if (structure == "barn1") structure else null, engine.state.buildings[spot])
            assertEquals(
                if (structure ==
                    "scarecrow"
                ) {
                    structure
                } else {
                    null
                },
                engine.state.decorations[spot]?.decorationID,
            )
            engine.select(LandscapeTool.grass)
            assertEquals(placed(tile), engine.tap(tile, now))
            assertNull(engine.state.groundCover[tile])
        }
    }

    @Test
    fun `farmland and water cannot go beneath a building or a decoration that cannot stand on them`() {
        for (structure in listOf("barn1", "scarecrow")) {
            for (tool in listOf(LandscapeTool.farmland, LandscapeTool.water)) {
                val tile = groundTile(structure)
                val engine = engineBeneath(structure, tool)
                assertEquals(rejected(Rejection.StructureHere), engine.tap(tile, now))
                assertTrue(tile !in engine.state.farmland && engine.state.water[tile] == null)
            }
        }
    }

    @Test
    fun `a sprinkler stands on any terrain`() {
        for (tool in listOf(
            LandscapeTool.farmland,
            LandscapeTool.water,
            LandscapeTool.groundCover(GroundCover.dirtPath),
        )) {
            val tile = groundTile("sprinkler")
            val engine = engineBeneath("sprinkler", tool)
            assertFalse(engine.tap(tile, now).containsRejection)
            assertEquals("sprinkler", engine.state.decorations[spot]?.decorationID)
        }
    }

    @Test
    fun `rocks and trees still block terrain`() {
        val engine = engine(LandscapeTool.groundCover(GroundCover.dirtPath))
        engine.state.rocks[spot] = RockSize.small
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
    }
}
