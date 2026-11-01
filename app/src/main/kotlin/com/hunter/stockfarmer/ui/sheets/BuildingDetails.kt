package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText

/** Building descriptions from the reference ItemInfo, with numbers taken from the catalog. */
internal object BuildingDetails {
    fun lines(building: Building): List<String> {
        if (building.isPetHouse) {
            return listOf(
                "Holds one pet, a cat or a dog: choose its color and name",
                "Up to ${building.placementLimit} can be placed",
                "The pet may ripen a crop it stands on",
            )
        }
        if (building.isChickenCoop) {
            return listOf(
                "Hatches a chick when placed, then another every ${Coop.HATCH_INTERVAL.durationText}",
                "Holds ${HomeUpgrade.capacity(building.id, 1)} chickens, " +
                    "and ${HomeUpgrade.capacity(building.id, HomeUpgrade.maxLevel)} when upgraded",
                "Up to ${building.placementLimit} can be placed",
                "Each hen lays up to ${Egg.MAX_PER_HEN} eggs worth ${Egg.SALE_PRICE.dollars}",
                "Hens eat ripe ${Chicken.favoriteCrop.name} from the field, or their favorite crop from storage",
            )
        }
        building.livestockHome?.let { home ->
            return listOf(
                "Holds ${home.capacity} animals, " +
                    "and up to ${HomeUpgrade.capacity(building.id, HomeUpgrade.maxLevel)} when upgraded",
                "A baby every ${home.birthInterval.durationText}",
            ) +
                home.species.map { species ->
                    val work = species.work as LivestockWork.Gives
                    "${species.pluralName} give ${work.item.name.lowercase()} worth " +
                        "${work.item.sellValue.dollars} for the ${work.tool.displayName}"
                }
        }
        Recipe.of(building)?.let { recipe ->
            return listOf(
                "Makes ${recipe.outputCount} ${recipe.output.name} from " +
                    "${recipe.inputCount} ${recipe.input.name} in storage, in ${recipe.duration.durationText}",
                "Each one sells for ${recipe.output.sellValue.dollars}",
                "Choose a recipe in its menu, then collect it with the Bucket",
            )
        }
        building.storageCapacity?.let { capacity ->
            return listOf(
                "Adds $capacity to your total storage",
                "Up to ${building.placementLimit} can be placed",
                "Can only be destroyed once its space in storage is empty",
            )
        }
        return emptyList()
    }
}
