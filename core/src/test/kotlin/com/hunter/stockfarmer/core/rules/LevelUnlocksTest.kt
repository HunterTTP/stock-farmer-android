package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelUnlocksTest {
    private fun each(body: (Int, LevelUnlocks) -> Unit) {
        for (level in 2..LevelRules.maxLevel) body(level, LevelUnlocks.unlocked(level - 1, level))
    }

    @Test
    fun `everything is listed at its own level and only there`() {
        val seen = mutableMapOf<String, Int>()

        fun note(
            kind: String,
            id: String,
            level: Int,
            listedAt: Int,
        ) {
            assertEquals(level, listedAt, "$kind $id")
            seen.merge("$kind:$id", 1, Int::plus)
        }
        each { level, unlocks ->
            for (crop in unlocks.crops + unlocks.fruitTrees) note("crop", crop.id, crop.unlockLevel, level)
            for (building in unlocks.buildings) note("building", building.id, building.unlockLevel, level)
            for (decoration in unlocks.decorations) note("decoration", decoration.id, decoration.unlockLevel, level)
            for (species in unlocks.trees) note("tree", species.id, species.unlockLevel, level)
            for (recipe in unlocks.recipes) note("recipe", recipe.id, recipe.unlockLevel, level)
            for (upgrade in unlocks.toolUpgrades) {
                note("tool", "${upgrade.tool.rawValue}${upgrade.level}", upgrade.unlockLevel, level)
            }
            for (upgrade in unlocks.homeUpgrades) note("home", upgrade.careerID, upgrade.unlockLevel, level)
            for (rarity in unlocks.fishRarities) note("fish", rarity.rawValue, rarity.fishUnlockLevel, level)
        }
        assertTrue(seen.values.all { it == 1 })
        assertTrue(Crop.all.filter { it.unlockLevel > 1 }.all { seen["crop:${it.id}"] == 1 })
        assertTrue(Building.all.filter { it.unlockLevel > 1 }.all { seen["building:${it.id}"] == 1 })
        assertTrue(Decoration.all.filter { it.unlockLevel > 1 }.all { seen["decoration:${it.id}"] == 1 })
        assertTrue(ToolUpgrade.all.all { seen["tool:${it.tool.rawValue}${it.level}"] == 1 })
        assertTrue(HomeUpgrade.all.all { seen["home:${it.careerID}"] == 1 })
        assertTrue(Rarity.entries.filter { it.fishUnlockLevel > 1 }.all { seen["fish:${it.rawValue}"] == 1 })
    }

    @Test
    fun `farmland tiles add up to the field the level holds`() {
        each { level, unlocks ->
            assertEquals(FarmlandRules.tilesUnlockedByReaching(level), unlocks.farmlandTiles)
            assertEquals(FarmlandRules.tilesUnlocked(level), unlocks.farmlandTotal)
        }
    }

    @Test
    fun `new fish rarities and shifting spawn rates are listed`() {
        assertEquals(listOf(Rarity.uncommon, Rarity.rare), LevelUnlocks.unlocked(9, 10).fishRarities)
        assertEquals(listOf(Rarity.epic), LevelUnlocks.unlocked(14, 15).fishRarities)
        assertEquals(listOf(Rarity.legendary), LevelUnlocks.unlocked(19, 20).fishRarities)
        for (level in listOf(5, 10, 15, 20, 25, 30)) assertTrue(LevelUnlocks.unlocked(level - 1, level).fishRatesShift)
        for (level in listOf(2, 6, 11, 31, 34)) assertFalse(LevelUnlocks.unlocked(level - 1, level).fishRatesShift)
    }

    @Test
    fun `stock market quests are listed at the house level`() {
        assertTrue(LevelUnlocks.unlocked(QuestRules.stockQuestLevel - 1, QuestRules.stockQuestLevel).stockQuests)
        assertFalse(LevelUnlocks.unlocked(QuestRules.stockQuestLevel, QuestRules.stockQuestLevel + 1).stockQuests)
    }

    @Test
    fun `a jump of several levels lists every level it crossed`() {
        val jump = LevelUnlocks.unlocked(4, 9)
        val steps = (5..9).map { LevelUnlocks.unlocked(it - 1, it) }
        assertEquals(5..9, jump.levels)
        assertTrue(jump.crops == steps.flatMap { it.crops } && jump.buildings == steps.flatMap { it.buildings })
        assertEquals(steps.sumOf { it.farmlandTiles }, jump.farmlandTiles)
        assertEquals(steps.sumOf { it.toolUpgrades.size }, jump.toolUpgrades.size)
    }

    @Test
    fun `a level up event says where it started from`() {
        val engine = GameEngine(stateWith(xp = LevelRules.xpToReach(5) - 1))
        engine.state.xp = LevelRules.xpToReach(7)
        val first = engine.levelUpEvents(4).first()
        assertTrue(first is GameEvent.LeveledUp)
        first as GameEvent.LeveledUp
        assertTrue(first.level == 7 && first.from == 4)
    }
}
