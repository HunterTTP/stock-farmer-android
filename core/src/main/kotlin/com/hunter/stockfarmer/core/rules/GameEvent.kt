package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GridPoint

/** An animal in a building: a pet, a chicken at a coop, or a livestock animal at its home. */
sealed interface AnimalRef {
    /** The origin of the building the animal lives in. */
    val home: GridPoint

    data class Pet(
        val house: GridPoint,
    ) : AnimalRef {
        override val home: GridPoint get() = house
    }

    data class Chicken(
        val coop: GridPoint,
        val id: Int,
    ) : AnimalRef {
        override val home: GridPoint get() = coop
    }

    data class Livestock(
        override val home: GridPoint,
        val id: Int,
    ) : AnimalRef
}

/** What an action did. Refusals are [Rejected]; their message is the one text shown. */
sealed interface GameEvent {
    data class TractorGathered(
        val point: GridPoint,
    ) : GameEvent

    /** Unloaded with the Scythe from the tile at [at], which is where the harvest flies from; null when unloaded from the menu. */
    data class TractorCargoStored(
        val itemID: String,
        val quantity: Int,
        val at: GridPoint? = null,
        val xp: Int = 0,
    ) : GameEvent

    data class TractorCargoSold(
        val itemID: String,
        val quantity: Int,
        val value: Double,
        val at: GridPoint? = null,
        val xp: Int = 0,
    ) : GameEvent

    /** A fish bites at the rod; it is landed or lost in a `FishingContest`. */
    data class FishHooked(
        val point: GridPoint,
    ) : GameEvent

    data class Planted(
        val point: GridPoint,
        val cost: Double,
    ) : GameEvent

    data class Fertilized(
        val point: GridPoint,
        val cost: Double,
    ) : GameEvent

    data class Stored(
        val point: GridPoint,
        val itemID: String,
        val xp: Int,
    ) : GameEvent

    data class Sold(
        val point: GridPoint,
        val itemID: String,
        val value: Double,
        val xp: Int,
    ) : GameEvent

    data class StorageTrashed(
        val itemID: String,
        val quantity: Int,
    ) : GameEvent

    data class StorageSold(
        val itemID: String,
        val quantity: Int,
        val value: Double,
    ) : GameEvent

    data class LeveledUp(
        val level: Int,
        val unlockedCrops: List<String>,
        val farmlandTiles: Int,
        val from: Int,
    ) : GameEvent

    data class Placed(
        val point: GridPoint,
    ) : GameEvent

    data class Built(
        val point: GridPoint,
        val cost: Double,
    ) : GameEvent

    data class Adopted(
        val point: GridPoint,
    ) : GameEvent

    data class Summoned(
        val point: GridPoint,
    ) : GameEvent

    /** An animal was fed by hand from storage. */
    data class Fed(
        val animal: AnimalRef,
    ) : GameEvent

    /** An animal ate a ripe crop from the field. */
    data class CropEaten(
        val point: GridPoint,
        val by: AnimalRef,
    ) : GameEvent

    data class WorkStarted(
        val point: GridPoint,
    ) : GameEvent

    data class HomeUpgraded(
        val point: GridPoint,
        val level: Int,
        val cost: Double,
    ) : GameEvent

    /** A market quest was turned in; [summary] names what it asked for. */
    data class QuestCompleted(
        val xp: Int,
        val summary: String,
    ) : GameEvent

    data class Fenced(
        val point: GridPoint,
        val cost: Double,
    ) : GameEvent

    data class TreePlaced(
        val point: GridPoint,
        val cost: Double,
    ) : GameEvent

    data class Chopped(
        val point: GridPoint,
        val treeOrigin: GridPoint,
        val hits: Int,
        /** The passes that fell the tree; the pass that fells it counts to its own total. */
        val total: Int,
        val isNewHit: Boolean,
    ) : GameEvent

    data class Mined(
        val point: GridPoint,
        val hits: Int,
        /** The passes that break the rock; the pass that breaks it counts to its own total. */
        val total: Int,
        val isNewHit: Boolean,
    ) : GameEvent

    data class Unfenced(
        val point: GridPoint,
        val refund: Double,
    ) : GameEvent

    data class Decorated(
        val point: GridPoint,
        val cost: Double,
    ) : GameEvent

    data class Undecorated(
        val point: GridPoint,
        val refund: Double,
    ) : GameEvent

    data class Hatched(
        val point: GridPoint,
    ) : GameEvent

    data class AnimalReleased(
        val point: GridPoint,
    ) : GameEvent

    data class AnimalSold(
        val point: GridPoint,
        val value: Double,
    ) : GameEvent

    data class Born(
        val point: GridPoint,
    ) : GameEvent

    data class CropRushed(
        val point: GridPoint,
    ) : GameEvent

    data class Demolished(
        val point: GridPoint,
        val refund: Double,
    ) : GameEvent

    data class Moved(
        val point: GridPoint,
    ) : GameEvent

    data class Destroyed(
        val count: Int,
    ) : GameEvent

    data class BoughtShares(
        val ticker: String,
        val shares: Long,
        val total: Double,
    ) : GameEvent

    data class SoldShares(
        val ticker: String,
        val shares: Long,
        val total: Double,
    ) : GameEvent

    data class Rejected(
        val rejection: Rejection,
    ) : GameEvent
}

val List<GameEvent>.firstRejection: Rejection? get() = filterIsInstance<GameEvent.Rejected>().firstOrNull()?.rejection

val List<GameEvent>.containsRejection: Boolean get() = firstRejection != null
