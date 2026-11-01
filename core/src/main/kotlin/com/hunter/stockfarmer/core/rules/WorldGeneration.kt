package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.randomElement
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/** How far, in tiles, from the starting farmland nothing is generated, so the first fields have room. */
internal const val STARTING_CLEARANCE = 3

/** Ponds keep further from the starting farmland than trees do. */
internal const val POND_CLEARANCE = 8
private val pondCount = 2..4
private val pondRadius = 3.5..7.0

/** No pond is smaller than this many tiles wide and tall, so every pond has room for a square of three by three water tiles. */
internal const val SMALLEST_POND_WIDTH = 7
internal const val SMALLEST_POND_HEIGHT = 5

/** With the usual number of ponds, how much a pond's size changes for each pond more or fewer. */
private const val USUAL_POND_COUNT = 3
private const val SIZE_CHANGE_PER_POND = 0.15

/** How tall a pond is for its width: always wider than tall. */
private val pondHeightForWidth = 0.55..0.9
private const val POND_ATTEMPTS = 200

/** Trees are tried this many times at random tiles; a try that lands near the middle is mostly turned away. */
private const val TREE_ATTEMPTS = 12000
private const val EDGE_DISTANCE = (GameState.GRID_SIZE - 1) / 2.0

/** A new game begins at eight in the morning. */
internal const val NEW_GAME_MINUTE = 8.0 * 60

/**
 * A game with round ponds with up to two ducks and a fish, or not yet, on each, then trees and rocks scattered over the
 * land around the starting farmland, thin near the farmland and thick toward the edge of the map.
 */
fun newGame(
    using: RandomSource,
    now: Instant,
): GameState {
    val state = pondsOnly(using)
    state.addTrees(now, using)
    val engine = GameEngine(state)
    engine.scatterRocksIfNeeded(using)
    engine.scatterOreVeinsIfNeeded(using)
    engine.scatterPondDucks(now, using)
    engine.scatterPondFish(now, using)
    engine.setTimeOfDay(NEW_GAME_MINUTE, now)
    return engine.state
}

internal fun pondsOnly(using: RandomSource): GameState = GameState().also { it.addPonds(using) }

internal fun GameState.Companion.isClearOfStart(
    point: GridPoint,
    clearance: Int = STARTING_CLEARANCE,
): Boolean = startingFarmland.all { max(abs(it.row - point.row), abs(it.col - point.col)) >= clearance }

/**
 * Ponds are filled ovals of deep water, wider than tall, with their tips trimmed, kept off the map's edge and a tile of
 * grass apart from each other.
 */
private fun GameState.addPonds(using: RandomSource) {
    val wanted = using.nextInt(pondCount)
    // Fewer ponds are slightly bigger and more ponds slightly smaller.
    val sizeScale = 1 + SIZE_CHANGE_PER_POND * (USUAL_POND_COUNT - wanted)
    var placed = 0
    for (attempt in 0 until POND_ATTEMPTS) {
        if (placed >= wanted) break
        val radius =
            max(
                SMALLEST_POND_WIDTH / 2.0,
                using.nextDouble(pondRadius.start, pondRadius.endInclusive) * sizeScale,
            )
        val radiusDown =
            max(
                SMALLEST_POND_HEIGHT / 2.0,
                radius * using.nextDouble(pondHeightForWidth.start, pondHeightForWidth.endInclusive),
            )
        val pond =
            oval(
                radius,
                radiusDown,
                GridPoint(
                    using.nextInt(0 until GameState.GRID_SIZE),
                    using.nextInt(0 until GameState.GRID_SIZE),
                ),
            )
        val margin = pond.flatMap { it.surrounding }.toSet()
        if (!isBigEnough(pond) || !pond.all(::canHoldWater) || !margin.all { it !in water }) continue
        pond.forEach { water[it] = WaterStyle.deep }
        placed += 1
    }
}

private fun isBigEnough(pond: List<GridPoint>): Boolean {
    val rows = pond.map { it.row }.toSet()
    val cols = pond.map { it.col }.toSet()
    val top = rows.minOrNull() ?: return false
    val bottom = rows.maxOrNull() ?: return false
    val left = cols.minOrNull() ?: return false
    val right = cols.maxOrNull() ?: return false
    return right - left + 1 >= SMALLEST_POND_WIDTH && bottom - top + 1 >= SMALLEST_POND_HEIGHT
}

/** The tiles of an oval, without the lone tile that would stick out at each end and make it look pointed. */
private fun oval(
    radiusAcross: Double,
    radiusDown: Double,
    center: GridPoint,
): List<GridPoint> {
    val rows = ceil(radiusDown).toInt()
    val cols = ceil(radiusAcross).toInt()
    val tiles =
        (-rows..rows).flatMap { rowOffset ->
            (-cols..cols).mapNotNull { colOffset ->
                val across = colOffset / radiusAcross
                val down = rowOffset / radiusDown
                if (across * across + down * down <=
                    1
                ) {
                    GridPoint(center.row + rowOffset, center.col + colOffset)
                } else {
                    null
                }
            }
        }
    val kept = tiles.toSet()
    return tiles.filter { tile -> tile.neighbors.count { it in kept } >= 2 }
}

/** Inside the map's edge, with a tile of grass left all round it, and well away from the start. */
private fun canHoldWater(point: GridPoint): Boolean =
    point.row in 1 until GameState.GRID_SIZE - 1 && point.col in 1 until GameState.GRID_SIZE - 1 &&
        GameState.isClearOfStart(point, POND_CLEARANCE)

/** How strongly the map's edge draws trees, rocks and ores: nothing at the middle of the map, certain at its edge. */
internal fun edgeWeight(point: GridPoint): Double {
    val ring = max(abs(point.row - EDGE_DISTANCE), abs(point.col - EDGE_DISTANCE)) / EDGE_DISTANCE
    return ring.pow(4)
}

/** Trees of every size, each part of the way through the time it stays at its size, so they don't all grow together. */
private fun GameState.addTrees(
    now: Instant,
    using: RandomSource,
) {
    val taken = water.keys.toMutableSet()
    val planted = trees.toMutableMap()
    repeat(TREE_ATTEMPTS) {
        val origin = GridPoint(using.nextInt(0 until GameState.GRID_SIZE), using.nextInt(0 until GameState.GRID_SIZE))
        if (using.nextUnit() >= edgeWeight(origin)) return@repeat
        val tree = Tree.wild.randomElement(using) ?: return@repeat
        val color = tree.species.colors.randomElement(using) ?: return@repeat
        val ground = tree.footprint(origin)
        if (!ground.all { GameState.contains(it) && GameState.isClearOfStart(it) && it !in taken }) return@repeat
        taken += ground
        val phase = using.nextDouble(0.0, Tree.growthInterval)
        planted[origin] = PlacedTree(tree, color, now.advancedBy(-phase))
    }
    replaceTrees(planted)
}

/** The tile and the eight around it. */
private val GridPoint.surrounding: List<GridPoint>
    get() = (-1..1).flatMap { rowOffset -> (-1..1).map { GridPoint(row + rowOffset, col + it) } }
