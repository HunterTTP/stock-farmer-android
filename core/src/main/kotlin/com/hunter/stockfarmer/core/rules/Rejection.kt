package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText

/** Why an action was refused; [message] is the one text shown for a refusal, whether from a tap, a drag or a hold. */
sealed interface Rejection {
    data object TractorBusy : Rejection

    data object TractorNeedsRipeCrops : Rejection

    data object TractorCannotHarvestFruitTrees : Rejection

    data object TractorCannotReachCrops : Rejection

    data object TractorCannotReturnHome : Rejection

    data object TractorCargoWaiting : Rejection

    data class TractorHoldsCrop(
        val name: String,
    ) : Rejection

    data object OutOfBounds : Rejection

    data object NeedFarmland : Rejection

    data object AlreadyPlanted : Rejection

    data object AlreadyFertilized : Rejection

    data class NotFinishedGrowing(
        val cropName: String,
        val remaining: Double,
    ) : Rejection

    data object FishNotBiting : Rejection

    data object NothingToHarvest : Rejection

    data class NeedMoney(
        val cost: Double,
    ) : Rejection

    data class NeedLevel(
        val level: Int,
    ) : Rejection

    data class NeedLogs(
        val logs: Int,
    ) : Rejection

    data object UseTheAxe : Rejection

    data object UseThePickaxe : Rejection

    data class NeedStones(
        val stones: Int,
    ) : Rejection

    data object UseTheScythe : Rejection

    data object UseTheScytheForWool : Rejection

    data object UseTheBucketForFruit : Rejection

    data object UseTheBucketForEggs : Rejection

    data object UseTheBucketForAnimals : Rejection

    data object UseTheBucketForGoods : Rejection

    data object UseTheRodForFish : Rejection

    data object TooManyButterflies : Rejection

    data class NeedIngredients(
        val count: Int,
        val name: String,
    ) : Rejection

    data object WorkInProgress : Rejection

    data object GoodsWaiting : Rejection

    data object QuestIncomplete : Rejection

    data class NeedQuestCompletions(
        val count: Int,
    ) : Rejection

    data object AlreadyUpgraded : Rejection

    data object ToolFullyUpgraded : Rejection

    data object HomeFullyUpgraded : Rejection

    data object NeedGrass : Rejection

    data object StructureHere : Rejection

    data object CropReady : Rejection

    data object AlreadyPlaced : Rejection

    data object HouseOccupied : Rejection

    data object NeedsFrontTile : Rejection

    data object BlocksAFront : Rejection

    data class BuildingLimit(
        val name: String,
        val limit: Int,
    ) : Rejection

    data object NothingHere : Rejection

    data object NotEnoughSpace : Rejection

    data class FarmlandLimit(
        val placed: Int,
        val limit: Int,
    ) : Rejection

    data object StorageFull : Rejection

    data class NoneInStorage(
        val name: String,
    ) : Rejection

    data object NeedWaterForFish : Rejection

    data object WontEat : Rejection

    data object AlreadyFed : Rejection

    data object NeedAnimalToFeed : Rejection

    data class StorageInUse(
        val excess: Int,
    ) : Rejection

    data object InvalidQuantity : Rejection

    data class StockSlotsFull(
        val limit: Int,
    ) : Rejection

    data class NotEnoughShares(
        val owned: Long,
    ) : Rejection

    /** True when the rejection only means this one tile can't take the tool, so a drag skips the tile without a word. */
    val isTileSpecific: Boolean
        get() =
            when (this) {
                TractorBusy, TractorNeedsRipeCrops, TractorCannotHarvestFruitTrees, TractorCannotReachCrops,
                TractorCannotReturnHome, TractorCargoWaiting, is TractorHoldsCrop, is NeedQuestCompletions,
                AlreadyUpgraded, ToolFullyUpgraded, HomeFullyUpgraded, QuestIncomplete, is NeedMoney, is NeedLevel,
                is NeedLogs, is NeedStones, is NeedIngredients, WorkInProgress, GoodsWaiting, is FarmlandLimit,
                is BuildingLimit, HouseOccupied, NeedsFrontTile, StorageFull, TooManyButterflies, is NoneInStorage,
                is StorageInUse, InvalidQuantity, is StockSlotsFull, is NotEnoughShares,
                -> false

                else -> true
            }

    /** The Harvest tool implement the message tells the player to use, if it does. */
    val suggestedTool: HarvestTool?
        get() =
            when (this) {
                UseTheAxe -> HarvestTool.axe

                UseThePickaxe -> HarvestTool.pickaxe

                UseTheScythe, UseTheScytheForWool -> HarvestTool.scythe

                UseTheBucketForFruit, UseTheBucketForEggs, UseTheBucketForAnimals, UseTheBucketForGoods,
                GoodsWaiting,
                -> HarvestTool.bucket

                UseTheRodForFish -> HarvestTool.rod

                else -> null
            }

