package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.WaterLayoutCache
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.secondsSince
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

/** The rules' fast paths give what the plain versions they replaced gave. */
class EngineEquivalenceTest {
    /** The walk as the game takes it: one [AnimalWalk] shared by all the animals of a tick. */
    private val sharedWalk: Walker = { engine, now, random ->
        val walk = engine.animalWalk(now)
        for (animal in engine.animals) {
            if (random.nextUnit() > STEP_CHANCE) continue
            engine.wanderDirection(animal, random, walk)?.let { engine.move(animal, it) }
        }
    }

    /** Herds brought up to date one step at a time, each step in full, as they were before quiet steps were skipped. */
    private val everyHerdStep: HerdUpdate = { engine, now, random -> engine.steppingUpdateHerds(now, random) }

    private fun GameEngine.steppingUpdateHerds(
        now: Instant,
        using: RandomSource,
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        for (placed in state.placedBuildings) {
            val home = placed.building.livestockHome ?: continue
            if (state.herds[placed.origin] == null) state.herds[placed.origin] = Herd(nextBirthAt = now)
            events += steppingAdvance(placed.origin, home, now, using)
        }
        settleDucksOnPonds(now)
        for (origin in pondHerdOrigins) events += steppingAdvance(origin, LivestockHome.pond, now, using)
        return events
    }

    private fun GameEngine.steppingAdvance(
        origin: GridPoint,
        home: LivestockHome,
        now: Instant,
        using: RandomSource,
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val earliest = now.advancedBy(-LONGEST_HERD_CATCH_UP)
        var time = maxOf(state.herds[origin]?.processedAt ?: now, earliest)
        do {
            val previous = time
            time = minOf(now, time.advancedBy(HERD_STEP))
            growHerd(origin, time.secondsSince(previous), time)
            events += bearDueBabies(origin, home, time, using)
        } while (time < now)
        state.herds[origin]?.let { state.herds[origin] = it.copy(processedAt = now) }
        return events
    }

    /** Trees grown with a full index after every change, as they were before. */
    private val reindexingTreeGrowth: TreeGrowth = { engine, now -> engine.reindexingGrowTrees(now) }

    private fun GameEngine.reindexingGrowTrees(now: Instant): Boolean {
        var didGrow = false
        val carried = objectPlacement?.takeIf { it.obj is ObjectPlacement.Object.OfTree }?.from
        val due =
            state.trees.filter { (origin, placed) ->
                origin != carried && placed.tree.next != null && now.secondsSince(placed.grewAt) >= Tree.growthInterval
            }
        for (origin in due.keys.sortedWith(compareBy({ it.row }, { it.col }))) {
            var placed = due.getValue(origin)
            var position = origin
            while (true) {
                val next = placed.tree.next ?: break
                if (now.secondsSince(placed.grewAt) < Tree.growthInterval) break
                val newOrigin = next.origin(placed.tree.trunk(position))
                val ground = next.footprint(newOrigin)
                val own = placed.tree.footprint(position).toSet()
                if (!ground.all { it in own || isBareGround(it) } || blocksAHomesFront(ground)) break
                state.removeTree(position)
                placed =
                    PlacedTree(
                        next,
                        placed.color,
                        placed.grewAt.advancedBy(Tree.growthInterval),
                        placed.isNaturallyGenerated,
                    )
                position = newOrigin
                state.putTree(position, placed)
                if (!placed.isNaturallyGenerated) noteTerrain(CareerRecord.terrainID(next, placed.color))
                didGrow = true
            }
        }
        return didGrow
    }

    @Test
    fun `a walk shared by all the animals of a tick gives the recorded results`() {
        val scenarios = EngineGoldenScenarios(walker = sharedWalk)
        assertEquals(EngineGolden.FOOD_FENCED_OFF, scenarios.foodFencedOff())
        assertEquals(EngineGolden.FOOD_BEHIND_A_GATE, scenarios.foodBehindAGate())
        assertEquals(EngineGolden.SHELTER, scenarios.shelterFromRainAndNight())
        assertEquals(EngineGolden.TRACTOR, scenarios.tractorThroughTheAnimals())
        assertEquals(EngineGolden.STRANDED, scenarios.strandedAnimals())
        assertEquals(EngineGolden.RANDOM_FARMS, scenarios.randomFarms())
    }

