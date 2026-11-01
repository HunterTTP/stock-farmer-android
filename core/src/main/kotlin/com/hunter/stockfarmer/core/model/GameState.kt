package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.catalog.Workshop
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant

@Serializable
data class Plot(
    val cropID: String,
    @Serializable(with = InstantSerializer::class) val plantedAt: Instant,
    /** For a tile of a plant that covers several, the top left tile of the whole plant; null for a plant on one tile. */
    val origin: GridPoint? = null,
    /** How many of the tile's meals animals have eaten, from 0 up to but not including [SERVINGS_PER_TILE]. */
    val servingsEaten: Int = 0,
) {
    /**
     * Whether this is the tile a plant is counted and drawn from: its own tile for a crop, and for a fruit tree, which
     * is kept on its bottom row, the one on the left.
     */
    fun isAnchor(tile: GridPoint): Boolean {
        val origin = origin ?: return true
        return tile == GridPoint(origin.row + side - 1, origin.col)
    }

    /** How many tiles square the plant is. */
    val side: Int get() = Crop.named(cropID)?.side ?: 1

    companion object {
        /** How many meals one tile of a crop feeds, each taking one of its four pictures. */
        const val SERVINGS_PER_TILE = 4
    }
}

/**
 * Everything about one farm. It is a mutable object that the [com.hunter.stockfarmer.core.rules.GameEngine] owns and
 * changes; [copy] gives an independent value, so an action can be previewed on a copy without touching the real farm.
 */
@Serializable
class GameState {
    var money: Double = 0.0
    var mode: PlayMode = PlayMode.plant
    var farmland: MutableSet<GridPoint> = startingFarmland.toMutableSet()
    var plots: MutableMap<GridPoint, Plot> = mutableMapOf()

    /** What has been collected into storage: how many of each item, by item id. */
    var storage: MutableMap<String, Int> = mutableMapOf()

    /** What the Harvest tool is holding. */
    var harvestTool: HarvestTool = HarvestTool.scythe

    /** The implements set to sell what they gather at once instead of storing it. */
    var sellingTools: MutableSet<HarvestTool> = mutableSetOf()

    /** Farmland tiles with fertilizer on them, used up when the crop on the tile is harvested or destroyed. */
    var fertilized: MutableSet<GridPoint> = mutableSetOf()
    var water: MutableMap<GridPoint, WaterStyle> = mutableMapOf()
    var tractorRun: TractorRun? = null
    var tractorCargo: MutableMap<GridPoint, Map<String, Int>> = mutableMapOf()

    /** The level of each home that holds animals that has been upgraded; a home that isn't here is at Level 1. */
    var homeLevels: MutableMap<GridPoint, Int> = mutableMapOf()

    @SerialName("buildings")
    private var buildingIDs: MutableMap<GridPoint, String> = mutableMapOf()

    /** The style of each building that isn't in its first style, such as a blue barn, keyed by the building's origin. */
    @SerialName("buildingStyles")
    private var buildingStyleIDs: MutableMap<GridPoint, String> = mutableMapOf()

    /** The style last picked for each kind of building, by building id, which the next one placed has. */
    var selectedBuildingStyleIDs: MutableMap<String, String> = mutableMapOf()

    /** The pet living in each pet house, keyed by the house's origin. */
    var pets: MutableMap<GridPoint, Pet> = mutableMapOf()

    /** The chickens at each chicken coop, keyed by the coop's origin. */
    var coops: MutableMap<GridPoint, Coop> = mutableMapOf()

    /** The batch each workshop is working on, keyed by the workshop's origin. */
    var workshops: MutableMap<GridPoint, Workshop> = mutableMapOf()
    var selectedWorkshopRecipes: MutableMap<GridPoint, String> = mutableMapOf()

    /** The animals at each building that holds livestock, keyed by the building's origin. */
    var herds: MutableMap<GridPoint, Herd> = mutableMapOf()

