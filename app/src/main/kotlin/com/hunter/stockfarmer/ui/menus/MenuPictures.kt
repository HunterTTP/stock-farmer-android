package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.rules.Butterflies

internal sealed interface MenuPicture {
    data class Asset(
        val path: String,
        val chickenFrame: Boolean = false,
    ) : MenuPicture

    data class Water(
        val style: WaterStyle,
    ) : MenuPicture

    data class Ground(
        val cover: GroundCover,
    ) : MenuPicture

    data class Symbol(
        val text: String,
    ) : MenuPicture

    data class Butterfly(
        val wings: Butterflies.Wings,
    ) : MenuPicture
}

internal object MenuPictures {
    fun building(building: Building) = asset("buildings", building.assetName)

    fun decoration(
        decoration: Decoration,
        size: Int? = null,
    ): MenuPicture =
        asset(
            decoration.imageFolder,
            decoration.assetNames[decoration.offeredSize(size ?: decoration.offeredSizes.last())],
        )

    fun fence(color: FenceColor) = asset("decorations/${color.folder}", "fence-middle-horizontal")

    fun tree(
        tree: Tree,
        color: TreeColor,
    ) = asset("trees", tree.assetName(color))

    fun terrain(name: String) = MenuPicture.Asset("images/terrain/$name.jpg")

    fun stored(item: StorageItem): MenuPicture =
        when (item) {
            is StorageItem.CropItem -> crop(item.crop)
            is StorageItem.RareCrop -> crop(item.crop)
            StorageItem.Egg -> asset("animals/Chickens", "Egg_Big_48x48")
            StorageItem.Milk, StorageItem.GoatMilk -> asset("animals/Cows", "Milk_Can_2_48x48")
            StorageItem.Fertilizer -> asset("tools", "fertilizer")
            StorageItem.Wool -> MenuPicture.Symbol("🧶")
            StorageItem.Flour -> MenuPicture.Symbol("▣")
            StorageItem.Bread -> MenuPicture.Symbol("🍞")
            StorageItem.Planks -> MenuPicture.Symbol("🪵")
            StorageItem.Cloth -> MenuPicture.Symbol("🧵")
            StorageItem.Bricks -> MenuPicture.Symbol("🧱")
            StorageItem.Cheese -> asset("products", "Cheese_1_48x48")
            is StorageItem.Artisan -> asset("products", item.good.assetName)
            StorageItem.Log -> asset("trees", "log")
            StorageItem.Stone -> asset("rocks", RockSize.small.assetName)
            is StorageItem.Ore -> asset("rocks", item.ore.rock.assetName)
            is StorageItem.ChickenItem -> asset("animals/Chickens", item.variant.assetName).copy(chickenFrame = true)
            is StorageItem.Fish -> asset("animals/Fish", item.kind.assetName)
            is StorageItem.DecorationItem -> decoration(item.decoration, item.size)
            is StorageItem.Fence -> fence(item.color)
            is StorageItem.Butterfly -> MenuPicture.Butterfly(item.wings)
        }

    private fun crop(crop: Crop): MenuPicture =
        if (crop.isFruitTree) asset("crops/fruit_trees", crop.basketAssetName) else asset("crops", "${crop.id}-phase-4")

    private fun asset(
        folder: String,
        name: String,
    ) = MenuPicture.Asset("images/$folder/$name.png")
}
