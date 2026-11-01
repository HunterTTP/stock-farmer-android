package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.rules.CropEconomy
import com.hunter.stockfarmer.core.rules.FruitTreePhase

/** Something planted on farmland: a crop that is cut once, or a fruit tree that keeps bearing. */
data class Crop(
    val id: String,
    val name: String,
    val isFruitTree: Boolean = false,
) {
    /** Where it stands in the order everything unlocks: one crop or fruit tree per level. */
    val unlockLevel: Int get() = unlockLevels[id] ?: 1

    /**
     * What its prices and times follow. A fruit tree sits halfway between the crops either side of it, so the economy of
     * the plain crops is the same as if there were no trees.
     */
    val economyTier: Double get() = economyTiers[id] ?: -0.5

    /**
     * A seed costs this for every tile planted. A fruit tree is paid for once, and since it bears again without
     * replanting it costs a number of harvests' worth of its basket.
     */
    val seedPrice: Double
        get() =
            if (isFruitTree) {
                (sellValue * CropEconomy.HARVESTS_TO_REPAY_FRUIT_TREE).roundedToCents
            } else {
                CropEconomy.seedPrice(economyTier)
            }

    /** A fruit tree's basket pays one and a half harvests of a crop of its tier. */
    val sellValue: Double
        get() {
            val base = CropEconomy.sellValue(economyTier)
            return if (isFruitTree) (base * CropEconomy.FRUIT_TREE_CYCLES).roundedToCents else base
        }

    /** A fruit tree takes as long as three grow cycles of the crop unlocked right after it. */
    val growMinutes: Double
        get() =
            if (isFruitTree) {
                CropEconomy.growMinutes(economyTier + CropEconomy.NEXT_CROP_TIER_OFFSET) *
                    CropEconomy.FRUIT_TREE_GROW_CYCLES
            } else {
                CropEconomy.growMinutes(economyTier)
            }

    val growDuration: Double get() = growMinutes * 60

    /** What harvesting it puts in storage: the crop itself, or a basket of the fruit. */
    val produceName: String get() = if (isFruitTree) "${name.replace(" Tree", "")} Basket" else name

    /** The planting material offered by the Plant tool, distinct from the crop and its harvested produce. */
    val plantingName: String
        get() {
            if (isFruitTree) return name
            return when (id) {
                "potato" -> "Potato Tubers"
                "garlic" -> "Garlic Cloves"
                "asparagus" -> "Asparagus Crowns"
                "red_onion" -> "Red Onion Sets"
                "strawberry" -> "Strawberry Runners"
                "raspberry", "blackberry" -> "$name Canes"
                "pineapple" -> "Pineapple Crowns"
                "grapes" -> "Grape Cuttings"
                "prickly_pear" -> "Prickly Pear Pads"
                else -> "$name Seeds"
            }
        }

    /** How many tiles wide and deep it stands: a fruit tree takes a square of four tiles. */
    val side: Int get() = if (isFruitTree) FRUIT_TREE_SIDE else 1

    fun footprint(origin: GridPoint): List<GridPoint> = origin.rectangle(rows = side, cols = side)

    /**
     * The tiles it is kept on, which are the bottom row of its ground: what it takes up, the rest being only the room
     * its picture rises into.
     */
    fun plotTiles(origin: GridPoint): List<GridPoint> = footprint(origin).bottomRow

    /** The top left of the ground it covers when planted from a tapped tile: the bottom row, on the left of the middle. */
    fun origin(plantingOn: GridPoint): GridPoint = plantingOn.topLeftOfSquare(side)

    /** The file name of the tree's picture in one phase, for fruit trees. */
    fun treeAssetName(phase: FruitTreePhase): String {
        val phaseName = if (phase == FruitTreePhase.fruitless) "Fruitless" else "Ripe"
        return "Fruit_Tree_${id.capitalized()}_${phaseName}_48x48"
    }

    /** The file name of the basket of its fruit, for fruit trees. */
    val basketAssetName: String
        // The banana basket is named for the plural.
        get() = "Basket_${id.capitalized()}${if (id == "banana") "s" else ""}_48x48"

    /** The Harvest tool implement that gathers it: the scythe cuts crops and the bucket picks fruit. */
    val harvestTool: HarvestTool get() = if (isFruitTree) HarvestTool.bucket else HarvestTool.scythe

    companion object {
        const val FRUIT_TREE_SIDE = 2

        /** How many fruit trees of one kind can stand on the map at once. */
        const val MOST_FRUIT_TREES_OF_A_KIND = 6

        private const val CROPS_PER_FRUIT_TREE = 3

        val plainCrops: List<Crop> =
            listOf(
                "wheat" to "Wheat",
                "carrot" to "Carrot",
                "potato" to "Potato",
                "beetroot" to "Beetroot",
                "lettuce" to "Lettuce",
                "cabbage" to "Cabbage",
                "broccoli" to "Broccoli",
                "cauliflower" to "Cauliflower",
                "garlic" to "Garlic",
                "leek" to "Leek",
                "asparagus" to "Asparagus",
                "zucchini" to "Zucchini",
                "tomato" to "Tomato",
                "pepper" to "Pepper",
                "eggplant" to "Eggplant",
                "red_onion" to "Red Onion",
                "strawberry" to "Strawberry",
                "raspberry" to "Raspberry",
                "blackberry" to "Blackberry",
            ).map { (id, name) -> Crop(id, name) }

        /** A fruit tree comes after every third crop, in this order. */
        val fruitTrees: List<Crop> =
            listOf(
                "apple" to "Apple Tree",
                "orange" to "Orange Tree",
                "peach" to "Peach Tree",
                "lemon" to "Lemon Tree",
                "banana" to "Banana Tree",
            ).map { (id, name) -> Crop(id, name, isFruitTree = true) }

        /** Everything plantable in the order it unlocks, one at each level. */
        private val originalProgression: List<Crop> =
            plainCrops.flatMapIndexed { index, crop ->
                val treeNumber = (index + 1) / CROPS_PER_FRUIT_TREE - 1
                val followsTree = (index + 1) % CROPS_PER_FRUIT_TREE == 0 && treeNumber in fruitTrees.indices
                if (followsTree) listOf(crop, fruitTrees[treeNumber]) else listOf(crop)
            }

        val additional: List<Crop> =
            listOf(
                "radish" to "Radish",
                "turnip" to "Turnip",
                "corn" to "Corn",
                "pumpkin" to "Pumpkin",
                "watermelon" to "Watermelon",
                "pineapple" to "Pineapple",
                "cotton" to "Cotton",
                "grapes" to "Grapes",
                "prickly_pear" to "Prickly Pear",
                "coffee" to "Coffee",
            ).map { (id, name) -> Crop(id, name) }

        val progression: List<Crop> = originalProgression + additional

        val all: List<Crop> = progression

        val starter: Crop = all[0]

        private val byID = all.associateBy { it.id }
        private val unlockLevels = progression.withIndex().associate { (index, crop) -> crop.id to index + 1 }
        private val economyTiers: Map<String, Double> =
            buildMap {
                var plainCropsSoFar = 0
                for (crop in progression) {
                    put(crop.id, plainCropsSoFar - if (crop.isFruitTree) 0.5 else 0.0)
                    if (!crop.isFruitTree) plainCropsSoFar += 1
                }
            }

        fun named(id: String): Crop? = byID[id]
    }
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
