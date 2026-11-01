package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ToolUpgradeTest {
    private val now = Instant.ofEpochSecond(1_700_000_000)
    private val planted = Instant.ofEpochSecond(7_000_000)
    private val spot = GridPoint(20, 20)

    private fun engine(
        level: Int = LevelRules.maxLevel,
        money: Double = 10_000.0,
        tool: HarvestTool = HarvestTool.axe,
        toolLevel: Int = 1,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = money, xp = LevelRules.xpToReach(level), logs = 0))
        engine.state.toolLevels[tool.rawValue] = toolLevel
        engine.select(tool)
        return engine
    }

    private fun passesToFell(
        tree: Tree,
        toolLevel: Int,
    ): Int {
        val engine = engine(toolLevel = toolLevel)
        val origin = tree.origin(spot)
        engine.state.putTree(origin, PlacedTree(tree, tree.species.colors[0], now))
        var passes = 0
        while (origin in engine.state.trees && passes < 20) {
            engine.useTool(spot, now)
            engine.liftTool()
            passes += 1
        }
        return passes
    }

    private fun passesToBreak(
        size: RockSize,
        toolLevel: Int,
    ): Int {
        val engine = engine(tool = HarvestTool.pickaxe, toolLevel = toolLevel)
        engine.state.rocks[spot] = size
        var passes = 0
        while (spot in engine.state.rocks && passes < 40) {
            engine.useTool(spot, now)
            engine.liftTool()
            passes += 1
        }
        return passes
    }

    /** An engine holding a tool at a level, with a ripe field of the starter crop and a roll that always comes out as given. */
    private fun ripeField(
        tool: HarvestTool,
        toolLevel: Int,
        roll: Double,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = 0.0))
        for (point in GameState.startingFarmland) engine.tap(point, planted)
        engine.state.toolLevels[tool.rawValue] = toolLevel
        engine.select(tool)
        engine.bonusRoll = { roll }
        return engine
    }

    @Test
    fun `every upgradable tool has levels two and three unlocked in order with rising costs`() {
        for (tool in HarvestTool.entries) {
            val upgrades = ToolUpgrade.all.filter { it.tool == tool }
            assertEquals(listOf(2, 3), upgrades.map { it.level })
            assertTrue(upgrades[0].unlockLevel < upgrades[1].unlockLevel)
            assertTrue(upgrades[0].cost < upgrades[1].cost)
        }
    }

    @Test
    fun `unlock levels spread from five to thirty`() {
        val byLevel = ToolUpgrade.all.sortedBy { it.unlockLevel }
        assertEquals(5, byLevel.first().unlockLevel)
        assertEquals(30, byLevel.last().unlockLevel)
        assertEquals(byLevel.size, byLevel.map { it.unlockLevel }.toSet().size)
        assertTrue(30 <= LevelRules.maxLevel)
        assertEquals(15, ToolUpgrade.all.first { it.tool == HarvestTool.axe && it.level == 3 }.unlockLevel)
    }

    @Test
    fun `each upgrade needs its level and money`() {
        for (upgrade in ToolUpgrade.all) {
            val tool = upgrade.tool
            val before = upgrade.level - 1
            assertEquals(
                Rejection.NeedLevel(upgrade.unlockLevel),
                engine(upgrade.unlockLevel - 1, tool = tool, toolLevel = before).upgradeRejection(tool),
            )
            assertEquals(
                Rejection.NeedMoney(upgrade.cost),
                engine(upgrade.unlockLevel, upgrade.cost - 1, tool, before).upgradeRejection(tool),
            )
            assertNull(engine(upgrade.unlockLevel, upgrade.cost, tool, before).upgradeRejection(tool))
        }
    }

    @Test
    fun `the rods upgrades widen the fishing circle`() {
        assertEquals(1.0, engine(tool = HarvestTool.rod).fishingReachFactor)
        val factors = listOf(2, 3).map { engine(tool = HarvestTool.rod, toolLevel = it).fishingReachFactor }
        assertTrue(factors[0] > 1 && factors[1] > factors[0])
        val anchor = GridPoint(10, 10)
        val bounds = FishingContest.Bounds(0.0, 300.0, 0.0, 500.0)
        val reaches = mutableListOf<Double>()
        for (level in 1..3) {
            val engine = engine(tool = HarvestTool.rod, toolLevel = level)
            engine.state.fishPonds[anchor] = FishPond(PondFish("carp", spot), now)
            reaches += engine.fishingContest(spot, bounds, SystemRandomSource)!!.reach
        }
        assertTrue(
            reaches[0] == FishingContest.REACH && reaches[1] == reaches[0] * 1.5 && reaches[2] == reaches[1] * 1.5,
        )
    }

    @Test
    fun `an upgrade is only offered once unlocked and until bought`() {
        assertFalse(engine(4).offersUpgrade(HarvestTool.axe))
        assertTrue(engine(5).offersUpgrade(HarvestTool.axe))
        assertFalse(engine(14, toolLevel = 2).offersUpgrade(HarvestTool.axe))
        assertTrue(engine(15, toolLevel = 2).offersUpgrade(HarvestTool.axe))
        assertFalse(engine(30, toolLevel = 3).offersUpgrade(HarvestTool.axe))
    }

    @Test
    fun `buying charges each level once and only for that tool`() {
        val engine = engine(money = 1_000_000.0)
        assertTrue(engine.upgrade(HarvestTool.scythe).isEmpty())
        assertTrue(engine.upgrade(HarvestTool.scythe).isEmpty())
        assertEquals(3, engine.level(HarvestTool.scythe))
        assertEquals(1, engine.level(HarvestTool.axe))
        assertEquals(
            1_000_000.0 -
                ToolUpgrade.all
                    .filter {
                        it.tool == HarvestTool.scythe
                    }.sumOf { it.cost },
            engine.state.money,
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.ToolFullyUpgraded)),
            engine.upgrade(HarvestTool.scythe),
        )
        assertEquals(3, engine.level(HarvestTool.scythe))
    }

    @Test
    fun `tool levels survive saving`() {
        val engine = engine()
        engine.upgrade(HarvestTool.axe)
        val decoded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(mapOf(HarvestTool.axe.rawValue to 2), decoded.toolLevels)
    }

    @Test
    fun `the axe takes half the hits rounded up at level two and one at level three`() {
        for (tree in TreeSpecies.pine.trees) {
            assertEquals(tree.hitsToFell, passesToFell(tree, 1))
            assertEquals((tree.hitsToFell + 1) / 2, passesToFell(tree, 2))
            assertEquals(1, passesToFell(tree, 3))
        }
    }

    @Test
    fun `the pickaxe takes half the hits rounded up at level two and one at level three`() {
        for (size in RockSize.stoneSizes) {
            assertEquals(size.hitsToBreak, passesToBreak(size, 1))
            assertEquals((size.hitsToBreak + 1) / 2, passesToBreak(size, 2))
            assertEquals(1, passesToBreak(size, 3))
        }
    }

    @Test
    fun `faster tools still give all their logs and stones`() {
        val engine = engine(toolLevel = 3)
        val tree = Tree.named("oak-huge") ?: Tree.starter
        engine.state.putTree(tree.origin(spot), PlacedTree(tree, tree.species.colors[0], now))
        engine.useTool(spot, now)
        assertEquals(tree.logs, engine.state.storage["log"])
        val pickaxe = engine(tool = HarvestTool.pickaxe, toolLevel = 3)
        pickaxe.state.rocks[spot] = RockSize.big
        pickaxe.useTool(spot, now)
        assertEquals(RockSize.big.stones, pickaxe.state.storage["stone"])
    }

    @Test
    fun `the scythe never doubles at level one whatever the roll`() {
        val engine = ripeField(HarvestTool.scythe, 1, 0.0)
        assertEquals(1, engine.useTool(GridPoint(24, 24), planted.advancedBy(30.0)).size)
    }

    @Test
    fun `a winning roll gathers a second crop and a losing one does not`() {
        for (toolLevel in listOf(2, 3)) {
            val chance = ripeField(HarvestTool.scythe, toolLevel, 0.0).doubleDropChance(HarvestTool.scythe)
            val lucky = ripeField(HarvestTool.scythe, toolLevel, chance - 0.001)
            assertEquals(2, lucky.useTool(GridPoint(24, 24), planted.advancedBy(30.0)).size)
            assertEquals(2, lucky.state.storage[Crop.starter.id])
            val unlucky = ripeField(HarvestTool.scythe, toolLevel, chance)
            assertEquals(1, unlucky.useTool(GridPoint(24, 24), planted.advancedBy(30.0)).size)
        }
    }

    @Test
    fun `every tool has its own effects and the harvesters gather more often at higher levels`() {
        for (tool in listOf(HarvestTool.axe, HarvestTool.pickaxe)) {
            assertEquals(
                listOf(ToolEffect.HalvedPasses, ToolEffect.SinglePass),
                ToolUpgrade.all.filter { it.tool == tool }.map { it.effect },
            )
        }
        val chances =
            listOf(HarvestTool.scythe, HarvestTool.bucket).map { tool ->
                ToolUpgrade.all.filter { it.tool == tool }.mapNotNull { (it.effect as? ToolEffect.DoubleDrop)?.chance }
            }
        assertTrue(chances.all { it.size == 2 && it[0] > 0 && it[1] > it[0] })
        assertNotEquals(chances[0], chances[1])
    }

    @Test
    fun `a double sold on the spot pays for both and only the first gives XP`() {
        val engine = ripeField(HarvestTool.scythe, 2, 0.0)
        engine.setSelling(true, HarvestTool.scythe)
        val events = engine.useTool(GridPoint(24, 24), planted.advancedBy(30.0))
        assertEquals(2, events.size)
        assertEquals(Crop.starter.sellValue * 2, engine.state.money)
        assertEquals(0, (events[1] as GameEvent.Sold).xp)
    }

    @Test
    fun `a double is skipped when the second item would not fit`() {
        val engine = ripeField(HarvestTool.scythe, 3, 0.0)
        engine.state.storage = mutableMapOf("filler" to engine.storageCapacity - 1)
        assertEquals(1, engine.useTool(GridPoint(24, 24), planted.advancedBy(30.0)).size)
        assertEquals(engine.storageCapacity, engine.storedCount)
    }

    @Test
    fun `the bucket doubles eggs`() {
        val engine = engine(tool = HarvestTool.bucket, toolLevel = 2)
        engine.bonusRoll = { 0.0 }
        engine.state.eggs = listOf(Egg(spot, 0.0, 0.0, GridPoint(1, 1), 1))
        assertEquals(2, engine.useTool(spot, now).size)
        assertEquals(2, engine.state.storage[StorageItem.Egg.id])
    }

    @Test
    fun `the axe and pickaxe never double`() {
        for (tool in listOf(HarvestTool.axe, HarvestTool.pickaxe)) {
            assertEquals(0.0, engine(tool = tool, toolLevel = 3).doubleDropChance(tool))
        }
        for (tool in listOf(HarvestTool.scythe, HarvestTool.bucket)) {
            assertEquals(10, engine(tool = tool, toolLevel = 3).passes(10, tool))
        }
    }
}
