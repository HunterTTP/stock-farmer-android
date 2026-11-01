package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Workshop
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RecipeChoiceTest {
    private val now = Instant.ofEpochSecond(40_000_000)

    private fun engine(): Pair<GameEngine, GridPoint> {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, logs = 1_000))
        engine.select(Building.named("tailorTable")!!)
        engine.build(GridPoint(15, 15), now)
        val origin =
            engine.state.placedBuildings
                .first()
                .origin
        engine.state.storage = mutableMapOf("cotton" to 3, "wool" to 3)
        return engine to origin
    }

    private fun reload(engine: GameEngine) = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))

    @Test
    fun `cotton cloth keeps its existing quantities after the crop moves later`() {
        val recipe = Recipe.named("cottonCloth")!!
        assertTrue(recipe.input.id == "cotton" && recipe.inputCount == 3)
        assertTrue(recipe.output == StorageItem.Cloth && recipe.outputCount == 2)
        assertEquals(150.0, recipe.outputValue)
        assertTrue(recipe.outputValue * 2 < recipe.input.sellValue * 3)
        assertEquals(StorageItem.Wool, Recipe.of(Building.named("tailorTable")!!)?.input)
    }

    @Test
    fun `recipe choice and batch survive saving and cannot change while working`() {
        var (engine, origin) = engine()
        assertTrue(engine.chooseRecipe("cottonCloth", origin, now).isEmpty())
        assertTrue("selectedWorkshopRecipes" in GameStateJson.encode(engine.state))
        engine = reload(engine)
        assertEquals(listOf<GameEvent>(GameEvent.WorkStarted(origin)), engine.startWork(origin, now))
        assertEquals(mapOf("wool" to 3), engine.state.storage)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.WorkInProgress)),
            engine.chooseRecipe("tailorTable", origin, now),
        )
        assertTrue(engine.state.fullBuildings(now.advancedBy(8 * 60.0)).isEmpty())
        engine = reload(engine)
        assertEquals(
            WorkshopStatus.Working(1.0),
            engine.workshopStatus(origin, now.advancedBy(12 * 60.0 - 1)),
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.GoodsWaiting)),
            engine.chooseRecipe("tailorTable", origin, now.advancedBy(12 * 60.0)),
        )
        engine.select(HarvestTool.bucket)
        val stored = GameEvent.Stored(origin, "cloth", 0)
        assertEquals(listOf<GameEvent>(stored, stored), engine.useTool(origin, now.advancedBy(12 * 60.0)))
        assertEquals(mapOf("wool" to 3, "cloth" to 2), engine.state.storage)
    }

    @Test
    fun `idle recipe choice moves with its building and demolition clears it`() {
        var (engine, origin) = engine()
        engine.chooseRecipe("cottonCloth", origin, now)
        engine.beginMoving(engine.building(origin)!!)
        val moved = GridPoint(30, 30)
        engine.movePlacement(moved)
        engine.confirmPlacement(now)
        engine = reload(engine)
        engine.startWork(moved, now)
        assertEquals(mapOf("wool" to 3), engine.state.storage)
        engine.demolish(engine.building(moved)!!)
        assertTrue(engine.state.selectedWorkshopRecipes.isEmpty())
    }

    @Test
    fun `a recipe cannot be selected at the wrong workshop or before its crop unlocks`() {
        val (engine, origin) = engine()
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NothingHere)),
            engine.chooseRecipe("stoneOven", origin, now),
        )
        engine.state.xp = LevelRules.xpToReach(18)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedLevel(31))),
            engine.chooseRecipe("cottonCloth", origin, now),
        )
        assertEquals(mapOf("cotton" to 3, "wool" to 3), engine.state.storage)
    }

    @Test
    fun `old saves default to the original recipe and invalid recipe keys are rejected`() {
        val (engine, origin) = engine()
        val old = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(old.selectedWorkshopRecipes.isEmpty())
        assertEquals("tailorTable", old.workshopRecipe(origin)?.id)
        engine.state.selectedWorkshopRecipes[origin] = "grainMachine"
        val invalid = GameStateJson.encode(engine.state)
        assertThrows(Exception::class.java) { GameStateJson.decode(invalid) }
        engine.state.selectedWorkshopRecipes.remove(origin)
        engine.state.workshops[origin] = Workshop(now, "grainMachine")
        val wrongBatch = GameStateJson.encode(engine.state)
        assertThrows(Exception::class.java) { GameStateJson.decode(wrongBatch) }
    }

    @Test
    fun `the saved batch recipe wins over a different idle preference`() {
        val (engine, origin) = engine()
        engine.chooseRecipe("cottonCloth", origin, now)
        engine.startWork(origin, now)
        engine.state.selectedWorkshopRecipes[origin] = "tailorTable"
        val saved = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals("cottonCloth", saved.workshops[origin]?.recipeID)
        assertEquals("cottonCloth", saved.workshopRecipe(origin)?.id)
        assertTrue(saved.fullBuildings(now.advancedBy(8 * 60.0)).isEmpty())
    }

    @Test
    fun `restored recipe choices still respect new unlocks without disabling old workshops`() {
        val (engine, origin) = engine()
        engine.state.selectedWorkshopRecipes[origin] = "cottonCloth"
        engine.state.xp = LevelRules.xpToReach(18)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedLevel(31))),
            engine.startWork(origin, now),
        )
        assertEquals(mapOf("cotton" to 3, "wool" to 3), engine.state.storage)
        engine.state.selectedWorkshopRecipes.remove(origin)
        engine.state.xp = 0
        assertEquals(listOf<GameEvent>(GameEvent.WorkStarted(origin)), engine.startWork(origin, now))
        assertEquals(mapOf("cotton" to 3), engine.state.storage)
    }

    @Test
    fun `alternative recipes do not duplicate a workshop in the quest pool`() {
        val workshops = QuestRules.goals(LevelRules.maxLevel).flatten().filterIsInstance<QuestGoal.Workshop>()
        assertEquals(workshops.size, workshops.toSet().size)
        assertEquals(1, workshops.count { it == QuestGoal.Workshop("tailorTable") })
    }
}
