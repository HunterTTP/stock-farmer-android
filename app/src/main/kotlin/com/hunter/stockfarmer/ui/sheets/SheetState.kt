package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.TractorCropSummary
import com.hunter.stockfarmer.core.rules.WorkshopStatus
import com.hunter.stockfarmer.core.rules.animals
import com.hunter.stockfarmer.core.rules.chicken
import com.hunter.stockfarmer.core.rules.demolishRejection
import com.hunter.stockfarmer.core.rules.farmlandLimit
import com.hunter.stockfarmer.core.rules.favoriteCrop
import com.hunter.stockfarmer.core.rules.homeCapacity
import com.hunter.stockfarmer.core.rules.homeLevel
import com.hunter.stockfarmer.core.rules.homeUpgradeRejection
import com.hunter.stockfarmer.core.rules.houseUpgradeRejection
import com.hunter.stockfarmer.core.rules.livestock
import com.hunter.stockfarmer.core.rules.materialRefund
import com.hunter.stockfarmer.core.rules.meal
import com.hunter.stockfarmer.core.rules.name
import com.hunter.stockfarmer.core.rules.nextHomeUpgrade
import com.hunter.stockfarmer.core.rules.refund
import com.hunter.stockfarmer.core.rules.saleValue
import com.hunter.stockfarmer.core.rules.startRejection
import com.hunter.stockfarmer.core.rules.stockSlotLimit
import com.hunter.stockfarmer.core.rules.stockSlotsUsed
import com.hunter.stockfarmer.core.rules.storageCapacity
import com.hunter.stockfarmer.core.rules.storedCount
import com.hunter.stockfarmer.core.rules.storedItems
import com.hunter.stockfarmer.core.rules.timeUntilGrown
import com.hunter.stockfarmer.core.rules.tractorCropOverview
import com.hunter.stockfarmer.core.rules.workshopRecipe
import com.hunter.stockfarmer.core.rules.workshopStatus
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

internal data class RecipeChoice(
    val recipe: Recipe,
    val selected: Boolean,
    val requiredLevel: Int?,
    val isNew: Boolean = false,
)

internal data class WorkshopState(
    val recipe: Recipe,
    val choices: List<RecipeChoice>,
    val inputStored: Int,
    val outputStored: Int,
    val status: WorkshopStatus,
    val startRefusal: String?,
) {
    val statusText: String get() =
        when (status) {
            WorkshopStatus.Idle -> "Idle: a batch takes ${recipe.duration.durationText}"
            is WorkshopStatus.Working -> "Working: ${status.timeLeft.durationText} left"
            WorkshopStatus.Ready -> "Finished: collect it with the Bucket from the Harvest tool"
        }
}

internal data class UpgradeState(
    val title: String,
    val detail: String,
    val refusal: String?,
    val requiredLevel: Int? = null,
    val affordable: Boolean = true,
)

internal data class TractorCropState(
    val summary: TractorCropSummary,
    val sale: String,
    val canHarvest: Boolean,
)

internal data class TractorState(
    val farmland: String,
    val run: String?,
    val canCancel: Boolean,
    val crops: List<TractorCropState>,
)

internal data class StoredState(
    val item: StorageItem,
    val count: Int,
    val sale: String,
)

internal data class BuildingState(
    val placed: PlacedBuilding,
    val workshop: WorkshopState?,
    val resident: AnimalState?,
    val homeMessage: String?,
    val homeLevel: String?,
    val upgrade: UpgradeState?,
    val animals: List<AnimalState>,
    val nextChick: String?,
    val tractor: TractorState?,
    val storage: String?,
    val stored: List<StoredState>,
    val stocks: String?,
    val demolishRefusal: String?,
    val refund: String,
    val moving: Boolean,
)

internal data class AnimalState(
    val ref: AnimalRef,
    val name: String,
    val description: List<String>,
    val releasable: Boolean,
    val foods: List<StoredState>,
    val pet: Pet? = null,
    val chicken: ChickenVariant? = null,
    val livestockVariant: String? = null,
)

