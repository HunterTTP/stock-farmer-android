package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.animalStandingAt
import com.hunter.stockfarmer.core.rules.beginMoving
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.cancelTractorHarvest
import com.hunter.stockfarmer.core.rules.chooseChickVariant
import com.hunter.stockfarmer.core.rules.chooseRecipe
import com.hunter.stockfarmer.core.rules.claimCompletedQuests
import com.hunter.stockfarmer.core.rules.demolish
import com.hunter.stockfarmer.core.rules.equip
import com.hunter.stockfarmer.core.rules.liftTool
import com.hunter.stockfarmer.core.rules.movePetIn
import com.hunter.stockfarmer.core.rules.name
import com.hunter.stockfarmer.core.rules.position
import com.hunter.stockfarmer.core.rules.releaseChicken
import com.hunter.stockfarmer.core.rules.releaseLivestock
import com.hunter.stockfarmer.core.rules.rename
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.sellLivestock
import com.hunter.stockfarmer.core.rules.sellStored
import com.hunter.stockfarmer.core.rules.sellTractorCargo
import com.hunter.stockfarmer.core.rules.startTractorHarvest
import com.hunter.stockfarmer.core.rules.startWork
import com.hunter.stockfarmer.core.rules.storeTractorCargo
import com.hunter.stockfarmer.core.rules.summonPet
import com.hunter.stockfarmer.core.rules.trashStored
import com.hunter.stockfarmer.core.rules.upgradeHome
import com.hunter.stockfarmer.core.rules.upgradeHouse
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import java.time.Instant

internal sealed interface SheetAction {
    data class Recipe(
        val id: String,
    ) : SheetAction

    data object StartWork : SheetAction

    data object CollectGoods : SheetAction

    data class MovePetIn(
        val breed: PetBreed,
        val name: String,
    ) : SheetAction

    data object Summon : SheetAction

    data object Upgrade : SheetAction

    data class NextChick(
        val id: String?,
    ) : SheetAction

    data class Harvest(
        val cropID: String,
    ) : SheetAction

    data object CancelHarvest : SheetAction

    data class Cargo(
        val cropID: String,
        val sell: Boolean,
    ) : SheetAction

    data class Stored(
        val item: StorageItem,
        val quantity: Int,
        val sell: Boolean,
    ) : SheetAction

    data object Move : SheetAction

    data object Demolish : SheetAction

    data class Rename(
        val name: String,
    ) : SheetAction

    data object Release : SheetAction

    data object Sell : SheetAction

    data class Feed(
        val item: StorageItem,
    ) : SheetAction
}

internal data class SheetResult(
    val events: List<GameEvent>,
    val close: Boolean = false,
) {
    val refusal: String? get() =
        events
            .filterIsInstance<GameEvent.Rejected>()
            .firstOrNull()
            ?.rejection
            ?.message
    val shouldClose: Boolean get() = close && refusal == null
}

internal fun GameSession.sheetAction(
    target: SheetTarget,
    action: SheetAction,
    now: Instant,
    using: RandomSource = SystemRandomSource,
): SheetResult =
    act { engine ->
        val result = SheetActions.perform(engine, target, action, now)
        if (result.events.isEmpty() && (action is SheetAction.Recipe || action is SheetAction.NextChick)) {
            haptic(HapticCue.Light)
        }
        result.copy(events = result.events + engine.claimCompletedQuests(using))
    }.also { publish(it.events) }

internal object SheetActions {
    fun perform(
        engine: GameEngine,
        target: SheetTarget,
        action: SheetAction,
        now: Instant,
    ): SheetResult =
        when (target) {
            is SheetTarget.Building -> building(engine, target.origin, action, now)
            is SheetTarget.Animal -> animal(engine, target.animal, action, now)
            is SheetTarget.Fish -> rejected(Rejection.UseTheRodForFish)
        }

