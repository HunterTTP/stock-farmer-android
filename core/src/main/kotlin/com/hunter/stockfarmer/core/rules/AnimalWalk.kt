package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

private const val SIDE = GameState.GRID_SIZE

/** A tile's place in the map's arrays; negative for a tile off the map. */
private fun tileIndex(
    row: Int,
    col: Int,
): Int = if (row in 0 until SIDE && col in 0 until SIDE) row * SIDE + col else -1

/**
 * What every animal's next step reads while the animals walk at one moment, worked out at most once each: the ground they
 * may stand on, the food for each crop and what draws them. A step only ever moves an animal, and none of this looks at
 * where animals stand, so it holds for all of them. Where animals stand is always read live: crowding, who has eggs and who
 * is inside. Good only while nothing but animal positions changes, so make a new one for each walk.
 */
class AnimalWalk internal constructor(
    private val engine: GameEngine,
    internal val now: Instant,
) {
    private val state: GameState = engine.state

    internal val tractorTiles: Set<GridPoint> = state.tractorOccupiedTiles

    /** Whether each tile is open ground: unknown until first asked, as most walks ask about few of them. */
    private val openGround = ByteArray(SIDE * SIDE)

    private val conditions by lazy(LazyThreadSafetyMode.NONE) { GrowthConditions(state) }

    private val foodByCrop = HashMap<String, BooleanArray?>()

    private val attractionRows: IntArray
    private val attractionCols: IntArray

    /** How far each tile is from the nearest thing that draws animals; negative until first asked. */
    private val nearestAttraction = IntArray(SIDE * SIDE) { UNKNOWN }

    // One search runs at a time, so they share these. A tile has been reached in this search if its stamp is the search's.
    private val reachedInSearch = IntArray(SIDE * SIDE)
    private var search = 0
    private val queue = IntArray(SIDE * SIDE)
    private val firstSteps = IntArray(SIDE * SIDE)
    private val stepsFromStart = IntArray(SIDE * SIDE)

    /** What the last search for a way home started from, which may be off the map, and so has no place in the arrays. */
    private var homeStart = GridPoint(0, 0)

    init {
        val plots = state.plots.keys
        val buildings = state.placedBuildings.flatMap { it.footprint }
        attractionRows = IntArray(plots.size + buildings.size)
        attractionCols = IntArray(attractionRows.size)
        var index = 0
        for (tile in plots) {
            attractionRows[index] = tile.row
            attractionCols[index++] = tile.col
        }
        for (tile in buildings) {
            attractionRows[index] = tile.row
            attractionCols[index++] = tile.col
        }
    }

    internal fun isOpenGround(point: GridPoint): Boolean = isOpenGround(point.row, point.col)

    private fun isOpenGround(
        row: Int,
        col: Int,
    ): Boolean {
        val index = tileIndex(row, col)
        if (index < 0) return false
        val known = openGround[index]
        if (known != UNKNOWN_GROUND) return known == OPEN
        val open = engine.isOpenGround(GridPoint(row, col))
        openGround[index] = if (open) OPEN else CLOSED
        return open
    }

    /** [GameEngine.canStand], for an animal already known to be a waterfowl or not. */
    internal fun canStand(
        point: GridPoint,
        isWaterfowl: Boolean,
    ): Boolean {
        if (point in tractorTiles) return false
        if (isWaterfowl) return point in state.water && state.decoration(point) == null
        return isOpenGround(point)
    }

    /** The tiles an animal after this crop could eat from, all on open ground and so on the map; null if there are none. */
    private fun food(crop: Crop): BooleanArray? =
        foodByCrop.getOrPut(crop.id) {
            var food: BooleanArray? = null
            for (tile in state.plots.keys) {
                if (engine.edibleFavorite(tile, crop, now, conditions)) {
                    val tiles = food ?: BooleanArray(SIDE * SIDE).also { food = it }
                    tiles[tileIndex(tile.row, tile.col)] = true
                }
            }
            food
        }

    /**
     * The first step on the shortest way over open ground to a tile at or beside the crop, searching outward from the
     * animal in the order of [GridPoint.neighbors], so that ties go the same way every time. Null if the animal is already
     * there or there is no way.
     */
    internal fun firstStepToward(
        crop: Crop,
        from: GridPoint,
    ): GridPoint? {
        val food = food(crop) ?: return null
        if (isAtOrBeside(food, from.row, from.col)) return null
        val stamp = ++search
        tileIndex(from.row, from.col).let { if (it >= 0) reachedInSearch[it] = stamp }
        var tail = 0
        for (direction in 0 until DIRECTIONS) {
            val row = from.row + ROW_STEPS[direction]
            val col = from.col + COL_STEPS[direction]
            if (!isOpenGround(row, col)) continue
            val index = tileIndex(row, col)
            if (reachedInSearch[index] == stamp) continue
            reachedInSearch[index] = stamp
            queue[tail] = index
            firstSteps[tail++] = direction
        }
        var head = 0
        while (head < tail) {
            val tile = queue[head]
            val direction = firstSteps[head++]
            val row = tile / SIDE
            val col = tile % SIDE
            if (isAtOrBeside(food, row, col)) return GridPoint(ROW_STEPS[direction], COL_STEPS[direction])
            for (next in 0 until DIRECTIONS) {
                val nextRow = row + ROW_STEPS[next]
                val nextCol = col + COL_STEPS[next]
                if (!isOpenGround(nextRow, nextCol)) continue
                val index = tileIndex(nextRow, nextCol)
                if (reachedInSearch[index] == stamp) continue
                reachedInSearch[index] = stamp
                queue[tail] = index
                firstSteps[tail++] = direction
            }
        }
        return null
    }

    private fun isAtOrBeside(
        food: BooleanArray,
        row: Int,
        col: Int,
    ): Boolean {
        if (isFood(food, row, col)) return true
        for (direction in 0 until DIRECTIONS) {
            if (isFood(food, row + ROW_STEPS[direction], col + COL_STEPS[direction])) return true
        }
        return false
    }

    private fun isFood(
        food: BooleanArray,
        row: Int,
        col: Int,
    ): Boolean {
        val index = tileIndex(row, col)
        return index >= 0 && food[index]
    }

    /**
     * How many steps over open ground each tile is from [start], within the rows and columns given, worked out only as far
     * as [target], which is all that is needed to tell which of its neighbors are nearer; false if there is no way there
     * inside them. Read the steps with [stepsFromHome].
     */
    internal fun searchFromHome(
        start: GridPoint,
        target: GridPoint,
        rows: IntRange,
        cols: IntRange,
    ): Boolean {
        val stamp = ++search
        homeStart = start
        tileIndex(start.row, start.col).let { index ->
            if (index >= 0) {
                reachedInSearch[index] = stamp
                stepsFromStart[index] = 0
            }
        }
        val targetIndex = tileIndex(target.row, target.col)
        var tail = 0

        fun reach(
            row: Int,
            col: Int,
            steps: Int,
        ) {
            if (row !in rows || col !in cols || !isOpenGround(row, col)) return
            val index = tileIndex(row, col)
            if (reachedInSearch[index] == stamp) return
            reachedInSearch[index] = stamp
            stepsFromStart[index] = steps
            queue[tail++] = index
        }
        for (direction in 0 until DIRECTIONS) {
            reach(start.row + ROW_STEPS[direction], start.col + COL_STEPS[direction], 1)
        }
        var head = 0
        while (head < tail && !(targetIndex >= 0 && reachedInSearch[targetIndex] == stamp)) {
            val tile = queue[head++]
            val row = tile / SIDE
            val col = tile % SIDE
            val steps = stepsFromStart[tile] + 1
            for (direction in 0 until DIRECTIONS) {
                reach(row + ROW_STEPS[direction], col + COL_STEPS[direction], steps)
            }
        }
        return targetIndex >= 0 && reachedInSearch[targetIndex] == stamp
    }

    /** How many steps the last search from home put a tile at; [Int.MAX_VALUE] if it did not reach it. */
    internal fun stepsFromHome(point: GridPoint): Int {
        if (point == homeStart) return 0
        val index = tileIndex(point.row, point.col)
        return if (index >= 0 && reachedInSearch[index] == search) stepsFromStart[index] else Int.MAX_VALUE
    }

    /** How many tiles, counting diagonals, a tile on the map is from the nearest crop or building; zero if there are none. */
    internal fun tilesToNearestAttraction(point: GridPoint): Int {
        val index = tileIndex(point.row, point.col)
        val known = nearestAttraction[index]
        if (known != UNKNOWN) return known
        var nearest = if (attractionRows.isEmpty()) 0 else Int.MAX_VALUE
        for (attraction in attractionRows.indices) {
            nearest =
                minOf(
                    nearest,
                    max(
                        abs(attractionRows[attraction] - point.row),
                        abs(attractionCols[attraction] - point.col),
                    ),
                )
        }
        nearestAttraction[index] = nearest
        return nearest
    }

    private companion object {
        const val UNKNOWN = -1
        const val UNKNOWN_GROUND: Byte = 0
        const val OPEN: Byte = 1
        const val CLOSED: Byte = 2
        const val DIRECTIONS = 4

        /** The same order as [GridPoint.neighbors]: above, below, left, right. */
        val ROW_STEPS = intArrayOf(-1, 1, 0, 0)
        val COL_STEPS = intArrayOf(0, 0, -1, 1)
    }
}
