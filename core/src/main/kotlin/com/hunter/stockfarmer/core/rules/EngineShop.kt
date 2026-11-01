package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero

/** Tiles and buildings on the map, or crops currently growing for seeds. */
fun GameEngine.placedOnMap(item: ShopItem): Int =
    when (item) {
        is ShopItem.Seed -> state.plots.count { (tile, plot) -> plot.cropID == item.cropID && plot.isAnchor(tile) }
        is ShopItem.GroundCoverItem -> state.groundCover.values.count { it == item.cover }
        ShopItem.Farmland -> state.farmland.size
        ShopItem.Water -> state.water.size
        is ShopItem.BuildingItem -> placedCount(ofBuildingKindOf = item.id)
        ShopItem.Fence -> state.fences.size
        is ShopItem.DecorationItem -> state.decorations.values.count { it.decorationID == item.id }
        is ShopItem.Tree -> state.trees.values.count { it.tree.species.id == item.id }
        ShopItem.Fertilizer -> state.fertilized.size
    }

/** The most of an item that can be placed at once, for the things that have a limit. */
fun GameEngine.placementLimit(item: ShopItem): Int? =
    when (item) {
        ShopItem.Farmland -> state.farmlandLimit
        is ShopItem.BuildingItem -> Building.named(item.id)?.placementLimit
        is ShopItem.Seed -> if (Crop.named(item.cropID)?.isFruitTree == true) Crop.MOST_FRUIT_TREES_OF_A_KIND else null
        is ShopItem.DecorationItem -> if (item.id == "trunk") Decoration.TRUNK_LIMIT else null
        else -> null
    }

fun GameEngine.hasReachedPlacementLimit(item: ShopItem): Boolean = placementLimit(item)?.let { placedOnMap(item) >= it } ?: false

/** How many are placed of a building, counting both tiers of a market stand and of the house as one kind. */
private fun GameEngine.placedCount(ofBuildingKindOf: String): Int {
    val kind = Building.named(ofBuildingKindOf)
    return state.buildings.values.count { other ->
        val placed = Building.named(other)
        when {
            kind?.isMarketStand == true -> placed?.isMarketStand == true
            kind?.isHouse == true -> placed?.isHouse == true
            else -> other == ofBuildingKindOf
        }
    }
}

/** What one use of the item costs: a seed per planted tile, a building per placement, a tile of path. */
fun GameEngine.price(item: ShopItem): Double =
    when (item) {
        is ShopItem.Seed -> Crop.named(item.cropID)?.seedPrice ?: 0.0
        is ShopItem.GroundCoverItem -> item.cover.cost
        ShopItem.Farmland -> 0.0
        ShopItem.Water -> LandscapeTool.waterCost
        is ShopItem.BuildingItem -> Building.named(item.id)?.cost ?: 0.0
        ShopItem.Fence -> FenceColor.price
        is ShopItem.DecorationItem -> Decoration.named(item.id)?.cost ?: 0.0
        is ShopItem.Tree -> tree(item.id)?.cost ?: 0.0
        ShopItem.Fertilizer -> Fertilizer.price
    }

fun GameEngine.refund(building: Building): Double = (building.cost * GameEngine.DESTROY_REFUND_SHARE).roundedToCents

fun GameEngine.refund(decoration: Decoration): Double = (decoration.cost * GameEngine.DESTROY_REFUND_SHARE).roundedToCents

/** The logs and stones destroying a building gives back. */
fun GameEngine.materialRefund(building: Building): Pair<Int, Int> =
    (building.logCost * GameEngine.DESTROY_REFUND_SHARE).roundedHalfAwayFromZero().toInt() to
        (building.stoneCost * GameEngine.DESTROY_REFUND_SHARE).roundedHalfAwayFromZero().toInt()

fun GameEngine.name(item: ShopItem): String =
    when (item) {
        is ShopItem.Seed -> Crop.named(item.cropID)?.plantingName ?: "Crop"
        is ShopItem.GroundCoverItem -> item.cover.displayName
        ShopItem.Farmland -> LandscapeTool.farmland.name
        ShopItem.Water -> LandscapeTool.water.name
        is ShopItem.BuildingItem -> Building.named(item.id)?.name ?: "Building"
        ShopItem.Fence -> "Fences"
        is ShopItem.DecorationItem -> Decoration.named(item.id)?.name ?: "Decoration"
        is ShopItem.Tree -> TreeSpecies.named(item.id)?.name ?: "Tree"
        ShopItem.Fertilizer -> "Fertilizer"
    }
