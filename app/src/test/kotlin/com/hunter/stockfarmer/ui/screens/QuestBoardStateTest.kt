package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.QuestRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class QuestBoardStateTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val origin = GridPoint(10, 10)

    @Test
    fun `quest cards preserve board order and show stock and missing building hints`() {
        val engine = GameEngine(GameState())
        engine.state.putBuilding(origin, "marketStand", null)
        val objectives =
            listOf(
                QuestObjective(QuestGoal.StockProfit, 100, 25),
                QuestObjective(QuestGoal.Workshop("grainMachine"), 2, 1),
            )
        val quest = MarketQuest(UUID(0, 1), objectives, 1234)
        engine.state.marketQuests["marketStand"] = listOf(quest)
        val card =
            QuestBoardState
                .all(engine, now)
                .single()
                .quests
                .single()
        assertEquals(quest.id, card.id)
        assertEquals("1,234 XP", card.reward)
        assertEquals(listOf(0.25f, 0.5f), card.objectives.map { it.fraction })
        assertEquals("Build a House to open the Stock Market", card.objectives[0].hint)
        assertEquals("Available in the Build tool", card.objectives[1].hint)
        assertFalse(card.complete)
        engine.state.putBuilding(GridPoint(20, 20), "house2", null)
        assertNull(QuestCardState.from(quest, engine).objectives[0].hint)
    }

    @Test
    fun `max level hides reward XP and upgrade locks come from the engine`() {
        val engine = GameEngine(GameState().apply { xp = LevelRules.xpToReach(LevelRules.maxLevel) })
        engine.state.putBuilding(origin, "marketStand", null)
        val quest = MarketQuest(UUID(0, 1), listOf(QuestObjective(QuestGoal.PlantTrees, 1, 1)), 50)
        assertEquals("Maximum level", QuestCardState.from(quest, engine).reward)
        assertTrue(QuestCardState.from(quest, engine).complete)
        assertEquals(
            "Complete ${QuestRules.QUESTS_TO_UPGRADE} quests to upgrade the Market Stand",
            QuestBoardState
                .all(engine, now)
                .single()
                .upgrade
                ?.lockReason,
        )
        engine.state.putBuilding(origin, "marketStandLarge", null)
        assertNull(QuestBoardState.all(engine, now).single().upgrade)
    }

    @Test
    fun `reward banner includes only actual completed quest events in event order`() {
        val rewards =
            QuestReward.from(
                listOf(
                    GameEvent.QuestCompleted(100, "Harvest wheat"),
                    GameEvent.BoughtShares("AAPL", 1, 10.0),
                    GameEvent.QuestCompleted(0, "Plant trees"),
                ),
            )
        assertEquals(listOf(QuestReward("Harvest wheat", 100), QuestReward("Plant trees", 0)), rewards)
    }
}
