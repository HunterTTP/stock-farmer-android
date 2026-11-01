package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.model.compactDurationText
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.CareerSeries
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.bestDayIncome
import com.hunter.stockfarmer.core.rules.bestDayStockProfit
import com.hunter.stockfarmer.core.rules.history

internal data class CareerState(
    val level: Int,
    val totalXP: String,
    val xpProgress: String,
    val levelFraction: Float,
    val time: String,
    val income: String,
    val profit: String,
    val bestIncome: String,
    val bestProfit: String,
    val cropsHarvested: String,
    val completedQuests: String,
    val histories: Map<CareerSeries, List<ValueSample>>,
    val collections: Map<CareerCollection, List<CareerSection>>,
) {
    val collected: Int get() = collections.values.flatten().sumOf { it.collected }
    val total: Int get() = collections.values.flatten().sumOf { it.entries.size }
    val perfection: Int get() = if (total == 0) 0 else collected * 100 / total

    companion object {
        fun from(engine: GameEngine): CareerState {
            val state = engine.state
            val (earned, needed) = LevelRules.progress(state.xp)
            return CareerState(
                state.level,
                ScreenFormat.number(state.xp.toLong()),
                if (state.level >= LevelRules.maxLevel) {
                    "Max level"
                } else {
                    "${ScreenFormat.number(earned.toLong())} / ${ScreenFormat.number(needed.toLong())} XP"
                },
                earned.toFloat() / needed.coerceAtLeast(1),
                state.playTime.compactDurationText,
                (state.career.grossIncomeCents / 100.0).dollars,
                ScreenFormat.signedMoney(state.lifetimeStockProfitCents),
                engine.bestDayIncome.dollars,
                engine.bestDayStockProfit.dollars,
                ScreenFormat.number(state.career.cropsHarvested.toLong()),
                ScreenFormat.number(state.completedMarketQuests.toLong()),
                CareerSeries.entries.associateWith { engine.history(it) },
                CareerCollection.entries.associateWith { CareerCollections.sections(engine, it) },
            )
        }
    }
}
