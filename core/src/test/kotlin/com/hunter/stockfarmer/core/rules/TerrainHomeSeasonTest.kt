package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.hatch
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.updateHerds
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** The Swift `SeasonTests`, `GroundCoverTests`, `HomeUpgradeTests` and `SaveValidationTests` that the first port left out. */
class TerrainHomeSeasonTest {
    // Seasons

    @Test fun `a farm is in spring until another season is chosen`() {
        val engine = GameEngine()
        assertEquals(Season.spring, engine.state.season)
        engine.setSeason(Season.winter)
        assertEquals(Season.winter, engine.state.season)
        engine.setSeason(Season.fall)
        assertEquals(Season.fall, engine.state.season)
    }

    @Test fun `choosing a season changes nothing else and ignores the day`() {
        val engine = GameEngine()
        val before = engine.state.copy()
        engine.setSeason(Season.fall)
        assertTrue(
            engine.state.day == before.day && engine.state.money == before.money &&
                engine.state.clockOffset == before.clockOffset,
        )
    }

    @Test fun `choosing a season notes the season left and the one taken as seen`() {
        val engine = GameEngine()
        engine.setSeason(Season.winter)
        val seen = engine.state.career.seenEnvironmentIDs
        assertTrue(
            CareerRecord.environmentID(Season.spring) in seen && CareerRecord.environmentID(Season.winter) in seen,
        )
        assertFalse(CareerRecord.environmentID(Season.fall) in seen)
    }

    @Test fun `the chosen season is saved and a save without one is in spring`() {
        val engine = GameEngine()
        engine.setSeason(Season.winter)
        assertEquals(Season.winter, GameStateJson.decode(GameStateJson.encode(engine.state)).season)
        assertEquals(Season.spring, GameStateJson.decode("""{"money": 1}""").season)
    }

    // Ground cover

    private val point = GridPoint(25, 25)
    private val now = Instant.ofEpochSecond(40_000_000)

    private fun rejected(events: List<GameEvent>) = events.any { it is GameEvent.Rejected }

    @Test fun `paths are saved walkable and cleared by grass water and farmland`() {
        for (cover in GroundCover.entries) {
            val engine = GameEngine(stateWith(money = 100.0, xp = lateGameXP))
            engine.select(LandscapeTool.groundCover(cover))
            assertEquals(listOf<GameEvent>(GameEvent.Decorated(point, cover.cost)), engine.useTool(point, now))
            assertEquals(cover, engine.state.groundCover[point])
            assertFalse(point in engine.state.farmland)
            assertEquals(100 - cover.cost, engine.state.money)
            val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
            assertEquals(cover, restored.groundCover[point])
            assertEquals(LandscapeTool.groundCover(cover), restored.selectedTerrain)
            engine.select(LandscapeTool.grass)
            assertEquals(listOf<GameEvent>(GameEvent.Placed(point)), engine.useTool(point, now))
            assertTrue(engine.state.groundCover.isEmpty())
            engine.select(LandscapeTool.groundCover(cover))
            engine.useTool(point, now)
            engine.select(LandscapeTool.water)
            engine.useTool(point, now)
            assertTrue(engine.state.groundCover.isEmpty())
        }
    }

    @Test fun `every kind of terrain replaces every other at once`() {
        val tools =
            listOf(
                LandscapeTool.farmland,
                LandscapeTool.water,
                LandscapeTool.groundCover(GroundCover.dirtPath),
                LandscapeTool.groundCover(GroundCover.cobblestonePath),
            )

        fun terrain(engine: GameEngine) =
            listOf(
                point in engine.state.farmland,
                engine.state.water[point] != null,
                engine.state.groundCover[point] == GroundCover.dirtPath,
                engine.state.groundCover[point] == GroundCover.cobblestonePath,
            )
        for ((firstIndex, first) in tools.withIndex()) {
            for ((secondIndex, second) in tools.withIndex()) {
                if (firstIndex == secondIndex) continue
                val engine = GameEngine(stateWith(money = 100.0, xp = lateGameXP))
                engine.state.farmland.clear()
                engine.select(first)
                engine.useTool(point, now)
                engine.select(second)
                val events = engine.useTool(point, now)
                assertFalse(rejected(events), "$first then $second")
                assertEquals(tools.indices.map { it == secondIndex }, terrain(engine), "$first then $second")
            }
        }
    }

