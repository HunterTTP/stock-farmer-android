package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ArtisanRecipeTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val ids =
        listOf(
            "grapeJuice",
            "watermelonJuice",
            "goatCheese",
            "agedCheese",
            "roastedCoffee",
            "pineapplePreserves",
            "pricklyPearPreserves",
            "pumpkinPie",
            "turnipSoup",
            "radishPickles",
            "furniture",
            "fineCloth",
            "applePreserves",
            "orangePreserves",
            "peachPreserves",
            "lemonPreserves",
            "bananaPreserves",
        )

    private fun engineWith(building: Building): Pair<GameEngine, GridPoint> {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, logs = 1_000))
        engine.select(building)
        engine.build(GridPoint(15, 15), now)
        return engine to
            engine.state.placedBuildings
                .first()
                .origin
    }

    @Test
    fun `new recipes are optional profitable reachable and have unique goods`() {
        for (id in ids) {
            val recipe = Recipe.named(id)!!
            assertEquals(id, recipe.output.id)
            assertTrue(recipe.output.sellValue * recipe.outputCount > recipe.input.sellValue * recipe.inputCount)
            assertTrue(recipe.unlockLevel <= LevelRules.maxLevel)
            assertEquals(recipe.output, StorageItem.of(id))
        }
        assertEquals(
            StorageItem.all.size,
            StorageItem.all
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(StorageItem.Flour, Recipe.named("grainMachine")?.output)
        assertEquals(StorageItem.Bread, Recipe.named("stoneOven")?.output)
    }

    @Test
    fun `every new batch consumes its ingredients survives saving and collects its own output`() {
        for (id in ids) {
            val recipe = Recipe.named(id)!!
            var (engine, origin) = engineWith(Building.named(recipe.buildingID)!!)
            engine.state.storage = mutableMapOf(recipe.input.id to recipe.inputCount)
            assertTrue(engine.chooseRecipe(id, origin, now).isEmpty())
            assertEquals(listOf<GameEvent>(GameEvent.WorkStarted(origin)), engine.startWork(origin, now))
            assertTrue(engine.state.storage.isEmpty())
            engine = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
            assertEquals(
                WorkshopStatus.Working(1.0),
                engine.workshopStatus(origin, now.advancedBy(recipe.duration - 1)),
            )
            engine.select(HarvestTool.bucket)
            engine.useTool(origin, now.advancedBy(recipe.duration))
            assertEquals(mapOf(id to recipe.outputCount), engine.state.storage)
        }
    }

    @Test
    fun `optional buildings do not increase existing stock quest targets`() {
        assertEquals(24_000.0, QuestRules.stockReferenceDollars(24))
    }

    @Test
    fun `dairy adds milk processing without replacing flour`() {
        val dairy = Building.named("dairy")!!
        val recipe = Recipe.of(dairy)!!
        assertTrue(recipe.input.id == "milk" && recipe.output.id == "cheese")
        assertTrue(recipe.output.sellValue * recipe.outputCount > StorageItem.Milk.sellValue * recipe.inputCount)
        assertTrue(dairy.unlockLevel in 19..LevelRules.maxLevel)
        assertTrue(dairy.assetName != Building.named("grainMachine")?.assetName)
        assertEquals("flour", Recipe.of(Building.named("grainMachine")!!)?.output?.id)
    }

    @Test
    fun `a cheese batch consumes milk waits and retains its state through saving`() {
        val dairy = Building.named("dairy")!!
        val recipe = Recipe.of(dairy)!!
        var (engine, origin) = engineWith(dairy)
        engine.state.storage = mutableMapOf("milk" to recipe.inputCount)
        assertEquals(listOf<GameEvent>(GameEvent.WorkStarted(origin)), engine.startWork(origin, now))
        assertTrue(engine.state.storage.isEmpty())
        engine = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
        assertEquals(
            WorkshopStatus.Working(1.0),
            engine.workshopStatus(origin, now.advancedBy(recipe.duration - 1)),
        )
        engine.select(HarvestTool.bucket)
        assertEquals(
            listOf<GameEvent>(GameEvent.Stored(origin, "cheese", 0)),
            engine.useTool(origin, now.advancedBy(recipe.duration)),
        )
        assertEquals(mapOf("cheese" to 1), engine.state.storage)
        assertEquals(WorkshopStatus.Idle, engine.workshopStatus(origin, now.advancedBy(recipe.duration)))
        assertEquals(
            mapOf("cheese" to 1),
            GameStateJson.decode(GameStateJson.encode(engine.state)).storage,
        )
    }
}
