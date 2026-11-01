package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.GameEngine

/**
 * Something that can be kept in storage and sold from it: a crop, something the animals give, a log, a stone,
 * something a workshop made, a grown chicken of one color, a fish, a decoration of one size, or a fence.
 */
sealed interface StorageItem {
    data class CropItem(
        val crop: Crop,
    ) : StorageItem

    /** Rare produce already in older saves remains sellable after Golden Scarecrow removal. */
    data class RareCrop(
        val crop: Crop,
    ) : StorageItem

    data object Egg : StorageItem

    data object Milk : StorageItem

    data object GoatMilk : StorageItem

    data object Fertilizer : StorageItem

    data object Wool : StorageItem

    data object Log : StorageItem

    data object Stone : StorageItem

    data class Ore(
        val ore: OreKind,
    ) : StorageItem

    data object Flour : StorageItem

    data object Bread : StorageItem

    data object Planks : StorageItem

    data object Cloth : StorageItem

    data object Bricks : StorageItem

    data object Cheese : StorageItem

    data class Artisan(
        val good: ArtisanGood,
    ) : StorageItem

    data class ChickenItem(
        val variant: ChickenVariant,
    ) : StorageItem

    data class Fish(
        val kind: FishKind,
    ) : StorageItem

    data class DecorationItem(
        val decoration: Decoration,
        val size: Int,
    ) : StorageItem

    data class Fence(
        val color: FenceColor,
    ) : StorageItem

    /** Caught with the Bucket and let go again with the Decorate tool; worth nothing, so it can only be released. */
    data class Butterfly(
        val wings: Butterflies.Wings,
    ) : StorageItem

    val unlockLevel: Int
        get() =
            when (this) {
                is CropItem -> {
                    crop.unlockLevel
                }

                is RareCrop -> {
                    crop.unlockLevel
                }

                Egg, is ChickenItem -> {
                    Building.named("chickenCoop")?.unlockLevel ?: 1
                }

                Milk -> {
                    Building.named("barn2")?.unlockLevel ?: 1
                }

                GoatMilk -> {
                    Building.named("goatBarn")?.unlockLevel ?: 1
                }

                Wool -> {
                    Building.named("barn1")?.unlockLevel ?: 1
                }

                Fertilizer -> {
                    Building.named("rabbitHutch")?.unlockLevel ?: 1
                }

                is DecorationItem -> {
                    decoration.unlockLevel
                }

                is Fence -> {
                    1
                }

                Log, Stone, is Ore, is Fish, is Butterfly -> {
                    1
                }

                Flour, Bread, Planks, Cloth, Bricks, Cheese, is Artisan -> {
                    Recipe.all.filter { it.output == this }.minOfOrNull { it.unlockLevel } ?: 1
                }
            }

    /** What storage keys the item by: the crop's id, the chicken color's id, or the fish's id with `fish-` before it. */
    val id: String
        get() =
            when (this) {
                is CropItem -> crop.id
                is RareCrop -> "rare-${crop.id}"
                Egg -> "egg"
                Milk -> "milk"
                GoatMilk -> "goatMilk"
                Fertilizer -> "fertilizer"
                Wool -> "wool"
                Log -> "log"
                Stone -> "stone"
                is Ore -> "ore-${ore.rawValue}"
                Flour -> "flour"
                Bread -> "bread"
                Planks -> "planks"
                Cloth -> "cloth"
                Bricks -> "bricks"
                Cheese -> "cheese"
                is Artisan -> good.rawValue
                is ChickenItem -> variant.id
                is Fish -> "fish-${kind.id}"
                is DecorationItem -> "decoration-${decoration.id}-$size"
                is Fence -> "fence-${color.id}"
                is Butterfly -> "butterfly-${wings.id}"
            }