    @Test fun `covering land never erases crops water structures or the farmland limit`() {
        val engine = GameEngine(stateWith(money = 100.0))
        engine.state.farmland = mutableSetOf(point)
        engine.state.plots[point] = Plot(Crop.starter.id, now)
        engine.select(LandscapeTool.groundCover(GroundCover.dirtPath))
        assertTrue(rejected(engine.useTool(point, now)))
        assertTrue(engine.state.plots[point] != null && engine.state.groundCover.isEmpty())
    }

    @Test fun `old saves default to no ground cover and invalid coordinates are rejected`() {
        val state = stateWith(money = 100.0)
        val saved = Json.parseToJsonElement(GameStateJson.encode(state)) as JsonObject
        assertTrue(GameStateJson.decode(JsonObject(saved - "groundCover").toString()).groundCover.isEmpty())
        state.groundCover[GridPoint(-1, 0)] = GroundCover.dirtPath
        val invalid = GameStateJson.encode(state)
        assertThrows(Exception::class.java) { GameStateJson.decode(invalid) }
    }

    // Home upgrades

    private val base = Instant.ofEpochSecond(60_000_000)
    private val origin = GridPoint(20, 20)

    private fun homeEngine(
        buildingID: String = "barn2",
        level: Int = 34,
        money: Double = 100_000.0,
    ): GameEngine {
        val state = stateWith(money = money, xp = LevelRules.xpToReach(level), logs = 0)
        state.putBuilding(origin, buildingID)
        return GameEngine(state)
    }

    private fun fill(
        engine: GameEngine,
        until: Double = 100_000.0,
    ) {
        val random = SeededRandom(9)
        var time = 0.0
        while (time <= until) {
            engine.updateHerds(base.advancedBy(time), random)
            engine.hatch(base.advancedBy(time))
            time += 300
        }
    }

    private fun animalCount(engine: GameEngine) =
        engine.state.herds[origin]
            ?.animals
            ?.size ?: engine.state.coops[origin]
            ?.chickens
            ?.size ?: 0

    @Test fun `every home has two upgrades at different levels and rising prices`() {
        assertTrue(HomeUpgrade.capacities == listOf(4, 7, 10) && HomeUpgrade.coopCapacities == listOf(2, 4, 6))
        assertEquals(listOf(2, 4, 6), (1..3).map { HomeUpgrade.capacity("chickenCoop", it) })
        assertEquals(listOf(4, 7, 10), (1..3).map { HomeUpgrade.capacity("barn1", it) })
        assertEquals(HomeUpgrade.buildingIDs.size * 2, HomeUpgrade.all.size)
        for (id in HomeUpgrade.buildingIDs) {
            val second = HomeUpgrade.upgrade(id, 2)
            val third = HomeUpgrade.upgrade(id, 3)
            assertTrue(second != null && third != null)
            assertTrue((second?.unlockLevel ?: 0) > (Building.named(id)?.unlockLevel ?: 99))
            assertTrue((third?.unlockLevel ?: 0) > (second?.unlockLevel ?: 99))
            assertTrue((third?.cost ?: 0.0) > (second?.cost ?: 1e12) / 2)
        }
        assertTrue(HomeUpgrade.all.all { it.unlockLevel <= LevelRules.maxLevel })
        assertEquals(
            HomeUpgrade.all.size,
            HomeUpgrade.all
                .map { it.unlockLevel }
                .toSet()
                .size,
        )
        assertEquals(
            HomeUpgrade.all.size,
            HomeUpgrade.all
                .map { it.careerID }
                .toSet()
                .size,
        )
    }

