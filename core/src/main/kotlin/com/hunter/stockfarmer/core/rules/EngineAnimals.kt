package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.cleanedName
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextUnit
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

private const val SPAWN_SEARCH_RADIUS = 3
private const val ESCAPE_SEARCH_RADIUS = 12

/** Farm animals keep this many tiles from a driving Tractor. */
internal const val TRACTOR_FLEE_RADIUS = 3

/** A tile with chickens on it is this much as appealing to another chicken. */
private const val CROWDED_TILE_APPEAL = 0.15

fun GameEngine.position(animal: AnimalRef): GridPoint? =
    when (animal) {
        is AnimalRef.Pet -> state.pets[animal.house]?.position
        is AnimalRef.Chicken -> chicken(animal.id, animal.coop)?.position
        is AnimalRef.Livestock -> livestock(animal.id, animal.home)?.position
    }

internal fun GameEngine.setPosition(
    position: GridPoint,
    animal: AnimalRef,
) {
    when (animal) {
        is AnimalRef.Pet -> {
            state.pets[animal.house]?.let { state.pets[animal.house] = it.copy(position = position) }
        }

        is AnimalRef.Chicken -> {
            updateChicken(animal.coop, animal.id) { it.copy(position = position) }
            state.eggs =
                state.eggs.map {
                    if (it.coop == animal.coop &&
                        it.henID == animal.id
                    ) {
                        it.copy(point = position)
                    } else {
                        it
                    }
                }
        }

        is AnimalRef.Livestock -> {
            updateLivestock(animal.home, animal.id) { it.copy(position = position) }
        }
    }
}

internal fun GameEngine.updateChicken(
    coop: GridPoint,
    id: Int,
    change: (Chicken) -> Chicken,
) {
    val current = state.coops[coop] ?: return
    state.coops[coop] = current.copy(chickens = current.chickens.map { if (it.id == id) change(it) else it })
}

internal fun GameEngine.updateLivestock(
    home: GridPoint,
    id: Int,
    change: (Livestock) -> Livestock,
) {
    val current = state.herds[home] ?: return
    state.herds[home] = current.copy(animals = current.animals.map { if (it.id == id) change(it) else it })
}

fun GameEngine.name(animal: AnimalRef): String? =
    when (animal) {
        is AnimalRef.Pet -> state.pets[animal.house]?.name
        is AnimalRef.Chicken -> chicken(animal.id, animal.coop)?.name
        is AnimalRef.Livestock -> livestock(animal.id, animal.home)?.name
    }

/** Every animal on the map. */
val GameEngine.animals: List<AnimalRef>
    get() =
        state.pets.keys.map { AnimalRef.Pet(it) } +
            state.coops.flatMap { (coop, coopState) -> coopState.chickens.map { AnimalRef.Chicken(coop, it.id) } } +
            state.herds.flatMap { (home, herd) -> herd.animals.map { AnimalRef.Livestock(home, it.id) } }

/** The animal standing on a tile. */
fun GameEngine.animalStandingAt(point: GridPoint): AnimalRef? = animals.firstOrNull { position(it) == point }

fun GameEngine.rename(
    animal: AnimalRef,
    name: String,
) {
    when (animal) {
        is AnimalRef.Pet -> {
            renamePet(animal.house, name)
        }

        is AnimalRef.Chicken -> {
            updateChicken(animal.coop, animal.id) {
                it.copy(name = name.cleanedName(Chicken.MAX_NAME_LENGTH, Chicken.DEFAULT_NAME))
            }
        }

        is AnimalRef.Livestock -> {
            val livestock = livestock(animal.id, animal.home) ?: return
            updateLivestock(animal.home, animal.id) {
                it.copy(name = name.cleanedName(Livestock.MAX_NAME_LENGTH, livestock.species.name))
            }
        }
    }
}

/** The tiles in front of pet houses and coops, where their animals appear. */
internal val GameEngine.frontTilesOfHomes: Set<GridPoint> get() = state.homeFrontTiles

internal fun GameEngine.blocksAHomesFront(tiles: List<GridPoint>): Boolean = tiles.any { it in state.homeFrontTiles }

