package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.FenceConnections
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

/** Fences and shelters in the engine. */
class FenceShelterEngineTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val spot = GridPoint(10, 10)

    private fun engine(money: Double = 100.0): GameEngine {
        val engine = GameEngine(stateWith(money = money))
        engine.select(FenceColor.starter)
        return engine
    }

    private fun rejected(rejection: Rejection): List<GameEvent> = listOf(GameEvent.Rejected(rejection))

    @Test fun `there is one color for now and selecting it switches to fences`() {
        assertEquals(listOf("wood"), FenceColor.all.map { it.id })
        val engine = GameEngine()
        engine.select(FenceColor.starter)
        assertEquals(PlayMode.decorate, engine.state.mode)
        assertTrue(engine.isSelected(com.hunter.stockfarmer.core.model.ShopItem.Fence))
    }

    @Test fun `a fence costs two dollars and appears in the chosen color`() {
        val engine = engine()
        assertEquals(listOf<GameEvent>(GameEvent.Fenced(spot, 2.0)), engine.tap(spot, now))
        assertEquals("wood", engine.state.fences[spot])
        assertEquals(98.0, engine.state.money)
    }

    @Test fun `a fence needs money`() {
        val engine = engine(money = 1.0)
        assertEquals(rejected(Rejection.NeedMoney(2.0)), engine.tap(spot, now))
        assertTrue(engine.state.fences.isEmpty())
    }

    @Test fun `tapping a fence of the same color removes it and refunds`() {
        val engine = engine()
        engine.tap(spot, now)
        assertEquals(listOf<GameEvent>(GameEvent.Unfenced(spot, 2.0)), engine.tap(spot, now))
        assertTrue(engine.state.fences.isEmpty())
        assertEquals(100.0, engine.state.money)
    }

    @Test fun `fences go only on free grass`() {
        val engine = engine()
        engine.state.water[GridPoint(12, 12)] = WaterStyle.deep
        engine.state.putBuilding(GridPoint(14, 14), "barn1")
        engine.state.plots[GridPoint(30, 30)] = Plot("wheat", now)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(24, 24), now))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(12, 12), now))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(18, 15), now))
        assertTrue(engine.state.fences.isEmpty())
    }

    @Test fun `fences block landscaping and buildings`() {
        val engine = engine()
        engine.tap(spot, now)
        engine.select(LandscapeTool.farmland)
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
        engine.select(LandscapeTool.water)
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
        engine.state.money = 10_000.0
        engine.state.xp = lateGameXP
        engine.state.mode = PlayMode.build
        engine.state.selectedBuildingID = "petHouse"
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.build(spot, now))
    }

    @Test fun `terrain never removes a fence but a path goes underneath`() {
        val engine = engine()
        engine.tap(spot, now)
        val fence = engine.state.fences.toMap()
        engine.select(LandscapeTool.grass)
        assertEquals(rejected(Rejection.NothingHere), engine.tap(spot, now))
        engine.select(LandscapeTool.groundCover(GroundCover.dirtPath))
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(spot, GroundCover.dirtPath.cost)), engine.tap(spot, now))
        engine.select(LandscapeTool.grass)
        assertEquals(listOf<GameEvent>(GameEvent.Placed(spot)), engine.tap(spot, now))
        assertTrue(engine.state.groundCover.isEmpty() && engine.state.fences == fence)
    }

    @Test fun `animals cannot walk through fences or have one built on them`() {
        val engine = engine()
        engine.state.putBuilding(GridPoint(20, 20), "petHouse")
        engine.state.pets[GridPoint(20, 20)] = Pet(breed = "grey", name = "x", position = GridPoint(21, 20))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(21, 20), now))
        engine.tap(GridPoint(21, 21), now)
        assertNull(engine.move(AnimalRef.Pet(GridPoint(20, 20)), GridPoint(0, 1)))
    }

    @Test fun `fences survive a save and load`() {
        val engine = engine()
        engine.tap(spot, now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.fences, loaded.fences)
    }

    @Test fun `a fence may stand behind any row of a building but its bottom row`() {
        val engine = engine()
        engine.state.putBuilding(GridPoint(20, 20), "rabbitHutch")
        assertEquals(
            listOf<GameEvent>(GameEvent.Fenced(GridPoint(20, 21), 2.0)),
            engine.tap(GridPoint(20, 21), now),
        )
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(21, 21), now))
    }

    @Test fun `a short building has no top row to overlap`() {
        val engine = engine()
        engine.state.putBuilding(GridPoint(20, 20), "petHouse")
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(20, 20), now))
    }

    @Test fun `a tall building may be built over fences in its top row only`() {
        val engine = engine()
        engine.state.money = 100_000.0
        engine.state.xp = lateGameXP
        engine.state.storage[StorageItem.Stone.id] = 6
        engine.tap(GridPoint(20, 21), now)
        engine.state.mode = PlayMode.build
        engine.state.selectedBuildingID = "rabbitHutch"
        engine.beginPlacing(GridPoint(20, 21))
        val top = engine.buildingPlacement!!.origin
        engine.movePlacement(GridPoint(20, 20))
        assertTrue(top.row == 19 || top.row == 20)
        assertNull(engine.placementRejection(now))
        engine.movePlacement(GridPoint(19, 20))
        assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
    }

    @Test fun `house and coop need plain ground in front`() {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, mode = PlayMode.build))
        engine.state.selectedBuildingID = "petHouse"
        engine.state.water[GridPoint(11, 10)] = WaterStyle.deep
        assertEquals(rejected(Rejection.NeedsFrontTile), engine.build(GridPoint(10, 10), now))
        engine.state.water.clear()
        engine.state.fences[GridPoint(11, 10)] = "wood"
        assertEquals(rejected(Rejection.NeedsFrontTile), engine.build(GridPoint(10, 10), now))
        engine.state.fences.clear()
        engine.state.farmland += GridPoint(11, 10)
        assertEquals(
            listOf<GameEvent>(GameEvent.Built(GridPoint(10, 10), 25.0)),
            engine.build(GridPoint(10, 10), now),
        )
    }

    @Test fun `nothing blocks the front tile of a house later`() {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, mode = PlayMode.build))
        engine.state.selectedBuildingID = "petHouse"
        engine.build(GridPoint(10, 10), now)
        engine.select(FenceColor.starter)
        assertEquals(rejected(Rejection.BlocksAFront), engine.tap(GridPoint(11, 10), now))
        engine.select(LandscapeTool.water)
        assertEquals(rejected(Rejection.BlocksAFront), engine.tap(GridPoint(11, 10), now))
    }

    @Test fun `destroying a fence refunds it from any mode`() {
        val engine = engine()
        engine.tap(spot, now)
        engine.setMode(PlayMode.plant)
        assertEquals(listOf<GameEvent>(GameEvent.Unfenced(spot, 2.0)), engine.destroyFence(spot))
        assertTrue(engine.state.fences.isEmpty())
        assertTrue(engine.destroyFence(spot).isEmpty())
    }

    @Test fun `the axe takes down a fence into storage like a decoration`() {
        val engine = engine()
        engine.tap(spot, now)
        engine.select(HarvestTool.axe)
        val events = engine.useTool(spot, now)
        assertFalse(events.containsRejection)
        assertNull(engine.state.fences[spot])
        assertEquals(1, engine.state.storage[StorageItem.Fence(FenceColor.starter).id])
        assertEquals(98.0, engine.state.money)
    }

    @Test fun `the axe leaves a fence standing when storage is full`() {
        val engine = engine()
        engine.tap(spot, now)
        engine.state.storage[StorageItem.Stone.id] = engine.storageCapacity
        engine.select(HarvestTool.axe)
        assertEquals(rejected(Rejection.StorageFull), engine.useTool(spot, now))
        assertNotNull(engine.state.fences[spot])
    }

    @Test fun `a stored fence can be equipped and set down for free until none are left`() {
        val engine = engine(money = 0.0)
        val item = StorageItem.Fence(FenceColor.starter)
        engine.state.storage[item.id] = 1
        assertTrue(item.isEquippable && !engine.isEquipped(item))
        assertTrue(engine.equip(item))
        assertTrue(engine.isEquipped(item) && engine.isPlacingFromStorage)
        assertEquals(listOf<GameEvent>(GameEvent.Fenced(spot, 0.0)), engine.tap(spot, now))
        assertEquals(FenceColor.starter.id, engine.state.fences[spot])
        assertTrue(engine.state.storage[item.id] == null && engine.state.money == 0.0)
        val next = GridPoint(10, 12)
        assertEquals(rejected(Rejection.NoneInStorage(item.name)), engine.tap(next, now.plusSeconds(5)))
        assertNull(engine.state.fences[next])
    }

    @Test fun `picking the fence from the list goes back to paying for it`() {
        val engine = engine()
        val item = StorageItem.Fence(FenceColor.starter)
        engine.state.storage[item.id] = 3
        engine.equip(item)
        engine.select(FenceColor.starter)
        assertFalse(engine.isEquipped(item))
        assertEquals(listOf<GameEvent>(GameEvent.Fenced(spot, FenceColor.price)), engine.tap(spot, now))
        assertEquals(3, engine.state.storage[item.id])
    }

    @Test fun `a stored fence is worth what destroying one refunds`() {
        val item = StorageItem.Fence(FenceColor.starter)
        assertTrue(item.name == "Wood Fence" && item.isSellable)
        assertEquals((FenceColor.price * GameEngine.DESTROY_REFUND_SHARE).roundedToCents, item.sellValue)
        assertEquals(item, StorageItem.of(item.id))
    }

    @Test fun `connections come from the neighbors on the map`() {
        val engine = engine()
        engine.tap(spot, now)
        engine.tap(GridPoint(10, 11), now)
        engine.tap(GridPoint(11, 10), now)
        assertEquals(FenceConnections(false, true, true, false), FenceConnections(spot, engine.state.fences))
    }

    // Shelter

    private val base = Instant.ofEpochSecond(12_000_000)
    private val house = GridPoint(20, 20)
    private val front = GridPoint(21, 20)

    private fun shelterEngine(
        minute: Double,
        at: Instant,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build))
        engine.state.selectedBuildingID = "petHouse"
        engine.build(house, at)
        engine.setTimeOfDay(minute, at)
        return engine
    }

    @Test fun `animals need shelter at night and in the rain but not by day`() {
        assertTrue(shelterEngine(0.0, base).needsShelter(base))
        assertFalse(shelterEngine(12 * 60.0, base).needsShelter(base))
        assertTrue(shelterEngine(14.5 * 60, base).needsShelter(base))
    }

    @Test fun `animals go in once the rain is half built up and stay in until it has stopped`() {
        assertFalse(shelterEngine(14 * 60 + 2.0, base).needsShelter(base))
        assertFalse(shelterEngine(14 * 60 + 9.9, base).needsShelter(base))
        assertTrue(shelterEngine(14 * 60 + 10.0, base).needsShelter(base))
        assertTrue(shelterEngine(16 * 60 + 50.0, base).needsShelter(base))
        assertFalse(shelterEngine(17 * 60.0, base).needsShelter(base))
    }

    @Test fun `at night an animal walks to the front of its home and is inside`() {
        val engine = shelterEngine(0.0, base)
        engine.state.pets[house] = Pet(breed = "grey", name = "x", position = GridPoint(24, 17))
        val pet = AnimalRef.Pet(house)
        val random = SeededRandom(3)
        var steps = 0
        while (engine.position(pet) != front && steps++ < 40) {
            engine.wanderDirection(pet, base, random)?.let { engine.move(pet, it) }
        }
        assertEquals(front, engine.position(pet))
        assertTrue(engine.isInside(pet, base))
        assertNull(engine.wanderDirection(pet, base, random))
    }

    @Test fun `by day an animal at the front is not inside and wanders off`() {
        val engine = shelterEngine(12 * 60.0, base)
        engine.state.pets[house] = Pet(breed = "grey", name = "x", position = front)
        val random = SeededRandom(4)
        assertFalse(engine.isInside(AnimalRef.Pet(house), base))
        assertNotNull(engine.wanderDirection(AnimalRef.Pet(house), base, random))
    }

    @Test fun `a pond duck has no home to go to`() {
        val engine = GameEngine()
        engine.state.herds[GridPoint(10, 10)] = Herd(nextBirthAt = base)
        assertNull(engine.shelterTile(AnimalRef.Livestock(GridPoint(10, 10), 0)))
    }

    @Test fun `an animal far from home finds its way round a long fence that the nearby search cannot cross`() {
        val engine = shelterEngine(14.5 * 60, base)
        val pet = AnimalRef.Pet(house)
        for (row in 0 until 48) engine.state.fences[GridPoint(row, 30)] = "wood"
        val step = engine.directionToShelter(pet, GridPoint(21, 40))!!
        assertEquals(1, abs(step.row) + abs(step.col))
        // The way home is long, but following it step by step gets there.
        var spot = GridPoint(21, 40)
        var steps = 0
        while (spot != front && steps++ < 200) {
            val way = engine.directionToShelter(pet, spot)!!
            spot = GridPoint(spot.row + way.row, spot.col + way.col)
        }
        assertEquals(front, spot)
    }

    @Test fun `an animal with no way home because it is fenced in has no direction`() {
        val engine = shelterEngine(14.5 * 60, base)
        for (offset in -2..2) {
            for (
            tile in
            listOf(
                GridPoint(30 + offset, 40 - 2),
                GridPoint(30 + offset, 40 + 2),
                GridPoint(30 - 2, 40 + offset),
                GridPoint(30 + 2, 40 + offset),
            )
            ) {
                engine.state.fences[tile] = "wood"
            }
        }
        assertNull(engine.directionToShelter(AnimalRef.Pet(house), GridPoint(30, 40)))
    }
}
