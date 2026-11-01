@file:Suppress("ktlint:standard:max-line-length")

package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.StockMarketRules
import java.time.Instant
import kotlin.math.abs

/** A saved game that is damaged, and so is not loaded. */
class InvalidSaveException(
    field: String,
) : Exception("Invalid saved $field")

private const val MOST_COUNT = 1_000_000_000

private fun count(value: Int) = value in 0..MOST_COUNT

private fun date(value: Instant?): Boolean = value == null || abs(value.epochSecond) < 1_000_000_000_000L

/** Tall pictures may extend above the map; their origins must still be bounded before footprint arithmetic. */
private fun origin(point: GridPoint) =
    point.row in -GameState.GRID_SIZE until GameState.GRID_SIZE && point.col in 0 until GameState.GRID_SIZE

private fun priceCents(value: Long) = value in 0..StockMarketRules.MAX_PRICE_CENTS

/** Reject corruption before indexing or running rules. Missing historical fields are handled by decoding. */
fun GameState.validate() {
    fun require(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw InvalidSaveException(field)
    }
    require(money.isFinite() && money in 0.0..GameState.MAX_MONEY, "money")
    require(
        count(
            xp,
        ) && day > 0 && clockOffset.isFinite() && abs(clockOffset) < 1_000_000_000_000.0 && playTime.isFinite() &&
            playTime >= 0,
        "clock or progression",
    )
    require(
        timelapse.captureMinute in 0 until 1440 && (timelapse.lastCapturedDay?.let { it > 0 } ?: true),
        "timelapse",
    )
    require(count(completedMarketQuests), "completed quests")
    for ((id, quests) in marketQuests) {
        // Upgrading retains outstanding quests from both tiers until claimed.
        require(
            QuestRules.capacity(id) > 0 && (id == "marketStandLarge" || quests.size <= QuestRules.capacity(id)),
            "quest board",
        )
        require(quests.map { it.id }.toSet().size == quests.size, "quest identifiers")
        require(
            quests.all { quest ->
                quest.rewardXP in 1..QuestRules.maximumRewardXP &&
                    (quest.rewardLevel?.let { it in 1..LevelRules.maxLevel } ?: true) &&
                    quest.objectives.size in 1..2 &&
                    quest.objectives.all {
                        it.goal.isValid && it.target in 1..it.goal.maxTarget && it.progress in 0..it.target
                    }
            },
            "quests",
        )
    }
    require(storage.values.all(::count), "storage")
    validateCareer()
    require(Decoration.named(lastSprinklerID)?.isSprinkler == true, "sprinkler selection")
    validateStocks()
    validateLand()
    validateHomes()
    validateTractor()
    require(trees.keys.all(::origin) && trees.values.all { Tree.named(it.treeID) != null && date(it.grewAt) }, "trees")
    require(
        decorations.keys.all(::origin) &&
            decorations.values.all {
                date(it.placedAt) && date(it.wateringStartsAt) &&
                    it.wateredTiles.all(GameState::contains)
            },
        "decorations",
    )
    require(rocks.keys.all(GameState::contains) && fences.keys.all(GameState::contains), "obstacles")
    require(fences.values.all { FenceColor.named(it) != null }, "fences")
    require(
        lingeringWater.keys.all(GameState::contains) && lingeringWater.values.all(::date) && date(lastSavedAt),
        "watering or save time",
    )
    require(
        pets.keys.all(::origin) &&
            pets.values.all {
                GameState.contains(it.position) && date(it.meal?.eatenAt) &&
                    (if (it.species == PetSpecies.cat) Cat.named(it.breed) != null else Dog.named(it.breed) != null)
            },
        "pets",
    )
    require(eggs.all { GameState.contains(it.point) && it.offsetX.isFinite() && it.offsetY.isFinite() }, "eggs")
    require(salesToday.values.all { count(it.quantity) && it.earnings.isFinite() && it.earnings >= 0 }, "sales")
}