internal fun isChicken(animal: AnimalRef): Boolean = animal is AnimalRef.Chicken

/** Whether an animal may stand on a tile: open ground for any animal, except ducks, which stay on water. */
internal fun GameEngine.canStand(
    point: GridPoint,
    mover: AnimalRef?,
): Boolean {
    if (point in state.tractorOccupiedTiles) return false
    if (mover != null && isWaterfowl(mover)) return point in state.water && state.decoration(point) == null
    return isOpenGround(point)
}

/**
 * Ground with no water or fence on it and no picture over any of it, which animals may walk on: not under a building,
 * tree or decoration, even where only the room its picture rises into lies, and not under a backdrop tree.
 */
internal fun GameEngine.isOpenGround(point: GridPoint): Boolean =
    isBuildableGround(point) && state.building(point) == null && state.tree(point) == null &&
        state.decoration(point) == null

/**
 * Ground with no water or fence on it and nothing taking it up: not the bottom row of a building, tree or decoration, nor
 * a rock, the rest of their ground being room for other things to be set down over.
 */
internal fun GameEngine.isBuildableGround(point: GridPoint): Boolean =
    GameState.contains(point) && point !in state.water && point !in state.fences &&
        state.buildingBlocking(point) == null && !state.hasObstacle(point)

/** How many steps, crossing anything, it is from a tile to the nearest open ground; zero if it is open already. */
private fun GameEngine.stepsToOpenGround(point: GridPoint): Int? {
    var frontier = listOf(point)
    val seen = mutableSetOf(point)
    for (steps in 0..ESCAPE_SEARCH_RADIUS) {
        if (frontier.any(::isOpenGround)) return steps
        frontier = frontier.flatMap { it.neighbors }.filter { GameState.contains(it) && seen.add(it) }
    }
    return null
}

/**
 * An animal standing where it shouldn't be, such as inside a building, may cross tiles on its way out, taking the way that
 * is nearest to open ground. Nothing else ever steps onto a building, water or fence.
 */
private fun GameEngine.escapeSteps(
    animal: AnimalRef,
    current: GridPoint,
): List<GridPoint> {
    if (isWaterfowl(animal) || isOpenGround(current)) return emptyList()
    val distance = stepsToOpenGround(current) ?: return emptyList()
    return current.neighbors.filter { neighbor ->
        GameState.contains(neighbor) && (stepsToOpenGround(neighbor)?.let { it < distance } ?: false)
    }
}

/** The chickens on a tile, which other chickens would rather not join. */
internal fun GameEngine.chickenCount(point: GridPoint): Int = animals.count { isChicken(it) && position(it) == point }

/** The tile directly in front of a coop, if nothing is blocking it; chicks only ever hatch there. */
internal fun GameEngine.hatchTile(
    home: PlacedBuilding,
    mover: AnimalRef,
): GridPoint? {
    val front = home.building.frontTile(home.origin)
    return if (canStand(front, mover)) front else null
}

/** The tile in front of a building (below it), or the nearest free one. */
internal fun GameEngine.spawnTile(
    building: PlacedBuilding,
    mover: AnimalRef? = null,
): GridPoint? {
    val front = building.building.frontTile(building.origin)
    for (radius in 0..SPAWN_SEARCH_RADIUS) {
        val ring =
            (-radius..radius)
                .flatMap { rowOffset -> (-radius..radius).map { GridPoint(front.row + rowOffset, front.col + it) } }
                .filter { max(abs(it.row - front.row), abs(it.col - front.col)) == radius }
        ring.sortedWith(compareBy({ it.row }, { it.col })).firstOrNull { canStand(it, mover) }?.let { return it }
    }
    return null
}

/** An animal whose tile becomes water or part of a building walks back to the front of its home. */
internal fun GameEngine.relocateAnimals(points: List<GridPoint>) {
    for (animal in animals) {
        val current = position(animal) ?: continue
        if (current !in points) continue
        val home = state.building(animal.home) ?: continue
        val spot = spawnTile(home) ?: continue
        setPosition(spot, animal)
    }
}

