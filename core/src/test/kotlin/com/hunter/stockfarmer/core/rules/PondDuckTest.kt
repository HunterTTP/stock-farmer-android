package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.secondsSince
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

class PondDuckTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val center = GridPoint(20, 20)
    private val random = SeededRandom(1)

    private fun water(
        rows: IntRange,
        cols: IntRange,
        engine: GameEngine,
    ) {
        for (row in rows) for (col in cols) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
    }

    private fun GameEngine.update(at: Instant) = updateHerds(at, random)

    private fun distance(
        a: GridPoint,
        b: GridPoint,
    ) = max(abs(a.row - b.row), abs(a.col - b.col))

    /** A game with a pond of three by three water tiles around the center. */
    private fun engineWithPond() = GameEngine().also { water(19..21, 19..21, it) }

    @Test
    fun `a three by three pond gets its first duckling after ten minutes`() {
        val engine = engineWithPond()
        assertTrue(engine.update(now).isEmpty())
        assertNotNull(engine.state.herds[center])
        assertTrue(engine.update(now.advancedBy(599.0)).isEmpty())
        assertEquals(listOf<GameEvent>(GameEvent.Born(center)), engine.update(now.advancedBy(600.0)))
        val duckling = engine.animals(center)[0]
        assertEquals(LivestockSpecies.duck, duckling.species)
        assertNotNull(engine.state.water[duckling.position])
        assertFalse(duckling.isGrown(now.advancedBy(600.0)))
    }

    @Test
    fun `water that is not three by three attracts no ducks`() {
        val engine = GameEngine()
        water(19..20, 19..25, engine)
        water(19..21, 30..31, engine)
        engine.update(now)
        engine.update(now.advancedBy(24 * 60 * 60.0))
        assertTrue(engine.state.herds.isEmpty())
    }

    @Test
    fun `a three by three pond holds one duck and no more are born`() {
        val engine = engineWithPond()
        engine.update(now)
        for (step in 0 until 20) engine.update(now.advancedBy(600 + step * 1_800.0))
        assertEquals(1, engine.animals(center).size)
        assertTrue(engine.update(now.advancedBy(24 * 60 * 60.0)).isEmpty())
    }

    @Test
    fun `a bigger pond holds more ducks and bears another every half hour spaced apart`() {
        val engine = GameEngine()
        water(19..23, 19..33, engine)
        engine.update(now)
        for (step in 0 until 40) engine.update(now.advancedBy(600 + step * 1_800.0))
        val ducks =
            engine.state.herds.values
                .flatMap { it.animals }
        assertEquals(2, ducks.size)
        assertEquals(1_800.0, ducks[1].bornAt.secondsSince(ducks[0].bornAt))
        for (duck in ducks) {
            assertTrue(duck.position in engine.state.waterLayout.interior)
            for (other in ducks.filter { it.id != duck.id }) assertTrue(distance(duck.position, other.position) >= 3)
        }
    }

    @Test
    fun `one big pond of several blocks has one group of ducks`() {
        val engine = GameEngine()
        water(19..21, 19..30, engine)
        engine.update(now)
        engine.update(now.advancedBy(24 * 60 * 60.0))
        assertEquals(1, engine.state.herds.size)
        val two = GameEngine()
        water(19..21, 19..21, two)
        water(19..21, 40..42, two)
        two.update(now)
        assertEquals(2, two.state.herds.size)
    }

    @Test
    fun `ducks can only stand on water and other animals only on land`() {
        val engine = engineWithPond()
        engine.state.pets[GridPoint(5, 5)] = Pet(breed = "grey", name = "x", position = GridPoint(5, 5))
        engine.state.herds[center] = Herd(nextBirthAt = now)
        val duck = AnimalRef.Livestock(center, -1)
        val pet = AnimalRef.Pet(GridPoint(5, 5))
        assertTrue(engine.canStand(center, duck))
        assertFalse(engine.canStand(GridPoint(25, 25), duck))
        assertFalse(engine.canStand(GridPoint(20, 18), duck))
        assertFalse(engine.canStand(center, pet))
        assertTrue(engine.canStand(GridPoint(25, 25), pet))
    }

    private fun engineWithDuck(tile: GridPoint): Pair<GameEngine, AnimalRef> {
        val engine = engineWithPond()
        val variant = LivestockSpecies.duck.variants[0].id
        engine.state.herds[center] =
            Herd(listOf(Livestock(0, variant, "x", tile, bornAt = now)), now.advancedBy(1_000_000.0))
        return engine to AnimalRef.Livestock(center, 0)
    }

    @Test
    fun `a swimming duck only chooses water`() {
        val (engine, ref) = engineWithDuck(GridPoint(20, 21))
        val generator = SeededRandom(21)
        repeat(3_000) {
            engine.wanderDirection(ref, now, generator)?.let { engine.move(ref, it) }
            assertNotNull(engine.state.water[engine.position(ref) ?: GridPoint(0, 0)])
        }
    }

    @Test
    fun `a duck never steps onto land`() {
        val (engine, ref) = engineWithDuck(GridPoint(20, 21))
        assertNull(engine.move(ref, GridPoint(0, 1)))
        assertEquals(GridPoint(20, 21), engine.position(ref))
    }

    @Test
    fun `a duck whose water is filled in or built over is put back on the nearest water`() {
        val (engine, ref) = engineWithDuck(GridPoint(20, 21))
        engine.state.water.remove(GridPoint(20, 21))
        engine.update(now)
        val position = engine.position(ref)!!
        assertNotNull(engine.state.water[position])
        assertEquals(1, distance(position, GridPoint(20, 21)))
    }

    @Test
    fun `filling in the pond stops new ducklings but keeps the ducks`() {
        val engine = engineWithPond()
        engine.update(now)
        engine.update(now.advancedBy(600.0))
        assertEquals(1, engine.animals(center).size)
        engine.state.water.remove(center)
        engine.update(now.advancedBy(24 * 60 * 60.0))
        assertEquals(1, engine.animals(center).size)
    }

    @Test
    fun `ducks survive a save and an empty pond with no water is forgotten`() {
        val engine = engineWithPond()
        engine.update(now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(sameState(loaded, engine.state))
        engine.state.water.clear()
        engine.update(now)
        assertTrue(engine.state.herds.isEmpty())
    }

    // Ponds of a new world

    private val worlds: List<GameState> by lazy {
        val generator = SeededRandom(515)
        List(40) { newGame(generator, now) }
    }

    @Test
    fun `every pond is at least seven tiles wide and five tall`() {
        for (world in worlds) {
            val unvisited = world.water.keys.toMutableSet()
            while (unvisited.isNotEmpty()) {
                val start = unvisited.first()
                val pond = mutableSetOf(start)
                val frontier = mutableListOf(start)
                while (frontier.isNotEmpty()) {
                    val tile = frontier.removeAt(frontier.lastIndex)
                    for (next in tile.neighbors) if (next in world.water && pond.add(next)) frontier.add(next)
                }
                unvisited.removeAll(pond)
                assertTrue(pond.maxOf { it.col } - pond.minOf { it.col } + 1 >= 7)
                assertTrue(pond.maxOf { it.row } - pond.minOf { it.row } + 1 >= 5)
            }
        }
    }

    @Test
    fun `a new map starts its water with grown ducks spaced apart within the limits`() {
        var somePondHasDucks = false
        for (world in worlds) {
            val engine = GameEngine(world)
            val ducks = world.herds.values.flatMap { it.animals }
            assertTrue(ducks.size <= engine.animalLimitOnTheMap)
            somePondHasDucks = somePondHasDucks || ducks.isNotEmpty()
            for (duck in ducks) {
                assertTrue(duck.position in world.waterLayout.interior)
                assertTrue(duck.species == LivestockSpecies.duck && duck.isGrown(Instant.now()))
                for (other in ducks.filter { it.position != duck.position || it.id != duck.id }) {
                    assertTrue(other.id == duck.id || distance(duck.position, other.position) >= 3)
                }
            }
        }
        assertTrue(somePondHasDucks)
    }

    @Test
    fun `every body of water of a new map can bring more ducks`() {
        for (world in worlds) assertFalse(world.herds.isEmpty())
    }
}
