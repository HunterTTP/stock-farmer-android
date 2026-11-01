package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmlandUnlockTest {
    @Test
    fun `level one has the four starting tiles`() {
        assertEquals(4, FarmlandRules.tilesUnlocked(1))
        assertEquals(4, GameState.startingFarmland.size)
    }

    @Test
    fun `levels below twenty unlock twice their number`() {
        assertEquals(0, FarmlandRules.tilesUnlockedByReaching(1))
        for (level in 2 until 20) assertEquals(2 * level, FarmlandRules.tilesUnlockedByReaching(level))
        assertEquals(4 + 4 + 6, FarmlandRules.tilesUnlocked(3))
    }

    @Test
    fun `levels from twenty unlock their number with no cap`() {
        for (level in 20..LevelRules.maxLevel) assertEquals(level, FarmlandRules.tilesUnlockedByReaching(level))
        assertEquals(382, FarmlandRules.tilesUnlocked(19))
        assertEquals(402, FarmlandRules.tilesUnlocked(20))
        assertEquals(657, FarmlandRules.tilesUnlocked(30))
        assertEquals(787, FarmlandRules.tilesUnlocked(LevelRules.maxLevel))
    }

    @Test
    fun `the limit follows the players level`() {
        assertEquals(4, stateWith(xp = 0).farmlandLimit)
        assertEquals(
            FarmlandRules.tilesUnlocked(8),
            stateWith(xp = LevelRules.xpToReach(8)).farmlandLimit,
        )
    }

    @Test
    fun `tiles unlocked at a level add up every levels own unlocks`() {
        var total = GameState.startingFarmland.size
        for (level in 1..LevelRules.maxLevel) {
            total += FarmlandRules.tilesUnlockedByReaching(level)
            assertEquals(total, FarmlandRules.tilesUnlocked(level))
        }
        assertEquals(GameState.startingFarmland.size, FarmlandRules.tilesUnlocked(0))
    }
}
