package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.bottomRow

internal class TractorNavigation(
    state: GameState,
    home: GridPoint,
) {
    private data class Pose(
        val position: GridPoint,
        val facing: TractorFacing,
    )

    private val blocked: Set<GridPoint>
    private val farmland: Set<GridPoint> = state.farmland.toSet()

    init {
        val blocked = (state.water.keys + state.fences.keys + state.rocks.keys).toMutableSet()
        // Only the ground a thing takes up blocks the Tractor; the room its picture rises into can hold crops, so it is driven over.
        for (placed in state.placedBuildings.filter { it.origin != home }) blocked += placed.footprint.bottomRow
        for ((origin, tree) in state.trees) {
            blocked +=
                tree.tree
                    .footprint(origin)
                    .bottomRow
                    .filter { it.row != GameState.BACKDROP_ROW }
        }
        for ((origin, decoration) in state.decorations) blocked += decoration.decoration.footprint(origin).bottomRow
        for ((tile, plot) in state.plots.filter { (tile, plot) -> plot.isAnchor(tile) }) {
            val crop = Crop.named(plot.cropID)
            if (crop != null && crop.isFruitTree) blocked += crop.plotTiles(plot.origin ?: tile)
        }
        this.blocked = blocked
    }

    fun canEnter(
        head: GridPoint,
        facing: TractorFacing,
    ): Boolean = TractorRules.footprint(head, facing).all { GameState.contains(it) && it !in blocked }

    fun canStep(
        head: GridPoint,
        next: GridPoint,
        facing: TractorFacing? = null,
    ): Boolean {
        val heading = facing ?: TractorFacing.ofDirection(GridPoint(next.row - head.row, next.col - head.col))
        return canEnter(next, heading)
    }

    /**
     * The nearest crop, but first the nearest of those on the farmland connected to the tile the tractor is on, so a
     * plot is finished before the next one is started. Farmland is connected when its tiles touch along a side.
     */
    fun route(
        start: GridPoint,
        facing: TractorFacing,
        crops: Set<GridPoint>? = null,
        home: GridPoint? = null,
        homeFacing: TractorFacing = TractorFacing.right,
    ): List<GridPoint>? {
        if (crops != null) {
            val plot = crops.intersect(connectedFarmland(start))
            if (plot.isNotEmpty()) search(start, facing, plot, home, homeFacing)?.let { return it }
        }
        return search(start, facing, crops, home, homeFacing)
    }

    private fun connectedFarmland(tile: GridPoint): Set<GridPoint> {
        if (tile !in farmland) return emptySet()
        val region = mutableSetOf(tile)
        val pending = mutableListOf(tile)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            for (neighbor in current.neighbors) {
                if (neighbor in farmland && region.add(neighbor)) pending += neighbor
            }
        }
        return region
    }

    private fun search(
        start: GridPoint,
        facing: TractorFacing,
        crops: Set<GridPoint>?,
        home: GridPoint?,
        homeFacing: TractorFacing,
    ): List<GridPoint>? {
        val startPose = Pose(start, facing)
        val homeTiles =
            home?.let { TractorRules.footprint(TractorRules.headTile(it, homeFacing), homeFacing).toSet() }
        val queue = mutableListOf(startPose)
        val visited = mutableSetOf(startPose)
        val previous = mutableMapOf<Pose, Pose>()
        var index = 0
        while (index < queue.size) {
            val pose = queue[index]
            val tile = pose.position
            index += 1
            val arrived =
                if (homeTiles != null) {
                    TractorRules.footprint(tile, pose.facing).toSet() == homeTiles
                } else {
                    crops?.contains(tile) ?: false
                }
            if (arrived && canEnter(tile, pose.facing)) {
                val route = mutableListOf<GridPoint>()
                var cursor = pose
                while (cursor != startPose) {
                    route += cursor.position
                    cursor = previous[cursor] ?: return null
                }
                return route.reversed()
            }
            for (adjacent in tile.neighbors) {
                val heading = TractorFacing.ofDirection(GridPoint(adjacent.row - tile.row, adjacent.col - tile.col))
                val neighbor = TractorRules.nextHead(tile, heading)
                if (!canStep(tile, neighbor)) continue
                val next = Pose(neighbor, heading)
                if (visited.add(next)) {
                    previous[next] = pose
                    queue += next
                }
            }
        }
        return null
    }
}
