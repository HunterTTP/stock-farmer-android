package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockTest {
    @Test
    fun `original crops keep their unlock levels`() {
        assertEquals(1, Crop.starter.unlockLevel)
        assertEquals(2, Crop.named("carrot")?.unlockLevel)
        assertEquals(24, Crop.named("blackberry")?.unlockLevel)
    }

    @Test
    fun `original buildings keep their unlocks and additions stay reachable`() {
        assertEquals(3, Building.all.first().unlockLevel)
        assertEquals(5, Building.named("chickenCoop")?.unlockLevel)
        assertEquals(19, Building.named("barn2")?.unlockLevel)
        assertTrue(Building.all.all { it.unlockLevel <= LevelRules.maxLevel })
        assertEquals(Building.all.map { it.unlockLevel }.sorted(), Building.all.map { it.unlockLevel })
    }

    @Test
    fun `locked buildings cannot be selected until the level is reached`() {
        val item = ShopItem.BuildingItem("petHouse")
        var engine = GameEngine(stateWith(money = 100_000.0, xp = LevelRules.xpToReach(2)))
        assertEquals(3, engine.requiredLevel(item))
        engine.select(Building.named("petHouse")!!)
        assertEquals(PlayMode.plant, engine.state.mode)

        engine = GameEngine(stateWith(money = 100_000.0, xp = LevelRules.xpToReach(3)))
        assertNull(engine.requiredLevel(item))
        assertEquals(25.0, engine.price(item))
        engine.select(Building.named("petHouse")!!)
        assertEquals(PlayMode.build, engine.state.mode)
    }

    @Test
    fun `locked crops cannot be selected until the level is reached`() {
        val carrot = Crop.named("carrot")!!
        var engine = GameEngine(stateWith(xp = LevelRules.xpToReach(2) - 1))
        engine.select(carrot)
        assertEquals("wheat", engine.state.selectedCropID)
        engine = GameEngine(stateWith(xp = LevelRules.xpToReach(2)))
        engine.select(carrot)
        assertEquals("carrot", engine.state.selectedCropID)
    }

    @Test
    fun `a locked selected crop falls back to the starter crop`() {
        val state = GameState()
        state.selectedCropID = "blackberry"
        assertEquals(Crop.starter, GameEngine(state).selectedCrop)
    }
}

class SeenUnlocksTest {
    @Test
    fun `a new game has not counted anything as seen until told`() {
        val engine = GameEngine()
        assertFalse(engine.hasCountedUnlocks)
        engine.countAsSeen(setOf("seed:wheat"))
        assertTrue(engine.hasCountedUnlocks)
        assertTrue(engine.hasSeen("seed:wheat"))
        assertFalse(engine.hasSeen("seed:carrot"))
    }

    @Test
    fun `seeing more adds to what was seen`() {
        val engine = GameEngine()
        engine.countAsSeen(setOf("seed:wheat"))
        engine.countAsSeen(setOf("seed:carrot"))
        assertTrue(engine.hasSeen("seed:wheat") && engine.hasSeen("seed:carrot"))
    }

    @Test
    fun `what was seen belongs to the game and survives a save`() {
        val engine = GameEngine()
        engine.countAsSeen(setOf("seed:wheat"))
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(GameEngine(loaded).hasSeen("seed:wheat"))
        assertFalse(GameEngine(GameState()).hasSeen("seed:wheat"))
    }
}
