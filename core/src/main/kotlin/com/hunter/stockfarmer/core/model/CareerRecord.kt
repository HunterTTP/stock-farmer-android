package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** A number as it stood after some real time spent playing, for the Career charts. */
@Serializable
data class ValueSample(
    val playTime: Double,
    val value: Long,
)

/** How far the player had got in XP after some real time spent playing. */
@Serializable
data class XPSample(
    val playTime: Double,
    val xp: Int,
)

/** Adds a point when enough play time has passed since the last and the number has moved, thinning older points. */
private fun <T> List<T>.sampled(
    playTime: Double,
    value: Long,
    timeOf: (T) -> Double,
    valueOf: (T) -> Long,
    make: (Double, Long) -> T,
): List<T> {
    lastOrNull()?.let { last ->
        if (playTime - timeOf(last) < CareerRecord.SAMPLE_INTERVAL || value == valueOf(last)) return this
    }
    val grown = this + make(playTime, value)
    if (grown.size <= CareerRecord.MAX_SAMPLES) return grown
    val lastIndex = grown.size - 1
    return grown.filterIndexed { index, _ -> index % 2 == 0 || index == lastIndex }
}

/** What a farm has ever done, kept for the Career menu: firsts, records and lifetime totals the farm itself doesn't show. */
@Serializable
class CareerRecord {
    var plantedCropIDs: MutableSet<String> = mutableSetOf()

    /** The heaviest of each kind of fish landed, in pounds; 0 for one known to have been caught but not how heavy. */
    var heaviestCatches: MutableMap<String, Double> = mutableMapOf()
    var builtBuildingIDs: MutableSet<String> = mutableSetOf()

    /** The levels homes that hold animals have been upgraded to, by `HomeUpgrade.careerID`. */
    var reachedHomeLevels: MutableSet<String> = mutableSetOf()
    var placedDecorationIDs: MutableSet<String> = mutableSetOf()

    /** Livestock and chicken variants harvested from or sold, and pet breeds moved in. */
    var workedAnimalIDs: MutableSet<String> = mutableSetOf()

    /** Terrain tiles laid and trees planted, by [terrainID]. */
    var placedTerrain: MutableSet<String> = mutableSetOf()

    /** The rocks and ore veins broken, by `RockSize.rawValue`. */
    var minedRockIDs: MutableSet<String> = mutableSetOf()

    /** The recipes a workshop has finished making something from. */
    var madeRecipeIDs: MutableSet<String> = mutableSetOf()

    /** Seasons, butterflies, fireflies and weather that have been seen, by [environmentID]. */
    var seenEnvironmentIDs: MutableSet<String> = mutableSetOf()
    var cropsHarvested: Int = 0
    var grossIncomeCents: Long = 0

    /** Made and lost in the farm day now under way, and the best day before it: a stock day counts losses against gains. */
    var todayIncomeCents: Long = 0
    var bestDayIncomeCents: Long = 0
    var todayStockCents: Long = 0
    var bestDayStockCents: Long = 0
    var xpHistory: List<XPSample> = emptyList()
    var incomeHistory: List<ValueSample> = emptyList()
    var stockHistory: List<ValueSample> = emptyList()

    val placedTerrainIDs: Set<String> get() = placedTerrain
    val bestDayIncomeCentsIncludingToday: Long get() = max(bestDayIncomeCents, todayIncomeCents)
    val bestDayStockCentsIncludingToday: Long get() = max(bestDayStockCents, todayStockCents)

    fun addGrossIncome(dollars: Double) {
        if (dollars <= 0) return
        val cents = (dollars * 100).roundedHalfAwayFromZero().toLong()
        grossIncomeCents = min(MAX_GROSS_INCOME_CENTS, grossIncomeCents + cents)
        todayIncomeCents = min(MAX_GROSS_INCOME_CENTS, todayIncomeCents + cents)
    }

    fun addStockResult(cents: Long) {
        todayStockCents = min(MAX_GROSS_INCOME_CENTS, max(-MAX_GROSS_INCOME_CENTS, todayStockCents + cents))
    }