    /** Trees on the map, keyed by the top left tile of the ground each stands on. */
    @SerialName("trees")
    private var placedTrees: MutableMap<GridPoint, PlacedTree> = mutableMapOf()

    /** Rocks on the map, each standing on one tile. */
    var rocks: MutableMap<GridPoint, RockSize> = mutableMapOf()

    /** Whether the map has been given its rocks, which a new game is given when it is first opened. */
    var hasRocks: Boolean = false
    var hasOreVeins: Boolean = false
    var selectedTreeID: String = Tree.starter.id
    var selectedTreeColorID: String = TreeColor.green.id

    /** The lessons of the first-time walk through that have been done. */
    var lessonsLearned: MutableSet<Lesson> = mutableSetOf()

    /** The keys of the unlocks the player has looked at, which stop showing a red dot; null until the game first counts them. */
    var seenUnlocks: MutableSet<String>? = null

    /** When the game was last saved, from which the time it spent closed is worked out. */
    @Serializable(with = InstantSerializer::class)
    var lastSavedAt: Instant? = null

    /** Whether the hint on how to land the first fish has been shown. */
    var hasSeenFishingHint: Boolean = false

    /** Real seconds spent playing this game, with the app open and in front. */
    var playTime: Double = 0.0
    var timelapse: TimelapseSettings = TimelapseSettings()

    /** Eggs hens have laid that nobody has collected yet. */
    var eggs: List<Egg> = emptyList()

    /** The places fish appear, as many as the water's shape allows, each keyed by the water tile its fish appeared on. */
    var fishPonds: MutableMap<GridPoint, FishPond> = mutableMapOf()

    /** Decorations on the map other than fences, keyed by the top left tile of the ground each stands on. */
    @SerialName("decorations")
    private var placedDecorations: MutableMap<GridPoint, PlacedDecoration> = mutableMapOf()

    /** The decoration the Decorate tool is placing, or null while it places fences. */
    var selectedDecorationID: String? = null

    /** The size last picked for each decoration that comes in sizes, by decoration id. */
    var selectedDecorationSizes: MutableMap<String, Int> = mutableMapOf()

    /** Fences on the map, each with the id of its `FenceColor`. */
    var fences: MutableMap<GridPoint, String> = mutableMapOf()

    /** The stocks the player owns, by ticker. */
    var holdings: MutableMap<String, Holding> = mutableMapOf()

    /** Every price pulled for each ticker still owned, oldest first, for charting. */
    var priceHistory: MutableMap<String, List<PricePoint>> = mutableMapOf()

    /** Every stock bought or sold on this farm, oldest first, up to `StockMarketRules.MAX_TRADES_KEPT`. */
    var stockTrades: List<StockTrade> = emptyList()

    /** Profit or loss from every stock sale ever made on this farm, in cents. */
    var lifetimeStockProfitCents: Long = 0

    /** Cents of stock profit not yet counted towards a whole dollar of a quest. */
    var stockProfitRemainderCents: Long = 0

    /** When the prices of the stocks owned were last pulled, which spaces the next pull. */
    @Serializable(with = InstantSerializer::class)
    var lastStockPullAt: Instant? = null
    var xp: Int = 0
    var selectedCropID: String = Crop.starter.id
    var selectedLandscape: LandscapeTool = LandscapeTool.farmland
    var groundCover: MutableMap<GridPoint, GroundCover> = mutableMapOf()

    /** The last terrain choice, which the Terrain tool returns to. */
    var selectedTerrain: LandscapeTool = LandscapeTool.farmland

    /** How far the farm's clock has been set from the real one, in seconds, which only the day and night follow. */
    var clockOffset: Double = 0.0

    /** Whether the Fertilize tool last held sprinklers rather than fertilizer. */
    var fertilizesWithSprinklers: Boolean = false
    var lastSprinklerID: String = Decoration.SPRINKLER_ID