    val message: String
        get() =
            when (this) {
                TractorBusy -> {
                    "The Tractor is already harvesting or returning home"
                }

                TractorNeedsRipeCrops -> {
                    "None of this crop is ready to harvest yet"
                }

                TractorCannotHarvestFruitTrees -> {
                    "The Tractor cannot harvest fruit trees"
                }

                TractorCannotReachCrops -> {
                    "The Tractor cannot reach any remaining crops. Harvest canceled; returning home."
                }

                TractorCannotReturnHome -> {
                    "The Tractor cannot reach home. It has stopped here; this is its new home."
                }

                TractorCargoWaiting -> {
                    "Unload the Tractor before removing it"
                }

                is TractorHoldsCrop -> {
                    "Sell or store the Tractor's $name first"
                }

                OutOfBounds -> {
                    "Out of bounds"
                }

                NeedFarmland -> {
                    "Need farmland first"
                }

                AlreadyPlanted -> {
                    "Already planted"
                }

                AlreadyFertilized -> {
                    "Already fertilized"
                }

                is NotFinishedGrowing -> {
                    "$cropName is not finished growing\nGrow time remaining: ${remaining.durationText}"
                }

                FishNotBiting -> {
                    "No fish is swimming there; drag the rod over a fish"
                }

                NothingToHarvest -> {
                    "Nothing to harvest"
                }

                is NeedMoney -> {
                    "Need ${cost.dollars}"
                }

                InvalidQuantity -> {
                    "Enter a whole number of shares"
                }

                is StockSlotsFull -> {
                    "You can own at most $limit different stocks"
                }

                is NotEnoughShares -> {
                    "You own $owned ${if (owned == 1L) "share" else "shares"}"
                }

                is NeedLevel -> {
                    "Unlocks at Level $level"
                }

                is NeedLogs -> {
                    "Need $logs logs in storage"
                }

                is NeedStones -> {
                    "Need $stones stones in storage"
                }

                NeedGrass -> {
                    "A fruit tree needs free grass under its bottom row"
                }

                UseTheAxe -> {
                    "Use the Axe from the Harvest tool to remove a tree"
                }

                UseThePickaxe -> {
                    "Use the Pickaxe from the Harvest tool to break a rock"
                }

                UseTheScythe -> {
                    "Use the Scythe from the Harvest tool to harvest crops"
                }

                UseTheScytheForWool -> {
                    "Use the Scythe from the Harvest tool to shear sheep"
                }

                UseTheBucketForFruit -> {
                    "Use the Bucket from the Harvest tool to pick fruit"
                }

                UseTheBucketForEggs -> {
                    "Use the Bucket from the Harvest tool to collect eggs"
                }

                UseTheBucketForGoods -> {
                    "Use the Bucket from the Harvest tool to collect what it made"
                }

                UseTheRodForFish -> {
                    "Use the Fishing Rod from the Harvest tool to catch fish"
                }

                TooManyButterflies -> {
                    "At most ${Butterflies.MOST_RELEASED} butterflies can be released on the farm"
                }

                is NeedIngredients -> {
                    "Need $count $name in storage"
                }

                WorkInProgress -> {
                    "It is still at work"
                }

                GoodsWaiting -> {
                    "Collect what it made with the Bucket first"
                }

                QuestIncomplete -> {
                    "Finish every task before claiming this quest"
                }

                is NeedQuestCompletions -> {
                    "Complete $count quests to upgrade the Market Stand"
                }

                AlreadyUpgraded -> {
                    "This Market Stand is already upgraded"
                }

                ToolFullyUpgraded -> {
                    "This tool is fully upgraded"
                }

                HomeFullyUpgraded -> {
                    "This building is fully upgraded"
                }

                UseTheBucketForAnimals -> {
                    "Use the Bucket from the Harvest tool to collect what it has ready"
                }

                StructureHere -> {
                    "Structure here"
                }

                CropReady -> {
                    "A crop here is ready to harvest"
                }

                AlreadyPlaced -> {
                    "Already placed"
                }

                HouseOccupied -> {
                    "A pet already lives here"
                }

                NeedsFrontTile -> {
                    "Needs a free tile in front"
                }

                BlocksAFront -> {
                    "That would block the front of a pet house or coop"
                }

                is BuildingLimit -> {
                    if (limit == 1) "You can only place one $name" else "You can place at most $limit ${name}s"
                }

                NothingHere -> {
                    "Nothing here"
                }

                NotEnoughSpace -> {
                    "Not enough space"
                }

                StorageFull -> {
                    "Storage is full"
                }

                NeedWaterForFish -> {
                    "A fish needs free water"
                }

                WontEat -> {
                    "It doesn't want that"
                }

                AlreadyFed -> {
                    "It has just eaten"
                }

                NeedAnimalToFeed -> {
                    "Drag it onto an animal to feed it"
                }

                is NoneInStorage -> {
                    "You have no $name in storage"
                }

                is StorageInUse -> {
                    "Empty $excess ${if (excess == 1) "item" else "items"} from storage first to destroy this"
                }

                is FarmlandLimit -> {
                    "Farmland limit reached ($placed/$limit). More unlocks as you level up."
                }
            }
}