    /** Folds the farm day just over into the bests and starts the next one at nothing. */
    fun closeDay() {
        bestDayIncomeCents = bestDayIncomeCentsIncludingToday
        bestDayStockCents = bestDayStockCentsIncludingToday
        todayIncomeCents = 0
        todayStockCents = 0
    }

    fun noteCatch(
        fishID: String,
        weight: Double,
    ) {
        heaviestCatches[fishID] = max(heaviestCatches[fishID] ?: 0.0, weight)
    }

    /** Adds a point to each chart whose number has moved since enough play time ago. */
    fun sample(
        playTime: Double,
        xp: Int,
        stockProfitCents: Long,
    ) {
        xpHistory =
            xpHistory.sampled(playTime, xp.toLong(), XPSample::playTime, { it.xp.toLong() }) { time, value ->
                XPSample(time, value.toInt())
            }
        incomeHistory =
            incomeHistory.sampled(playTime, grossIncomeCents, ValueSample::playTime, ValueSample::value, ::ValueSample)
        stockHistory =
            stockHistory.sampled(playTime, stockProfitCents, ValueSample::playTime, ValueSample::value, ::ValueSample)
    }

    /** An independent copy, so a preview that changes it leaves this one alone. */
    fun copy(): CareerRecord =
        CareerRecord().also {
            it.plantedCropIDs = plantedCropIDs.toMutableSet()
            it.heaviestCatches = heaviestCatches.toMutableMap()
            it.builtBuildingIDs = builtBuildingIDs.toMutableSet()
            it.reachedHomeLevels = reachedHomeLevels.toMutableSet()
            it.placedDecorationIDs = placedDecorationIDs.toMutableSet()
            it.workedAnimalIDs = workedAnimalIDs.toMutableSet()
            it.placedTerrain = placedTerrain.toMutableSet()
            it.minedRockIDs = minedRockIDs.toMutableSet()
            it.madeRecipeIDs = madeRecipeIDs.toMutableSet()
            it.seenEnvironmentIDs = seenEnvironmentIDs.toMutableSet()
            it.cropsHarvested = cropsHarvested
            it.grossIncomeCents = grossIncomeCents
            it.todayIncomeCents = todayIncomeCents
            it.bestDayIncomeCents = bestDayIncomeCents
            it.todayStockCents = todayStockCents
            it.bestDayStockCents = bestDayStockCents
            it.xpHistory = xpHistory
            it.incomeHistory = incomeHistory
            it.stockHistory = stockHistory
        }

    companion object {
        /** How much play time must pass before the next point on a chart. */
        const val SAMPLE_INTERVAL = 5.0 * 60
        const val MAX_SAMPLES = 300
        const val MAX_GROSS_INCOME_CENTS = 100_000_000_000_000L
        const val MAX_COUNT = 1_000_000_000_000L

        /** The key a terrain tile or a tree of a color and size is kept under. */
        fun terrainID(water: WaterStyle): String = "water-${water.rawValue}"

        fun terrainID(
            tree: Tree,
            color: TreeColor,
        ): String = "tree:${tree.id}:${color.id}"

        fun terrainID(
            tool: LandscapeTool,
            waterStyle: WaterStyle,
        ): String? =
            when (tool) {
                is LandscapeTool.groundCover -> tool.cover.rawValue
                LandscapeTool.farmland -> "farmland"
                LandscapeTool.grass -> "grass"
                LandscapeTool.water -> terrainID(waterStyle)
                LandscapeTool.tree -> null
            }

        /** The key a sight of the farm's surroundings is kept under. */
        fun environmentID(season: Season): String = "season:${season.name}"

        fun environmentID(wings: Butterflies.Wings): String =
            when (wings) {
                is Butterflies.Wings.Color -> "butterfly:${wings.rarity.rawValue}"
                Butterflies.Wings.Shifting -> "butterfly:shifting"
            }

        const val FIREFLY_ID = "firefly"
        const val RAIN_ID = "rain"
        const val SNOW_ID = "snow"
    }
}
