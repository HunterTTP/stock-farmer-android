package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.countCurrentUnlocksAsSeen
import com.hunter.stockfarmer.core.rules.hasSeen
import com.hunter.stockfarmer.core.rules.isNew
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `selection marks only a successful choice seen and farm snapshots stay independent`() {
        val engine = engine()
        engine.countCurrentUnlocksAsSeen()
        val item = ShopItem.BuildingItem("petHouse")
        assertNotNull(MenuActions.choose(engine, MenuSelection.Shop(item)))
        assertFalse(engine.hasSeen("building:petHouse"))
        engine.state.xp = LevelRules.xpToReach(item.unlockLevel)
        val copy = engine.copy()
        assertNull(MenuActions.choose(engine, MenuSelection.Shop(item)))
        assertTrue(engine.hasSeen("building:petHouse"))
        assertFalse(copy.hasSeen("building:petHouse"))
        assertTrue(copy.isNew(item))
    }
}