    /** Whether sprinklers spray all the time, as fountains, instead of together once a minute. */
    var sprinklersRunConstantly: Boolean = false

    /** Whether the Decorate tool is setting down decorations from storage, using up one each, rather than buying them. */
    var placesDecorationsFromStorage: Boolean = false

    /** Whether the Fertilize tool is using fertilizer from storage, one a tile, rather than paying for it. */
    var placesFertilizerFromStorage: Boolean = false

    /** The kind of fish the Decorate tool is holding to put into water, if it is holding one. */
    var heldItemID: String? = null

    /** The butterflies the player let go, which stay on the farm until caught again. */
    var releasedButterflies: List<ReleasedButterfly> = emptyList()

    /** The last decoration other than a sprinkler the Decorate tool held, or null if it last held fences. */
    var lastDecorationID: String? = null
    var selectedWaterStyle: WaterStyle = WaterStyle.deep
    var selectedBuildingID: String = Building.starter.id
    var selectedFenceColorID: String = FenceColor.starter.id

    /** The season the farm is dressed for, which only changes how it looks. */
    var season: Season = Season.spring

    /** Which day of the farm this is, counted up at each midnight; a game begins on day 1. */
    var day: Int = 1

    /** The night, counted in days of the farm's clock, whose midnight last began a day, so no night begins one twice. */
    var lastNewDayNight: Int? = null

    /** What has sold since the day began, by item, for the summary of the day shown at midnight. */
    var salesToday: MutableMap<String, ItemSale> = mutableMapOf()

    /** Farmland a sprinkler that was moved or destroyed has not yet dried out, with the moment each tile stops counting. */
    var lingeringWater: MutableMap<
        GridPoint,
        @Serializable(with = InstantSerializer::class)
        Instant,
    > = mutableMapOf()
    var marketQuests: MutableMap<String, List<MarketQuest>> = mutableMapOf()
    var completedMarketQuests: Int = 0

    /** How far each Harvest implement has been upgraded, by its raw value; one not listed is at level 1. */
    var toolLevels: MutableMap<String, Int> = mutableMapOf()
    var career: CareerRecord = CareerRecord()

    // Derived from the maps above whenever they change, so asking what stands on a tile is a lookup, not a scan.
    @Transient private var placedBuildingList: List<PlacedBuilding> = emptyList()

    @Transient private var placedBuildingIndexByTile: Map<GridPoint, Int> = emptyMap()

    @Transient private var buildingIndexByBottomTile: Map<GridPoint, Int> = emptyMap()

    @Transient private var treeOriginByTile: Map<GridPoint, GridPoint> = emptyMap()

    @Transient private var treeOriginByBottomTile: Map<GridPoint, GridPoint> = emptyMap()

    @Transient private var decorationOriginByTile: Map<GridPoint, GridPoint> = emptyMap()

    @Transient private var decorationOriginByBottomTile: Map<GridPoint, GridPoint> = emptyMap()

    /** The tile in front of every pet house and chicken coop, where its animals come out. */
    @Transient
    var homeFrontTiles: Set<GridPoint> = emptySet()
        private set

    @Transient
    val waterLayoutCache =
        com.hunter.stockfarmer.core.model
            .WaterLayoutCache()

    /** The bodies of water and their interior tiles, worked out again only after the water changes. */
    val waterLayout: WaterLayout get() = waterLayoutCache.layout(water)

    val buildings: Map<GridPoint, String> get() = buildingIDs
    val buildingStyles: Map<GridPoint, String> get() = buildingStyleIDs
    val trees: Map<GridPoint, PlacedTree> get() = placedTrees
    val decorations: Map<GridPoint, PlacedDecoration> get() = placedDecorations

    val level: Int get() = LevelRules.levelForXP(xp)

