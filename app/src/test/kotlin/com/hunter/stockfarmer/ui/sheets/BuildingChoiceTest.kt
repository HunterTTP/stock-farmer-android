package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.game.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BuildingChoiceTest {
    private val origin = GridPoint(10, 10)
    private val now = Instant.ofEpochSecond(60_000_000)

    @Test
    fun `chick picker includes random hen and marks only the chosen variant seen`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "chickenCoop", null)
        engine.state.coops[origin] = Coop(nextChickAt = now)
        val choices = ChickChoice.all(engine, origin)
        assertTrue(choices.first().selected)
        assertEquals(ChickenVariant.available, choices.drop(1).map { it.variant })
        assertTrue(choices.drop(1).all { it.isNew })
        val variant = ChickenVariant.available.first { it.isRooster }
        val result =
            GameSession(
                engine,
            ).sheetAction(SheetTarget.Building(origin), SheetAction.NextChick(variant.id), now)
        assertNull(result.refusal)
        val picked = ChickChoice.all(engine, origin)
        assertFalse(picked.first().selected)
        assertTrue(picked.single { it.variant == variant }.selected)
        assertFalse(picked.single { it.variant == variant }.isNew)
        assertTrue(picked.filter { it.variant != null && it.variant != variant }.all { it.isNew })
    }

    @Test
    fun `recipes keep base option unlocked and selecting an unlocked recipe clears its dot`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "tailorTable", null)
        val session = GameSession(engine)
        val initial = recipeChoices(engine, origin)
        val base = initial.first { it.recipe.id == "tailorTable" }
        assertNull(base.requiredLevel)
        assertTrue(base.isNew)
        val cotton = initial.first { it.recipe.id == "cottonCloth" }
        assertNotNull(cotton.requiredLevel)
        assertFalse(cotton.isNew)
        assertNotNull(
            session.sheetAction(SheetTarget.Building(origin), SheetAction.Recipe(cotton.recipe.id), now).refusal,
        )
        engine.state.xp = LevelRules.xpToReach(cotton.recipe.unlockLevel)
        assertTrue(recipeChoices(engine, origin).first { it.recipe.id == cotton.recipe.id }.isNew)
        assertNull(session.sheetAction(SheetTarget.Building(origin), SheetAction.Recipe(cotton.recipe.id), now).refusal)
        val chosen = recipeChoices(engine, origin).first { it.recipe.id == cotton.recipe.id }
        assertTrue(chosen.selected)
        assertFalse(chosen.isNew)
    }

    @Test
    fun `pet summon closes only on success`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "petHouse", null)
        val session = GameSession(engine)
        val target = SheetTarget.Building(origin)
        engine.state.pets[origin] = Pet(breed = Cat.starter.id, name = "Milo", position = GridPoint(20, 20))
        val front = GridPoint(11, 10)
        for (row in front.row - 3..front.row + 3) {
            for (col in front.col - 3..front.col + 3) {
                engine.state.rocks[GridPoint(row, col)] =
                    com.hunter.stockfarmer.core.catalog.RockSize.small
            }
        }
        assertFalse(session.sheetAction(target, SheetAction.Summon, now).shouldClose)
        engine.state.rocks.clear()
        val result = session.sheetAction(target, SheetAction.Summon, now)
        assertNull(result.refusal)
        assertTrue(result.shouldClose)
        assertEquals(
            front,
            engine.state.pets
                .getValue(origin)
                .position,
        )
    }
}
