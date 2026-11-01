package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.StorageItem

internal fun ScreenArt.Companion.item(item: StorageItem): ScreenArt? =
    when (item) {
        is StorageItem.Artisan -> image("products", item.good.assetName)
        is StorageItem.CropItem -> crop(item.crop)
        is StorageItem.Fish -> image("animals/Fish", item.kind.assetName)
        StorageItem.Cheese -> image("products", "Cheese_1_48x48")
        StorageItem.Egg -> image("animals/Chickens", "Egg_Big_48x48")
        StorageItem.Milk, StorageItem.GoatMilk -> image("animals/Cows", "Milk_Can_2_48x48")
        StorageItem.Fertilizer -> image("tools", "fertilizer")
        StorageItem.Log -> image("trees", "log")
        else -> null
    }

internal val StorageItem.iconSymbol: String
    get() =
        when (this) {
            StorageItem.Flour -> "▣"
            StorageItem.Bread -> "🍞"
            StorageItem.Planks -> "🪵"
            StorageItem.Cloth -> "🧵"
            StorageItem.Bricks -> "🧱"
            StorageItem.Wool -> "🧶"
            else -> "✦"
        }