    /** Tiles that are plain grass: not farmland, water or covered by a building. */
    val grassTileCount: Int
        get() {
            val buildingTiles = placedBuildings.sumOf { it.building.width * it.building.height }
            return GRID_SIZE * GRID_SIZE - farmland.size - water.size - buildingTiles
        }

    val placedBuildings: List<PlacedBuilding> get() = placedBuildingList

    init {
        reindex()
    }

    fun putBuilding(
        origin: GridPoint,
        id: String,
        styleID: String? = null,
    ) {
        buildingIDs[origin] = id
        setStyle(origin, styleID)
        indexBuildings()
    }

    fun removeBuilding(origin: GridPoint): String? {
        val removed = buildingIDs.remove(origin)
        buildingStyleIDs.remove(origin)
        indexBuildings()
        return removed
    }

    private fun setStyle(
        origin: GridPoint,
        styleID: String?,
    ) {
        if (styleID == null) buildingStyleIDs.remove(origin) else buildingStyleIDs[origin] = styleID
    }

    fun putTree(
        origin: GridPoint,
        placed: PlacedTree,
    ) {
        placedTrees[origin] = placed
        indexTrees()
    }

    fun removeTree(origin: GridPoint): PlacedTree? = placedTrees.remove(origin).also { indexTrees() }

    fun replaceTrees(trees: Map<GridPoint, PlacedTree>) {
        placedTrees = trees.toMutableMap()
        indexTrees()
    }

    fun putDecoration(
        origin: GridPoint,
        placed: PlacedDecoration,
    ) {
        placedDecorations[origin] = placed
        indexDecorations()
    }

    fun removeDecoration(origin: GridPoint): PlacedDecoration? =
        placedDecorations.remove(origin).also { indexDecorations() }

    fun building(covering: GridPoint): PlacedBuilding? =
        placedBuildingIndexByTile[covering]?.let { placedBuildingList[it] }

    /** The building occupying a tile: its bottom row, or the whole footprint of a Tractor. */
    fun buildingBlocking(point: GridPoint): PlacedBuilding? =
        buildingIndexByBottomTile[point]?.let { placedBuildingList[it] }

    /**
     * Whether a tree, a rock or a decoration takes up a tile, which nothing else can be put on: a rock, or the bottom row
     * of a tree or a decoration, the rest of their ground being room their pictures rise into. The trees of the top row
     * are only a backdrop: things go behind them.
     */
    fun hasObstacle(point: GridPoint): Boolean = hasSolidObstacle(point) || point in decorationOriginByBottomTile

    /** Whether the driving Tractor, a rock or a tree's bottom row takes up a tile: what no terrain can be put beneath. */
    internal fun hasSolidObstacle(point: GridPoint): Boolean =
        point in tractorOccupiedTiles || point in rocks ||
            (point.row != BACKDROP_ROW && point in treeOriginByBottomTile)

    /** The decoration whose bottom row takes up a tile. */
    internal fun decorationStanding(point: GridPoint): PlacedDecoration? =
        decorationOriginByBottomTile[point]?.let { placedDecorations[it] }

    /** The decoration whose ground includes a tile, with the top left of that ground; the one in front if two overlap. */
    fun decoration(covering: GridPoint): Pair<GridPoint, PlacedDecoration>? {
        val origin = decorationOriginByTile[covering] ?: return null
        val placed = placedDecorations[origin] ?: return null
        return origin to placed
    }

    /** The tree whose ground includes a tile, with the top left of that ground; the one in front if two overlap there. */
    fun tree(covering: GridPoint): Pair<GridPoint, PlacedTree>? {
        val origin = treeOriginByTile[covering] ?: return null
        val placed = placedTrees[origin] ?: return null
        return origin to placed
    }

    /** Whether the bottom row of a decoration is on a tile, which nothing else can grow or be planted on. */
    fun isBlockedByDecoration(point: GridPoint): Boolean = point in decorationOriginByBottomTile

