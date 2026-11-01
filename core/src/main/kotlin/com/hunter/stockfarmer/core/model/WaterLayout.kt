package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.WaterStyle

/** Connected water bodies and the tiles surrounded on all eight sides by water. */
class WaterLayout(
    water: Map<GridPoint, WaterStyle>,
) {
    class Body(
        tiles: List<GridPoint>,
        interior: List<GridPoint>,
    ) {
        val tiles: List<GridPoint> = java.util.List.copyOf(tiles)
        val interior: List<GridPoint> = java.util.List.copyOf(interior)
    }

    val bodies: List<Body>
    val bodyIndexByTile: Map<GridPoint, Int>
    val interior: Set<GridPoint>

    init {
        val inside =
            water.keys
                .filter { tile ->
                    (-1..1).all { row -> (-1..1).all { col -> GridPoint(tile.row + row, tile.col + col) in water } }
                }.toSet()
        val found = mutableListOf<Body>()
        val indices = mutableMapOf<GridPoint, Int>()
        val order = compareBy<GridPoint> { it.row }.thenBy { it.col }
        for (start in water.keys.sortedWith(order)) {
            if (start in indices) continue
            val index = found.size
            val tiles = mutableListOf(start)
            indices[start] = index
            val frontier = mutableListOf(start)
            while (frontier.isNotEmpty()) {
                val tile = frontier.removeAt(frontier.lastIndex)
                val neighbors =
                    listOf(
                        GridPoint(tile.row - 1, tile.col),
                        GridPoint(tile.row + 1, tile.col),
                        GridPoint(
                            tile.row,
                            tile.col - 1,
                        ),
                        GridPoint(tile.row, tile.col + 1),
                    )
                for (next in neighbors) {
                    if (next !in water || next in indices) continue
                    indices[next] = index
                    tiles.add(next)
                    frontier.add(next)
                }
            }
            tiles.sortWith(order)
            found.add(Body(tiles, tiles.filter { it in inside }))
        }
        bodies = java.util.List.copyOf(found)
        bodyIndexByTile = java.util.Map.copyOf(indices)
        interior = java.util.Set.copyOf(inside)
    }
}

/** Shared by game copies, but checked against the water on every lookup. */
class WaterLayoutCache {
    private var remembered: Pair<Map<GridPoint, WaterStyle>, WaterLayout>? = null

    @Synchronized
    fun layout(water: Map<GridPoint, WaterStyle>): WaterLayout {
        val snapshot = water.toMap()
        remembered?.let { if (it.first == snapshot) return it.second }
        val layout = WaterLayout(snapshot)
        remembered = snapshot to layout
        return layout
    }

    override fun equals(other: Any?): Boolean = other is WaterLayoutCache

    override fun hashCode(): Int = 0
}
