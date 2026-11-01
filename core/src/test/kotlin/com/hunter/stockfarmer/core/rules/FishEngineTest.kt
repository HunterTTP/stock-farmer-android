package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

/** The fish half of the Swift `FishTests`: ponds, arrivals, swimming, weights and days. */
class FishEngineTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val anchor = GridPoint(20, 20)

    private fun water(
        rows: IntRange,
        cols: IntRange,
        engine: GameEngine,
    ) {
        for (row in rows) for (col in cols) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
    }

    private fun engineWithPond(columns: IntRange = 20..23): GameEngine {
        val engine = GameEngine()
        water(20..23, columns, engine)
        return engine
    }

    private fun engineWithFish(
        at: GridPoint = GridPoint(21, 21),
        columns: IntRange = 20..23,
    ): GameEngine {
        val engine = engineWithPond(columns)
        engine.state.fishPonds[anchor] = FishPond(PondFish("carp", at, 12.0), now)
        engine.state.mode = com.hunter.stockfarmer.core.model.PlayMode.harvest
        engine.state.harvestTool = HarvestTool.bucket
        return engine
    }

    private fun reload(engine: GameEngine) = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))

    private fun fishCount(engine: GameEngine) =
        engine.state.fishPonds.values
            .count { it.fish != null }

    @Test fun `water with an interior gets its first fish after ten minutes`() {
        val engine = engineWithPond()
        val random = SeededRandom(3)
        engine.updateFish(now, using = random)
        assertTrue(
            engine.state.fishPonds.size == 1 && engine.state.fishPonds.values
                .first()
                .fish == null,
        )
        engine.updateFish(now.plusSeconds(599), using = random)
        assertNull(
            engine.state.fishPonds.values
                .first()
                .fish,
        )
        engine.updateFish(now.plusSeconds(600), using = random)
        val (home, pond) =
            engine.state.fishPonds.entries
                .first()
        val fish = pond.fish!!
        assertNotNull(FishKind.named(fish.kindID))
        assertTrue(GridPoint(21, 21).rectangle(2, 2).contains(fish.position))
        assertEquals(fish.position, home)
    }

    @Test fun `night halves the wait for both first fish and respawns`() {
        for ((hasPreviousFish, delay) in listOf(false to 300.0, true to 900.0)) {
            var engine = engineWithPond()
            val arrival = now.advancedBy(delay)
            engine.state.clockOffset = DayNightCycle.offset(23 * 60.0, arrival)
            val random = SeededRandom(45)
            if (hasPreviousFish) {
                engine.state.fishPonds[anchor] = FishPond(PondFish("carp", anchor), now)
                engine.loseFish(anchor, now)
            } else {
                engine.updateFish(now, using = random)
            }
            engine = reload(engine)
            engine.updateFish(arrival.advancedBy(-1.0), using = random)
            assertTrue(
                engine.state.fishPonds.values
                    .all { it.fish == null },
            )
            engine.updateFish(arrival, using = random)
            assertTrue(
                engine.state.fishPonds.values
                    .any { it.fish != null },
            )
        }
    }

    @Test fun `darkness accelerates an already waiting pond`() {
        val engine = engineWithPond()
        val random = SeededRandom(46)
        engine.state.clockOffset = DayNightCycle.offset(12 * 60.0, now)
        engine.updateFish(now, using = random)
        val later = now.plusSeconds(300)
        engine.updateFish(later, using = random)
        assertTrue(
            engine.state.fishPonds.values
                .all { it.fish == null },
        )
        engine.state.clockOffset = DayNightCycle.offset(23 * 60.0, later)
        engine.updateFish(later, using = random)
        assertTrue(
            engine.state.fishPonds.values
                .any { it.fish != null },
        )
    }

    @Test fun `pond wait times round trip and older ponds load`() {
        val pond = FishPond(nextFishAt = now.plusSeconds(600), waitStartedAt = now)
        val json = Json { encodeDefaults = true }
        assertEquals(pond, json.decodeFromString<FishPond>(json.encodeToString(FishPond.serializer(), pond)))
        val old = FishPond(nextFishAt = Instant.ofEpochSecond(1_800))
        assertEquals(old.nextFishAt.advancedBy(-GameEngine.FISH_INTERVAL), old.waitStartedAt)
    }

    @Test fun `water with no interior has no fish`() {
        val engine = GameEngine()
        water(19..20, 19..30, engine)
        water(30..33, 30..31, engine)
        val random = SeededRandom(3)
        engine.updateFish(now, using = random)
        engine.updateFish(now.plusSeconds(24 * 60 * 60), using = random)
        assertTrue(engine.state.fishPonds.isEmpty())
    }

    @Test fun `a three by three puddle holds one fish`() {
        val engine = GameEngine()
        water(19..21, 19..21, engine)
        val random = SeededRandom(3)
        engine.updateFish(now, using = random)
        engine.updateFish(now.plusSeconds(24 * 60 * 60), using = random)
        assertEquals(
            listOf(GridPoint(20, 20)),
            engine.state.fishPonds.keys
                .toList(),
        )
        assertNotNull(
            engine.state.fishPonds.values
                .first()
                .fish,
        )
    }

    @Test fun `a big lake holds several fish but never more than its limit`() {
        val engine = GameEngine()
        water(20..24, 20..35, engine)
        val random = SeededRandom(4)
        engine.updateFish(now, using = random)
        for (step in 1 until 10) engine.updateFish(now.plusSeconds(step * 3_600L), using = random)
        assertEquals(3, engine.state.fishPonds.size)
        assertTrue(fishCount(engine) in 2..3)
    }

    @Test fun `each pond has its own fish`() {
        val engine = engineWithPond()
        water(30..33, 30..33, engine)
        val random = SeededRandom(6)
        engine.updateFish(now, using = random)
        engine.updateFish(now.plusSeconds(3_600), using = random)
        assertEquals(2, engine.state.fishPonds.size)
        assertTrue(
            engine.state.fishPonds.values
                .all { it.fish != null },
        )
    }

    @Test fun `joining two ponds keeps the fish they had`() {
        val engine = engineWithPond()
        water(30..33, 20..23, engine)
        val random = SeededRandom(6)
        engine.updateFish(now, using = random)
        engine.updateFish(now.plusSeconds(3_600), using = random)
        val before = engine.state.fishPonds.toMap()
        assertEquals(2, before.size)
        water(24..29, 20..23, engine)
        engine.updateFish(now.plusSeconds(3_700), using = random)
        assertTrue(before.all { engine.state.fishPonds[it.key] == it.value })
    }

    @Test fun `removing all the water takes the fish away`() {
        val engine = engineWithFish()
        engine.state.water.clear()
        engine.settleFishPonds(now)
        assertTrue(engine.state.fishPonds.isEmpty())
    }

    @Test fun `a fish whose tile is filled in is set on the nearest water`() {
        val engine = engineWithFish()
        engine.state.water.remove(GridPoint(21, 21))
        engine.settleFishPonds(now)
        val fish =
            engine.state.fishPonds.values
                .mapNotNull { it.fish }
                .first()
        assertNotNull(engine.state.water[fish.position])
    }

    @Test fun `a pond still big enough keeps its fish when its anchor tile is filled in`() {
        val engine = engineWithFish()
        water(20..23, 24..33, engine)
        engine.state.water.remove(anchor)
        engine.settleFishPonds(now)
        assertEquals(2, engine.state.fishPonds.size)
        assertTrue(
            engine.state.fishPonds.values
                .any { it.fish?.kindID == "carp" },
        )
    }

    @Test fun `both fish have distinct positions and independent catch timers after saving`() {
        var engine = engineWithPond(20..33)
        val random = SeededRandom(51)
        engine.updateFish(now, using = random)
        val later = now.plusSeconds(600)
        engine.updateFish(later, using = random)
        val fish =
            engine.state.fishPonds
                .mapNotNull { (home, pond) -> pond.fish?.let { home to it } }
                .sortedWith(compareBy({ it.first.row }, { it.first.col }))
        assertEquals(2, fish.size)
        val first = fish.first()
        val second = fish.last()
        assertTrue(first.second.position != second.second.position)
        val weight = engine.fishWeight(second.first)
        val before = engine.state.fishPonds[second.first]
        engine = reload(engine)
        assertEquals(second.first, engine.hookedPond(second.second.position))
        assertFalse(engine.landFish(first.first, first.second.position, later).containsRejection)
        assertNull(engine.state.fishPonds[first.first]?.fish)
        assertEquals(before, engine.state.fishPonds[second.first])
        assertEquals(weight, engine.fishWeight(second.first))
        engine.loseFish(second.first, later.plusSeconds(60))
        assertEquals(later.advancedBy(GameEngine.FISH_INTERVAL), engine.state.fishPonds[first.first]?.nextFishAt)
        assertEquals(
            later.plusSeconds(60).advancedBy(GameEngine.FISH_INTERVAL),
            engine.state.fishPonds[second.first]?.nextFishAt,
        )
    }

    @Test fun `adding a second fish to an older pond preserves its existing fish and weight`() {
        val engine = engineWithFish(columns = 20..33)
        engine.state.fishPonds = mutableMapOf(anchor to engine.state.fishPonds[anchor]!!)
        val before = engine.state.fishPonds[anchor]
        val weight = engine.fishWeight(anchor)
        engine.settleFishPonds(now)
        assertEquals(2, engine.state.fishPonds.size)
        assertEquals(before, engine.state.fishPonds[anchor])
        assertEquals(weight, engine.fishWeight(anchor))
    }

    @Test fun `merging ponds preserves two existing fish before adding new ones`() {
        val engine = engineWithPond()
        water(30..33, 20..23, engine)
        val other = GridPoint(30, 20)
        engine.state.fishPonds[anchor] = FishPond(PondFish("carp", anchor, 12.0), now)
        engine.state.fishPonds[other] = FishPond(PondFish("tuna", other, 90.0), now)
        val before = engine.state.fishPonds.toMap()
        water(24..29, 20..23, engine)
        engine.settleFishPonds(now)
        assertTrue(before.all { engine.state.fishPonds[it.key] == it.value })
    }

    @Test fun `splitting a pond never duplicates fish whose homes are on the other side`() {
        val engine = engineWithPond()
        water(24..33, 20..23, engine)
        val other = GridPoint(30, 20)
        engine.state.fishPonds[anchor] = FishPond(PondFish("carp", other), now)
        engine.state.fishPonds[GridPoint(20, 21)] = FishPond(PondFish("tuna", anchor), now)
        for (col in 20..23) engine.state.water.remove(GridPoint(26, col))
        engine.settleFishPonds(now)
        assertEquals(2, engine.state.fishPonds.size)
        assertEquals(
            listOf("carp", "tuna"),
            engine.state.fishPonds.values
                .mapNotNull { it.fish?.kindID }
                .sorted(),
        )
    }

    @Test fun `two fish cannot swim onto each other`() {
        val engine = engineWithPond()
        val other = GridPoint(20, 21)
        engine.state.fishPonds[anchor] = FishPond(PondFish("carp", anchor), now)
        engine.state.fishPonds[other] = FishPond(PondFish("tuna", other), now)
        assertNull(engine.moveFish(anchor, GridPoint(0, 1)))
        assertNull(engine.moveFish(other, GridPoint(0, -1)))
    }

    @Test fun `a fish never swims out of the water`() {
        val engine = engineWithFish()
        val random = SeededRandom(9)
        repeat(500) {
            val direction = engine.fishDirection(anchor, random) ?: return@repeat
            engine.moveFish(anchor, direction)
            val tile =
                engine.state.fishPonds[anchor]
                    ?.fish
                    ?.position
            assertTrue(tile != null && engine.state.water[tile] != null)
        }
    }

    @Test fun `fish favor swimming sideways over up and down`() {
        val engine = engineWithFish(at = GridPoint(21, 21))
        val random = SeededRandom(17)
        val directions = (0 until 4_000).mapNotNull { engine.fishDirection(anchor, random) }
        val sideways = directions.count { it.row == 0 }
        assertEquals(4_000, directions.size)
        assertTrue(sideways in 3_001..3_499)
    }

    @Test fun `a fish swims one tile at a time and never onto land`() {
        val engine = GameEngine()
        water(20..23, 20..23, engine)
        engine.state.fishPonds[anchor] = FishPond(PondFish("carp", GridPoint(21, 21)), now)
        assertEquals(GridPoint(21, 22), engine.moveFish(anchor, GridPoint(0, 1)))
        assertNull(engine.moveFish(anchor, GridPoint(-5, 0)))
        engine.state.fishPonds[anchor] =
            engine.state.fishPonds[anchor]!!.let { it.copy(fish = it.fish!!.copy(position = GridPoint(20, 20))) }
        assertNull(engine.moveFish(anchor, GridPoint(-1, 0)))
    }

    @Test fun `the bucket leaves the fish and says to use the fishing rod`() {
        val engine = engineWithFish()
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.UseTheRodForFish)),
            engine.useTool(GridPoint(21, 21), now),
        )
        assertEquals(HarvestTool.rod, Rejection.UseTheRodForFish.suggestedTool)
        assertTrue(engine.state.fishPonds[anchor]?.fish != null && engine.storedCount == 0)
    }

    @Test fun `the bucket misses a tile the fish is not on`() {
        val engine = engineWithFish()
        assertTrue(engine.useTool(GridPoint(22, 22), now).isEmpty())
        assertNotNull(engine.state.fishPonds[anchor]?.fish)
    }

    @Test fun `other implements and tools leave the fish be`() {
        for (tool in listOf(HarvestTool.scythe, HarvestTool.axe, HarvestTool.pickaxe, HarvestTool.bucket)) {
            val engine = engineWithFish()
            engine.state.harvestTool = tool
            engine.useTool(GridPoint(21, 21), now)
            assertNotNull(engine.state.fishPonds[anchor]?.fish)
        }
    }

    @Test fun `a new fish comes half an hour after one is caught`() {
        val engine = engineWithFish()
        engine.state.harvestTool = HarvestTool.rod
        engine.state.money = 10.0
        engine.landFish(anchor, GridPoint(21, 21), now)
        val random = SeededRandom(2)
        engine.updateFish(now.plusSeconds(1_799), using = random)
        assertTrue(
            engine.state.fishPonds.values
                .all { it.fish == null },
        )
        engine.updateFish(now.plusSeconds(1_800), using = random)
        assertTrue(
            engine.state.fishPonds.values
                .any { it.fish != null },
        )
    }

    @Test fun `a new maps ponds may already have a fish each`() {
        val random = SeededRandom(12)
        val engine = GameEngine()
        water(20..23, 20..23, engine)
        water(30..33, 30..33, engine)
        water(40..43, 5..8, engine)
        var fished = 0
        repeat(40) {
            val trial = engine.copy()
            trial.scatterPondFish(now, random)
            assertEquals(2, trial.state.fishPonds.size)
            fished += fishCount(trial)
        }
        assertTrue(fished in 21..59)
    }

    @Test fun `every pond of a new map can hold a fish`() {
        for (seed in 0L until 20L) {
            val state = newGame(SeededRandom(seed), now)
            assertTrue(state.waterLayout.interior.isNotEmpty() && state.fishPonds.isNotEmpty())
            assertTrue(state.fishPonds.keys.all { state.water[it] != null })
            assertTrue(
                state.fishPonds.values
                    .mapNotNull { it.fish }
                    .all { state.water[it.position] != null },
            )
        }
    }

    @Test fun `an old save without fish loads with none`() {
        assertTrue(GameStateJson.decode("""{"money": 1}""").fishPonds.isEmpty())
        val engine = engineWithFish()
        engine.state.fishPonds[anchor] =
            engine.state.fishPonds[anchor]!!.let { it.copy(fish = it.fish!!.copy(position = GridPoint(22, 21))) }
        val again = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.fishPonds, again.fishPonds)
    }

    @Test fun `a fish's weight is settled when it appears and never changes`() {
        val engine = engineWithPond(20..33)
        val random = SeededRandom(33)
        engine.updateFish(now, using = random)
        engine.updateFish(now.plusSeconds(600), using = random)
        val fish = engine.state.fishPonds.mapNotNull { (home, pond) -> pond.fish?.let { home to it } }
        assertEquals(2, fish.size)
        for ((home, one) in fish) {
            val weight = one.weight!!
            val kind = one.kind!!
            assertTrue(weight in kind.weightRange)
            assertEquals(weight, engine.fishWeight(home))
            assertEquals(kind.sizeFactor(weight), one.sizeFactor)
        }
        engine.settleFishPonds(now.plusSeconds(900))
        assertEquals(
            fish.mapNotNull { it.second.weight }.sorted(),
            engine.state.fishPonds.values
                .mapNotNull { it.fish?.weight }
                .sorted(),
        )
        engine.moveFish(fish[0].first, GridPoint(0, 1))
        assertEquals(fish[0].second.weight, engine.fishWeight(fish[0].first))
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.fishPonds, loaded.fishPonds)
    }

    @Test fun `a new maps fish already have weights`() {
        val random = SeededRandom(12)
        val engine = GameEngine()
        water(20..23, 20..23, engine)
        water(30..33, 30..33, engine)
        var found = 0
        repeat(20) {
            val trial = engine.copy()
            trial.scatterPondFish(now, random)
            for (fish in trial.state.fishPonds.values
                .mapNotNull { it.fish }) {
                found += 1
                assertTrue(fish.weight?.let { it in fish.kind!!.weightRange } == true)
            }
        }
        assertTrue(found > 0)
    }

    @Test fun `a fish saved before weights keeps the weight it always had when settled`() {
        val engine = engineWithFish()
        engine.state.fishPonds[anchor] =
            engine.state.fishPonds[anchor]!!.copy(fish = PondFish("carp", GridPoint(21, 21)))
        val always = engine.fishWeight(anchor)!!
        engine.settleFishPonds(now)
        assertEquals(
            always,
            engine.state.fishPonds[anchor]
                ?.fish
                ?.weight,
        )
        assertEquals(always, engine.fishWeight(anchor))
    }

    @Test fun `heavier fish are drawn bigger and an average one at normal size`() {
        for (kind in FishKind.all) {
            val range = kind.weightRange
            val middle = (range.start + range.endInclusive) / 2
            assertTrue(abs(kind.sizeFactor(middle) - 1) < 0.000_001)
            assertEquals(FishKind.lightestSizeFactor, kind.sizeFactor(range.start))
            assertEquals(FishKind.heaviestSizeFactor, kind.sizeFactor(range.endInclusive))
            assertTrue(kind.sizeFactor(range.start) < kind.sizeFactor(middle))
            assertTrue(kind.sizeFactor(middle) < kind.sizeFactor(range.endInclusive))
        }
        assertEquals(1.0, PondFish("carp", anchor).sizeFactor)
    }

    @Test fun `a fish nobody catches swims away after one farm day and its place waits as after a catch`() {
        val engine = engineWithPond()
        val random = SeededRandom(14)
        engine.updateFish(now, using = random)
        val arrival = now.plusSeconds(600)
        engine.updateFish(arrival, using = random)
        val fish =
            engine.state.fishPonds.values
                .mapNotNull { it.fish }
                .first()
        assertEquals(arrival.advancedBy(engine.state.clockOffset), fish.appearedAt)
        val day = DayNightCycle.secondsPerDay
        engine.updateFish(arrival.advancedBy(day - 1), using = random)
        assertEquals(1, fishCount(engine))
        val gone = arrival.advancedBy(day)
        engine.updateFish(gone, using = random)
        assertEquals(0, fishCount(engine))
        assertTrue(
            engine.state.fishPonds.values.all {
                it.nextFishAt == gone.advancedBy(GameEngine.FISH_INTERVAL) && it.waitStartedAt == gone
            },
        )
        engine.updateFish(gone.advancedBy(GameEngine.FISH_INTERVAL), using = random)
        assertEquals(1, fishCount(engine))
    }

    @Test fun `the fish on the line stays past its day and the day is counted on the farm clock`() {
        val engine = engineWithFish()
        val random = SeededRandom(15)
        engine.updateFish(now, using = random)
        val day = DayNightCycle.secondsPerDay
        engine.updateFish(now.advancedBy(day * 3), onTheLine = setOf(anchor), using = random)
        assertNotNull(engine.state.fishPonds[anchor]?.fish)
        engine.state.clockOffset -= day * 4
        engine.updateFish(now.advancedBy(day * 3), using = random)
        assertNotNull(engine.state.fishPonds[anchor]?.fish)
        engine.updateFish(now.advancedBy(day * 7), using = random)
        assertNull(engine.state.fishPonds[anchor]?.fish)
    }

    @Test fun `a fish saved before its day was kept starts its day when next seen and the time survives a save`() {
        val engine = engineWithFish()
        val random = SeededRandom(16)
        assertNull(
            engine.state.fishPonds[anchor]
                ?.fish
                ?.appearedAt,
        )
        engine.updateFish(now, using = random)
        val appeared =
            engine.state.fishPonds[anchor]
                ?.fish
                ?.appearedAt!!
        assertEquals(now.advancedBy(engine.state.clockOffset), appeared)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(appeared, loaded.fishPonds[anchor]?.fish?.appearedAt)
    }

    @Test fun `fish are storage items that sell for their own price`() {
        val carp = FishKind.named("carp")!!
        val item = StorageItem.Fish(carp)
        assertEquals(item, StorageItem.of(item.id))
        assertTrue(item in StorageItem.all && item.name == "Carp" && item.sellValue == carp.value && item.isSellable)
        assertEquals(
            StorageItem.all.size,
            StorageItem.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test fun `there are forty two kinds of fish each with a picture name and price`() {
        assertEquals(42, FishKind.all.size)
        assertEquals(
            42,
            FishKind.all
                .map { it.id }
                .toSet()
                .size,
        )
        assertTrue(FishKind.all.all { it.name.isNotEmpty() && it.value > 0 && it.assetName == "fish_fishing-${it.id}" })
    }
}