    val name: String
        get() =
            when (this) {
                is CropItem -> {
                    crop.produceName
                }

                is RareCrop -> {
                    "Rare ${crop.produceName}"
                }

                Egg -> {
                    "Egg"
                }

                Milk -> {
                    "Milk"
                }

                GoatMilk -> {
                    "Goat Milk"
                }

                Fertilizer -> {
                    "Fertilizer"
                }

                Wool -> {
                    "Wool"
                }

                Log -> {
                    "Log"
                }

                Stone -> {
                    "Stone"
                }

                is Ore -> {
                    ore.displayName
                }

                Flour -> {
                    "Flour"
                }

                Bread -> {
                    "Bread"
                }

                Planks -> {
                    "Planks"
                }

                Cloth -> {
                    "Cloth"
                }

                Bricks -> {
                    "Bricks"
                }

                Cheese -> {
                    "Cheese"
                }

                is Artisan -> {
                    good.displayName
                }

                is ChickenItem -> {
                    variant.name
                }

                is Fish -> {
                    kind.name
                }

                is DecorationItem -> {
                    if (decoration.sizeNames.isEmpty()) {
                        decoration.name
                    } else {
                        "${decoration.name}: ${decoration.sizeNames[size]}"
                    }
                }

                is Fence -> {
                    "${color.name} Fence"
                }

                is Butterfly -> {
                    when (val wings = wings) {
                        is Butterflies.Wings.Color -> "${wings.rarity.displayName} Butterfly"
                        Butterflies.Wings.Shifting -> "Shifting Butterfly"
                    }
                }
            }

    /** What one of it sells for. */
    val sellValue: Double
        get() =
            when (this) {
                is CropItem -> {
                    crop.sellValue
                }

                is RareCrop -> {
                    crop.sellValue * 2
                }

                Egg -> {
                    com.hunter.stockfarmer.core.model.Egg.SALE_PRICE
                }

                Milk -> {
                    LivestockSpecies.MILK_SALE_PRICE
                }

                GoatMilk -> {
                    LivestockSpecies.GOAT_MILK_SALE_PRICE
                }

                Fertilizer -> {
                    com.hunter.stockfarmer.core.catalog.Fertilizer.price / 2
                }

                Wool -> {
                    LivestockSpecies.WOOL_SALE_PRICE
                }

                Log -> {
                    Tree.logSalePrice
                }

                Stone -> {
                    Rock.stoneSalePrice
                }

                is Ore -> {
                    ore.value
                }

                Flour, Bread, Planks, Cloth, Bricks, Cheese -> {
                    Recipe.all.firstOrNull { it.output == this }?.outputValue ?: 0.0
                }

                is Artisan -> {
                    good.value
                }

                is ChickenItem -> {
                    Chicken.GROWN_SALE_PRICE
                }

                is Fish -> {
                    kind.value
                }

                is DecorationItem -> {
                    (decoration.cost * GameEngine.DESTROY_REFUND_SHARE).roundedToCents
                }

                is Fence -> {
                    (FenceColor.price * GameEngine.DESTROY_REFUND_SHARE).roundedToCents
                }

                is Butterfly -> {
                    0.0
                }
            }

    /** Whether it can be sold: logs and stones are for building and sell for nothing, so they can only be thrown away. */
    val isSellable: Boolean get() = sellValue > 0

    /** Whether Equip does something with it: decorations and fences are set down, fish let go or fed, crops fed to animals. */
    val isEquippable: Boolean
        get() =
            when (this) {
                is DecorationItem, is Fence, is Fish, is CropItem, is RareCrop, Fertilizer, is Butterfly -> true
                else -> false
            }

    /** What the button that throws it away says: a butterfly or a fish is let go rather than trashed. */
    val discardTitle: String get() = if (isLetGo) "Release" else "Trash"

    val discardedTitle: String get() = if (isLetGo) "Released" else "Trashed"

    private val isLetGo: Boolean get() = this is Butterfly || this is Fish

    companion object {
        /** Every kind of item in catalog order; menus use [menuOrder] to follow unlock levels. */
        val all: List<StorageItem> by lazy {
            Crop.all.map(::CropItem) +
                Crop.all.map(::RareCrop) +
                listOf(Egg, Milk, GoatMilk, Fertilizer, Wool, Log, Stone, Flour, Bread, Planks, Cloth, Bricks, Cheese) +
                OreKind.entries.map(::Ore) +
                ArtisanGood.entries.map(::Artisan) +
                ChickenVariant.available.map(::ChickenItem) +
                FishKind.all.map(::Fish) +
                decorations +
                FenceColor.all.map(::Fence) +
                Butterflies.Wings.all.map(::Butterfly)
        }

        /** Every decoration a player can set down, in each of its sizes, except rocks, which are broken with the Pickaxe. */
        private val decorations: List<StorageItem> by lazy {
            Decoration.all.filter { !it.isRock }.flatMap { decoration ->
                decoration.offeredSizes.map { DecorationItem(decoration, it) }
            }
        }

        val menuOrder: List<StorageItem> by lazy { UnlockOrder.sorted(all) { it.unlockLevel } }

        private val byID: Map<String, StorageItem> by lazy { all.associateBy { it.id } }

        fun of(id: String): StorageItem? = byID[id]
    }
}
