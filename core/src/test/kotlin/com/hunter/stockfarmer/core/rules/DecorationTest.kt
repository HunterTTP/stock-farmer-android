package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Sets the selected sprinkler down the way the game does: carried to a tile, then confirmed. */
private fun GameEngine.placeSprinkler(
    tile: GridPoint,
    now: Instant,
): List<GameEvent> {
    beginPlacingSelectedDecoration(tile)
    return confirmPlacement(now)
}

private fun rejected(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))

class DecorationTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val spot = GridPoint(20, 20)

    private fun engine(
        id: String,
        money: Double = 100_000.0,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = money, xp = lateGameXP))
        engine.select(Decoration.named(id)!!)
        return engine
    }

    @Test
    fun `original decorations keep their order and all unlocks are reachable`() {
        assertEquals(
            listOf("rock", "woodenTable", "scarecrow", "trunk", "pumpkin", "sprinkler", "well", "weathercock"),
            Decoration.all.take(8).map { it.id },
        )
        assertEquals(
            Decoration.all
                .take(8)
                .map { it.unlockLevel }
                .sorted(),
            Decoration.all.take(8).map { it.unlockLevel },
        )
        assertTrue(Decoration.all.all { it.unlockLevel <= LevelRules.maxLevel })
        assertEquals(
            12,
            Decoration.all
                .take(8)
                .flatMap { it.assetNames }
                .size,
        )
        assertEquals(
            12,
            Decoration.all
                .take(8)
                .flatMap { it.assetNames }
                .toSet()
                .size,
        )
    }

    @Test
    fun `locked decorations cannot be picked`() {
        val engine = GameEngine()
        engine.select(Decoration.named("well")!!)
        assertTrue(engine.selectedDecoration == null && engine.state.mode == PlayMode.plant)
        assertEquals(11, engine.requiredLevel(ShopItem.DecorationItem("well")))
    }

    @Test
    fun `tapping bare grass sets the decoration down and charges it`() {
        val engine = engine("scarecrow")
        assertTrue(engine.isSelected(ShopItem.DecorationItem("scarecrow")) && !engine.isSelected(ShopItem.Fence))
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(spot, 150.0)), engine.tap(spot, now))
        assertEquals(100_000.0 - 150, engine.state.money)
        assertEquals(
            "scarecrow",
            engine.state
                .decoration(spot)
                ?.second
                ?.decorationID,
        )
        assertEquals(1, engine.placedOnMap(ShopItem.DecorationItem("scarecrow")))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(spot, now))
    }

    @Test
    fun `a bigger decoration stands on tiles rising from the tapped bottom left`() {
        val engine = engine("well")
        assertEquals(
            listOf(GridPoint(19, 20), GridPoint(19, 21), GridPoint(20, 20), GridPoint(20, 21)),
            engine.toolFootprint(spot).sortedWith(compareBy({ it.row }, { it.col })),
        )
        engine.tap(spot, now)
        assertEquals(
            GridPoint(19, 20),
            engine.state.decorations.keys
                .first(),
        )
        assertNotNull(engine.state.decoration(GridPoint(20, 21)))
        assertTrue(engine.state.hasObstacle(GridPoint(20, 21)) && !engine.state.hasObstacle(GridPoint(19, 21)))
    }

    @Test
    fun `not enough money or room is refused`() {
        val poor = engine("sprinkler", money = 100.0)
        assertEquals(rejected(Rejection.NeedMoney(400.0)), poor.placeSprinkler(spot, now))
        val engine = engine("woodenTable")
        engine.state.water[GridPoint(20, 21)] = WaterStyle.deep
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(spot, now))
        engine.state.farmland.add(GridPoint(30, 30))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(30, 30), now))
    }

    @Test
    fun `a sprinkler can be set down on grass farmland or water`() {
        val engine = engine("sprinkler")
        val farmland = GridPoint(30, 30)
        val water = GridPoint(32, 32)
        engine.state.farmland.add(farmland)
        engine.state.water[water] = WaterStyle.deep
        for (tile in listOf(spot, farmland, water)) {
            assertEquals(listOf<GameEvent>(GameEvent.Decorated(tile, 400.0)), engine.placeSprinkler(tile, now))
        }
        assertTrue(farmland in engine.state.farmland && engine.state.water[water] != null)
    }

    @Test
    fun `a sprinkler is refused where a crop or an animal stands`() {
        val engine = engine("sprinkler")
        val planted = GridPoint(30, 30)
        engine.state.farmland.add(planted)
        engine.state.plots[planted] = Plot(Crop.starter.id, now)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.placeSprinkler(planted, now))
        engine.place(GridPoint(10, 10), "petHouse")
        engine.state.pets[GridPoint(10, 10)] = Pet(breed = "grey", name = "x", position = spot)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.placeSprinkler(spot, now))
    }

    @Test
    fun `nothing is planted or fertilized under a sprinkler`() {
        val engine = engine("sprinkler")
        engine.state.farmland.add(spot)
        engine.placeSprinkler(spot, now)
        engine.select(Crop.starter)
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
        engine.selectFertilizer()
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
    }

    @Test
    fun `decorations block other things like trees do`() {
        val engine = engine("trunk")
        engine.tap(spot, now)
        assertTrue(!engine.isOpenGround(spot) && !engine.isFreeForTree(spot))
        engine.select(LandscapeTool.farmland)
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
        engine.select(FenceColor.starter)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(spot, now))
        assertTrue(engine.useTool(spot, now).isEmpty())
    }

    @Test
    fun `destroying gives eighty percent of the price back`() {
        val engine = engine("sprinkler")
        engine.placeSprinkler(spot, now)
        assertEquals(listOf<GameEvent>(GameEvent.Undecorated(spot, 320.0)), engine.destroyDecoration(spot, now))
        assertEquals(100_000.0 - 400 + 320, engine.state.money)
        assertTrue(engine.state.decorations.isEmpty())
        assertTrue(engine.destroyDecoration(spot, now).isEmpty())
    }

    @Test
    fun `picking fences again goes back to fences`() {
        val engine = engine("sprinkler")
        engine.select(FenceColor.starter)
        assertTrue(engine.selectedDecoration == null && engine.isSelected(ShopItem.Fence))
        assertEquals(listOf<GameEvent>(GameEvent.Fenced(spot, 2.0)), engine.tap(spot, now))
    }

    @Test
    fun `decorations survive a save`() {
        val engine = engine("well")
        engine.tap(spot, now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.decorations, loaded.decorations)
        assertEquals("well", loaded.selectedDecorationID)
        assertNotNull(loaded.decoration(GridPoint(20, 21)))
        val old = GameStateJson.decode("""{"money": 1}""")
        assertTrue(old.decorations.isEmpty() && old.selectedDecorationID == null)
    }
}

class DecorationEffectTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val spot = GridPoint(20, 20)

    private fun engine(
        id: String,
        money: Double = 100_000.0,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = money, xp = lateGameXP))
        engine.select(Decoration.named(id)!!)
        return engine
    }

    private fun state(
        id: String,
        origin: GridPoint,
        farmland: List<GridPoint>,
    ): GameState {
        val state = GameState()
        state.farmland = farmland.toMutableSet()
        state.putDecoration(origin, PlacedDecoration.of(Decoration.named(id)!!, 0))
        return state
    }

    @Test
    fun `a sprinkler waters farmland two tiles away each way and a well waters nothing`() {
        val origin = GridPoint(20, 20)
        val near = GridPoint(22, 22)
        val far = GridPoint(23, 20)
        val sprinkler = state("sprinkler", origin, listOf(near, far))
        assertEquals(setOf(near), sprinkler.saturatedFarmland)
        val well = state("well", origin, listOf(near, GridPoint(21, 20), GridPoint(21, 21)))
        assertTrue(well.saturatedFarmland.isEmpty())
        assertEquals(GrowthConditions.SATURATION_MULTIPLIER, GrowthConditions(sprinkler).speedMultiplier(near))
    }

    @Test
    fun `a new sprinkler saturates its farmland tile by tile over its first spray`() {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP))
        val tile = GridPoint(20, 20)
        val reach = (18..22).flatMap { row -> (18..22).map { GridPoint(row, it) } }.filter { it != tile }
        engine.state.farmland.addAll(reach)
        engine.state.farmland.add(GridPoint(30, 30))
        engine.select(Decoration.named("sprinkler")!!)
        engine.placeSprinkler(tile, now)
        var previous = 0
        var second = 0.0
        while (second <= Decoration.SPRAY_DURATION - 0.5) {
            engine.activateSprinklers(now.advancedBy(second))
            val saturated = engine.state.saturatedFarmland
            assertTrue(saturated.size >= previous && reach.containsAll(saturated))
            previous = saturated.size
            second += 0.5
        }
        val half = engine.state.saturatedFarmland.size
        assertTrue(previous > 0 && previous < reach.size)
        engine.activateSprinklers(now.advancedBy(Decoration.SPRAY_DURATION / 2))
        assertTrue(half >= engine.state.saturatedFarmland.size && half > 5)
        val finished = engine.activateSprinklers(now.advancedBy(Decoration.SPRAY_DURATION))
        assertTrue(finished && engine.state.saturatedFarmland == reach.toSet())
        val again = engine.activateSprinklers(now.advancedBy(1_000.0))
        assertTrue(
            !again && engine.state.decorations[tile]
                ?.wateredTiles
                ?.isEmpty() == true,
        )
    }

    @Test
    fun `a sprinkler is carried with arrows and only charged when confirmed`() {
        val engine = engine("sprinkler")
        engine.beginPlacingSelectedDecoration(spot)
        assertTrue(engine.isPlacing && engine.objectPlacement?.isNew == true && engine.state.money == 100_000.0)
        engine.nudgePlacement(0, 3)
        assertEquals(GridPoint(20, 23), engine.objectPlacement?.origin)
        engine.cancelPlacement()
        assertTrue(!engine.isPlacing && engine.state.decorations.isEmpty() && engine.state.money == 100_000.0)
        engine.beginPlacingSelectedDecoration(spot)
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(spot, 400.0)), engine.confirmPlacement(now))
        assertTrue(engine.state.money == 100_000.0 - 400 && engine.state.decorations[spot]?.placedAt == now)
        assertTrue(engine.isSprinklerSelected && engine.cannotStartPlacingSelectedDecoration == null)
        val poor = engine("sprinkler", money = 100.0)
        assertEquals(Rejection.NeedMoney(400.0), poor.cannotStartPlacingSelectedDecoration)
    }

    @Test
    fun `tapping with a sprinkler selected starts carrying it`() {
        val engine = engine("sprinkler")
        assertTrue(engine.tap(spot, now).isEmpty() && engine.objectPlacement?.origin == spot)
    }

    @Test
    fun `a sprinkler spraying dries its land out tile by tile when destroyed or moved`() {
        val tile = GridPoint(20, 20)
        val reach = (18..22).flatMap { row -> (18..22).map { GridPoint(row, it) } }.filter { it != tile }
        for (moving in listOf(false, true)) {
            val engine = engine("sprinkler")
            engine.state.farmland.addAll(reach)
            engine.placeSprinkler(tile, now)
            engine.activateSprinklers(now.advancedBy(Decoration.SPRAY_DURATION))
            assertEquals(reach.toSet(), engine.state.saturatedFarmland)
            val later = now.advancedBy(100.0)
            if (moving) {
                engine.beginMovingDecoration(tile)
                engine.movePlacement(GridPoint(40, 40))
                engine.confirmPlacement(later)
            } else {
                engine.destroyDecoration(tile, later)
            }
            assertTrue(engine.state.saturatedFarmland == reach.toSet() && engine.isSoilWateringChanging)
            var previous = reach.size
            var atHalfway = 0
            var second = 0.0
            while (second <= Decoration.SPRAY_DURATION - 0.5) {
                engine.activateSprinklers(later.advancedBy(second))
                val saturated = engine.state.saturatedFarmland.size
                assertTrue(saturated <= previous)
                previous = saturated
                if (second == Decoration.SPRAY_DURATION / 2) atHalfway = saturated
                second += 0.5
            }
            assertTrue(atHalfway > 0 && atHalfway < reach.size)
            engine.activateSprinklers(later.advancedBy(Decoration.SPRAY_DURATION))
            assertTrue(engine.state.saturatedFarmland.isEmpty() && engine.state.lingeringWater.isEmpty())
            assertFalse(engine.isSoilWateringChanging)
        }
    }

    @Test
    fun `lingering water is saved`() {
        val engine = engine("sprinkler")
        engine.state.farmland.add(GridPoint(21, 21))
        engine.placeSprinkler(spot, now)
        engine.activateSprinklers(now.advancedBy(Decoration.SPRAY_DURATION))
        engine.destroyDecoration(spot, now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(loaded.lingeringWater == engine.state.lingeringWater && loaded.lingeringWater.isNotEmpty())
        assertTrue(GameStateJson.decode("""{"money": 1}""").lingeringWater.isEmpty())
    }

    @Test
    fun `every sprinkler sprays together once an hour on one shared schedule`() {
        val boundary = Instant.ofEpochSecond(6_000)
        assertEquals(60.0, Decoration.SPRAY_INTERVAL)
        assertEquals(100, Decoration.spraysDone(boundary))
        assertEquals(60.0, Decoration.timeUntilNextSpray(boundary))
        assertEquals(15.0, Decoration.timeUntilNextSpray(boundary.advancedBy(45.0)))
        assertEquals(100, Decoration.spraysDone(boundary.advancedBy(59.0)))
        assertEquals(101, Decoration.spraysDone(boundary.advancedBy(60.0)))
        assertEquals(55.0, Decoration.timeUntilNextSpray(boundary.advancedBy(185.0)))
    }

    @Test
    fun `a scarecrow starts at once and an old sprinkler save still decodes`() {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP))
        val tile = GridPoint(20, 20)
        val near = GridPoint(21, 22)
        engine.state.farmland.add(near)
        engine.select(Decoration.named("scarecrow")!!)
        engine.tap(tile, now)
        assertEquals(setOf(near), engine.state.guardedFarmland)
        engine.select(Decoration.named("sprinkler")!!)
        engine.placeSprinkler(GridPoint(30, 30), now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.decorations, loaded.decorations)
        val oldSprinkler = Json.decodeFromString<PlacedDecoration>("""{"decorationID": "sprinkler", "size": 0}""")
        assertTrue(
            oldSprinkler.wateringStartsAt == null && oldSprinkler.placedAt == null &&
                oldSprinkler.wateredTiles.isEmpty(),
        )
        assertEquals(now, loaded.decorations[GridPoint(30, 30)]?.placedAt)
    }

    @Test
    fun `a scarecrow speeds up crops near it and stacks with water`() {
        val origin = GridPoint(20, 20)
        val near = GridPoint(21, 23)
        val far = GridPoint(24, 20)
        val state = state("scarecrow", origin, listOf(near, far))
        assertEquals(setOf(near), state.guardedFarmland)
        assertEquals(Decoration.GUARD_MULTIPLIER, GrowthConditions(state).speedMultiplier(near))
        assertEquals(1.0, GrowthConditions(state).speedMultiplier(far))
        state.water[GridPoint(21, 24)] = WaterStyle.deep
        assertEquals(
            Decoration.GUARD_MULTIPLIER * GrowthConditions.SATURATION_MULTIPLIER,
            GrowthConditions(state).speedMultiplier(near),
        )
    }

    @Test
    fun `trunks are cosmetic and can be removed while old storage is over capacity`() {
        val engine = GameEngine(state("trunk", GridPoint(20, 20), emptyList()))
        assertEquals(BACKPACK_CAPACITY, engine.storageCapacity)
        engine.state.storage = mutableMapOf("wheat" to 110)
        assertEquals(1, engine.destroyDecoration(GridPoint(20, 20), now).size)
    }

    @Test
    fun `at most fifteen trunks can be placed`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP))
        engine.select(Decoration.named("trunk")!!)
        for (index in 0 until Decoration.TRUNK_LIMIT) assertEquals(1, engine.tap(GridPoint(5, 2 * index + 1), now).size)
        assertEquals(rejected(Rejection.BuildingLimit("Trunk", 15)), engine.tap(GridPoint(10, 10), now))
    }

    @Test
    fun `a pumpkin comes in three sizes picked before placing and does not grow`() {
        val pumpkin = Decoration.named("pumpkin")!!
        assertTrue(pumpkin.sizeNames == listOf("Small", "Medium", "Big") && pumpkin.assetNames.size == 3)
        assertEquals(
            listOf("rock", "pumpkin"),
            Decoration.all
                .take(8)
                .filter { it.sizeNames.isNotEmpty() }
                .map { it.id },
        )
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP))
        engine.select(pumpkin)
        assertEquals(0, engine.selectedDecorationSize)
        engine.select(2, pumpkin)
        assertEquals(2, engine.selectedDecorationSize)
        val place = GridPoint(20, 20)
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(place, 20.0)), engine.tap(place, now))
        engine.select(1, pumpkin)
        val next = GridPoint(20, 22)
        engine.tap(next, now)
        assertTrue(engine.state.decorations[place]?.size == 2 && engine.state.decorations[next]?.size == 1)
        // Time changes nothing, and neither tool picks one.
        engine.select(HarvestTool.scythe)
        assertTrue(engine.useTool(place, now.advancedBy(1_000_000.0)).isEmpty())
        assertNull(engine.missingHarvestToolRejection(place, now.advancedBy(1_000_000.0)))
        engine.select(9, pumpkin)
        assertEquals(1, engine.selectedDecorationSize)
    }

    @Test
    fun `a saved pumpkin keeps its size and one from before sizes is small`() {
        val placed = PlacedDecoration.of(Decoration.named("pumpkin")!!, 2)
        val loaded = Json.decodeFromString<PlacedDecoration>(Json.encodeToString(placed))
        assertEquals(placed, loaded)
        val old = Json.decodeFromString<PlacedDecoration>("""{"decorationID": "pumpkin"}""")
        assertEquals(0, old.size)
        assertEquals(2, PlacedDecoration.of(placed.decoration, 7).size)
    }

    @Test
    fun `a rock is set down in the chosen size and breaks with the pickaxe for nothing`() {
        val rock = Decoration.named("rock")!!
        assertTrue(rock.isRock && rock.sizeNames == RockSize.stoneSizes.map { it.displayName } && rock.unlockLevel == 1)
        val engine = GameEngine(stateWith(money = 1_000.0, xp = 0, logs = 0, stones = 0))
        engine.select(2, rock)
        val place = GridPoint(20, 20)
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(place, rock.cost)), engine.tap(place, now))
        assertEquals(RockSize.big, engine.state.rocks[place])
        assertTrue(engine.state.decorations.isEmpty())
        assertEquals(1_000.0 - rock.cost, engine.state.money)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(place, now))
        engine.select(HarvestTool.pickaxe)
        repeat(RockSize.big.hitsToBreak) {
            engine.useTool(place, now)
            engine.liftTool()
        }
        assertNull(engine.state.rocks[place])
        assertNull(engine.state.storage[StorageItem.Stone.id])
        assertTrue(engine.state.playerPlacedRocks.isEmpty())
        assertTrue(
            engine.state.career.minedRockIDs
                .isEmpty(),
        )
    }
}
