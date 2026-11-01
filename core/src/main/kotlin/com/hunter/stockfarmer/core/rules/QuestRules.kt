package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.randomElement
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object QuestRules {
    const val UPGRADE_COST = 10_000.0
    const val QUESTS_TO_UPGRADE = 10

    /** The most of one crop a harvest quest asks for, however big the field grows. */
    const val MOST_CROPS_ASKED = 100

    fun capacity(buildingID: String): Int =
        when (buildingID) {
            "marketStand" -> 4
            "marketStandLarge" -> 6
            else -> 0
        }

    /** Stock Market quests are offered once the House that opens the market can be built. */
    val stockQuestLevel: Int get() = Building.named("house1")?.unlockLevel ?: LevelRules.maxLevel

    /** What a stock quest is sized from: the price of the dearest building unlocked, about an hour of income at that level. */
    internal fun stockReferenceDollars(level: Int): Double =
        Building.progression.filter { it.unlockLevel <= level }.maxOfOrNull { it.cost } ?: 0.0

    /** Rounded to two significant figures so a target reads as a goal, never below 1. */
    internal fun niceDollars(value: Double): Int {
        if (value < 10) return max(1, value.roundedHalfAwayFromZero().toInt())
        val step = 10.0.pow(floor(log10(value)) - 1)
        return ((value / step).roundedHalfAwayFromZero() * step).toInt()
    }

    /** A small gain on a modest stake, as a slice of the money a player has at the level. */
    internal fun stockProfitTarget(
        level: Int,
        large: Boolean,
    ): Int = niceDollars(stockReferenceDollars(level) * if (large) 0.006 else 0.002)

    val maximumRewardXP: Int get() = LevelRules.xpToLeave(LevelRules.maxLevel) / 4

    fun rewardXP(
        level: Int,
        large: Boolean,
        objectives: List<QuestObjective>,
        excluding: Set<Int> = emptySet(),
    ): Int {
        val clamped = min(LevelRules.maxLevel, max(1, level))
        val effort = objectives.sumOf { effort(it, clamped) }
        val fraction = min(if (large) 0.20 else 0.07, max(if (large) 0.08 else 0.01, effort * 0.02))
        var reward = max(1, (LevelRules.xpToLeave(clamped) * fraction).roundedHalfAwayFromZero().toInt())
        // Keep offers distinct even when rounding or similar work produces the same reward.
        while (reward in excluding) reward += 1
        return reward
    }

    private fun effort(
        objective: QuestObjective,
        level: Int,
    ): Double {
        val count = objective.target.toDouble()
        return when (val goal = objective.goal) {
            is QuestGoal.Harvest -> {
                val minutes = Crop.named(goal.id)?.growMinutes ?: 1.0
                count / max(8, FarmlandRules.tilesUnlocked(level) / 5).toDouble() * (1 + minutes / 10)
            }

            is QuestGoal.Workshop -> {
                val duration = Recipe.all.firstOrNull { it.buildingID == goal.id }?.duration ?: 180.0
                count * (1 + duration / 300)
            }

            is QuestGoal.Feed -> {
                count / 10
            }

            is QuestGoal.Collect -> {
                count * (if (goal.id == StorageItem.Egg.id) 1.0 else 1.5) / 4
            }

            QuestGoal.PlantTrees -> {
                count / 4
            }

            QuestGoal.StockProfit, QuestGoal.StockValue -> {
                2.5
            }
        }
    }

    internal fun goals(level: Int): List<List<QuestGoal>> {
        val crops =
            Crop.all.filter { !it.isFruitTree && it.unlockLevel <= max(1, level - 3) }.map { QuestGoal.Harvest(it.id) }
        val workshops =
            Building.all.filter { it.unlockLevel <= level && Recipe.of(it) != null }.map { QuestGoal.Workshop(it.id) }
        val homes =
            Building.all
                .filter { it.unlockLevel <= level && (it.isChickenCoop || it.livestockHome != null) }
                .map { QuestGoal.Feed(it.id) }
        val produce: MutableList<QuestGoal> =
            if (level >=
                5
            ) {
                mutableListOf(QuestGoal.Collect(StorageItem.Egg.id))
            } else {
                mutableListOf()
            }
        for (building in Building.all.filter { it.unlockLevel <= level }) {
            for (species in building.livestockHome?.species ?: emptyList()) {
                val work = species.work as? LivestockWork.Gives ?: continue
                val goal = QuestGoal.Collect(work.item.id)
                if (goal !in produce) produce += goal
            }
        }
        return listOf(crops, workshops, homes, listOf(QuestGoal.PlantTrees), produce).filter { it.isNotEmpty() }
    }

    internal fun makeQuest(
        level: Int,
        large: Boolean,
        primary: QuestGoal,
        excluding: Set<Int>,
        using: RandomSource,
    ): MarketQuest {
        val selected = mutableListOf(primary)
        if (large && !primary.isStock) {
            val otherGroups = goals(level).filter { primary !in it }
            val goal = otherGroups.randomElement(using)?.randomElement(using)
            if (goal != null) selected += goal
        }
        val objectives =
            selected.map { goal ->
                QuestObjective(
                    goal,
                    if (goal.isStock) stockProfitTarget(level, large) else target(goal, level, large, using),
                )
            }
        return MarketQuest(
            objectives = objectives,
            rewardXP = rewardXP(level, large, objectives, excluding),
            rewardLevel = level,
        )
    }

    private fun target(
        goal: QuestGoal,
        level: Int,
        large: Boolean,
        using: RandomSource,
    ): Int =
        when (goal) {
            is QuestGoal.Harvest -> {
                val field = FarmlandRules.tilesUnlocked(level)
                val base = max(8, field / 5)
                min(MOST_CROPS_ASKED, using.nextInt(base..(base + base / 2)) * if (large) 3 else 1)
            }

            is QuestGoal.Workshop -> {
                using.nextInt(if (large) 3..5 else 1..2)
            }

            is QuestGoal.Feed -> {
                using.nextInt(if (large) 25..40 else 8..15)
            }

            is QuestGoal.Collect -> {
                using.nextInt(if (large) 10..16 else 3..6)
            }

            QuestGoal.PlantTrees -> {
                using.nextInt(if (large) 6..9 else 2..4)
            }

            QuestGoal.StockProfit, QuestGoal.StockValue -> {
                1
            }
        }
}
