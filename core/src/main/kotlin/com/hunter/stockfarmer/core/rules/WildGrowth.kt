package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.OreVein
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Rock
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.randomElement
import com.hunter.stockfarmer.core.util.shuffled
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The tiles the midnight wave has passed over so far, nearest to where it set off first. Each tile is handed out once, as
 * the front of the wave reaches its middle, which is when it may grow something wild.
 */
class WildGrowthSweep(
    row: Double,
    column: Double,
) {
    private val reach = NightPulse.reach(row, column)
    private val tiles: List<Pair<GridPoint, Double>> =
        (0 until GameState.GRID_SIZE)
            .flatMap { tileRow ->
                (0 until GameState.GRID_SIZE).map { tileColumn ->
                    GridPoint(tileRow, tileColumn) to hypot(tileRow + 0.5 - row, tileColumn + 0.5 - column)
                }
            }.sortedBy { it.second }
    private var passed = 0

    /** The tiles the front has newly reached by this point of the wave's roll. */
    fun tilesReached(progress: Double): List<GridPoint> {
        val radius = NightPulse.radius(progress, reach)
        val start = passed
        while (passed < tiles.size && tiles[passed].second <= radius) passed += 1
        return tiles.subList(start, passed).map { it.first }
    }
}

/**
 * What one night's wave will leave on the map, worked out when the night begins: each thing and the tile it is to grow on,
 * which the wave grows when its front reaches that tile.
 */
class WildGrowthPlan {
    sealed interface Item {
        data class OfTree(
            val tree: Tree,
            val color: TreeColor,
        ) : Item

        data class OfRock(
            val size: RockSize,
        ) : Item
    }

    internal val items = mutableMapOf<GridPoint, Item>()

    val tiles: Set<GridPoint> get() = items.keys
    val isEmpty: Boolean get() = items.isEmpty()
}

/** Every night leaves this many young trees, stones and ore veins, as far as the map has room and the stone and vein limits allow. */
internal val NIGHTLY_TREES = 5..7
internal val NIGHTLY_ROCKS = 2..3
internal val NIGHTLY_ORE_VEINS = 1..1

/**
 * The map is cut into this many slices round its middle and the night's things are dealt out among them in turn, so they
 * are spread evenly all the way round rather than gathering in one place.
 */
internal const val WILD_SECTORS = 8
private const val WILD_TILES_TRIED = 600

/**
 * Plans a night's growth: the trees, rocks and ores of the night, each in the next slice of the map round its middle, on a
 * tile chosen with the odds of the edge weight, so they lie mostly toward the edge of the map.
 */
fun GameEngine.planWildGrowth(using: RandomSource): WildGrowthPlan {
    val plan = WildGrowthPlan()
    val stones = state.rocks.values.count { it.ore == null }
    val veins = oreVeinCount
    val items = mutableListOf<WildGrowthPlan.Item?>()
    repeat(using.nextInt(NIGHTLY_TREES)) {
        items +=
            youngWildTrees.randomElement(using)?.let { tree ->
                tree.species.colors
                    .randomElement(using)
                    ?.let { WildGrowthPlan.Item.OfTree(tree, it) }
            }
    }
    repeat(min(using.nextInt(NIGHTLY_ROCKS), max(0, Rock.onANewMap.last - stones))) {
        items += WildGrowthPlan.Item.OfRock(randomSize(using))
    }
    repeat(min(using.nextInt(NIGHTLY_ORE_VEINS), max(0, OreVein.maximumOnMap - veins))) {
        items += randomOreSize(using)?.let { WildGrowthPlan.Item.OfRock(it) }
    }
    val dealt = items.shuffled(using)
    val first = using.nextInt(0 until WILD_SECTORS)
    val taken = mutableSetOf<GridPoint>()
    for ((index, item) in dealt.withIndex()) {
        if (item == null) continue
        for (step in 0 until WILD_SECTORS) {
            val tile = wildTile(item, (first + index + step) % WILD_SECTORS, taken, using) ?: continue
            plan.items[tile.first] = item
            taken += tile.second
            break
        }
    }
    return plan
}

/** Which slice of the map round its middle a tile is in. */
internal fun wildSector(point: GridPoint): Int {
    val middle = (GameState.GRID_SIZE - 1) / 2.0
    val angle = atan2(point.row - middle, point.col - middle)
    return min(WILD_SECTORS - 1, ((angle + Math.PI) / (2 * Math.PI) * WILD_SECTORS).toInt())
}

/** A tile of a slice where the item could grow and nothing else is planned, drawn with the odds of the edge weight. */
private fun GameEngine.wildTile(
    item: WildGrowthPlan.Item,
    sector: Int,
    taken: Set<GridPoint>,
    using: RandomSource,
): Pair<GridPoint, List<GridPoint>>? {
    repeat(WILD_TILES_TRIED) {
        val origin = GridPoint(using.nextInt(0 until GameState.GRID_SIZE), using.nextInt(0 until GameState.GRID_SIZE))
        if (wildSector(origin) != sector || using.nextUnit() >= edgeWeight(origin)) return@repeat
        val ground = groundOf(item, origin)
        if (ground.any { it in taken } || !canGrow(item, origin)) return@repeat
        return origin to ground
    }
    return null
}

private fun groundOf(
    item: WildGrowthPlan.Item,
    origin: GridPoint,
): List<GridPoint> = if (item is WildGrowthPlan.Item.OfTree) item.tree.footprint(origin) else listOf(origin)

private fun GameEngine.canGrow(
    item: WildGrowthPlan.Item,
    origin: GridPoint,
): Boolean =
    when (item) {
        is WildGrowthPlan.Item.OfTree -> canGrowWildTree(item.tree, origin)
        is WildGrowthPlan.Item.OfRock -> isFreeForRock(origin)
    }

/** Grows what the plan has for each tile the wave has just reached, if the ground is still free for it. Says which tiles grew something. */
fun GameEngine.growWild(
    tiles: List<GridPoint>,
    plan: WildGrowthPlan,
    now: Instant,
): List<GridPoint> {
    val grew = mutableListOf<GridPoint>()
    for (tile in tiles) {
        val item = plan.items[tile] ?: continue
        if (!canGrow(item, tile)) continue
        when (item) {
            is WildGrowthPlan.Item.OfTree -> state.putTree(tile, PlacedTree(item.tree, item.color, now))
            is WildGrowthPlan.Item.OfRock -> state.rocks[tile] = item.size
        }
        grew += tile
    }
    return grew
}
