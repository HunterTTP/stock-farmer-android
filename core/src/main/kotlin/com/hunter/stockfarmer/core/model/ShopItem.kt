package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.TreeSpecies

sealed interface ShopItem {
    data class Seed(
        val cropID: String,
    ) : ShopItem

    data class GroundCoverItem(
        val cover: GroundCover,
    ) : ShopItem

    data object Farmland : ShopItem

    data object Water : ShopItem

    data class BuildingItem(
        val id: String,
    ) : ShopItem

    data object Fence : ShopItem

    data class DecorationItem(
        val id: String,
    ) : ShopItem

    data class Tree(
        val id: String,
    ) : ShopItem

    data object Fertilizer : ShopItem

    val unlockLevel: Int
        get() =
            when (this) {
                is Seed -> Crop.named(cropID)?.unlockLevel ?: 1
                is BuildingItem -> Building.named(id)?.unlockLevel ?: 1
                is Tree -> TreeSpecies.named(id)?.unlockLevel ?: 1
                is DecorationItem -> Decoration.named(id)?.unlockLevel ?: 1
                is GroundCoverItem, Farmland, Water, Fence, Fertilizer -> 1
            }
}