    /** The fruit tree whose ground includes a tile, with the top left of that ground and its plot, kept on its bottom row. */
    fun fruitTree(covering: GridPoint): Pair<GridPoint, Plot>? {
        val side = Crop.FRUIT_TREE_SIDE
        for (rowShift in 0 until side) {
            for (colShift in 0 until side) {
                val origin = GridPoint(covering.row - rowShift, covering.col - colShift)
                val anchor = GridPoint(origin.row + side - 1, origin.col)
                val plot = plots[anchor]
                if (plot != null && plot.origin == origin) return origin to plot
            }
        }
        return null
    }

    /** The plant on a tile: the crop planted there, or the fruit tree whose ground includes it. A crop wins where both are. */
    fun plant(covering: GridPoint): Pair<GridPoint, Plot>? {
        plots[covering]?.let { return (it.origin ?: covering) to it }
        return fruitTree(covering)
    }

    /** Rebuilds what is worked out from the maps above; call it after loading a saved game. */
    fun reindex() {
        indexBuildings()
        indexTrees()
        indexDecorations()
    }

    fun indexBuildings() {
        // Sorted, so two games with the same buildings are equal whatever order the map happens to give.
        val list =
            buildingIDs
                .mapNotNull { (origin, id) ->
                    Building.named(id)?.let { PlacedBuilding(origin, it.styled(buildingStyleIDs[origin])) }
                }.sortedWith(compareBy({ it.origin.row }, { it.origin.col }))
        placedBuildingList = list
        val byTile = mutableMapOf<GridPoint, Int>()
        val byBottomTile = mutableMapOf<GridPoint, Int>()
        // The one lowest on the map goes last, so it is the one found where pictures overlap.
        for ((index, placed) in list.withIndex().sortedBy { (_, placed) ->
            placed.origin.row + placed.building.height
        }) {
            for (tile in placed.footprint) byTile[tile] = index
            for (tile in placed.occupiedTiles) byBottomTile[tile] = index
        }
        placedBuildingIndexByTile = byTile
        buildingIndexByBottomTile = byBottomTile
        homeFrontTiles = list.filter { it.building.needsFrontTile }.map { it.building.frontTile(it.origin) }.toSet()
    }

    private fun indexDecorations() {
        val byTile = mutableMapOf<GridPoint, GridPoint>()
        val byBottomTile = mutableMapOf<GridPoint, GridPoint>()
        for ((origin, placed) in placedDecorations.entries.sortedBy { it.key.row + it.value.decoration.height }) {
            val ground = placed.decoration.footprint(origin)
            for (tile in ground) byTile[tile] = origin
            for (tile in ground.bottomRow) byBottomTile[tile] = origin
        }
        decorationOriginByTile = byTile
        decorationOriginByBottomTile = byBottomTile
    }

    private fun indexTrees() {
        val byTile = mutableMapOf<GridPoint, GridPoint>()
        val byBottomTile = mutableMapOf<GridPoint, GridPoint>()
        for ((origin, placed) in placedTrees.entries.sortedBy { it.key.row + it.value.tree.side }) {
            val ground = placed.tree.footprint(origin)
            for (tile in ground) byTile[tile] = origin
            for (tile in ground.bottomRow) byBottomTile[tile] = origin
        }
        treeOriginByTile = byTile
        treeOriginByBottomTile = byBottomTile
    }

