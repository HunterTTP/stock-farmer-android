package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant
import kotlin.math.min

/** The tiles the plant standing on a tile is kept on, which for a fruit tree are the two of its bottom row. */
fun GameEngine.cropsToDestroy(point: GridPoint): List<GridPoint> {
    val site = state.plant(point) ?: return emptyList()
    val crop = Crop.named(site.second.cropID)
    if (site.second.origin == null || crop == null) return listOf(point)
    return crop.plotTiles(site.first)
}

/** How many separate plants stand on some tiles. */
fun GameEngine.plantCount(points: List<GridPoint>): Int =
    points.mapNotNull { point -> state.plots[point]?.let { it.origin ?: point } }.toSet().size

fun GameEngine.destroyCrops(points: List<GridPoint>): List<GameEvent> {
    val doomed = points.filter { it in state.plots }
    val plants = doomed.map { state.plots[it]?.origin ?: it }.toSet().size
    doomed.forEach { state.plots.remove(it) }
    return if (doomed.isEmpty()) emptyList() else listOf(GameEvent.Destroyed(plants))
}

/** A fertilized tile with no crop on it, whose fertilizer can be destroyed. */
fun GameEngine.fertilizerToDestroy(point: GridPoint): GridPoint? =
    if (point in state.fertilized && point !in state.plots) point else null

/** Fertilizer is thrown away for no money back. */
fun GameEngine.destroyFertilizer(point: GridPoint): List<GameEvent> {
    if (fertilizerToDestroy(point) == null) return emptyList()
    state.fertilized.remove(point)
    return listOf(GameEvent.Destroyed(1))
}

/** Time until the crop on a tile can be harvested, or null when nothing is planted there. */
fun GameEngine.remainingGrowTime(
    point: GridPoint,
    now: Instant,
): Double? {
    val site = state.plant(point) ?: return null
    val crop = Crop.named(site.second.cropID) ?: return null
    return GrowthConditions(state).remainingTime(site.second, crop, site.first, now)
}

private val GameEngine.isFarmlandFullyPlanted: Boolean
    get() = state.farmland.isNotEmpty() && state.farmland.all { it in state.plots }

/** True when there is farmland, every farmland tile is planted, and every crop is fully grown. */
fun GameEngine.isWholeFieldGrown(now: Instant): Boolean {
    if (!isFarmlandFullyPlanted) return false
    val conditions = GrowthConditions(state)
    return state.plots.all { (point, plot) -> conditions.isReady(plot, point, now) }
}

/** When the last crop of a fully planted field finishes growing, or null if some farmland is empty. */
fun GameEngine.wholeFieldReadyDate(): Instant? {
    if (!isFarmlandFullyPlanted) return null
    val conditions = GrowthConditions(state)
    return state.plots
        .mapNotNull { (point, plot) ->
            Crop
                .named(
                    plot.cropID,
                )?.let { plot.plantedAt.advancedBy(it.growDuration / conditions.speedMultiplier(point)) }
        }.maxOrNull()
}

/**
 * The plant a tap on a tile is about: the crop on it, or the fruit tree over it, unless the tile is bare farmland under a
 * fruit tree's picture, where a tap plants.
 */
internal fun GameEngine.plantTapped(point: GridPoint): Pair<GridPoint, Plot>? {
    state.plots[point]?.let { return (it.origin ?: point) to it }
    return if (point in state.farmland) null else state.fruitTree(point)
}

/** Identifies a plant tap; harvesting still requires the matching implement. */
internal fun GameEngine.isCropTap(point: GridPoint): Boolean = plantTapped(point) != null

/** A direct tap on a crop: harvests it when finished and says how long is left otherwise. Taps on bare ground do nothing. */
fun GameEngine.tapCrop(
    point: GridPoint,
    now: Instant,
): List<GameEvent> = if (isCropTap(point)) tapFarm(point, now) else emptyList()

/** Plants on empty farmland or harvests a finished crop, one tile per tap. */
internal fun GameEngine.tapFarm(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val levelBefore = state.level
    val site = plantTapped(point)
    val result =
        if (site != null) {
            harvest(site.second, site.first, point, GrowthConditions(state), now)
        } else {
            plant(point, now).map { listOf(it) }
        }
    return when (result) {
        is Attempt.Done -> result.value + levelUpEvents(levelBefore)
        is Attempt.Refused -> listOf(GameEvent.Rejected(result.rejection))
    }
}

