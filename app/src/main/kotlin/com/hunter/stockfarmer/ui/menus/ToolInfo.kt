package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.presentation.ItemFacts
import com.hunter.stockfarmer.core.presentation.facts
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.core.rules.nextUpgrade
import com.hunter.stockfarmer.core.rules.upgrade
import com.hunter.stockfarmer.game.GameSession

internal data class ToolUpgradeState(
    val title: String,
    val money: Double,
    val upgrade: ToolUpgrade?,
    val facts: ItemFacts?,
    val requiredLevel: Int?,
    val affordable: Boolean,
) {
    companion object {
        fun from(
            engine: GameEngine,
            tool: HarvestTool,
        ): ToolUpgradeState {
            val upgrade = engine.nextUpgrade(tool)
            return ToolUpgradeState(
                "${tool.displayName} Level ${upgrade?.level ?: engine.level(tool)}",
                engine.state.money,
                upgrade,
                upgrade?.let { engine.facts(it) },
                upgrade?.unlockLevel?.takeIf { it > engine.state.level },
                upgrade?.let { engine.state.money >= it.cost } ?: true,
            )
        }
    }
}

data class ToolUpgradeResult(
    val bought: Boolean,
    val message: String,
)

/** Success has no core event, so callers use bought to dismiss and message for the HUD toast. */
fun GameSession.upgradeTool(tool: HarvestTool): ToolUpgradeResult =
    act { engine ->
        val events = engine.upgrade(tool)
        publish(events)
        val rejection = events.firstRejection
        if (rejection != null) {
            ToolUpgradeResult(false, rejection.message)
        } else {
            ToolUpgradeResult(true, "${tool.displayName} upgraded to Level ${engine.level(tool)}")
        }
    }