    /** An independent copy, so a preview that changes it leaves this farm alone. */
    fun copy(): GameState =
        GameState().also { copy ->
            copy.money = money
            copy.mode = mode
            copy.farmland = farmland.toMutableSet()
            copy.plots = plots.toMutableMap()
            copy.storage = storage.toMutableMap()
            copy.harvestTool = harvestTool
            copy.sellingTools = sellingTools.toMutableSet()
            copy.fertilized = fertilized.toMutableSet()
            copy.water = water.toMutableMap()
            copy.tractorRun = tractorRun
            copy.tractorCargo = tractorCargo.toMutableMap()
            copy.homeLevels = homeLevels.toMutableMap()
            copy.buildingIDs = buildingIDs.toMutableMap()
            copy.buildingStyleIDs = buildingStyleIDs.toMutableMap()
            copy.selectedBuildingStyleIDs = selectedBuildingStyleIDs.toMutableMap()
            copy.pets = pets.toMutableMap()
            copy.coops = coops.toMutableMap()
            copy.workshops = workshops.toMutableMap()
            copy.selectedWorkshopRecipes = selectedWorkshopRecipes.toMutableMap()
            copy.herds = herds.toMutableMap()
            copy.placedTrees = placedTrees.toMutableMap()
            copy.rocks = rocks.toMutableMap()
            copy.hasRocks = hasRocks
            copy.hasOreVeins = hasOreVeins
            copy.selectedTreeID = selectedTreeID
            copy.selectedTreeColorID = selectedTreeColorID
            copy.lessonsLearned = lessonsLearned.toMutableSet()
            copy.seenUnlocks = seenUnlocks?.toMutableSet()
            copy.lastSavedAt = lastSavedAt
            copy.hasSeenFishingHint = hasSeenFishingHint
            copy.playTime = playTime
            copy.timelapse = timelapse
            copy.eggs = eggs
            copy.fishPonds = fishPonds.toMutableMap()
            copy.placedDecorations = placedDecorations.toMutableMap()
            copy.selectedDecorationID = selectedDecorationID
            copy.selectedDecorationSizes = selectedDecorationSizes.toMutableMap()
            copy.fences = fences.toMutableMap()
            copy.holdings = holdings.toMutableMap()
            copy.priceHistory = priceHistory.toMutableMap()
            copy.stockTrades = stockTrades
            copy.lifetimeStockProfitCents = lifetimeStockProfitCents
            copy.stockProfitRemainderCents = stockProfitRemainderCents
            copy.lastStockPullAt = lastStockPullAt
            copy.xp = xp
            copy.selectedCropID = selectedCropID
            copy.selectedLandscape = selectedLandscape
            copy.groundCover = groundCover.toMutableMap()
            copy.selectedTerrain = selectedTerrain
            copy.clockOffset = clockOffset
            copy.fertilizesWithSprinklers = fertilizesWithSprinklers
            copy.lastSprinklerID = lastSprinklerID
            copy.sprinklersRunConstantly = sprinklersRunConstantly
            copy.placesDecorationsFromStorage = placesDecorationsFromStorage
            copy.placesFertilizerFromStorage = placesFertilizerFromStorage
            copy.heldItemID = heldItemID
            copy.releasedButterflies = releasedButterflies
            copy.lastDecorationID = lastDecorationID
            copy.selectedWaterStyle = selectedWaterStyle
            copy.selectedBuildingID = selectedBuildingID
            copy.selectedFenceColorID = selectedFenceColorID
            copy.season = season
            copy.day = day
            copy.lastNewDayNight = lastNewDayNight
            copy.salesToday = salesToday.toMutableMap()
            copy.lingeringWater = lingeringWater.toMutableMap()
            copy.marketQuests = marketQuests.toMutableMap()
            copy.completedMarketQuests = completedMarketQuests
            copy.toolLevels = toolLevels.toMutableMap()
            copy.career = career.copy()
            copy.reindex()
        }

    companion object {
        const val GRID_SIZE = 50
        const val MAX_MONEY = 999_999_999_999.99
        const val BACKDROP_ROW = 0

        val startingFarmland: Set<GridPoint> by lazy {
            val center = GRID_SIZE / 2
            (center - 1..center).flatMap { row -> (center - 1..center).map { GridPoint(row, it) } }.toSet()
        }

        fun contains(point: GridPoint): Boolean = point.row in 0 until GRID_SIZE && point.col in 0 until GRID_SIZE
    }
}