    private fun building(
        engine: GameEngine,
        origin: GridPoint,
        action: SheetAction,
        now: Instant,
    ): SheetResult {
        val placed =
            engine.state.placedBuildings.firstOrNull { it.origin == origin }
                ?: return rejected(Rejection.NothingHere)
        val events =
            when (action) {
                is SheetAction.Recipe -> {
                    engine.chooseRecipe(action.id, origin, now)
                }

                SheetAction.StartWork -> {
                    engine.startWork(origin, now)
                }

                SheetAction.CollectGoods -> {
                    engine.select(HarvestTool.bucket)
                    engine.useTool(origin, now).also { engine.liftTool() }
                }

                is SheetAction.MovePetIn -> {
                    engine.movePetIn(origin, action.breed, action.name)
                }

                SheetAction.Summon -> {
                    engine.summonPet(origin)
                }

                SheetAction.Upgrade -> {
                    if (placed.building.isHouse) engine.upgradeHouse(origin, now) else engine.upgradeHome(origin)
                }

                is SheetAction.NextChick -> {
                    engine.chooseChickVariant(action.id, origin)
                }

                is SheetAction.Harvest -> {
                    engine.startTractorHarvest(origin, action.cropID, now)
                }

                SheetAction.CancelHarvest -> {
                    engine.cancelTractorHarvest(origin)
                }

                is SheetAction.Cargo -> {
                    if (action.sell) {
                        engine.sellTractorCargo(action.cropID, origin)
                    } else {
                        engine.storeTractorCargo(action.cropID, origin)
                    }
                }

                is SheetAction.Stored -> {
                    if (action.sell) {
                        engine.sellStored(action.item, action.quantity)
                    } else {
                        engine.trashStored(action.item, action.quantity)
                    }
                }

                SheetAction.Move -> {
                    if (engine.state.tractorRun?.home == origin) return rejected(Rejection.TractorBusy)
                    engine.beginMoving(placed)
                    return SheetResult(emptyList(), engine.buildingPlacement != null)
                }

                SheetAction.Demolish -> {
                    engine.demolish(placed)
                }

                else -> {
                    return rejected(Rejection.NothingHere)
                }
            }
        return SheetResult(
            events,
            action is SheetAction.MovePetIn || action is SheetAction.Harvest ||
                action == SheetAction.CancelHarvest || action == SheetAction.Demolish || action == SheetAction.Summon,
        )
    }

    private fun animal(
        engine: GameEngine,
        animal: AnimalRef,
        action: SheetAction,
        now: Instant,
    ): SheetResult {
        if (engine.name(animal) == null) return rejected(Rejection.NothingHere)
        return when (action) {
            is SheetAction.Rename -> {
                engine.rename(animal, action.name)
                SheetResult(emptyList())
            }

            SheetAction.Release -> {
                when (animal) {
                    is AnimalRef.Chicken -> SheetResult(engine.releaseChicken(animal.id, animal.coop), true)
                    is AnimalRef.Livestock -> SheetResult(engine.releaseLivestock(animal.id, animal.home), true)
                    is AnimalRef.Pet -> rejected(Rejection.NothingHere)
                }
            }

            SheetAction.Sell -> {
                when (animal) {
                    is AnimalRef.Livestock -> SheetResult(engine.sellLivestock(animal.id, animal.home), true)
                    is AnimalRef.Chicken, is AnimalRef.Pet -> rejected(Rejection.NothingHere)
                }
            }

            is SheetAction.Feed -> {
                val point = engine.position(animal) ?: return rejected(Rejection.NothingHere)
                if (engine.animalStandingAt(point) != animal) return rejected(Rejection.NeedAnimalToFeed)
                // Feeding uses the same held-item path as dragging produce from Storage onto an animal.
                if (!engine.equip(action.item)) return rejected(Rejection.NoneInStorage(action.item.name))
                SheetResult(engine.useTool(point, now).also { engine.liftTool() })
            }

            else -> {
                rejected(Rejection.NothingHere)
            }
        }
    }

    private fun rejected(reason: Rejection) = SheetResult(listOf(GameEvent.Rejected(reason)))
}
