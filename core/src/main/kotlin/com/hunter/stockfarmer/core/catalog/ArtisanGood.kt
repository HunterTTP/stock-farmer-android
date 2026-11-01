@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero

enum class ArtisanGood(
    val displayName: String,
    val buildingID: String,
    private val inputItem: () -> StorageItem,
    val inputCount: Int,
    private val minutes: Double,
) {
    copperBar("Copper Bar", "stoneOven", { StorageItem.Ore(OreKind.copper) }, 6, 10.0),
    silverBar("Silver Bar", "stoneOven", { StorageItem.Ore(OreKind.silver) }, 6, 10.0),
    goldBar("Gold Bar", "stoneOven", { StorageItem.Ore(OreKind.gold) }, 6, 10.0),
    polishedRuby("Polished Ruby", "diyTable", { StorageItem.Ore(OreKind.ruby) }, 6, 10.0),
    polishedSapphire("Polished Sapphire", "diyTable", { StorageItem.Ore(OreKind.sapphire) }, 6, 10.0),
    grapeJuice("Grape Juice", "grainMachine", { cropItem("grapes") }, 3, 4.0),
    watermelonJuice("Watermelon Juice", "grainMachine", { cropItem("watermelon") }, 4, 4.0),
    goatCheese("Goat Cheese", "dairy", { StorageItem.GoatMilk }, 5, 8.0),
    agedCheese("Aged Cheese", "cheeseCellar", { StorageItem.Cheese }, 2, 20.0),
    roastedCoffee("Roasted Coffee", "stoneOven", { cropItem("coffee") }, 4, 6.0),
    pineapplePreserves("Pineapple Preserves", "stoneOven", { cropItem("pineapple") }, 4, 7.0),
    pricklyPearPreserves("Prickly Pear Preserves", "stoneOven", { cropItem("prickly_pear") }, 4, 7.0),
    pumpkinPie("Pumpkin Pie", "stoneOven", { cropItem("pumpkin") }, 4, 5.0),
    turnipSoup("Turnip Soup", "stoneOven", { cropItem("turnip") }, 8, 3.0),
    radishPickles("Radish Pickles", "diyTable", { cropItem("radish") }, 8, 3.0),
    furniture("Furniture", "woodworkBench", { StorageItem.Planks }, 3, 10.0),
    fineCloth("Fine Cloth", "tailorTable", { StorageItem.Cloth }, 2, 10.0),
    applePreserves("Apple Preserves", "stoneOven", { cropItem("apple") }, 2, 4.0),
    orangePreserves("Orange Preserves", "stoneOven", { cropItem("orange") }, 2, 4.0),
    peachPreserves("Peach Preserves", "stoneOven", { cropItem("peach") }, 2, 4.0),
    lemonPreserves("Lemon Preserves", "stoneOven", { cropItem("lemon") }, 2, 4.0),
    bananaPreserves("Banana Preserves", "stoneOven", { cropItem("banana") }, 2, 4.0),
    ;

    val rawValue: String get() = name
    val input: StorageItem get() = inputItem()
    val duration: Double get() = minutes * 60
    val value: Double get() = (input.sellValue * inputCount * 1.5 * 100).roundedHalfAwayFromZero() / 100
    val assetName: String get() = "artisan_$rawValue"
}

private fun cropItem(id: String): StorageItem = StorageItem.CropItem(checkNotNull(Crop.named(id)))
