package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockTrackingTest {
    private fun engine(level: Int = 1) =
        GameEngine().also {
            it.state.xp = LevelRules.xpToReach(level)
            it.state.lessonsLearned.addAll(Lesson.entries)
        }

    @Test
    fun `keys match the reference and always available items have no key`() {
        val engine = engine()
        assertEquals("seed:${Crop.starter.id}", engine.unlockKey(ShopItem.Seed(Crop.starter.id)))
        assertNull(engine.unlockKey(ShopItem.Seed("unknown")))
        assertEquals("building:petHouse", engine.unlockKey(ShopItem.BuildingItem("petHouse")))
        assertEquals("tree:pine", engine.unlockKey(ShopItem.Tree("pine")))
        assertEquals("decoration:rock", engine.unlockKey(ShopItem.DecorationItem("rock")))
        assertEquals("groundCover:dirtPath", engine.unlockKey(ShopItem.GroundCoverItem(GroundCover.dirtPath)))
        listOf(ShopItem.Fence, ShopItem.Water, ShopItem.Fertilizer).forEach { assertNull(engine.unlockKey(it)) }
    }

    @Test
    fun `initial counting is once per farm and later unlocks stay new until opened`() {
        val engine = engine()
        val crop = Crop.all.first { it.unlockLevel > 1 }
        val item = ShopItem.Seed(crop.id)
        assertFalse(engine.isNew(ShopItem.Seed(Crop.starter.id)))
        assertFalse(engine.isNew(item))
        engine.state.xp = LevelRules.xpToReach(crop.unlockLevel)
        engine.countCurrentUnlocksAsSeen()
        assertTrue(engine.isNew(item))
        engine.markSeen(item)
        assertFalse(engine.isNew(item))
        val restored = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
        assertTrue(restored.hasSeen("seed:${crop.id}"))
        assertFalse(restored.isNew(item))
    }

    @Test
    fun `a bigger farmland allowance has its own dot and teaching suppresses dots`() {
        val engine = engine()
        engine.countCurrentUnlocksAsSeen()
        val oldKey = engine.unlockKey(ShopItem.Farmland)
        engine.state.xp = LevelRules.xpToReach(2)
        assertNotEquals(oldKey, engine.unlockKey(ShopItem.Farmland))
        assertEquals("farmland:${engine.state.farmlandLimit}", engine.unlockKey(ShopItem.Farmland))
        assertTrue(engine.isNew(ShopItem.Farmland))
        engine.state.lessonsLearned.clear()
        assertFalse(engine.isNew(ShopItem.Farmland))
        engine.state.lessonsLearned.addAll(Lesson.entries)
        assertTrue(engine.isNew(ShopItem.Farmland))
    }
}
