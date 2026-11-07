package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.presentation.facts
import com.hunter.stockfarmer.core.presentation.houseUpgradeFacts
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.farmhouseUpgradeRejection
import com.hunter.stockfarmer.core.rules.homeUpgradeRejection
import com.hunter.stockfarmer.core.rules.nextHomeUpgrade
import java.time.Instant

internal enum class BuildingUpgradeKind { MARKET, HOME, HOUSE }

internal data class BuildingUpgradeState(
    val origin: GridPoint,
    val kind: BuildingUpgradeKind,
    val upgrade: UpgradeState,
) {
    val market: Boolean get() = kind == BuildingUpgradeKind.MARKET
    val house: Boolean get() = kind == BuildingUpgradeKind.HOUSE
    val home: Boolean get() = kind == BuildingUpgradeKind.HOME

    constructor(
        origin: GridPoint,
        market: Boolean,
        upgrade: UpgradeState,
    ) : this(
        origin,
        if (market) BuildingUpgradeKind.MARKET else BuildingUpgradeKind.HOME,
        upgrade,
    )

    companion object {
        fun all(
            engine: GameEngine,
            now: Instant,
        ): List<BuildingUpgradeState> {
            val markets =
                QuestBoardState.all(engine, now).mapNotNull { board ->
                    board.upgrade?.let { BuildingUpgradeState(board.origin, BuildingUpgradeKind.MARKET, it) }
                }
            val homes =
                engine.state.buildings.entries.sortedWith(compareBy({ it.key.row }, { it.key.col })).mapNotNull {
                    val next = engine.nextHomeUpgrade(it.key) ?: return@mapNotNull null
                    val building = Building.named(it.value) ?: return@mapNotNull null
                    BuildingUpgradeState(
                        it.key,
                        BuildingUpgradeKind.HOME,
                        UpgradeState(
                            "${building.name} Level ${next.level}",
                            engine.facts(next),
                            next.cost.dollars,
                            engine.homeUpgradeRejection(it.key)?.message,
                        ),
                    )
                }
            val house =
                engine.state.buildings.entries
                    .firstOrNull {
                        Building.named(it.value)?.isHouse == true && Building.named(it.value)?.isUpgradedHouse == false
                    }?.let { entry ->
                        val origin = entry.key
                        BuildingUpgradeState(
                            origin,
                            BuildingUpgradeKind.HOUSE,
                            UpgradeState(
                                "Luxury House",
                                engine.houseUpgradeFacts,
                                StockMarketRules.HOUSE_UPGRADE_COST.dollars,
                                engine.farmhouseUpgradeRejection(origin, now)?.message,
                            ),
                        )
                    }
            return markets + homes + listOfNotNull(house)
        }
    }
}
