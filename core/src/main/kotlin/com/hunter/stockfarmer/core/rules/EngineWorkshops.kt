package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.Workshop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.QuestGoal
import java.time.Instant

/** What a workshop is doing right now. */
sealed interface WorkshopStatus {
    data object Idle : WorkshopStatus

    data class Working(
        val timeLeft: Double,
    ) : WorkshopStatus

    /** Finished, and waiting for the Bucket to collect what it made. */
    data object Ready : WorkshopStatus
}

fun GameState.workshopRecipe(origin: GridPoint): Recipe? {
    val building = buildings[origin]?.let(Building::named) ?: return null
    val original = Recipe.of(building) ?: return null
    val id = workshops[origin]?.recipeID ?: if (workshops[origin] != null) null else selectedWorkshopRecipes[origin]
    val recipe = id?.let(Recipe::named)
    return if (recipe == null || recipe.buildingID != building.id) original else recipe
}

fun GameEngine.workshopStatus(
    origin: GridPoint,
    now: Instant,
): WorkshopStatus {
    val recipe = state.workshopRecipe(origin) ?: return WorkshopStatus.Idle
    val workshop = state.workshops[origin] ?: return WorkshopStatus.Idle
    return if (workshop.isFinished(
            recipe,
            now,
        )
    ) {
        WorkshopStatus.Ready
    } else {
        WorkshopStatus.Working(workshop.timeLeft(recipe, now))
    }
}

private fun GameEngine.originalRecipe(origin: GridPoint): Recipe? =
    state.buildings[origin]?.let(Building::named)?.let(Recipe::of)

/** Why a workshop can't start a batch now, or null if it can. */
fun GameEngine.startRejection(
    origin: GridPoint,
    now: Instant,
): Rejection? {
    val recipe = state.workshopRecipe(origin) ?: return Rejection.NothingHere
    if (recipe.id != originalRecipe(origin)?.id &&
        state.level < recipe.unlockLevel
    ) {
        return Rejection.NeedLevel(recipe.unlockLevel)
    }
    when (workshopStatus(origin, now)) {
        is WorkshopStatus.Working -> return Rejection.WorkInProgress
        WorkshopStatus.Ready -> return Rejection.GoodsWaiting
        WorkshopStatus.Idle -> Unit
    }
    return if ((state.storage[recipe.input.id] ?: 0) >=
        recipe.inputCount
    ) {
        null
    } else {
        Rejection.NeedIngredients(recipe.inputCount, recipe.input.name)
    }
}

/** Takes what a batch needs out of storage and sets the workshop to work. */
fun GameEngine.startWork(
    origin: GridPoint,
    now: Instant,
): List<GameEvent> {
    val recipe = state.workshopRecipe(origin) ?: return emptyList()
    startRejection(origin, now)?.let { return reject(it) }
    removeFromStorage(recipe.input.id, recipe.inputCount)
    state.workshops[origin] = Workshop(now, if (recipe.id == originalRecipe(origin)?.id) null else recipe.id)
    countAsSeen(setOf("recipe:${recipe.id}"))
    return listOf(GameEvent.WorkStarted(origin))
}

/** The bucket takes what a finished workshop made, from any tile of the workshop, into storage as far as it fits. */
internal fun GameEngine.collectGoods(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val placed = state.building(point) ?: return emptyList()
    val recipe = state.workshopRecipe(placed.origin) ?: return emptyList()
    if (workshopStatus(placed.origin, now) != WorkshopStatus.Ready) return emptyList()
    if (!hasRoom(recipe.outputCount, HarvestTool.bucket)) return reject(Rejection.StorageFull)
    state.workshops.remove(placed.origin)
    noteMade(recipe)
    recordQuestProgress(QuestGoal.Workshop(placed.building.id))
    return (0 until recipe.outputCount).map { gather(recipe.output, point, HarvestTool.bucket) }
}

/** The workshop whose finished goods are on a tile, if any, for telling a player which tool takes them. */
internal fun GameEngine.finishedWorkshop(
    point: GridPoint,
    now: Instant,
): PlacedBuilding? = state.building(point)?.takeIf { workshopStatus(it.origin, now) == WorkshopStatus.Ready }

/** A workshop takes its work with it when it is moved. */
internal fun GameEngine.moveWorkshop(
    oldOrigin: GridPoint,
    newOrigin: GridPoint,
) {
    val recipe = state.selectedWorkshopRecipes.remove(oldOrigin)
    if (recipe !=
        null
    ) {
        state.selectedWorkshopRecipes[newOrigin] = recipe
    } else {
        state.selectedWorkshopRecipes.remove(newOrigin)
    }
    val workshop = state.workshops.remove(oldOrigin) ?: return
    state.workshops[newOrigin] = workshop
}

fun GameEngine.chooseRecipe(
    id: String,
    origin: GridPoint,
    now: Instant,
): List<GameEvent> {
    val building = state.buildings[origin]?.let(Building::named)
    val recipe = Recipe.named(id)
    if (building == null || recipe == null || recipe.buildingID != building.id) return reject(Rejection.NothingHere)
    val original = Recipe.of(building)
    if (recipe.id != original?.id &&
        state.level < recipe.unlockLevel
    ) {
        return reject(Rejection.NeedLevel(recipe.unlockLevel))
    }
    when (workshopStatus(origin, now)) {
        is WorkshopStatus.Working -> return reject(Rejection.WorkInProgress)
        WorkshopStatus.Ready -> return reject(Rejection.GoodsWaiting)
        WorkshopStatus.Idle -> Unit
    }
    if (recipe.id ==
        original?.id
    ) {
        state.selectedWorkshopRecipes.remove(origin)
    } else {
        state.selectedWorkshopRecipes[origin] =
            recipe.id
    }
    countAsSeen(setOf("recipe:${recipe.id}"))
    return emptyList()
}
