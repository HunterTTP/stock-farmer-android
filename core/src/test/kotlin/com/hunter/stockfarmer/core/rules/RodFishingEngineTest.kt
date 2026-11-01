package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** The Swift `RodFishingTests`, `FishWeightTests` and `SellOnHarvestTests`. */
class RodFishingEngineTest {
    private val anchor = GridPoint(10, 10)
    private val now = Instant.ofEpochSecond(40_000_000)
    private val bounds = FishingContest.Bounds(40.0, 350.0, 120.0, 680.0)

    private fun engine(
        kind: String = "carp",
        spawnedAt: Instant? = null,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = 10.0, xp = lateGameXP, logs = 0))
        for (tile in anchor.rectangle(4, 4)) engine.state.water[tile] = WaterStyle.medium
        engine.state.fishPonds[anchor] = FishPond(PondFish(kind, anchor), spawnedAt ?: now)
        engine.select(HarvestTool.rod)
        return engine
    }

    private fun GameEngine.setFish(fish: PondFish?) {
        state.fishPonds[anchor] = state.fishPonds[anchor]!!.copy(fish = fish)
    }

    private fun rejected(rejection: Rejection): List<GameEvent> = listOf(GameEvent.Rejected(rejection))

    @Test fun `casting hooks the fish only on the exact tile it swims in without costing or changing anything`() {
        val engine = engine()
        val before = engine.state.copy()
        for (other in listOf(GridPoint(13, 13), GridPoint(10, 11), GridPoint(11, 10))) {
            assertEquals(rejected(Rejection.FishNotBiting), engine.useTool(other, now))
        }
        engine.setFish(PondFish("carp", GridPoint(12, 11)))
        val tile = GridPoint(12, 11)
        assertEquals(listOf<GameEvent>(GameEvent.FishHooked(tile)), engine.useTool(tile, now))
        assertEquals(rejected(Rejection.FishNotBiting), engine.useTool(anchor, now))
        engine.setFish(before.fishPonds[anchor]?.fish)
        assertTrue(sameState(before, engine.state))
        assertEquals(10.0, engine.state.money)
    }

    @Test fun `landing the fish stores it frees the pond and keeps the respawn timer`() {
        val engine = engine()
        val events = engine.landFish(anchor, anchor, now)
        assertEquals(1, events.size)
        assertTrue(engine.state.money == 10.0 && engine.storedCount == 1)
        assertEquals(mapOf("fish-carp" to 1), engine.state.storage)
        assertNull(engine.state.fishPonds[anchor]?.fish)
        assertEquals(now.advancedBy(GameEngine.FISH_INTERVAL), engine.state.fishPonds[anchor]?.nextFishAt)
        val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(restored.storage == engine.state.storage && restored.harvestTool == HarvestTool.rod)
        assertEquals(rejected(Rejection.FishNotBiting), engine.landFish(anchor, anchor, now))
        assertEquals(rejected(Rejection.FishNotBiting), engine.useTool(anchor, now))
    }

    @Test fun `the fish on the line can be landed or given up after it has swum off to another tile`() {
        val engine = engine()
        val hooked = engine.hookedPond(anchor)!!
        engine.setFish(PondFish("carp", GridPoint(13, 13)))
        assertEquals(1, engine.landFish(hooked, anchor, now, selling = true).size)
        assertNull(engine.state.fishPonds[anchor]?.fish)
        val other = engine()
        other.setFish(PondFish("carp", GridPoint(12, 12)))
        other.loseFish(hooked, now)
        assertNull(other.state.fishPonds[anchor]?.fish)
        assertNull(engine().hookedPond(GridPoint(11, 11)))
    }

    @Test fun `a fish that got away stays in the pond for another try`() {
        val engine = engine()
        val before = engine.state.copy()
        engine.useTool(anchor, now)
        assertTrue(sameState(before, engine.state))
        assertNotNull(engine.state.fishPonds[anchor]?.fish)
    }

    @Test fun `refusals never remove the fish`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("wheat" to engine.storageCapacity)
        assertEquals(listOf<GameEvent>(GameEvent.FishHooked(anchor)), engine.useTool(anchor, now))
        assertEquals(rejected(Rejection.StorageFull), engine.landFish(anchor, anchor, now))
        assertNotNull(engine.state.fishPonds[anchor]?.fish)
        engine.state.storage = mutableMapOf()
        engine.select(HarvestTool.bucket)
        assertEquals(rejected(Rejection.UseTheRodForFish), engine.useTool(anchor, now))
        assertNotNull(engine.state.fishPonds[anchor]?.fish)
    }

    @Test fun `the rod hooks the fish of each tier with the contest for its kind`() {
        val random = SeededRandom(1)
        val engine = engine()
        val common = engine.fishingContest(anchor, bounds, random)
        engine.setFish(PondFish("mackerel", anchor))
        val fine = engine.fishingContest(anchor, bounds, random)
        engine.setFish(PondFish("kingsalmon", anchor))
        val rare = engine.fishingContest(anchor, bounds, random)
        assertTrue((common?.fillSeconds ?: 99.0) < (fine?.fillSeconds ?: 0.0))
        assertTrue((fine?.fillSeconds ?: 99.0) < (rare?.fillSeconds ?: 0.0))
        engine.setFish(null)
        assertNull(engine.fishingContest(anchor, bounds, random))
    }

    @Test fun `each rarity has its own contest getting harder up to legendary`() {
        val random = SeededRandom(1)
        val engine = engine()
        val expected =
            listOf(
                Triple("bluegill", 24.0, 1.6),
                Triple("carp", 55.0, 3.0),
                Triple("mackerel", 85.0, 4.4),
                Triple("kingsalmon", 108.0, 5.8),
                Triple("bluemarlin", 130.0, 7.0),
            )
        for ((kind, speed, seconds) in expected) {
            engine.setFish(PondFish(kind, anchor))
            val contest = engine.fishingContest(anchor, bounds, random)!!
            assertEquals(speed, contest.speed, kind)
            assertEquals(seconds, contest.fillSeconds, kind)
        }
        val contests =
            expected.map { (kind, _, _) ->
                engine.setFish(PondFish(kind, anchor))
                engine.fishingContest(anchor, bounds, random)!!
            }
        assertEquals(contests.map { it.speed }.sorted(), contests.map { it.speed })
        assertEquals(5, contests.map { it.speed }.toSet().size)
        assertEquals(contests.map { it.fillSeconds }.sorted(), contests.map { it.fillSeconds })
        assertEquals(5, contests.map { it.fillSeconds }.toSet().size)
    }

    @Test fun `giving up the fish takes it from the pond for the usual time and stores nothing`() {
        val engine = engine()
        engine.loseFish(anchor, now)
        assertNull(engine.state.fishPonds[anchor]?.fish)
        assertEquals(now.advancedBy(GameEngine.FISH_INTERVAL), engine.state.fishPonds[anchor]?.nextFishAt)
        assertTrue(engine.storedCount == 0 && engine.state.money == 10.0)
        val before = engine.state.copy()
        engine.loseFish(anchor, now.plusSeconds(5))
        assertTrue(sameState(before, engine.state))
    }

    @Test fun `a fish can be sold on the spot for its value even with storage full`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("wheat" to engine.storageCapacity)
        val events = engine.landFish(anchor, anchor, now, selling = true)
        val value = FishKind.named("carp")!!.value
        assertEquals(listOf<GameEvent>(GameEvent.Sold(anchor, "fish-carp", value, 0)), events)
        assertEquals(10 + value, engine.state.money)
        assertEquals(mapOf("wheat" to engine.storageCapacity), engine.state.storage)
        assertNull(engine.state.fishPonds[anchor]?.fish)
        assertEquals(now.advancedBy(GameEngine.FISH_INTERVAL), engine.state.fishPonds[anchor]?.nextFishAt)
        assertEquals(1, engine.state.salesToday["fish-carp"]?.quantity)
    }

    @Test fun `the fishing hint is remembered and an old save has not seen it`() {
        val engine = engine()
        assertFalse(engine.state.hasSeenFishingHint)
        engine.markFishingHintSeen()
        assertTrue(GameStateJson.decode(GameStateJson.encode(engine.state)).hasSeenFishingHint)
        assertFalse(GameStateJson.decode("""{"money": 5}""").hasSeenFishingHint)
    }

    // Weights

    @Test fun `every kind has a sensible weight range`() {
        for (kind in FishKind.all) {
            assertTrue(kind.weightRange.start > 0 && kind.weightRange.start < kind.weightRange.endInclusive, kind.id)
        }
        assertTrue(
            FishKind.named("bluegill")!!.weightRange.endInclusive < FishKind.named("bluemarlin")!!.weightRange.start,
        )
    }

    @Test fun `a fish weighs within its kinds range and the same for as long as it is there`() {
        for (kind in FishKind.all) {
            for (hour in 0 until 10) {
                val engine = engine(kind.id, now.advancedBy(hour * 3_600.0))
                val weight = engine.fishWeight(anchor)!!
                assertTrue(weight in kind.weightRange, "${kind.id} $weight")
                assertEquals(weight, engine.fishWeight(anchor))
            }
        }
        assertNull(engine().fishWeight(GridPoint(30, 30)))
    }

    @Test fun `weights vary and most are middling`() {
        val weights = (0 until 400).map { engine(spawnedAt = now.advancedBy(it * 3_600.0)).fishWeight(anchor)!! }
        val carp = FishKind.named("carp")!!
        val heaviness = weights.map { carp.heaviness(it) }
        assertTrue(weights.toSet().size > 50)
        assertTrue(heaviness.count { it in 0.25..0.75 } > heaviness.size / 2)
        assertTrue(heaviness.any { it < 0.25 } && heaviness.any { it > 0.75 })
    }

    @Test fun `weight never changes what a fish sells for so fish of a kind are the same`() {
        for (kind in listOf("carp", "tuna", "bluemarlin").map { FishKind.named(it)!! }) {
            val prices = mutableSetOf<Double>()
            val range = kind.weightRange
            for (weight in listOf(range.start, (range.start + range.endInclusive) / 2, range.endInclusive)) {
                val engine = engine()
                engine.setFish(PondFish(kind.id, anchor, weight))
                val sold = engine.fishSaleValue(anchor)!!
                prices += sold
                val money = engine.state.money
                engine.landFish(anchor, anchor, now, selling = true)
                assertTrue(engine.state.money == money + kind.value && sold == kind.value)
            }
            assertEquals(setOf(kind.value), prices)
        }
    }

    @Test fun `storing and selling earn the same xp and stored fish of a kind stack with no difference`() {
        val stored = engine()
        stored.setFish(PondFish("carp", anchor, 5.0))
        stored.landFish(anchor, anchor, now)
        stored.setFish(PondFish("carp", anchor, 40.0))
        stored.landFish(anchor, anchor, now)
        assertEquals(mapOf("fish-carp" to 2), stored.state.storage)
        val sold = engine()
        sold.setFish(PondFish("carp", anchor, 5.0))
        val xpBefore = sold.state.xp
        sold.landFish(anchor, anchor, now, selling = true)
        val kept = engine()
        kept.setFish(PondFish("carp", anchor, 5.0))
        kept.landFish(anchor, anchor, now)
        assertEquals(kept.state.xp - xpBefore, sold.state.xp - xpBefore)
    }

    @Test fun `landing a fish earns xp for the level rarity and weight and can level up`() {
        val level = 23
        val xpForLevel = LevelRules.xpToReach(level)
        val carp = FishKind.named("bluegill")!!
        val tuna = FishKind.named("mackerel")!!
        val marlin = FishKind.named("bluemarlin")!!

        fun middle(kind: FishKind) = (kind.weightRange.start + kind.weightRange.endInclusive) / 2
        assertEquals(112, LevelRules.fishXP(carp, middle(carp), level))
        assertEquals(449, LevelRules.fishXP(tuna, middle(tuna), level))
        assertEquals(1123, LevelRules.fishXP(marlin, middle(marlin), level))
        assertEquals(1685, LevelRules.fishXP(marlin, marlin.weightRange.endInclusive, level))
        assertEquals(562, LevelRules.fishXP(marlin, marlin.weightRange.start, level))
        assertEquals(0, LevelRules.fishXP(marlin, middle(marlin), LevelRules.maxLevel))
        for (selling in listOf(false, true)) {
            val engine = engine()
            engine.state.xp = xpForLevel
            engine.setFish(PondFish("mackerel", anchor, 3.5))
            val events = engine.landFish(anchor, anchor, now, selling = selling)
            assertEquals(xpForLevel + 449, engine.state.xp)
            assertFalse(events.containsRejection)
        }
        val engine = engine()
        engine.state.xp = LevelRules.xpToReach(level + 1) - 1
        engine.setFish(PondFish("carp", anchor))
        val events = engine.landFish(anchor, anchor, now)
        assertEquals(level + 1, engine.state.level)
        assertTrue(events.size > 1)
    }

    // Selling on harvest

    private val planted = Instant.ofEpochSecond(7_000_000)
    private val ready = planted.plusSeconds(30)
    private val origin = GridPoint(24, 24)
    private val spot = GridPoint(22, 20)

    private fun fieldWithTheScythe(): GameEngine {
        val engine = GameEngine(stateWith(money = 0.0, logs = 0))
        engine.tap(origin, planted)
        engine.select(HarvestTool.scythe)
        return engine
    }

    @Test fun `every implement stores until told to sell`() {
        val engine = GameEngine()
        assertTrue(HarvestTool.entries.none { engine.isSelling(it) })
    }

    @Test fun `each implement remembers its own choice`() {
        val engine = GameEngine()
        engine.setSelling(true, HarvestTool.bucket)
        assertTrue(
            engine.isSelling(HarvestTool.bucket) && !engine.isSelling(HarvestTool.scythe) &&
                !engine.isSelling(HarvestTool.axe),
        )
        engine.setSelling(false, HarvestTool.bucket)
        assertFalse(engine.isSelling(HarvestTool.bucket))
    }

    @Test fun `the scythe sells a crop at once and still pays xp`() {
        val engine = fieldWithTheScythe()
        engine.setSelling(true, HarvestTool.scythe)
        val money = engine.state.money
        val xp = engine.state.xp
        val events = engine.useTool(origin, ready)
        assertEquals(
            listOf<GameEvent>(GameEvent.Sold(origin, Crop.starter.id, Crop.starter.sellValue, 2)),
            events,
        )
        assertTrue(engine.state.storage.isEmpty())
        assertEquals(money + Crop.starter.sellValue, engine.state.money)
        assertEquals(xp + 2, engine.state.xp)
        assertNull(engine.state.plots[origin])
    }

    @Test fun `selling needs no room in storage`() {
        val engine = fieldWithTheScythe()
        engine.state.storage = mutableMapOf(StorageItem.Log.id to engine.storageCapacity)
        assertTrue(engine.useTool(origin, ready).containsRejection)
        engine.setSelling(true, HarvestTool.scythe)
        assertFalse(engine.useTool(origin, ready).containsRejection)
    }

    @Test fun `the bucket sells eggs at once`() {
        val engine = GameEngine()
        engine.select(HarvestTool.bucket)
        engine.setSelling(true, HarvestTool.bucket)
        engine.state.eggs = listOf(lyingEgg(spot, 0.0, 0.0))
        val xp = LevelRules.produceXP(StorageItem.Egg, engine.state.level)
        val events = engine.useTool(spot, ready)
        assertTrue(xp > 0 && events == listOf<GameEvent>(GameEvent.Sold(spot, StorageItem.Egg.id, Egg.SALE_PRICE, xp)))
        assertEquals(Egg.SALE_PRICE, engine.state.money)
        assertTrue(engine.state.storage.isEmpty() && engine.state.eggs.isEmpty())
    }

    @Test fun `the axe and pickaxe cannot sell so they store even if an old save said sell`() {
        assertEquals(
            listOf(HarvestTool.axe, HarvestTool.pickaxe, HarvestTool.rod),
            HarvestTool.entries.filter { !it.canSell },
        )
        val engine = GameEngine()
        engine.setSelling(true, HarvestTool.axe)
        engine.setSelling(true, HarvestTool.pickaxe)
        assertTrue(engine.state.sellingTools.isEmpty())
        engine.state.sellingTools = mutableSetOf(HarvestTool.axe, HarvestTool.pickaxe, HarvestTool.bucket)
        assertTrue(
            !engine.isSelling(HarvestTool.axe) && !engine.isSelling(HarvestTool.pickaxe) &&
                engine.isSelling(HarvestTool.bucket),
        )
        engine.state.sellingTools = mutableSetOf()
        engine.select(HarvestTool.axe)
        val tree = Tree.starter
        engine.state.putTree(tree.origin(spot), PlacedTree(tree, tree.species.colors[0], Instant.now()))
        repeat(tree.hitsToFell) {
            engine.useTool(spot, ready)
            engine.liftTool()
        }
        assertTrue(engine.state.trees.isEmpty())
        assertEquals(mapOf(StorageItem.Log.id to tree.logs), engine.state.storage)
    }

    @Test fun `the choice survives a save and an old save stores everything`() {
        val state = GameState()
        state.sellingTools = mutableSetOf(HarvestTool.bucket)
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertEquals(setOf(HarvestTool.bucket), loaded.sellingTools)
        assertTrue(GameStateJson.decode("""{"money": 5}""").sellingTools.isEmpty())
    }
}
