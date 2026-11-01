package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.homeUpgradeRejection
import com.hunter.stockfarmer.core.rules.nextHomeUpgrade
import java.time.Instant

internal data class BuildingUpgradeState(
    val origin: GridPoint,
    val market: Boolean,
    val upgrade: UpgradeState,
) {
    companion object {
        fun all(
            engine: GameEngine,
            now: Instant,
        ): List<BuildingUpgradeState> {
            val markets =
                QuestBoardState.all(engine, now).mapNotNull { board ->
                    board.upgrade?.let { BuildingUpgradeState(board.origin, true, it) }
                }
            val homes =
                engine.state.buildings.entries.sortedWith(compareBy({ it.key.row }, { it.key.col })).mapNotNull {
                    val next = engine.nextHomeUpgrade(it.key) ?: return@mapNotNull null
                    val building = Building.named(it.value) ?: return@mapNotNull null
                    BuildingUpgradeState(
                        it.key,
                        false,
                        UpgradeState(
                            "${building.name} Level ${next.level}",
                            listOf(
                                "Holds ${HomeUpgrade.capacity(it.value, next.level)} animals, " +
                                    "up from ${HomeUpgrade.capacity(it.value, next.level - 1)}",
                                "Unlocked at Level ${next.unlockLevel}",
                            ),
                            next.cost.dollars,
                            engine.homeUpgradeRejection(it.key)?.message,
                        ),
                    )
                }
            return markets + homes
        }
    }
}