/**
 * Picks which way an animal sets off: one of the four directions (as a row and column offset) with a free tile next to it
 * favoring those toward buildings and crops. Null if it is boxed in.
 */
fun GameEngine.wanderDirection(
    animal: AnimalRef,
    now: Instant,
    using: RandomSource,
): GridPoint? {
    val current = position(animal) ?: return null
    val escape = escapeSteps(animal, current)
    (escape.firstOrNull { canStand(it, animal) } ?: escape.firstOrNull())?.let {
        return GridPoint(it.row - current.row, it.col - current.col)
    }
    if (isFarmAnimal(animal)) {
        val gap = tractorGap(current)
        if (gap != null && gap <= TRACTOR_FLEE_RADIUS) return directionAwayFromTractor(animal, current, gap)
    }
    if (needsShelter(now) && shelterTile(animal) != null) return directionToShelter(animal, current)
    directionToFavoriteCrop(animal, now)?.let { return it }
    val attractions = animalAttractions(state)
    val options =
        current.neighbors.filter { canStand(it, animal) }.map {
            GridPoint(it.row - current.row, it.col - current.col) to appeal(it, animal, attractions)
        }
    var roll = using.nextUnit() * max(options.sumOf { it.second }, java.lang.Double.MIN_VALUE)
    for ((direction, appeal) in options) {
        roll -= appeal
        if (roll < 0) return direction
    }
    return options.lastOrNull()?.first
}

/** Chickens and livestock keep away from the Tractor; pets do not. */
private fun isFarmAnimal(animal: AnimalRef): Boolean = animal !is AnimalRef.Pet

/** How many tiles a tile is from the nearest tile the driving Tractor takes up, counting diagonals, if it is driving. */
private fun GameEngine.tractorGap(point: GridPoint): Int? =
    state.tractorOccupiedTiles.minOfOrNull { max(abs(it.row - point.row), abs(it.col - point.col)) }

/** The step to the open tile farthest from the Tractor, if it is farther than where the animal stands; null to stay put. */
private fun GameEngine.directionAwayFromTractor(
    animal: AnimalRef,
    current: GridPoint,
    gap: Int,
): GridPoint? {
    val options =
        current.neighbors.mapNotNull { tile ->
            if (!canStand(tile, animal)) return@mapNotNull null
            val tileGap = tractorGap(tile) ?: return@mapNotNull null
            if (tileGap > gap) tile to tileGap else null
        }
    return options.maxByOrNull { it.second }?.let { GridPoint(it.first.row - current.row, it.first.col - current.col) }
}

/** Moves an animal one tile in a direction, unless something is in the way or it would leave its range. Returns where it went. */
fun GameEngine.move(
    animal: AnimalRef,
    direction: GridPoint,
): GridPoint? {
    val current = position(animal) ?: return null
    val destination = GridPoint(current.row + direction.row, current.col + direction.col)
    if (destination in state.tractorOccupiedTiles) return null
    if (destination in escapeSteps(animal, current)) {
        setPosition(destination, animal)
        return destination
    }
    if (!canStand(destination, animal)) return null
    setPosition(destination, animal)
    return destination
}

/**
 * How much an animal likes a tile: halves with every tile of distance from the nearest building or crop, and a chicken
 * likes a tile that other chickens already stand on far less.
 */
private fun GameEngine.appeal(
    point: GridPoint,
    animal: AnimalRef,
    attractions: List<GridPoint>,
): Double {
    // Ducks are free to go anywhere on the water.
    if (isWaterfowl(animal)) return 1.0
    val nearest = attractions.minOfOrNull { max(abs(it.row - point.row), abs(it.col - point.col)) } ?: 0
    val base = max(0.5.pow(nearest), 0.001)
    return if (isChicken(animal) && chickenCount(point) > 0) base * CROWDED_TILE_APPEAL else base
}

internal fun animalAttractions(state: GameState): List<GridPoint> =
    state.plots.keys.toList() + state.placedBuildings.flatMap { it.footprint }
