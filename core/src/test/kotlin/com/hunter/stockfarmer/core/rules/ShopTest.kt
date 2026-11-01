package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import kotlin.math.abs

class ShopTest {
    private val engine = GameEngine(stateWith(xp = lateGameXP))

    @Test
    fun `wheat seeds farmland and water are free`() {
        assertEquals(0.0, engine.price(ShopItem.Seed("wheat")))
        assertEquals(0.0, engine.price(ShopItem.Farmland))
        assertEquals(0.0, engine.price(ShopItem.Water))
    }

    @Test
    fun `other seeds and buildings cost their catalog price`() {
        assertEquals(Crop.named("carrot")!!.seedPrice, engine.price(ShopItem.Seed("carrot")))
        assertEquals(6_000.0, engine.price(ShopItem.BuildingItem("barn1")))
    }

    @Test
    fun `buildings refund eighty percent of their price`() {
        assertEquals(4_800.0, engine.refund(Building.named("barn1")!!))
    }

    @Test
    fun `items are named for the list`() {
        assertEquals("Carrot Seeds", engine.name(ShopItem.Seed("carrot")))
        assertEquals("Chicken Coop", engine.name(ShopItem.BuildingItem("chickenCoop")))
        assertEquals("Farmland", engine.name(ShopItem.Farmland))
    }

    @ParameterizedTest
    @CsvSource(
        "wheat,Wheat Seeds",
        "carrot,Carrot Seeds",
        "potato,Potato Tubers",
        "beetroot,Beetroot Seeds",
        "lettuce,Lettuce Seeds",
        "cabbage,Cabbage Seeds",
        "broccoli,Broccoli Seeds",
        "cauliflower,Cauliflower Seeds",
        "garlic,Garlic Cloves",
        "leek,Leek Seeds",
        "asparagus,Asparagus Crowns",
        "zucchini,Zucchini Seeds",
        "tomato,Tomato Seeds",
        "pepper,Pepper Seeds",
        "eggplant,Eggplant Seeds",
        "red_onion,Red Onion Sets",
        "strawberry,Strawberry Runners",
        "raspberry,Raspberry Canes",
        "blackberry,Blackberry Canes",
        "apple,Apple Tree",
        "orange,Orange Tree",
        "peach,Peach Tree",
        "lemon,Lemon Tree",
        "banana,Banana Tree",
        "radish,Radish Seeds",
        "turnip,Turnip Seeds",
        "corn,Corn Seeds",
        "pumpkin,Pumpkin Seeds",
        "watermelon,Watermelon Seeds",
        "pineapple,Pineapple Crowns",
        "cotton,Cotton Seeds",
        "grapes,Grape Cuttings",
        "prickly_pear,Prickly Pear Pads",
        "coffee,Coffee Seeds",
    )
    fun `planting items use the appropriate plant material`(
        id: String,
        expected: String,
    ) {
        assertEquals(expected, engine.name(ShopItem.Seed(id)))
    }

    @Test
    fun `planting names keep harvested produce and growing plant names separate`() {
        assertEquals("Wheat", Crop.starter.name)
        assertEquals("Wheat", StorageItem.CropItem(Crop.starter).name)
        assertEquals("Apple Basket", StorageItem.CropItem(Crop.named("apple")!!).name)
        assertEquals("Potato", StorageItem.CropItem(Crop.named("potato")!!).name)
    }

    @Test
    fun `planting taps spend money on every tile`() {
        val engine = GameEngine(stateWith(money = 100.0, xp = LevelRules.xpToReach(2)))
        engine.select(Crop.named("carrot")!!)
        val price = Crop.named("carrot")!!.seedPrice
        val now = Instant.ofEpochSecond(0)
        engine.tap(GridPoint(24, 24), now)
        engine.tap(GridPoint(24, 25), now)
        assertTrue(abs(engine.state.money - (100 - 2 * price)) < 1e-9)
    }
}