private fun GameState.validateCareer() {
    val c = career
    val limit = CareerRecord.MAX_GROSS_INCOME_CENTS
    val valid =
        c.grossIncomeCents in 0..limit &&
            c.heaviestCatches.values.all { it.isFinite() && it in 0.0..100_000.0 } &&
            c.placedTerrainIDs.size <= 1_000 && c.minedRockIDs.size <= 100 && c.madeRecipeIDs.size <= 1_000 &&
            c.seenEnvironmentIDs.size <= 1_000 &&
            c.cropsHarvested.toLong() in 0..CareerRecord.MAX_COUNT &&
            listOf(c.todayIncomeCents, c.bestDayIncomeCents).all { it in 0..limit } &&
            listOf(c.todayStockCents, c.bestDayStockCents).all { abs(it) <= limit } &&
            c.incomeHistory.size <= CareerRecord.MAX_SAMPLES + 1 &&
            c.stockHistory.size <= CareerRecord.MAX_SAMPLES + 1 &&
            (c.incomeHistory + c.stockHistory).all {
                it.playTime.isFinite() && it.playTime >= 0 && abs(it.value) <= limit
            } &&
            c.xpHistory.size <= CareerRecord.MAX_SAMPLES + 1 &&
            c.xpHistory.all { it.playTime.isFinite() && it.playTime >= 0 && count(it.xp) }
    if (!valid) throw InvalidSaveException("career")
}

private fun GameState.validateStocks() {
    fun require(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw InvalidSaveException(field)
    }
    require(
        holdings.size <= StockMarketRules.UPGRADED_SLOT_LIMIT &&
            holdings.all { (ticker, holding) ->
                StockMarketRules.normalizedTicker(ticker) == ticker && holding.ticker == ticker &&
                    holding.shares in 1..StockMarketRules.MAX_SHARES &&
                    priceCents(
                        holding.averageCostCents,
                    ) && priceCents(holding.lastPriceCents) && date(holding.quotedAt) &&
                    (holding.previousCloseCents?.let(::priceCents) ?: true)
            },
        "holdings",
    )
    require(
        stockTrades.size <= StockMarketRules.MAX_TRADES_KEPT &&
            abs(lifetimeStockProfitCents) <= StockMarketRules.MAX_PROFIT_CENTS &&
            stockProfitRemainderCents in 0 until 100 &&
            stockTrades.all {
                StockMarketRules.normalizedTicker(
                    it.ticker,
                ) == it.ticker && it.shares in 1..StockMarketRules.MAX_SHARES &&
                    priceCents(it.priceCents) && abs(it.profitCents) <= StockMarketRules.MAX_PROFIT_CENTS && date(it.at)
            },
        "stock trades",
    )
    require(
        date(lastStockPullAt) && priceHistory.size <= StockMarketRules.UPGRADED_SLOT_LIMIT &&
            priceHistory.all { (ticker, points) ->
                ticker in holdings && points.size <= StockMarketRules.MAX_HISTORY_POINTS &&
                    points.all { date(it.at) && priceCents(it.priceCents) }
            },
        "price history",
    )
}

private fun GameState.validateLand() {
    fun require(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw InvalidSaveException(field)
    }
    require(
        farmland.all(GameState::contains) && fertilized.all(GameState::contains) && water.keys.all(GameState::contains),
        "land",
    )
    require(groundCover.keys.all(GameState::contains) && groundCover.keys.all { it !in water }, "ground cover")
    require(
        plots.keys.all(GameState::contains) &&
            plots.values.all { plot: Plot ->
                Crop.named(plot.cropID) != null && (plot.origin?.let(::origin) ?: true) && date(plot.plantedAt) &&
                    plot.servingsEaten in 0 until Plot.SERVINGS_PER_TILE
            },
        "plots",
    )
}

