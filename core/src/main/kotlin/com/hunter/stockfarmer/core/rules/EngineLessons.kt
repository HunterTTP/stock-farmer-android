package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import java.time.Instant

/** The step the player should do next. */
val GameEngine.currentLesson: Lesson? get() = Lesson.entries.firstOrNull { it !in state.lessonsLearned }

/** Marks a step as done; only the step being taught counts, so they can't be skipped ahead. */
fun GameEngine.learn(lesson: Lesson) {
    if (currentLesson != lesson) return
    state.lessonsLearned.add(lesson)
}

/** Marks a step and every step before it as done, for things that can only happen after the earlier ones. */
internal fun GameEngine.learnThrough(lesson: Lesson) {
    Lesson.entries.take(lesson.ordinal + 1).forEach { state.lessonsLearned.add(it) }
}

/** Ends the wait for the wheat once some is ripe; if the Scythe is already in hand there is nothing left to pick. */
fun GameEngine.learnWheatIsRipe(now: Instant) {
    if (currentLesson != Lesson.grow || !isAnyCropRipe(now)) return
    learn(Lesson.grow)
    if (state.mode == PlayMode.harvest && state.harvestTool == HarvestTool.scythe) learn(Lesson.pickHarvest)
}

/** Whether a ripe crop is waiting, which is when the harvest lesson tells the player to go and cut it. */
fun GameEngine.isAnyCropRipe(now: Instant): Boolean {
    val conditions = GrowthConditions(state)
    return state.plots.any { (point, plot) -> conditions.isReady(plot, point, now) }
}

/** Wheat is the example the lessons sell. */
val GameEngine.itemToLearnSellingWith: StorageItem?
    get() = if ((state.storage[Crop.starter.id] ?: 0) > 0) StorageItem.CropItem(Crop.starter) else null

internal fun GameEngine.markHarvested() = learnThrough(Lesson.harvest)

/** Adds real time spent playing, which is how long the game has been open and in front. */
fun GameEngine.addPlayTime(seconds: Double) {
    if (seconds <= 0) return
    state.playTime += seconds
    state.career.sample(state.playTime, state.xp, state.lifetimeStockProfitCents)
}

/** Remembers when the game was saved, so the time it then spends closed can be told. */
fun GameEngine.markSaved(date: Instant) {
    state.lastSavedAt = date
}
