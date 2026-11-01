package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.util.RandomSource
import java.time.Instant

/** Why the house can't be upgraded to the luxury house right now, or null if it can. */
fun GameEngine.houseUpgradeRejection(
    origin: GridPoint,
    now: Instant,
): Rejection? {
    val placed = building(origin)
    if (placed == null || placed.origin != origin || !placed.building.isHouse) return Rejection.NothingHere
    if (placed.building.isUpgradedHouse) return Rejection.AlreadyUpgraded
    if (state.level <
        StockMarketRules.HOUSE_UPGRADE_LEVEL
    ) {
        return Rejection.NeedLevel(StockMarketRules.HOUSE_UPGRADE_LEVEL)
    }
    if (state.money <
        StockMarketRules.HOUSE_UPGRADE_COST
    ) {
        return Rejection.NeedMoney(StockMarketRules.HOUSE_UPGRADE_COST)
    }
    return buildRejection(BuildingPlacement(Building.upgradedHouse, origin, placed), now)
}

/** Swaps the house for the luxury house where it stands, which lets the player own more stocks. */
fun GameEngine.upgradeHouse(
    origin: GridPoint,
    now: Instant,
): List<GameEvent> {
    houseUpgradeRejection(origin, now)?.let { return reject(it) }
    val upgraded = Building.upgradedHouse
    state.money -= StockMarketRules.HOUSE_UPGRADE_COST
    for (tile in upgraded.footprint(origin).bottomRow) {
        state.farmland.remove(tile)
        state.fertilized.remove(tile)
    }
    state.putBuilding(origin, upgraded.id, null)
    relocateAnimals(upgraded.footprint(origin))
    return listOf(GameEvent.Built(origin, StockMarketRules.HOUSE_UPGRADE_COST))
}

fun GameEngine.marketUpgradeRejection(
    origin: GridPoint,
    now: Instant,
): Rejection? {
    val placed = building(origin)
    if (placed == null || placed.origin != origin || !placed.building.isMarketStand) return Rejection.NothingHere
    if (placed.building.isUpgradedMarketStand) return Rejection.AlreadyUpgraded
    if (state.completedMarketQuests < QuestRules.QUESTS_TO_UPGRADE) {
        return Rejection.NeedQuestCompletions(QuestRules.QUESTS_TO_UPGRADE)
    }
    if (state.money < QuestRules.UPGRADE_COST) return Rejection.NeedMoney(QuestRules.UPGRADE_COST)
    return buildRejection(BuildingPlacement(Building.upgradedMarketStand, origin, placed), now)
}

fun GameEngine.upgradeMarketStand(
    origin: GridPoint,
    now: Instant,
    using: RandomSource,
): List<GameEvent> {
    marketUpgradeRejection(origin, now)?.let { return reject(it) }
    val upgraded = Building.upgradedMarketStand.styled(state.buildingStyles[origin])
    state.money -= QuestRules.UPGRADE_COST
    for (tile in upgraded.footprint(origin).bottomRow) {
        state.farmland.remove(tile)
        state.fertilized.remove(tile)
    }
    state.putBuilding(origin, upgraded.id, upgraded.styleID)
    val current = state.marketQuests.remove(Building.marketStand.id) ?: emptyList()
    val existing = state.marketQuests[upgraded.id] ?: emptyList()
    state.marketQuests[upgraded.id] = current + existing.filter { quest -> current.none { it.id == quest.id } }
    fillQuestBoard(upgraded.id, using)
    relocateAnimals(upgraded.footprint(origin))
    return listOf(GameEvent.Built(origin, QuestRules.UPGRADE_COST))
}

/**
 * Brings a saved game up to date when it is opened, at launch or from the save slots: the farm's clock stays where it was
 * left and trees grow. Nothing grows back while away: new trees, rocks and ores only come as the midnight wave passes.
 */
fun GameEngine.reopen(
    now: Instant,
    using: RandomSource,
) {
    holdClockWhileAway(now)
    skipNewDayInProgress(now)
    growTrees(now)
    scatterRocksIfNeeded(using)
    scatterOreVeinsIfNeeded(using)
}
