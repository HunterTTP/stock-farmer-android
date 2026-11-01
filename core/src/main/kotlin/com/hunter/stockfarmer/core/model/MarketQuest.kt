package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface QuestGoal {
    @Serializable
    data class Harvest(
        val id: String,
    ) : QuestGoal

    @Serializable
    data class Workshop(
        val id: String,
    ) : QuestGoal

    @Serializable
    data class Feed(
        val id: String,
    ) : QuestGoal

    @Serializable
    data class Collect(
        val id: String,
    ) : QuestGoal

    @Serializable
    data object PlantTrees : QuestGoal

    @Serializable
    data object StockProfit : QuestGoal

    /** Retired; a board saved with one is given a profit quest in its place. */
    @Serializable
    data object StockValue : QuestGoal

    fun title(quantity: Int): String =
        when (this) {
            is Harvest -> {
                "Harvest $quantity ${Crop.named(id)?.produceName ?: id}"
            }

            is Workshop -> {
                "Collect $quantity ${if (quantity == 1) "batch" else "batches"} from the ${Building
                    .named(
                        id,
                    )?.name ?: id}"
            }

            is Feed -> {
                "Have animals from the ${Building.named(id)?.name ?: id} eat $quantity favorite crops"
            }

            is Collect -> {
                "Collect $quantity × ${StorageItem.of(id)?.name ?: id}"
            }

            StockProfit -> {
                "Earn ${quantity.toDouble().dollars} profit in the Stock Market"
            }

            StockValue -> {
                "Hit ${quantity.toDouble().dollars} portfolio value in the Stock Market"
            }

            PlantTrees -> {
                "Plant $quantity ${if (quantity == 1) "tree or fruit tree" else "trees or fruit trees"}"
            }
        }

    val buildingID: String?
        get() =
            when (this) {
                is Workshop -> id
                is Feed -> id
                else -> null
            }

    val isStock: Boolean get() = this == StockProfit

    /** The most an objective can ask for: stock goals are in dollars and can be large, the rest are counts. */
    val maxTarget: Int get() = if (isStock || this == StockValue) 1_000_000_000 else 10_000

    val isValid: Boolean
        get() =
            when (this) {
                is Harvest -> {
                    Crop.named(id) != null
                }

                is Workshop -> {
                    Recipe.all.any { it.buildingID == id }
                }

                is Feed -> {
                    Building.named(id)?.let { it.isChickenCoop || it.livestockHome != null } ?: false
                }

                is Collect -> {
                    StorageItem.of(id) in
                        listOf(
                            StorageItem.Egg,
                            StorageItem.Milk,
                            StorageItem.GoatMilk,
                            StorageItem.Fertilizer,
                            StorageItem.Wool,
                        )
                }

                PlantTrees, StockProfit, StockValue -> {
                    true
                }
            }
}

@Serializable
data class QuestObjective(
    val goal: QuestGoal,
    val target: Int,
    val progress: Int = 0,
) {
    val isComplete: Boolean get() = progress >= target
    val title: String get() = goal.title(target)
}

@Serializable
data class MarketQuest(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val objectives: List<QuestObjective>,
    val rewardXP: Int,
    /** Missing on quests offered before rewards varied by effort. */
    val rewardLevel: Int? = null,
) {
    val isComplete: Boolean get() = objectives.all { it.isComplete }
    val summary: String get() = objectives.joinToString(" and ") { it.title }
}
