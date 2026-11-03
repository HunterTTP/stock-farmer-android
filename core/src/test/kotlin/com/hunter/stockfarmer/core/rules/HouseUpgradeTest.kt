package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class HouseUpgradeTest {
    private val origin = GridPoint(10, 10)
    private val now = Instant.ofEpochSecond(1_700_000_000)

    private fun engine(
        level: Int = LevelRules.maxLevel,
        money: Double = 100_000.0,
        house: String = "house1",
    ): GameEngine {
        val state = stateWith(money = money, xp = LevelRules.xpToReach(level), logs = 0)
        state.putBuilding(origin, house)
        return GameEngine(state)
    }

    private fun quote(ticker: String) = StockQuote(ticker, 100, now)

    private fun GameEngine.buyOne(ticker: String) = buyShares(quote(ticker), 1, now = now)

    @Test
    fun `the luxury house is no longer sold but still resolves`() {
        assertTrue(Building.all.none { it.id == "house2" })
        val house = Building.named("house2")!!
        assertTrue(house.isUpgradedHouse && house.isHouse)
        assertTrue(house.width == 4 && house.height == 5)
    }

    @Test
    fun `only one house of either tier can stand`() {
        val engine = engine(level = 12, money = 1_000_000.0)
        engine.state.storage = mutableMapOf("log" to 1_000, "stone" to 1_000)
        engine.select(Building.named("house1")!!)
        val limit = listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("House", 1)))
        assertEquals(limit, engine.build(GridPoint(30, 30), now))
        val upgraded = engine(house = "house2")
        upgraded.state.storage = mutableMapOf("log" to 1_000, "stone" to 1_000)
        upgraded.select(Building.named("house1")!!)
        assertEquals(limit, upgraded.build(GridPoint(30, 30), now))
    }

    @Test
    fun `upgrading costs fifty thousand and swaps the house`() {
        val engine = engine()
        assertNull(engine.farmhouseUpgradeRejection(origin, now))
        assertEquals(listOf<GameEvent>(GameEvent.Built(origin, 50_000.0)), engine.upgradeFarmhouse(origin, now))
        assertEquals(50_000.0, engine.state.money)
        assertEquals("house2", engine.building(origin)?.building?.id)
        assertEquals(Rejection.AlreadyUpgraded, engine.farmhouseUpgradeRejection(origin, now))
    }

    @Test
    fun `upgrading still needs level twenty four and the money`() {
        assertEquals(Rejection.NeedLevel(24), engine(level = 23).farmhouseUpgradeRejection(origin, now))
        assertEquals(Rejection.NeedMoney(50_000.0), engine(money = 49_999.0).farmhouseUpgradeRejection(origin, now))
        assertEquals(Rejection.NothingHere, engine(house = "marketStand").farmhouseUpgradeRejection(origin, now))
    }

    @Test
    fun `upgrading needs room for the taller house`() {
        val engine = engine()
        engine.state.putBuilding(GridPoint(14, 10), "petHouse")
        val before = engine.state.copy()
        val events = engine.upgradeFarmhouse(origin, now)
        assertTrue(events.any { it is GameEvent.Rejected })
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `the luxury house doubles the stock slots`() {
        val base = engine(money = 1_000_000.0)
        assertEquals(5, base.stockSlotLimit)
        for (ticker in listOf("A", "B", "C", "D", "E")) base.buyOne(ticker)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StockSlotsFull(5))), base.buyOne("F"))
        base.upgradeFarmhouse(origin, now)
        assertEquals(10, base.stockSlotLimit)
        for (ticker in listOf("F", "G", "H", "I", "J")) base.buyOne(ticker)
        assertEquals(10, base.stockSlotsUsed)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StockSlotsFull(10))), base.buyOne("K"))
    }

    @Test
    fun `stocks outlive their house and are found again in a new one`() {
        val engine = engine(money = 1_000_000.0, house = "house2")
        for (ticker in listOf("A", "B", "C", "D", "E", "F", "G")) engine.buyShares(quote(ticker), 2, now = now)
        val placed = engine.building(origin)!!
        assertTrue(engine.demolish(placed).any { it is GameEvent.Demolished })
        assertNull(engine.building(origin))
        assertEquals(7, engine.state.holdings.size)
        engine.state.storage = mutableMapOf("log" to 1_000, "stone" to 1_000)
        engine.select(Building.named("house1")!!)
        engine.build(origin, now)
        assertEquals("house1", engine.building(origin)?.building?.id)
        assertEquals(List(7) { 2L }, engine.holdings.map { it.shares })
        // Over the plain house's limit, so nothing new can be bought, but everything can still be sold.
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StockSlotsFull(5))), engine.buyOne("H"))
        assertTrue(engine.sellShares(quote("G"), 2, now = now).any { it is GameEvent.SoldShares })
    }

    @Test
    fun `ten stocks save and load`() {
        val engine = engine(money = 1_000_000.0, house = "house2")
        for (ticker in listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")) engine.buyOne(ticker)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(10, loaded.holdings.size)
    }
}
