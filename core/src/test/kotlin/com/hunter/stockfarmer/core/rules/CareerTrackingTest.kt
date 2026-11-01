package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.hatch
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class CareerTrackingTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val spot = GridPoint(20, 20)

    private fun richEngine(building: String): GameEngine {
        val engine =
            GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = building
        return engine
    }

    private fun quote(cents: Long) = StockQuote("AAPL", cents, now)

    @Test
    fun `planting a crop remembers it`() {
        val engine = GameEngine()
        assertFalse(engine.hasPlanted(Crop.starter))
        engine.tap(GridPoint(24, 24), now)
        assertTrue(engine.hasPlanted(Crop.starter))
        assertFalse(engine.hasPlanted(Crop.all[1]))
    }

    @Test
    fun `building remembers the building but moving it does not`() {
        val engine = richEngine("shed")
        val shed = Building.named("shed")!!
        assertFalse(engine.hasBuilt(shed))
        engine.build(spot, now)
        assertTrue(engine.hasBuilt(shed))
        engine.state.career = CareerRecord()
        engine.beginMoving(engine.state.placedBuildings[0])
        engine.nudgePlacement(0, 3)
        engine.confirmPlacement(now)
        assertFalse(engine.hasBuilt(shed))
    }

    @Test
    fun `setting down a decoration remembers it and refunds do not count as income`() {
        val engine = richEngine("shed")
        val decoration = Decoration.all.first { it.offeredSizes.isNotEmpty() && !it.isSprinkler && !it.isRock }
        engine.state.mode = PlayMode.decorate
        engine.select(decoration)
        engine.tap(spot, now)
        assertTrue(engine.hasPlaced(decoration))
        engine.destroyDecoration(spot, now)
        assertEquals(0.0, engine.grossIncome)
    }

    @Test
    fun `landing a fish keeps the heaviest of its kind`() {
        val anchor = GridPoint(20, 20)
        val engine = GameEngine()
        for (row in 20..23) for (col in 20..23) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
        val carp = FishKind.named("carp")!!
        for (weight in listOf(12.0, 7.0)) {
            engine.state.fishPonds =
                mutableMapOf(anchor to FishPond(PondFish("carp", GridPoint(21, 21), weight), now))
            engine.landFish(anchor, GridPoint(21, 21), now, selling = true)
        }
        assertEquals(12.0, engine.heaviestCatch(carp))
        assertNull(engine.heaviestCatch(FishKind.all.first { it.id != "carp" }))
    }

    @Test
    fun `gathering an egg remembers the hen that laid it`() {
        val engine = richEngine("chickenCoop")
        engine.build(spot, Instant.ofEpochSecond(1))
        engine.hatch(now)
        val hen = engine.chickens(spot).first()
        engine.state.storage = mutableMapOf()
        engine.state.eggs = listOf(Egg(GridPoint(30, 30), 0.0, 0.0, spot, hen.id))
        engine.collectEggs(GridPoint(30, 30), now)
        assertTrue(engine.hasWorked(hen.variant))
        assertTrue(lyingEgg(spot, 0.0, 0.0).point == spot)
    }

    @Test
    fun `a pet moving in is remembered`() {
        val engine = richEngine("petHouse")
        engine.build(spot, now)
        val house = engine.state.placedBuildings[0].origin
        engine.movePetIn(house, PetBreed.cat(Cat.named("grey")!!), "Mittens")
        assertTrue(engine.hasWorked("grey"))
    }

    @Test
    fun `gross income adds farm sales and stock gains but not spending or losses`() {
        val engine = GameEngine(stateWith(money = 1_000.0))
        engine.state.storage[Crop.starter.id] = 3
        engine.sellStored(StorageItem.CropItem(Crop.starter), 3)
        val sales = engine.grossIncome
        assertTrue(sales == engine.saleValue(StorageItem.CropItem(Crop.starter), 3) && sales > 0)
        engine.buyShares(quote(1_000), 2)
        assertEquals(sales, engine.grossIncome)
        engine.sellShares(quote(1_500), 1)
        assertEquals(sales + 5, engine.grossIncome)
        engine.sellShares(quote(500), 1)
        assertEquals(sales + 5, engine.grossIncome)
        assertEquals(0.0, engine.lifetimeStockProfit)
    }

    @Test
    fun `play time adds to the XP chart and the chart always spans the whole career`() {
        val engine = GameEngine(stateWith(xp = 50))
        engine.addPlayTime(400.0)
        engine.state.xp = 90
        engine.addPlayTime(400.0)
        assertEquals(
            listOf(ValueSample(0.0, 0), ValueSample(400.0, 50), ValueSample(800.0, 90)),
            engine.history(CareerSeries.XP),
        )
    }

    @Test
    fun `laying tiles and planting trees remembers each kind`() {
        val engine = GameEngine()
        engine.state.money = 100.0
        engine.state.mode = PlayMode.landscape
        assertFalse(engine.hasLaid("grass"))
        engine.select(LandscapeTool.grass)
        engine.state.water[GridPoint(5, 5)] = WaterStyle.deep
        engine.tap(GridPoint(5, 5), now)
        assertTrue(engine.hasLaid("grass"))
        engine.select(LandscapeTool.groundCover(GroundCover.dirtPath))
        engine.tap(GridPoint(30, 30), now)
        assertTrue(engine.hasLaid("dirtPath") && !engine.hasLaid("cobblestonePath"))
        engine.select(WaterStyle.shallow)
        engine.select(LandscapeTool.water)
        engine.tap(GridPoint(32, 32), now)
        assertTrue(engine.hasLaid("water-shallow") && !engine.hasLaid("water-deep"))
        engine.select(LandscapeTool.tree)
        engine.tap(GridPoint(35, 35), now)
        assertTrue(engine.hasLaid(CareerRecord.terrainID(Tree.starter, engine.selectedTreeColor)))
    }

    @Test
    fun `free water placement and recoloring credit every color in career`() {
        val engine = GameEngine()
        val point = GridPoint(5, 5)
        for (style in WaterStyle.entries) {
            engine.select(style)
            assertEquals(listOf<GameEvent>(GameEvent.Placed(point)), engine.tap(point, now))
            assertTrue(engine.hasLaid(CareerRecord.terrainID(style)))
            assertEquals(0.0, engine.state.money)
        }
    }

    @Test
    fun `best days count the day under way and keep the best of the days before`() {
        val engine = GameEngine(stateWith(money = 1_000.0))
        val wheat = StorageItem.CropItem(Crop.starter)
        engine.state.storage[Crop.starter.id] = 10
        engine.sellStored(wheat, 4)
        val first = engine.grossIncome
        assertEquals(first, engine.bestDayIncome)
        engine.beginNextDay()
        engine.sellStored(wheat, 1)
        assertEquals(first, engine.bestDayIncome)
        engine.beginNextDay()
        engine.sellStored(wheat, 5)
        assertEquals(
            engine.grossIncome - first - engine.saleValue(wheat, 1),
            engine.bestDayIncome,
            1e-9,
        )
    }

    @Test
    fun `a bad stock day never lowers the best stock day`() {
        val engine = GameEngine(stateWith(money = 10_000.0))
        engine.buyShares(quote(1_000), 2)
        engine.sellShares(quote(1_500), 1)
        assertEquals(5.0, engine.bestDayStockProfit)
        engine.beginNextDay()
        engine.sellShares(quote(100), 1)
        assertEquals(5.0, engine.bestDayStockProfit)
        assertEquals(-4.0, engine.lifetimeStockProfit)
    }

    @Test
    fun `money and stock charts follow play time`() {
        val engine = GameEngine(stateWith(money = 1_000.0))
        engine.state.storage[Crop.starter.id] = 3
        engine.addPlayTime(400.0)
        engine.sellStored(StorageItem.CropItem(Crop.starter), 3)
        engine.addPlayTime(400.0)
        val income = engine.history(CareerSeries.INCOME)
        assertEquals(listOf(0.0, 400.0, 800.0), income.map { it.playTime })
        assertEquals(Math.round(engine.grossIncome * 100), income.last().value)
        val stock = engine.history(CareerSeries.STOCK_PROFIT)
        assertTrue(stock.map { it.playTime } == listOf(0.0, 400.0, 800.0) && stock.all { it.value == 0L })
    }

    @Test
    fun `harvesting counts every crop and fruit tree`() {
        val engine = GameEngine()
        engine.state.mode = PlayMode.harvest
        val point = GridPoint(24, 24)
        engine.state.plots[point] = Plot(Crop.starter.id, now.advancedBy(-1_000_000.0))
        engine.tap(point, now)
        assertEquals(1, engine.cropsHarvested)
    }

    @Test
    fun `breaking a rock remembers its size`() {
        val engine = GameEngine()
        engine.state.mode = PlayMode.harvest
        engine.state.harvestTool = HarvestTool.pickaxe
        val rock = GridPoint(10, 10)
        engine.state.rocks[rock] = RockSize.small
        repeat(engine.hitsToBreak(RockSize.small)) {
            engine.mineRock(rock)
            engine.liftTool()
        }
        assertTrue(engine.hasMined(RockSize.small) && !engine.hasMined(RockSize.big))
    }

    @Test
    fun `a finished workshop remembers its recipe`() {
        val engine = richEngine("grainMachine")
        engine.build(spot, Instant.ofEpochSecond(1))
        val origin = engine.state.placedBuildings[0].origin
        val recipe = engine.state.workshopRecipe(origin)!!
        engine.state.storage = mutableMapOf(recipe.input.id to recipe.inputCount)
        engine.startWork(origin, now)
        engine.collectGoods(origin, now.advancedBy(recipe.duration + 1))
        assertTrue(engine.hasMade(recipe))
    }

    @Test
    fun `sightings are remembered`() {
        val engine = GameEngine()
        assertTrue(engine.hasSeenEnvironment(CareerRecord.environmentID(engine.state.season)))
        engine.noteSeenEnvironment(CareerRecord.environmentID(Butterflies.Wings.Shifting))
        assertTrue(engine.hasSeenEnvironment("butterfly:shifting"))
        assertFalse(engine.hasSeenEnvironment("butterfly:rare"))
    }
}
