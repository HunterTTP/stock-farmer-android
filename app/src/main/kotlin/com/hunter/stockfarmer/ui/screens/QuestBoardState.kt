package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.marketUpgradeRejection
import com.hunter.stockfarmer.core.rules.quests
import java.time.Instant
import java.util.UUID

internal data class QuestObjectiveState(
    val title: String,
    val progress: String,
    val fraction: Float,
    val hint: String?,
    val art: ScreenArt?,
    val symbol: String,
)

internal data class QuestCardState(
    val id: UUID,
    val reward: String,
    val complete: Boolean,
    val objectives: List<QuestObjectiveState>,
) {
    companion object {
        fun from(
            quest: MarketQuest,
            engine: GameEngine,
        ): QuestCardState =
            QuestCardState(
                quest.id,
                if (engine.state.level < LevelRules.maxLevel) {
                    "${ScreenFormat.number(quest.rewardXP.toLong())} XP"
                } else {
                    "Maximum level"
                },
                quest.isComplete,
                quest.objectives.map { objective ->
                    val buildings = engine.state.buildings.values
                    val hint =
                        when {
                            objective.goal.isStock && buildings.none { Building.named(it)?.isHouse == true } -> {
                                "Build a House to open the Stock Market"
                            }

                            objective.goal.buildingID?.let { it !in buildings } == true -> {
                                "Available in the Build tool"
                            }

                            else -> {
                                null
                            }
                        }
                    QuestObjectiveState(
                        objective.title,
                        "${objective.progress}/${objective.target}",
                        (objective.progress.toFloat() / objective.target.coerceAtLeast(1)).coerceIn(0f, 1f),
                        hint,
                        objective.goal.art,
                        (objective.goal as? QuestGoal.Collect)?.id?.let(StorageItem::of)?.iconSymbol ?: "✦",
                    )
                },
            )
    }
}

private val QuestGoal.art: ScreenArt?
    get() =
        when (this) {
            is QuestGoal.Harvest -> {
                Crop.named(id)?.let(ScreenArt::crop)
            }

            is QuestGoal.Collect -> {
                StorageItem.of(id)?.let { ScreenArt.item(it) }
            }

            is QuestGoal.Workshop, is QuestGoal.Feed -> {
                buildingID?.let(Building::named)?.let {
                    ScreenArt.image("buildings", it.assetName)
                }
            }

            QuestGoal.StockProfit, QuestGoal.StockValue -> {
                Building.named("house1")?.let {
                    ScreenArt.image("buildings", it.assetName)
                }
            }

            QuestGoal.PlantTrees -> {
                ScreenArt.image(
                    "trees",
                    Tree.starter.assetName(
                        Tree.starter.species.colors
                            .first(),
                    ),
                )
            }
        }

internal data class QuestBoardState(
    val origin: GridPoint,
    val name: String,
    val quests: List<QuestCardState>,
    val upgrade: UpgradeState?,
) {
    companion object {
        fun all(
            engine: GameEngine,
            now: Instant,
        ): List<QuestBoardState> =
            engine.state.buildings.entries.sortedWith(compareBy({ it.key.row }, { it.key.col })).mapNotNull {
                val building =
                    Building.named(it.value)?.takeIf { building -> building.isMarketStand }
                        ?: return@mapNotNull null
                QuestBoardState(
                    it.key,
                    building.name,
                    engine.quests(it.key).map { quest -> QuestCardState.from(quest, engine) },
                    if (building.isUpgradedMarketStand) {
                        null
                    } else {
                        UpgradeState(
                            "Upgrade Market Stand",
                            listOf(
                                "Your Market Stand becomes bigger where it stands",
                                "Its quests are harder and in two parts and reward more XP",
                                "Needs ${QuestRules.QUESTS_TO_UPGRADE} completed quests",
                                "Quests completed: ${engine.state.completedMarketQuests}/" +
                                    QuestRules.QUESTS_TO_UPGRADE,
                            ),
                            QuestRules.UPGRADE_COST.dollars,
                            engine.marketUpgradeRejection(it.key, now)?.message,
                        )
                    },
                )
            }
    }
}

internal data class UpgradeState(
    val title: String,
    val effects: List<String>,
    val cost: String,
    val lockReason: String?,
)

data class QuestReward(
    val summary: String,
    val xp: Int,
) {
    companion object {
        fun from(events: List<GameEvent>): List<QuestReward> =
            events.filterIsInstance<GameEvent.QuestCompleted>().map { QuestReward(it.summary, it.xp) }
    }
}
