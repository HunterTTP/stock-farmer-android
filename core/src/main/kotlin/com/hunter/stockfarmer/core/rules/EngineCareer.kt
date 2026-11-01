package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.ValueSample

fun GameEngine.hasPlanted(crop: Crop): Boolean = crop.id in state.career.plantedCropIDs

/** The heaviest of a kind of fish landed, in pounds, or null if none has been; 0 for one known only from an older save. */
fun GameEngine.heaviestCatch(kind: FishKind): Double? = state.career.heaviestCatches[kind.id]

fun GameEngine.hasBuilt(building: Building): Boolean = building.id in state.career.builtBuildingIDs

/** Whether a home that holds animals has ever been upgraded to the level of an upgrade. */
fun GameEngine.hasReached(upgrade: HomeUpgrade): Boolean = upgrade.careerID in state.career.reachedHomeLevels

fun GameEngine.hasPlaced(decoration: Decoration): Boolean = decoration.id in state.career.placedDecorationIDs

/** Whether an animal variant or pet breed has been harvested from, sold or moved in. */
fun GameEngine.hasWorked(animalID: String): Boolean = animalID in state.career.workedAnimalIDs

/** Everything ever made from sales and winning stock trades, never reduced by what was spent, refunded or lost. */
val GameEngine.grossIncome: Double get() = state.career.grossIncomeCents / 100.0

/** What a Career chart plots against real time played. */
enum class CareerSeries { XP, INCOME, STOCK_PROFIT }

/** A series from the start of the farm to now, in XP or cents. */
fun GameEngine.history(series: CareerSeries): List<ValueSample> {
    val (recorded, current) =
        when (series) {
            CareerSeries.XP -> state.career.xpHistory.map { ValueSample(it.playTime, it.xp.toLong()) } to state.xp.toLong()
            CareerSeries.INCOME -> state.career.incomeHistory to state.career.grossIncomeCents
            CareerSeries.STOCK_PROFIT -> state.career.stockHistory to state.lifetimeStockProfitCents
        }
    val now = ValueSample(state.playTime, current)
    return listOf(ValueSample(0.0, 0)) +
        recorded.filter { it.playTime > 0 && it.playTime < now.playTime } +
        if (now.playTime > 0) listOf(now) else emptyList()
}

/** The most made in one farm day, counting the day under way, in dollars. */
val GameEngine.bestDayIncome: Double get() = state.career.bestDayIncomeCentsIncludingToday / 100.0

/** The most stock trades gained in one farm day, losses counted against gains and never below nothing, in dollars. */
val GameEngine.bestDayStockProfit: Double get() = state.career.bestDayStockCentsIncludingToday / 100.0

val GameEngine.cropsHarvested: Int get() = state.career.cropsHarvested

fun GameEngine.hasMined(size: RockSize): Boolean = size.rawValue in state.career.minedRockIDs

fun GameEngine.hasMade(recipe: Recipe): Boolean = recipe.id in state.career.madeRecipeIDs

/** Whether a season, butterfly, firefly or kind of weather has been seen; the season now showing always has. */
fun GameEngine.hasSeenEnvironment(id: String): Boolean =
    id in state.career.seenEnvironmentIDs || id == CareerRecord.environmentID(state.season)

fun GameEngine.noteSeenEnvironment(id: String) {
    state.career.seenEnvironmentIDs.add(id)
}

fun GameEngine.hasLaid(terrainID: String): Boolean = terrainID in state.career.placedTerrainIDs

internal fun GameEngine.noteTerrain(id: String) {
    state.career.placedTerrain.add(id)
}

internal fun GameEngine.noteMined(size: RockSize) {
    state.career.minedRockIDs.add(size.rawValue)
}

internal fun GameEngine.noteMade(recipe: Recipe) {
    state.career.madeRecipeIDs.add(recipe.id)
}

internal fun GameEngine.notePlanted(crop: Crop) {
    state.career.plantedCropIDs.add(crop.id)
}

internal fun GameEngine.noteBuilt(building: Building) {
    state.career.builtBuildingIDs.add(building.id)
}

internal fun GameEngine.noteReached(upgrade: HomeUpgrade) {
    state.career.reachedHomeLevels.add(upgrade.careerID)
}

internal fun GameEngine.notePlaced(decorationID: String) {
    state.career.placedDecorationIDs.add(decorationID)
}

internal fun GameEngine.noteWorked(animalID: String) {
    state.career.workedAnimalIDs.add(animalID)
}
