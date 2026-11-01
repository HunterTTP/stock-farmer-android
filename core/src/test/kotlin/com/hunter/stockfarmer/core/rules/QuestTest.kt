package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class QuestTest {
    private val stand = GridPoint(10, 10)
    private val now = Instant.ofEpochSecond(30_000_000)

    private fun engine(
        large: Boolean = false,
        level: Int = 19,
    ): GameEngine {
        val state = stateWith(money = 1_000_000.0, xp = LevelRules.xpToReach(level), logs = 0)
        state.putBuilding(stand, if (large) "marketStandLarge" else "marketStand")
        return GameEngine(state)
    }

    private fun quest(
        goal: QuestGoal,
        target: Int,
    ) = MarketQuest(objectives = listOf(QuestObjective(goal, target)), rewardXP = 100, rewardLevel = 19)

    private fun GameEngine.progress(
        questIndex: Int = 0,
        board: GridPoint = stand,
    ): Int = quests(board)[questIndex].objectives[0].progress

    @Test
    fun `boards have four or six persistent random quests with one about the stock market`() {
        for (large in listOf(false, true)) {
            val engine = engine(large)
            val random = SeededRandom(12)
            engine.prepareQuests(stand, random)
            val quests = engine.quests(stand)
            assertEquals(if (large) 6 else 4, quests.size)
            assertEquals(quests.size, quests.map { it.id }.toSet().size)
            val stock = quests.filter { q -> q.objectives.any { it.goal.isStock } }
            assertEquals(1, stock.size)
            assertEquals(1, stock[0].objectives.size)
            assertTrue(quests.filter { it !in stock }.all { it.objectives.size == if (large) 2 else 1 })
            assertEquals(quests.size, quests.map { it.rewardXP }.toSet().size)
            engine.prepareQuests(stand, random)
            assertEquals(quests, engine.quests(stand))
        }
    }

    @Test
    fun `tasks use older crops and unlocked buildings even when not owned`() {
        val engine = engine(level = 16)
        engine.prepareQuests(stand, SeededRandom(31))
        val goals = engine.quests(stand).flatMap { it.objectives }.map { it.goal }
        assertTrue(goals.any { it is QuestGoal.Harvest })
        assertTrue(goals.any { it is QuestGoal.Workshop })
        for (goal in goals) {
            when (goal) {
                is QuestGoal.Harvest -> {
                    assertTrue((Crop.named(goal.id)?.unlockLevel ?: 99) <= 13)
                }

                is QuestGoal.Workshop, is QuestGoal.Feed -> {
                    val id = goal.buildingID!!
                    assertTrue((Building.named(id)?.unlockLevel ?: 99) <= 16)
                    assertTrue(id !in engine.state.buildings.values)
                }

                else -> {
                    Unit
                }
            }
        }
        val objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 3))
        assertTrue(QuestRules.rewardXP(19, false, objectives) > QuestRules.rewardXP(16, false, objectives))
        assertTrue(QuestRules.rewardXP(19, true, objectives) > QuestRules.rewardXP(19, false, objectives))
    }

    @Test
    fun `successful harvests count once and rejected actions do not`() {
        val engine = engine()
        engine.state.marketQuests["marketStand"] = listOf(quest(QuestGoal.Harvest("wheat"), 2))
        val tile = GridPoint(25, 25)
        engine.state.plots[tile] = Plot("wheat", now)
        engine.select(HarvestTool.scythe)
        engine.useTool(tile, now)
        assertEquals(0, engine.progress())
        engine.useTool(tile, now.advancedBy(Crop.starter.growDuration))
        engine.useTool(tile, now.advancedBy(Crop.starter.growDuration))
        assertEquals(1, engine.progress())
        assertEquals(1, engine.state.storage["wheat"])
    }

    @Test
    fun `harvest quests never ask for more than one hundred of a crop`() {
        for (level in listOf(10, 19, 24, 30, LevelRules.maxLevel)) {
            for (large in listOf(false, true)) {
                val random = SeededRandom(level.toLong())
                repeat(40) {
                    val quest = QuestRules.makeQuest(level, large, QuestGoal.Harvest("wheat"), emptySet(), random)
                    for (objective in quest.objectives) {
                        if (objective.goal is QuestGoal.Harvest) {
                            assertTrue(objective.target <= QuestRules.MOST_CROPS_ASKED)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `harvesting counts for a quest whether the crop is sold or stored`() {
        for (selling in listOf(false, true)) {
            val engine = engine()
            engine.state.marketQuests["marketStand"] = listOf(quest(QuestGoal.Harvest("wheat"), 5))
            engine.setSelling(selling, HarvestTool.scythe)
            val tile = GridPoint(25, 25)
            engine.state.plots[tile] = Plot("wheat", now)
            engine.select(HarvestTool.scythe)
            engine.useTool(tile, now.advancedBy(Crop.starter.growDuration))
            assertEquals(1, engine.progress())
        }
    }

    @Test
    fun `a bonus second crop counts for the quest too`() {
        val engine = engine()
        engine.state.marketQuests["marketStand"] = listOf(quest(QuestGoal.Harvest("wheat"), 5))
        engine.state.toolLevels[HarvestTool.scythe.rawValue] = 3
        engine.bonusRoll = { 0.0 }
        val tile = GridPoint(25, 25)
        engine.state.plots[tile] = Plot("wheat", now)
        engine.select(HarvestTool.scythe)
        engine.useTool(tile, now.advancedBy(Crop.starter.growDuration))
        assertEquals(2, engine.state.storage["wheat"])
        assertEquals(2, engine.progress())
    }

    @Test
    fun `claiming requires every objective pays once and replaces the quest`() {
        val engine = engine()
        val quest =
            MarketQuest(
                objectives =
                    listOf(
                        QuestObjective(QuestGoal.PlantTrees, 2, 2),
                        QuestObjective(QuestGoal.Harvest("wheat"), 1),
                    ),
                rewardXP = 100,
            )
        engine.state.marketQuests["marketStand"] = listOf(quest)
        val random = SeededRandom(7)
        val before = engine.state.xp
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.QuestIncomplete)),
            engine.claimQuest(quest.id, stand, random),
        )
        engine.recordQuestProgress(QuestGoal.Harvest("wheat"))
        assertTrue(engine.claimQuest(quest.id, stand, random).any { it is GameEvent.QuestCompleted && it.xp == 100 })
        assertEquals(before + 100, engine.state.xp)
        assertEquals(2, engine.quests(stand).size)
        assertTrue(engine.quests(stand).all { it.id != quest.id })
        engine.claimQuest(quest.id, stand, random)
        assertEquals(before + 100, engine.state.xp)
    }

    @Test
    fun `completed quests are turned in on their own and named in the event`() {
        val engine = engine()
        val done = MarketQuest(objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 2, 2)), rewardXP = 100)
        val unfinished = quest(QuestGoal.Collect("egg"), 3)
        engine.state.marketQuests["marketStand"] = listOf(done, unfinished)
        val random = SeededRandom(3)
        val before = engine.state.xp
        val events = engine.claimCompletedQuests(random)
        assertTrue(GameEvent.QuestCompleted(100, "Plant 2 trees or fruit trees") in events)
        assertEquals(before + 100, engine.state.xp)
        assertEquals(1, engine.state.completedMarketQuests)
        assertTrue(engine.quests(stand).any { it.id == unfinished.id })
        assertTrue(engine.quests(stand).all { it.id != done.id })
        assertTrue(engine.claimCompletedQuests(random).isEmpty())
    }

    @Test
    fun `quest progress survives saves and missing field loads`() {
        val engine = engine()
        engine.state.marketQuests["marketStand"] = listOf(quest(QuestGoal.PlantTrees, 3))
        engine.recordQuestProgress(QuestGoal.PlantTrees)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.marketQuests, loaded.marketQuests)
        assertTrue(GameStateJson.decode("{}").marketQuests.isEmpty())
    }

    @Test
    fun `workshop batches count when collected even when sold immediately`() {
        val engine = engine()
        val workshop = GridPoint(15, 15)
        engine.place(workshop, "stoneOven")
        engine.state.marketQuests["marketStand"] = listOf(quest(QuestGoal.Workshop("stoneOven"), 2))
        engine.state.storage = mutableMapOf("flour" to 3)
        engine.startWork(workshop, now)
        engine.select(HarvestTool.bucket)
        engine.useTool(workshop, now)
        assertEquals(0, engine.progress())
        engine.state.storage = mutableMapOf("wheat" to engine.storageCapacity)
        engine.useTool(workshop, now.advancedBy(300.0))
        assertEquals(0, engine.progress())
        engine.setSelling(true, HarvestTool.bucket)
        engine.useTool(workshop, now.advancedBy(300.0))
        assertEquals(1, engine.progress())
        assertEquals(null, engine.state.storage["bread"])
    }

    @Test
    fun `feeding counts actual meals and eggs count once`() {
        val engine = engine()
        val home = GridPoint(15, 15)
        engine.place(home, "chickenCoop")
        val henSpot = GridPoint(18, 15)
        val hen = Chicken.hatched(0, ChickenVariant.all[0], "Hen", henSpot, now.advancedBy(-1_000.0))
        engine.state.coops[home] = Coop(chickens = listOf(hen), nextChickAt = now)
        engine.state.storage["beetroot"] = 5
        engine.state.marketQuests["marketStand"] =
            listOf(quest(QuestGoal.Feed("chickenCoop"), 5), quest(QuestGoal.Collect("egg"), 3))
        engine.equip(StorageItem.CropItem(Chicken.favoriteCrop))
        engine.useTool(henSpot, now)
        engine.useTool(henSpot, now)
        assertEquals(1, engine.progress(0))
        assertEquals(4, engine.state.storage["beetroot"])
        val tile = GridPoint(25, 25)
        engine.state.eggs = listOf(lyingEgg(tile, 0.0, 0.0))
        engine.select(HarvestTool.bucket)
        engine.useTool(tile, now)
        engine.useTool(tile, now)
        assertEquals(1, engine.progress(1))
    }

    @Test
    fun `tree planting counts but moving and previewing do not`() {
        val engine = engine()
        engine.state.marketQuests["marketStand"] = listOf(quest(QuestGoal.PlantTrees, 3))
        engine.select(Tree.starter.species)
        val tile = GridPoint(35, 35)
        val preview = engine.copy()
        preview.useTool(tile, now)
        assertEquals(0, engine.progress())
        assertEquals(1, preview.progress())
        engine.useTool(tile, now)
        engine.useTool(tile, now)
        assertEquals(1, engine.progress())
        val tree =
            engine.state.trees.entries
                .first()
        engine.beginMovingTree(tree.key)
        engine.movePlacement(GridPoint(40, 40))
        engine.confirmPlacement(now)
        assertEquals(1, engine.progress())
    }

    @Test
    fun `both boards track progress and moving or rebuilding keeps it`() {
        val engine = engine()
        val large = GridPoint(20, 20)
        engine.place(large, "marketStandLarge")
        engine.state.marketQuests =
            mutableMapOf(
                "marketStand" to listOf(quest(QuestGoal.PlantTrees, 3)),
                "marketStandLarge" to listOf(quest(QuestGoal.PlantTrees, 6)),
            )
        engine.recordQuestProgress(QuestGoal.PlantTrees)
        assertEquals(1, engine.progress(0, large))
        val saved = engine.quests(stand)
        engine.beginMoving(engine.building(stand)!!)
        val moved = GridPoint(30, 30)
        engine.movePlacement(moved)
        engine.confirmPlacement(now)
        assertEquals(saved, engine.quests(moved))
        engine.demolish(engine.building(moved)!!)
        engine.recordQuestProgress(QuestGoal.PlantTrees)
        assertEquals(saved, engine.state.marketQuests["marketStand"])
        assertEquals(2, engine.progress(0, large))
        engine.place(stand, "marketStand")
        engine.prepareQuests(stand, SeededRandom(4))
        assertEquals(saved, engine.quests(stand).take(saved.size))
    }

    @Test
    fun `claims level up and respect the existing maximum level`() {
        val engine = engine()
        engine.state.xp = LevelRules.xpToReach(20) - 10
        val complete = MarketQuest(objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 1, 1)), rewardXP = 100)
        engine.state.marketQuests["marketStand"] = listOf(complete)
        val random = SeededRandom(5)
        val events = engine.claimQuest(complete.id, stand, random)
        assertEquals(20, engine.state.level)
        assertTrue(events.any { it is GameEvent.LeveledUp && it.level == 20 })
        engine.state.xp = lateGameXP
        engine.state.marketQuests["marketStand"] = listOf(complete)
        val capped = engine.claimQuest(complete.id, stand, random)
        assertEquals(1, capped.size)
        assertTrue(capped.any { it is GameEvent.QuestCompleted && it.xp == 0 })
        assertEquals(lateGameXP, engine.state.xp)
    }

    @Test
    fun `replacing an unfinished quest resets only that quest without a reward`() {
        val engine = engine()
        val old = quest(QuestGoal.PlantTrees, 3)
        val other = quest(QuestGoal.Collect("egg"), 3)
        engine.state.marketQuests["marketStand"] = listOf(old, other)
        engine.recordQuestProgress(QuestGoal.PlantTrees)
        val before = engine.state.xp
        engine.replaceQuest(old.id, stand, SeededRandom(8))
        assertNotEquals(old.id, engine.quests(stand)[0].id)
        assertTrue(engine.quests(stand)[0].objectives.all { it.progress == 0 })
        assertEquals(other, engine.quests(stand)[1])
        assertEquals(before, engine.state.xp)
    }

    @Test
    fun `generated quests are valid across levels and large tasks take more work`() {
        val random = SeededRandom(10)
        val cropIDs = mutableSetOf<String>()
        for (level in Building.marketStand.unlockLevel..LevelRules.maxLevel) {
            repeat(20) {
                val small = engine(level = level)
                val large = engine(large = true, level = level)
                small.prepareQuests(stand, random)
                large.prepareQuests(stand, random)
                for (board in listOf(small, large)) {
                    val rewards = board.quests(stand).map { it.rewardXP }
                    assertEquals(rewards.size, rewards.toSet().size)
                    assertTrue(rewards.all { it > 0 && it <= QuestRules.maximumRewardXP })
                    val loaded = GameStateJson.decode(GameStateJson.encode(board.state))
                    assertEquals(board.state.marketQuests, loaded.marketQuests)
                    for (objective in board.quests(stand).flatMap { it.objectives }) {
                        assertTrue(objective.goal.isValid)
                        objective.goal.buildingID?.let { assertTrue((Building.named(it)?.unlockLevel ?: 99) <= level) }
                    }
                }
                (small.quests(stand)[0].objectives[0].goal as? QuestGoal.Harvest)?.let { cropIDs += it.id }
                val smallTarget = small.quests(stand)[0].objectives[0].target
                assertTrue(
                    large.quests(stand)[0].objectives[0].target > smallTarget ||
                        smallTarget == QuestRules.MOST_CROPS_ASKED,
                )
                assertTrue(
                    large
                        .quests(stand)
                        .filter { !it.objectives[0].goal.isStock }
                        .all { q ->
                            q.objectives
                                .map { it.goal }
                                .toSet()
                                .size == 2
                        },
                )
            }
        }
        assertTrue(cropIDs.size > 5)
    }

    @Test
    fun `corrupt quest progress is rejected`() {
        val engine = engine()
        engine.state.marketQuests["marketStand"] =
            listOf(MarketQuest(objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 1, 2)), rewardXP = 100))
        val text = GameStateJson.encode(engine.state)
        assertThrows(Exception::class.java) { GameStateJson.decode(text) }
    }

    @Test
    fun `reward reflects quantity task and time required`() {
        fun reward(
            goal: QuestGoal,
            count: Int,
        ) = QuestRules.rewardXP(19, false, listOf(QuestObjective(goal, count)))
        assertTrue(reward(QuestGoal.PlantTrees, 4) > reward(QuestGoal.PlantTrees, 2))
        assertTrue(reward(QuestGoal.Workshop("tailorTable"), 1) > reward(QuestGoal.Workshop("grainMachine"), 1))
        assertTrue(reward(QuestGoal.Workshop("grainMachine"), 2) > reward(QuestGoal.PlantTrees, 2))
    }

    @Test
    fun `every offer has a different reward including replacements across both stands`() {
        val engine = engine()
        val large = GridPoint(20, 20)
        engine.place(large, "marketStandLarge")
        val random = SeededRandom(42)
        engine.prepareQuests(stand, random)
        engine.prepareQuests(large, random)
        repeat(100) {
            val quests = engine.quests(stand) + engine.quests(large)
            assertEquals(10, quests.map { it.rewardXP }.toSet().size)
            engine.replaceQuest(engine.quests(large)[0].id, large, random)
        }
    }

    @Test
    fun `objective titles include the required quantity`() {
        assertEquals("Harvest 40 Wheat", QuestObjective(QuestGoal.Harvest("wheat"), 40).title)
        assertEquals(
            "Collect 1 batch from the Grain Machine",
            QuestObjective(QuestGoal.Workshop("grainMachine"), 1).title,
        )
        assertEquals(
            "Collect 3 batches from the Grain Machine",
            QuestObjective(QuestGoal.Workshop("grainMachine"), 3).title,
        )
        assertEquals(
            "Have animals from the Chicken Coop eat 10 favorite crops",
            QuestObjective(QuestGoal.Feed("chickenCoop"), 10).title,
        )
        assertEquals("Collect 4 × Egg", QuestObjective(QuestGoal.Collect("egg"), 4).title)
        assertEquals("Plant 3 trees or fruit trees", QuestObjective(QuestGoal.PlantTrees, 3).title)
    }
}
