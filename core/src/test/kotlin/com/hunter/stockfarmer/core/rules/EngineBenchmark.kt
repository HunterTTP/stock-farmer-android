package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextUnit
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * What the costly rules take on [LateFarm], by plain wall clock on this JVM. Not part of `test`: run
 * `gradlew :core:engineBenchmark --console=plain --max-workers=1`. The numbers say nothing about a phone under ART, only how
 * one version of the rules compares with another on the same machine.
 */
@Tag("benchmark")
class EngineBenchmark {
    private var sink = 0L

    /** Walks every animal once, as the game's tick does. */
    private fun walk(
        engine: GameEngine,
        now: Instant,
        random: RandomSource,
    ) {
        val walk = engine.animalWalk(now)
        for (animal in engine.animals) {
            if (random.nextUnit() > STEP_CHANCE) continue
            engine.wanderDirection(animal, random, walk)?.let { engine.move(animal, it) }
        }
    }

    private fun report(
        name: String,
        warmup: Int,
        runs: Int,
        run: () -> Long,
    ) {
        repeat(warmup) { sink += run() }
        val times = (0 until runs).map { run() }.sorted()
        val median = times[times.size / 2]
        println(
            "BENCH %-44s median %10.3f ms   mean %10.3f ms   min %10.3f ms   max %10.3f ms   runs %d".format(
                name,
                median / MILLISECOND,
                times.average() / MILLISECOND,
                times.first() / MILLISECOND,
                times.last() / MILLISECOND,
                runs,
            ),
        )
    }

    private fun timeWalks(
        name: String,
        engine: GameEngine,
        ticks: Int = 60,
    ) {
        val random = CountingRandom(21)
        var now = LateFarm.start
        report(name, ticks, ticks) {
            now = now.advancedBy(0.01)
            val started = System.nanoTime()
            walk(engine, now, random)
            System.nanoTime() - started
        }
    }

    /** Night falls with every animal out on the farm, always from the same spots: they soon get home and stop searching. */
    private fun timeFirstWalkHome() {
        val farm = LateFarm.build(minute = 23.0 * 60)
        report("walk: night, everyone setting off home", 40, 60) {
            val copy = farm.copy()
            val random = CountingRandom(23)
            val started = System.nanoTime()
            walk(copy, LateFarm.start, random)
            System.nanoTime() - started
        }
    }

    /** A copy of the farm with the herds last brought up to date this long ago, half the animals having just eaten. */
    private fun awayFor(
        seconds: Double,
        withMeals: Boolean,
    ): GameEngine {
        val back = LateFarm.start.advancedBy(-seconds)
        val engine = LateFarm.build(now = back)
        if (!withMeals) return engine
        for (animal in engine.animals.filterIsInstance<AnimalRef.Livestock>().filter { it.id % 2 == 0 }) {
            engine.updateLivestock(animal.home, animal.id) { it.copy(meal = AnimalMeal(back)) }
        }
        return engine
    }

    private fun timeCatchUp(
        name: String,
        seconds: Double,
        withMeals: Boolean,
        warmup: Int,
        runs: Int,
    ) {
        val farm = awayFor(seconds, withMeals)
        report(name, warmup, runs) {
            val copy = farm.copy()
            val random = CountingRandom(22)
            val started = System.nanoTime()
            copy.updateHerds(LateFarm.start, random)
            val took = System.nanoTime() - started
            sink += random.draws
            took
        }
    }

    private fun timeTrees(
        name: String,
        grownSteps: Int,
    ) {
        val farm = LateFarm.build()
        val state = farm.state
        val longAgo = LateFarm.start.advancedBy(-Tree.growthInterval * grownSteps)
        state.replaceTrees(state.trees.mapValues { (_, tree) -> PlacedTree(tree.treeID, tree.colorID, longAgo) })
        report(name, 20, 40) {
            val copy = farm.copy()
            val started = System.nanoTime()
            val grew = copy.growTrees(LateFarm.start)
            val took = System.nanoTime() - started
            if (grew) sink += 1
            took
        }
    }

    @Test
    fun `report what the rules take on the late farm`() {
        timeWalks("walk: hungry, food fenced off (worst)", LateFarm.build())
        timeWalks(
            "walk: hungry, gate open (food reachable)",
            LateFarm.build().also { it.state.fences.remove(LateFarm.gate) },
        )
        timeWalks("walk: everyone fed (no search)", fedFarm())
        timeWalks("walk: hungry + Tractor driving", drivingFarm())
        timeFirstWalkHome()
        timeCatchUp("herd catch-up: 8 h, no meals", 8 * HOUR, withMeals = false, warmup = 10, runs = 15)
        timeCatchUp("herd catch-up: 8 h, half just ate", 8 * HOUR, withMeals = true, warmup = 10, runs = 15)
        timeCatchUp("herd catch-up: 3 days, no meals", 72 * HOUR, withMeals = false, warmup = 5, runs = 9)
        timeCatchUp("herd catch-up: 3 days, half just ate", 72 * HOUR, withMeals = true, warmup = 5, runs = 9)
        timeTrees("growTrees: 200 due, one size each", 1)
        timeTrees("growTrees: 200 due, every size each", 3)
        timeWaterLayout()
        println("BENCH (ignore) $sink")
    }

    private fun fedFarm(): GameEngine {
        val engine = LateFarm.build()
        for (animal in engine.animals) engine.giveMeal(animal, LateFarm.start)
        return engine
    }

    private fun drivingFarm(): GameEngine {
        val engine = LateFarm.build()
        engine.state.tractorRun =
            TractorRun(GridPoint(0, 0), "wheat", GridPoint(10, 3), GridPoint(10, 4), facing = TractorFacing.right)
        return engine
    }

    private fun timeWaterLayout() {
        val state = LateFarm.build().state
        report("waterLayout: 15 lookups, as in a tick", 2_000, 2_000) {
            val started = System.nanoTime()
            repeat(15) { sink += state.waterLayout.bodies.size }
            System.nanoTime() - started
        }
    }

    private companion object {
        const val MILLISECOND = 1e6
        const val HOUR = 3_600.0
    }
}
