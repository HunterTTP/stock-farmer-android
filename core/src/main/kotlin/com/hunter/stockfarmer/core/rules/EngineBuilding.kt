package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.BuildingStyle
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import java.time.Instant

/** A building on its way to the map: a new one being set down, or an existing one being moved. */
data class BuildingPlacement(
    val building: Building,
    val origin: GridPoint,
    /** The building being moved, which stays where it is until the placement is confirmed. */
    val replacing: PlacedBuilding?,
) {
    val footprint: List<GridPoint> get() = building.footprint(origin)
    internal val occupiedTiles: List<GridPoint> get() = building.occupiedTiles(origin)
}

fun GameEngine.select(building: Building) {
    if (!isUnlocked(building)) return
    state.selectedBuildingID = if (building.isMarketStand) Building.marketStand.id else building.id
    state.mode = PlayMode.build
    placement = null
}

/** Picks the style the selected building, and each one of its kind placed after, is built in. */
fun GameEngine.select(
    style: BuildingStyle,
    building: Building,
) {
    if (!isUnlocked(building)) return
    building.styled(style.id).styleID?.let { state.selectedBuildingStyleIDs[building.id] = it }
    select(building)
}

val GameEngine.selectedBuilding: Building
    get() {
        val building = Building.named(state.selectedBuildingID) ?: Building.starter
        val offered = if (building.isMarketStand) Building.marketStand else building
        return offered.styled(state.selectedBuildingStyleIDs[offered.id])
    }

fun GameEngine.building(point: GridPoint): PlacedBuilding? {
    val run = state.tractorRun
    if (run != null &&
        point in state.tractorOccupiedTiles
    ) {
        return state.placedBuildings.firstOrNull { it.origin == run.home }
    }
    return state.building(point)
}

val GameEngine.buildingPlacement: BuildingPlacement? get() = if (state.mode == PlayMode.build) placement else null

val GameEngine.isPlacingBuilding: Boolean get() = buildingPlacement != null

/** Whether a building, a fruit tree, a decoration or a tree is being set down, when only its arrows, OK and Cancel respond. */
val GameEngine.isPlacing: Boolean get() = isPlacingBuilding || isPlacingFruitTree || objectPlacement != null

/** Why a new building can't be started right now (not unlocked, or not enough money, logs or stones), or null if it can. */
val GameEngine.cannotStartPlacingSelectedBuilding: Rejection? get() = unaffordableRejection(selectedBuilding)

private fun GameEngine.unaffordableRejection(building: Building): Rejection? {
    if (!isUnlocked(building)) return Rejection.NeedLevel(building.unlockLevel)
    if (state.money < building.cost) return Rejection.NeedMoney(building.cost)
    if ((state.storage[StorageItem.Log.id] ?: 0) < building.logCost) return Rejection.NeedLogs(building.logCost)
    return if ((state.storage[StorageItem.Stone.id] ?: 0) >=
        building.stoneCost
    ) {
        null
    } else {
        Rejection.NeedStones(building.stoneCost)
    }
}

/** Starts setting the selected building down, centered on a tile. */
fun GameEngine.beginPlacing(aroundTile: GridPoint) {
    if (state.mode != PlayMode.build || !isUnlocked(selectedBuilding)) return
    placement = placementOf(selectedBuilding, null, aroundTile)
}

/** Starts picking a building up to put it somewhere else; it begins where it already stands. */
fun GameEngine.beginMoving(placed: PlacedBuilding) {
    if (state.tractorRun?.home == placed.origin) return
    fruitPlacement = null
    objectPlacement = null
    state.mode = PlayMode.build
    placement = BuildingPlacement(placed.building, placed.origin, placed)
}

fun GameEngine.cancelPlacement() {
    placement = null
    fruitPlacement = null
    objectPlacement = null
}

/** Shifts the building one tile, staying inside the map. */
fun GameEngine.nudgePlacement(
    rows: Int,
    cols: Int,
) {
    if (objectPlacement != null) return nudgeObjectPlacement(rows, cols)
    fruitPlacement?.let { tree ->
        fruitPlacement =
            tree.copy(origin = clampedFruitOrigin(GridPoint(tree.origin.row + rows, tree.origin.col + cols), tree.crop))
        return
    }
    val current = buildingPlacement ?: return
    placement =
        current.copy(
            origin =
                current.building.clampedOrigin(
                    GridPoint(
                        current.origin.row + rows,
                        current.origin.col + cols,
                    ),
                ),
        )
}

/** Puts the building with its corner at [origin], staying inside the map. */
fun GameEngine.movePlacement(origin: GridPoint) {
    if (objectPlacement != null) return moveObjectPlacement(origin)
    fruitPlacement?.let { tree ->
        fruitPlacement = tree.copy(origin = clampedFruitOrigin(origin, tree.crop))
        return
    }
    val current = buildingPlacement ?: return
    placement = current.copy(origin = current.building.clampedOrigin(origin))
}

/** Why the building can't go down where it is now, or null if it can. */
fun GameEngine.placementRejection(now: Instant): Rejection? {
    objectPlacement?.let { return objectRejection(it) }
    fruitPlacement?.let { return fruitTreeRejection(it) }
    return buildingPlacement?.let { buildRejection(it, now) }
}

/** Whether confirming the building being placed puts up the first of its kind the farm has ever had, which is celebrated. */
val GameEngine.isPlacingFirstOfItsKind: Boolean
    get() {
        val current = buildingPlacement ?: return false
        return current.replacing == null && !hasBuilt(current.building)
    }