internal fun GameEngine.harvest(
    plot: Plot,
    anchor: GridPoint,
    point: GridPoint,
    conditions: GrowthConditions,
    now: Instant,
): Attempt<List<GameEvent>> {
    val crop = Crop.named(plot.cropID) ?: return Attempt.Refused(Rejection.NothingToHarvest)
    if (!conditions.isReady(plot, crop, anchor, now)) {
        return Attempt.Refused(notFinished(plot, crop, anchor, conditions, now))
    }
    if (!isHolding(crop.harvestTool)) {
        return Attempt.Refused(if (crop.isFruitTree) Rejection.UseTheBucketForFruit else Rejection.UseTheScythe)
    }
    if (!hasRoom(1, crop.harvestTool)) return Attempt.Refused(Rejection.StorageFull)
    val xp = takeCrop(crop, anchor, now)
    return Attempt.Done(gatherWithBonus(StorageItem.CropItem(crop), point, crop.harvestTool, xp))
}

/**
 * Takes a crop off its tiles and counts the harvest. Its XP is earned now, unless it is the Tractor's, which earns it when
 * the load is stored or sold.
 */
internal fun GameEngine.takeCrop(
    crop: Crop,
    anchor: GridPoint,
    now: Instant,
    earnsXP: Boolean = true,
): Int {
    for (tile in crop.plotTiles(anchor)) {
        if (crop.isFruitTree) state.plots[tile] = Plot(crop.id, now, anchor) else state.plots.remove(tile)
        state.fertilized.remove(tile)
    }
    val xp = if (earnsXP) earnXPForHarvesting(crop, 1) else 0
    state.career.cropsHarvested = min(CareerRecord.MAX_COUNT, state.career.cropsHarvested + 1L).toInt()
    recordQuestProgress(QuestGoal.Harvest(crop.id))
    return xp
}

/** Adds the XP of harvesting some of a crop, which is nothing at the last level, and says how much it was. */
internal fun GameEngine.earnXPForHarvesting(
    crop: Crop,
    quantity: Int,
): Int {
    val xp = if (state.level < LevelRules.maxLevel) LevelRules.harvestXP(crop) * quantity else 0
    state.xp += xp
    return xp
}

/** Adds the XP of taking an animal's produce, and says how much it was. */
internal fun GameEngine.earnXPForProducing(item: StorageItem): Int {
    val xp = LevelRules.produceXP(item, state.level)
    state.xp += xp
    return xp
}

/** Why a tile holding a crop can't be built or landscaped over: it is still growing, or ready to harvest. */
internal fun GameEngine.cropInTheWay(
    point: GridPoint,
    now: Instant,
): Rejection? {
    val plot = state.plots[point] ?: return null
    val crop = Crop.named(plot.cropID) ?: return null
    val conditions = GrowthConditions(state)
    if (conditions.isReady(plot, crop, point, now)) return Rejection.CropReady
    return notFinished(plot, crop, point, conditions, now)
}

internal fun GameEngine.notFinished(
    plot: Plot,
    crop: Crop,
    point: GridPoint,
    conditions: GrowthConditions,
    now: Instant,
): Rejection = Rejection.NotFinishedGrowing(crop.name, conditions.remainingTime(plot, crop, point, now))

internal fun GameEngine.levelUpEvents(levelBefore: Int): List<GameEvent> {
    val level = state.level
    if (level <= levelBefore) return emptyList()
    val newTiles = FarmlandRules.tilesUnlocked(level) - FarmlandRules.tilesUnlocked(levelBefore)
    return listOf(GameEvent.LeveledUp(level, newTiles, levelBefore))
}

private fun GameEngine.plant(
    point: GridPoint,
    now: Instant,
): Attempt<GameEvent> {
    val crop = selectedCrop
    if (point in state.plots) return Attempt.Refused(Rejection.AlreadyPlanted)
    if (point !in state.farmland) return Attempt.Refused(Rejection.NeedFarmland)
    if (state.isBlockedByDecoration(point)) return Attempt.Refused(Rejection.StructureHere)
    val cost = price(ShopItem.Seed(crop.id))
    spend(cost)?.let { return Attempt.Refused(it) }
    state.plots[point] = Plot(crop.id, now)
    notePlanted(crop)
    learnThrough(Lesson.plant)
    return Attempt.Done(GameEvent.Planted(point, cost))
}
