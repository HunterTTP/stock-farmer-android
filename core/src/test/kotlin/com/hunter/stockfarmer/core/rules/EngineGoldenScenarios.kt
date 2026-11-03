package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.nextUnit
import java.security.MessageDigest
import java.time.Instant

/** A seeded generator that also counts its draws, so a change in how many numbers a rule takes shows up. */
internal class CountingRandom(
    seed: Long,
) : RandomSource {
    private val source = SeededRandom(seed)
    var draws = 0L
        private set

    override fun nextLong(): Long {
        draws += 1
        return source.nextLong()
    }
}

/** One step for every animal that sets off, in the order the game walks them. */
internal typealias Walker = (GameEngine, Instant, RandomSource) -> Unit

internal typealias HerdUpdate = (GameEngine, Instant, RandomSource) -> List<GameEvent>

internal typealias TreeGrowth = (GameEngine, Instant) -> Boolean

/** The walk through the rules' public step, one animal at a time. */
internal val walkEachAnimal: Walker = { engine, now, random ->
    for (animal in engine.animals) {
        if (random.nextUnit() > STEP_CHANCE) continue
        engine.wanderDirection(animal, now, random)?.let { engine.move(animal, it) }
    }
}

internal const val STEP_CHANCE = 0.6

/**
 * Scenarios whose every step goes into a digest of the whole saved state, the events and the random draws. Their expected
 * digests were recorded from the rules as they stood before any of the performance work they guard, so any change in where
 * an animal walks, what is born, when or how many numbers are drawn fails them.
 */
