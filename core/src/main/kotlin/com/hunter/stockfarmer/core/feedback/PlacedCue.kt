package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.rules.GameEvent

/** A sound and, when it was made on the farm, the tile it came from. */
data class PlacedCue(
    val cue: SoundCue,
    val tile: GridPoint? = null,
)

/**
 * One sound per kind of outcome, so a 5×5 harvester doesn't fire 25 overlapping sounds. A refusal is heard alone, and a
 * level-up's fanfare replaces other action cues.
 */
fun cuesFor(
    events: List<GameEvent>,
    landscapeTool: LandscapeTool,
    petSpecies: (GridPoint) -> PetSpecies?,
): List<PlacedCue> {
    val unique = mutableListOf<PlacedCue>()
    for (event in events) {
        val placed = actionCue(event)?.let { listOf(PlacedCue(it)) } ?: cuesOf(event, landscapeTool, petSpecies)
        for (cue in placed) if (unique.none { it.cue == cue.cue }) unique += cue
    }
    unique.firstOrNull { it.cue.isRefusal }?.let { return listOf(it) }
    return unique.firstOrNull { it.cue == SoundCue.levelUp }?.let { listOf(it) } ?: unique
}

private fun actionCue(event: GameEvent): SoundCue? =
    when (event) {
        is GameEvent.QuestCompleted -> SoundCue.questComplete
        is GameEvent.Stored -> cookingCue(event.itemID)
        is GameEvent.Sold -> cookingCue(event.itemID)
        else -> null
    }

private fun cookingCue(itemID: String): SoundCue? {
    if (itemID == "bread") return SoundCue.cookDone
    val item = StorageItem.of(itemID)
    return if (item is StorageItem.Artisan && item.good.buildingID == "stoneOven") SoundCue.cookDone else null
}

private fun cuesOf(
    event: GameEvent,
    landscapeTool: LandscapeTool,
    petSpecies: (GridPoint) -> PetSpecies?,
): List<PlacedCue> {
    fun at(
        point: GridPoint?,
        cue: SoundCue,
    ) = listOf(PlacedCue(cue, point))
    return when (event) {
        is GameEvent.Planted -> {
            at(event.point, SoundCue.plant)
        }

        is GameEvent.Fertilized -> {
            at(event.point, SoundCue.plant)
        }

        is GameEvent.WorkStarted -> {
            at(event.point, SoundCue.plant)
        }

        is GameEvent.FishHooked -> {
            at(event.point, SoundCue.fishingCast)
        }

        is GameEvent.Stored -> {
            at(event.point, SoundCue.cropPickup)
        }

        is GameEvent.TractorGathered -> {
            emptyList()
        }

        is GameEvent.TractorCargoStored -> {
            at(event.at, SoundCue.placeGround)
        }

        is GameEvent.TractorCargoSold -> {
            at(event.at, SoundCue.sell)
        }

        is GameEvent.StorageSold, is GameEvent.BoughtShares, is GameEvent.SoldShares -> {
            at(null, SoundCue.sell)
        }

        is GameEvent.Sold -> {
            at(event.point, SoundCue.sell)
        }

        is GameEvent.Placed -> {
            at(
                event.point,
                if (landscapeTool ==
                    LandscapeTool.water
                ) {
                    SoundCue.placeWater
                } else {
                    SoundCue.placeGround
                },
            )
        }

        is GameEvent.Built -> {
            at(event.point, SoundCue.built)
        }

        is GameEvent.HomeUpgraded -> {
            at(event.point, SoundCue.built)
        }

        is GameEvent.Moved, is GameEvent.CropEaten, is GameEvent.Fed, is GameEvent.Hatched, is GameEvent.Born -> {
            emptyList()
        }

        is GameEvent.Adopted -> {
            at(event.point, petCue(petSpecies(event.point)))
        }

        is GameEvent.Summoned -> {
            at(event.point, petCue(petSpecies(event.point)))
        }

        is GameEvent.CropRushed -> {
            at(event.point, petCue(petSpecies(event.point)))
        }

        is GameEvent.Fenced -> {
            at(event.point, SoundCue.placeGround)
        }

        is GameEvent.Decorated -> {
            at(event.point, SoundCue.placeGround)
        }

        is GameEvent.TreePlaced -> {
            at(event.point, SoundCue.placeGround)
        }

        is GameEvent.Unfenced -> {
            at(event.point, SoundCue.sell)
        }

        is GameEvent.Undecorated -> {
            at(event.point, SoundCue.sell)
        }

        is GameEvent.Demolished -> {
            at(event.point, SoundCue.sell)
        }

        is GameEvent.Chopped -> {
            if (event.isNewHit) at(event.point, SoundCue.destroy) else emptyList()
        }

        is GameEvent.Mined -> {
            if (event.isNewHit) at(event.point, SoundCue.destroy) else emptyList()
        }

        is GameEvent.StorageTrashed -> {
            at(
                null,
                if (StorageItem.of(event.itemID)?.discardTitle ==
                    "Release"
                ) {
                    SoundCue.release
                } else {
                    SoundCue.destroy
                },
            )
        }

        is GameEvent.AnimalSold -> {
            at(event.point, SoundCue.sell)
        }

        is GameEvent.AnimalReleased -> {
            at(event.point, SoundCue.release)
        }

        is GameEvent.LeveledUp -> {
            at(null, SoundCue.levelUp)
        }

        is GameEvent.QuestCompleted -> {
            at(null, SoundCue.questComplete)
        }

        is GameEvent.Destroyed -> {
            at(null, SoundCue.destroy)
        }

        is GameEvent.Rejected -> {
            at(null, event.rejection.cue)
        }
    }
}

private fun petCue(species: PetSpecies?): SoundCue =
    if (species ==
        PetSpecies.dog
    ) {
        SoundCue.dogBark
    } else {
        SoundCue.catTrill
    }
