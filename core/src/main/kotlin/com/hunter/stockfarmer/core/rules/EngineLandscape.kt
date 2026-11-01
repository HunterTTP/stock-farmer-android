package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import java.time.Instant

fun GameEngine.select(waterStyle: WaterStyle) {
    state.selectedWaterStyle = waterStyle
    select(LandscapeTool.water)
}

fun GameEngine.select(tool: LandscapeTool) {
    state.selectedLandscape = tool
    state.selectedTerrain = tool
    state.mode = PlayMode.landscape
}

/** Picks up the Fertilize tool with whatever it last held, fertilizer or sprinklers. */
fun GameEngine.selectTilling() {
    val sprinkler = Decoration.named(state.lastSprinklerID)
    if (state.fertilizesWithSprinklers && sprinkler != null && isUnlocked(sprinkler)) select(sprinkler) else selectFertilizer()
}

/** Picks up the Terrain tool with whatever terrain or tree it last held. */
fun GameEngine.selectTerrain() = select(state.selectedTerrain)

/** Picks up the Decorate tool with whatever decoration or fence it last held. */
fun GameEngine.selectDecorating() {
    val decoration = state.lastDecorationID?.let(Decoration::named)
    if (decoration != null && isUnlocked(decoration) && decoration.offeredSizes.isNotEmpty()) {
        select(decoration)
    } else {
        select(selectedFenceColor)
    }
}

internal fun GameEngine.tapLandscape(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val tool = state.selectedLandscape
    if (tool == LandscapeTool.tree) return tapTree(point, now)
    structureRefusal(point)?.let { return reject(it) }
    cropInTheWay(point, now)?.let { return reject(it) }
    val result: Attempt<Unit> =
        when (tool) {
            is LandscapeTool.groundCover -> return placeGroundCover(tool.cover, point)
            LandscapeTool.farmland -> placeFarmland(point)
            LandscapeTool.grass -> clearToGrass(point)
            LandscapeTool.water -> placeWater(point)
            LandscapeTool.tree -> Attempt.Refused(Rejection.NothingHere)
        }
    return when (result) {
        is Attempt.Done -> {
            CareerRecord.terrainID(tool, state.selectedWaterStyle)?.let { noteTerrain(it) }
            state.groundCover.remove(point)
            listOf(GameEvent.Placed(point))
        }
        is Attempt.Refused -> reject(result.rejection)
    }
}

/**
 * Why the selected terrain can't go on a tile: a rock, a tree or the driving Tractor is on it, or a fence, building or
 * decoration is that can't stand on the terrain. A sprinkler stands on any terrain.
 */
private fun GameEngine.structureRefusal(point: GridPoint): Rejection? {
    if (state.hasSolidObstacle(point)) return Rejection.StructureHere
    val decoration = state.decorationStanding(point)
    val hasStructure = point in state.fences || state.buildingBlocking(point) != null || decoration != null
    val standsOnAnything = decoration?.decoration?.isSprinkler == true
    return if (hasStructure && !state.selectedLandscape.fitsUnderAStructure && !standsOnAnything) Rejection.StructureHere else null
}

private fun GameEngine.placeGroundCover(
    cover: GroundCover,
    point: GridPoint,
): List<GameEvent> {
    if (state.groundCover[point] == cover) return reject(Rejection.AlreadyPlaced)
    spend(cover.cost)?.let { return reject(it) }
    state.farmland.remove(point)
    state.fertilized.remove(point)
    state.water.remove(point)
    state.groundCover[point] = cover
    noteTerrain(cover.rawValue)
    return listOf(GameEvent.Decorated(point, cover.cost))
}

/** Farmland is free; levels unlock how many tiles can be placed at once. */
private fun GameEngine.placeFarmland(point: GridPoint): Attempt<Unit> {
    if (point in state.farmland && state.groundCover[point] != null) return Attempt.Done(Unit)
    if (point in state.farmland) return Attempt.Refused(Rejection.AlreadyPlaced)
    val limit = state.farmlandLimit
    if (state.farmland.size >= limit) return Attempt.Refused(Rejection.FarmlandLimit(state.farmland.size, limit))
    state.water.remove(point)
    state.farmland.add(point)
    return Attempt.Done(Unit)
}

private fun GameEngine.clearToGrass(point: GridPoint): Attempt<Unit> {
    val hadCover = state.groundCover.remove(point) != null
    val hadFarmland = state.farmland.remove(point)
    val hadWater = state.water.remove(point) != null
    if (!hadCover && !hadFarmland && !hadWater) return Attempt.Refused(Rejection.NothingHere)
    state.fertilized.remove(point)
    return Attempt.Done(Unit)
}

/** Water is free and unlimited, including recoloring existing tiles. */
private fun GameEngine.placeWater(point: GridPoint): Attempt<Unit> {
    if (point in frontTilesOfHomes) return Attempt.Refused(Rejection.BlocksAFront)
    val style = state.selectedWaterStyle
    if (state.water[point] == style) return Attempt.Refused(Rejection.AlreadyPlaced)
    spend(LandscapeTool.waterCost)?.let { return Attempt.Refused(it) }
    state.farmland.remove(point)
    state.fertilized.remove(point)
    state.water[point] = style
    relocateAnimals(listOf(point))
    return Attempt.Done(Unit)
}
