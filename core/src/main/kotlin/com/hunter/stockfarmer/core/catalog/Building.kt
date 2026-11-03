package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.StockMarketRules
import kotlin.math.max
import kotlin.math.min

/**
 * One of the ways a building can look, such as a color of paint or the way a tractor faces. A style has its own picture
 * and, when the picture is a different shape, its own footprint.
 */
data class BuildingStyle(
    val id: String,
    val name: String,
    val assetName: String,
    val width: Int,
    val height: Int,
)

data class Building(
    val id: String,
    val assetName: String,
    val name: String,
    val width: Int,
    val height: Int,
    val cost: Double,
    /** How many logs it takes from storage to build. */
    val logCost: Int,
    /** How many stones it takes from storage to build. */
    val stoneCost: Int,
    /** The player level at which it can be built. */
    val unlockLevel: Int,
    /** The picture of it while it holds something finished, for the buildings that look different when they do. */
    val fullAssetName: String? = null,
    /** Every look the building comes in, the first being how it is when nothing else is picked; empty if it has only one. */
    val styles: List<BuildingStyle> = emptyList(),
    /** The id of the style this copy of the building has, or null for the first style or a building without any. */
    val styleID: String? = null,
) {
    /** This building in one of its styles. The first style, and one it doesn't have, give the building as it is. */
    fun styled(id: String?): Building {
        if (id == null || id == styles.firstOrNull()?.id) return this
        val style = styles.firstOrNull { it.id == id } ?: return this
        return copy(assetName = style.assetName, width = style.width, height = style.height, styleID = style.id)
    }

    /** The style this copy of the building has. */
    val style: BuildingStyle? get() = styles.firstOrNull { it.id == styleID } ?: styles.firstOrNull()

    /** A pet house holds one pet and, unlike other buildings, doesn't speed up crops. */
    val isPetHouse: Boolean get() = id == "petHouse"

    /** The player's house, which opens the Stock Market; upgraded, it is the luxury house. */
    val isHouse: Boolean get() = id == "house1" || isUpgradedHouse
    val isUpgradedHouse: Boolean get() = id == "house2"

    /** A chicken coop hatches chickens over time. */
    val isChickenCoop: Boolean get() = id == "chickenCoop"

    /** How many items this building adds to the storage; null for buildings that aren't storage. */
    val storageCapacity: Int?
        get() =
            when (id) {
                "shed" -> 50
                "silo" -> 150
                else -> null
            }

    /** Every building has a torch at its door at night, except the tractor, which has no door. */
    val glowsAtNight: Boolean get() = id != "tractor1"

    val isStorage: Boolean get() = storageCapacity != null
    val isMarketStand: Boolean get() = id == "marketStand" || isUpgradedMarketStand
    val isUpgradedMarketStand: Boolean get() = id == "marketStandLarge"

    /** How many of this building can be placed at once: the pet house, coop, shed and silo have several. */
    val placementLimit: Int
        get() =
            when {
                isPetHouse -> 10
                isChickenCoop -> 4
                id == "shed" || id == "silo" -> 4
                else -> 1
            }

    /** What kind of livestock live in this building, how many, and how fast, if it is a home for them. */
    val livestockHome: LivestockHome? get() = LivestockHome.of(this)

    /** Houses, coops and livestock homes send their animals out onto the tile in front of them, which must stay free. */
    val needsFrontTile: Boolean get() = isPetHouse || isChickenCoop || livestockHome != null

    fun frontTile(origin: GridPoint): GridPoint = GridPoint(origin.row + height, origin.col + width / 2)

    /** The tile of the building the animals walk onto to go in, straight behind the front tile. */
    fun doorTile(origin: GridPoint): GridPoint {
        val front = frontTile(origin)
        return GridPoint(front.row - 1, front.col)
    }

    /**
     * The nearest origin to this one that keeps the ground the building stands on, its bottom row of tiles, inside the
     * map: the rest of it may rise past the back edge. The Tractor takes up all its tiles, so stays wholly inside.
     */
    fun clampedOrigin(origin: GridPoint): GridPoint =
        GridPoint(
            min(max(origin.row, if (id == "tractor1") 0 else 1 - height), GameState.GRID_SIZE - height),
            min(max(origin.col, 0), GameState.GRID_SIZE - width),
        )

    fun occupiedTiles(origin: GridPoint): List<GridPoint> {
        val tiles = footprint(origin)
        return if (id == "tractor1") tiles else tiles.bottomRow
    }

    fun footprint(origin: GridPoint): List<GridPoint> = origin.rectangle(rows = height, cols = width)

    companion object {
        val marketStand =
            Building(
                id = "marketStand",
                assetName = "market_stand_small",
                name = "Market Stand",
                width = 2,
                height = 2,
                cost = 1000.0,
                logCost = 35,
                stoneCost = 4,
                unlockLevel = 6,
                styles = marketStyles(large = false),
            )

        // The saved large ID remains the upgrade tier, but is never offered in the shop.
        val upgradedMarketStand =
            Building(
                id = "marketStandLarge",
                assetName = "market_stand_big",
                name = "Market Stand",
                width = 3,
                height = 2,
                cost = marketStand.cost + QuestRules.UPGRADE_COST,
                logCost = marketStand.logCost,
                stoneCost = marketStand.stoneCost,
                unlockLevel = marketStand.unlockLevel,
                styles = marketStyles(large = true),
            )

        val house =
            Building("house1", "house_1", "House", 4, 4, 8500.0, 57, 8, 12)

        // The saved luxury house ID remains the upgrade tier of the house, but is never offered in the shop.
        val upgradedHouse =
            Building(
                id = "house2",
                assetName = "house_2",
                name = "House",
                width = 4,
                height = 5,
                cost = house.cost + StockMarketRules.HOUSE_UPGRADE_COST,
                logCost = house.logCost,
                stoneCost = house.stoneCost,
                unlockLevel = house.unlockLevel,
            )

        /**
         * Buildings are ordered by price; the cheapest unlocks at level 3 and the dearest at level 19. The logs every
         * building asks for add up to about the logs in all the trees a new map starts with, and the stones to about the
         * stones in its rocks, each shared out by price so that dearer buildings take more of them.
         */
        val progression: List<Building> =
            listOf(
                Building("petHouse", "pet_house_1", "Pet House", 1, 1, 25.0, 15, 2, 3),
                Building("chickenCoop", "pet_house_2", "Chicken Coop", 1, 2, 250.0, 30, 3, 5),
                marketStand,
                Building("shed", "chicken_coop_1", "Shed", 2, 2, 2500.0, 38, 5, 7),
                Building("rabbitHutch", "hen_house_1", "Rabbit Hutch", 3, 2, 4000.0, 43, 6, 8),
                Building(
                    "grainMachine",
                    "grain_machine_empty",
                    "Grain Machine",
                    2,
                    2,
                    5000.0,
                    47,
                    6,
                    9,
                    fullAssetName = "grain_machine_full",
                ),
                Building("barn1", "barn_1", "Sheep Barn", 4, 5, 6000.0, 51, 7, 10),
                Building(
                    "silo",
                    "silo_tower",
                    "Silo",
                    2,
                    5,
                    7000.0,
                    54,
                    7,
                    11,
                    styles =
                        listOf(
                            BuildingStyle("tower", "Tower", "silo_tower", 2, 5),
                            BuildingStyle("towerAndShed", "Tower and Shed", "silo_tower_shed", 3, 6),
                        ),
                ),
                house,
                Building("goatBarn", "barn_goat", "Goat Barn", 4, 5, 9500.0, 58, 8, 12),
                Building(
                    "tractor1",
                    "tractor_1_right",
                    "Tractor",
                    2,
                    1,
                    11000.0,
                    60,
                    8,
                    13,
                    styles =
                        listOf(
                            BuildingStyle("right", "Right", "tractor_1_right", 2, 1),
                            BuildingStyle("left", "Left", "tractor_1_left", 2, 1),
                            BuildingStyle("front", "Front", "tractor_1_front", 1, 2),
                            BuildingStyle("back", "Back", "tractor_1_back", 1, 2),
                        ),
                ),
                Building(
                    "stoneOven",
                    "stone_oven_empty",
                    "Stone Oven",
                    3,
                    2,
                    12500.0,
                    63,
                    9,
                    14,
                    fullAssetName = "stone_oven_full",
                ),
                Building("pigBarn", "barn_pig", "Pig Barn", 4, 5, 13000.0, 64, 9, 14),
                Building("stable1", "stable_1", "Stable", 6, 4, 14000.0, 66, 9, 15),
                Building(
                    "woodworkBench",
                    "woodwork_bench_empty",
                    "Woodwork Bench",
                    3,
                    2,
                    15000.0,
                    68,
                    10,
                    16,
                    fullAssetName = "woodwork_bench_full",
                ),
                Building(
                    "tailorTable",
                    "tailor_table_empty",
                    "Tailor Table",
                    2,
                    2,
                    20000.0,
                    74,
                    11,
                    18,
                    fullAssetName = "tailor_table_full",
                ),
                Building(
                    "diyTable",
                    "diy_table_empty",
                    "DIY Crafting Table",
                    2,
                    2,
                    22000.0,
                    76,
                    12,
                    19,
                    fullAssetName = "diy_table_full",
                ),
                Building(
                    "barn2",
                    "barn_2_red",
                    "Luxury Barn",
                    10,
                    5,
                    24000.0,
                    78,
                    12,
                    19,
                    styles =
                        listOf("red", "blue", "grey", "yellow").map {
                            BuildingStyle(it, it.replaceFirstChar(Char::uppercaseChar), "barn_2_$it", 10, 5)
                        },
                ),
            )

        val all: List<Building> =
            progression +
                listOf(
                    Building(
                        "dairy",
                        "dairy_empty",
                        "Dairy",
                        3,
                        2,
                        26_000.0,
                        80,
                        13,
                        20,
                        fullAssetName = "dairy_full",
                    ),
                    Building(
                        "cheeseCellar",
                        "cheese_cellar_empty",
                        "Cheese Cellar",
                        2,
                        2,
                        28_000.0,
                        82,
                        14,
                        21,
                        fullAssetName = "cheese_cellar_full",
                    ),
                )

        val starter: Building = all[0]

        private val byID = all.associateBy { it.id }

        fun named(id: String): Building? =
            when (id) {
                upgradedMarketStand.id -> upgradedMarketStand
                upgradedHouse.id -> upgradedHouse
                else -> byID[id]
            }

        private fun marketStyles(large: Boolean): List<BuildingStyle> =
            listOf("yellow", "blue", "green", "pink").map { color ->
                val size = if (large) "big" else "small"
                val asset = if (color == "yellow") "market_stand_$size" else "market_stand_${color}_$size"
                BuildingStyle(color, color.replaceFirstChar(Char::uppercaseChar), asset, if (large) 3 else 2, 2)
            }
    }
}

data class PlacedBuilding(
    val origin: GridPoint,
    val building: Building,
) {
    val footprint: List<GridPoint> get() = building.footprint(origin)
    val occupiedTiles: List<GridPoint> get() = building.occupiedTiles(origin)
}