    @Test
    fun `a shared walk and a new one for each animal agree on farms the recorded runs never saw`() {
        val seeds = 100L..130L
        assertEquals(
            EngineGoldenScenarios(walker = walkEachAnimal).randomFarms(seeds),
            EngineGoldenScenarios(walker = sharedWalk).randomFarms(seeds),
        )
    }

    @Test
    fun `taking every herd step reproduces the recorded herds`() {
        assertEquals(EngineGolden.HERD_CATCH_UP, EngineGoldenScenarios(herdUpdate = everyHerdStep).herdCatchUp(1L..80L))
    }

    @Test
    fun `skipping quiet herd steps matches taking them all, on herds the recorded runs never saw`() {
        val seeds = 1_000L..1_060L
        assertEquals(
            EngineGoldenScenarios(herdUpdate = everyHerdStep).herdCatchUp(seeds),
            EngineGoldenScenarios().herdCatchUp(seeds),
        )
    }

    @Test
    fun `reindexing after every tree change reproduces the recorded forests`() {
        assertEquals(EngineGolden.TREE_GROWTH, EngineGoldenScenarios(treeGrowth = reindexingTreeGrowth).treeGrowth())
    }

    @Test
    fun `growing trees without reindexing matches reindexing, on forests the recorded runs never saw`() {
        val seeds = 1_000L..1_200L
        assertEquals(
            EngineGoldenScenarios(treeGrowth = reindexingTreeGrowth).treeGrowth(seeds),
            EngineGoldenScenarios().treeGrowth(seeds),
        )
    }

    /** Always the same number in 0 until 1, to settle a roll. */
    private fun rolling(unit: Double) = RandomSource { ((unit * (1L shl 53)).toLong() shl 11) }

    @Test
    fun `chickens already moved in a walk still crowd the tile for the next`() {
        val coop = GridPoint(30, 10)
        val state = GameState()
        val engine = GameEngine(state)
        val now = LateFarm.start
        engine.setTimeOfDay(LateFarm.MORNING, now)
        state.putBuilding(coop, "chickenCoop")
        val hen = ChickenVariant.all[0]
        val old = now.advancedBy(-86_400.0)
        val mover = Chicken.hatched(0, hen, Chicken.DEFAULT_NAME, GridPoint(28, 30), old)
        val staying = Chicken.hatched(1, hen, Chicken.DEFAULT_NAME, GridPoint(30, 30), old)
        state.coops[coop] = Coop(chickens = listOf(mover, staying), nextChickAt = now, nextChickenID = 2)
        val walk = engine.animalWalk(now)
        val second = AnimalRef.Chicken(coop, 1)
        val up = GridPoint(-1, 0)
        val down = GridPoint(1, 0)
        assertEquals(up, engine.wanderDirection(second, rolling(0.2), walk))
        engine.move(AnimalRef.Chicken(coop, 0), down)
        assertEquals(GridPoint(29, 30), engine.position(AnimalRef.Chicken(coop, 0)))
        assertEquals(down, engine.wanderDirection(second, rolling(0.2), walk))
        assertEquals(down, engine.wanderDirection(second, now, rolling(0.2)))
    }

    @Test
    fun `an animal that moved in a walk searches from where it now stands`() {
        val state = GameState()
        val engine = GameEngine(state)
        val now = LateFarm.start
        engine.setTimeOfDay(LateFarm.MORNING, now)
        val barn = GridPoint(5, 5)
        state.putBuilding(barn, "barn2")
        val cow = LivestockSpecies.cow
        val crop = Crop.named("zucchini")!!
        state.farmland.add(GridPoint(20, 20))
        state.plots[GridPoint(20, 20)] = Plot(crop.id, now.advancedBy(-crop.growDuration * 2))
        val animal = Livestock.born(0, cow.variants[0], cow.name, GridPoint(20, 17), now.advancedBy(-cow.growDuration))
        state.herds[barn] = Herd(listOf(animal), now.advancedBy(1_000.0), now, 1)
        val ref = AnimalRef.Livestock(barn, 0)
        val walk = engine.animalWalk(now)
        val right = GridPoint(0, 1)
        assertEquals(right, engine.directionToFavoriteCrop(ref, walk))
        engine.move(ref, right)
        assertEquals(right, engine.directionToFavoriteCrop(ref, walk))
        engine.move(ref, right)
        assertEquals(GridPoint(20, 19), engine.position(ref))
        assertNull(engine.directionToFavoriteCrop(ref, walk))
    }

