package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.model.GridPoint
import kotlin.math.max

/** A home's level, 1 as built. */
fun GameEngine.homeLevel(origin: GridPoint): Int = state.homeLevels[origin] ?: 1

/**
 * How many animals a home may hold: what its level holds, but never fewer than it already has, so a farm that held more
 * under older limits keeps them all.
 */
fun GameEngine.homeCapacity(origin: GridPoint): Int {
    val held = state.herds[origin]?.animals?.size ?: state.coops[origin]?.chickens?.size ?: 0
    return max(HomeUpgrade.capacity(state.buildings[origin] ?: "", homeLevel(origin)), held)
}

/** The upgrade after a home's current level, null for a building without levels or once it has them all. */
fun GameEngine.nextHomeUpgrade(origin: GridPoint): HomeUpgrade? {
    val id = state.buildings[origin] ?: return null
    return HomeUpgrade.upgrade(id, homeLevel(origin) + 1)
}

/** Why a home can't be upgraded right now, or null if it can. */
fun GameEngine.homeUpgradeRejection(origin: GridPoint): Rejection? {
    val id = state.buildings[origin]
    if (id == null || id !in HomeUpgrade.buildingIDs) return Rejection.NothingHere
    val upgrade = nextHomeUpgrade(origin) ?: return Rejection.HomeFullyUpgraded
    if (state.level < upgrade.unlockLevel) return Rejection.NeedLevel(upgrade.unlockLevel)
    if (state.money < upgrade.cost) return Rejection.NeedMoney(upgrade.cost)
    return null
}

/** Whether an upgrade is on offer: from the level it unlocks at, until it has been bought. */
fun GameEngine.offersHomeUpgrade(origin: GridPoint): Boolean = nextHomeUpgrade(origin)?.let { state.level >= it.unlockLevel } ?: false

/** Pays for the next level of a home, which then has room for more animals; the births already under way fill it. */
fun GameEngine.upgradeHome(origin: GridPoint): List<GameEvent> {
    homeUpgradeRejection(origin)?.let { return reject(it) }
    val upgrade = nextHomeUpgrade(origin) ?: return emptyList()
    state.money -= upgrade.cost
    state.homeLevels[origin] = upgrade.level
    noteReached(upgrade)
    return listOf(GameEvent.HomeUpgraded(origin, upgrade.level, upgrade.cost))
}

/** A home's level follows it when the building is moved. */
internal fun GameEngine.moveHomeLevel(
    oldOrigin: GridPoint,
    newOrigin: GridPoint,
) {
    if (oldOrigin == newOrigin) return
    val level = state.homeLevels.remove(oldOrigin) ?: return
    state.homeLevels[newOrigin] = level
}
