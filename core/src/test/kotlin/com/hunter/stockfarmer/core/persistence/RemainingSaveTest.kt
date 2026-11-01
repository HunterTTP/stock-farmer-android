package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Holding
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PricePoint
import com.hunter.stockfarmer.core.model.XPSample
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.beginMovingTree
import com.hunter.stockfarmer.core.rules.beginNewDayIfDue
import com.hunter.stockfarmer.core.rules.claimStockPull
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.destroyFence
import com.hunter.stockfarmer.core.rules.isOpenGround
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.learnWheatIsRipe
import com.hunter.stockfarmer.core.rules.reopen
import com.hunter.stockfarmer.core.rules.upgrade
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Remaining Swift save assertions use Android's own encoding, since the two platforms keep separate saves. */
class RemainingSaveTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val point = GridPoint(15, 15)

    private fun saved(state: GameState) =
        GameStateJson.json.parseToJsonElement(GameStateJson.encode(state)) as JsonObject

    private fun decode(fields: Map<String, JsonElement>) = GameStateJson.decode(JsonObject(fields).toString())

    private fun roundTrip(state: GameState): GameState =
        GameStateJson.decode(GameStateJson.encode(state)).also {
            assertTrue(sameState(state, it))
        }

    @Test fun `pets hens and livestock reject invalid meal dates`() {
        val state = stateWith(logs = 0)
        state.pets[point] =
            com.hunter.stockfarmer.core.model.Pet(
                breed = "grey",
                name = "Cat",
                position = point,
                meal = AnimalMeal(Instant.ofEpochSecond(1_000_000_000_001)),
            )
        assertThrows(InvalidSaveException::class.java) { GameStateJson.decode(GameStateJson.encode(state)) }
    }

    @Test fun `animal meals are omitted when absent and load unfed`() {
        val state = stateWith(logs = 0)
        state.pets[point] =
            com.hunter.stockfarmer.core.model
                .Pet(breed = "grey", name = "Cat", position = point)
        state.coops[point] =
            Coop(
                chickens = listOf(Chicken(0, "hen-white", "Hen", point, hatchedAt = now)),
                nextChickAt = now,
            )
        state.herds[point] =
            Herd(
                listOf(
                    com.hunter.stockfarmer.core.model
                        .Livestock(0, "cow:blackSpotted", "Cow", point, bornAt = now),
                ),
                now,
            )
        val text = GameStateJson.encode(state)
        assertFalse("\"meal\"" in text)
        assertTrue(sameState(state, GameStateJson.decode(text)))
    }

    @Test fun `servings eaten are saved and older tiles load untouched`() {
        val state = GameState()
        state.plots[point] = Plot("wheat", now, servingsEaten = 1)
        assertEquals(1, roundTrip(state).plots[point]?.servingsEaten)
        val json = saved(state)
        val plots = json["plots"] as JsonArray
        val old =
            JsonArray(
                plots.map {
                    if (it is JsonObject &&
                        "servingsEaten" in it
                    ) {
                        JsonObject(it - "servingsEaten")
                    } else {
                        it
                    }
                },
            )
        assertEquals(0, decode(json + ("plots" to old)).plots[point]?.servingsEaten)
    }

    @Test fun `styles survive a save and a save from before styles still loads`() {
        val state = GameState()
        state.putBuilding(point, "barn2", "grey")
        val loaded = roundTrip(state)
        assertEquals(state.buildingStyles, loaded.buildingStyles)
        assertEquals(
            "grey",
            loaded.placedBuildings
                .single()
                .building.styleID,
        )
        val old = decode(saved(state) - "buildingStyles")
        assertTrue(old.buildingStyles.isEmpty())
        assertEquals(Building.named("barn2"), old.placedBuildings.single().building)
    }

    @Test fun `a save without a career is seeded from the farm`() {
        val state = GameState()
        state.xp = 70
        state.playTime = 900.0
        state.plots[GridPoint(25, 25)] = Plot("wheat", Instant.EPOCH)
        state.storage[StorageItem.Fish(FishKind.all[0]).id] = 2
        state.putBuilding(GridPoint(10, 10), "petHouse")
        val old = decode(saved(state) - "career")
        assertTrue("wheat" in old.career.plantedCropIDs)
        assertEquals(0.0, old.career.heaviestCatches[FishKind.all[0].id])
        assertEquals(setOf("petHouse"), old.career.builtBuildingIDs)
        assertEquals(0L, old.career.grossIncomeCents)
        assertEquals(listOf(XPSample(900.0, 70)), old.career.xpHistory)
    }

    @Test fun `a career saved before terrain was kept is seeded from the farm`() {
        val state = GameState()
        state.water[GridPoint(5, 5)] = WaterStyle.medium
        val json = saved(state)
        val career = JsonObject((json["career"] as JsonObject) - "placedTerrain")
        assertEquals(setOf("water-medium", "farmland"), decode(json + ("career" to career)).career.placedTerrainIDs)
    }

    @Test fun `a career from the first release still opens`() {
        val career =
            GameStateJson.json.parseToJsonElement(
                """{"plantedCropIDs":["wheat"],"heaviestCatches":{},
            "builtBuildingIDs":[],"placedDecorationIDs":[],"workedAnimalIDs":[],"grossIncomeCents":500,"xpHistory":[]}""",
            )
        val loaded = decode(saved(GameState()) + ("career" to career))
        assertEquals(setOf("wheat"), loaded.career.plantedCropIDs)
        assertEquals(500L, loaded.career.grossIncomeCents)
        assertEquals(0, loaded.career.cropsHarvested)
    }

    @Test fun `decorations survive a save and a save from before them still loads`() {
        val state = GameState()
        state.putDecoration(point, PlacedDecoration("stool"))
        assertEquals(state.decorations, roundTrip(state).decorations)
        assertTrue(decode(saved(state) - "decorations").decorations.isEmpty())
    }

    @Test fun `a damaged field is reported so the original save can be preserved`() {
        assertThrows(Exception::class.java) {
            GameStateJson.decode("""{"money":42,"mode":"nonsense","selectedWaterStyle":7}""")
        }
    }

    @Test fun `herds survive a save and a save from before herds still loads`() {
        val state = GameState()
        state.herds[point] = Herd(emptyList(), now)
        assertEquals(state.herds, roundTrip(state).herds)
        assertTrue(GameStateJson.decode("""{"money":5}""").herds.isEmpty())
    }

    @Test fun `the last night begun is saved and an old save without it still loads`() {
        val engine = GameEngine()
        val midnight = Instant.ofEpochSecond(1001L * 1440 + 1)
        engine.beginNewDayIfDue(midnight)
        assertEquals(NightPulse.day(midnight, 0.0), roundTrip(engine.state).lastNewDayNight)
        assertNull(decode(saved(engine.state) - "lastNewDayNight").lastNewDayNight)
    }

    @Test fun `the flag and held fish survive saves and older fish are wild`() {
        val state = GameState()
        state.heldItemID = "fish-carp"
        state.fishPonds[point] =
            com.hunter.stockfarmer.core.model.FishPond(
                com.hunter.stockfarmer.core.model
                    .PondFish("carp", point, isNaturallyGenerated = false),
                now,
            )
        val loaded = roundTrip(state)
        assertEquals("fish-carp", loaded.heldItemID)
        assertFalse(loaded.fishPonds[point]!!.fish!!.isNaturallyGenerated)
        val json = saved(state)
        val ponds =
            JsonArray(
                (json["fishPonds"] as JsonArray).map { entry ->
                    if (entry is JsonObject && "fish" in entry) {
                        JsonObject(
                            entry +
                                ("fish" to JsonObject((entry["fish"] as JsonObject) - "isNaturallyGenerated")),
                        )
                    } else {
                        entry
                    }
                },
            )
        assertTrue(decode(json + ("fishPonds" to ponds)).fishPonds[point]!!.fish!!.isNaturallyGenerated)
    }

    @Test fun `holdings saved without a previous close make the next pull due`() {
        val state = GameState()
        state.holdings["AAPL"] = Holding("AAPL", 1, 100, 100, now)
        state.priceHistory["AAPL"] = listOf(PricePoint(now, 100))
        val engine = GameEngine(state)
        engine.claimStockPull(now)
        assertNull(GameStateJson.decode(GameStateJson.encode(state)).lastStockPullAt)
    }

    @Test fun `a saved tree without an origin flag must be cut down`() {
        val tree =
            GameStateJson.json.decodeFromString<PlacedTree>(
                """{"treeID":"pine-small","colorID":"green","grewAt":"2000-01-01T00:00:00Z"}""",
            )
        val engine = GameEngine(stateWith(xp = lateGameXP))
        engine.state.putTree(point, tree)
        engine.beginMovingTree(point)
        assertFalse(engine.isPlacing)
    }

    @Test fun `an old save still loads past the waiting step`() {
        val engine =
            GameEngine(GameStateJson.decode("""{"hasHarvested":true,"lessonsLearned":["plant","pickHarvest"]}"""))
        assertEquals(Lesson.harvest, engine.currentLesson)
        engine.learnWheatIsRipe(now)
        assertEquals(Lesson.harvest, engine.currentLesson)
    }

    @Test fun `a save holding the old saw still loads`() {
        val state = GameStateJson.decode("""{"harvestTool":"saw","money":5}""")
        assertEquals(HarvestTool.scythe, state.harvestTool)
        assertEquals(5.0, state.money)
    }

    @Test fun `retired tractor selection loads as scythe and drops only its selling preference`() {
        val state =
            GameStateJson.decode(
                """{"harvestTool":"tractor","sellingTools":["tractor","bucket"],"money":120}""",
            )
        assertEquals(HarvestTool.scythe, state.harvestTool)
        assertEquals(setOf(HarvestTool.bucket), state.sellingTools)
        assertEquals(120.0, state.money)
        roundTrip(state)
    }

    @Test fun `retired tractor selling preference does not change the selected tool`() {
        val state = GameStateJson.decode("""{"harvestTool":"bucket","sellingTools":["tractor","scythe"]}""")
        assertEquals(HarvestTool.bucket, state.harvestTool)
        assertEquals(setOf(HarvestTool.scythe), state.sellingTools)
    }

    @Test fun `older saves default to the standard sprinkler or keep an active big one`() {
        for (selected in listOf(Decoration.SPRINKLER_ID, Decoration.BIG_SPRINKLER_ID)) {
            val state = GameStateJson.decode("""{"selectedDecorationID":"$selected","fertilizesWithSprinklers":true}""")
            assertEquals(selected, state.lastSprinklerID)
        }
        assertEquals(Decoration.SPRINKLER_ID, GameStateJson.decode("{}").lastSprinklerID)
    }

    @Test fun `ore veins are not for sale and old selections are cleared`() {
        assertNull(Decoration.named("oreVein"))
        assertEquals(RockSize.stoneSizes, Decoration.named("rock")!!.rockSizes)
        val state = stateWith(logs = 0)
        state.selectedDecorationID = "oreVein"
        state.lastDecorationID = "oreVein"
        state.selectedDecorationSizes["oreVein"] = 4
        state.rocks[point] = RockSize.ruby
        val restored = GameStateJson.decode(GameStateJson.encode(state))
        assertNull(restored.selectedDecorationID)
        assertNull(restored.lastDecorationID)
        assertTrue(restored.selectedDecorationSizes.isEmpty())
        assertEquals(state.rocks, restored.rocks)
    }

    @Test fun `fishing gives nothing but fish and old junk in a save is dropped`() {
        assertTrue(StorageItem.all.none { it.id.startsWith("junk") })
        assertEquals(
            mapOf("fish-carp" to 1),
            GameStateJson.decode("""{"storage":{"junk-boot":2,"junk-branch":1,"fish-carp":1}}""").storage,
        )
    }

    @Test fun `golden nest is removed and old saves keep their other contents`() {
        assertNull(Decoration.named("goldenNest"))
        assertNull(StorageItem.of("goldenEgg"))
        val state = stateWith(money = 100.0, logs = 0)
        state.putDecoration(GridPoint(20, 20), PlacedDecoration("stool"))
        state.storage = mutableMapOf("egg" to 2, "goldenEgg" to 3, "wheat" to 4)
        state.eggs = listOf(Egg(point, 0.0, 0.0, point, 0))
        val json = saved(state).toMutableMap()
        val tile = GameStateJson.json.encodeToJsonElement(GridPoint.serializer(), point)
        json["decorations"] =
            JsonArray(
                (json["decorations"] as JsonArray) + tile +
                    JsonObject(mapOf("decorationID" to JsonPrimitive("goldenNest"), "size" to JsonPrimitive(0))),
            )
        json["selectedDecorationID"] = JsonPrimitive("goldenNest")
        json["lastDecorationID"] = JsonPrimitive("goldenNest")
        json["selectedDecorationSizes"] = JsonObject(mapOf("goldenNest" to JsonPrimitive(0)))
        json["eggs"] =
            JsonArray(
                (json["eggs"] as JsonArray).map {
                    JsonObject((it as JsonObject) + ("isGolden" to JsonPrimitive(true)))
                },
            )
        val restored = decode(json)
        assertEquals(state.decorations, restored.decorations)
        assertEquals(850.0, restored.money)
        assertEquals(mapOf("egg" to 5, "wheat" to 4), restored.storage)
        assertEquals(state.eggs, restored.eggs)
        assertNull(restored.selectedDecorationID)
        assertNull(restored.lastDecorationID)
        assertTrue(restored.selectedDecorationSizes.isEmpty())
        assertFalse("golden" in GameStateJson.encode(restored))
        roundTrip(restored)
    }

    @Test fun `corrupt retired egg counts are rejected`() {
        val state = stateWith(logs = 0)
        state.storage["goldenEgg"] = -1
        assertThrows(InvalidSaveException::class.java) { GameStateJson.decode(GameStateJson.encode(state)) }
    }

    @Test fun `a save with a fruit tree on all four tiles loads on its bottom row`() {
        val state = GameState()
        val origin = GridPoint(9, 10)
        val apple = Crop.named("apple")!!
        for (tile in apple.footprint(origin)) state.plots[tile] = Plot(apple.id, now, origin)
        val other = GridPoint(30, 30)
        state.plots[other] = Plot("wheat", now)
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertEquals(setOf(GridPoint(10, 10), GridPoint(10, 11), other), loaded.plots.keys)
        roundTrip(loaded)
    }

    @Test fun `literal fruit tree migration keeps an unrelated upper crop`() {
        val state = GameState()
        val origin = GridPoint(10, 10)
        state.plots[origin] = Plot("wheat", now)
        for (tile in listOf(GridPoint(10, 11), GridPoint(11, 10), GridPoint(11, 11))) {
            state.plots[tile] = Plot("apple", now, origin)
        }
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertEquals(3, loaded.plots.size)
        assertEquals("wheat", loaded.plots[origin]?.cropID)
        assertNull(loaded.plots[GridPoint(10, 11)])
        assertEquals("apple", loaded.fruitTree(GridPoint(11, 10))?.second?.cropID)
    }

    @Test fun `removed covers migrate without losing raised bed crops`() {
        for (id in listOf("sandPath", "raisedBed", "barnFloor", "stableFloor")) {
            val state = stateWith(money = 100.0)
            state.groundCover[point] = GroundCover.dirtPath
            state.farmland.add(point)
            state.plots[point] = Plot("wheat", now)
            state.selectedTerrain = LandscapeTool.groundCover(GroundCover.dirtPath)
            val text = GameStateJson.encode(state).replace("dirtPath", id)
            val restored = GameStateJson.decode(text)
            assertEquals(if (id == "raisedBed") null else GroundCover.dirtPath, restored.groundCover[point])
            assertTrue(point in restored.farmland)
            assertEquals("wheat", restored.plots[point]?.cropID)
            assertEquals(LandscapeTool.groundCover(GroundCover.dirtPath), restored.selectedTerrain)
            roundTrip(restored)
        }
    }

    @Test fun `retired fences and gates become ordinary wood and keep their tiles`() {
        val retired =
            listOf(
                "darkWood",
                "rustic",
                "picket",
                "lightPicket",
                "goldenPicket",
                "redPicket",
                "whiteRail",
                "brownRail",
                "darkRail",
                "woodGate",
                "darkWoodGate",
                "rusticGate",
            )
        for (id in retired) {
            val state = stateWith(money = 100.0)
            state.fences[point] = id
            state.selectedFenceColorID = id
            val json =
                saved(state) + (
                    "openGates" to
                        JsonArray(
                            listOf(GameStateJson.json.encodeToJsonElement(GridPoint.serializer(), point)),
                        )
                )
            val restored = decode(json)
            assertEquals(mapOf(point to "wood"), restored.fences)
            assertEquals("wood", restored.selectedFenceColorID)
            assertEquals(100.0, restored.money)
            val engine = GameEngine(restored)
            assertFalse(engine.isOpenGround(point))
            roundTrip(restored)
            assertEquals(listOf<GameEvent>(GameEvent.Unfenced(point, 2.0)), engine.destroyFence(point))
        }
    }

    @Test fun `a save with goat and pig barns built from the old barn loads as the new barns`() {
        val state = GameState()
        for (col in listOf(10, 20, 30, 40)) {
            val home = GridPoint(10, col)
            state.putBuilding(home, "barn1")
            state.herds[home] = Herd(emptyList(), now)
        }
        val json = saved(state)
        val species = listOf("goat", "pig", "sheep", null).iterator()
        val herds =
            JsonArray(
                (json["herds"] as JsonArray).map { entry ->
                    if (entry is JsonObject && "animals" in entry) {
                        val id = species.next()
                        if (id == null) entry else JsonObject(entry + ("speciesID" to JsonPrimitive(id)))
                    } else {
                        entry
                    }
                },
            )
        val loaded = decode(json + ("herds" to herds))
        assertEquals(
            listOf("goatBarn", "pigBarn", "barn1", "barn1"),
            listOf(10, 20, 30, 40).map { loaded.buildings[GridPoint(10, it)] },
        )
    }

    @Test fun `a rooster from an old save becomes a hen of the same color`() {
        val hen = Chicken(0, "hen-golden", "x", point, hatchedAt = now)
        val text = GameStateJson.json.encodeToString(Chicken.serializer(), hen).replace("hen-golden", "rooster-golden")
        val loaded = GameStateJson.json.decodeFromString(Chicken.serializer(), text)
        assertEquals(
            com.hunter.stockfarmer.core.catalog.ChickenVariant
                .named("hen-golden"),
            loaded.kind,
        )
    }

    @Test fun `a drinking trough from before is taken off the map with its animals`() {
        val state = GameState()
        state.putBuilding(point, "trough")
        state.putBuilding(GridPoint(30, 30), "shed")
        state.herds[point] = Herd(emptyList(), now)
        state.eggs = listOf(Egg(point, 0.0, 0.0, point, 0))
        val engine = GameEngine(GameStateJson.decode(GameStateJson.encode(state)))
        engine.reopen(now, SeededRandom(1))
        assertEquals(
            listOf("shed"),
            engine.state.buildings.values
                .sorted(),
        )
        assertNull(engine.state.herds[point])
        assertTrue(engine.state.eggs.isEmpty())
    }

    @Test fun `literal historical save migrates retired tools and buildings`() {
        val folder =
            java.nio.file.Files
                .createTempDirectory("remaining-ios-save")
        val file = folder.resolve("farm.json")
        try {
            java.nio.file.Files.writeString(
                file,
                """{"schemaVersion":1,"state":{"money":120,"harvestTool":"saw",
                "hasRocks":true,"buildings":[{"row":10,"col":10},"trough",{"row":20,"col":20},"marketStand"],
                "buildingStyles":[{"row":20,"col":20},"big"]}}""",
            )
            val engine = GameEngine(FileSaveStore(file).load()!!)
            engine.reopen(now, SeededRandom(1))
            assertEquals(120.0, engine.state.money)
            assertEquals(HarvestTool.scythe, engine.state.harvestTool)
            assertNull(engine.state.buildings[GridPoint(10, 10)])
            assertEquals("marketStandLarge", engine.state.buildings[GridPoint(20, 20)])
        } finally {
            java.nio.file.Files
                .deleteIfExists(file)
            java.nio.file.Files
                .deleteIfExists(folder)
        }
    }

    @Test fun `old active tree selection moves to terrain and drops the decorate tree flag`() {
        val state = stateWith(xp = lateGameXP)
        state.mode = com.hunter.stockfarmer.core.model.PlayMode.landscape
        state.selectedLandscape = LandscapeTool.tree
        val restored = decode(saved(state) + ("decoratesWithTrees" to JsonPrimitive(true)))
        assertEquals(LandscapeTool.tree, restored.selectedTerrain)
        assertFalse("decoratesWithTrees" in saved(restored))
    }

    @Test fun `tool levels survive saving and an early axe level still loads`() {
        val engine = GameEngine(stateWith(money = 10_000.0, xp = lateGameXP, logs = 0))
        engine.upgrade(HarvestTool.axe)
        assertEquals(mapOf("axe" to 2), roundTrip(engine.state).toolLevels)
        val early = decode(saved(GameState()) + ("axeLevel" to JsonPrimitive(3)))
        assertEquals(mapOf("axe" to 3), early.toolLevels)
    }
}
