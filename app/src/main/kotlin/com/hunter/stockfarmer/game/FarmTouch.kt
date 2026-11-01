package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.beginPlacing
import com.hunter.stockfarmer.core.rules.beginPlacingFruitTree
import com.hunter.stockfarmer.core.rules.beginPlacingSelectedDecoration
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.cancelPlacement
import com.hunter.stockfarmer.core.rules.cannotStartPlacingSelectedBuilding
import com.hunter.stockfarmer.core.rules.cannotStartPlacingSelectedDecoration
import com.hunter.stockfarmer.core.rules.cannotStartPlacingSelectedFruitTree
import com.hunter.stockfarmer.core.rules.confirmPlacement
import com.hunter.stockfarmer.core.rules.containsRejection
import com.hunter.stockfarmer.core.rules.isChopping
import com.hunter.stockfarmer.core.rules.isFruitTreeSelected
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.isPlacingFirstOfItsKind
import com.hunter.stockfarmer.core.rules.isSprinklerSelected
import com.hunter.stockfarmer.core.rules.liftTool
import com.hunter.stockfarmer.core.rules.movePlacement
import com.hunter.stockfarmer.core.rules.nudgePlacement
import com.hunter.stockfarmer.core.rules.placementRejection
import com.hunter.stockfarmer.core.rules.tapCrop
import com.hunter.stockfarmer.farm.animals.animalAtTouch
import com.hunter.stockfarmer.ui.fishing.FishingCast
import com.hunter.stockfarmer.ui.sheets.SheetTarget
import com.hunter.stockfarmer.ui.sheets.SheetTargets
import java.time.Instant

/** What a touch on a tile does: carry a thing being placed there, start placing the selected one, or use the tool. */
fun GameSession.touch(
    point: GridPoint,
    now: Instant = Instant.now(),
): List<GameEvent> =
    act { engine ->
        when {
            engine.isPlacing -> emptyList()
            else -> tapTile(engine, point, now)
        }
    }.let { publish(it) }

private fun GameSession.tapTile(
    engine: GameEngine,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val target = SheetTargets.at(engine, point)
    if (target is SheetTarget.Building) return openBuilding(engine, target.origin, point, now)
    if (askAboutLandscape(engine, point, now)) return emptyList()
    toolGuidance(engine, point, now)?.let { return it }
    val butterflies = sceneFeedback.catchButterfly?.invoke(point, now).orEmpty()
    if (butterflies.isNotEmpty()) return flash(listOf(point), butterflies, now)
    val crops = engine.tapCrop(point, now)
    if (crops.isNotEmpty()) return toolFlash(engine, point, crops, now)
    if (target is SheetTarget.Animal) return interactWithAnimal(target.animal, now)
    engine.tryPlaceOnTap(point)?.let { return it }
    return engine.useTool(point, now).also {
        engine.liftTool()
        hookFish(engine, it)
        if (it.isNotEmpty()) toolFlash(engine, point, it, now)
    }
}

private fun GameEngine.tryPlaceOnTap(point: GridPoint): List<GameEvent>? =
    when {
        state.mode == PlayMode.build -> {
            cannotStartPlacingSelectedBuilding?.let { return listOf(GameEvent.Rejected(it)) }
            beginPlacing(point)
            emptyList()
        }

        isFruitTreeSelected -> {
            cannotStartPlacingSelectedFruitTree?.let { return listOf(GameEvent.Rejected(it)) }
            beginPlacingFruitTree(point)
            emptyList()
        }

        state.mode == PlayMode.decorate && isSprinklerSelected -> {
            cannotStartPlacingSelectedDecoration?.let { return listOf(GameEvent.Rejected(it)) }
            beginPlacingSelectedDecoration(point)
            emptyList()
        }

        else -> {
            null
        }
    }

internal fun GameSession.flash(
    tiles: List<GridPoint>,
    events: List<GameEvent>,
    now: Instant,
): List<GameEvent> = events.also { sceneFeedback.flash(tiles, !events.containsRejection, now) }

private fun GameSession.toolFlash(
    engine: GameEngine,
    point: GridPoint,
    events: List<GameEvent>,
    now: Instant,
): List<GameEvent> = events.also { if (!engine.isChopping) flash(listOf(point), it, now) }

/** Scene hit testing uses the interpolated center, before tile-based actions, including for a hold. */
fun GameSession.touchAnimalAt(
    x: Float,
    y: Float,
    now: Instant = Instant.now(),
): Boolean =
    act { engine ->
        if (engine.isPlacing) return@act false
        val animal = animalAtTouch(x, y, animalGestures.targets) ?: return@act false
        publish(interactWithAnimal(animal, now))
        true
    }

internal fun GameSession.hookFish(
    engine: GameEngine,
    events: List<GameEvent>,
) {
    if (casts.value == null) FishingCast.start(engine, events)?.let { casts.value = it }
}

internal fun GameEngine.startsPlacing(point: GridPoint): Boolean =
    when {
        state.mode == PlayMode.build -> {
            beginPlacing(point)
            isPlacing
        }

        isFruitTreeSelected -> {
            beginPlacingFruitTree(point)
            isPlacing
        }

        state.mode == PlayMode.decorate && isSprinklerSelected -> {
            beginPlacingSelectedDecoration(point)
            isPlacing
        }

        else -> {
            false
        }
    }

/** Puts down whatever is being placed, or says why it cannot be. */
fun GameSession.confirmPlacement(now: Instant = Instant.now()): List<GameEvent> =
    publish(
        act { engine ->
            val first = engine.buildingPlacement?.takeIf { engine.isPlacingFirstOfItsKind }
            engine.confirmPlacement(now).also { events ->
                if (first != null && events.any { it is GameEvent.Built }) {
                    val building = first.building
                    celebrations.celebrate(BuildingCelebration(first.origin, building.width, building.height))
                }
            }
        },
    )

fun GameSession.cancelPlacement(now: Instant = Instant.now()) =
    act { engine ->
        val footprint =
            engine.buildingPlacement?.replacing?.footprint
                ?: engine.fruitPlacement?.replacing?.let { engine.fruitPlacement?.crop?.footprint(it.origin) }
                ?: engine.objectPlacement?.let { obj -> obj.from?.let { obj.footprint(it) } }
        engine.cancelPlacement()
        if (footprint != null) {
            sceneFeedback.flash(footprint, false, now)
        }
    }

fun GameSession.nudgePlacement(
    rows: Int,
    cols: Int,
) = act { it.nudgePlacement(rows, cols) }

/** Where the thing being placed would go, and whether it can go there; null when nothing is being placed. */
fun GameSession.preview(now: Instant = Instant.now()): PlacementPreview? = placementPreview(now) ?: view.toolPreview

private fun GameSession.placementPreview(now: Instant): PlacementPreview? =
    act { engine ->
        val footprint =
            engine.buildingPlacement?.footprint
                ?: engine.fruitPlacement?.footprint
                ?: engine.objectPlacement?.footprint
        footprint?.let {
            PlacementPreview(
                it,
                engine.placementRejection(now) == null,
                engine.buildingPlacement?.building,
            )
        }
    }

/** The tiles a thing being placed would cover, and the building to show over them when it is one. */
class PlacementPreview(
    val footprint: List<GridPoint>,
    val canPlace: Boolean,
    val building: Building?,
)