/** Puts the building down; if something is in the way it stays where it is and the reason comes back. */
fun GameEngine.confirmPlacement(now: Instant): List<GameEvent> {
    objectPlacement?.let { return confirmObjectPlacement(it, now) }
    fruitPlacement?.let { return confirmFruitTree(it, now) }
    val current = buildingPlacement ?: return emptyList()
    buildRejection(current, now)?.let { return reject(it) }
    val building = current.building
    val moved = current.replacing
    if (moved != null) {
        state.removeBuilding(moved.origin)
    } else {
        state.money -= building.cost
        removeFromStorage(StorageItem.Log.id, building.logCost)
        removeFromStorage(StorageItem.Stone.id, building.stoneCost)
        noteBuilt(building)
    }
    for (tile in current.occupiedTiles) {
        state.farmland.remove(tile)
        state.fertilized.remove(tile)
    }
    state.putBuilding(current.origin, building.id, building.styleID)
    if (moved != null) {
        movePet(moved.origin, current.origin)
        moveCoop(moved.origin, current.origin)
        moveHerd(moved.origin, current.origin)
        moveWorkshop(moved.origin, current.origin)
        moveTractorCargo(moved.origin, current.origin)
    }
    relocateAnimals(current.footprint)
    placement = null
    return listOf(
        if (moved ==
            null
        ) {
            GameEvent.Built(current.origin, building.cost)
        } else {
            GameEvent.Moved(current.origin)
        },
    )
}

/** Takes a building down for good and returns 80% of what it cost, in money and in each material. */
fun GameEngine.demolish(placed: PlacedBuilding): List<GameEvent> {
    demolishRejection(placed)?.let { return reject(it) }
    if (state.removeBuilding(placed.origin) == null) return emptyList()
    state.tractorCargo.remove(placed.origin)
    if (placement?.replacing == placed) placement = null
    state.pets.remove(placed.origin)
    state.coops.remove(placed.origin)
    state.herds.remove(placed.origin)
    state.homeLevels.remove(placed.origin)
    state.workshops.remove(placed.origin)
    state.selectedWorkshopRecipes.remove(placed.origin)
    state.eggs = state.eggs.filter { it.coop != placed.origin }
    val refund = refund(placed.building)
    credit(refund)
    val (logs, stones) = materialRefund(placed.building)
    state.storage[StorageItem.Log.id] = (state.storage[StorageItem.Log.id] ?: 0) + logs
    state.storage[StorageItem.Stone.id] = (state.storage[StorageItem.Stone.id] ?: 0) + stones
    return listOf(GameEvent.Demolished(placed.origin, refund))
}

private fun placementOf(
    building: Building,
    replacing: PlacedBuilding?,
    aroundTile: GridPoint,
): BuildingPlacement {
    val origin =
        building.clampedOrigin(
            GridPoint(
                aroundTile.row - (building.height - 1) / 2,
                aroundTile.col - (building.width - 1) / 2,
            ),
        )
    return BuildingPlacement(building, origin, replacing)
}

internal fun GameEngine.buildRejection(
    placement: BuildingPlacement,
    now: Instant,
): Rejection? {
    val moving = placement.replacing
    if (moving != null && state.tractorRun?.home == moving.origin) return Rejection.TractorBusy
    val building = placement.building
    if (moving == null) unaffordableRejection(building)?.let { return it }
    val limit = building.placementLimit
    if (moving == null && placedOnMap(ShopItem.BuildingItem(building.id)) >= limit) {
        return Rejection.BuildingLimit(building.name, limit)
    }
    frontRejection(placement)?.let { return it }
    for (point in placement.occupiedTiles) cropInTheWay(point, now)?.let { return it }
    val replacesAnother = state.buildings[placement.origin] != null && placement.origin != moving?.origin
    return if (!replacesAnother &&
        placement.occupiedTiles.all { canBuildOn(it, placement) }
    ) {
        null
    } else {
        Rejection.NotEnoughSpace
    }
}

/**
 * Pet houses and coops need a tile in front where their animals appear, which must be plain grass, farmland or a crop: no
 * building, water or fence, now or later.
 */
private fun GameEngine.frontRejection(placement: BuildingPlacement): Rejection? {
    val building = placement.building
    if (building.needsFrontTile) {
        val front = building.frontTile(placement.origin)
        val occupant = if (GameState.contains(front)) state.building(front) else null
        val free = occupant == null || occupant == placement.replacing
        if (!GameState.contains(front) || !free || front in state.water || front in state.fences ||
            state.hasObstacle(front)
        ) {
            return Rejection.NeedsFrontTile
        }
    }
    val blocksAFront =
        state.placedBuildings.any { other ->
            other != placement.replacing && other.building.needsFrontTile &&
                other.building.frontTile(other.origin) in placement.footprint
        }
    return if (blocksAFront) Rejection.BlocksAFront else null
}

/** A tall building may stand over fences in its top row, which then run behind it. */
private fun GameEngine.canBuildOn(
    point: GridPoint,
    placement: BuildingPlacement,
): Boolean {
    if (!GameState.contains(point) || point in state.water || state.hasObstacle(point)) return false
    if (point in state.fences) {
        val standsOverFence =
            placement.building.id != "tractor1" && placement.building.height > 1 && point.row == placement.origin.row
        if (!standsOverFence) return false
    }
    val occupant = state.buildingBlocking(point) ?: return true
    return occupant == placement.replacing
}
