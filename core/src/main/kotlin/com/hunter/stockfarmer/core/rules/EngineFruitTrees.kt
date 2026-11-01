package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.bottomRow
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/** A fruit tree on its way to the map: a new one being set down, or a standing one being moved. */
data class FruitTreePlacement(
    val crop: Crop,
    val origin: GridPoint,
    val replacing: Moved?,
) {
    /** A tree being moved: where it stands now and when it was planted, which it keeps. */
    data class Moved(
        val origin: GridPoint,
        val plantedAt: Instant,
    )

    val footprint: List<GridPoint> get() = crop.footprint(origin)
}

/** Whether the tool in hand sets a fruit tree down, which is placed like a building instead of planted tile by tile. */
val GameEngine.isFruitTreeSelected: Boolean get() = state.mode == PlayMode.plant && selectedCrop.isFruitTree

val GameEngine.isPlacingFruitTree: Boolean get() = fruitPlacement != null

/** Why a new fruit tree can't be started right now (not enough money), or null if it can. */
val GameEngine.cannotStartPlacingSelectedFruitTree: Rejection?
    get() {
        val price = selectedCrop.seedPrice
        return if (state.money >= price) null else Rejection.NeedMoney(price)
    }

/** Starts setting the selected fruit tree down, with its trunk on a tile. */
fun GameEngine.beginPlacingFruitTree(aroundTile: GridPoint) {
    if (!isFruitTreeSelected) return
    val crop = selectedCrop
    fruitPlacement = FruitTreePlacement(crop, clampedFruitOrigin(crop.origin(aroundTile), crop), null)
}

/** Starts picking a standing fruit tree up to put it somewhere else; it begins where it stands and keeps its growth. */
fun GameEngine.beginMovingFruitTree(point: GridPoint) {
    val site = state.plant(point) ?: return
    val crop = Crop.named(site.second.cropID)
    if (crop == null || !crop.isFruitTree) return
    placement = null
    objectPlacement = null
    fruitPlacement = FruitTreePlacement(crop, site.first, FruitTreePlacement.Moved(site.first, site.second.plantedAt))
}

internal fun clampedFruitOrigin(
    origin: GridPoint,
    crop: Crop,
): GridPoint =
    GridPoint(
        min(max(origin.row, 0), GameState.GRID_SIZE - crop.side),
        min(max(origin.col, 0), GameState.GRID_SIZE - crop.side),
    )

internal fun GameEngine.fruitTreeRejection(placing: FruitTreePlacement): Rejection? {
    if (placing.replacing == null) cannotStartPlacingSelectedFruitTree?.let { return it }
    if (placing.replacing == null && placedOnMap(ShopItem.Seed(placing.crop.id)) >= Crop.MOST_FRUIT_TREES_OF_A_KIND) {
        return Rejection.BuildingLimit(placing.crop.name, Crop.MOST_FRUIT_TREES_OF_A_KIND)
    }
    val own = placing.replacing?.let { placing.crop.plotTiles(it.origin).toSet() } ?: emptySet()
    if (!placing.footprint.bottomRow.all { it in own || isFreeForTree(it) }) return Rejection.NeedGrass
    return if (blocksAHomesFront(placing.footprint)) Rejection.BlocksAFront else null
}

internal fun GameEngine.confirmFruitTree(
    placing: FruitTreePlacement,
    now: Instant,
): List<GameEvent> {
    fruitTreeRejection(placing)?.let { return reject(it) }
    val crop = placing.crop
    var plantedAt = now
    val moved = placing.replacing
    if (moved != null) {
        plantedAt = moved.plantedAt
        crop.plotTiles(moved.origin).forEach { state.plots.remove(it) }
    } else {
        state.money -= crop.seedPrice
    }
    for (tile in crop.plotTiles(placing.origin)) state.plots[tile] = Plot(crop.id, plantedAt, placing.origin)
    fruitPlacement = null
    if (moved == null) {
        recordQuestProgress(QuestGoal.PlantTrees)
        notePlanted(crop)
    }
    return listOf(
        if (moved ==
            null
        ) {
            GameEvent.Planted(placing.origin, crop.seedPrice)
        } else {
            GameEvent.Moved(placing.origin)
        },
    )
}
