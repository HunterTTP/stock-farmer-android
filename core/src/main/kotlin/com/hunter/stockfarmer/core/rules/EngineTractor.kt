package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import com.hunter.stockfarmer.core.presentation.TractorMotion
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TRACTOR_SHOVE_RADIUS = 6

fun GameEngine.startTractorHarvest(
    home: GridPoint,
    cropID: String,
    now: Instant,
): List<GameEvent> {
    if (state.tractorRun != null) return reject(Rejection.TractorBusy)
    if (state.buildings[home] != "tractor1") return reject(Rejection.NothingHere)
    val crop =
        com.hunter.stockfarmer.core.catalog.Crop
            .named(cropID) ?: return reject(Rejection.NothingToHarvest)
    state.tractorCargo[home]?.keys?.sorted()?.firstOrNull()?.let(com.hunter.stockfarmer.core.catalog.Crop::named)?.let {
        return reject(Rejection.TractorHoldsCrop(it.produceName))
    }
    if (crop.isFruitTree) return reject(Rejection.TractorCannotHarvestFruitTrees)
    val plots = state.plots.filter { it.value.cropID == cropID }
    if (plots.isEmpty()) return reject(Rejection.NothingToHarvest)
    val conditions = GrowthConditions(state)
    val ripe = plots.filter { (tile, plot) -> conditions.isReady(plot, crop, tile, now) }
    if (ripe.isEmpty()) return reject(Rejection.TractorNeedsRipeCrops)
    val facing = TractorFacing.ofStyle(state.buildingStyles[home])
    val head = TractorRules.headTile(home, facing)
    val targets = ripe.keys.toSet()
    val route =
        TractorNavigation(state, home).route(head, facing, targets) ?: return reject(Rejection.TractorCannotReachCrops)
    val run = TractorRun(home = home, cropID = cropID, position = head, facing = facing, remaining = targets)
    reserveTractorTiles(planTractorStep(run, route.firstOrNull()))
    return emptyList()
}

fun GameEngine.cancelTractorHarvest(home: GridPoint): List<GameEvent> {
    val run = state.tractorRun
    if (run == null || run.home != home || run.returning) return emptyList()
    return planTractorReturn(run.copy(returning = true, remaining = emptySet()), TractorNavigation(state, home)).second
}

/** Advances only supplied active play time; no wall-clock catch-up is used for driving. */
fun GameEngine.advanceTractor(
    elapsed: Double,
    isActive: Boolean,
    now: Instant,
): List<GameEvent> {
    var run = state.tractorRun
    if (!isActive || !elapsed.isFinite() || elapsed <= 0 || run == null) return emptyList()
    val navigation = TractorNavigation(state, run.home)
    val homeFacing = TractorFacing.ofStyle(state.buildingStyles[run.home])
    val homeHead = TractorRules.headTile(run.home, homeFacing)
    val conditions = GrowthConditions(state)
    val currentRun = run
    run =
        run.copy(
            remaining =
                run.remaining
                    .filter { tile ->
                        val plot = state.plots[tile]
                        plot != null && plot.cropID == currentRun.cropID && conditions.isReady(plot, tile, now)
                    }.toSet(),
        )
    if (!run.returning && run.remaining.isEmpty()) run = planTractorStep(run.copy(returning = true), null)
    if (run.returning && (run.progress == 0.0 || !navigation.canEnter(homeHead, homeFacing))) {
        val (planned, events) = planTractorReturn(run, navigation)
        run = planned
        if (state.tractorRun == null) return events
    }
    val next = run.nextTile
    if (next != null) {
        if (navigation.canStep(run.position, next, run.nextPoseFacing)) {
            val duration = TractorMotion.duration(run)
            val progress =
                if (duration >
                    0
                ) {
                    min(1.0, run.progress + min(elapsed, TractorRules.SECONDS_PER_TILE) / duration)
                } else {
                    1.0
                }
            run = run.copy(progress = progress)
            if (run.progress < 1) {
                state.tractorRun = run
                return emptyList()
            }
            run = run.copy(facing = run.destinationFacing, position = next)
        }
        run = planTractorStep(run, null)
    }
    val events = mutableListOf<GameEvent>()
    val crop =
        com.hunter.stockfarmer.core.catalog.Crop
            .named(run.cropID)
    if (!run.returning && run.position in run.remaining && crop != null) {
        takeCrop(crop, run.position, now, earnsXP = false)
        recordQuestProgress(QuestGoal.Collect(crop.id))
        markHarvested()
        val cargo = state.tractorCargo[run.home] ?: emptyMap()
        state.tractorCargo[run.home] = cargo + (crop.id to ((cargo[crop.id] ?: 0) + 1))
        run = run.copy(remaining = run.remaining - run.position)
        events += GameEvent.TractorGathered(run.position)
    }
    if (run.remaining.isEmpty()) run = run.copy(returning = true)
    if (run.returning) return events + planTractorReturn(run, navigation).second
    val route = navigation.route(run.position, run.facing, run.remaining)
    if (route != null) {
        run = planTractorStep(run, route.firstOrNull())
    } else {
        run = run.copy(returning = true)
        events += GameEvent.Rejected(Rejection.TractorCannotReachCrops)
    }
    reserveTractorTiles(run)
    return events
}

