package com.hunter.stockfarmer.core

import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.beginPlacing
import com.hunter.stockfarmer.core.rules.confirmPlacement
import com.hunter.stockfarmer.core.rules.movePlacement
import com.hunter.stockfarmer.core.rules.updateCoops
import com.hunter.stockfarmer.core.rules.updateHerds
import com.hunter.stockfarmer.core.util.SeededRandom
import java.time.Instant
import com.hunter.stockfarmer.core.rules.beginMoving as beginMovingInPlace

/** A game with the given money, XP, mode and building materials, as the Swift tests build theirs. */
fun stateWith(
    money: Double = 0.0,
    xp: Int = 0,
    mode: PlayMode = PlayMode.plant,
    logs: Int = 90,
    stones: Int? = null,
): GameState {
    val state = GameState()
    if (logs > 0) state.storage[StorageItem.Log.id] = logs
    // A test that asks for a great many logs is building a great deal, so it gets plenty of stones too; otherwise a few,
    // which leave room in storage for gathering.
    val stoneCount =
        stones ?: if (logs > 90) {
            1_000
        } else if (logs > 0) {
            5
        } else {
            0
        }
    if (stoneCount > 0) state.storage[StorageItem.Stone.id] = stoneCount
    state.money = money
    state.xp = xp
    state.mode = mode
    return state
}

/** Enough XP to have unlocked every crop and building. */
val lateGameXP: Int get() = LevelRules.xpToReach(LevelRules.maxLevel)

/** Sets the selected building down centered on a tile, the way the placement buttons do. */
fun GameEngine.build(
    aroundTile: GridPoint,
    now: Instant,
): List<GameEvent> {
    beginPlacing(aroundTile)
    return confirmPlacement(now)
}

/** Hatches whatever chicks are due at [now], with the same random choices every time. */
fun GameEngine.hatch(now: Instant): List<GameEvent> = updateCoops(now, SeededRandom(1))

/** Brings the animals of every livestock building up to [now], with the same random choices every time. */
fun GameEngine.updateHerds(now: Instant): List<GameEvent> = updateHerds(now, SeededRandom(1))

/** An egg laid by some hen of some coop, for tests that only care where it lies. */
fun lyingEgg(
    point: GridPoint,
    offsetX: Double,
    offsetY: Double,
) = com.hunter.stockfarmer.core.model
    .Egg(point, offsetX, offsetY, GridPoint(0, 0), 0)

/** Puts a building on the map by its id. */
fun GameEngine.place(
    origin: GridPoint,
    id: String,
    styleID: String? = null,
) = state.putBuilding(origin, id, styleID)

/** Picks a building up and sets the placement centered on a tile, as dragging it there would. */
fun GameEngine.beginMoving(
    placed: com.hunter.stockfarmer.core.catalog.PlacedBuilding,
    aroundTile: GridPoint,
) {
    beginMovingInPlace(placed)
    movePlacement(
        GridPoint(aroundTile.row - (placed.building.height - 1) / 2, aroundTile.col - (placed.building.width - 1) / 2),
    )
}

fun com.hunter.stockfarmer.core.catalog.PlacedBuilding.covers(point: GridPoint): Boolean =
    point.row in origin.row until origin.row + building.height &&
        point.col in origin.col until origin.col + building.width