internal class EngineGoldenScenarios(
    private val walker: Walker = walkEachAnimal,
    private val herdUpdate: HerdUpdate = { engine, now, random -> engine.updateHerds(now, random) },
    private val treeGrowth: TreeGrowth = { engine, now -> engine.growTrees(now) },
) {
    private class Recorder(
        seed: Long,
    ) {
        val random = CountingRandom(seed)
        private val digest = MessageDigest.getInstance("SHA-256")
        private var combinedDraws = 0L

        fun record(text: String) = digest.update(text.toByteArray())

        fun record(
            engine: GameEngine,
            events: List<GameEvent> = emptyList(),
        ) {
            record(GameStateJson.encode(engine.state))
            record(events.toString())
        }

        val result: String
            get() = digest.digest().joinToString("") { "%02x".format(it) }.take(24) + ":" + random.draws
    }

    private fun tick(
        engine: GameEngine,
        now: Instant,
        interval: Double,
        random: RandomSource,
    ): List<GameEvent> {
        val events = (engine.updateCoops(now, random) + herdUpdate(engine, now, random)).toMutableList()
        engine.layEggs(interval, now, random)
        engine.updateFish(now, emptySet(), random)
        for ((house, pet) in engine.state.pets.entries
            .toList()) {
            if (engine.state.plots[pet.position] != null) {
                events += engine.petTendsCrop(house, 5.0, interval, now, random)
            }
        }
        events += engine.eatFavoriteCrops(now)
        treeGrowth(engine, now)
        walker(engine, now, random)
        return events
    }

    private fun runTicks(
        engine: GameEngine,
        recorder: Recorder,
        from: Instant,
        ticks: Int,
        beforeEachTick: (Int, Instant) -> Unit = { _, _ -> },
    ): Instant {
        var now = from
        repeat(ticks) { index ->
            now = now.advancedBy(1.0)
            beforeEachTick(index, now)
            recorder.record(engine, tick(engine, now, 1.0, recorder.random))
        }
        return now
    }

    /** Every eater shut out of a ripe field: each searches all it can reach, finds no way, and wanders. */
    fun foodFencedOff(): String {
        val recorder = Recorder(11)
        runTicks(LateFarm.build(), recorder, LateFarm.start, 20)
        return recorder.result
    }

    /** A gate in the fence: animals find their way in, eat, and the food the others are after changes tick by tick. */
    fun foodBehindAGate(): String {
        val recorder = Recorder(12)
        val engine = LateFarm.build()
        engine.state.fences.remove(LateFarm.gate)
        runTicks(engine, recorder, LateFarm.start, 90)
        return recorder.result
    }

    /** The rain and then the night send everyone home over open ground, and let them out again. */
    fun shelterFromRainAndNight(): String {
        val recorder = Recorder(13)
        val engine = LateFarm.build(minute = 13.0 * 60 + 55)
        engine.state.fences.remove(LateFarm.gate)
        val later = runTicks(engine, recorder, LateFarm.start, 40)
        engine.setTimeOfDay(19.0 * 60 + 55, later)
        val night = runTicks(engine, recorder, later, 40)
        engine.setTimeOfDay(4.0 * 60 + 58, night)
        runTicks(engine, recorder, night, 20)
        return recorder.result
    }

    /** The Tractor driving through the animals, who keep away and may not step on the tiles it is taking up. */
    fun tractorThroughTheAnimals(): String {
        val recorder = Recorder(14)
        val engine = LateFarm.build()
        runTicks(engine, recorder, LateFarm.start, 40) { index, _ ->
            engine.state.tractorRun =
                TractorRun(
                    GridPoint(0, 0),
                    "wheat",
                    GridPoint(8, 2 + index),
                    GridPoint(8, 3 + index),
                    facing = TractorFacing.right,
                )
        }
        return recorder.result
    }

    /** Animals where no animal should be: in buildings, on water and fences, boxed in, and off the map beside the food. */
    fun strandedAnimals(): String {
        val recorder = Recorder(15)
        for (minute in listOf(8.0 * 60, 15.0 * 60, 22.0 * 60)) {
            val engine = strandedFarm(LateFarm.start, minute)
            runTicks(engine, recorder, LateFarm.start, 40)
        }
        return recorder.result
    }

    private fun strandedFarm(
        now: Instant,
        minute: Double,
    ): GameEngine {
        val state = GameState()
        val engine = GameEngine(state)
        engine.setTimeOfDay(minute, now)
        engine.bonusRoll = { 0.5 }
        val old = now.advancedBy(-2_592_000.0)
        val cowCrop = "zucchini"
        for (tile in listOf(GridPoint(0, 5), GridPoint(49, 40), GridPoint(20, 0), GridPoint(30, 30))) {
            state.farmland.add(tile)
            state.plots[tile] = Plot(cowCrop, old)
        }
        val barn = GridPoint(10, 10)
        state.putBuilding(barn, "barn2")
        val blocked = GridPoint(20, 20)
        state.putBuilding(blocked, "barn1")
        val cow = LivestockSpecies.cow
        val spots =
            listOf(
                GridPoint(12, 12),
                GridPoint(-1, 5),
                GridPoint(50, 40),
                GridPoint(20, -1),
                GridPoint(-40, -40),
                GridPoint(35, 35),
                GridPoint(36, 20),
            )
        state.herds[barn] =
            Herd(
                spots.mapIndexed { id, spot -> Livestock.born(id, cow.variants[0], cow.name, spot, old) },
                now.advancedBy(30.0),
                now,
                spots.size,
            )
        for (row in 34..36) {
            for (col in 34..36) if (row != 35 || col != 35) state.fences[GridPoint(row, col)] = FenceColor.starter.id
        }
        for (row in 35..37) {
            for (col in 19..21) {
                if (row != 36 ||
                    col != 20
                ) {
                    state.water[GridPoint(row, col)] = WaterStyle.deep
                }
            }
        }
        val front = Building.named("barn1")!!.frontTile(blocked)
        state.fences[front] = FenceColor.starter.id
        state.herds[blocked] = Herd(nextBirthAt = now.advancedBy(10.0), processedAt = now)
        val house = GridPoint(5, 30)
        state.putBuilding(house, "petHouse")
        state.pets[house] = Pet(PetSpecies.cat, Cat.starter.id, "Pet", GridPoint(36, 20))
        val coop = GridPoint(5, 40)
        state.putBuilding(coop, "chickenCoop")
        val hen = ChickenVariant.all[0]
        val hens =
            listOf(GridPoint(34, 34), GridPoint(6, 40), GridPoint(7, 40), GridPoint(7, 40)).mapIndexed { id, spot ->
                Chicken.hatched(id, hen, Chicken.DEFAULT_NAME, spot, old)
            }
        state.coops[coop] = Coop(chickens = hens, nextChickAt = now.advancedBy(60.0), nextChickenID = hens.size)
        return engine
    }

    /** Farms of every shape, with the player changing them as they run. */
    fun randomFarms(seeds: LongRange = 1L..30L): String {
        val recorder = Recorder(16)
        for (seed in seeds) {
            val start = LateFarm.start.advancedBy(seed * 1_000.0)
            val shape = SeededRandom(seed * 7919)
            val engine = randomFarm(shape, start)
            var now = start
            repeat(40) {
                val interval = 0.25 + shape.nextUnit() * 1.75
                now = now.advancedBy(interval)
                if (shape.nextUnit() < 0.3) changeFarm(engine, shape, now)
                recorder.record(engine, tick(engine, now, interval, recorder.random))
            }
        }
        return recorder.result
    }

    private fun randomFarm(
        shape: SeededRandom,
        now: Instant,
    ): GameEngine {
        val state = GameState()
        val engine = GameEngine(state)
        engine.setTimeOfDay(shape.nextInt(0..1439).toDouble(), now)
        engine.bonusRoll = { 0.5 }
        repeat(shape.nextInt(0..4)) { rectangle(shape, 3..12).forEach { state.water[it] = WaterStyle.deep } }
        repeat(shape.nextInt(1..5)) {
            for (tile in rectangle(shape, 2..9)) {
                if (tile in state.water) continue
                state.farmland.add(tile)
                val crop = Crop.named(eaten.random(shape))!!
                state.plots[tile] =
                    Plot(
                        crop.id,
                        now.advancedBy(-crop.growDuration * shape.nextUnit() * 1.5),
                        servingsEaten =
                            shape.nextInt(0..3),
                    )
                if (shape.nextUnit() < 0.2) state.fertilized.add(tile)
            }
        }
        repeat(shape.nextInt(0..8)) {
            val area = rectangle(shape, 2..10)
            val top = area.minOf { it.row } - 1
            val bottom = area.maxOf { it.row } + 1
            val left = area.minOf { it.col } - 1
            val right = area.maxOf { it.col } + 1
            for (row in top..bottom) {
                for (col in left..right) {
                    if (row == top || row == bottom || col == left || col == right) {
                        if (shape.nextUnit() < 0.9) state.fences[GridPoint(row, col)] = FenceColor.starter.id
                    }
                }
            }
        }
        repeat(shape.nextInt(0..40)) {
            state.putTree(
                spot(shape),
                PlacedTree(
                    Tree.all.random(shape),
                    TreeColor.green,
                    now.advancedBy(-Tree.growthInterval * shape.nextUnit() * 3),
                ),
            )
        }
        repeat(shape.nextInt(0..20)) { state.rocks[spot(shape)] = RockSize.entries.first() }
        repeat(shape.nextInt(0..4)) {
            state.putDecoration(spot(shape), PlacedDecoration(if (shape.nextUnit() < 0.5) "scarecrow" else "sprinkler"))
        }
        repeat(shape.nextInt(0..4)) { randomHerd(state, shape, now) }
        repeat(shape.nextInt(0..2)) { randomCoop(state, shape, now) }
        repeat(shape.nextInt(0..2)) {
            val house = spot(shape)
            state.putBuilding(house, "petHouse")
            state.pets[house] = Pet(PetSpecies.cat, Cat.starter.id, "Pet", wherever(shape))
        }
        if (shape.nextUnit() < 0.25) {
            state.tractorRun =
                TractorRun(GridPoint(0, 0), "wheat", spot(shape), facing = TractorFacing.entries.random(shape))
        }
        return engine
    }

    private fun randomHerd(
        state: GameState,
        shape: SeededRandom,
        now: Instant,
    ) {
        val home = LivestockHome.all.random(shape)
        val building = Building.named(home.buildingID)!!
        val origin = building.clampedOrigin(spot(shape))
        state.putBuilding(origin, home.buildingID)
        if (shape.nextUnit() < 0.5) state.homeLevels[origin] = shape.nextInt(1..3)
        val animals =
            (0 until shape.nextInt(0..8)).map { id ->
                randomAnimal(id, home.species[0], wherever(shape), shape, now)
            }
        state.herds[origin] =
            Herd(animals, now.advancedBy(shape.nextUnit() * 900.0 - 300.0), processedAtFor(shape, now), animals.size)
    }

    private fun randomAnimal(
        id: Int,
        species: LivestockSpecies,
        position: GridPoint,
        shape: SeededRandom,
        now: Instant,
    ): Livestock {
        val born = now.advancedBy(-species.growDuration * shape.nextUnit() * 2)
        val baby = Livestock.born(id, species.variants.random(shape), species.name, position, born)
        val meal = randomMeal(shape, now)
        return when {
            shape.nextUnit() < 0.3 -> {
                val worked = now.advancedBy(-shape.nextUnit() * 900.0)
                baby.copy(meal = meal, lastWorkedAt = worked, readyAt = worked.advancedBy(species.workInterval))
            }

            else -> {
                baby.copy(meal = meal)
            }
        }
    }

    /** Often no meal; else one long over, underway, or, as a hand-fed meal after the herd was last brought up to date, still ahead. */
    private fun randomMeal(
        shape: SeededRandom,
        now: Instant,
    ): AnimalMeal? =
        when (shape.nextInt(0..4)) {
            0, 1 -> null
            2 -> AnimalMeal(now.advancedBy(-shape.nextUnit() * 90_000.0))
            3 -> AnimalMeal(now.advancedBy(-shape.nextUnit() * AnimalMeal.DURATION))
            else -> AnimalMeal(now.advancedBy(shape.nextUnit() * 600.0))
        }

    private fun processedAtFor(
        shape: SeededRandom,
        now: Instant,
    ): Instant? =
        when (shape.nextInt(0..4)) {
            0 -> null
            1 -> now.advancedBy(shape.nextUnit() * 20.0)
            else -> now.advancedBy(-shape.nextUnit() * 20.0)
        }

    private fun randomCoop(
        state: GameState,
        shape: SeededRandom,
        now: Instant,
    ) {
        val origin = spot(shape)
        state.putBuilding(origin, "chickenCoop")
        val chickens =
            (0 until shape.nextInt(0..6)).map { id ->
                val hatched = now.advancedBy(-shape.nextUnit() * Chicken.GROW_DURATION * 2)
                Chicken
                    .hatched(id, ChickenVariant.all.random(shape), Chicken.DEFAULT_NAME, nearby(origin, shape), hatched)
                    .copy(meal = randomMeal(shape, now))
            }
        state.coops[origin] =
            Coop(
                chickens = chickens,
                nextChickAt = now.advancedBy(shape.nextUnit() * 600.0),
                nextChickenID = chickens.size,
            )
        for (hen in chickens) {
            if (shape.nextUnit() < 0.2) state.eggs = state.eggs + Egg(hen.position, 0.0, 0.0, origin, hen.id)
        }
    }

    private fun changeFarm(
        engine: GameEngine,
        shape: SeededRandom,
        now: Instant,
    ) {
        val state = engine.state
        when (shape.nextInt(0..7)) {
            0 -> {
                state.fences.keys
                    .toList()
                    .randomOrNull(shape)
                    ?.let { state.fences.remove(it) }
            }

            1 -> {
                state.fences[spot(shape)] = FenceColor.starter.id
            }

            2 -> {
                state.plots.keys
                    .toList()
                    .randomOrNull(shape)
                    ?.let { state.plots.remove(it) }
            }

            3 -> {
                val tile = spot(shape)
                state.farmland.add(tile)
                val crop = Crop.named(eaten.random(shape))!!
                state.plots[tile] = Plot(crop.id, now.advancedBy(-crop.growDuration))
            }

            4 -> {
                engine.animals.randomOrNull(shape)?.let { engine.giveMeal(it, now) }
            }

            5 -> {
                state.herds.keys
                    .toList()
                    .randomOrNull(shape)
                    ?.let { state.homeLevels[it] = shape.nextInt(1..3) }
            }

            6 -> {
                val tile = spot(shape)
                if (state.water.remove(tile) == null) state.water[tile] = WaterStyle.deep
            }

            else -> {
                engine.animals
                    .filterIsInstance<AnimalRef.Livestock>()
                    .randomOrNull(shape)
                    ?.let { engine.releaseLivestock(it.id, it.home) }
            }
        }
    }

    /** Herds brought up to date over every length of time, from no time to longer than the cap, as the farm changes in between. */
    fun herdCatchUp(seeds: LongRange = 1L..80L): String {
        val recorder = Recorder(17)
        for (seed in seeds) {
            val shape = SeededRandom(seed * 6151)
            var now = LateFarm.start.advancedBy(seed * 100.0)
            val engine = herdFarm(shape, now)
            repeat(shape.nextInt(1..4)) {
                now = now.advancedBy(elapsed(shape))
                recorder.record(engine, herdUpdate(engine, now, recorder.random))
                changeHerds(engine, shape, now)
                recorder.record(engine)
            }
        }
        return recorder.result
    }

    private fun herdFarm(
        shape: SeededRandom,
        now: Instant,
    ): GameEngine {
        val state = GameState()
        val engine = GameEngine(state)
        engine.setTimeOfDay(8.0 * 60, now)
        repeat(shape.nextInt(0..3)) {
            for (tile in rectangle(shape, 3..12)) state.water[tile] = WaterStyle.deep
        }
        repeat(shape.nextInt(1..4)) { randomHerd(state, shape, now) }
        if (shape.nextUnit() < 0.5) {
            // A home with nowhere for a baby to appear: every tile it might choose is taken.
            state.herds.keys.firstOrNull()?.let { home ->
                val front = state.building(home)?.let { it.building.frontTile(it.origin) } ?: return@let
                for (row in -3..3) {
                    for (col in -3..3) state.fences[GridPoint(front.row + row, front.col + col)] = FenceColor.starter.id
                }
            }
        }
        for (origin in state.waterLayout.bodies
            .mapNotNull { it.interior.firstOrNull() }
            .take(2)) {
            val duck = LivestockSpecies.duck
            val ducks =
                (0 until shape.nextInt(0..2)).map { id ->
                    Livestock.born(id, duck.variants[0], duck.name, origin, now.advancedBy(-duck.growDuration))
                }
            state.herds[origin] = Herd(ducks, now.advancedBy(shape.nextUnit() * 3_600.0 - 900.0), now, ducks.size)
        }
        return engine
    }

    private fun elapsed(shape: SeededRandom): Double =
        when (shape.nextInt(0..11)) {
            0 -> 0.0
            1 -> 1.0
            2 -> 3.700001
            3 -> 5.0
            4 -> 7.123456
            5 -> 299.9
            6 -> 600.0 * shape.nextUnit()
            7 -> 3_600.0 * shape.nextUnit()
            8 -> 28_800.0 * shape.nextUnit()
            9 -> 28_800.0
            10 -> 90_000.0 * shape.nextUnit()
            else -> if (shape.nextUnit() < 0.5) 259_200.0 else 300_000.0
        }

    private fun changeHerds(
        engine: GameEngine,
        shape: SeededRandom,
        now: Instant,
    ) {
        val state = engine.state
        val animals = engine.animals.filterIsInstance<AnimalRef.Livestock>()
        when (shape.nextInt(0..6)) {
            0 -> {
                animals.randomOrNull(shape)?.let { engine.giveMeal(it, now) }
            }

            1 -> {
                animals.randomOrNull(shape)?.let { engine.releaseLivestock(it.id, it.home) }
            }

            2 -> {
                state.herds.keys
                    .toList()
                    .randomOrNull(shape)
                    ?.let { state.homeLevels[it] = shape.nextInt(1..3) }
            }

            3 -> {
                animals.randomOrNull(shape)?.let { ref ->
                    engine.updateLivestock(ref.home, ref.id) {
                        it.copy(lastWorkedAt = now, readyAt = now.advancedBy(it.species.workInterval))
                    }
                }
            }

            4 -> {
                state.water.keys
                    .toList()
                    .take(shape.nextInt(0..20))
                    .forEach { state.water.remove(it) }
            }

            5 -> {
                state.fences.keys
                    .toList()
                    .take(shape.nextInt(0..20))
                    .forEach { state.fences.remove(it) }
            }

            else -> {
                animals.randomOrNull(shape)?.let { engine.giveMeal(it, now.advancedBy(shape.nextUnit() * 120.0)) }
            }
        }
    }

    /** Crowded, overlapping forests of every species growing over the same ground, with who covers what compared after each. */
    fun treeGrowth(seeds: LongRange = 1L..60L): String {
        val recorder = Recorder(18)
        for (seed in seeds) {
            val shape = SeededRandom(seed * 4099)
            var now = LateFarm.start
            val engine = forest(shape, now)
            repeat(3) {
                now = now.advancedBy(Tree.growthInterval * shape.nextUnit() * 2)
                recorder.record(treeGrowth(engine, now).toString())
                recorder.record(engine)
                recorder.record(treeIndex(engine.state))
            }
        }
        return recorder.result
    }

    private fun forest(
        shape: SeededRandom,
        now: Instant,
    ): GameEngine {
        val state = GameState()
        val engine = GameEngine(state)
        val top = shape.nextInt(0..30)
        val left = shape.nextInt(0..30)
        val size = shape.nextInt(4..18)

        fun inForest() = GridPoint(top + shape.nextInt(-1..size), left + shape.nextInt(0..size))
        repeat(shape.nextInt(2..size * size / 2)) {
            state.putTree(
                inForest(),
                PlacedTree(
                    Tree.all.random(shape),
                    TreeColor.green,
                    now.advancedBy(-Tree.growthInterval * shape.nextUnit() * 4),
                    shape.nextUnit() < 0.7,
                ),
            )
        }
        repeat(shape.nextInt(0..6)) { state.rocks[inForest()] = RockSize.entries.first() }
        repeat(shape.nextInt(0..6)) { state.fences[inForest()] = FenceColor.starter.id }
        repeat(shape.nextInt(0..6)) { state.water[inForest()] = WaterStyle.deep }
        repeat(shape.nextInt(0..6)) { state.farmland.add(inForest()) }
        repeat(shape.nextInt(0..2)) { state.putDecoration(inForest(), PlacedDecoration("scarecrow")) }
        repeat(shape.nextInt(0..2)) {
            val house = inForest()
            state.putBuilding(house, "petHouse")
            state.pets[house] = Pet(PetSpecies.cat, Cat.starter.id, "Pet", inForest())
        }
        if (shape.nextUnit() < 0.3) {
            state.trees.entries
                .toList()
                .randomOrNull(shape)
                ?.let { (origin, placed) ->
                    engine.objectPlacement = ObjectPlacement(ObjectPlacement.Object.OfTree(placed), origin, origin)
                }
        }
        return engine
    }

    /** Which tree a full index says stands on each tile, on the map and just off it, whose ties depend on map order. */
    private fun treeIndex(state: GameState): String =
        buildString {
            for (row in -3..GameState.GRID_SIZE + 2) {
                for (col in -3..GameState.GRID_SIZE + 2) {
                    val tile = GridPoint(row, col)
                    val covering = state.tree(tile)?.first
                    val solid = state.hasSolidObstacle(tile)
                    if (covering != null || solid) append("$row,$col=$covering/$solid;")
                }
            }
        }

    /** The water changing under a layout that is asked for each time: never stale, whether the change is in place or undone. */
    fun waterLayouts(seeds: LongRange = 1L..20L): String {
        val recorder = Recorder(19)
        for (seed in seeds) {
            val shape = SeededRandom(seed * 3571)
            val state = GameState()
            rectangle(shape, 3..12).forEach { state.water[it] = WaterStyle.deep }
            repeat(40) {
                val tile = GridPoint(shape.nextInt(0..49), shape.nextInt(0..49))
                when (shape.nextInt(0..2)) {
                    0 -> {
                        state.water[tile] = WaterStyle.deep
                    }

                    1 -> {
                        state.water.keys
                            .toList()
                            .randomOrNull(shape)
                            ?.let { state.water.remove(it) }
                    }

                    else -> {
                        Unit
                    }
                }
                val layout = state.waterLayout
                recorder.record(
                    layout.bodies.joinToString("|") { "${it.tiles}/${it.interior}" } +
                        layout.bodyIndexByTile.entries
                            .map { "${it.key}${it.value}" }
                            .sorted() +
                        layout.interior.map { it.toString() }.sorted(),
                )
            }
        }
        return recorder.result
    }

    private fun rectangle(
        shape: SeededRandom,
        sides: IntRange,
    ): List<GridPoint> {
        val origin = GridPoint(shape.nextInt(1..38), shape.nextInt(0..38))
        return origin.rectangle(shape.nextInt(sides), shape.nextInt(sides))
    }

    private fun spot(shape: SeededRandom) = GridPoint(shape.nextInt(1..48), shape.nextInt(0..49))

    private fun nearby(
        origin: GridPoint,
        shape: SeededRandom,
    ) = GridPoint(origin.row + shape.nextInt(-2..3), origin.col + shape.nextInt(-2..2))

    /** Usually somewhere on the map, sometimes just off it. */
    private fun wherever(shape: SeededRandom) =
        if (shape.nextUnit() < 0.05) GridPoint(shape.nextInt(-2..51), shape.nextInt(-2..51)) else spot(shape)

    private fun <T> List<T>.random(shape: SeededRandom): T = this[shape.nextInt(indices)]

    private fun <T> List<T>.randomOrNull(shape: SeededRandom): T? = if (isEmpty()) null else random(shape)

    private companion object {
        val eaten = listOf("carrot", "potato", "lettuce", "cabbage", "broccoli", "zucchini", "beetroot", "wheat")
    }
}
