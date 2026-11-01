package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.randomElement
import com.hunter.stockfarmer.core.util.shuffled
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val LARGE_BOARD = "marketStandLarge"

fun GameEngine.quests(origin: GridPoint): List<MarketQuest> {
    val id = state.buildings[origin] ?: return emptyList()
    if (QuestRules.capacity(id) <= 0) return emptyList()
    return state.marketQuests[id] ?: emptyList()
}

fun GameEngine.prepareQuests(
    origin: GridPoint,
    using: RandomSource,
) {
    migrateQuestRewards()
    capHarvestQuests()
    val id = state.buildings[origin] ?: return
    if (QuestRules.capacity(id) <= 0) return
    if (state.marketQuests[id] == null) fillQuestBoard(id, using) else ensureStockQuest(id, using)
}

internal fun GameEngine.capHarvestQuests() {
    state.marketQuests.replaceAll { _, quests ->
        quests.map { quest ->
            quest.copy(
                objectives =
                    quest.objectives.map { objective ->
                        if (objective.goal is QuestGoal.Harvest && objective.target > QuestRules.MOST_CROPS_ASKED) {
                            objective.copy(
                                target = QuestRules.MOST_CROPS_ASKED,
                                progress = min(objective.progress, QuestRules.MOST_CROPS_ASKED),
                            )
                        } else {
                            objective
                        }
                    },
            )
        }
    }
}

internal fun GameEngine.migrateQuestRewards() {
    val used =
        state.marketQuests.values
            .flatten()
            .filter { it.rewardLevel != null }
            .map { it.rewardXP }
            .toMutableSet()
    for (board in state.marketQuests.keys.sorted()) {
        state.marketQuests[board] =
            state.marketQuests.getValue(board).map { quest ->
                if (quest.rewardLevel != null) {
                    quest
                } else {
                    val reward = QuestRules.rewardXP(state.level, board == LARGE_BOARD, quest.objectives, used)
                    used += reward
                    quest.copy(rewardXP = reward, rewardLevel = state.level)
                }
            }
    }
}

/**
 * From the level the House unlocks, every board always carries one Stock Market quest: added to a board that lacks one,
 * taking a free slot or else the last unfinished quest.
 */
internal fun GameEngine.ensureStockQuest(
    board: String,
    using: RandomSource,
) {
    if (state.level < QuestRules.stockQuestLevel) return
    val quests = state.marketQuests[board]?.toMutableList() ?: return
    // The retired portfolio-value quest becomes a profit quest.
    val retired = quests.indexOfFirst { quest -> quest.objectives.any { it.goal == QuestGoal.StockValue } }
    if (retired >= 0) {
        quests[retired] = makeStockQuest(board, quests.map { it.rewardXP }.toSet(), using)
        state.marketQuests[board] = quests
    }
    if (quests.any { quest -> quest.objectives.any { it.goal.isStock } }) return
    val quest = makeStockQuest(board, emptySet(), using)
    if (quests.size < QuestRules.capacity(board)) {
        quests += quest
    } else {
        val index = quests.indexOfLast { !it.isComplete }
        if (index < 0) return
        quests[index] = quest
    }
    state.marketQuests[board] = quests
}

private fun GameEngine.makeStockQuest(
    board: String,
    extraRewards: Set<Int>,
    using: RandomSource,
): MarketQuest {
    val usedRewards =
        state.marketQuests.values
            .flatten()
            .map { it.rewardXP }
            .toSet() + extraRewards
    return QuestRules.makeQuest(state.level, board == LARGE_BOARD, QuestGoal.StockProfit, usedRewards, using)
}

internal fun GameEngine.fillQuestBoard(
    id: String,
    using: RandomSource,
) {
    val large = id == LARGE_BOARD
    val groups = QuestRules.goals(state.level)
    // Always offer an older crop and an unlocked building, then vary the remaining farm activities.
    val ordered = groups.take(2) + groups.drop(2).shuffled(using)
    if (ordered.isEmpty()) return
    val quests = (state.marketQuests[id] ?: emptyList()).toMutableList()
    val usedRewards =
        state.marketQuests.values
            .flatten()
            .map { it.rewardXP }
            .toMutableSet()
    val wantsStock =
        state.level >= QuestRules.stockQuestLevel && quests.none { q -> q.objectives.any { it.goal.isStock } }
    val capacity = max(quests.size, QuestRules.capacity(id))
    for (slot in quests.size until capacity) {
        if (wantsStock && slot == capacity - 1) {
            val quest = makeStockQuest(id, usedRewards, using)
            quests += quest
            usedRewards += quest.rewardXP
            continue
        }
        val goal = ordered[slot % ordered.size].randomElement(using) ?: continue
        val quest = QuestRules.makeQuest(state.level, large, goal, usedRewards, using)
        quests += quest
        usedRewards += quest.rewardXP
    }
    state.marketQuests[id] = quests
}

