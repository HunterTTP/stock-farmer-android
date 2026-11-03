package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.TreeSize
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

/** Whatever the player plants starts as the smallest tree of the chosen species. */
val GameEngine.selectedTree: Tree get() = (Tree.named(state.selectedTreeID)?.species ?: Tree.starter.species).smallest

/** The tree a species is planted as. */
fun GameEngine.tree(speciesID: String): Tree? = TreeSpecies.named(speciesID)?.smallest

/** The chosen color if the selected tree comes in it, otherwise that tree's first color. */
val GameEngine.selectedTreeColor: TreeColor get() = selectedTree.color(state.selectedTreeColorID)

/** Picks a species and switches to placing trees. */
fun GameEngine.select(species: TreeSpecies) {
    if (species.unlockLevel > state.level) return
    state.selectedTreeID = species.smallest.id
    startPlacingTrees()
}

/** Picks a color for the trees being placed and switches to placing them. */
fun GameEngine.select(color: TreeColor) {
    state.selectedTreeColorID = color.id
    startPlacingTrees()
}

internal fun GameEngine.startPlacingTrees() = select(LandscapeTool.tree)

/** Whether the Harvest tool is holding the axe, which fells trees instead of asking about them. */
val GameEngine.isChopping: Boolean get() = state.mode == PlayMode.harvest && state.harvestTool == HarvestTool.axe

/** Whether the Harvest tool is holding the pickaxe, which breaks rocks instead of asking about them. */
val GameEngine.isMining: Boolean get() = state.mode == PlayMode.harvest && state.harvestTool == HarvestTool.pickaxe

/** The tiles the tool would use on when held over a tile: a tree's or fruit tree's whole ground, otherwise just the tile. */
fun GameEngine.toolFootprint(tile: GridPoint): List<GridPoint> {
    if (state.mode == PlayMode.plant) return selectedCrop.footprint(selectedCrop.origin(tile))
    val decoration = selectedDecoration
    if (state.mode == PlayMode.decorate && decoration != null) {
        return decoration.footprint(decoration.originSettingBottomLeftOn(tile))
    }
    if (state.mode != PlayMode.landscape || state.selectedLandscape != LandscapeTool.tree) return listOf(tile)
    return selectedTree.footprint(selectedTree.origin(tile))
}

/** Sets the selected tree down with its trunk on the tapped tile. */
internal fun GameEngine.tapTree(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val tree = selectedTree
    val origin = tree.origin(point)
    val ground = tree.footprint(origin)
    if (state.money < tree.cost) return listOf(GameEvent.Rejected(Rejection.NeedMoney(tree.cost)))
    if (!ground.bottomRow.all(::isFreeForTree)) return listOf(GameEvent.Rejected(Rejection.NotEnoughSpace))
    if (blocksAHomesFront(ground)) return listOf(GameEvent.Rejected(Rejection.BlocksAFront))
    state.money -= tree.cost
    state.putTree(origin, PlacedTree(tree, selectedTreeColor, now, isNaturallyGenerated = false))
    noteTerrain(CareerRecord.terrainID(tree, selectedTreeColor))
    recordQuestProgress(QuestGoal.PlantTrees)
    return listOf(GameEvent.TreePlaced(origin, tree.cost))
}

/**
 * Trees that have stood long enough at their size grow to the next, around the same trunk, if there is room. A tree left
 * too long to count every step takes them all; one that is crowded waits until space is cleared. Says whether any tree grew.
 */
fun GameEngine.growTrees(now: Instant): Boolean {
    var didGrow = false
    val carried = objectPlacement?.takeIf { it.obj is ObjectPlacement.Object.OfTree }?.from
    val due =
        state.trees.filter { (origin, placed) ->
            origin != carried && placed.tree.next != null && now.secondsSince(placed.grewAt) >= Tree.growthInterval
        }
    for (origin in due.keys.sortedWith(compareBy({ it.row }, { it.col }))) {
        var placed = due.getValue(origin)
        var position = origin
        while (true) {
            val next = placed.tree.next ?: break
            if (now.secondsSince(placed.grewAt) < Tree.growthInterval) break
            val newOrigin = next.origin(placed.tree.trunk(position))
            val ground = next.footprint(newOrigin)
            val own = placed.tree.footprint(position).toSet()
            if (!ground.all { it in own || isBareGround(it) } || blocksAHomesFront(ground)) break
            val grown =
                PlacedTree(
                    next,
                    placed.color,
                    placed.grewAt.advancedBy(Tree.growthInterval),
                    placed.isNaturallyGenerated,
                )
            state.growTreeUnsettled(position, newOrigin, grown)
            placed = grown
            position = newOrigin
            if (!placed.isNaturallyGenerated) noteTerrain(CareerRecord.terrainID(next, placed.color))
            didGrow = true
        }
    }
    if (didGrow) state.settleTrees()
    return didGrow
}

/** Ground nothing grows or is scattered on by itself: free for a tree, and with no tree of the top row on it either. */
internal fun GameEngine.isBareGround(point: GridPoint): Boolean =
    state.groundCover[point] == null && isFreeForTree(point) && isOpenGround(point)

/**
 * Ground free of water, farmland, crop, fence, rock and animal, and taken up by no building, tree or decoration, the room
 * under the pictures of those that rise over it not counting, nor does any tree of the top row.
 */
internal fun GameEngine.isFreeForTree(point: GridPoint): Boolean =
    isBuildableGround(point) && point !in state.farmland && point !in state.plots && animalStandingAt(point) == null

internal val youngWildTrees: List<Tree> by lazy {
    Tree.wild.filter {
        it.size == TreeSize.small ||
            it.size == TreeSize.medium
    }
}

/** Whether a tree could stand with its origin on a tile: bare grass under all of it, away from the start and the front of a home. */
internal fun GameEngine.canGrowWildTree(
    tree: Tree,
    origin: GridPoint,
): Boolean {
    val fronts = frontTilesOfHomes
    return tree.footprint(origin).all {
        GameState.contains(it) && GameState.isClearOfStart(it) && isBareGround(it) && it !in fronts
    }
}