private fun GameState.validateHomes() {
    fun require(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw InvalidSaveException(field)
    }
    require(buildings.keys.all(::origin) && buildings.values.all { Building.named(it) != null }, "buildings")
    require(
        homeLevels.all { (home, level) ->
            (buildings[home] ?: "") in HomeUpgrade.buildingIDs &&
                level in 2..HomeUpgrade.maxLevel
        },
        "homeLevels",
    )
    for (coop in coops.values) {
        require(
            coop.nextVariantID?.let { id ->
                ChickenVariant.available.any { it.id == id }
            } ?: true,
            "chick appearance",
        )
        require(count(coop.nextChickenID) && date(coop.nextChickAt), "coop")
        require(
            coop.chickens
                .map { it.id }
                .toSet()
                .size == coop.chickens.size &&
                coop.chickens.all {
                    count(it.id) && GameState.contains(it.position) && ChickenVariant.named(it.variant) != null &&
                        date(it.hatchedAt) && date(it.meal?.eatenAt)
                },
            "chickens",
        )
    }
    for (herd in herds.values) {
        require(count(herd.nextAnimalID) && date(herd.nextBirthAt) && date(herd.processedAt), "herd")
        require(
            herd.animals
                .map { it.id }
                .toSet()
                .size == herd.animals.size &&
                herd.animals.all {
                    count(it.id) && GameState.contains(it.position) && LivestockSpecies.variant(it.variant) != null &&
                        date(it.bornAt) && date(it.readyAt) && date(it.lastWorkedAt) && date(it.meal?.eatenAt) &&
                        it.growthBonus.isFinite() && it.growthBonus >= 0
                },
            "livestock",
        )
    }
    require(coops.keys.all(::origin) && herds.keys.all(::origin) && workshops.keys.all(::origin), "homes")
    require(
        selectedWorkshopRecipes.all { (point, id) ->
            Recipe.named(id)?.let { it.buildingID == buildings[point] }
                ?: false
        },
        "workshop recipe choices",
    )
    require(
        workshops.all { (point, batch) ->
            batch.recipeID?.let { id -> Recipe.named(id)?.let { it.buildingID == buildings[point] } ?: false } ?: true
        },
        "workshop batch recipes",
    )
    require(
        fishPonds.keys.all(GameState::contains) &&
            fishPonds.values.all {
                date(it.nextFishAt) && date(it.waitStartedAt) && it.waitStartedAt <= it.nextFishAt &&
                    (it.fish?.let { fish -> GameState.contains(fish.position) && fish.kind != null } ?: true)
            },
        "fish",
    )
}

private fun GameState.validateTractor() {
    fun require(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw InvalidSaveException(field)
    }
    require(
        tractorCargo.all { (home, cargo) ->
            origin(home) && buildings[home] == "tractor1" &&
                cargo.all { (id, quantity) ->
                    (Crop.named(id)?.let { !it.isFruitTree } ?: false) && count(quantity) &&
                        quantity > 0
                }
        },
        "Tractor cargo",
    )
    val run = tractorRun ?: return
    require(
        GameState.contains(run.home) && GameState.contains(run.position) && buildings[run.home] == "tractor1" &&
            (Crop.named(run.cropID)?.let { !it.isFruitTree } ?: false) &&
            run.progress.isFinite() && run.progress >= 0 && run.progress < 1 &&
            run.remaining.size <= GameState.GRID_SIZE * GameState.GRID_SIZE && run.remaining.all(GameState::contains),
        "Tractor trip",
    )
    require(TractorRules.footprint(run.position, run.facing).all(GameState::contains), "Tractor position")
    val next = run.nextTile
    if (next != null) {
        require(
            GameState.contains(next) && abs(next.row - run.position.row) + abs(next.col - run.position.col) == 1 &&
                TractorRules.footprint(next, run.destinationFacing).all(GameState::contains),
            "Tractor step",
        )
    } else {
        require(run.progress == 0.0 && run.nextPoseFacing == null, "Tractor progress")
    }
}
