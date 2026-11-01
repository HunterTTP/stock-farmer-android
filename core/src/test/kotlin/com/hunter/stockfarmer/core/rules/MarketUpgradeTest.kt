package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MarketUpgradeTest {
    private val origin = GridPoint(10, 10)
    private val now = Instant.ofEpochSecond(30_000_000)

    private fun engine(
        completed: Int = 10,
        money: Double = 10_000.0,
    ): GameEngine {
        val state = stateWith(money = money, xp = LevelRules.xpToReach(16), logs = 0)
        state.putBuilding(origin, "marketStand")
        state.completedMarketQuests = completed
        return GameEngine(state)
    }

    @Test
    fun `only the base stand is sold and saved large selection cannot bypass upgrade`() {
        assertEquals(listOf("Market Stand"), Building.all.filter { it.isMarketStand }.map { it.name })
        assertTrue(Building.all.none { it.id == "marketStandLarge" })
        val engine = engine()
        engine.state.selectedBuildingID = "marketStandLarge"
        assertEquals("marketStand", engine.selectedBuilding.id)
        engine.select(Building.named("marketStandLarge")!!)
        assertEquals("marketStand", engine.selectedBuilding.id)
    }

    @Test
    fun `only successful claims count toward the ten quests`() {
        val engine = engine(completed = 8)
        val quest =
            MarketQuest(objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 1)), rewardXP = 100, rewardLevel = 16)
        engine.state.marketQuests["marketStand"] = listOf(quest)
        val random = SeededRandom(1)
        engine.claimQuest(quest.id, origin, random)
        assertEquals(8, engine.state.completedMarketQuests)
        engine.recordQuestProgress(QuestGoal.PlantTrees)
        engine.claimQuest(quest.id, origin, random)
        engine.claimQuest(quest.id, origin, random)
        assertEquals(9, engine.state.completedMarketQuests)
        assertEquals(Rejection.NeedQuestCompletions(10), engine.marketUpgradeRejection(origin, now))
        val next = engine.quests(origin)[0]
        for (objective in next.objectives) engine.recordQuestProgress(objective.goal, objective.target)
        engine.claimQuest(next.id, origin, random)
        assertEquals(10, engine.state.completedMarketQuests)
        assertNull(engine.marketUpgradeRejection(origin, now))
    }

    @Test
    fun `upgrade costs ten thousand once and keeps current quests`() {
        val engine = engine()
        val random = SeededRandom(2)
        engine.prepareQuests(origin, random)
        val old = engine.quests(origin)
        val storage = engine.state.storage.toMap()
        assertEquals(
            listOf<GameEvent>(GameEvent.Built(origin, 10_000.0)),
            engine.upgradeMarketStand(origin, now, random),
        )
        assertEquals(0.0, engine.state.money)
        assertEquals(storage, engine.state.storage)
        val building = engine.building(origin)!!.building
        assertEquals("Market Stand", building.name)
        assertTrue(building.width == 3 && building.height == 2)
        assertEquals("market_stand_big", building.assetName)
        assertEquals(6, engine.quests(origin).size)
        assertEquals(old, engine.quests(origin).take(old.size))
        assertTrue(engine.quests(origin).takeLast(2).all { it.objectives.size == 2 })
        assertNull(engine.state.marketQuests["marketStand"])
        val before = engine.state.copy()
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.AlreadyUpgraded)),
            engine.upgradeMarketStand(origin, now, random),
        )
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `refused upgrades do not spend or change progress`() {
        val cases =
            listOf(
                Triple(9, 10_000.0, Rejection.NeedQuestCompletions(10)),
                Triple(10, 9_999.0, Rejection.NeedMoney(10_000.0)),
            )
        for ((completed, money, expected) in cases) {
            val engine = engine(completed, money)
            val before = engine.state.copy()
            assertEquals(
                listOf<GameEvent>(GameEvent.Rejected(expected)),
                engine.upgradeMarketStand(origin, now, SeededRandom(3)),
            )
            assertTrue(sameState(before, engine.state))
        }
    }

    @Test
    fun `expanded footprint must be free and inside the farm`() {
        val engine = engine()
        val extra = GridPoint(11, 12)
        engine.state.water[extra] = WaterStyle.deep
        assertEquals(Rejection.NotEnoughSpace, engine.marketUpgradeRejection(origin, now))
        engine.state.water.remove(extra)
        engine.state.plots[extra] = Plot("wheat", now)
        assertNotNull(engine.marketUpgradeRejection(origin, now))
        engine.state.plots = mutableMapOf()
        engine.state.removeBuilding(origin)
        engine.place(GridPoint(10, 48), "marketStand")
        assertEquals(Rejection.NotEnoughSpace, engine.marketUpgradeRejection(GridPoint(10, 48), now))
    }

    @Test
    fun `completed count and upgraded stand round trip and old saves default to zero`() {
        val engine = engine()
        engine.upgradeMarketStand(origin, now, SeededRandom(4))
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(10, loaded.completedMarketQuests)
        assertEquals(engine.state.buildings, loaded.buildings)
        assertEquals(engine.state.marketQuests, loaded.marketQuests)
        assertEquals(0, GameStateJson.decode("{}").completedMarketQuests)
    }

    @Test
    fun `existing large stands remain upgraded and count against the placement limit`() {
        val engine = engine(completed = 0, money = 100_000.0)
        engine.place(origin, "marketStandLarge")
        engine.reopen(now, SeededRandom(5))
        assertTrue(engine.building(origin)!!.building.isUpgradedMarketStand)
        assertEquals(
            1,
            engine.placedOnMap(
                com.hunter.stockfarmer.core.model.ShopItem
                    .BuildingItem("marketStand"),
            ),
        )
        engine.state.storage = mutableMapOf("log" to 100, "stone" to 100)
        engine.select(Building.named("marketStand")!!)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Market Stand", 1))),
            engine.build(GridPoint(20, 20), now),
        )
    }

    @Test
    fun `an old farm with both boards keeps every quest until claimed`() {
        val engine = engine()
        val large = GridPoint(20, 20)
        engine.place(large, "marketStandLarge")
        val random = SeededRandom(6)
        engine.prepareQuests(origin, random)
        engine.prepareQuests(large, random)
        val quests = engine.quests(origin) + engine.quests(large)
        engine.upgradeMarketStand(origin, now, random)
        assertEquals(quests, engine.quests(origin))
        for (quest in quests.take(3)) {
            for (objective in quest.objectives) engine.recordQuestProgress(objective.goal, objective.target)
            engine.claimQuest(quest.id, origin, random)
        }
        assertEquals(7, engine.quests(origin).size)
        val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.marketQuests, restored.marketQuests)
    }

    @Test
    fun `moving keeps the upgrade and the refund includes only its cash cost`() {
        val engine = engine()
        engine.upgradeMarketStand(origin, now, SeededRandom(7))
        val quests = engine.quests(origin)
        engine.beginMoving(engine.building(origin)!!)
        val moved = GridPoint(30, 30)
        engine.movePlacement(moved)
        engine.confirmPlacement(now)
        val building = engine.building(moved)!!
        assertTrue(building.building.isUpgradedMarketStand)
        assertEquals(quests, engine.quests(moved))
        assertEquals(8_800.0, engine.refund(building.building))
        assertEquals(engine.materialRefund(Building.marketStand).first, engine.materialRefund(building.building).first)
        assertEquals(
            engine.materialRefund(Building.marketStand).second,
            engine.materialRefund(building.building).second,
        )
        engine.demolish(building)
        assertEquals(10, engine.state.completedMarketQuests)
    }

    @Test
    fun `invalid completed quest counts are rejected`() {
        assertThrows(Exception::class.java) { GameStateJson.decode("""{"completedMarketQuests":-1}""") }
    }

    @Test
    fun `upgrading at level six fills six quests without locked tasks`() {
        val engine = engine()
        engine.state.xp = LevelRules.xpToReach(6)
        val random = SeededRandom(8)
        engine.prepareQuests(origin, random)
        assertEquals(4, engine.quests(origin).size)
        engine.upgradeMarketStand(origin, now, random)
        assertEquals(6, engine.quests(origin).size)
        engine.state.marketQuests = mutableMapOf()
        engine.prepareQuests(origin, random)
        val quests = engine.quests(origin)
        assertEquals(6, quests.size)
        assertTrue(quests.all { it.objectives.size == 2 })
        assertEquals(6, quests.map { it.rewardXP }.toSet().size)
        for (objective in quests.flatMap { it.objectives }) {
            when (val goal = objective.goal) {
                is QuestGoal.Harvest -> assertTrue((Crop.named(goal.id)?.unlockLevel ?: 99) <= 3)
                is QuestGoal.Feed -> assertTrue((Building.named(goal.id)?.unlockLevel ?: 99) <= 6)
                is QuestGoal.Collect -> assertEquals("egg", goal.id)
                QuestGoal.PlantTrees -> Unit
                is QuestGoal.Workshop -> throw AssertionError("Workshops are still locked at level six")
                QuestGoal.StockProfit, QuestGoal.StockValue -> throw AssertionError("Stock quests wait for the House")
            }
        }
    }
}
