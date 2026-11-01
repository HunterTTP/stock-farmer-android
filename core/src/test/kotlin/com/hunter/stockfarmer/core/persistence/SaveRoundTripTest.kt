package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Holding
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.model.PricePoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.beginMovingTree
import com.hunter.stockfarmer.core.rules.beginNewDayIfDue
import com.hunter.stockfarmer.core.rules.claimStockPull
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SaveRoundTripTest {
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
            Pet(
                breed = "grey",
                name = "Cat",
                position = point,
                meal = AnimalMeal(Instant.ofEpochSecond(1_000_000_000_001)),
            )
        assertThrows(InvalidSaveException::class.java) { GameStateJson.decode(GameStateJson.encode(state)) }
    }

    @Test fun `animal meals are omitted when absent and load unfed`() {
        val state = stateWith(logs = 0)
        state.pets[point] = Pet(breed = "grey", name = "Cat", position = point)
        state.coops[point] =
            Coop(
                chickens = listOf(Chicken(0, "hen-white", "Hen", point, hatchedAt = now)),
                nextChickAt = now,
            )
        state.herds[point] = Herd(listOf(Livestock(0, "cow:blackSpotted", "Cow", point, bornAt = now)), now)
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

    @Test fun `decorations survive a save and a save from before them still loads`() {
        val state = GameState()
        state.putDecoration(point, PlacedDecoration("stool"))
        assertEquals(state.decorations, roundTrip(state).decorations)
        assertTrue(decode(saved(state) - "decorations").decorations.isEmpty())
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
        state.fishPonds[point] = FishPond(PondFish("carp", point, isNaturallyGenerated = false), now)
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

    @Test fun `new farms start on the standard sprinkler`() {
        assertEquals(Decoration.SPRINKLER_ID, GameStateJson.decode("{}").lastSprinklerID)
    }

    @Test fun `ore veins are not for sale and saved rocks keep their size`() {
        assertNull(Decoration.named("oreVein"))
        assertEquals(RockSize.stoneSizes, Decoration.named("rock")!!.rockSizes)
        val state = stateWith(logs = 0)
        state.rocks[point] = RockSize.ruby
        assertEquals(state.rocks, roundTrip(state).rocks)
    }

    @Test fun `fishing gives nothing but fish and the golden nest and egg are gone`() {
        assertTrue(StorageItem.all.none { it.id.startsWith("junk") })
        assertNull(Decoration.named("goldenNest"))
        assertNull(StorageItem.of("goldenEgg"))
    }
}
