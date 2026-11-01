package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.ToolEffect
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.core.rules.nextUpgrade
import com.hunter.stockfarmer.core.rules.upgrade
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.EffectLine
import java.text.NumberFormat
import java.util.Locale

object ToolInfo {
    fun effects(tool: HarvestTool): List<EffectLine> {
        val lines =
            when (tool) {
                HarvestTool.scythe -> {
                    plain(
                        "Drag it over ripe crops to cut them and over sheep to take their wool; " +
                            "fruit trees need the Bucket",
                        "Everything goes into storage, to be sold from there",
                        "Earns XP for crops just like harvesting",
                        "Stops when storage is full",
                    )
                }

                HarvestTool.axe -> {
                    listOf(
                        effect("Drag it over a tree to fell it"),
                        effect(
                            "Gives ",
                            green("${Tree.all.minOfOrNull { it.logs } ?: 0}"),
                            " to ",
                            green("${Tree.all.maxOfOrNull { it.logs } ?: 0}"),
                            " logs, one more for each size it has grown to",
                        ),
                        effect("Buildings are made from logs and stones in storage"),
                        effect("Stops when the logs won't fit in storage"),
                    )
                }

                HarvestTool.pickaxe -> {
                    listOf(
                        effect("Drag it over a rock to break it"),
                        effect(
                            "Gives ",
                            green("${RockSize.small.stones}"),
                            " to ",
                            green("${RockSize.big.stones}"),
                            " stones, the bigger the rock the more",
                        ),
                        effect("Buildings are made from logs and stones in storage"),
                        effect("Stops when the stones won't fit in storage"),
                    )
                }

                HarvestTool.rod -> {
                    plain(
                        "Cast anywhere in a pond with a ready fish",
                        "Hold to lift the bar and keep the fish inside it to reel it in",
                        "Rarer fish are quicker and harder to keep",
                        "A fish that gets away stays in the pond",
                        "Catches go into storage",
                    )
                }

                HarvestTool.bucket -> {
                    listOf(
                        effect(
                            "Drag it over hens, cows, goats, pigs, rabbits and donkeys to take their eggs, " +
                                "milk and fertilizer, or over ripe fruit trees to pick them",
                        ),
                        effect("Each egg sells from storage for ", money(Egg.SALE_PRICE.dollars)),
                        effect("Picking fruit earns XP like cutting crops"),
                        effect(
                            "Drag it over a butterfly to catch it for storage, " +
                                "where it can be equipped and let go on the farm",
                        ),
                        effect("Stops when storage is full"),
                    )
                }
            }
        val upgrades = ToolUpgrade.all.filter { it.tool == tool }.sortedBy { it.level }
        val upgrade =
            if (upgrades.size < 2) {
                effect("Has no upgrades")
            } else {
                effect(
                    "Upgrade to Level ${upgrades[0].level} at ",
                    purple("Level ${upgrades[0].unlockLevel}"),
                    " and to Level ${upgrades[1].level} at ",
                    purple("Level ${upgrades[1].unlockLevel}"),
                )
            }
        return lines + upgrade
    }

    fun upgradeEffect(upgrade: ToolUpgrade): EffectLine =
        when (val bonus = upgrade.effect) {
            ToolEffect.HalvedPasses -> {
                effect("Takes ", purple("half as many passes"), " on everything it works on, rounded up")
            }

            ToolEffect.SinglePass -> {
                effect("Takes ", purple("1 pass"), " on everything it works on")
            }

            is ToolEffect.WiderCircle -> {
                effect(
                    "The circle in fishing mode is ",
                    purple("${NumberFormat.getNumberInstance(Locale.US).format(bonus.factor)}×"),
                    " as wide",
                )
            }

            is ToolEffect.DoubleDrop -> {
                effect(
                    purple("${(bonus.chance * 100).roundedHalfAwayFromZero().toInt()}%"),
                    " chance of gathering a second item each time",
                )
            }
        }
}

internal data class ToolUpgradeState(
    val title: String,
    val money: Double,
    val upgrade: ToolUpgrade?,
    val effects: List<EffectLine>,
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
                upgrade
                    ?.let {
                        listOf(
                            ToolInfo.upgradeEffect(it),
                            effect(
                                "Costs ",
                                money(it.cost.dollars),
                                " and is unlocked at Level ${it.unlockLevel}",
                            ),
                        )
                    }.orEmpty(),
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