    @Test
    fun `a crop eaten between walks is gone from the next walk's food`() {
        val state = GameState()
        val engine = GameEngine(state)
        val now = LateFarm.start
        engine.setTimeOfDay(LateFarm.MORNING, now)
        val barn = GridPoint(5, 5)
        state.putBuilding(barn, "barn2")
        val cow = LivestockSpecies.cow
        val crop = Crop.named("zucchini")!!
        state.farmland.add(GridPoint(20, 25))
        state.plots[GridPoint(20, 25)] = Plot(crop.id, now.advancedBy(-crop.growDuration * 2))
        val animal = Livestock.born(0, cow.variants[0], cow.name, GridPoint(20, 17), now.advancedBy(-cow.growDuration))
        state.herds[barn] = Herd(listOf(animal), now.advancedBy(1_000.0), now, 1)
        val ref = AnimalRef.Livestock(barn, 0)
        assertEquals(GridPoint(0, 1), engine.directionToFavoriteCrop(ref, engine.animalWalk(now)))
        state.plots.remove(GridPoint(20, 25))
        assertNull(engine.directionToFavoriteCrop(ref, engine.animalWalk(now)))
    }

    @Test
    fun `the water layout is reused until the water differs, however it comes to differ`() {
        val cache = WaterLayoutCache()
        val water = mutableMapOf(GridPoint(1, 1) to WaterStyle.deep)
        val first = cache.layout(water)
        assertSame(first, cache.layout(water))
        assertSame(first, cache.layout(water.toMap()))
        water[GridPoint(1, 2)] = WaterStyle.deep
        val second = cache.layout(water)
        assertNotSame(first, second)
        assertEquals(2, second.bodies[0].tiles.size)
        // The cache keeps its own copy: it must see this change to the same map, and not be changed by it.
        water.remove(GridPoint(1, 2))
        val third = cache.layout(water)
        assertEquals(1, third.bodies[0].tiles.size)
        assertEquals(2, second.bodies[0].tiles.size)
    }

    @Test
    fun `a tree growing behind another leaves the shared tile to the one in front, as a full index does`() {
        val state = GameState()
        val engine = GameEngine(state)
        val now = LateFarm.start
        val oak = Tree.all.first { it.side == 3 }.species
        val behind = GridPoint(10, 10)
        val front = GridPoint(11, 11)
        state.putTree(behind, PlacedTree(oak.trees[1], TreeColor.green, now.advancedBy(-Tree.growthInterval)))
        state.putTree(front, PlacedTree(oak.trees[2], TreeColor.green, now))
        assertEquals(front, state.tree(front)?.first)
        assertEquals(true, engine.growTrees(now))
        assertEquals(oak.trees[2], state.trees.getValue(behind).tree)
        assertEquals(front, state.tree(front)?.first)
        val fullyIndexed = state.copy()
        for (row in 8..14) {
            for (col in 8..14) {
                val tile = GridPoint(row, col)
                assertEquals(fullyIndexed.tree(tile)?.first, state.tree(tile)?.first)
                assertEquals(fullyIndexed.hasObstacle(tile), state.hasObstacle(tile))
            }
        }
    }

    /** What lets growing a tree leave nothing to take out of the indexes: it only ever gains ground. */
    @Test
    fun `every tree's next size stands on all the ground of the last, and its bottom row on all of the last's`() {
        val at = GridPoint(20, 20)
        for (tree in Tree.all) {
            val next = tree.next ?: continue
            val nextGround = next.footprint(next.origin(tree.trunk(at)))
            val ground = tree.footprint(at)
            assertEquals(true, nextGround.containsAll(ground), tree.id)
            assertEquals(true, nextGround.bottomRow.containsAll(ground.bottomRow), tree.id)
        }
    }
}
