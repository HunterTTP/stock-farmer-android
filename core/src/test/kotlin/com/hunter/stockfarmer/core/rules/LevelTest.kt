package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LevelTest {
    private val now = Instant.ofEpochSecond(5_000_000)
    private val tile = GridPoint(24, 24)

    @Test
    fun `the first level takes three harvests of a full starting field`() {
        // Four farmland tiles, three harvests each, 2 XP a harvest of wheat.
        assertEquals(3 * 4 * 2, LevelRules.xpToLeave(1))
        assertEquals(1, LevelRules.levelForXP(23))
        assertEquals(2, LevelRules.levelForXP(24))
    }

    @Test
    fun `every level takes three harvests of its newest crop on all its farmland`() {
        for (level in 1 until LevelRules.maxLevel) {
            val crop = Crop.all.first { it.unlockLevel == level }
            val tiles = FarmlandRules.tilesUnlocked(level)
            assertEquals(3 * tiles * (crop.unlockLevel + 1), LevelRules.xpToLeave(level))
        }
    }

    @Test
    fun `levels follow the XP curve and stop at the last crop`() {
        assertEquals(24, LevelRules.xpToReach(2))
        assertEquals(24 + 72, LevelRules.xpToReach(3))
        assertEquals(2, LevelRules.levelForXP(95))
        assertEquals(3, LevelRules.levelForXP(96))
        assertEquals(Crop.progression.size, LevelRules.maxLevel)
        assertEquals(LevelRules.maxLevel, LevelRules.levelForXP(LevelRules.xpToReach(LevelRules.maxLevel)))
        assertEquals(LevelRules.maxLevel, LevelRules.levelForXP(1_000_000_000))
        assertEquals(LevelRules.xpToReach(LevelRules.maxLevel), LevelRules.xpToReach(LevelRules.maxLevel + 6))
    }

    @Test
    fun `xp thresholds match their definition for every level`() {
        var expected = 0
        for (level in 1..LevelRules.maxLevel) {
            assertEquals(expected, LevelRules.xpToReach(level))
            val tiles = FarmlandRules.tilesUnlocked(level)
            expected += if (level < LevelRules.maxLevel) 3 * tiles * (Crop.all[level - 1].unlockLevel + 1) else 0
        }
    }

    @Test
    fun `level and XP are consistent for every level`() {
        for (level in 1..LevelRules.maxLevel) {
            assertEquals(level, LevelRules.levelForXP(LevelRules.xpToReach(level)))
            if (level < LevelRules.maxLevel) {
                assertEquals(level, LevelRules.levelForXP(LevelRules.xpToReach(level + 1) - 1))
            }
        }
    }

    @Test
    fun `progress reports XP inside the current level`() {
        val (earned, needed) = LevelRules.progress(100)
        assertEquals(4, earned)
        assertEquals(168, needed)
    }

    @Test
    fun `the last level shows a full bar and earns nothing more`() {
        val (earned, needed) = LevelRules.progress(lateGameXP)
        assertEquals(needed, earned)
        val engine = GameEngine(stateWith(xp = lateGameXP))
        engine.tap(tile, now)
        engine.select(HarvestTool.scythe)
        val events = engine.tap(tile, now.advancedBy(30.0))
        assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, Crop.starter.id, 0)), events)
        assertEquals(lateGameXP, engine.state.xp)
    }

    @Test
    fun `only harvesting a fully grown crop awards XP`() {
        val engine = GameEngine()
        assertEquals(listOf<GameEvent>(GameEvent.Planted(tile, 0.0)), engine.tap(tile, now))
        assertEquals(0, engine.state.xp)
        engine.select(HarvestTool.scythe)
        engine.tap(tile, now.advancedBy(10.0))
        assertEquals(0, engine.state.xp)
        val events = engine.tap(tile, now.advancedBy(30.0))
        assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, Crop.starter.id, 2)), events)
        assertEquals(2, engine.state.xp)
    }

    @Test
    fun `wheat awards two XP`() {
        assertEquals(2, LevelRules.harvestXP(Crop.starter))
    }

    @Test
    fun `higher tier crops award more XP`() {
        assertEquals(3, LevelRules.harvestXP(Crop.named("carrot")!!))
        assertEquals(25, LevelRules.harvestXP(Crop.named("blackberry")!!))
        val plain = Crop.all.filter { !it.isFruitTree }
        assertEquals(plain.map { it.unlockLevel + 1 }, plain.map(LevelRules::harvestXP))
    }

    @Test
    fun `a fruit tree earns one and a half times the XP of a crop at its level`() {
        for (tree in Crop.all.filter { it.isFruitTree }) {
            assertEquals(Math.round((tree.unlockLevel + 1) * 1.5).toInt(), LevelRules.harvestXP(tree))
        }
        assertEquals(8, LevelRules.harvestXP(Crop.named("apple")!!))
    }

    @Test
    fun `crossing a level reports it and the crops it unlocks`() {
        val engine = GameEngine(stateWith(xp = LevelRules.xpToReach(2) - 1))
        engine.tap(tile, now)
        engine.select(HarvestTool.scythe)
        val events = engine.tap(tile, now.advancedBy(30.0))
        assertEquals(
            listOf(GameEvent.Stored(tile, Crop.starter.id, 2), GameEvent.LeveledUp(2, listOf("Carrot"), 4, 1)),
            events,
        )
        assertTrue(engine.isUnlocked(Crop.named("carrot")!!))
    }
}