/** All strings and action availability are derived with an explicit time, independently of Compose. */
internal data class SheetState(
    val money: String,
    val level: Int,
    val building: BuildingState? = null,
    val animal: AnimalState? = null,
    val fish: PondFish? = null,
) {
    companion object {
        fun from(
            engine: GameEngine,
            target: SheetTarget,
            now: Instant,
        ): SheetState =
            SheetState(
                engine.state.money.dollars,
                engine.state.level,
                building = (target as? SheetTarget.Building)?.let { building(engine, it.origin, now) },
                animal = (target as? SheetTarget.Animal)?.let { animal(engine, it.animal, now) },
                fish = (target as? SheetTarget.Fish)?.let { engine.state.fishPonds[it.pond]?.fish },
            )

        private fun building(
            engine: GameEngine,
            origin: GridPoint,
            now: Instant,
        ): BuildingState? {
            val placed = engine.state.placedBuildings.firstOrNull { it.origin == origin } ?: return null
            val building = placed.building
            val animals = engine.animals.filter { it.home == origin }.mapNotNull { animal(engine, it, now) }
            val capacity = engine.homeCapacity(origin)
            val coop = engine.state.coops[origin]
            val herd = engine.state.herds[origin]
            val nextChick = coop?.timeUntilNextChick(now, capacity)?.let { "Next chick in ${it.durationText}" }
            val nextBaby = herd?.timeUntilNextBirth(now, capacity)?.let { "Next baby in ${it.durationText}" }
            val message =
                when {
                    coop != null -> "Chickens: ${coop.chickens.size}/$capacity\n${nextChick ?: "Full"}"
                    herd != null -> "Animals: ${herd.animals.size}/$capacity\n${nextBaby ?: "Full"}"
                    else -> null
                }
            val next = engine.nextHomeUpgrade(origin)
            val upgrade =
                when {
                    building.isHouse && !building.isUpgradedHouse -> {
                        UpgradeState(
                            "Upgrade to Luxury House · ${StockMarketRules.HOUSE_UPGRADE_COST.dollars}",
                            "${StockMarketRules.UPGRADED_SLOT_LIMIT} stock slots · " +
                                "Unlocks at Level ${StockMarketRules.HOUSE_UPGRADE_LEVEL}",
                            engine.houseUpgradeRejection(origin, now)?.message,
                        )
                    }

                    next != null -> {
                        UpgradeState(
                            "Upgrade to Level ${next.level} · ${next.cost.dollars}",
                            "${HomeUpgrade.capacity(building.id, next.level)} animals · " +
                                "Unlocks at Level ${next.unlockLevel}",
                            engine.homeUpgradeRejection(origin)?.message,
                            next.unlockLevel.takeIf { it > engine.state.level },
                            engine.state.money >= next.cost,
                        )
                    }

                    else -> {
                        null
                    }
                }
            val recipe = engine.state.workshopRecipe(origin)
            val workshop =
                recipe?.let {
                    WorkshopState(
                        it,
                        recipeChoices(engine, origin),
                        engine.state.storage[it.input.id] ?: 0,
                        engine.state.storage[it.output.id] ?: 0,
                        engine.workshopStatus(origin, now),
                        engine.startRejection(origin, now)?.message,
                    )
                }
            val run = engine.state.tractorRun
            val tractor =
                if (building.id == "tractor1") {
                    TractorState(
                        "Farmland tiles used: ${engine.state.farmland.size} / ${engine.state.farmlandLimit}",
                        run?.takeIf { it.home == origin }?.let {
                            if (it.returning) {
                                "Tractor returning home"
                            } else {
                                "Tractor harvesting ${Crop.named(it.cropID)?.name ?: it.cropID}"
                            }
                        },
                        run?.home == origin && !run.returning,
                        engine.tractorCropOverview(origin, now).map {
                            TractorCropState(
                                it,
                                engine.saleValue(StorageItem.CropItem(it.crop), it.gathered).dollars,
                                it.gathered == 0 && it.readyCount > 0 && !it.crop.isFruitTree && run == null,
                            )
                        },
                    )
                } else {
                    null
                }
            val (logs, stones) = engine.materialRefund(building)
            val storage =
                building.storageCapacity?.let {
                    "Adds $it storage · ${engine.storedCount} / ${engine.storageCapacity} used"
                }
            val stored =
                if (building.isStorage) {
                    engine.storedItems.map { (item, count) ->
                        StoredState(item, count, engine.saleValue(item, count).dollars)
                    }
                } else {
                    emptyList()
                }
            return BuildingState(
                placed,
                workshop,
                animals.firstOrNull { it.ref is AnimalRef.Pet },
                message,
                if (building.isChickenCoop || building.livestockHome != null) {
                    "Level ${engine.homeLevel(origin)} of ${HomeUpgrade.maxLevel} · $capacity animals"
                } else {
                    null
                },
                upgrade,
                animals,
                coop?.nextVariantID,
                tractor,
                storage,
                stored,
                if (building.isHouse) "Stock slots: ${engine.stockSlotsUsed} / ${engine.stockSlotLimit}" else null,
                engine.demolishRejection(placed)?.message,
                "Refund: ${engine.refund(building).dollars}, $logs Logs, $stones Stones",
                run?.home == origin,
            )
        }

        fun animal(
            engine: GameEngine,
            ref: AnimalRef,
            now: Instant,
        ): AnimalState? {
            val name = engine.name(ref) ?: return null
            val chicken = (ref as? AnimalRef.Chicken)?.let { engine.chicken(it.id, it.coop) }
            val livestock = (ref as? AnimalRef.Livestock)?.let { engine.livestock(it.id, it.home) }
            val pet = (ref as? AnimalRef.Pet)?.let { engine.state.pets[it.house] }
            val favorite = engine.favoriteCrop(ref)
            val lines =
                buildList {
                    chicken?.let {
                        val growth =
                            if (it.isGrown(
                                    now,
                                )
                            ) {
                                "(Fully grown)"
                            } else {
                                "Grows up in ${it.timeUntilGrown(now).durationText}"
                            }
                        add(growth)
                    }
                    livestock?.let {
                        if (!it.isGrown(now)) {
                            add("Grows up in ${engine.timeUntilGrown(it, now).durationText}")
                        } else {
                            add("(Fully grown)")
                            val work = it.species.work as LivestockWork.Gives
                            val wait =
                                it.readyAt
                                    .secondsSince(now)
                                    .coerceAtLeast(0.0)
                                    .durationText
                            val readiness =
                                if (it.readyProduce(now) != null) {
                                    "${work.item.name} is ready: use the ${work.tool.displayName}"
                                } else {
                                    "${work.item.name} ready in $wait"
                                }
                            add(readiness)
                        }
                    }
                    favorite?.let { add("Favorite crop: ${it.name} (ripe in the field)") }
                    engine.meal(ref)?.takeIf { it.isActive(now) }?.let {
                        add("Well fed: ${it.endsAt.secondsSince(now).durationText} left")
                    }
                }
            val foods =
                engine.storedItems
                    .filter { (item, _) ->
                        when (item) {
                            is StorageItem.Fish -> ref is AnimalRef.Pet
                            is StorageItem.CropItem -> item.crop.id == favorite?.id
                            is StorageItem.RareCrop -> item.crop.id == favorite?.id
                            else -> false
                        }
                    }.map { StoredState(it.first, it.second, "") }
            val feedable = ref !is AnimalRef.Pet
            return AnimalState(ref, name, lines, feedable, foods, pet, chicken?.kind, livestock?.variant)
        }
    }
}