private fun GameEngine.planTractorReturn(
    run: TractorRun,
    navigation: TractorNavigation,
): Pair<TractorRun, List<GameEvent>> {
    val homeFacing = TractorFacing.ofStyle(state.buildingStyles[run.home])
    val homeHead = TractorRules.headTile(run.home, homeFacing)
    val route =
        if (navigation.canEnter(homeHead, homeFacing)) {
            navigation.route(run.position, run.facing, home = run.home, homeFacing = homeFacing)
        } else {
            null
        }
    if (route == null) {
        parkTractorHere(run)
        return run to reject(Rejection.TractorCannotReturnHome)
    }
    var planned = run
    val next = run.nextTile
    if (run.progress > 0 && next != null && route.firstOrNull() != next) {
        // Retain both endpoint poses so reversing follows the same centered path.
        planned =
            run.copy(
                facing = run.destinationFacing,
                position = next,
                nextTile = run.position,
                nextPoseFacing = run.facing,
                progress = 1 - run.progress,
            )
    } else if (run.progress == 0.0) {
        val first = route.firstOrNull()
        if (first == null) {
            state.tractorRun = null
            return run to emptyList()
        }
        planned = planTractorStep(run, first)
    }
    reserveTractorTiles(planned)
    return planned to emptyList()
}

/** Keeps the tiles the Tractor drives over for it: any animal standing on them is moved to the nearest free tile. */
private fun GameEngine.reserveTractorTiles(run: TractorRun) {
    state.tractorRun = run
    val reserved = state.tractorOccupiedTiles
    for (animal in animals) {
        val current = position(animal) ?: continue
        if (current !in reserved) continue
        val spot = nearestFreeTile(current, animal) ?: continue
        setPosition(spot, animal)
    }
}

/** The nearest tile an animal could stand on that nothing else is on, within a few tiles of where it is. */
private fun GameEngine.nearestFreeTile(
    point: GridPoint,
    animal: AnimalRef,
): GridPoint? {
    for (radius in 1..TRACTOR_SHOVE_RADIUS) {
        val ring =
            (-radius..radius)
                .flatMap { rowOffset -> (-radius..radius).map { GridPoint(point.row + rowOffset, point.col + it) } }
                .filter { max(abs(it.row - point.row), abs(it.col - point.col)) == radius }
        ring
            .sortedWith(compareBy({ it.row }, { it.col }))
            .firstOrNull {
                canStand(it, animal) &&
                    animalStandingAt(it) == null
            }?.let { return it }
    }
    return null
}

private fun planTractorStep(
    run: TractorRun,
    next: GridPoint?,
): TractorRun = run.copy(nextTile = next, nextPoseFacing = null, progress = 0.0)

private fun GameEngine.parkTractorHere(run: TractorRun) {
    val origin = TractorRules.origin(run.position, run.facing)
    state.removeBuilding(run.home)
    state.putBuilding(origin, "tractor1", Building.named("tractor1")?.styled(run.facing.styleID)?.styleID)
    moveTractorCargo(run.home, origin)
    state.tractorRun = null
}
