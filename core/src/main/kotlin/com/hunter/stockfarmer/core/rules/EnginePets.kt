package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.cleanedName
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextUnit
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/** Each second a pet has spent on a growing crop adds this much to its chance, per second, of finishing the crop. */
internal const val CROP_RUSH_CHANCE_GROWTH = 0.002
internal const val CROP_RUSH_MAX_CHANCE_PER_SECOND = 0.03

fun GameEngine.pet(house: GridPoint): Pet? = state.pets[house]

/** Moves a pet into an empty pet house; it appears in front of the house. */
fun GameEngine.movePetIn(
    house: GridPoint,
    breed: PetBreed,
    name: String,
): List<GameEvent> {
    val placed = state.building(house)
    if (placed == null || placed.origin != house || !placed.building.isPetHouse) return emptyList()
    if (state.pets[house] != null) return reject(Rejection.HouseOccupied)
    val spot = spawnTile(placed) ?: return reject(Rejection.NotEnoughSpace)
    state.pets[house] = Pet(breed.species, breed.id, name.cleanedName(Pet.MAX_NAME_LENGTH, breed.name), spot)
    noteWorked(breed.id)
    return listOf(GameEvent.Adopted(spot))
}

/** Brings a pet back to the front of its house, from where it wanders off as usual. */
fun GameEngine.summonPet(house: GridPoint): List<GameEvent> {
    val pet = state.pets[house] ?: return emptyList()
    val placed = state.building(house) ?: return emptyList()
    val spot = spawnTile(placed, AnimalRef.Pet(house)) ?: return reject(Rejection.NotEnoughSpace)
    state.pets[house] = pet.copy(position = spot)
    return listOf(GameEvent.Summoned(spot))
}

fun GameEngine.renamePet(
    house: GridPoint,
    name: String,
) {
    val pet = state.pets[house] ?: return
    state.pets[house] = pet.copy(name = name.cleanedName(Pet.MAX_NAME_LENGTH, pet.kind.name))
}

/** A pet follows its house when it is moved. */
internal fun GameEngine.movePet(
    oldHouse: GridPoint,
    newHouse: GridPoint,
) {
    val pet = state.pets.remove(oldHouse) ?: return
    state.pets[newHouse] = pet
    val house = state.building(newHouse) ?: return
    spawnTile(house)?.let { state.pets[newHouse] = pet.copy(position = it) }
}

/**
 * A pet standing on a growing crop, but never a fruit tree, and never while it is in its house, may make it ripen at once;
 * the longer it has stayed, the likelier it is. Call it once per tick with the time since the previous tick.
 */
fun GameEngine.petTendsCrop(
    house: GridPoint,
    dwell: Double,
    interval: Double,
    now: Instant,
    using: RandomSource,
): List<GameEvent> {
    if (isInside(AnimalRef.Pet(house), now)) return emptyList()
    val pet = state.pets[house] ?: return emptyList()
    val point = pet.position
    val plot = state.plots[point] ?: return emptyList()
    val crop = Crop.named(plot.cropID) ?: return emptyList()
    if (crop.isFruitTree || GrowthConditions(state).isReady(plot, crop, point, now)) return emptyList()
    val productiveSeconds = pet.meal?.productiveSeconds(interval, now) ?: max(0.0, interval)
    val chance = min(CROP_RUSH_MAX_CHANCE_PER_SECOND, CROP_RUSH_CHANCE_GROWTH * dwell) * productiveSeconds
    if (using.nextUnit() >= chance) return emptyList()
    for (tile in cropsToDestroy(point)) {
        state.plots[tile] = Plot(plot.cropID, now.advancedBy(-crop.growDuration), plot.origin)
    }
    return listOf(GameEvent.CropRushed(point))
}
