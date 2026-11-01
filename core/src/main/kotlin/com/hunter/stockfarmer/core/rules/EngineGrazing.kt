package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.eatsToProduce
import com.hunter.stockfarmer.core.model.favoriteCrop
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant

/**
 * What an animal likes best, which it grazes from the field or is fed by hand; null for any that has none: pets, roosters,
 * and animals that give nothing to harvest.
 */
fun GameEngine.favoriteCrop(animal: AnimalRef): Crop? =
    when (animal) {
        is AnimalRef.Pet -> {
            null
        }

        is AnimalRef.Chicken -> {
            chicken(
                animal.id,
                animal.coop,
            )?.let { if (it.kind.isRooster) null else Chicken.favoriteCrop }
        }

        is AnimalRef.Livestock -> {
            livestock(animal.id, animal.home)?.species?.favoriteCrop
        }
    }

fun GameEngine.meal(animal: AnimalRef): AnimalMeal? =
    when (animal) {
        is AnimalRef.Pet -> state.pets[animal.house]?.meal
        is AnimalRef.Chicken -> chicken(animal.id, animal.coop)?.meal
        is AnimalRef.Livestock -> livestock(animal.id, animal.home)?.meal
    }

/**
 * Whether an animal has eaten since it last gave something to harvest, which is what lets it give the next; it is not
 * hungry again, and does not graze, until it has been harvested.
 */
fun GameEngine.hasEaten(animal: AnimalRef): Boolean =
    when (animal) {
        is AnimalRef.Pet -> false
        is AnimalRef.Chicken -> chicken(animal.id, animal.coop)?.hasEaten ?: false
        is AnimalRef.Livestock -> livestock(animal.id, animal.home)?.hasEaten ?: false
    }

/**
 * Whether an animal gives something to harvest (milk, wool or eggs), which is what makes it need to eat: it gives none
 * until it has, and not again after each harvest until it has again. Ducks, pets, roosters and chicks need no food.
 */
fun GameEngine.producesHarvest(
    animal: AnimalRef,
    now: Instant,
): Boolean =
    when (animal) {
        is AnimalRef.Pet -> {
            false
        }

        is AnimalRef.Chicken -> {
            chicken(animal.id, animal.coop)?.let { !it.kind.isRooster && it.isGrown(now) } ?: false
        }

        is AnimalRef.Livestock -> {
            livestock(
                animal.id,
                animal.home,
            )?.let { it.species.work is LivestockWork.Gives && it.species.eatsToProduce }
                ?: false
        }
    }

/**
 * Whether an animal that has not eaten since it was last harvested has gone two days without, counted from the later of
 * its birth, its last meal and its last harvest: the time it shows its hunger.
 */
fun GameEngine.isHungry(
    animal: AnimalRef,
    now: Instant,
): Boolean {
    if (!producesHarvest(animal, now) || hasEaten(animal)) return false
    val since: List<Instant?> =
        when (animal) {
            is AnimalRef.Pet -> {
                emptyList()
            }

            is AnimalRef.Chicken -> {
                chicken(animal.id, animal.coop)?.let { listOf(it.hatchedAt, it.meal?.eatenAt, it.lastHarvestedAt) }
                    ?: emptyList()
            }

            is AnimalRef.Livestock -> {
                livestock(animal.id, animal.home)?.let { listOf(it.bornAt, it.meal?.eatenAt, it.lastWorkedAt) }
                    ?: emptyList()
            }
        }
    val latest = since.filterNotNull().maxOrNull() ?: return false
    return now >= latest.advancedBy(AnimalMeal.hungerDelay)
}

internal fun GameEngine.canGraze(
    animal: AnimalRef,
    now: Instant,
): Boolean {
    if (!producesHarvest(animal, now) || hasEaten(animal) || isInside(animal, now)) return false
    return when (animal) {
        is AnimalRef.Pet -> false
        is AnimalRef.Chicken -> state.eggs.count { it.coop == animal.coop && it.henID == animal.id } < Egg.MAX_PER_HEN
        is AnimalRef.Livestock -> livestock(animal.id, animal.home)?.readyProduce(now) == null
    }
}