/** Turns in every finished quest on every board, as the player never has to ask. */
fun GameEngine.claimCompletedQuests(using: RandomSource): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    for (origin in state.buildings.keys.sortedWith(compareBy({ it.row }, { it.col }))) {
        for (quest in quests(origin).filter { it.isComplete }) events += claimQuest(quest.id, origin, using)
    }
    return events
}

fun GameEngine.claimQuest(
    questID: UUID,
    origin: GridPoint,
    using: RandomSource,
): List<GameEvent> {
    val id = state.buildings[origin] ?: return listOf(GameEvent.Rejected(Rejection.NothingHere))
    val index = state.marketQuests[id]?.indexOfFirst { it.id == questID } ?: -1
    val quest = state.marketQuests[id]?.getOrNull(index) ?: return listOf(GameEvent.Rejected(Rejection.NothingHere))
    if (!quest.isComplete) return listOf(GameEvent.Rejected(Rejection.QuestIncomplete))
    val levelBefore = state.level
    val xp = if (levelBefore < LevelRules.maxLevel) quest.rewardXP else 0
    state.xp += xp
    state.completedMarketQuests = min(1_000_000_000, state.completedMarketQuests + 1)
    if ((state.marketQuests[id]?.size ?: 0) > QuestRules.capacity(id)) {
        state.marketQuests[id] = state.marketQuests.getValue(id).filterIndexed { i, _ -> i != index }
    } else {
        replaceQuest(index, id, using)
    }
    ensureStockQuest(id, using)
    return listOf<GameEvent>(GameEvent.QuestCompleted(xp, quest.summary)) + levelUpEvents(levelBefore)
}

fun GameEngine.replaceQuest(
    questID: UUID,
    origin: GridPoint,
    using: RandomSource,
) {
    val id = state.buildings[origin] ?: return
    val index = state.marketQuests[id]?.indexOfFirst { it.id == questID } ?: return
    if (index < 0 || state.marketQuests[id]?.get(index)?.isComplete != false) return
    replaceQuest(index, id, using)
}

private fun GameEngine.replaceQuest(
    index: Int,
    board: String,
    using: RandomSource,
) {
    val existing = state.marketQuests[board] ?: return
    if (existing[index].objectives.any { it.goal.isStock }) {
        state.marketQuests[board] = existing.replacing(index, makeStockQuest(board, emptySet(), using))
        return
    }
    val used = existing.flatMap { it.objectives }.map { it.goal }
    val groups = QuestRules.goals(state.level)
    val fresh = groups.map { group -> group.filter { it !in used } }.filter { it.isNotEmpty() }
    val goal = (fresh.ifEmpty { groups }).randomElement(using)?.randomElement(using) ?: return
    val usedRewards =
        state.marketQuests.values
            .flatten()
            .filter { it.id != existing[index].id }
            .map { it.rewardXP }
            .toSet()
    val replacement = QuestRules.makeQuest(state.level, board == LARGE_BOARD, goal, usedRewards, using)
    state.marketQuests[board] = existing.replacing(index, replacement)
}

private fun List<MarketQuest>.replacing(
    index: Int,
    quest: MarketQuest,
): List<MarketQuest> = mapIndexed { i, old -> if (i == index) quest else old }

internal fun GameEngine.recordQuestProgress(
    goal: QuestGoal,
    quantity: Int = 1,
) {
    if (quantity <= 0) return
    for (id in state.marketQuests.keys.toList()) {
        if (id !in state.buildings.values) continue
        val quests = state.marketQuests[id] ?: continue
        state.marketQuests[id] =
            quests.map { quest ->
                quest.copy(
                    objectives =
                        quest.objectives.map { objective ->
                            if (objective.goal != goal || objective.isComplete) {
                                objective
                            } else {
                                objective.copy(
                                    progress =
                                        objective.progress + min(quantity, objective.target - objective.progress),
                                )
                            }
                        },
                )
            }
    }
}
