package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextUnit
import java.time.Instant
import kotlin.math.max

/**
 * A grown hen that is out of its coop lays an egg now and then where it stands, twice as often after eating a favorite
 * crop, but never more than [Egg.MAX_PER_HEN] of its eggs at once. Chicks lay none.
 */
fun GameEngine.layEggs(
    interval: Double,
    now: Instant,
    using: RandomSource,
) {
    for ((origin, coop) in state.coops) {
        for (hen in coop.chickens) {
            if (hen.kind.isRooster || !hen.isGrown(now) || !hen.hasEaten || isInside(AnimalRef.Chicken(origin, hen.id), now)) continue
            val productiveSeconds = hen.meal?.productiveSeconds(interval, now) ?: max(0.0, interval)
            val chance = productiveSeconds / Egg.LAY_INTERVAL
            if (state.eggs.count { it.coop == origin && it.henID == hen.id } >= Egg.MAX_PER_HEN) continue
            if (using.nextUnit() >= chance) continue
            state.eggs =
                state.eggs +
                Egg(
                    point = hen.position,
                    offsetX = using.nextDouble(-Egg.MAX_OFFSET, Egg.MAX_OFFSET),
                    offsetY = using.nextDouble(-Egg.MAX_OFFSET, Egg.MAX_OFFSET),
                    coop = origin,
                    henID = hen.id,
                )
        }
    }
}

/** Whether a hen has an egg floating over her, waiting to be collected. */
fun GameEngine.hasEggWaiting(
    coop: GridPoint,
    hen: Int,
): Boolean = state.eggs.any { it.coop == coop && it.henID == hen }

/** The bucket puts the egg of the hen standing on a tile into storage, if it fits. */
internal fun GameEngine.collectEggs(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    val levelBefore = state.level
    while (true) {
        val index = state.eggs.indexOfFirst { it.point == point }
        if (index < 0) break
        if (!hasRoom(1, HarvestTool.bucket)) {
            return events + levelUpEvents(levelBefore) + GameEvent.Rejected(Rejection.StorageFull)
        }
        val egg = state.eggs[index]
        state.eggs = state.eggs.filterIndexed { i, _ -> i != index }
        chickenIndex(egg.henID, egg.coop)?.let { henIndex ->
            val coop = state.coops.getValue(egg.coop)
            state.coops[egg.coop] =
                coop.copy(chickens = coop.chickens.mapIndexed { i, hen -> if (i == henIndex) hen.copy(lastHarvestedAt = now) else hen })
        }
        state.coops[egg.coop]?.chickens?.firstOrNull { it.id == egg.henID }?.let { noteWorked(it.variant) }
        events += gatherWithBonus(StorageItem.Egg, point, HarvestTool.bucket, earnXPForProducing(StorageItem.Egg))
    }
    return events + levelUpEvents(levelBefore)
}

/**
 * The last pass over the same tree fells it and stores its logs, if they all fit. A decoration or a fence is taken down in
 * one pass and stored.
 */
internal fun GameEngine.chopTree(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val site = state.tree(point)
    if (site == null) {
        val decoration = state.decoration(point)
        if (decoration != null && !decoration.second.decoration.isRock) {
            return fellDecoration(decoration.second, decoration.first, point, now)
        }
        FenceColor.named(state.fences[point] ?: "")?.let { return takeDownFence(it, point) }
        chopPass?.lastTile = point
        return emptyList()
    }
    val (origin, placed) = site
    val logs = placed.tree.logs
    if (!hasRoom(logs, HarvestTool.axe)) return listOf(GameEvent.Rejected(Rejection.StorageFull))
    val (pass, isNewHit) = advanceAxe(point, origin, placed.tree.footprint(origin))
    if (pass.hits < hitsToFell(placed.tree)) {
        chopPass = pass
        return listOf(GameEvent.Chopped(point, origin, pass.hits, isNewHit))
    }
    chopPass = null
    state.removeTree(origin)
    val gathered = (0 until logs).map { gather(StorageItem.Log, point, HarvestTool.axe) }
    return listOf<GameEvent>(GameEvent.Chopped(point, origin, pass.hits, true)) + gathered
}

/** The axe takes a fence down in one pass and stores it, as it does a decoration. */
private fun GameEngine.takeDownFence(
    color: FenceColor,
    point: GridPoint,
): List<GameEvent> {
    if (!hasRoom(1, HarvestTool.axe)) return listOf(GameEvent.Rejected(Rejection.StorageFull))
    chopPass = null
    state.fences.remove(point)
    return listOf(GameEvent.Chopped(point, point, 1, true), gather(StorageItem.Fence(color), point, HarvestTool.axe))
}

private fun GameEngine.fellDecoration(
    placed: PlacedDecoration,
    origin: GridPoint,
    tile: GridPoint,
    now: Instant,
): List<GameEvent> {
    if (!hasRoom(1, HarvestTool.axe)) return listOf(GameEvent.Rejected(Rejection.StorageFull))
    chopPass = null
    dryOut(placed, origin, now)
    state.removeDecoration(origin)
    return listOf(
        GameEvent.Chopped(tile, origin, 1, true),
        gather(StorageItem.DecorationItem(placed.decoration, placed.size), tile, HarvestTool.axe),
    )
}

/** The last pass over the same rock breaks it and stores its stones, if they all fit. */
internal fun GameEngine.mineRock(point: GridPoint): List<GameEvent> {
    val size = state.rocks[point]
    if (size == null) {
        chopPass?.lastTile = point
        return emptyList()
    }
    if (!hasRoom(size.stones, HarvestTool.pickaxe)) return listOf(GameEvent.Rejected(Rejection.StorageFull))
    val (pass, isNewHit) = advanceAxe(point, point, listOf(point))
    if (pass.hits < hitsToBreak(size)) {
        chopPass = pass
        return listOf(GameEvent.Mined(point, pass.hits, isNewHit))
    }
    chopPass = null
    state.rocks.remove(point)
    noteMined(size)
    val item = size.ore?.let { StorageItem.Ore(it) } ?: StorageItem.Stone
    val gathered = (0 until size.stones).map { gather(item, point, HarvestTool.pickaxe) }
    return listOf<GameEvent>(GameEvent.Mined(point, pass.hits, true)) + gathered
}

/**
 * A pass is one go over a tree or rock, so the axe or pickaxe has to leave it and come back, or be lifted, to count again.
 * Running over another one starts that one afresh and drops the first.
 */
private fun GameEngine.advanceAxe(
    point: GridPoint,
    origin: GridPoint,
    ground: List<GridPoint>,
): Pair<ChopPass, Boolean> {
    val current = chopPass
    val pass = if (current?.origin == origin) current else ChopPass(origin)
    val isSamePass = pass.lastTile?.let { it in ground } ?: false
    val advanced = ChopPass(pass.origin, pass.hits + if (isSamePass) 0 else 1, point)
    return advanced to !isSamePass
}

/** The finger has left the farm, so the next time the axe meets a tree or the pickaxe a rock it is a new pass. */
fun GameEngine.liftTool() {
    chopPass?.lastTile = null
}
