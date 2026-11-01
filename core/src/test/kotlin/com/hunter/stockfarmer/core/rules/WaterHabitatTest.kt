package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextBoolean
import com.hunter.stockfarmer.core.util.nextInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

class WaterHabitatTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val herdRandom = SeededRandom(1)

    private fun water(
        rows: IntRange,
        cols: IntRange,
        engine: GameEngine,
    ) {
        for (row in rows) for (col in cols) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
    }

    private fun ducks(engine: GameEngine) =
        engine.state.herds.values
            .flatMap { it.animals }
            .map { it.position }

    private fun fish(engine: GameEngine) =
        engine.state.fishPonds.values
            .mapNotNull { it.fish }
            .map { it.position }

    private fun distance(
        a: GridPoint,
        b: GridPoint,
    ) = max(abs(a.row - b.row), abs(a.col - b.col))

    private fun step(
        engine: GameEngine,
        time: Instant,
        generator: SeededRandom,
    ) {
        engine.updateHerds(time, herdRandom)
        engine.updateFish(time, using = generator)
    }

    @Test
    fun `a big square lake keeps the spacing the window caps and the map limit`() {
        val engine = GameEngine()
        water(10..35, 10..35, engine)
        val generator = SeededRandom(8)
        engine.updateHerds(now, herdRandom)
        for (index in 0 until 300) step(engine, now.advancedBy(index * 1_800.0), generator)
        val ducks = ducks(engine)
        val fish = fish(engine)
        assertTrue(ducks.size > 5 && fish.size > 5)
        assertTrue(ducks.size <= engine.animalLimitOnTheMap && fish.size <= engine.animalLimitOnTheMap)
        assertTrue(engine.animalLimitOnTheMap <= MOST_OF_A_KIND_ON_THE_MAP)
        for (kind in listOf(ducks, fish)) {
            for ((index, one) in kind.withIndex()) {
                for (other in kind.drop(index + 1)) assertTrue(distance(one, other) >= 3)
            }
        }
        for (duck in ducks) for (one in fish) assertTrue(distance(duck, one) >= 2)
        for (top in 0..GameState.GRID_SIZE - 5) {
            for (left in 0..GameState.GRID_SIZE - 5) {
                val window = { point: GridPoint -> point.row in top until top + 5 && point.col in left until left + 5 }
                assertTrue(ducks.count(window) <= 2 && fish.count(window) <= 2)
            }
        }
    }

    @Test
    fun `a narrow stream still gets fish among its ducks`() {
        val engine = GameEngine()
        water(23..27, 0 until GameState.GRID_SIZE, engine)
        val generator = SeededRandom(2)
        for (index in 0 until 200) step(engine, now.advancedBy(index * 1_800.0), generator)
        assertTrue(ducks(engine).size >= 6 && fish(engine).size >= 5)
    }

    @Test
    fun `a tiny map never holds more than two of each kind`() {
        val engine = GameEngine()
        water(10..13, 10..40, engine)
        val generator = SeededRandom(2)
        for (index in 0 until 100) step(engine, now.advancedBy(index * 1_800.0), generator)
        assertTrue(ducks(engine).size <= engine.animalLimitOnTheMap && fish(engine).size <= engine.animalLimitOnTheMap)
    }

    @Test
    fun `the layout always matches the water after any edits`() {
        val engine = GameEngine()
        val generator = SeededRandom(77)
        for (round in 0 until 200) {
            val point = GridPoint(generator.nextInt(5..12), generator.nextInt(5..12))
            if (generator.nextBoolean()) {
                engine.state.water[point] = WaterStyle.deep
            } else {
                engine.state.water.remove(
                    point,
                )
            }
            if (round % 5 != 0) continue
            val layout = engine.state.waterLayout
            val interior =
                engine.state.water.keys
                    .filter { tile ->
                        (-1..1).all { dr ->
                            (-1..1).all { dc ->
                                GridPoint(tile.row + dr, tile.col + dc) in
                                    engine.state.water
                            }
                        }
                    }.toSet()
            assertEquals(interior, layout.interior)
            assertEquals(engine.state.water.size, layout.bodies.sumOf { it.tiles.size })
            for (body in layout.bodies) {
                assertTrue(body.interior.all { it in interior })
                for (tile in body.tiles) {
                    assertTrue(tile.neighbors.filter { it in engine.state.water }.all { it in body.tiles })
                }
            }
        }
    }

    @Test
    fun `crowded animals are never moved or removed and still swim freely`() {
        val engine = GameEngine()
        water(19..23, 19..23, engine)
        val variant = LivestockSpecies.variant("duck:white")?.second?.id ?: LivestockSpecies.duck.variants[0].id
        val origin = GridPoint(21, 21)
        val spots = listOf(GridPoint(20, 20), GridPoint(20, 21), GridPoint(21, 21), GridPoint(22, 22))
        val animals = spots.mapIndexed { index, spot -> Livestock(index, variant, "x", spot, bornAt = now) }
        engine.state.herds[origin] = Herd(animals, now.advancedBy(1_000_000.0), nextAnimalID = 4)
        val before =
            engine.state.herds[origin]!!
                .animals
                .map { it.position }
        engine.updateHerds(now.advancedBy(86_400.0), herdRandom)
        assertEquals(
            before,
            engine.state.herds[origin]
                ?.animals
                ?.map { it.position },
        )
        assertEquals(
            4,
            engine.state.herds[origin]
                ?.animals
                ?.size,
        )
        val duck = AnimalRef.Livestock(origin, 0)
        assertNotNull(engine.wanderDirection(duck, now, SeededRandom(5)))
        assertEquals(GridPoint(21, 20), engine.move(duck, GridPoint(1, 0)))
    }

    @Test
    fun `a puddle dug anywhere gets one animal at its middle`() {
        val engine = GameEngine()
        water(40..42, 5..7, engine)
        val generator = SeededRandom(11)
        engine.updateHerds(now, herdRandom)
        for (index in 0 until 4) step(engine, now.advancedBy(index * 1_800.0 + 600), generator)
        val all = ducks(engine) + fish(engine)
        assertTrue(all.size == 1 && all.first() == GridPoint(41, 6))
    }
}