/** One serving of a ripe planting is a meal, and a tile has four; it earns no harvest money, inventory or XP. */
fun GameEngine.eatFavoriteCrops(now: Instant): List<GameEvent> {
    val conditions = GrowthConditions(state)
    val events = mutableListOf<GameEvent>()
    for (animal in animals.sortedWith(compareBy({ it.home.row }, { it.home.col }, { it.kindOrder }, { it.animalID }))) {
        if (!canGraze(animal, now)) continue
        val position = position(animal) ?: continue
        val favorite = favoriteCrop(animal) ?: continue
        val point =
            (listOf(position) + position.neighbors).firstOrNull { edibleFavorite(it, favorite, now, conditions) }
                ?: continue
        takeServing(point)
        giveMeal(animal, now)
        recordFavoriteEaten(animal)
        events += GameEvent.CropEaten(point, animal)
    }
    return events
}

/** An animal ate one of a tile's servings: the tile is gone, with its fertilizer, once the last is taken. */
private fun GameEngine.takeServing(point: GridPoint) {
    val plot = state.plots[point] ?: return
    val eaten = plot.servingsEaten + 1
    if (eaten >= Plot.SERVINGS_PER_TILE) {
        state.plots.remove(point)
        state.fertilized.remove(point)
    } else {
        state.plots[point] = plot.copy(servingsEaten = eaten)
    }
}

/** An animal has just eaten: its next minutes are boosted, and it starts giving again. */
internal fun GameEngine.giveMeal(
    animal: AnimalRef,
    now: Instant,
) {
    val meal = AnimalMeal(now)
    when (animal) {
        is AnimalRef.Pet -> state.pets[animal.house]?.let { state.pets[animal.house] = it.copy(meal = meal) }
        is AnimalRef.Chicken -> updateChicken(animal.coop, animal.id) { it.copy(meal = meal) }
        is AnimalRef.Livestock -> updateLivestock(animal.home, animal.id) { it.copy(meal = meal) }
    }
}

/** An animal has eaten its favorite crop, which counts toward the quests that ask for it. */
internal fun GameEngine.recordFavoriteEaten(animal: AnimalRef) {
    state.buildings[animal.home]?.let { recordQuestProgress(QuestGoal.Feed(it), 1) }
}

private fun GameEngine.edibleFavorite(
    point: GridPoint,
    crop: Crop,
    now: Instant,
    conditions: GrowthConditions,
): Boolean {
    val plot = state.plots[point] ?: return false
    if (plot.cropID != crop.id || crop.isFruitTree || !isOpenGround(point)) return false
    return conditions.isReady(plot, crop, point, now)
}

/** Follow reachable food, keeping shelter ahead of grazing. */
internal fun GameEngine.directionToFavoriteCrop(
    animal: AnimalRef,
    now: Instant,
): GridPoint? {
    if (!canGraze(animal, now)) return null
    val current = position(animal) ?: return null
    val favorite = favoriteCrop(animal) ?: return null
    val conditions = GrowthConditions(state)
    val food =
        state.plots.keys
            .filter { edibleFavorite(it, favorite, now, conditions) }
            .toSet()
    if (food.isEmpty()) return null
    val seen = mutableSetOf(current)
    val queue = mutableListOf<Pair<GridPoint, GridPoint?>>(current to null)
    var index = 0
    while (index < queue.size) {
        val (point, firstStep) = queue[index]
        index += 1
        if (point in food || point.neighbors.any { it in food }) return firstStep
        for (neighbor in point.neighbors) {
            if (canStand(neighbor, animal) && seen.add(neighbor)) {
                queue += neighbor to (firstStep ?: GridPoint(neighbor.row - current.row, neighbor.col - current.col))
            }
        }
    }
    return null
}

private val AnimalRef.kindOrder: Int
    get() =
        when (this) {
            is AnimalRef.Pet -> 0
            is AnimalRef.Chicken -> 1
            is AnimalRef.Livestock -> 2
        }

private val AnimalRef.animalID: Int
    get() =
        when (this) {
            is AnimalRef.Pet -> 0
            is AnimalRef.Chicken -> id
            is AnimalRef.Livestock -> id
        }
