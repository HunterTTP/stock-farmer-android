package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

/** How far the axe has got with one tree. */
internal class ChopPass(
    val origin: GridPoint,
    val hits: Int = 0,
    /** The last tile the axe touched, null once it has been lifted. */
    var lastTile: GridPoint? = null,
)

/**
 * The rules of the farm. The engine owns one [GameState] and changes it as actions are taken; to preview what an action
 * would do, run it on a [copy].
 */
class GameEngine(
    var state: GameState = GameState(),
) {
    internal var placement: BuildingPlacement? = null
    var fruitPlacement: FruitTreePlacement? = null
        internal set

    /** A decoration or tree being carried to a new spot. */
    var objectPlacement: ObjectPlacement? = null
        internal set

    /** The tree the axe is working on and how far it has got. */
    internal var chopPass: ChopPass? = null

    /**
     * Until when a stored thing that has just been set down stays quiet about there being none left, as the finger that
     * placed the last one is still on the farm.
     */
    internal var storagePlacementQuietUntil: Instant? = null

    /** A number from 0 up to but not including 1 for each chance the game takes, which tests replace to settle it. */
    internal var bonusRoll: () -> Double = { ThreadLocalRandom.current().nextDouble() }

    /** An independent engine in the same situation, so an action can be previewed without changing this one. */
    fun copy(): GameEngine =
        GameEngine(state.copy()).also {
            it.placement = placement
            it.fruitPlacement = fruitPlacement
            it.objectPlacement = objectPlacement
            it.chopPass = chopPass?.let { pass -> ChopPass(pass.origin, pass.hits, pass.lastTile) }
            it.storagePlacementQuietUntil = storagePlacementQuietUntil
            it.bonusRoll = bonusRoll
        }

    /** The chosen crop, or the starter crop if the chosen one is somehow still locked. */
    val selectedCrop: Crop
        get() = Crop.named(state.selectedCropID)?.takeIf { isUnlocked(it) } ?: Crop.starter

    /** Exactly one item is active at a time: the selection of the current mode's category. */
    fun isSelected(item: ShopItem): Boolean =
        when (item) {
            is ShopItem.Seed -> {
                state.mode == PlayMode.plant && state.selectedCropID == item.cropID
            }

            is ShopItem.GroundCoverItem -> {
                isSelected(LandscapeTool.groundCover(item.cover))
            }

            ShopItem.Farmland -> {
                isSelected(LandscapeTool.farmland)
            }

            ShopItem.Water -> {
                isSelected(LandscapeTool.water)
            }

            is ShopItem.BuildingItem -> {
                state.mode == PlayMode.build && state.selectedBuildingID == item.id
            }

            ShopItem.Fence -> {
                state.mode == PlayMode.decorate && state.selectedDecorationID == null
            }

            is ShopItem.DecorationItem -> {
                state.mode == PlayMode.decorate && state.selectedDecorationID == item.id
            }

            is ShopItem.Tree -> {
                state.mode == PlayMode.landscape && state.selectedLandscape == LandscapeTool.tree &&
                    selectedTree.species.id == item.id
            }

            ShopItem.Fertilizer -> {
                state.mode == PlayMode.fertilize
            }
        }

    fun isSelected(tool: LandscapeTool): Boolean = state.mode == PlayMode.landscape && state.selectedLandscape == tool

    fun setMode(mode: PlayMode) {
        state.mode = mode
        placement = null
        fruitPlacement = null
        objectPlacement = null
    }

    /** A tap on the map: a crop is always harvested or reported on, whatever is selected; anywhere else the selected tool is used. */
    fun tap(
        origin: GridPoint,
        now: Instant,
    ): List<GameEvent> {
        if (!GameState.contains(origin)) return reject(Rejection.OutOfBounds)
        if (isCropTap(origin)) return tapFarm(origin, now)
        return applySelectedMode(origin, now)
    }

    private fun applySelectedMode(
        origin: GridPoint,
        now: Instant,
    ): List<GameEvent> =
        when (state.mode) {
            PlayMode.plant -> {
                when {
                    selectedCrop.isFruitTree -> {
                        emptyList()
                    }

                    state.plots[origin] != null -> {
                        reject(missingHarvestToolRejection(origin, now) ?: Rejection.AlreadyPlanted)
                    }

                    else -> {
                        tapFarm(origin, now)
                    }
                }
            }

            PlayMode.harvest -> {
                emptyList()
            }

            PlayMode.landscape -> {
                tapLandscape(origin, now)
            }

            PlayMode.build -> {
                emptyList()
            }

            PlayMode.decorate -> {
                when {
                    state.heldItemID != null -> useHeldItem(origin, now)
                    else -> selectedDecoration?.let { tapDecoration(it, origin, now) } ?: tapFence(origin, now)
                }
            }

            PlayMode.fertilize -> {
                tapFertilizer(origin, now)
            }
        }

    /**
     * Uses the selected tool on one tile, as a tap or one step of a drag. Only the Harvest tool harvests; every other tool
     * leaves crops alone, and none of them undoes what is already there. Its scythe cuts crops and shears sheep, its axe
     * fells trees, its pickaxe breaks rocks, its bucket collects eggs, milk, fertilizer and what the workshops made, and its
     * fishing rod catches fish.
     */
    fun useTool(
        point: GridPoint,
        now: Instant,
    ): List<GameEvent> {
        if (!GameState.contains(point)) return reject(Rejection.OutOfBounds)
        if (state.mode == PlayMode.decorate && (point in state.fences || state.hasObstacle(point))) return emptyList()
        if (state.mode == PlayMode.landscape && state.selectedLandscape == LandscapeTool.tree &&
            state.hasObstacle(point)
        ) {
            return emptyList()
        }
        if (state.mode != PlayMode.harvest) return applySelectedMode(point, now)
        return when (state.harvestTool) {
            HarvestTool.scythe -> {
                val gathered = collectTractorCargo(point) + collectProduce(point, HarvestTool.scythe, now)
                gathered.ifEmpty { tapCrop(point, now) }
            }

            HarvestTool.rod -> {
                castRod(point)
            }

            HarvestTool.axe -> {
                chopTree(point, now)
            }

            HarvestTool.pickaxe -> {
                mineRock(point)
            }

            HarvestTool.bucket -> {
                if (isFishSwimming(point)) {
                    reject(Rejection.UseTheRodForFish)
                } else {
                    val gathered =
                        collectEggs(point, now) + collectProduce(point, HarvestTool.bucket, now) +
                            collectGoods(point, now)
                    gathered.ifEmpty { tapCrop(point, now) }
                }
            }
        }
    }

    /** Takes the cost out of the player's money, or says how much is needed if there isn't enough. */
    internal fun spend(cost: Double): Rejection? {
        if (state.money < cost) return Rejection.NeedMoney(cost)
        state.money -= cost
        return null
    }

    internal fun credit(amount: Double) {
        state.money = minOf(GameState.MAX_MONEY, state.money + amount)
    }

    companion object {
        /** How long a pond takes to bring a fish, in seconds. */
        const val FISH_INTERVAL = 30.0 * 60

        /** What destroying something refunds, as a share of what it cost. */
        const val DESTROY_REFUND_SHARE = 0.8
    }
}