    @Test fun `every home starts at four animals and grows to seven then ten and a coop two four six`() {
        for (id in HomeUpgrade.buildingIDs) {
            val engine = homeEngine(id)
            val held = if (id == "chickenCoop") listOf(2, 4, 6) else listOf(4, 7, 10)
            fill(engine)
            assertEquals(held[0], animalCount(engine), "$id level 1")
            assertEquals(
                listOf<GameEvent>(GameEvent.HomeUpgraded(origin, 2, HomeUpgrade.upgrade(id, 2)!!.cost)),
                engine.upgradeHome(origin),
            )
            fill(engine, 200_000.0)
            assertEquals(held[1], animalCount(engine), "$id level 2")
            engine.upgradeHome(origin)
            fill(engine, 400_000.0)
            assertEquals(held[2], animalCount(engine), "$id level 3")
            assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.HomeFullyUpgraded)), engine.upgradeHome(origin))
        }
    }

    @Test fun `an upgrade waits for its player level and the money and is recorded in career`() {
        val upgrade = HomeUpgrade.upgrade("chickenCoop", 2)!!
        val early = homeEngine("chickenCoop", upgrade.unlockLevel - 1)
        assertFalse(early.offersHomeUpgrade(origin))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedLevel(upgrade.unlockLevel))),
            early.upgradeHome(origin),
        )
        val poor = homeEngine("chickenCoop", upgrade.unlockLevel, upgrade.cost - 1)
        assertTrue(poor.offersHomeUpgrade(origin))
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedMoney(upgrade.cost))), poor.upgradeHome(origin))
        val ready = homeEngine("chickenCoop", upgrade.unlockLevel, upgrade.cost)
        assertFalse(ready.hasReached(upgrade))
        ready.upgradeHome(origin)
        assertTrue(ready.state.money == 0.0 && ready.homeLevel(origin) == 2 && ready.hasReached(upgrade))
        assertEquals(Rejection.NothingHere, homeEngine("petHouse").homeUpgradeRejection(origin))
    }

    @Test fun `a home keeps every animal it already has under older limits`() {
        val engine = homeEngine("barn2")
        val cow = LivestockSpecies.cow.variants[0]
        val animals = (0 until 8).map { Livestock(it, cow.id, "Cow", origin, bornAt = base) }
        engine.state.herds[origin] = Herd(animals, base)
        assertEquals(8, engine.homeCapacity(origin))
        fill(engine)
        assertEquals(8, animalCount(engine))
        engine.upgradeHome(origin)
        assertEquals(8, engine.homeCapacity(origin))
        engine.upgradeHome(origin)
        fill(engine, 400_000.0)
        assertEquals(10, animalCount(engine))
    }

    @Test fun `a home level follows a moved building and goes with a demolished one`() {
        val engine = homeEngine("goatBarn")
        engine.state.homeLevels[origin] = 2
        val moved = GridPoint(30, 30)
        engine.moveHomeLevel(origin, moved)
        assertEquals(mapOf(moved to 2), engine.state.homeLevels)
        engine.state.putBuilding(moved, "goatBarn")
        engine.state.removeBuilding(origin)
        engine.demolish(engine.state.placedBuildings.first())
        assertTrue(engine.state.homeLevels.isEmpty())
    }

    @Test fun `home levels and reached levels survive saving and old saves load at level one`() {
        val engine = homeEngine("rabbitHutch")
        engine.upgradeHome(origin)
        val text = GameStateJson.encode(engine.state)
        val restored = GameStateJson.decode(text)
        assertTrue(
            restored.homeLevels == mapOf(origin to 2) && restored.career.reachedHomeLevels == setOf("rabbitHutch-2"),
        )
        val saved = Json.parseToJsonElement(text) as JsonObject
        val career = (saved["career"] as JsonObject) - "reachedHomeLevels"
        val old = GameStateJson.decode(JsonObject(saved - "homeLevels" + ("career" to JsonObject(career))).toString())
        assertTrue(old.homeLevels.isEmpty() && old.career.reachedHomeLevels.isEmpty())
        val invalid = GameStateJson.decode(text)
        invalid.homeLevels[origin] = 9
        val invalidText = GameStateJson.encode(invalid)
        assertThrows(Exception::class.java) { GameStateJson.decode(invalidText) }
    }

    // Save validation

    @Test fun `decoration sizes are clamped on decode`() {
        for (size in listOf(-1, 999)) {
            val placed =
                GameStateJson.json.decodeFromString(
                    PlacedDecoration.serializer(),
                    """{"decorationID":"pumpkin","size":$size}""",
                )
            assertTrue(placed.size in placed.decoration.assetNames.indices)
        }
    }

    @Test fun `malformed state is rejected instead of silently reset`() {
        for (
        json in
        listOf(
            """{"money":-1}""",
            """{"xp":-1}""",
            """{"storage":{"wheat":-1}}""",
            """{"plots":"broken"}""",
            """{"farmland":[{"row":999,"col":0}]}""",
        )
        ) {
            assertThrows(Exception::class.java, { GameStateJson.decode(json) }, json)
        }
    }
}
