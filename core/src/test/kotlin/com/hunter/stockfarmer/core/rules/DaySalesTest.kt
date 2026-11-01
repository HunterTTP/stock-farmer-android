package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ItemSale
import com.hunter.stockfarmer.core.persistence.GameStateJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DaySalesTest {
    private fun engineHolding(vararg items: Pair<StorageItem, Int>): GameEngine {
        val engine = GameEngine()
        for ((item, count) in items) engine.state.storage[item.id] = count
        return engine
    }

    private val wheat = StorageItem.CropItem(Crop.starter)
    private val egg = StorageItem.Egg

    @Test
    fun `a new game is on day one with nothing sold`() {
        val engine = GameEngine()
        assertEquals(1, engine.state.day)
        assertTrue(engine.beginNextDay().sales.isEmpty())
    }

    @Test
    fun `selling from storage adds up what each item sold and earned`() {
        val engine = engineHolding(wheat to 10, egg to 4)
        engine.sellStored(wheat, 3)
        engine.sellStored(wheat, 2)
        engine.sellStored(egg, 4)
        val sales = engine.beginNextDay().sales
        val sold = sales.first { it.itemID == wheat.id }
        assertEquals(5, sold.quantity)
        assertEquals(engine.saleValue(wheat, 5), sold.earnings)
        assertEquals(4, sales.first { it.itemID == egg.id }.quantity)
    }

    @Test
    fun `selling on the spot counts too`() {
        val engine = GameEngine()
        engine.select(HarvestTool.bucket)
        engine.setSelling(true, HarvestTool.bucket)
        val spot = GridPoint(24, 24)
        engine.state.eggs = mutableListOf(lyingEgg(spot, 0.0, 0.0))
        engine.useTool(spot, Instant.ofEpochSecond(12_000_000))
        val sales = engine.beginNextDay().sales
        assertEquals(listOf(ItemSale(egg.id, "Egg", 1, com.hunter.stockfarmer.core.model.Egg.SALE_PRICE)), sales)
    }

    @Test
    fun `each item gets its own row with its name quantity and earnings`() {
        val fish = StorageItem.Fish(FishKind.all[0])
        val chicken = StorageItem.ChickenItem(ChickenVariant.all[0])
        val engine = engineHolding(wheat to 3, fish to 1, chicken to 2)
        engine.sellStored(wheat, 3)
        engine.sellStored(fish, 1)
        engine.sellStored(chicken, 2)
        val sales = engine.beginNextDay().sales
        assertEquals(3, sales.size)
        assertEquals(
            setOf(Crop.starter.produceName, FishKind.all[0].name, ChickenVariant.all[0].name),
            sales.map { it.name }.toSet(),
        )
        assertEquals(listOf(1, 2, 3), sales.map { it.quantity }.sorted())
    }

    @Test
    fun `trashing and buying are not sales`() {
        val engine = engineHolding(StorageItem.Log to 5)
        engine.trashStored(StorageItem.Log, 2)
        assertTrue(engine.beginNextDay().sales.isEmpty())
    }

    @Test
    fun `a new day counts up clears the sales and lists the biggest earner first`() {
        val engine = engineHolding(wheat to 1, egg to 20)
        engine.sellStored(wheat, 1)
        engine.sellStored(egg, 20)
        val summary = engine.beginNextDay()
        assertTrue(summary.day == 2 && engine.state.day == 2)
        assertEquals(listOf(egg.id, wheat.id), summary.sales.map { it.itemID })
        assertTrue(engine.beginNextDay().sales.isEmpty())
        assertEquals(3, engine.state.day)
    }

    @Test
    fun `days and sales survive saving and an old save still loads`() {
        val engine = engineHolding(egg to 2)
        engine.sellStored(egg, 2)
        engine.beginNextDay()
        engine.beginNextDay()
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(3, loaded.day)
        val old = GameStateJson.decode("""{"money":42}""")
        assertTrue(old.day == 1 && old.salesToday.isEmpty())
    }
}
