package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.DecorationEffect
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GrowthConditions
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.name
import com.hunter.stockfarmer.core.rules.price
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import com.hunter.stockfarmer.ui.theme.EffectLine
import com.hunter.stockfarmer.ui.theme.EffectLine.Segment
import com.hunter.stockfarmer.ui.theme.EffectLine.Segment.Tint

/** Item text and semantic tints, built without Compose or Android calls. */
data class ItemInfo(
    val name: String,
    val effects: List<EffectLine>,
    val placedLabel: String,
) {
    companion object {
        fun from(
            item: ShopItem,
            engine: GameEngine,
        ): ItemInfo {
            val lines = mutableListOf<EffectLine>()
            engine.requiredLevel(item)?.let { lines += effect("Unlocks at ", purple("Level $it")) }
            costLine(item, engine)?.let { lines += it }
            lines +=
                when (item) {
                    is ShopItem.Seed -> {
                        val crop = Crop.named(item.cropID) ?: Crop.starter
                        listOf(
                            if (crop.isFruitTree) {
                                effect(
                                    "Stands on four free grass tiles and bears a basket worth ",
                                    money(crop.sellValue.dollars),
                                    " again and again",
                                )
                            } else {
                                effect("Sells for ", money(crop.sellValue.dollars), " when harvested")
                            },
                            effect(
                                if (crop.isFruitTree) "Ripens in " else "Grows in ",
                                blue(crop.growDuration.durationText),
                            ),
                            effect("Harvesting earns ", purple("${LevelRules.harvestXP(crop)} XP")),
                        )
                    }

                    is ShopItem.GroundCoverItem -> {
                        plain(
                            "A walkable path through the farm",
                            "Clear with Grass, Farmland or Water",
                        )
                    }

                    ShopItem.Farmland -> {
                        plain(
                            "Crops can only be planted on farmland",
                            "Every level unlocks more tiles",
                        )
                    }

                    ShopItem.Water -> {
                        listOf(
                            effect(
                                "Farmland next to it grows ",
                                green(percent(GrowthConditions.SATURATION_MULTIPLIER)),
                                " faster",
                            ),
                        )
                    }

                    ShopItem.Fence -> {
                        plain(
                            "Fences join up with their neighbors, and animals can't walk through them",
                            "Go on grass only, or behind the top row of a building taller than one tile",
                            "Tap a fence to sell it",
                            "Tap a gate to open or close it; hold it to sell",
                        )
                    }

                    is ShopItem.Tree -> {
                        val species = TreeSpecies.named(item.id) ?: TreeSpecies.pine
                        listOf(
                            effect(
                                "Starts small and grows through ",
                                green("${species.trees.size}"),
                                " sizes, in ",
                                green("${species.colors.size}"),
                                " colors",
                            ),
                            effect("Stands on bare grass, and animals can't walk through it"),
                            effect("Fell it with the Axe from the Harvest tool"),
                        )
                    }

                    is ShopItem.DecorationItem -> {
                        decorationLines(Decoration.named(item.id) ?: Decoration.all.first())
                    }

                    ShopItem.Fertilizer -> {
                        listOf(
                            effect(
                                "Crops planted on farmland with fertilizer grow ",
                                green(percent(Fertilizer.growthMultiplier)),
                                " faster",
                            ),
                            effect("It is used up when the crop on it is harvested"),
                        )
                    }

                    is ShopItem.BuildingItem -> {
                        descriptionLines(Building.named(item.id) ?: Building.starter)
                    }
                }
            return ItemInfo(engine.name(item), lines, if (item is ShopItem.Seed) "Planted" else "Placed")
        }

        fun descriptionLines(building: Building): List<EffectLine> =
            when {
                building.id == "tractor1" -> {
                    plain(
                        "Shows planted crops, their ready times and gathered cargo",
                        "Harvests crops and brings them home; tap Store or Sell to unload",
                        "Only one can be placed",
                    )
                }

                building.isPetHouse -> {
                    listOf(
                        effect("Holds one pet, a cat or a dog: choose its color and name"),
                        effect("Up to ", green("${Building.named("petHouse")?.placementLimit ?: 0}"), " can be placed"),
                        effect("The pet may ripen a crop it stands on"),
                    )
                }

                building.isHouse -> {
                    listOf(
                        effect(
                            "Tap it to open the Stock Market: buy and sell shares of up to ",
                            green("${StockMarketRules.BASE_SLOT_LIMIT}"),
                            " stocks",
                        ),
                        effect(
                            "At Level ${StockMarketRules.HOUSE_UPGRADE_LEVEL}, upgrade it for ",
                            money(StockMarketRules.HOUSE_UPGRADE_COST.dollars),
                            " to own up to ",
                            green("${StockMarketRules.UPGRADED_SLOT_LIMIT}"),
                        ),
                        effect("Only one can be placed"),
                    )
                }

                building.isChickenCoop -> {
                    coopLines()
                }

                building.livestockHome != null -> {
                    livestockLines(building.livestockHome!!)
                }

                Recipe.of(building) != null -> {
                    workshopLines(Recipe.of(building)!!)
                }

                QuestRules.capacity(building.id) > 0 -> {
                    listOf(
                        effect(
                            "${QuestRules.capacity(building.id)} " +
                                (if (building.id == "marketStandLarge") "harder, two-part " else "") +
                                "quests reward XP scaled to your level",
                        ),
                        effect(
                            "Complete ${QuestRules.QUESTS_TO_UPGRADE} quests to upgrade for ",
                            money(QuestRules.UPGRADE_COST.dollars),
                        ),
                        effect("Only one Market Stand can be built"),
                    )
                }

                building.storageCapacity != null -> {
                    listOf(
                        effect("Adds ", green("${building.storageCapacity}"), " to your total storage"),
                        if (building.placementLimit == 1) {
                            effect("Only one can be placed")
                        } else {
                            effect("Up to ", green("${building.placementLimit}"), " can be placed")
                        },
                        effect("Can only be destroyed once its space in storage is empty"),
                    )
                }

                else -> {
                    emptyList()
                }
            }

        private fun coopLines(): List<EffectLine> {
            val base = HomeUpgrade.capacity("chickenCoop", 1)
            val upgraded = HomeUpgrade.capacity("chickenCoop", HomeUpgrade.maxLevel)
            val limit = Building.named("chickenCoop")?.placementLimit ?: 0
            return listOf(
                effect(
                    "Hatches a chick when placed, then another every ",
                    blue(Coop.HATCH_INTERVAL.durationText),
                    ", up to ",
                    green("$base"),
                    " a coop, and ",
                    green("$upgraded"),
                    " when upgraded",
                ),
                effect("Up to ", green("$limit"), " can be placed"),
                effect(
                    "Each hen lays up to ",
                    green("${Egg.MAX_PER_HEN}"),
                    " eggs worth ",
                    money(Egg.SALE_PRICE.dollars),
                ),
                effect("Hens eat ripe Wheat from the field to lay twice as fast for five minutes"),
            )
        }

        private fun livestockLines(home: LivestockHome): List<EffectLine> {
            val names = home.species.map { it.pluralName.lowercase(java.util.Locale.US) }
            val kinds =
                if (names.size > 1) {
                    names.dropLast(1).joinToString(", ") + " and " + names.last()
                } else {
                    names.joinToString()
                }
            val growth = home.species.map { it.growDuration }
            return buildList {
                add(
                    effect(
                        "Holds ",
                        green("${home.capacity}"),
                        " $kinds, and up to ",
                        green("${HomeUpgrade.capacity(home.buildingID, HomeUpgrade.maxLevel)}"),
                        " when upgraded",
                    ),
                )
                if (home.species.size > 1) add(effect("Each one keeps a single kind, picked before it is placed"))
                add(
                    effect(
                        "A baby every ",
                        blue(home.birthInterval.durationText),
                        ", grown up in ",
                        blue((growth.minOrNull() ?: 0.0).durationText),
                        if (growth.minOrNull() == growth.maxOrNull()) {
                            ""
                        } else {
                            " to ${(growth.maxOrNull() ?: 0.0).durationText}"
                        },
                    ),
                )
                home.species.forEach { species ->
                    val work = species.work as? LivestockWork.Gives ?: return@forEach
                    add(
                        effect(
                            "${species.pluralName} give ${work.item.name.lowercase(java.util.Locale.US)} worth ",
                            money(work.item.sellValue.dollars),
                            " for the ${work.tool.displayName}",
                        ),
                    )
                }
            }
        }

        private fun workshopLines(recipe: Recipe) =
            listOf(
                effect(
                    "Makes ",
                    green("${recipe.outputCount}"),
                    " ${recipe.output.name.lowercase(java.util.Locale.US)} from ",
                    green("${recipe.inputCount}"),
                    " ${recipe.input.name.lowercase(java.util.Locale.US)} in storage, in ",
                    blue(recipe.duration.durationText),
                ),
                effect("Each one sells for ", money(recipe.output.sellValue.dollars)),
                effect(
                    if (Recipe.all.count { it.buildingID == recipe.buildingID } > 1) {
                        "Choose a recipe in its menu, then collect it with the Bucket"
                    } else {
                        "Start a batch from its menu, then collect it with the Bucket"
                    },
                ),
            )

        private fun decorationLines(decoration: Decoration): List<EffectLine> {
            if (decoration.isRock) {
                return listOf(
                    effect(
                        "Break it with the Pickaxe for ",
                        green(RockSize.stoneSizes.joinToString(", ") { "${it.stones}" }),
                        " stones, by size",
                    ),
                )
            }
            val line =
                when (val bonus = decoration.effect) {
                    DecorationEffect.None -> {
                        effect("Just for show")
                    }

                    is DecorationEffect.Waters -> {
                        effect(
                            "Farmland within ",
                            green("${bonus.reach}"),
                            " tiles grows ",
                            green(percent(GrowthConditions.SATURATION_MULTIPLIER)),
                            " faster, as it does by water",
                        )
                    }

                    is DecorationEffect.Guards -> {
                        effect(
                            "Crops within ",
                            green("${bonus.reach}"),
                            " tiles grow ",
                            green(percent(Decoration.GUARD_MULTIPLIER)),
                            " faster",
                        )
                    }
                }
            return listOf(line, effect("Tap one to destroy it, and get 80% of its price back"))
        }

        private fun costLine(
            item: ShopItem,
            engine: GameEngine,
        ): EffectLine? =
            when (item) {
                is ShopItem.Seed -> {
                    val price = engine.price(item)
                    when {
                        Crop.named(item.cropID)?.isFruitTree == true -> {
                            effect(
                                "Costs ",
                                money(price.dollars),
                                " once: it never needs replanting",
                            )
                        }

                        price == 0.0 -> {
                            effect("Free to plant")
                        }

                        else -> {
                            effect("Costs ", money(price.dollars), " for each tile planted")
                        }
                    }
                }

                is ShopItem.BuildingItem, ShopItem.Farmland -> {
                    null
                }

                ShopItem.Fence -> {
                    effect("Costs ", money(FenceColor.price.dollars), " for each fence")
                }

                is ShopItem.DecorationItem -> {
                    effect(
                        "Costs ",
                        money((Decoration.named(item.id)?.cost ?: 0.0).dollars),
                        " each",
                    )
                }

                is ShopItem.Tree, ShopItem.Water -> {
                    effect("Free to place")
                }

                ShopItem.Fertilizer -> {
                    effect("Costs ", money(Fertilizer.price.dollars), " for each tile")
                }

                is ShopItem.GroundCoverItem -> {
                    effect("Costs ", money(item.cover.cost.dollars), " for each tile")
                }
            }
    }
}

internal fun effect(vararg parts: Any): EffectLine =
    EffectLine(parts.map { if (it is Segment) it else Segment(it.toString()) })

internal fun plain(vararg lines: String): List<EffectLine> = lines.map { effect(it) }

internal fun money(text: String) = Segment(text, Tint.Money)

internal fun blue(text: String) = Segment(text, Tint.Blue)

internal fun purple(text: String) = Segment(text, Tint.Purple)

internal fun green(text: String) = Segment(text, Tint.Green)

internal fun percent(multiplier: Double) = "${((multiplier - 1) * 100).roundedHalfAwayFromZero().toInt()}%"
